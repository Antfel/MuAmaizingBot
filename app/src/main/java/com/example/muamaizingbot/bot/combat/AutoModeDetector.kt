package com.example.muamaizingbot.bot.combat

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.example.muamaizingbot.vision.opencv.OpenCvBitmapConverter
import com.example.muamaizingbot.vision.roi.MuCombatRois
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.text.Normalizer
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Detect Auto vs Manual combat toggle via OCR inside a tight [MuCombatRois.autoHudRoi]
 * (excludes Inventory). Text is normalized (accents stripped) so `Aüto` → auto.
 */
object AutoModeDetector {

    private const val TAG = "AutoModeDetector"
    private const val OCR_UPSCALE = 3.0
    /** ROI is the label band only (~30px on 1280); keep tap near text center. */
    private const val MANUAL_TAP_OFFSET_UP_PX = 6

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun detect(frame: Bitmap): AutoModeDetection {
        val hudRoi = MuCombatRois.autoHudRoi(frame)
        val crop = crop(frame, hudRoi)
        if (crop == null) {
            Log.w(TAG, "[AUTO_OCR] crop failed roi=$hudRoi")
            return AutoModeDetection.empty()
        }

        val processed = preprocess(crop)
        crop.recycle()
        if (processed == null) {
            Log.w(TAG, "[AUTO_OCR] preprocess failed")
            return AutoModeDetection.empty()
        }

        return try {
            val text = recognize(processed) ?: run {
                Log.w(TAG, "[AUTO_OCR] empty/fail")
                return AutoModeDetection.empty()
            }

            val rawAll = text.text.replace('\n', ' ').trim()
            var autoHit: TextHit? = null
            var manualHit: TextHit? = null

            for (block in text.textBlocks) {
                for (line in block.lines) {
                    val lineText = line.text.trim()
                    if (lineText.isEmpty()) continue
                    val box = line.boundingBox ?: continue
                    val abs = mapBoxToFrame(box, hudRoi)
                    val hit = TextHit(lineText, abs)
                    when (classifyLabel(lineText)) {
                        Label.MANUAL -> {
                            if (manualHit == null || hit.area() > manualHit.area()) {
                                manualHit = hit
                            }
                        }
                        Label.AUTO -> {
                            if (autoHit == null || hit.area() > autoHit.area()) {
                                autoHit = hit
                            }
                        }
                        Label.NONE -> Unit
                    }
                }
            }

            // Whole-ROI fallback when lines split oddly ("A" + "üto").
            if (manualHit == null && autoHit == null) {
                when (classifyLabel(rawAll)) {
                    Label.MANUAL -> manualHit = TextHit(rawAll, hudRoi)
                    Label.AUTO -> autoHit = TextHit(rawAll, hudRoi)
                    Label.NONE -> Unit
                }
            }

            val isManual = manualHit != null
            val isAuto = !isManual && autoHit != null
            val tapY = manualHit?.bounds?.centerY()?.let { (it - MANUAL_TAP_OFFSET_UP_PX).coerceAtLeast(0) }

            Log.d(
                TAG,
                "[AUTO_OCR] raw=\"$rawAll\" norm=\"${normalizeOcr(rawAll)}\" " +
                    "manual=${manualHit?.text} auto=${autoHit?.text} " +
                    "→ isAuto=$isAuto isManual=$isManual " +
                    "roi=${hudRoi.left},${hudRoi.top}-${hudRoi.right},${hudRoi.bottom}",
            )

            AutoModeDetection(
                isAutoMode = isAuto,
                isManualMode = isManual,
                autoScore = if (isAuto) 1f else 0f,
                manualScore = if (isManual) 1f else 0f,
                manualTapX = manualHit?.bounds?.centerX(),
                manualTapY = tapY,
                ocrRaw = rawAll,
            )
        } finally {
            processed.recycle()
        }
    }

    private enum class Label { AUTO, MANUAL, NONE }

    /** Strip accents / junk so OCR noise like `Aüto` still matches. */
    fun normalizeOcr(raw: String): String {
        val decomposed = Normalizer.normalize(raw, Normalizer.Form.NFD)
        return decomposed
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .replace(Regex("[^a-z]"), "")
    }

    private fun classifyLabel(raw: String): Label {
        val n = normalizeOcr(raw)
        if (n.isEmpty()) return Label.NONE
        if (n.contains("manual")) return Label.MANUAL
        if (n.contains("autoplay") || n.contains("autonav") || n.contains("navigat")) {
            return Label.NONE
        }
        if (n.contains("auto")) return Label.AUTO
        return Label.NONE
    }

    private data class TextHit(val text: String, val bounds: Rect) {
        fun area(): Int = bounds.width().coerceAtLeast(0) * bounds.height().coerceAtLeast(0)
    }

    private fun mapBoxToFrame(box: Rect, hudRoi: Rect): Rect {
        return Rect(
            hudRoi.left + (box.left / OCR_UPSCALE).toInt(),
            hudRoi.top + (box.top / OCR_UPSCALE).toInt(),
            hudRoi.left + (box.right / OCR_UPSCALE).toInt(),
            hudRoi.top + (box.bottom / OCR_UPSCALE).toInt(),
        )
    }

    private fun crop(frame: Bitmap, roi: Rect): Bitmap? {
        val left = roi.left.coerceIn(0, frame.width)
        val top = roi.top.coerceIn(0, frame.height)
        val right = roi.right.coerceIn(left, frame.width)
        val bottom = roi.bottom.coerceIn(top, frame.height)
        val w = right - left
        val h = bottom - top
        if (w < 16 || h < 16) {
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

    private suspend fun recognize(bitmap: Bitmap): Text? {
        val image = InputImage.fromBitmap(bitmap, 0)
        return suspendCancellableCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    if (continuation.isActive) {
                        continuation.resume(result)
                    }
                }
                .addOnFailureListener { error ->
                    Log.w(TAG, "[AUTO_OCR] ML Kit failed: ${error.message}")
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
        }
    }
}

data class AutoModeDetection(
    val isAutoMode: Boolean,
    val isManualMode: Boolean,
    val autoScore: Float,
    val manualScore: Float,
    val manualTapX: Int?,
    val manualTapY: Int?,
    val ocrRaw: String = "",
) {
    companion object {
        fun empty(): AutoModeDetection =
            AutoModeDetection(
                isAutoMode = false,
                isManualMode = false,
                autoScore = 0f,
                manualScore = 0f,
                manualTapX = null,
                manualTapY = null,
                ocrRaw = "",
            )
    }
}
