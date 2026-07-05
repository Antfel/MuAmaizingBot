package com.example.muamaizingbot.settings

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.WindowMetrics
import com.example.muamaizingbot.vision.template.TemplateAssets

object ResolutionDetector {

    private const val MIN_SUPPORTED_WIDTH = 960
    private const val MIN_SUPPORTED_HEIGHT = 540

    fun detect(context: Context): ResolutionDetectionResult {
        val (width, height) = readDisplaySize(context)
        return classify(width, height)
    }

    fun classify(width: Int, height: Int): ResolutionDetectionResult {
        val (landscapeWidth, landscapeHeight) = normalizeLandscape(width, height)
        if (landscapeWidth < MIN_SUPPORTED_WIDTH || landscapeHeight < MIN_SUPPORTED_HEIGHT) {
            return ResolutionDetectionResult(
                displayWidth = width,
                displayHeight = height,
                matchType = ResolutionDetectionResult.MatchType.UNSUPPORTED,
                suggestedPreset = null,
                userMessage = "Resolución ${width}×${height} no soportada. Mínimo recomendado: 1280×720.",
            )
        }

        val exactKey = TemplateAssets.resolutionKey(landscapeWidth, landscapeHeight)
        if (exactKey in TemplateAssets.SUPPORTED_RESOLUTION_KEYS) {
            val preset = presetForKey(exactKey)
            return ResolutionDetectionResult(
                displayWidth = width,
                displayHeight = height,
                matchType = ResolutionDetectionResult.MatchType.EXACT,
                suggestedPreset = preset,
                userMessage = "Pantalla ${landscapeWidth}×${landscapeHeight} — preset ${preset.label}.",
            )
        }

        val snappedKey = TemplateAssets.snapToSupported(landscapeWidth, landscapeHeight)
        val preset = presetForKey(snappedKey)
        return ResolutionDetectionResult(
            displayWidth = width,
            displayHeight = height,
            matchType = ResolutionDetectionResult.MatchType.NEAREST,
            suggestedPreset = preset,
            userMessage = "Pantalla ${landscapeWidth}×${landscapeHeight} — sin templates exactos. " +
                "Se usará ${preset.width}×${preset.height} (más cercano).",
        )
    }

    /** MU Immortal runs landscape; use the longer edge as width for preset matching. */
    private fun normalizeLandscape(width: Int, height: Int): Pair<Int, Int> {
        return if (width >= height) width to height else height to width
    }

    private fun presetForKey(key: String): ResolutionPreset {
        return ResolutionPreset.entries.first { !it.isAuto && it.resolutionKey == key }
    }

    private fun readDisplaySize(context: Context): Pair<Int, Int> {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics: WindowMetrics = windowManager.currentWindowMetrics
            val bounds = metrics.bounds
            return bounds.width() to bounds.height()
        }
        @Suppress("DEPRECATION")
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        return metrics.widthPixels to metrics.heightPixels
    }
}
