package com.example.muamaizingbot.vision.template

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.example.muamaizingbot.vision.opencv.OpenCVInitializer
import com.example.muamaizingbot.vision.opencv.OpenCvBitmapConverter
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

/** 1:1 template match against the fixed 1280×720 pack. */
object PcTemplateMatcher {

    private const val TAG = "PcTemplateMatcher"

    fun findTemplate(
        source: Bitmap,
        template: Bitmap,
        threshold: Float = 0.85f,
        roi: Rect? = null,
        templateName: String = "unknown",
        category: String = "unknown",
    ): PcTemplateMatchResult? {
        val result = matchDebug(
            source = source,
            template = template,
            templateName = templateName,
            category = category,
            roi = roi,
        )
        return if (result.score >= threshold) result else null
    }

    fun match(
        source: Bitmap,
        template: Bitmap,
        threshold: Float = 0.85f,
        roi: Rect? = null,
    ): Float {
        return matchDebug(source, template, roi = roi).score
    }

    fun matchDebug(
        source: Bitmap,
        template: Bitmap,
        templateName: String = "unknown",
        category: String = "unknown",
        roi: Rect? = null,
    ): PcTemplateMatchResult {
        require(!source.isRecycled) { "source bitmap is recycled" }
        require(!template.isRecycled) { "template bitmap is recycled" }

        if (!OpenCVInitializer.isInitialized) {
            Log.e(TAG, "[MATCH] opencv not initialized template=$templateName")
            return emptyResult(templateName, category, template.width, template.height)
        }

        val searchBitmap = cropToRoi(source, roi)
        val createdSearchBitmap = searchBitmap !== source

        var sourceMat: Mat? = null
        var templateMat: Mat? = null
        var resultMat: Mat? = null

        return try {
            val sw = searchBitmap.width
            val sh = searchBitmap.height
            val tw = template.width
            val th = template.height

            if (tw > sw || th > sh) {
                Log.w(
                    TAG,
                    "[MATCH] template larger than search area template=$templateName " +
                        "template=${tw}x$th search=${sw}x$sh frame=${source.width}x${source.height}",
                )
                return emptyResult(templateName, category, tw, th)
            }

            sourceMat = OpenCvBitmapConverter.bitmapToBgrMat(searchBitmap)
            templateMat = OpenCvBitmapConverter.bitmapToBgrMat(template)
            resultMat = Mat()
            Imgproc.matchTemplate(sourceMat, templateMat, resultMat, Imgproc.TM_CCOEFF_NORMED)

            val minMax = Core.minMaxLoc(resultMat)
            val roiOffsetX = roi?.left ?: 0
            val roiOffsetY = roi?.top ?: 0

            PcTemplateMatchResult(
                score = minMax.maxVal.toFloat(),
                bestX = minMax.maxLoc.x.toInt() + roiOffsetX,
                bestY = minMax.maxLoc.y.toInt() + roiOffsetY,
                templateWidth = tw,
                templateHeight = th,
                templateName = templateName,
                category = category,
            )
        } catch (t: Throwable) {
            Log.e(TAG, "[MATCH] error engine=opencv template=$templateName message=${t.message}")
            emptyResult(templateName, category, template.width, template.height)
        } finally {
            resultMat?.release()
            templateMat?.release()
            sourceMat?.release()
            if (createdSearchBitmap) {
                searchBitmap.recycle()
            }
        }
    }

    private fun cropToRoi(source: Bitmap, roi: Rect?): Bitmap {
        if (roi == null) {
            return source
        }
        val left = roi.left.coerceIn(0, source.width)
        val top = roi.top.coerceIn(0, source.height)
        val right = roi.right.coerceIn(left, source.width)
        val bottom = roi.bottom.coerceIn(top, source.height)
        val width = right - left
        val height = bottom - top
        if (width <= 0 || height <= 0) {
            return source
        }
        return Bitmap.createBitmap(source, left, top, width, height)
    }

    private fun emptyResult(
        templateName: String,
        category: String,
        templateWidth: Int,
        templateHeight: Int,
    ): PcTemplateMatchResult {
        return PcTemplateMatchResult(
            score = 0f,
            bestX = 0,
            bestY = 0,
            templateWidth = templateWidth,
            templateHeight = templateHeight,
            templateName = templateName,
            category = category,
        )
    }
}
