package com.example.muamaizingbot.vision

import android.graphics.Bitmap
import kotlin.math.abs

object BitmapRegionSimilarity {

    fun compare(a: Bitmap?, b: Bitmap?): Float {
        if (a == null || b == null) {
            return 0f
        }
        val width = minOf(a.width, b.width)
        val height = minOf(a.height, b.height)
        if (width <= 0 || height <= 0) {
            return 0f
        }

        var diffSum = 0L
        var count = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixelA = a.getPixel(x, y)
                val pixelB = b.getPixel(x, y)
                diffSum += abs(((pixelA shr 16) and 0xFF) - ((pixelB shr 16) and 0xFF))
                diffSum += abs(((pixelA shr 8) and 0xFF) - ((pixelB shr 8) and 0xFF))
                diffSum += abs((pixelA and 0xFF) - (pixelB and 0xFF))
                count += 3
            }
        }
        if (count == 0) {
            return 0f
        }
        val meanDiff = diffSum.toFloat() / count / 255f
        return 1f - meanDiff
    }
}
