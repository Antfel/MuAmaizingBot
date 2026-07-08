package com.example.muamaizingbot.calibration

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.example.muamaizingbot.vision.coord.RefCoords
import com.example.muamaizingbot.vision.template.PcTemplateMatcher
import kotlin.math.roundToInt

object CalibrationMatcher {

    private const val TAG = "CalibrationMatch"
    private const val MATCH_THRESHOLD = 0.55f

    private val templateCache = mutableMapOf<CalibrationAnchor, Bitmap>()

    /**
     * Returns frame center (top-left window position is derived from center + size).
     * Template match locates the crop; anchor offset within the crop is applied.
     */
    fun suggestFrameCenter(
        context: Context,
        frame: Bitmap,
        anchor: CalibrationAnchor,
    ): Pair<Int, Int>? {
        val template = loadTemplate(context, anchor) ?: return null
        val match = PcTemplateMatcher.findTemplate(
            source = frame,
            template = template,
            threshold = MATCH_THRESHOLD,
            templateName = anchor.id,
            category = "calibration",
        ) ?: run {
            Log.w(TAG, "[CAL] no match anchor=${anchor.id} frame=${frame.width}x${frame.height}")
            return null
        }
        val templateCenterX = match.centerX
        val templateCenterY = match.centerY
        Log.d(
            TAG,
            "[CAL] suggest anchor=${anchor.id} score=${match.score} frameCenter=($templateCenterX,$templateCenterY)",
        )
        return templateCenterX to templateCenterY
    }

    fun defaultFrameCenter(
        frameWidth: Int,
        frameHeight: Int,
        anchor: CalibrationAnchor,
        markerWidthPx: Int,
        markerHeightPx: Int,
    ): Pair<Int, Int> {
        val anchorX = anchor.refAnchorX.toLong() * frameWidth / REF_WIDTH
        val anchorY = anchor.refAnchorY.toLong() * frameHeight / REF_HEIGHT
        return anchor.frameCenterForAnchorPoint(
            anchorX.toInt(),
            anchorY.toInt(),
            markerWidthPx,
            markerHeightPx,
        )
    }

    private fun loadTemplate(context: Context, anchor: CalibrationAnchor): Bitmap? {
        templateCache[anchor]?.let { cached ->
            if (!cached.isRecycled) {
                return cached
            }
        }
        return runCatching {
            context.assets.open(anchor.templateAssetPath).use { stream ->
                BitmapFactory.decodeStream(stream)?.also { templateCache[anchor] = it }
            }
        }.getOrNull()
    }

    private const val REF_WIDTH = RefCoords.REF_WIDTH
    private const val REF_HEIGHT = RefCoords.REF_HEIGHT
}
