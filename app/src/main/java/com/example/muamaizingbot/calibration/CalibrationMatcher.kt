package com.example.muamaizingbot.calibration

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.example.muamaizingbot.vision.template.PcTemplateMatcher

object CalibrationMatcher {

    private const val TAG = "CalibrationMatch"
    private const val MATCH_THRESHOLD = 0.55f

    private val templateCache = mutableMapOf<CalibrationAnchor, Bitmap>()

    fun suggestScreenPoint(
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
        Log.d(
            TAG,
            "[CAL] suggest anchor=${anchor.id} score=${match.score} at=(${match.centerX},${match.centerY})"
        )
        return match.centerX to match.centerY
    }

    fun defaultScreenPoint(
        frameWidth: Int,
        frameHeight: Int,
        anchor: CalibrationAnchor,
    ): Pair<Int, Int> {
        val x = anchor.refX.toLong() * frameWidth / REF_WIDTH
        val y = anchor.refY.toLong() * frameHeight / REF_HEIGHT
        return x.toInt() to y.toInt()
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

    private const val REF_WIDTH = 2560
    private const val REF_HEIGHT = 1440
}
