package com.example.muamaizingbot.vision.navigation

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.example.muamaizingbot.capture.ScreenCaptureManager
import com.example.muamaizingbot.maps.SwipeCoords
import com.example.muamaizingbot.vision.BitmapRegionSimilarity
import com.example.muamaizingbot.vision.roi.ScaledRoi
import kotlinx.coroutines.delay

object ScrollSettleWait {

    private const val TAG = "ScrollSettle"
    private const val STABILITY_THRESHOLD = 0.985f
    private const val STABILITY_SAMPLES = 2
    private const val STABILITY_INTERVAL_MS = 200L
    private const val STABILITY_TIMEOUT_MS = 6000L

    suspend fun waitForMapListSettled(swipe: SwipeCoords? = null): Boolean {
        return waitForRegionSettled(
            label = if (swipe != null && swipe.x1 > 700) "wire_popup" else "map_list",
            regionOf = { frame -> cropListRegion(frame, swipe) },
        )
    }

    suspend fun waitForRegionSettled(
        label: String,
        regionOf: (Bitmap) -> Bitmap?,
        timeoutMs: Long = STABILITY_TIMEOUT_MS,
    ): Boolean {
        var stableCount = 0
        val deadline = System.currentTimeMillis() + timeoutMs

        while (System.currentTimeMillis() < deadline) {
            val frameA = ScreenCaptureManager.getLatestBitmap() ?: break
            val regionA = regionOf(frameA)
            frameA.recycle()

            delay(STABILITY_INTERVAL_MS)

            val frameB = ScreenCaptureManager.getLatestBitmap() ?: break
            val regionB = regionOf(frameB)
            frameB.recycle()

            val similarity = BitmapRegionSimilarity.compare(regionA, regionB)
            regionA?.recycle()
            regionB?.recycle()

            if (similarity >= STABILITY_THRESHOLD) {
                stableCount++
                if (stableCount >= STABILITY_SAMPLES) {
                    Log.d(TAG, "[SETTLED] label=$label similarity=$similarity")
                    return true
                }
            } else {
                stableCount = 0
            }
        }

        Log.w(TAG, "[SETTLED] timeout label=$label")
        return false
    }

    fun cropListRegion(frame: Bitmap, swipe: SwipeCoords?): Bitmap? {
        val rect = listRegionRect(swipe, frame.width, frame.height)
        val width = rect.width()
        val height = rect.height()
        if (width <= 0 || height <= 0) {
            return null
        }
        return Bitmap.createBitmap(frame, rect.left, rect.top, width, height)
    }

    fun listRegionRect(swipe: SwipeCoords?, frameWidth: Int, frameHeight: Int): Rect {
        if (swipe != null) {
            val top = minOf(swipe.y1, swipe.y2) - 350
            val bottom = maxOf(swipe.y1, swipe.y2) + 350
            return ScaledRoi.fromRefRect(
                swipe.x1 - 250,
                top,
                swipe.x1 + 250,
                bottom,
                frameWidth,
                frameHeight,
            )
        }
        return ScaledRoi.fromRefRect(400, 350, 900, 950, frameWidth, frameHeight)
    }
}
