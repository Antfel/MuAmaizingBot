package com.example.muamaizingbot.vision.map

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.example.muamaizingbot.vision.opencv.OpenCvBitmapConverter
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Measures the green Auto-Navigating path on the open zone map via HSV
 * (no templates — path length varies). Calibrated on Plains + Kalima @ 1280×720:
 * `dots < 10` → Near, `dots >= 10` → Far.
 *
 * `dots == 0` is [PathClass.UNKNOWN] (path not painted / not visible yet), not Near.
 */
object MapPathLengthVision {

    private const val TAG = "MapPath"
    private const val NATIVE_W = 1280
    private const val NATIVE_H = 720

    /** Parchment canvas content (excludes left teleport list + chrome). */
    private const val ROI_L = 300
    private const val ROI_T = 80
    private const val ROI_R = 1180
    private const val ROI_B = 680

    /** Near / Far threshold agreed from Plains + Kalima samples. */
    const val FAR_MIN_DOTS = 10

    private val HSV_LOW = Scalar(35.0, 80.0, 100.0)
    private val HSV_HIGH = Scalar(90.0, 255.0, 255.0)

    private const val MIN_DOT_AREA = 5
    private const val MAX_DOT_AREA = 80
    private const val MAX_DOT_SIDE = 20

    enum class PathClass { NEAR, FAR, UNKNOWN }

    data class PathMeasure(
        val dots: Int,
        val pathClass: PathClass,
        val maskPixels: Int = 0,
    )

    fun pathContentRoi(frameWidth: Int, frameHeight: Int): Rect {
        val sx = frameWidth.toFloat() / NATIVE_W
        val sy = frameHeight.toFloat() / NATIVE_H
        return Rect(
            (ROI_L * sx).toInt().coerceIn(0, frameWidth),
            (ROI_T * sy).toInt().coerceIn(0, frameHeight),
            (ROI_R * sx).toInt().coerceIn(0, frameWidth),
            (ROI_B * sy).toInt().coerceIn(0, frameHeight),
        )
    }

    fun classify(dots: Int): PathClass = when {
        dots <= 0 -> PathClass.UNKNOWN
        dots < FAR_MIN_DOTS -> PathClass.NEAR
        else -> PathClass.FAR
    }

    fun measure(frame: Bitmap): PathMeasure {
        val (dots, maskPixels) = countGreenPathDots(frame)
        val pathClass = classify(dots)
        Log.d(TAG, "[PATH] dots=$dots maskPix=$maskPixels class=$pathClass")
        return PathMeasure(dots = dots, pathClass = pathClass, maskPixels = maskPixels)
    }

    /**
     * @return pair(dotCount, nonZeroMaskPixels). Dot count -1 on ROI failure.
     */
    fun countGreenPathDots(frame: Bitmap): Pair<Int, Int> {
        val roi = pathContentRoi(frame.width, frame.height)
        if (roi.width() < 40 || roi.height() < 40) return -1 to 0

        val crop = Bitmap.createBitmap(frame, roi.left, roi.top, roi.width(), roi.height())
        val bgr = OpenCvBitmapConverter.bitmapToBgrMat(crop)
        val hsv = Mat()
        val mask = Mat()
        val hierarchy = Mat()
        val contours = ArrayList<MatOfPoint>()
        return try {
            Imgproc.cvtColor(bgr, hsv, Imgproc.COLOR_BGR2HSV)
            Core.inRange(hsv, HSV_LOW, HSV_HIGH, mask)
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(3.0, 3.0))
            Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel, org.opencv.core.Point(-1.0, -1.0), 2)
            kernel.release()

            val maskPixels = Core.countNonZero(mask)
            Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            var dots = 0
            for (c in contours) {
                val rect = Imgproc.boundingRect(c)
                val area = Imgproc.contourArea(c).toInt()
                if (area < MIN_DOT_AREA || area > MAX_DOT_AREA) continue
                if (maxOf(rect.width, rect.height) > MAX_DOT_SIDE) continue
                dots++
            }
            dots to maskPixels
        } finally {
            contours.forEach { it.release() }
            hierarchy.release()
            mask.release()
            hsv.release()
            bgr.release()
            crop.recycle()
        }
    }
}
