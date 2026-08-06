package com.example.muamaizingbot.bot.maintenance

import android.graphics.Rect
import android.util.Log
import com.example.muamaizingbot.settings.BotTiming
import com.example.muamaizingbot.settings.BotTimingCategory
import com.example.muamaizingbot.vision.coord.RefCoords
import com.example.muamaizingbot.vision.navigation.NavigationVision
import kotlinx.coroutines.delay

/**
 * Top-right HUD icon rail (Store / VIP / Benefit / …).
 *
 * When expanded it can occlude the boss focus emblem; Farm Bosses must collapse
 * it before probing [com.example.muamaizingbot.bot.bosses.BossTargetingActions.hasBossFocus].
 *
 * Collapsed: [EXPAND_ARROW] (‹). Expanded: [STORE_ICON] and/or [COLLAPSE_ARROW] (›).
 */
object TopHudRailActions {

    private const val TAG = "TopHudRail"

    private const val EXPAND_ARROW = "templates/mu/ui/store/hud_expand_arrow.png"
    private const val COLLAPSE_ARROW = "templates/mu/ui/store/hud_expand_arrow_open.png"
    private const val STORE_ICON = "templates/mu/ui/store/hud_store_icon.png"

    private const val ARROW_THRESHOLD = 0.72f
    private const val STORE_THRESHOLD = 0.75f
    private const val COLLAPSE_ATTEMPTS = 3
    private const val POST_COLLAPSE_MS = 500L

    /** Fallback tap when collapse-arrow template misses @ 1280×720. */
    private const val COLLAPSE_FALLBACK_X_1280 = 1052
    private const val COLLAPSE_FALLBACK_Y_720 = 47

    /** Top-right band covering the rail + chevron. */
    fun railRoi(frameWidth: Int, frameHeight: Int): Rect {
        return Rect(
            (frameWidth * 0.55f).toInt().coerceIn(0, frameWidth),
            0,
            frameWidth,
            (frameHeight * 0.22f).toInt().coerceIn(0, frameHeight),
        )
    }

    private fun railRoi(): Rect {
        val (w, h) = RefCoords.activeScreenSize()
        return railRoi(w, h)
    }

    suspend fun isExpanded(): Boolean {
        val roi = railRoi()
        // Store icon is the reliable expanded signal (collapse-arrow can FP when closed).
        if (NavigationVision.findTemplate(STORE_ICON, STORE_THRESHOLD, roi) != null) {
            return true
        }
        val collapse = NavigationVision.findTemplate(COLLAPSE_ARROW, 0.88f, roi)
        val expand = NavigationVision.findTemplate(EXPAND_ARROW, ARROW_THRESHOLD, roi)
        return collapse != null && expand == null
    }

    /**
     * Collapse the top HUD rail if open. No-op (and silent) when already collapsed.
     * @return true when the rail looks collapsed after the call.
     */
    suspend fun ensureCollapsed(): Boolean {
        if (!isExpanded()) {
            return true
        }
        Log.d(TAG, "[HUD_RAIL] expanded — collapsing (blocks boss HUD)")
        repeat(COLLAPSE_ATTEMPTS) { attempt ->
            if (!tapCollapse()) {
                Log.w(TAG, "[HUD_RAIL] collapse tap failed attempt=${attempt + 1}")
            }
            delay(BotTiming.ms(POST_COLLAPSE_MS, BotTimingCategory.POST_TAP))
            if (!isExpanded()) {
                Log.d(TAG, "[HUD_RAIL] collapsed attempt=${attempt + 1}")
                return true
            }
        }
        Log.w(TAG, "[HUD_RAIL] still expanded after $COLLAPSE_ATTEMPTS attempts")
        val roi = railRoi()
        NavigationVision.logBestScore(STORE_ICON, roi)
        NavigationVision.logBestScore(COLLAPSE_ARROW, roi)
        NavigationVision.logBestScore(EXPAND_ARROW, roi)
        return false
    }

    private suspend fun tapCollapse(): Boolean {
        val roi = railRoi()
        val arrow = NavigationVision.findTemplate(COLLAPSE_ARROW, ARROW_THRESHOLD, roi)
        if (arrow != null) {
            Log.d(
                TAG,
                "[HUD_RAIL] collapse arrow score=${"%.3f".format(arrow.score)} " +
                    "at=(${arrow.centerX},${arrow.centerY})",
            )
            return NavigationVision.tapMatch(arrow)
        }
        val (w, h) = RefCoords.activeScreenSize()
        val x = COLLAPSE_FALLBACK_X_1280 * w / RefCoords.TARGET_WIDTH
        val y = COLLAPSE_FALLBACK_Y_720 * h / RefCoords.TARGET_HEIGHT
        Log.d(TAG, "[HUD_RAIL] collapse arrow miss — fallback tap=($x,$y)")
        return NavigationVision.tapScreen(x, y, label = "hud_rail_collapse")
    }
}
