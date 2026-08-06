package com.example.muamaizingbot.vision.wire

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.example.muamaizingbot.vision.opencv.OpenCvBitmapConverter
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * OCR for the minimap HUD label `[Wire N]` / `[Line N]`.
 * Used to break ties when several [wire_N_hud] templates score similarly.
 *
 * Open-map titles use `Line`; the HUD chip uses `Wire` — same channel id.
 */
object WireHudOcr {

    private const val TAG = "WireHudOcr"
    private const val OCR_UPSCALE = 3.0

    /**
     * HUD chip uses `[Wire N]`; open-map title uses `[Line N]` for the same channel.
     * Also tolerate OCR noise like `IWire4]` / `(Line 1]`.
     */
    private val WIRE_LABEL = Regex(
        pattern = """(?:Wire|Line)\s*([1-9])""",
        option = RegexOption.IGNORE_CASE,
    )

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    fun parseHudWireId(raw: String): Int? {
        val compact = raw.replace('\n', ' ').trim()
        return WIRE_LABEL.find(compact)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.takeIf { it in 1..9 }
    }

    /** Read wire id from a crop around the HUD `[Wire N]` match. */
    suspend fun readWireId(frame: Bitmap, roi: Rect): Int? {
        val crop = crop(frame, roi) ?: run {
            Log.w(TAG, "[WIRE_HUD_OCR] crop failed roi=$roi")
            return null
        }
        val processed = preprocess(crop)
        crop.recycle()
        if (processed == null) {
            Log.w(TAG, "[WIRE_HUD_OCR] preprocess failed")
            return null
        }
        return try {
            val raw = recognize(processed)?.text?.replace('\n', ' ')?.trim().orEmpty()
            val wireId = parseHudWireId(raw)
            Log.d(TAG, "[WIRE_HUD_OCR] raw=\"$raw\" wire=$wireId roi=$roi")
            wireId
        } finally {
            processed.recycle()
        }
    }

    private fun crop(frame: Bitmap, roi: Rect): Bitmap? {
        val left = roi.left.coerceIn(0, frame.width)
        val top = roi.top.coerceIn(0, frame.height)
        val right = roi.right.coerceIn(left, frame.width)
        val bottom = roi.bottom.coerceIn(top, frame.height)
        val w = right - left
        val h = bottom - top
        if (w < 16 || h < 10) {
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

        val rgba = Mat()
        Imgproc.cvtColor(upscaled, rgba, Imgproc.COLOR_GRAY2RGBA)
        upscaled.release()
        val bitmap = Bitmap.createBitmap(rgba.cols(), rgba.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(rgba, bitmap)
        rgba.release()
        return bitmap
    }

    private suspend fun recognize(
        bitmap: Bitmap,
    ): com.google.mlkit.vision.text.Text? {
        val image = InputImage.fromBitmap(bitmap, 0)
        return suspendCancellableCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    if (continuation.isActive) {
                        continuation.resume(result)
                    }
                }
                .addOnFailureListener { error ->
                    Log.w(TAG, "[WIRE_HUD_OCR] ML Kit failed: ${error.message}")
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
        }
    }
}
