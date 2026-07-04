package com.example.muamaizingbot

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.muamaizingbot.vision.opencv.OpenCVInitializer
import com.example.muamaizingbot.vision.opencv.OpenCvBitmapConverter
import com.example.muamaizingbot.vision.template.PcTemplateMatcher
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

@RunWith(AndroidJUnit4::class)
class OpenCvMatchTemplateTest {

    @Test
    fun matchTemplate_doesNotCrashOnDevice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(OpenCVInitializer.init())

        val source = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        source.eraseColor(Color.WHITE)
        val template = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        template.eraseColor(Color.WHITE)

        var sourceMat: Mat? = null
        var templateMat: Mat? = null
        var resultMat: Mat? = null
        try {
            sourceMat = OpenCvBitmapConverter.bitmapToBgrMat(source)
            templateMat = OpenCvBitmapConverter.bitmapToBgrMat(template)
            resultMat = Mat()
            Imgproc.matchTemplate(sourceMat, templateMat, resultMat, Imgproc.TM_CCOEFF_NORMED)
            val minMax = Core.minMaxLoc(resultMat)
            assertTrue(minMax.maxVal >= 0.99)
        } finally {
            resultMat?.release()
            templateMat?.release()
            sourceMat?.release()
            source.recycle()
            template.recycle()
        }

        val pcResult = PcTemplateMatcher.matchDebug(
            source = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.WHITE)
            },
            template = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.WHITE)
            }
        )
        assertTrue(pcResult.score >= 0.99f)
    }
}
