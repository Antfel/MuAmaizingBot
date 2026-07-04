package com.example.muamaizingbot.bot.navigation

import android.util.Log
import com.example.muamaizingbot.maps.MapDefinition
import com.example.muamaizingbot.vision.navigation.NavigationVision

object MapWindowActions {

    private const val TAG = "MapWindow"
    private const val MAP_BUTTON_X = 2440
    private const val MAP_BUTTON_Y = 120
    private const val MAP_WINDOW_THRESHOLD = 0.8f

    const val MAP_WINDOW_OPEN = "templates/mu/ui/common/map_window_open.png"
    const val CLOSE_X = "templates/mu/ui/common/close_x.png"

    suspend fun ensureMapWindowOpen(
        waitForWorldReady: MapDefinition? = null,
        retries: Int = 3,
        timeoutMs: Long = 5000,
    ): Boolean {
        if (waitForWorldReady != null) {
            NavigationWaitActions.waitUntilWorldReady(waitForWorldReady)
        }
        if (isMapWindowOpen()) {
            Log.d(TAG, "[MAP] window already open")
            return true
        }
        return openMapWindow(retries = retries, timeoutMs = timeoutMs)
    }

    suspend fun isMapWindowOpen(): Boolean {
        return NavigationVision.findTemplate(MAP_WINDOW_OPEN, MAP_WINDOW_THRESHOLD) != null
    }

    suspend fun openMapWindow(
        retries: Int = 3,
        timeoutMs: Long = 5000,
        waitForWorldReady: MapDefinition? = null,
    ): Boolean {
        if (waitForWorldReady != null) {
            NavigationWaitActions.waitUntilWorldReady(waitForWorldReady)
        }

        dismissVisiblePopup()

        repeat(retries) { attempt ->
            Log.d(TAG, "[MAP] open attempt=${attempt + 1}/$retries")
            if (!NavigationVision.tap(MAP_BUTTON_X, MAP_BUTTON_Y)) {
                Log.w(TAG, "[MAP] map button tap failed")
            }
            if (waitUntilMapWindowOpen(timeoutMs)) {
                Log.d(TAG, "[MAP] window open")
                return true
            }
            Log.w(TAG, "[MAP] open failed attempt=${attempt + 1}")
        }
        Log.e(TAG, "[MAP] open failed after retries")
        return false
    }

    suspend fun closeMapWindowIfOpen(): Boolean {
        if (!isMapWindowOpen()) {
            Log.d(TAG, "[MAP] close skipped, already closed")
            return true
        }
        return closeMapWindow()
    }

    suspend fun closeMapWindow(): Boolean {
        Log.d(TAG, "[MAP] close started")
        if (!NavigationVision.tapTemplate(CLOSE_X, MAP_WINDOW_THRESHOLD)) {
            Log.w(TAG, "[MAP] close_x not found")
            return false
        }
        val closed = waitUntilMapWindowClosed(5000)
        Log.d(TAG, "[MAP] close result=$closed")
        return closed
    }

    suspend fun waitUntilMapWindowOpen(timeoutMs: Long): Boolean {
        return NavigationVision.waitForTemplate(
            assetPath = MAP_WINDOW_OPEN,
            threshold = MAP_WINDOW_THRESHOLD,
            timeoutMs = timeoutMs,
        ) != null
    }

    suspend fun waitUntilMapWindowClosed(timeoutMs: Long): Boolean {
        return NavigationVision.waitUntilAbsent(
            assetPath = MAP_WINDOW_OPEN,
            threshold = MAP_WINDOW_THRESHOLD,
            timeoutMs = timeoutMs,
        )
    }

    private suspend fun dismissVisiblePopup() {
        val match = NavigationVision.findTemplate(CLOSE_X, MAP_WINDOW_THRESHOLD) ?: return
        NavigationVision.tapMatch(match)
        NavigationVision.waitUntilAbsent(CLOSE_X, MAP_WINDOW_THRESHOLD, 1500)
        Log.d(TAG, "[MAP] dismissed popup")
    }
}
