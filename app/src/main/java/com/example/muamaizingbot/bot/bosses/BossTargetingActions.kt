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
 * Fight validation is a template score on [BOSS_FOCUS] (circularMask).
 * Boss and golden share that emblem. Other players stay on
 * [com.example.muamaizingbot.vision.focus.FocusPortraitClassifier].
 */
object BossTargetingActions {

    private const val TAG = "FarmBosses"
    /** Top-bar circular emblem — stays while HP drops. Boss and golden share it. */
    const val BOSS_FOCUS = "templates/mu/ui/targeting/boss_focus.png"
    /** Real emblem sits ~0.99; empty ground and PJ portraits stay under ~0.89. */
    private const val BOSS_FOCUS_THRESHOLD = 0.90f
    private const val MAX_ATTEMPTS = 4
    /** Post-skull settle before reading boss emblem (initial acquire). */
    private const val SETTLE_MS = 1_200L
    /** Faster settle for mid-fight re-acquire / post-kill confirm taps. */
    const val FAST_SETTLE_MS = 400L

    /**
     * Search box for [BOSS_FOCUS] authored at 1280×720: (510,5)–(600,80).
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

    /** True while the boss/golden emblem matches [BOSS_FOCUS] at [BOSS_FOCUS_THRESHOLD]. */
    suspend fun hasBossFocus(): Boolean {
        // Expanded Store/VIP rail overlaps the top boss emblem ROI.
        TopHudRailActions.ensureCollapsed()
        val (w, h) = RefCoords.activeScreenSize()
        val roi = bossFocusRoi(w, h)
        // Threshold 0 asks for the best peak so a miss still logs its score.
        val match = NavigationVision.findTemplate(BOSS_FOCUS, 0f, roi = roi)
        val score = match?.score ?: 0f
        val hit = score >= BOSS_FOCUS_THRESHOLD
        if (match != null) {
            Log.d(
                TAG,
                "[BOSS] boss_focus at=(${match.centerX},${match.centerY}) " +
                    "score=${"%.3f".format(score)} hit=$hit",
            )
        } else {
            Log.d(TAG, "[BOSS] boss_focus score=0.000 hit=false")
        }
        return hit
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
        }
        return false
    }

    private suspend fun tapFocusBossSkull(): Boolean {
        val (w, h) = RefCoords.activeScreenSize()
        val (x, y) = MuCombatRois.focusBossTap(w, h)
        Log.d(TAG, "[BOSS] Focus Boss tap=($x,$y)")
        return NavigationVision.tapScreen(x, y, label = "focus_boss")
    }
}
