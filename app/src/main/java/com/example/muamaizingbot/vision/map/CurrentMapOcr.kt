package com.example.muamaizingbot.vision.map

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.example.muamaizingbot.maps.MapDefinition
import com.example.muamaizingbot.vision.opencv.OpenCvBitmapConverter
import com.example.muamaizingbot.vision.roi.ScaledRoi
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.text.Normalizer
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * OCR for the top-right HUD zone name (“Plain of Four Winds 2”, “Raklion 1”, …).
 *
 * Replaces `*_current.png` template matching: sibling maps share nearly identical glyphs
 * and false-positive above threshold (e.g. Plains 1 template on Plains 2 @ 0.95).
 */
object CurrentMapOcr {

    private const val TAG = "CurrentMapOcr"
    private const val OCR_UPSCALE = 3.0

    /**
     * Top-right map-name band, left of the wire Switch chip.
     * Calibrated @1280×720 around Plains/Raklion/Kalima HUD labels (~1040–1235 × 0–36).
     */
    private const val ROI_REF_LEFT = 2080
    private const val ROI_REF_TOP = 0
    private const val ROI_REF_RIGHT = 2470
    private const val ROI_REF_BOTTOM = 72

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    data class ReadResult(
        val rawText: String,
        val matched: Boolean,
    )

    suspend fun isOnMap(frame: Bitmap, mapDef: MapDefinition): Boolean {
        return read(frame, mapDef).matched
    }

    suspend fun read(frame: Bitmap, mapDef: MapDefinition): ReadResult {
        val expected = mapDef.name.ifBlank { mapDef.id }
        val raw = readRaw(frame)
        if (raw.isNullOrBlank()) {
            Log.d(TAG, "[MAP_OCR] empty expected=\"$expected\"")
            return ReadResult(rawText = "", matched = false)
        }
        val matched = matchesExpected(raw, expected)
        Log.d(
            TAG,
            "[MAP_OCR] raw=\"${raw.replace('\n', ' ').trim()}\" " +
                "expected=\"$expected\" matched=$matched",
        )
        return ReadResult(rawText = raw, matched = matched)
    }

    suspend fun readRaw(frame: Bitmap): String? {
        val roi = mapNameRoi(frame)
        val crop = crop(frame, roi) ?: run {
            Log.w(TAG, "[MAP_OCR] crop failed roi=$roi")
            return null
        }
        val processed = preprocess(crop)
        crop.recycle()
        if (processed == null) {
            Log.w(TAG, "[MAP_OCR] preprocess failed")
            return null
        }
        return try {
            recognize(processed)?.text?.replace('\n', ' ')?.trim()
        } finally {
            processed.recycle()
        }
    }

    fun mapNameRoi(frame: Bitmap): Rect {
        return ScaledRoi.fromRefRect(
            ROI_REF_LEFT,
            ROI_REF_TOP,
            ROI_REF_RIGHT,
            ROI_REF_BOTTOM,
            frame.width,
            frame.height,
        )
    }

    /**
     * Digit-safe name match: “Raklion 1” must not match “Raklion 11” / “Raklion 2”.
     * Tolerates OCR junk around the label (parentheses, trailing Switch bleed).
     */
    fun matchesExpected(raw: String, expectedName: String): Boolean {
        val cleaned = sanitizeHudText(raw)
        val ocr = compress(cleaned)
        val exp = compress(expectedName)
        if (exp.isEmpty() || ocr.isEmpty()) return false

        var idx = ocr.indexOf(exp)
        while (idx >= 0) {
            val end = idx + exp.length
            val nextIsDigit = end < ocr.length && ocr[end].isDigit()
            if (!nextIsDigit) {
                return true
            }
            idx = ocr.indexOf(exp, idx + 1)
        }

        // Fallback: same trailing floor number + base name tokens.
        val expNum = trailingNumber(expectedName) ?: return false
        val ocrNum = trailingNumber(cleaned) ?: return false
        if (expNum != ocrNum) return false
        val expBase = compress(expectedName.replace(Regex("""\d+\s*$"""), ""))
        val ocrBase = compress(cleaned.replace(Regex("""\d+\s*$"""), ""))
        return expBase.length >= 4 && ocrBase.contains(expBase)
    }

    /** Drop wire-chip bleed (“…2-6Switch”) and bracket noise. */
    fun sanitizeHudText(raw: String): String {
        return raw
            .replace('\u2013', '-')
            .replace('\u2014', '-')
            .replace(Regex("""(?i)[\-\s]*\d*\s*Switch.*"""), " ")
            .replace(Regex("""\[.*?\]"""), " ")
            .trim()
    }

    /** Letters + digits only, lowercased, accents stripped. */
    fun compress(raw: String): String {
        val decomposed = Normalizer.normalize(raw, Normalizer.Form.NFD)
        return decomposed
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .filter { it.isLetterOrDigit() }
    }

    fun trailingNumber(raw: String): Int? {
        return Regex("""(\d+)\s*$""").find(sanitizeHudText(raw).trim())
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
    }

    private fun crop(frame: Bitmap, roi: Rect): Bitmap? {
        val left = roi.left.coerceIn(0, frame.width)
        val top = roi.top.coerceIn(0, frame.height)
        val right = roi.right.coerceIn(left, frame.width)
        val bottom = roi.bottom.coerceIn(top, frame.height)
        val w = right - left
        val h = bottom - top
        if (w < 24 || h < 12) {
            return null
        }
        return Bitmap.createBitmap(frame, left, top, w, h)
    }

    private fun preprocess(crop: Bitmap): Bitmap? {
        val bgr = OpenCvBitmapConverter.bitmapToBgrMat(crop)
        val gray = Mat()
        Imgproc.cvtColor(bgr, gray, Imgproc.COLOR_BGR2GRAY)
        bgr.release()

        val upscaled = Mat()
        Imgproc.resize(gray, upscaled, Size(), OCR_UPSCALE, OCR_UPSCALE, Imgproc.INTER_CUBIC)
        gray.release()

        val blurred = Mat()
        Imgproc.GaussianBlur(upscaled, blurred, Size(3.0, 3.0), 0.0)
        upscaled.release()

        val thresholded = Mat()
        Imgproc.threshold(
            blurred,
            thresholded,
            0.0,
            255.0,
            Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU,
        )
        blurred.release()

        val rgba = Mat()
        Imgproc.cvtColor(thresholded, rgba, Imgproc.COLOR_GRAY2RGBA)
        thresholded.release()
        val bitmap = Bitmap.createBitmap(rgba.cols(), rgba.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(rgba, bitmap)
        rgba.release()
        return bitmap
    }

    private suspend fun recognize(bitmap: Bitmap): com.google.mlkit.vision.text.Text? {
        val image = InputImage.fromBitmap(bitmap, 0)
        return suspendCancellableCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    if (continuation.isActive) {
                        continuation.resume(result)
                    }
                }
                .addOnFailureListener { error ->
                    Log.w(TAG, "[MAP_OCR] ML Kit failed: ${error.message}")
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
        }
    }
}
