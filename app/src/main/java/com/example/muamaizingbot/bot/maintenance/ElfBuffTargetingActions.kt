package com.example.muamaizingbot.bot.maintenance

import android.graphics.Rect
import android.util.Log
import com.example.muamaizingbot.profile.LocationRepository
import com.example.muamaizingbot.settings.BotTiming
import com.example.muamaizingbot.settings.BotTimingCategory
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
    private const val PK_MODE_PEACE_POPUP = "templates/mu/ui/targeting/pk_mode_peace_popup.png"
    private const val PK_MODE_PEACE_BAR = "templates/mu/ui/targeting/pk_mode_peace_bar.png"
    private const val PK_MODE_TEAM_POPUP = "templates/mu/ui/targeting/pk_mode_team_popup.png"
    private const val PK_MODE_TEAM_BAR = "templates/mu/ui/targeting/pk_mode_team_bar.png"

    private const val PK_TEMPLATE_THRESHOLD = 0.85f
    private const val FOCUS_TEMPLATE_THRESHOLD = 0.68f
    private const val POST_PK_TAP_MS = 350L
    private const val PK_POPUP_QUICK_WAIT_MS = 800L
    private const val PK_POPUP_SECOND_WAIT_MS = 1_500L
    private const val PK_POPUP_FALLBACK_WAIT_MS = 3_500L
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

    private enum class PkBarMode {
        ALL,
        UNION,
        PEACE,
        TEAM,
    }

    private data class PkBarMatch(
        val mode: PkBarMode,
        val match: PcTemplateMatchResult,
    )

    /**
     * Match every supported closed-bar state and keep the strongest candidate.
     * This allows recovery even when the game changes the selector to Peace or Team.
     */
    private suspend fun detectPkBarMode(): PkBarMatch? {
        val bar = barRoi()
        val paths = listOf(
            PkBarMode.ALL to PK_MODE_ALL,
            PkBarMode.UNION to unionBarTemplatePath(),
            PkBarMode.PEACE to PK_MODE_PEACE_BAR,
            PkBarMode.TEAM to PK_MODE_TEAM_BAR,
        )
        return paths.mapNotNull { (mode, path) ->
            NavigationVision.findTemplate(path, PK_TEMPLATE_THRESHOLD, bar)
                ?.let { PkBarMatch(mode, it) }
        }.maxByOrNull { it.match.score }
    }

    /** True when the closed targeting bar shows All. */
    suspend fun isPkModeAll(): Boolean {
        return detectPkBarMode()?.mode == PkBarMode.ALL
    }

    /** True when the closed targeting bar shows Union. */
    suspend fun isPkModeUnion(): Boolean {
        return detectPkBarMode()?.mode == PkBarMode.UNION
    }

    private suspend fun waitPkOption(
        assetPath: String,
        label: String,
        timeoutMs: Long,
        logMiss: Boolean,
    ): PcTemplateMatchResult? {
        val popup = pkPopupRoi()
        val match = NavigationVision.waitForTemplate(
            assetPath,
            PK_TEMPLATE_THRESHOLD,
            timeoutMs = timeoutMs,
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
        if (logMiss) {
            Log.w(TAG, "[ELF_GIVER] pk popup $label not found within adaptive wait")
            NavigationVision.logBestScore(assetPath, popup)
        }
        return null
    }

    /** Peace and Team only occur as popup rows in this ROI, making them safe witnesses. */
    private suspend fun isPkPopupVisible(): Boolean {
        val popup = pkPopupRoi()
        return NavigationVision.findTemplate(PK_MODE_PEACE_POPUP, PK_TEMPLATE_THRESHOLD, popup) != null ||
            NavigationVision.findTemplate(PK_MODE_TEAM_POPUP, PK_TEMPLATE_THRESHOLD, popup) != null
    }

    private suspend fun openPkMenuAndFindOption(
        openAt: PcTemplateMatchResult,
        assetPath: String,
        label: String,
    ): PcTemplateMatchResult? {
        if (!NavigationVision.tapScreen(openAt.centerX, openAt.centerY, label = "pk_open")) {
            return null
        }
        delay(BotTiming.ms(POST_PK_TAP_MS, BotTimingCategory.POST_TAP))

        waitPkOption(
            assetPath,
            label,
            BotTiming.ms(PK_POPUP_QUICK_WAIT_MS, BotTimingCategory.SCREEN_LOAD),
            logMiss = false,
        )?.let {
            return it
        }

        if (!isPkPopupVisible()) {
            Log.d(TAG, "[ELF_GIVER] pk popup absent after quick wait — retry open")
            if (!NavigationVision.tapScreen(openAt.centerX, openAt.centerY, label = "pk_open_retry")) {
                return null
            }
            delay(BotTiming.ms(POST_PK_TAP_MS, BotTimingCategory.POST_TAP))
            waitPkOption(
                assetPath,
                label,
                BotTiming.ms(PK_POPUP_SECOND_WAIT_MS, BotTimingCategory.SCREEN_LOAD),
                logMiss = false,
            )?.let {
                return it
            }
        } else {
            Log.d(TAG, "[ELF_GIVER] pk popup visible; waiting for $label")
        }

        return waitPkOption(
            assetPath,
            label,
            BotTiming.ms(PK_POPUP_FALLBACK_WAIT_MS, BotTimingCategory.SCREEN_LOAD),
            logMiss = true,
        )
    }

    suspend fun ensurePkModeAll(): Boolean {
        val currentBar = detectPkBarMode()
        if (currentBar?.mode == PkBarMode.ALL) {
            Log.d(TAG, "[ELF_GIVER] pk mode already All")
            return true
        }
        val popup = pkPopupRoi()
        val allInPopup = NavigationVision.findTemplate(PK_MODE_ALL_POPUP, PK_TEMPLATE_THRESHOLD, popup)
        val popupWitness = listOf(
            unionPopupTemplatePath(),
            PK_MODE_PEACE_POPUP,
            PK_MODE_TEAM_POPUP,
        ).any { path ->
            NavigationVision.findTemplate(path, PK_TEMPLATE_THRESHOLD, popup) != null
        }

        when {
            allInPopup != null && popupWitness -> {
                Log.d(TAG, "[ELF_GIVER] pk menu already open — tap All")
                if (!NavigationVision.tapScreen(allInPopup.centerX, allInPopup.centerY, label = "pk_all")) {
                    return false
                }
            }
            currentBar != null -> {
                Log.d(TAG, "[ELF_GIVER] pk mode ${currentBar.mode} — open menu to select All")
                val allOpt = openPkMenuAndFindOption(
                    currentBar.match,
                    PK_MODE_ALL_POPUP,
                    "All",
                ) ?: return false
                if (!NavigationVision.tapScreen(allOpt.centerX, allOpt.centerY, label = "pk_all")) {
                    return false
                }
            }
            else -> {
                Log.w(TAG, "[ELF_GIVER] pk bar templates miss (All/Union/Peace/Team) — cannot ensure All")
                return false
            }
        }
        delay(BotTiming.ms(POST_PK_TAP_MS, BotTimingCategory.POST_TAP))
        val ok = isPkModeAll()
        Log.d(TAG, "[ELF_GIVER] ensurePkModeAll ok=$ok")
        return ok
    }

    /**
     * Switch to Union with All as the required initial closed-bar state.
     * An already-open popup is allowed for a retry, but an already-Union bar is
     * not accepted as the beginning of a fresh focus-validation attempt.
     */
    suspend fun switchPkModeUnionFromAll(): Boolean {
        val isCross = resolveIsCross()
        val unionPopupPath = unionPopupTemplatePath(isCross)
        val unionBarPath = unionBarTemplatePath(isCross)
        val unionLabel = if (isCross) "UnionKuaFu" else "Union"
        val currentBar = detectPkBarMode()
        val popup = pkPopupRoi()
        val unionInPopup = NavigationVision.findTemplate(unionPopupPath, PK_TEMPLATE_THRESHOLD, popup)

        when {
            unionInPopup != null -> {
                Log.d(TAG, "[ELF_GIVER] pk menu open — tap $unionLabel")
                if (!NavigationVision.tapScreen(unionInPopup.centerX, unionInPopup.centerY, label = "pk_union")) {
                    return false
                }
            }
            currentBar?.mode == PkBarMode.ALL -> {
                Log.d(TAG, "[ELF_GIVER] pk mode ALL — open menu for $unionLabel")
                val unionOpt = openPkMenuAndFindOption(
                    currentBar.match,
                    unionPopupPath,
                    unionLabel,
                ) ?: return false
                if (!NavigationVision.tapScreen(unionOpt.centerX, unionOpt.centerY, label = "pk_union")) {
                    return false
                }
            }
            else -> {
                Log.w(
                    TAG,
                    "[ELF_GIVER] switch $unionLabel rejected: expected All, " +
                        "actual=${currentBar?.mode}",
                )
                return false
            }
        }
        delay(BotTiming.ms(POST_PK_TAP_MS, BotTimingCategory.POST_TAP))
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
    suspend fun spamFocusUntilRedHud(maxAttempts: Int = FOCUS_SPAM_MAX): Boolean {
        if (ElfBuffFocusHud.isRedHpBarVisible()) {
            Log.d(TAG, "[ELF_GIVER] red focus HUD already visible under All")
            return true
        }
        val roi = barRoi()
        for (attempt in 1..maxAttempts) {
            val match = NavigationVision.findTemplate(FOCUS_PLAYER, FOCUS_TEMPLATE_THRESHOLD, roi)
            if (match == null) {
                Log.w(TAG, "[ELF_GIVER] focus_player miss attempt=$attempt/$maxAttempts")
                delay(BotTiming.ms(POST_FOCUS_TAP_MS, BotTimingCategory.POST_TAP))
                if (ElfBuffFocusHud.isRedHpBarVisible()) return true
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
            delay(BotTiming.ms(POST_FOCUS_TAP_MS, BotTimingCategory.POST_TAP))
            if (ElfBuffFocusHud.isRedHpBarVisible()) {
                Log.d(TAG, "[ELF_GIVER] red focus HUD under All after attempt=$attempt")
                return true
            }
        }
        val visible = ElfBuffFocusHud.isRedHpBarVisible()
        Log.d(TAG, "[ELF_GIVER] focus spam done redHudVisible=$visible")
        return visible
    }
}
