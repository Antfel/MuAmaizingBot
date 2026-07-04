package com.example.muamaizingbot.overlay

import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager

object OverlayBounds {

    fun displaySize(windowManager: WindowManager): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            return bounds.width() to bounds.height()
        }
        @Suppress("DEPRECATION")
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        return metrics.widthPixels to metrics.heightPixels
    }

    /** Left edge, vertically centered. */
    fun defaultPosition(
        screenWidth: Int,
        screenHeight: Int,
        overlayWidth: Int,
        overlayHeight: Int,
        marginPx: Int = 0,
    ): Pair<Int, Int> {
        val x = marginPx
        val y = ((screenHeight - overlayHeight) / 2).coerceAtLeast(0)
        return clamp(x, y, screenWidth, screenHeight, overlayWidth, overlayHeight)
    }

    fun clamp(
        x: Int,
        y: Int,
        screenWidth: Int,
        screenHeight: Int,
        overlayWidth: Int,
        overlayHeight: Int,
    ): Pair<Int, Int> {
        val maxX = (screenWidth - overlayWidth).coerceAtLeast(0)
        val maxY = (screenHeight - overlayHeight).coerceAtLeast(0)
        return x.coerceIn(0, maxX) to y.coerceIn(0, maxY)
    }
}
