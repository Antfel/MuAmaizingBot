package com.example.muamaizingbot.vision.coord

import android.graphics.Bitmap
import android.graphics.Rect
import com.example.muamaizingbot.maps.SwipeCoords
import com.example.muamaizingbot.settings.ResolutionSettingsRepository
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * All bot coordinates and templates are authored for [REF_WIDTH]×[REF_HEIGHT].
 * Scale to the active screen using proportional X/Y factors.
 */
object RefCoords {

    const val REF_WIDTH = 2560
    const val REF_HEIGHT = 1440

    fun activeScreenSize(): Pair<Int, Int> = ResolutionSettingsRepository.activeScreenSize()

    fun activeScreenSize(frame: Bitmap): Pair<Int, Int> = frame.width to frame.height

    fun scaleX(refX: Int, screenWidth: Int = activeScreenSize().first): Int {
        return (refX.toLong() * screenWidth / REF_WIDTH).toInt()
    }

    fun scaleY(refY: Int, screenHeight: Int = activeScreenSize().second): Int {
        return (refY.toLong() * screenHeight / REF_HEIGHT).toInt()
    }

    fun scalePoint(refX: Int, refY: Int, screenWidth: Int, screenHeight: Int): Pair<Int, Int> {
        return scaleX(refX, screenWidth) to scaleY(refY, screenHeight)
    }

    fun scalePoint(refX: Int, refY: Int): Pair<Int, Int> {
        val (w, h) = activeScreenSize()
        return scalePoint(refX, refY, w, h)
    }

    fun scalePoint(refX: Int, refY: Int, frame: Bitmap): Pair<Int, Int> {
        return scalePoint(refX, refY, frame.width, frame.height)
    }

    fun scaleRect(refRect: Rect, screenWidth: Int, screenHeight: Int): Rect {
        return Rect(
            scaleX(refRect.left, screenWidth),
            scaleY(refRect.top, screenHeight),
            scaleX(refRect.right, screenWidth),
            scaleY(refRect.bottom, screenHeight),
        )
    }

    fun scaleSwipe(swipe: SwipeCoords, screenWidth: Int, screenHeight: Int): SwipeCoords {
        return SwipeCoords(
            x1 = scaleX(swipe.x1, screenWidth),
            y1 = scaleY(swipe.y1, screenHeight),
            x2 = scaleX(swipe.x2, screenWidth),
            y2 = scaleY(swipe.y2, screenHeight),
            durationMs = swipe.durationMs,
            maxAttempts = swipe.maxAttempts,
        )
    }

    fun scaleSwipe(swipe: SwipeCoords): SwipeCoords {
        val (w, h) = activeScreenSize()
        return scaleSwipe(swipe, w, h)
    }

    fun scaleFactorX(screenWidth: Int): Float = screenWidth.toFloat() / REF_WIDTH

    fun scaleFactorY(screenHeight: Int): Float = screenHeight.toFloat() / REF_HEIGHT

    /** UI escala uniformemente en 16:9; usar el menor factor evita templates más grandes que el frame. */
    fun uniformScaleFactor(screenWidth: Int, screenHeight: Int): Float {
        return min(scaleFactorX(screenWidth), scaleFactorY(screenHeight))
    }

    /** Variaciones ±12% alrededor del factor teórico — el juego no reescala texto/UI de forma perfectamente lineal. */
    private val MULTI_SCALE_TOLERANCE = floatArrayOf(0.88f, 0.92f, 0.96f, 1.0f, 1.04f, 1.08f, 1.12f)

    fun templateScaleFactors(screenWidth: Int, screenHeight: Int): FloatArray {
        val base = uniformScaleFactor(screenWidth, screenHeight)
        if (abs(base - 1f) < 0.02f) {
            return floatArrayOf(1f)
        }
        return FloatArray(MULTI_SCALE_TOLERANCE.size) { i ->
            MULTI_SCALE_TOLERANCE[i] * base
        }
    }

    fun scaledTemplateSize(
        templateWidth: Int,
        templateHeight: Int,
        scale: Float,
    ): Pair<Int, Int> {
        val w = (templateWidth * scale).roundToInt().coerceAtLeast(1)
        val h = (templateHeight * scale).roundToInt().coerceAtLeast(1)
        return w to h
    }

    fun scaleTemplateSize(templateWidth: Int, templateHeight: Int, screenWidth: Int, screenHeight: Int): Pair<Int, Int> {
        val scale = uniformScaleFactor(screenWidth, screenHeight)
        return scaledTemplateSize(templateWidth, templateHeight, scale)
    }

    fun scaleTemplateBitmap(template: Bitmap, screenWidth: Int, screenHeight: Int): Bitmap {
        val scale = uniformScaleFactor(screenWidth, screenHeight)
        val (w, h) = scaledTemplateSize(template.width, template.height, scale)
        if (w == template.width && h == template.height) {
            return template
        }
        return Bitmap.createScaledBitmap(template, w, h, true)
    }
}
