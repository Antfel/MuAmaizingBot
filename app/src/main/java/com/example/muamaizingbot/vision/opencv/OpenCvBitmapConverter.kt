package com.example.muamaizingbot.vision.opencv

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

object OpenCvBitmapConverter {

    /** Bitmap ARGB → Mat BGR, igual que cv2.imread en el bot PC. */
    fun bitmapToBgrMat(bitmap: Bitmap): Mat {
        val rgba = Mat()
        Utils.bitmapToMat(bitmap, rgba)
        val bgr = Mat()
        when (rgba.channels()) {
            4 -> Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
            3 -> Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGB2BGR)
            else -> rgba.copyTo(bgr)
        }
        rgba.release()
        return bgr
    }
}
