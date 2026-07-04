package com.example.muamaizingbot.vision.coordinate

import android.graphics.Bitmap
import android.util.Log
import com.example.muamaizingbot.maps.MapDefinition
import com.example.muamaizingbot.vision.opencv.OpenCvBitmapConverter
import com.example.muamaizingbot.vision.roi.ScaledRoi
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

object CoordinateReader {

    private const val TAG = "CoordOcr"
    private const val OCR_UPSCALE = 3.0

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun readCurrentCoordinates(frame: Bitmap, mapDef: MapDefinition?): Pair<Int, Int>? {
        val crop = cropCoordRegion(frame) ?: run {
            Log.w(TAG, "[COORD] crop failed")
            return null
        }

        val processed = preprocessCoordCrop(crop)
        crop.recycle()
        if (processed == null) {
            Log.w(TAG, "[COORD] preprocess failed")
            return null
        }

        val ocrBitmap = grayMatToBitmap(processed)
        processed.release()

        val rawText = recognizeText(ocrBitmap)
        ocrBitmap.recycle()
        if (rawText.isNullOrBlank()) {
            Log.w(TAG, "[COORD] OCR empty")
            return null
        }

        Log.d(TAG, "[COORD] OCR raw text: ${rawText.trim()}")

        val parsed = CoordinateTextParser.parseCoordinates(rawText)
        if (parsed == null) {
            Log.w(TAG, "[COORD] parse failed")
            return null
        }

        val bounded = CoordinateTextParser.applyCoordinateBounds(parsed, mapDef?.coordinateBounds)
        if (bounded == null) {
            Log.w(TAG, "[COORD] outside bounds parsed=$parsed")
            return null
        }

        Log.d(TAG, "[COORD] Current coordinates: (${bounded.first},${bounded.second})")
        return bounded
    }

    private fun cropCoordRegion(frame: Bitmap): Bitmap? {
        val roi = ScaledRoi.fromRefRect(2435, 250, 2550, 285, frame.width, frame.height)
        val width = roi.width()
        val height = roi.height()
        if (width <= 0 || height <= 0) {
            return null
        }
        return Bitmap.createBitmap(frame, roi.left, roi.top, width, height)
    }

    private fun preprocessCoordCrop(crop: Bitmap): Mat? {
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
        Imgproc.threshold(blurred, thresholded, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)
        blurred.release()

        return thresholded
    }

    private fun grayMatToBitmap(gray: Mat): Bitmap {
        val rgba = Mat()
        Imgproc.cvtColor(gray, rgba, Imgproc.COLOR_GRAY2RGBA)
        val bitmap = Bitmap.createBitmap(rgba.cols(), rgba.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(rgba, bitmap)
        rgba.release()
        return bitmap
    }

    private suspend fun recognizeText(bitmap: Bitmap): String? {
        val image = InputImage.fromBitmap(bitmap, 0)
        return suspendCancellableCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    if (continuation.isActive) {
                        continuation.resume(result.text)
                    }
                }
                .addOnFailureListener { error ->
                    Log.w(TAG, "[COORD] ML Kit failed: ${error.message}")
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
        }
    }
}
