package com.example.muamaizingbot.vision.template

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.example.muamaizingbot.vision.opencv.OpenCVInitializer
import com.example.muamaizingbot.vision.opencv.OpenCvBitmapConverter
import kotlin.math.min
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
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
        circularMask: Boolean = false,
        opaqueMask: Boolean = false,
    ): PcTemplateMatchResult? {
        val result = matchDebug(
            source = source,
            template = template,
            templateName = templateName,
            category = category,
            roi = roi,
            circularMask = circularMask,
            opaqueMask = opaqueMask,
        )
        return if (result.score >= threshold) result else null
    }

    /**
     * All peaks ≥ [threshold], non-max suppressed by roughly one template footprint.
     * Highest score first.
     */
    fun findAllTemplates(
        source: Bitmap,
        template: Bitmap,
        threshold: Float = 0.85f,
        roi: Rect? = null,
        templateName: String = "unknown",
        category: String = "unknown",
        circularMask: Boolean = false,
        opaqueMask: Boolean = false,
        maxMatches: Int = 20,
    ): List<PcTemplateMatchResult> {
        require(!source.isRecycled) { "source bitmap is recycled" }
        require(!template.isRecycled) { "template bitmap is recycled" }

        if (!OpenCVInitializer.isInitialized) {
            Log.e(TAG, "[MATCH] opencv not initialized template=$templateName")
            return emptyList()
        }

        val searchBitmap = cropToRoi(source, roi)
        val createdSearchBitmap = searchBitmap !== source

        var sourceMat: Mat? = null
        var templateMat: Mat? = null
        var resultMat: Mat? = null
        var maskMat: Mat? = null

        return try {
            val sw = searchBitmap.width
            val sh = searchBitmap.height
            val tw = template.width
            val th = template.height
            if (tw > sw || th > sh) {
                return emptyList()
            }

            sourceMat = OpenCvBitmapConverter.bitmapToBgrMat(searchBitmap)
            templateMat = OpenCvBitmapConverter.bitmapToBgrMat(template)
            resultMat = Mat()
            runMaskedOrPlainMatch(sourceMat!!, templateMat!!, resultMat!!, circularMask, opaqueMask) { m ->
                maskMat = m
            }

            val roiOffsetX = roi?.left ?: 0
            val roiOffsetY = roi?.top ?: 0
            val suppressW = (tw * 0.7).toInt().coerceAtLeast(8)
            val suppressH = (th * 0.7).toInt().coerceAtLeast(8)
            val hits = ArrayList<PcTemplateMatchResult>(maxMatches.coerceAtMost(8))

            repeat(maxMatches) {
                val minMax = Core.minMaxLoc(resultMat!!)
                val score = minMax.maxVal.toFloat()
                if (score < threshold) {
                    return@repeat
                }
                val lx = minMax.maxLoc.x.toInt()
                val ly = minMax.maxLoc.y.toInt()
                hits.add(
                    PcTemplateMatchResult(
                        score = score,
                        bestX = lx + roiOffsetX,
                        bestY = ly + roiOffsetY,
                        templateWidth = tw,
                        templateHeight = th,
                        templateName = templateName,
                        category = category,
                    ),
                )
                // Zero neighborhood so next minMaxLoc finds another peak.
                val x0 = (lx - suppressW / 2).coerceAtLeast(0)
                val y0 = (ly - suppressH / 2).coerceAtLeast(0)
                val x1 = (lx + suppressW / 2 + 1).coerceAtMost(resultMat.cols())
                val y1 = (ly + suppressH / 2 + 1).coerceAtMost(resultMat.rows())
                if (x1 > x0 && y1 > y0) {
                    val region = resultMat.submat(y0, y1, x0, x1)
                    region.setTo(Scalar(-1.0))
                    region.release()
                }
            }
            hits.sortedByDescending { it.score }
        } catch (t: Throwable) {
            Log.e(TAG, "[MATCH] findAll error template=$templateName message=${t.message}")
            emptyList()
        } finally {
            maskMat?.release()
            resultMat?.release()
            templateMat?.release()
            sourceMat?.release()
            if (createdSearchBitmap) {
                searchBitmap.recycle()
            }
        }
    }

    fun match(
        source: Bitmap,
        template: Bitmap,
        threshold: Float = 0.85f,
        roi: Rect? = null,
        circularMask: Boolean = false,
        opaqueMask: Boolean = false,
    ): Float {
        return matchDebug(
            source,
            template,
            roi = roi,
            circularMask = circularMask,
            opaqueMask = opaqueMask,
        ).score
    }

    fun matchDebug(
        source: Bitmap,
        template: Bitmap,
        templateName: String = "unknown",
        category: String = "unknown",
        roi: Rect? = null,
        circularMask: Boolean = false,
        opaqueMask: Boolean = false,
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
        var maskMat: Mat? = null

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
            runMaskedOrPlainMatch(sourceMat!!, templateMat!!, resultMat!!, circularMask, opaqueMask) { m ->
                maskMat = m
            }

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
            maskMat?.release()
            resultMat?.release()
            templateMat?.release()
            sourceMat?.release()
            if (createdSearchBitmap) {
                searchBitmap.recycle()
            }
        }
    }

    private fun runMaskedOrPlainMatch(
        sourceMat: Mat,
        templateMat: Mat,
        resultMat: Mat,
        circularMask: Boolean,
        opaqueMask: Boolean,
        onMask: (Mat?) -> Unit,
    ) {
        // OpenCV only accepts a mask with TM_CCORR_NORMED / TM_SQDIFF.
        when {
            opaqueMask -> {
                val mask = opaqueMaskMat(templateMat)
                onMask(mask)
                Imgproc.matchTemplate(sourceMat, templateMat, resultMat, Imgproc.TM_CCORR_NORMED, mask)
            }
            circularMask -> {
                val mask = circularMaskMat(templateMat.cols(), templateMat.rows())
                onMask(mask)
                Imgproc.matchTemplate(sourceMat, templateMat, resultMat, Imgproc.TM_CCORR_NORMED, mask)
            }
            else -> {
                onMask(null)
                Imgproc.matchTemplate(sourceMat, templateMat, resultMat, Imgproc.TM_CCOEFF_NORMED)
            }
        }
    }

    /** Filled disk covering the inscribed circle of the template bitmap. */
    private fun circularMaskMat(width: Int, height: Int): Mat {
        val mask = Mat.zeros(height, width, CvType.CV_8UC1)
        val cx = (width - 1) / 2.0
        val cy = (height - 1) / 2.0
        val radius = (min(width, height) / 2.0)
        Imgproc.circle(mask, Point(cx, cy), radius.toInt(), Scalar(255.0), Imgproc.FILLED)
        return mask
    }

    /** Ignore near-black / empty canvas around map icons (boss_alive, golden_alive). */
    private fun opaqueMaskMat(templateBgr: Mat): Mat {
        val gray = Mat()
        Imgproc.cvtColor(templateBgr, gray, Imgproc.COLOR_BGR2GRAY)
        val mask = Mat()
        Imgproc.threshold(gray, mask, 18.0, 255.0, Imgproc.THRESH_BINARY)
        gray.release()
        return mask
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
