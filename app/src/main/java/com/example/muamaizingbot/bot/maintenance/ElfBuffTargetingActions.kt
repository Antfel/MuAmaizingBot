package com.example.muamaizingbot.bot.maintenance

import android.graphics.Rect
import android.util.Log
import com.example.muamaizingbot.vision.coord.RefCoords
import com.example.muamaizingbot.vision.navigation.NavigationVision
import com.example.muamaizingbot.vision.roi.MuCombatRois
import com.example.muamaizingbot.vision.template.PcTemplateMatchResult
import kotlinx.coroutines.delay

/**
 * PK mode + Focus-button helpers for the UI-driven giver loop:
 * All → spam Focus → UnionKuaFu → (cast) → clear X → All.
 *
 * After tapping All, Immortal opens option boxes **above** the bar (~10s).
 * UnionKuaFu is matched in [pkPopupRoi], not only on the closed bar.
 */
object ElfBuffTargetingActions {

    private const val TAG = "ElfBuffCast"

    private const val FOCUS_PLAYER = "templates/mu/ui/targeting/focus_player.png"
    private const val PK_MODE_ALL = "templates/mu/ui/targeting/pk_mode_all.png"
    private const val PK_MODE_UNION = "templates/mu/ui/targeting/pk_mode_union.png"

    private const val TEMPLATE_THRESHOLD = 0.68f
    private const val POST_PK_TAP_MS = 250L
    private const val PK_POPUP_WAIT_MS = 2_000L
    private const val POST_FOCUS_TAP_MS = 180L
    private const val FOCUS_SPAM_MAX = 10

    private fun barRoi(): Rect {
        val (w, h) = RefCoords.activeScreenSize()
        return MuCombatRois.targetingHudRoi(w, h)
    }

    /** Boxes that appear above All after opening the PK menu. */
    private fun pkPopupRoi(): Rect {
        val (w, h) = RefCoords.activeScreenSize()
        return MuCombatRois.pkModePopupRoi(w, h)
    }

    /** True when closed bar shows All (Union not on closed bar). */
    suspend fun isPkModeAll(): Boolean {
        val bar = barRoi()
        val all = NavigationVision.findTemplate(PK_MODE_ALL, TEMPLATE_THRESHOLD, bar)
        if (all == null) return false
        val union = NavigationVision.findTemplate(PK_MODE_UNION, TEMPLATE_THRESHOLD, bar)
        return union == null
    }

    /** True when closed bar shows UnionKuaFu (All not on closed bar). */
    suspend fun isPkModeUnion(): Boolean {
        val bar = barRoi()
        val union = NavigationVision.findTemplate(PK_MODE_UNION, TEMPLATE_THRESHOLD, bar)
        if (union == null) return false
        val all = NavigationVision.findTemplate(PK_MODE_ALL, TEMPLATE_THRESHOLD, bar)
        return all == null
    }

    private suspend fun waitPkOption(
        assetPath: String,
        label: String,
    ): PcTemplateMatchResult? {
        val popup = pkPopupRoi()
        val match = NavigationVision.waitForTemplate(
            assetPath,
            TEMPLATE_THRESHOLD,
            timeoutMs = PK_POPUP_WAIT_MS,
            roi = popup,
        )
        if (match != null) {
            Log.d(
                TAG,
                "[ELF_GIVER] pk popup $label at=(${match.centerX},${match.centerY}) " +
                    "score=${"%.3f".format(match.score)}",
            )
            return match
        }
        Log.w(TAG, "[ELF_GIVER] pk popup $label not found within ${PK_POPUP_WAIT_MS}ms")
        NavigationVision.logBestScore(assetPath, popup)
        return null
    }

    suspend fun ensurePkModeAll(): Boolean {
        if (isPkModeAll()) {
            Log.d(TAG, "[ELF_GIVER] pk mode already All")
            return true
        }
        val bar = barRoi()
        val allOnBar = NavigationVision.findTemplate(PK_MODE_ALL, TEMPLATE_THRESHOLD, bar)
        val unionOnBar = NavigationVision.findTemplate(PK_MODE_UNION, TEMPLATE_THRESHOLD, bar)
        val popup = pkPopupRoi()
        val allInPopup = NavigationVision.findTemplate(PK_MODE_ALL, TEMPLATE_THRESHOLD, popup)
        val unionInPopup = NavigationVision.findTemplate(PK_MODE_UNION, TEMPLATE_THRESHOLD, popup)

        when {
            allInPopup != null && unionInPopup != null -> {
                Log.d(TAG, "[ELF_GIVER] pk menu already open — tap All")
                if (!NavigationVision.tapScreen(allInPopup.centerX, allInPopup.centerY, label = "pk_all")) {
                    return false
                }
            }
            unionOnBar != null || allOnBar != null -> {
                val openAt = unionOnBar ?: allOnBar!!
                Log.d(TAG, "[ELF_GIVER] open pk menu to select All")
                if (!NavigationVision.tapScreen(openAt.centerX, openAt.centerY, label = "pk_open")) {
                    return false
                }
                delay(POST_PK_TAP_MS)
                val allOpt = waitPkOption(PK_MODE_ALL, "All") ?: return false
                if (!NavigationVision.tapScreen(allOpt.centerX, allOpt.centerY, label = "pk_all")) {
                    return false
                }
            }
            else -> {
                Log.w(TAG, "[ELF_GIVER] pk All/Union templates miss — cannot ensure All")
                return false
            }
        }
        delay(POST_PK_TAP_MS)
        val ok = isPkModeAll()
        Log.d(TAG, "[ELF_GIVER] ensurePkModeAll ok=$ok")
        return ok
    }

    suspend fun switchPkModeUnion(): Boolean {
        if (isPkModeUnion()) {
            Log.d(TAG, "[ELF_GIVER] pk mode already Union")
            return true
        }
        val bar = barRoi()
        val popup = pkPopupRoi()
        val allOnBar = NavigationVision.findTemplate(PK_MODE_ALL, TEMPLATE_THRESHOLD, bar)
        val unionInPopup = NavigationVision.findTemplate(PK_MODE_UNION, TEMPLATE_THRESHOLD, popup)

        when {
            unionInPopup != null -> {
                Log.d(TAG, "[ELF_GIVER] pk menu open — tap Union")
                if (!NavigationVision.tapScreen(unionInPopup.centerX, unionInPopup.centerY, label = "pk_union")) {
                    return false
                }
            }
            allOnBar != null -> {
                Log.d(TAG, "[ELF_GIVER] pk is All — open menu (Union boxes above)")
                if (!NavigationVision.tapScreen(allOnBar.centerX, allOnBar.centerY, label = "pk_open")) {
                    return false
                }
                delay(POST_PK_TAP_MS)
                val unionOpt = waitPkOption(PK_MODE_UNION, "Union") ?: return false
                if (!NavigationVision.tapScreen(unionOpt.centerX, unionOpt.centerY, label = "pk_union")) {
                    return false
                }
            }
            else -> {
                Log.w(TAG, "[ELF_GIVER] pk All miss — cannot switch Union")
                return false
            }
        }
        delay(POST_PK_TAP_MS)
        val ok = isPkModeUnion()
        Log.d(TAG, "[ELF_GIVER] switchPkModeUnion ok=$ok")
        return ok
    }

    /**
     * Spam Focus (person) until focus HUD appears, or [maxAttempts] exhausted.
     */
    suspend fun spamFocusUntilHud(maxAttempts: Int = FOCUS_SPAM_MAX): Boolean {
        if (ElfBuffFocusHud.isFocusHudVisible()) {
            Log.d(TAG, "[ELF_GIVER] focus HUD already visible")
            return true
        }
        val roi = barRoi()
        for (attempt in 1..maxAttempts) {
            val match = NavigationVision.findTemplate(FOCUS_PLAYER, TEMPLATE_THRESHOLD, roi)
            if (match == null) {
                Log.w(TAG, "[ELF_GIVER] focus_player miss attempt=$attempt/$maxAttempts")
                delay(POST_FOCUS_TAP_MS)
                if (ElfBuffFocusHud.isFocusHudVisible()) return true
                continue
            }
            Log.d(
                TAG,
                "[ELF_GIVER] focus spam attempt=$attempt/$maxAttempts " +
                    "at=(${match.centerX},${match.centerY}) score=${"%.3f".format(match.score)}",
            )
            if (!NavigationVision.tapScreen(match.centerX, match.centerY, label = "focus_player")) {
                Log.w(TAG, "[ELF_GIVER] focus_player tap failed")
            }
            delay(POST_FOCUS_TAP_MS)
            if (ElfBuffFocusHud.isFocusHudVisible()) {
                Log.d(TAG, "[ELF_GIVER] focus HUD after attempt=$attempt")
                return true
            }
        }
        val visible = ElfBuffFocusHud.isFocusHudVisible()
        Log.d(TAG, "[ELF_GIVER] focus spam done hudVisible=$visible")
        return visible
    }
}
