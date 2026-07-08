package com.example.muamaizingbot.calibration

import android.graphics.Matrix
import com.example.muamaizingbot.vision.coord.RefCoords
import kotlin.math.roundToInt

/**
 * Maps reference coords (2560×1440) to capture pixels using:
 * 1. Per-anchor scale (user-fitted template size)
 * 2. Perspective warp from the four HUD anchor centers
 */
data class CalibrationTransform(
    val captureWidth: Int,
    val captureHeight: Int,
    private val positionMatrix: Matrix,
    private val scaleXByAnchor: FloatArray,
    private val scaleYByAnchor: FloatArray,
    private val refOriginX: Float,
    private val refOriginY: Float,
    private val refMinX: Float,
    private val refMaxX: Float,
    private val refMinY: Float,
    private val refMaxY: Float,
) {
    fun map(refX: Int, refY: Int): Pair<Int, Int> {
        val u = ((refX - refMinX) / (refMaxX - refMinX)).coerceIn(0f, 1f)
        val v = ((refY - refMinY) / (refMaxY - refMinY)).coerceIn(0f, 1f)
        val scaleX = bilinearScale(u, v, scaleXByAnchor)
        val scaleY = bilinearScale(u, v, scaleYByAnchor)
        val scaledRefX = refOriginX + (refX - refOriginX) * scaleX
        val scaledRefY = refOriginY + (refY - refOriginY) * scaleY
        val pts = floatArrayOf(scaledRefX, scaledRefY)
        positionMatrix.mapPoints(pts)
        return pts[0].roundToInt() to pts[1].roundToInt()
    }

    /** HUD fit multiplier on top of linear capture/ref scale (average of 4 anchors). */
    fun averageCalibrationScaleX(): Float = scaleXByAnchor.average().toFloat()

    fun averageCalibrationScaleY(): Float = scaleYByAnchor.average().toFloat()

    fun scaleFactorsAt(refX: Int, refY: Int): Pair<Float, Float> {
        val u = ((refX - refMinX) / (refMaxX - refMinX)).coerceIn(0f, 1f)
        val v = ((refY - refMinY) / (refMaxY - refMinY)).coerceIn(0f, 1f)
        return bilinearScale(u, v, scaleXByAnchor) to bilinearScale(u, v, scaleYByAnchor)
    }

    /** Target pixel size for a ref-authored template on this calibrated capture. */
    fun calibratedTemplateSize(refWidth: Int, refHeight: Int): Pair<Int, Int> {
        return calibratedTemplateSize(
            refWidth,
            refHeight,
            RefCoords.REF_WIDTH / 2,
            RefCoords.REF_HEIGHT / 2,
        )
    }

    fun calibratedTemplateSize(
        refWidth: Int,
        refHeight: Int,
        refCenterX: Int,
        refCenterY: Int,
    ): Pair<Int, Int> {
        val (scaleX, scaleY) = scaleFactorsAt(refCenterX, refCenterY)
        val w = (
            refWidth.toLong() * captureWidth * scaleX / RefCoords.REF_WIDTH
            ).roundToInt().coerceAtLeast(1)
        val h = (
            refHeight.toLong() * captureHeight * scaleY / RefCoords.REF_HEIGHT
            ).roundToInt().coerceAtLeast(1)
        return w to h
    }

    private fun bilinearScale(u: Float, v: Float, scales: FloatArray): Float {
        return bilinear(u, v, scales[0], scales[1], scales[2], scales[3])
    }

    private fun bilinear(u: Float, v: Float, s00: Float, s10: Float, s01: Float, s11: Float): Float {
        return (1f - u) * (1f - v) * s00 +
            u * (1f - v) * s10 +
            (1f - u) * v * s01 +
            u * v * s11
    }

    companion object {
        fun fromScreenRects(
            captureWidth: Int,
            captureHeight: Int,
            screenRects: Map<CalibrationAnchor, CalibrationScreenRect>,
        ): CalibrationTransform? {
            if (screenRects.size != CalibrationAnchor.ordered.size) {
                return null
            }
            val src = FloatArray(8)
            val dst = FloatArray(8)
            val scaleX = FloatArray(4)
            val scaleY = FloatArray(4)
            CalibrationAnchor.ordered.forEachIndexed { index, anchor ->
                val rect = screenRects[anchor] ?: return null
                val (anchorX, anchorY) = rect.anchorPoint(anchor)
                src[index * 2] = anchor.refAnchorX.toFloat()
                src[index * 2 + 1] = anchor.refAnchorY.toFloat()
                dst[index * 2] = anchorX.toFloat()
                dst[index * 2 + 1] = anchorY.toFloat()
                val expectedW = anchor.refTemplateWidth.toFloat() * captureWidth / RefCoords.REF_WIDTH
                val expectedH = anchor.refTemplateHeight.toFloat() * captureHeight / RefCoords.REF_HEIGHT
                scaleX[index] = (rect.width / expectedW).coerceIn(0.5f, 2.0f)
                scaleY[index] = (rect.height / expectedH).coerceIn(0.5f, 2.0f)
            }
            val matrix = Matrix()
            matrix.setPolyToPoly(src, 0, dst, 0, 4)
            val anchors = CalibrationAnchor.ordered
            return CalibrationTransform(
                captureWidth = captureWidth,
                captureHeight = captureHeight,
                positionMatrix = matrix,
                scaleXByAnchor = scaleX,
                scaleYByAnchor = scaleY,
                refOriginX = RefCoords.REF_WIDTH / 2f,
                refOriginY = RefCoords.REF_HEIGHT / 2f,
                refMinX = minOf(anchors[0].refAnchorX, anchors[2].refAnchorX).toFloat(),
                refMaxX = maxOf(anchors[1].refAnchorX, anchors[3].refAnchorX).toFloat(),
                refMinY = minOf(anchors[0].refAnchorY, anchors[1].refAnchorY).toFloat(),
                refMaxY = maxOf(anchors[2].refAnchorY, anchors[3].refAnchorY).toFloat(),
            )
        }
    }
}
