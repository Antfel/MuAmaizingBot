package com.example.muamaizingbot.bot.navigation

import android.graphics.Rect
import android.util.Log
import com.example.muamaizingbot.bot.disconnect.DisconnectDetector
import com.example.muamaizingbot.maps.MapDefinition
import com.example.muamaizingbot.settings.BotTiming
import com.example.muamaizingbot.settings.BotTimingCategory
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
        DisconnectDetector.markBusy("map-window")
        if (waitForWorldReady != null) {
            NavigationWaitActions.waitUntilWorldReady(waitForWorldReady)
        }
        WireSwitchActions.ensureChatClosed()
        if (hasMapTabChrome()) {
            Log.d(TAG, "[MAP] window already open")
            return true
        }
        return openMapWindow(retries = retries, timeoutMs = timeoutMs)
    }

    /**
     * Any top-right panel that looks like map chrome (Map tab **or** close_x).
     * Prefer [hasMapTabChrome] before scrolling the map list — chat/store also have close_x.
     */
    suspend fun isMapWindowOpen(): Boolean {
        if (hasMapTabChrome()) {
            return true
        }
        return isMapPanelOpenViaCloseButton()
    }

    /** True only when the left "Map" tab template is visible — not close_x alone. */
    suspend fun hasMapTabChrome(): Boolean {
        return NavigationVision.findTemplate(
            MAP_WINDOW_OPEN,
            NavigationTemplateThresholds.mapWindow(),
            mapHeaderRoi(),
        ) != null
    }

    /**
     * Ready to swipe the world map list: chat closed and Map tab visible.
     * Reopens the map if a false "open" (close_x-only / chat) left us mid-nav.
     */
    suspend fun ensureMapListReadyForScroll(retries: Int = 2): Boolean {
        DisconnectDetector.markBusy("map-window")
        if (WireSwitchActions.ensureChatClosed()) {
            Log.w(TAG, "[MAP] closed chat before list scroll")
        }
        if (hasMapTabChrome()) {
            return true
        }
        if (isMapPanelOpenViaCloseButton()) {
            Log.w(TAG, "[MAP] close_x without Map tab — dismiss before reopen")
            dismissPanelViaCloseButton()
        }
        Log.w(TAG, "[MAP] list not ready — opening map window")
        if (!openMapWindow(retries = retries, timeoutMs = 5000)) {
            return false
        }
        return hasMapTabChrome()
    }

    suspend fun openMapWindow(
        retries: Int = 3,
        timeoutMs: Long = 5000,
        waitForWorldReady: MapDefinition? = null,
    ): Boolean {
        DisconnectDetector.markBusy("map-window")
        if (waitForWorldReady != null) {
            NavigationWaitActions.waitUntilWorldReady(waitForWorldReady)
        }

        // Map button sits near chat — close chat first so a miss is not left open.
        if (WireSwitchActions.ensureChatClosed()) {
            Log.w(TAG, "[MAP] closed chat before map open")
        }

        // Skip tap only on real Map tab. close_x alone is often chat/store.
        if (hasMapTabChrome()) {
            Log.d(TAG, "[MAP] window already open (skip tap)")
            return true
        }
        if (isMapPanelOpenViaCloseButton()) {
            Log.w(TAG, "[MAP] close_x without Map tab — dismiss then open")
            dismissPanelViaCloseButton()
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
            // Map button sits near chat — a miss often opens chat instead.
            if (WireSwitchActions.ensureChatClosed()) {
                Log.w(TAG, "[MAP] closed chat after failed map open — retry")
            }
        }
        Log.e(TAG, "[MAP] open failed after retries")
        WireSwitchActions.ensureChatClosed()
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
            NavigationVision.logBestScore(CLOSE_X)
            Log.w(TAG, "[MAP] close_x not found")
            return false
        }
        val closed = waitUntilMapWindowClosed(5000)
        Log.d(TAG, "[MAP] close result=$closed")
        return closed
    }

    suspend fun waitUntilMapWindowOpen(timeoutMs: Long): Boolean {
        val effectiveTimeout = BotTiming.ms(timeoutMs, BotTimingCategory.SCREEN_LOAD)
        val roi = mapHeaderRoi()
        val threshold = NavigationTemplateThresholds.mapWindow()
        // Require Map tab chrome — close_x alone matches chat/store and caused blind list scrolls.
        return NavigationVision.waitForTemplate(
            assetPath = MAP_WINDOW_OPEN,
            threshold = threshold,
            timeoutMs = effectiveTimeout,
            roi = roi,
        ) != null
    }

    suspend fun waitUntilMapWindowClosed(timeoutMs: Long): Boolean {
        return NavigationVision.waitUntilAbsent(
            assetPath = MAP_WINDOW_OPEN,
            threshold = NavigationTemplateThresholds.mapWindow(),
            timeoutMs = BotTiming.ms(timeoutMs, BotTimingCategory.SCREEN_LOAD),
        )
    }

    /** Only dismiss when a map/panel UI is actually open — avoids HUD false close_x taps. */
    private suspend fun dismissVisiblePopup() {
        if (!isMapWindowOpen()) {
            return
        }
        dismissPanelViaCloseButton()
    }

    private suspend fun dismissPanelViaCloseButton() {
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

    /**
     * Top band of the map panel: left "Map" tab + title/close chrome.
     * Must include the left tab — [MAP_WINDOW_OPEN] is the "Map" label there @ 1280×720.
     */
    private fun mapHeaderRoi(): Rect {
        val (screenW, screenH) = com.example.muamaizingbot.capture.ScreenCaptureManager.peekLatestBitmapSize()
            ?: RefCoords.activeScreenSize()
        return Rect(
            0,
            0,
            screenW,
            RefCoords.scaleY(420, screenH),
        )
    }

    private suspend fun isMapPanelOpenViaCloseButton(): Boolean {
        val close = NavigationVision.findTemplate(CLOSE_X, NavigationTemplateThresholds.closeX()) ?: return false
        return isLikelyPanelCloseButton(close.centerX, close.centerY)
    }
}
