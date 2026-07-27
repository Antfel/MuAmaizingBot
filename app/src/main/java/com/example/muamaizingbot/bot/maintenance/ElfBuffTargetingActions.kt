package com.example.muamaizingbot.bot.maintenance

import android.graphics.Rect
import android.util.Log
import com.example.muamaizingbot.profile.LocationRepository
import com.example.muamaizingbot.vision.coord.RefCoords
import com.example.muamaizingbot.vision.navigation.NavigationVision
import com.example.muamaizingbot.vision.roi.MuCombatRois
import com.example.muamaizingbot.vision.template.PcTemplateMatchResult
import kotlinx.coroutines.delay

/**
 * PK mode + Focus-button helpers for the UI-driven giver loop:
 * All → spam Focus → Union → (cast) → clear X → All.
 *
 * After tapping All, Immortal opens option boxes **above** the bar (~10s).
 * Union in the open menu is matched in [pkPopupRoi]; closed-bar state uses a
 * separate Local template (button bevel differs from the popup row).
 *
 * Cross maps use UnionKuaFu popup [PK_MODE_UNION_CROSS] / bar [PK_MODE_UNION_CROSS_BAR].
 * Local maps: Union popup [PK_MODE_UNION_LOCAL] / bar [PK_MODE_UNION_LOCAL_BAR];
 * All popup [PK_MODE_ALL_POPUP] / bar [PK_MODE_ALL].
 */
object ElfBuffTargetingActions {

    private const val TAG = "ElfBuffCast"

    private const val FOCUS_PLAYER = "templates/mu/ui/targeting/focus_player.png"
    /** Closed targeting-bar All. */
    private const val PK_MODE_ALL = "templates/mu/ui/targeting/pk_mode_all.png"
    /** All row inside the open PK popup. */
    private const val PK_MODE_ALL_POPUP = "templates/mu/ui/targeting/pk_mode_all_popup.png"
    private const val PK_MODE_UNION_CROSS = "templates/mu/ui/targeting/pk_mode_union.png"
    /** Closed targeting-bar UnionKuaFu (bevel differs from popup row). */
    private const val PK_MODE_UNION_CROSS_BAR = "templates/mu/ui/targeting/pk_mode_union_cross_bar.png"
    /** Local Union row inside the open PK popup. */
    private const val PK_MODE_UNION_LOCAL = "templates/mu/ui/targeting/pk_mode_union_local.png"
    /** Local Union label on the closed targeting bar. */
    private const val PK_MODE_UNION_LOCAL_BAR = "templates/mu/ui/targeting/pk_mode_union_local_bar.png"

    private const val PK_TEMPLATE_THRESHOLD = 0.85f
    private const val FOCUS_TEMPLATE_THRESHOLD = 0.68f
    private const val POST_PK_TAP_MS = 350L
    private const val PK_POPUP_WAIT_MS = 3_500L
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

    /**
     * Active farm/elf spot decides Cross vs Local Union template.
     * Falls back to Cross (legacy UnionKuaFu) when no location is configured.
     */
    fun resolveIsCross(): Boolean {
        val location = LocationRepository.farmSpot.value
            ?: LocationRepository.elfBuff.value
            ?: return true
        return location.isCross
    }

    /** Template for open-menu / popup Union option. */
    private fun unionPopupTemplatePath(isCross: Boolean = resolveIsCross()): String {
        return if (isCross) PK_MODE_UNION_CROSS else PK_MODE_UNION_LOCAL
    }

    /** Template for closed targeting-bar Union state. */
    private fun unionBarTemplatePath(isCross: Boolean = resolveIsCross()): String {
        return if (isCross) PK_MODE_UNION_CROSS_BAR else PK_MODE_UNION_LOCAL_BAR
    }

    /** True when closed bar shows All (Union not on closed bar). */
    suspend fun isPkModeAll(): Boolean {
        val bar = barRoi()
        val all = NavigationVision.findTemplate(PK_MODE_ALL, PK_TEMPLATE_THRESHOLD, bar)
        if (all == null) return false
        val union = NavigationVision.findTemplate(unionBarTemplatePath(), PK_TEMPLATE_THRESHOLD, bar)
        return union == null
    }

    /** True when closed bar shows Union (All not on closed bar). */
    suspend fun isPkModeUnion(): Boolean {
        val bar = barRoi()
        val union = NavigationVision.findTemplate(unionBarTemplatePath(), PK_TEMPLATE_THRESHOLD, bar)
        if (union == null) return false
        val all = NavigationVision.findTemplate(PK_MODE_ALL, PK_TEMPLATE_THRESHOLD, bar)
        return all == null
    }

    private suspend fun waitPkOption(
        assetPath: String,
        label: String,
    ): PcTemplateMatchResult? {
        val popup = pkPopupRoi()
        val match = NavigationVision.waitForTemplate(
            assetPath,
            PK_TEMPLATE_THRESHOLD,
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
        val unionBarPath = unionBarTemplatePath()
        val unionPopupPath = unionPopupTemplatePath()
        val allOnBar = NavigationVision.findTemplate(PK_MODE_ALL, PK_TEMPLATE_THRESHOLD, bar)
        val unionOnBar = NavigationVision.findTemplate(unionBarPath, PK_TEMPLATE_THRESHOLD, bar)
        val popup = pkPopupRoi()
        val allInPopup = NavigationVision.findTemplate(PK_MODE_ALL_POPUP, PK_TEMPLATE_THRESHOLD, popup)
        val unionInPopup = NavigationVision.findTemplate(unionPopupPath, PK_TEMPLATE_THRESHOLD, popup)

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
                val allOpt = waitPkOption(PK_MODE_ALL_POPUP, "All") ?: return false
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
        val isCross = resolveIsCross()
        val unionPopupPath = unionPopupTemplatePath(isCross)
        val unionBarPath = unionBarTemplatePath(isCross)
        val unionLabel = if (isCross) "UnionKuaFu" else "Union"
        if (isPkModeUnion()) {
            Log.d(TAG, "[ELF_GIVER] pk mode already $unionLabel (isCross=$isCross)")
            return true
        }
        val bar = barRoi()
        val popup = pkPopupRoi()
        val allOnBar = NavigationVision.findTemplate(PK_MODE_ALL, PK_TEMPLATE_THRESHOLD, bar)
        val unionInPopup = NavigationVision.findTemplate(unionPopupPath, PK_TEMPLATE_THRESHOLD, popup)

        when {
            unionInPopup != null -> {
                Log.d(TAG, "[ELF_GIVER] pk menu open — tap $unionLabel")
                if (!NavigationVision.tapScreen(unionInPopup.centerX, unionInPopup.centerY, label = "pk_union")) {
                    return false
                }
            }
            allOnBar != null -> {
                Log.d(TAG, "[ELF_GIVER] pk is All — open menu ($unionLabel boxes above)")
                if (!NavigationVision.tapScreen(allOnBar.centerX, allOnBar.centerY, label = "pk_open")) {
                    return false
                }
                delay(POST_PK_TAP_MS)
                val unionOpt = waitPkOption(unionPopupPath, unionLabel) ?: return false
                if (!NavigationVision.tapScreen(unionOpt.centerX, unionOpt.centerY, label = "pk_union")) {
                    return false
                }
            }
            else -> {
                Log.w(TAG, "[ELF_GIVER] pk All miss — cannot switch $unionLabel")
                return false
            }
        }
        delay(POST_PK_TAP_MS)
        val ok = isPkModeUnion()
        Log.d(
            TAG,
            "[ELF_GIVER] switchPkModeUnion ok=$ok isCross=$isCross " +
                "popup=$unionPopupPath bar=$unionBarPath",
        )
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
            val match = NavigationVision.findTemplate(FOCUS_PLAYER, FOCUS_TEMPLATE_THRESHOLD, roi)
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
