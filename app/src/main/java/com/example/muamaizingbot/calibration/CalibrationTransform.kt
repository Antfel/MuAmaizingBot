package com.example.muamaizingbot.calibration

import android.graphics.Matrix
import kotlin.math.roundToInt

/**
 * Perspective map from reference HUD quad (2560×1440 anchors) to live capture quad.
 */
data class CalibrationTransform(
    val captureWidth: Int,
    val captureHeight: Int,
    private val matrix: Matrix,
) {
    fun map(refX: Int, refY: Int): Pair<Int, Int> {
        val pts = floatArrayOf(refX.toFloat(), refY.toFloat())
        matrix.mapPoints(pts)
        return pts[0].roundToInt() to pts[1].roundToInt()
    }

    companion object {
        fun fromScreenPoints(
            captureWidth: Int,
            captureHeight: Int,
            screenPoints: Map<CalibrationAnchor, Pair<Int, Int>>,
        ): CalibrationTransform? {
            if (screenPoints.size != CalibrationAnchor.ordered.size) {
                return null
            }
            val src = FloatArray(8)
            val dst = FloatArray(8)
            CalibrationAnchor.ordered.forEachIndexed { index, anchor ->
                val screen = screenPoints[anchor] ?: return null
                src[index * 2] = anchor.refX.toFloat()
                src[index * 2 + 1] = anchor.refY.toFloat()
                dst[index * 2] = screen.first.toFloat()
                dst[index * 2 + 1] = screen.second.toFloat()
            }
            val matrix = Matrix()
            matrix.setPolyToPoly(src, 0, dst, 0, 4)
            return CalibrationTransform(captureWidth, captureHeight, matrix)
        }
    }
}
