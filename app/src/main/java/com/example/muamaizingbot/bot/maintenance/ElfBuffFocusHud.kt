package com.example.muamaizingbot.bot.maintenance

import android.graphics.Rect
import android.util.Log
import com.example.muamaizingbot.vision.coord.RefCoords
import com.example.muamaizingbot.vision.navigation.NavigationVision
import com.example.muamaizingbot.vision.roi.MuCombatRois
import com.example.muamaizingbot.vision.roi.ScaledRoi
import com.example.muamaizingbot.vision.template.PcTemplateMatchResult

/**
 * Target-focus HUD (top center).
 * Under PK All the HP bar is red; under Union an ally's bar turns green.
 * Clear focus = tap Focus Boss (skull) button — drops current target HUD.
 */
object ElfBuffFocusHud {

    private const val TAG = "ElfBuffCast"

    private const val HP_BAR_RED = "templates/mu/ui/focus_hp_bar.png"
    private const val HP_BAR_GREEN = "templates/mu/ui/focus_hp_bar_green.png"
    private const val HP_BAR_THRESHOLD = 0.68f

    private const val FOCUS_BOSS = "templates/mu/ui/targeting/focus_elite_skull.png"
    private const val FOCUS_BOSS_THRESHOLD = 0.70f

    /** Fallback Focus Boss tap @ 1280×720 if template miss. */
    private const val FALLBACK_BOSS_X_1280 = 1115
    private const val FALLBACK_BOSS_Y_720 = 656

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

    private fun roi(): Rect {
        val (w, h) = RefCoords.activeScreenSize()
        return hudRoi(w, h)
    }

    suspend fun findRedHpBar(): PcTemplateMatchResult? {
        return NavigationVision.findTemplate(HP_BAR_RED, HP_BAR_THRESHOLD, roi())
    }

    suspend fun findGreenHpBar(): PcTemplateMatchResult? {
        return NavigationVision.findTemplate(HP_BAR_GREEN, HP_BAR_THRESHOLD, roi())
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
     */
    suspend fun classifyUnionFocus(): HpBarColor? {
        // Prefer green first — ally confirmation.
        if (isGreenHpBarVisible()) return HpBarColor.GREEN
        if (isRedHpBarVisible()) return HpBarColor.RED
        return null
    }

    /**
     * Drop current focus HUD by tapping Focus Boss (skull).
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
}
