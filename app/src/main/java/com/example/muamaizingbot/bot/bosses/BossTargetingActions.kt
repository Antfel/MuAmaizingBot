package com.example.muamaizingbot.bot.bosses

import android.graphics.Rect
import android.util.Log
import com.example.muamaizingbot.bot.maintenance.ElfBuffFocusHud
import com.example.muamaizingbot.bot.maintenance.TopHudRailActions
import com.example.muamaizingbot.vision.coord.RefCoords
import com.example.muamaizingbot.vision.navigation.NavigationVision
import com.example.muamaizingbot.vision.roi.MuCombatRois
import com.example.muamaizingbot.vision.roi.ScaledRoi
import kotlinx.coroutines.delay

/**
 * Acquire elite/boss focus via Focus Boss (skull).
 * Fight validation uses [BOSS_FOCUS] (circular top-bar icon; circularMask).
 *
 * [BOSS_FOCUS] is a lower-face crop of the circular emblem (eyes + chin). The top
 * horns are omitted so a nearby golden mob's nameplate ("letrero") that sits over
 * the emblem does not cause false negatives on acquire or mid-fight.
 */
object BossTargetingActions {

    private const val TAG = "FarmBosses"
    private const val FOCUS_BOSS = "templates/mu/ui/targeting/focus_elite_skull.png"
    /** Top boss-bar circular emblem (lower face) — stays while HP drops. */
    const val BOSS_FOCUS = "templates/mu/ui/targeting/boss_focus.png"
    private const val FOCUS_BOSS_THRESHOLD = 0.70f
    /** Slightly below 0.90 — mid-fight VFX / partial golden overlay dips score. */
    private const val BOSS_FOCUS_THRESHOLD = 0.85f
    private const val FALLBACK_BOSS_X_1280 = 1115
    private const val FALLBACK_BOSS_Y_720 = 656
    private const val MAX_ATTEMPTS = 4
    /** Post-skull settle before reading boss emblem (initial acquire). */
    private const val SETTLE_MS = 1_200L
    /** Faster settle for mid-fight re-acquire / post-kill confirm taps. */
    const val FAST_SETTLE_MS = 400L

    /**
     * Search box for [BOSS_FOCUS] authored at 1280×720:
     * (510,5)–(600,80). Converted to ref space for [ScaledRoi].
     */
    fun bossFocusRoi(frameWidth: Int, frameHeight: Int): Rect {
        return ScaledRoi.fromRefRect(
            left = RefCoords.unscaleX(510, RefCoords.TARGET_WIDTH),
            top = RefCoords.unscaleY(5, RefCoords.TARGET_HEIGHT),
            right = RefCoords.unscaleX(600, RefCoords.TARGET_WIDTH),
            bottom = RefCoords.unscaleY(80, RefCoords.TARGET_HEIGHT),
            frameWidth = frameWidth,
            frameHeight = frameHeight,
        )
    }

    private fun focusRoi(): Rect {
        val (w, h) = RefCoords.activeScreenSize()
        return bossFocusRoi(w, h)
    }

    /** True while the boss focus emblem is visible (circularMask match). */
    suspend fun hasBossFocus(): Boolean {
        // Expanded Store/VIP rail overlaps the top boss emblem ROI.
        TopHudRailActions.ensureCollapsed()
        val match = NavigationVision.findTemplate(
            BOSS_FOCUS,
            BOSS_FOCUS_THRESHOLD,
            roi = focusRoi(),
        )
        if (match != null) {
            Log.d(
                TAG,
                "[BOSS] boss_focus at=(${match.centerX},${match.centerY}) " +
                    "score=${"%.3f".format(match.score)}",
            )
            return true
        }
        NavigationVision.logBestScore(BOSS_FOCUS, focusRoi())
        return false
    }

    /**
     * Ensure boss focus HUD is up (skull → confirm via [hasBossFocus]).
     * @param maxAttempts skull taps before giving up (use 1 mid-fight).
     * @param settleMs wait after each skull tap before reading the emblem.
     * @param includeGolden same elite skull path; reserved for future golden-specific templates.
     */
    suspend fun ensureFocusBoss(
        includeGolden: Boolean = false,
        maxAttempts: Int = MAX_ATTEMPTS,
        settleMs: Long = SETTLE_MS,
    ): Boolean {
        if (includeGolden) {
            Log.d(TAG, "[BOSS] include_golden_mobs=true (elite skull path)")
        }
        if (hasBossFocus()) {
            Log.d(TAG, "[BOSS] focus already active")
            return true
        }
        // Ally/green leftover — drop then re-acquire.
        if (ElfBuffFocusHud.isGreenHpBarVisible()) {
            Log.d(TAG, "[BOSS] green focus present; clearing before acquire")
            ElfBuffFocusHud.clearFocus()
            delay(settleMs)
        }

        val attempts = maxAttempts.coerceAtLeast(1)
        val settle = settleMs.coerceAtLeast(0L)
        repeat(attempts) { attempt ->
            if (!tapFocusBossSkull()) {
                Log.w(TAG, "[BOSS] Focus Boss tap failed attempt=${attempt + 1}")
            }
            delay(settle)
            if (hasBossFocus()) {
                Log.d(TAG, "[BOSS] focus acquired attempt=${attempt + 1}")
                return true
            }
            Log.d(TAG, "[BOSS] no boss_focus after acquire attempt=${attempt + 1}")
            NavigationVision.logBestScore(BOSS_FOCUS, focusRoi())
        }
        return false
    }

    private suspend fun tapFocusBossSkull(): Boolean {
        val (w, h) = RefCoords.activeScreenSize()
        val barRoi = MuCombatRois.targetingHudRoi(w, h)
        val match = NavigationVision.findTemplate(
            FOCUS_BOSS,
            FOCUS_BOSS_THRESHOLD,
            roi = barRoi,
        )
        if (match != null) {
            Log.d(
                TAG,
                "[BOSS] Focus Boss at=(${match.centerX},${match.centerY}) " +
                    "score=${"%.3f".format(match.score)}",
            )
            return NavigationVision.tapScreen(match.centerX, match.centerY, label = "focus_boss")
        }
        NavigationVision.logBestScore(FOCUS_BOSS, barRoi)
        val x = FALLBACK_BOSS_X_1280 * w / 1280
        val y = FALLBACK_BOSS_Y_720 * h / 720
        Log.w(TAG, "[BOSS] Focus Boss miss — fallback tap=($x,$y)")
        return NavigationVision.tapScreen(x, y, label = "focus_boss_fallback")
    }
}
