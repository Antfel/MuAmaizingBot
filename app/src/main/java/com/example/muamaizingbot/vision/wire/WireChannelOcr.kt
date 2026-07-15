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
 * OCR for the shared MU "Switch Channel" list.
 *
 * Every map uses the same row shape: `{MapName}-{N}Switch` (no space before Switch),
 * e.g. `Plain of Four Winds 2-6Switch`. Only [N] identifies the wire.
 */
object WireChannelOcr {

    private const val TAG = "WireChannelOcr"
    private const val OCR_UPSCALE = 2.5

    /**
     * Prefer the digit immediately before `Switch` so `…2-6Switch` → 6 (not 2).
     * Tolerates OCR spaces / dash variants.
     */
    private val WIRE_BEFORE_SWITCH = Regex(
        pattern = """(\d)\s*Switch""",
        option = RegexOption.IGNORE_CASE,
    )

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    data class ChannelHit(
        val wireId: Int,
        val centerX: Int,
        val centerY: Int,
        val bounds: Rect,
        val rawText: String,
    )

    /** All wire rows visible in [listRoi], sorted top → bottom. */
    suspend fun findVisibleChannels(frame: Bitmap, listRoi: Rect): List<ChannelHit> {
        val crop = crop(frame, listRoi) ?: return emptyList()
        val processed = preprocess(crop)
        crop.recycle()
        val ocrBitmap = processed ?: return emptyList()

        return try {
            val text = recognize(ocrBitmap) ?: return emptyList()
            val hits = mutableListOf<ChannelHit>()
            for (block in text.textBlocks) {
                for (line in block.lines) {
                    val wireId = parseWireId(line.text) ?: continue
                    val box = line.boundingBox ?: continue
                    // Preprocess upscales the crop; map boxes back to capture pixels.
                    val abs = Rect(
                        listRoi.left + (box.left / OCR_UPSCALE).toInt(),
                        listRoi.top + (box.top / OCR_UPSCALE).toInt(),
                        listRoi.left + (box.right / OCR_UPSCALE).toInt(),
                        listRoi.top + (box.bottom / OCR_UPSCALE).toInt(),
                    )
                    hits.add(
                        ChannelHit(
                            wireId = wireId,
                            centerX = abs.centerX(),
                            centerY = abs.centerY(),
                            bounds = abs,
                            rawText = line.text.trim(),
                        ),
                    )
                }
            }
            val sorted = hits
                .groupBy { it.wireId }
                .values
                .map { group -> group.maxBy { it.bounds.width() * it.bounds.height() } }
                .sortedBy { it.centerY }
            Log.d(
                TAG,
                "[WIRE_OCR] visible=${sorted.map { "${it.wireId}@y${it.centerY}" }} " +
                    "raw=${sorted.map { it.rawText }}",
            )
            sorted
        } finally {
            ocrBitmap.recycle()
        }
    }

    suspend fun findChannel(
        frame: Bitmap,
        listRoi: Rect,
        wireId: Int,
    ): ChannelHit? {
        return findVisibleChannels(frame, listRoi).firstOrNull { it.wireId == wireId }
    }

    /**
     * Parse wire id from a channel row label.
     * Examples: `Plain of Four Winds 2-6Switch` → 6, `Lorencia-3Switch` → 3.
     */
    fun parseWireId(raw: String): Int? {
        val compact = raw
            .replace('\u2013', '-')
            .replace('\u2014', '-')
            .replace(" ", "")
        return WIRE_BEFORE_SWITCH.findAll(compact)
            .lastOrNull()
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.takeIf { it in 1..9 }
    }

    private fun crop(frame: Bitmap, listRoi: Rect): Bitmap? {
        val left = listRoi.left.coerceIn(0, frame.width)
        val top = listRoi.top.coerceIn(0, frame.height)
        val right = listRoi.right.coerceIn(left, frame.width)
        val bottom = listRoi.bottom.coerceIn(top, frame.height)
        val w = right - left
        val h = bottom - top
        if (w < 24 || h < 24) {
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
                    Log.w(TAG, "[WIRE_OCR] ML Kit failed: ${error.message}")
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
        }
    }
}
