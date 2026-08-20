package com.example.muamaizingbot.bot.maintenance

import android.graphics.Rect
import android.util.Log
import com.example.muamaizingbot.vision.coord.RefCoords
import com.example.muamaizingbot.vision.focus.FocusPortraitClassifier
import com.example.muamaizingbot.vision.navigation.NavigationVision
import com.example.muamaizingbot.vision.roi.MuCombatRois
import com.example.muamaizingbot.vision.roi.ScaledRoi
import com.example.muamaizingbot.vision.template.PcTemplateMatchResult
import kotlinx.coroutines.delay

/**
 * Target-focus HUD (top center).
 * Under PK All the HP bar is red; under Union an ally's bar turns green.
 * Giver mode: clear via Focus Boss template. War mode: hard tap @ (516,26).
 *
 * Combat-focus (farm / farm_bosses):
 * - Boss → [com.example.muamaizingbot.bot.bosses.BossTargetingActions.hasBossFocus]
 *   ([FocusPortraitClassifier] boss class; golden uses the same emblem)
 * - Enemy PJ → [isEnemyFocusVisible] / [isClearXVisible] → portrait class PJ
 */
object ElfBuffFocusHud {

    private const val TAG = "ElfBuffCast"

    private const val HP_BAR_RED = "templates/mu/ui/focus_hp_bar.png"
    private const val HP_BAR_GREEN = "templates/mu/ui/focus_hp_bar_green.png"
    private const val HP_BAR_THRESHOLD = 0.80f

    /** @deprecated Kept for asset/ROI reference; combat no longer matches this template. */
    const val FOCUS_CLEAR_X = "templates/mu/ui/focus_clear_x.png"
    /** Bench: true focus ~0.87; terrain FP ~0.78 — keep above FP band. */
    private const val FOCUS_CLEAR_X_THRESHOLD = 0.85f

    private const val FOCUS_BOSS = "templates/mu/ui/targeting/focus_elite_skull.png"
    private const val FOCUS_BOSS_THRESHOLD = 0.70f

    /** Fallback Focus Boss tap @ 1280×720 if template miss (giver). */
    private const val FALLBACK_BOSS_X_1280 = 1115
    private const val FALLBACK_BOSS_Y_720 = 656

    /** War: close focus HUD with hard tap (X button) @ 1280×720. */
    private const val WAR_CLEAR_X_1280 = 516
    private const val WAR_CLEAR_Y_720 = 26
    private const val WAR_CLEAR_VERIFY_ATTEMPTS = 8
    private const val WAR_CLEAR_VERIFY_POLL_MS = 200L

    /**
     * Search box for [FOCUS_CLEAR_X] authored at 1280×720, centered on the
     * War clear tap (516,26): (488,0)–(544,54). Pad ≈±28 around the 23×24 icon.
     * Distinct from [com.example.muamaizingbot.bot.bosses.BossTargetingActions.bossFocusRoi].
     */
    private const val CLEAR_X_ROI_LEFT_1280 = 488
    private const val CLEAR_X_ROI_TOP_1280 = 0
    private const val CLEAR_X_ROI_RIGHT_1280 = 544
    private const val CLEAR_X_ROI_BOTTOM_1280 = 54

    enum class HpBarColor { RED, GREEN }

    fun hudRoi(frameWidth: Int, frameHeight: Int): Rect {
        return ScaledRoi.fromRefRect(
            left = 500,
            top = 20,
            right = 2100,
            bottom = 360,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
        )
    }

    /** Tight ROI around the player-focus clear (X) button. */
    fun clearXRoi(frameWidth: Int, frameHeight: Int): Rect {
        return ScaledRoi.fromRefRect(
            left = RefCoords.unscaleX(CLEAR_X_ROI_LEFT_1280, RefCoords.TARGET_WIDTH),
            top = RefCoords.unscaleY(CLEAR_X_ROI_TOP_1280, RefCoords.TARGET_HEIGHT),
            right = RefCoords.unscaleX(CLEAR_X_ROI_RIGHT_1280, RefCoords.TARGET_WIDTH),
            bottom = RefCoords.unscaleY(CLEAR_X_ROI_BOTTOM_1280, RefCoords.TARGET_HEIGHT),
            frameWidth = frameWidth,
            frameHeight = frameHeight,
        )
    }

    private fun roi(): Rect {
        val (w, h) = RefCoords.activeScreenSize()
        return hudRoi(w, h)
    }

    private fun clearXSearchRoi(): Rect {
        val (w, h) = RefCoords.activeScreenSize()
        return clearXRoi(w, h)
    }

    suspend fun findRedHpBar(): PcTemplateMatchResult? {
        return NavigationVision.findTemplate(HP_BAR_RED, HP_BAR_THRESHOLD, roi())
    }

    suspend fun findGreenHpBar(): PcTemplateMatchResult? {
        return NavigationVision.findTemplate(HP_BAR_GREEN, HP_BAR_THRESHOLD, roi())
    }

    suspend fun findClearX(): PcTemplateMatchResult? {
        return NavigationVision.findTemplate(
            FOCUS_CLEAR_X,
            FOCUS_CLEAR_X_THRESHOLD,
            clearXSearchRoi(),
        )
    }

    suspend fun isRedHpBarVisible(): Boolean {
        val match = findRedHpBar()
        if (match != null) {
            Log.d(
                TAG,
                "[ELF_GIVER] focus HP red at=(${match.centerX},${match.centerY}) " +
                    "score=${"%.3f".format(match.score)}",
            )
            return true
        }
        return false
    }

    suspend fun isGreenHpBarVisible(): Boolean {
        val match = findGreenHpBar()
        if (match != null) {
            Log.d(
                TAG,
                "[ELF_GIVER] focus HP green at=(${match.centerX},${match.centerY}) " +
                    "score=${"%.3f".format(match.score)}",
            )
            return true
        }
        return false
    }

    /**
     * True while the focus HUD portrait is a player (PJ).
     * Name kept for call sites that previously keyed off the clear-X template.
     */
    suspend fun isClearXVisible(): Boolean {
        val kind = FocusPortraitClassifier.classifyLatest()
        val hit = kind == FocusPortraitClassifier.Kind.PJ
        Log.d(TAG, "[FOCUS] pj_portrait=$kind hit=$hit")
        return hit
    }

    /**
     * Enemy / PJ focus panel via portrait classifier (not red-bar / clear-X templates).
     * Boss HUD classifies as [FocusPortraitClassifier.Kind.BOSS], so it does not
     * trip this path.
     */
    suspend fun isEnemyFocusVisible(): Boolean {
        return isClearXVisible()
    }

    /** Any focus HUD (red under All, or green ally under Union). */
    suspend fun isFocusHudVisible(): Boolean {
        if (isRedHpBarVisible() || isGreenHpBarVisible()) return true
        NavigationVision.logBestScore(HP_BAR_RED, roi())
        NavigationVision.logBestScore(HP_BAR_GREEN, roi())
        return false
    }

    /**
     * After switching to Union:
     * GREEN = ally, RED = still hostile / not ally, null = no HUD.
     * Uses a single capture for both templates (War mid-cross / tick classify).
     * Bar-only — war does not use the portrait gate.
     */
    suspend fun classifyUnionFocus(): HpBarColor? {
        val frame = NavigationVision.captureFrame() ?: return null
        return try {
            val green = NavigationVision.findOnFrame(frame, HP_BAR_GREEN, HP_BAR_THRESHOLD, roi())
            if (green != null) {
                Log.d(
                    TAG,
                    "[ELF_GIVER] focus HP green at=(${green.centerX},${green.centerY}) " +
                        "score=${"%.3f".format(green.score)}",
                )
                return HpBarColor.GREEN
            }
            val red = NavigationVision.findOnFrame(frame, HP_BAR_RED, HP_BAR_THRESHOLD, roi())
            if (red != null) {
                Log.d(
                    TAG,
                    "[ELF_GIVER] focus HP red at=(${red.centerX},${red.centerY}) " +
                        "score=${"%.3f".format(red.score)}",
                )
                return HpBarColor.RED
            }
            null
        } finally {
            frame.recycle()
        }
    }

    /**
     * Drop current focus HUD by tapping Focus Boss (skull). Used by giver mode.
     */
    suspend fun clearFocus(): Boolean {
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
                "[ELF_GIVER] unfocus via Focus Boss at=(${match.centerX},${match.centerY}) " +
                    "score=${"%.3f".format(match.score)}",
            )
            return NavigationVision.tapScreen(match.centerX, match.centerY, label = "unfocus_boss")
        }

        NavigationVision.logBestScore(FOCUS_BOSS, barRoi)
        val x = FALLBACK_BOSS_X_1280 * w / 1280
        val y = FALLBACK_BOSS_Y_720 * h / 720
        Log.w(TAG, "[ELF_GIVER] Focus Boss miss — fallback tap=($x,$y)")
        return NavigationVision.tapScreen(x, y, label = "unfocus_boss_fallback")
    }

    /**
     * War: close focus HUD with hard tap at (516,26) @ 1280×720 (X on target panel),
     * then confirm the focus HUD is gone.
     */
    suspend fun clearFocusHardTapAndVerify(): Boolean {
        val (w, h) = RefCoords.activeScreenSize()
        val x = WAR_CLEAR_X_1280 * w / 1280
        val y = WAR_CLEAR_Y_720 * h / 720
        Log.d(TAG, "[WAR] unfocus hard tap screen=($x,$y) ref=($WAR_CLEAR_X_1280,$WAR_CLEAR_Y_720)")
        if (!NavigationVision.tapScreen(x, y, label = "unfocus_hard")) {
            Log.w(TAG, "[WAR] unfocus hard tap failed")
            return false
        }
        repeat(WAR_CLEAR_VERIFY_ATTEMPTS) { attempt ->
            delay(WAR_CLEAR_VERIFY_POLL_MS)
            if (classifyUnionFocus() == null) {
                Log.d(TAG, "[WAR] focus HUD cleared after hard tap (poll=${attempt + 1})")
                return true
            }
        }
        Log.w(TAG, "[WAR] focus HUD still visible after hard tap")
        return false
    }
}
