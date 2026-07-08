package com.example.muamaizingbot.bot.navigation

import android.graphics.Rect
import android.util.Log
import com.example.muamaizingbot.maps.MapDefinition
import com.example.muamaizingbot.vision.coord.RefCoords
import com.example.muamaizingbot.vision.navigation.NavigationVision

object MapWindowActions {

    private const val TAG = "MapWindow"
    private const val MAP_BUTTON_X = 2440
    private const val MAP_BUTTON_Y = 120

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
        if (NavigationVision.findTemplate(
                MAP_WINDOW_OPEN,
                NavigationTemplateThresholds.mapWindow(),
                mapHeaderRoi(),
            ) != null
        ) {
            return true
        }
        return isMapPanelOpenViaCloseButton()
    }

    suspend fun openMapWindow(
        retries: Int = 3,
        timeoutMs: Long = 5000,
        waitForWorldReady: MapDefinition? = null,
    ): Boolean {
        if (waitForWorldReady != null) {
            NavigationWaitActions.waitUntilWorldReady(waitForWorldReady)
        }

        if (isMapWindowOpen()) {
            Log.d(TAG, "[MAP] window already open (skip tap)")
            return true
        }

        dismissVisiblePopup()

        repeat(retries) { attempt ->
            Log.d(TAG, "[MAP] open attempt=${attempt + 1}/$retries")
            val (tapX, tapY) = RefCoords.scalePoint(MAP_BUTTON_X, MAP_BUTTON_Y)
            Log.d(TAG, "[MAP] map button tap ref=($MAP_BUTTON_X,$MAP_BUTTON_Y) screen=($tapX,$tapY)")
            if (!NavigationVision.tap(MAP_BUTTON_X, MAP_BUTTON_Y)) {
                Log.w(TAG, "[MAP] map button tap failed")
            }
            if (waitUntilMapWindowOpen(timeoutMs)) {
                Log.d(TAG, "[MAP] window open")
                return true
            }
            NavigationVision.logBestScore(MAP_WINDOW_OPEN, mapHeaderRoi())
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
        if (!NavigationVision.tapTemplate(CLOSE_X, NavigationTemplateThresholds.closeX())) {
            Log.w(TAG, "[MAP] close_x not found")
            return false
        }
        val closed = waitUntilMapWindowClosed(5000)
        Log.d(TAG, "[MAP] close result=$closed")
        return closed
    }

    suspend fun waitUntilMapWindowOpen(timeoutMs: Long): Boolean {
        val roi = mapHeaderRoi()
        val threshold = NavigationTemplateThresholds.mapWindow()
        if (NavigationVision.waitForTemplate(
                assetPath = MAP_WINDOW_OPEN,
                threshold = threshold,
                timeoutMs = timeoutMs,
                roi = roi,
            ) != null
        ) {
            return true
        }
        return waitUntilMapPanelOpenViaCloseButton(timeoutMs.coerceAtMost(1500))
    }

    suspend fun waitUntilMapWindowClosed(timeoutMs: Long): Boolean {
        return NavigationVision.waitUntilAbsent(
            assetPath = MAP_WINDOW_OPEN,
            threshold = NavigationTemplateThresholds.mapWindow(),
            timeoutMs = timeoutMs,
        )
    }

    /** Only dismiss when a map/panel UI is actually open — avoids HUD false close_x taps. */
    private suspend fun dismissVisiblePopup() {
        if (!isMapWindowOpen()) {
            return
        }
        val match = NavigationVision.findTemplate(CLOSE_X, NavigationTemplateThresholds.closeX()) ?: return
        if (!isLikelyPanelCloseButton(match.centerX, match.centerY)) {
            Log.d(TAG, "[MAP] skip dismiss; close_x outside panel region at=(${match.centerX},${match.centerY})")
            return
        }
        NavigationVision.tapMatch(match)
        NavigationVision.waitUntilAbsent(CLOSE_X, NavigationTemplateThresholds.closeX(), 1500)
        Log.d(TAG, "[MAP] dismissed popup")
    }

    fun isLikelyPanelCloseButton(screenX: Int, screenY: Int): Boolean {
        val (screenW, screenH) = com.example.muamaizingbot.capture.ScreenCaptureManager.peekLatestBitmapSize()
            ?: RefCoords.activeScreenSize()
        val minX = RefCoords.scaleX(1700, screenW)
        val maxY = RefCoords.scaleY(420, screenH)
        return screenX >= minX && screenY <= maxY
    }

    /** Upper band where the zone map title bar and close button live. */
    private fun mapHeaderRoi(): Rect {
        val (screenW, screenH) = com.example.muamaizingbot.capture.ScreenCaptureManager.peekLatestBitmapSize()
            ?: RefCoords.activeScreenSize()
        return Rect(
            RefCoords.scaleX(1500, screenW),
            0,
            screenW,
            RefCoords.scaleY(420, screenH),
        )
    }

    private suspend fun isMapPanelOpenViaCloseButton(): Boolean {
        val close = NavigationVision.findTemplate(CLOSE_X, NavigationTemplateThresholds.closeX()) ?: return false
        return isLikelyPanelCloseButton(close.centerX, close.centerY)
    }

    private suspend fun waitUntilMapPanelOpenViaCloseButton(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (isMapPanelOpenViaCloseButton()) {
                Log.d(TAG, "[MAP] panel open via close_x fallback")
                return true
            }
            kotlinx.coroutines.delay(200)
        }
        return false
    }
}
