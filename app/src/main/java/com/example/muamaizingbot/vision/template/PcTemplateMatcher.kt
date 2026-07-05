package com.example.muamaizingbot.vision.template

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.example.muamaizingbot.vision.opencv.OpenCVInitializer
import com.example.muamaizingbot.vision.opencv.OpenCvBitmapConverter
import kotlin.math.roundToInt
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

object PcTemplateMatcher {

    private const val TAG = "PcTemplateMatcher"

    /** Fine tune around pre-scaled PNG size — game text rarely matches 0.75x exactly. */
    private val FINE_SCALE_FACTORS = floatArrayOf(0.90f, 0.94f, 0.97f, 1.0f, 1.03f, 1.06f, 1.10f)

    fun findTemplate(
        source: Bitmap,
        template: Bitmap,
        threshold: Float = 0.85f,
        roi: Rect? = null,
        templateName: String = "unknown",
        category: String = "unknown"
    ): PcTemplateMatchResult? {
        val result = matchDebug(
            source = source,
            template = template,
            templateName = templateName,
            category = category,
            roi = roi
        )
        return if (result.score >= threshold) result else null
    }

    fun match(
        source: Bitmap,
        template: Bitmap,
        threshold: Float = 0.85f,
        roi: Rect? = null
    ): Float {
        return matchDebug(source, template, roi = roi).score
    }

    fun matchDebug(
        source: Bitmap,
        template: Bitmap,
        templateName: String = "unknown",
        category: String = "unknown",
        roi: Rect? = null
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
        var templateOrigMat: Mat? = null
        var scaledTemplateMat: Mat? = null
        var resultMat: Mat? = null

        return try {
            val sw = searchBitmap.width
            val sh = searchBitmap.height
            val scaleFactors = fineScaleFactors()

            sourceMat = OpenCvBitmapConverter.bitmapToBgrMat(searchBitmap)
            templateOrigMat = OpenCvBitmapConverter.bitmapToBgrMat(template)

            var bestScore = 0f
            var bestX = 0
            var bestY = 0
            var bestTw = template.width
            var bestTh = template.height
            var bestFine = 1f

            for (fine in scaleFactors) {
                val tw = (template.width * fine).roundToInt().coerceAtLeast(1)
                val th = (template.height * fine).roundToInt().coerceAtLeast(1)
                if (tw > sw || th > sh) {
                    continue
                }

                scaledTemplateMat?.release()
                scaledTemplateMat = resizeTemplateMat(templateOrigMat, tw, th)
                resultMat?.release()
                resultMat = Mat()

                Imgproc.matchTemplate(sourceMat, scaledTemplateMat, resultMat, Imgproc.TM_CCOEFF_NORMED)

                val minMax = Core.minMaxLoc(resultMat)
                val score = minMax.maxVal.toFloat()
                if (score > bestScore) {
                    bestScore = score
                    bestX = minMax.maxLoc.x.toInt()
                    bestY = minMax.maxLoc.y.toInt()
                    bestTw = tw
                    bestTh = th
                    bestFine = fine
                }
            }

            if (bestScore <= 0f && (template.width > sw || template.height > sh)) {
                Log.w(
                    TAG,
                    "[MATCH] template larger than search area template=$templateName " +
                        "template=${template.width}x${template.height} search=${sw}x$sh " +
                        "frame=${source.width}x${source.height} " +
                        "resolution=${TemplateRepository.currentResolutionKey()}"
                )
            }

            val roiOffsetX = roi?.left ?: 0
            val roiOffsetY = roi?.top ?: 0

            if (bestFine != 1f && bestScore > 0f) {
                Log.d(
                    TAG,
                    "[MATCH] template=$templateName fine=${"%.2f".format(bestFine)} " +
                        "score=${"%.3f".format(bestScore)} size=${bestTw}x$bestTh " +
                        "resolution=${TemplateRepository.currentResolutionKey()}"
                )
            }

            PcTemplateMatchResult(
                score = bestScore,
                bestX = bestX + roiOffsetX,
                bestY = bestY + roiOffsetY,
                templateWidth = bestTw,
                templateHeight = bestTh,
                templateName = templateName,
                category = category
            )
        } catch (t: Throwable) {
            Log.e(
                TAG,
                "[MATCH] error engine=opencv template=$templateName message=${t.message}"
            )
            emptyResult(templateName, category, template.width, template.height)
        } finally {
            resultMat?.release()
            scaledTemplateMat?.release()
            templateOrigMat?.release()
            sourceMat?.release()
            if (createdSearchBitmap) {
                searchBitmap.recycle()
            }
        }
    }

    private fun fineScaleFactors(): FloatArray {
        return if (TemplateRepository.currentResolutionKey() == TemplateAssets.REF_RESOLUTION_KEY) {
            floatArrayOf(1f)
        } else {
            FINE_SCALE_FACTORS
        }
    }

    private fun resizeTemplateMat(source: Mat, targetW: Int, targetH: Int): Mat {
        val out = Mat()
        val downscale = targetW < source.cols() || targetH < source.rows()
        val interpolation = if (downscale) Imgproc.INTER_AREA else Imgproc.INTER_LINEAR
        Imgproc.resize(
            source,
            out,
            Size(targetW.toDouble(), targetH.toDouble()),
            0.0,
            0.0,
            interpolation,
        )
        return out
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
        templateHeight: Int
    ): PcTemplateMatchResult {
        return PcTemplateMatchResult(
            score = 0f,
            bestX = 0,
            bestY = 0,
            templateWidth = templateWidth,
            templateHeight = templateHeight,
            templateName = templateName,
            category = category
        )
    }
}
