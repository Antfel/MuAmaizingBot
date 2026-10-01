package com.example.muamaizingbot.bot.maintenance

import android.graphics.Rect
import android.util.Log
import com.example.muamaizingbot.bot.BotController
import com.example.muamaizingbot.bot.BotRuntimeState
import com.example.muamaizingbot.bot.bosses.FarmBossesLoop
import com.example.muamaizingbot.bot.navigation.TrustedCurrentMapMemory
import com.example.muamaizingbot.maps.MapDefinitionRepository
import com.example.muamaizingbot.profile.CombatFocusPkMode
import com.example.muamaizingbot.profile.LocationRepository
import com.example.muamaizingbot.profile.ProfileRepository
import com.example.muamaizingbot.profile.isFarmBossesMode
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
 * Map [MapDefinition.isCross] prefers UnionKuaFu (cross) or short Union (local),
 * but both label templates are accepted — game load can leave Cross maps showing
 * short "Union" (or the reverse), and that must not stop giver buffs.
 *
 * Templates: cross popup/bar [PK_MODE_UNION_CROSS] / [PK_MODE_UNION_CROSS_BAR];
 * local [PK_MODE_UNION_LOCAL] / [PK_MODE_UNION_LOCAL_BAR];
 * All [PK_MODE_ALL_POPUP] / [PK_MODE_ALL].
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
    private const val FOCUS_TEMPLATE_THRESHOLD = 0.62f
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
     * PK Union template preference from the **map** [MapDefinition.isCross], not the
     * saved spot override. Prefer UnionKuaFu when cross; short Union otherwise.
     * Detection and switch still accept **both** labels (load lag / sticky HUD).
     *
     * Resolution order: trusted HUD map → farm_bosses active map → farm/elf spot map.
     */
    fun resolveIsCross(): Boolean {
        TrustedCurrentMapMemory.trustedMapId()?.let { mapId ->
            MapDefinitionRepository.getById(mapId)?.let { def ->
                Log.d(TAG, "[ELF_GIVER] pk isCross=${def.isCross} from trusted map=${def.id}")
                return def.isCross
            }
        }
        val profile = ProfileRepository.currentProfile.value
        if (profile != null && profile.isFarmBossesMode()) {
            FarmBossesLoop.currentMapId(profile)?.let { mapId ->
                MapDefinitionRepository.getById(mapId)?.let { def ->
                    Log.d(TAG, "[ELF_GIVER] pk isCross=${def.isCross} from boss map=${def.id}")
                    return def.isCross
                }
            }
        }
        val spotMapId = LocationRepository.farmSpot.value?.map
            ?: LocationRepository.elfBuff.value?.map
        if (spotMapId != null) {
            MapDefinitionRepository.getById(spotMapId)?.let { def ->
                Log.d(TAG, "[ELF_GIVER] pk isCross=${def.isCross} from spot map=${def.id}")
                return def.isCross
            }
        }
        Log.d(TAG, "[ELF_GIVER] pk isCross=true (fallback, no map)")
        return true
    }

    /** Popup Union rows: preferred map variant first, then the alternate. */
    private fun unionPopupCandidates(preferCross: Boolean = resolveIsCross()): List<Pair<String, String>> {
        return if (preferCross) {
            listOf(
                PK_MODE_UNION_CROSS to "UnionKuaFu",
                PK_MODE_UNION_LOCAL to "Union",
            )
        } else {
            listOf(
                PK_MODE_UNION_LOCAL to "Union",
                PK_MODE_UNION_CROSS to "UnionKuaFu",
            )
        }
    }

    /** Closed-bar Union labels: preferred first, then alternate. */
    private fun unionBarCandidates(preferCross: Boolean = resolveIsCross()): List<Pair<String, String>> {
        return if (preferCross) {
            listOf(
                PK_MODE_UNION_CROSS_BAR to "UnionKuaFu",
                PK_MODE_UNION_LOCAL_BAR to "Union",
            )
        } else {
            listOf(
                PK_MODE_UNION_LOCAL_BAR to "Union",
                PK_MODE_UNION_CROSS_BAR to "UnionKuaFu",
            )
        }
    }

    private fun preferredUnionLabel(preferCross: Boolean = resolveIsCross()): String =
        if (preferCross) "UnionKuaFu" else "Union"

    private enum class PkBarMode {
        ALL,
        UNION,
        PEACE,
        TEAM,
    }

    private data class PkBarMatch(
        val mode: PkBarMode,
        val match: PcTemplateMatchResult,
        val label: String = mode.name,
    )

    private data class PkModeSpec(
        val barMode: PkBarMode,
        val popupPath: String,
        val label: String,
        val tapLabel: String,
    )

    private fun pkModeSpec(mode: CombatFocusPkMode): PkModeSpec {
        return when (mode) {
            CombatFocusPkMode.ALL -> PkModeSpec(
                barMode = PkBarMode.ALL,
                popupPath = PK_MODE_ALL_POPUP,
                label = "All",
                tapLabel = "pk_all",
            )
            CombatFocusPkMode.PEACE -> PkModeSpec(
                barMode = PkBarMode.PEACE,
                popupPath = PK_MODE_PEACE_POPUP,
                label = "Peace",
                tapLabel = "pk_peace",
            )
            CombatFocusPkMode.TEAM -> PkModeSpec(
                barMode = PkBarMode.TEAM,
                popupPath = PK_MODE_TEAM_POPUP,
                label = "Team",
                tapLabel = "pk_team",
            )
            CombatFocusPkMode.UNION -> {
                val isCross = resolveIsCross()
                val (popupPath, label) = unionPopupCandidates(isCross).first()
                PkModeSpec(
                    barMode = PkBarMode.UNION,
                    popupPath = popupPath,
                    label = label,
                    tapLabel = "pk_union",
                )
            }
        }
    }

    /**
     * Match every supported closed-bar state and keep the strongest candidate.
     * Union accepts both UnionKuaFu and short Union — whichever scores higher.
     */
    private suspend fun detectPkBarMode(): PkBarMatch? {
        val bar = barRoi()
        val candidates = buildList {
            NavigationVision.findTemplate(PK_MODE_ALL, PK_TEMPLATE_THRESHOLD, bar)?.let {
                add(PkBarMatch(PkBarMode.ALL, it, "All"))
            }
            for ((path, label) in unionBarCandidates()) {
                NavigationVision.findTemplate(path, PK_TEMPLATE_THRESHOLD, bar)?.let {
                    add(PkBarMatch(PkBarMode.UNION, it, label))
                }
            }
            NavigationVision.findTemplate(PK_MODE_PEACE_BAR, PK_TEMPLATE_THRESHOLD, bar)?.let {
                add(PkBarMatch(PkBarMode.PEACE, it, "Peace"))
            }
            NavigationVision.findTemplate(PK_MODE_TEAM_BAR, PK_TEMPLATE_THRESHOLD, bar)?.let {
                add(PkBarMatch(PkBarMode.TEAM, it, "Team"))
            }
        }
        return candidates.maxByOrNull { it.match.score }
    }

    /** Best Union/UnionKuaFu row currently visible in the open PK popup (if any). */
    private suspend fun findBestUnionInPopup(): PkBarMatch? {
        val popup = pkPopupRoi()
        return unionPopupCandidates().mapNotNull { (path, label) ->
            NavigationVision.findTemplate(path, PK_TEMPLATE_THRESHOLD, popup)
                ?.let { PkBarMatch(PkBarMode.UNION, it, label) }
        }.maxByOrNull { it.match.score }
    }

    /** True when the closed targeting bar shows All. */
    suspend fun isPkModeAll(): Boolean {
        return detectPkBarMode()?.mode == PkBarMode.ALL
    }

    /** True when the closed targeting bar shows Union or UnionKuaFu. */
    suspend fun isPkModeUnion(): Boolean {
        return detectPkBarMode()?.mode == PkBarMode.UNION
    }

    private suspend fun isPkBarMode(mode: PkBarMode): Boolean {
        return detectPkBarMode()?.mode == mode
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

    /**
     * Wait for either Union label in the popup. Prefers the map variant, accepts the
     * alternate if that is what the game painted (cross load lag → short Union).
     */
    private suspend fun waitPkUnionOption(
        timeoutMs: Long,
        logMiss: Boolean,
    ): PkBarMatch? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            findBestUnionInPopup()?.let { hit ->
                Log.d(
                    TAG,
                    "[ELF_GIVER] pk popup ${hit.label} at=(${hit.match.centerX},${hit.match.centerY}) " +
                        "score=${"%.3f".format(hit.match.score)} prefer=${preferredUnionLabel()}",
                )
                return hit
            }
            delay(80L)
        }
        if (logMiss) {
            Log.w(
                TAG,
                "[ELF_GIVER] pk popup Union/UnionKuaFu not found within adaptive wait " +
                    "prefer=${preferredUnionLabel()}",
            )
            val popup = pkPopupRoi()
            for ((path, label) in unionPopupCandidates()) {
                NavigationVision.logBestScore(path, popup)
                Log.d(TAG, "[ELF_GIVER] pk popup miss probe label=$label path=$path")
            }
        }
        return null
    }

    /** Peace and Team only occur as popup rows in this ROI, making them safe witnesses. */
    private suspend fun isPkPopupVisible(): Boolean {
        val popup = pkPopupRoi()
        return NavigationVision.findTemplate(PK_MODE_PEACE_POPUP, PK_TEMPLATE_THRESHOLD, popup) != null ||
            NavigationVision.findTemplate(PK_MODE_TEAM_POPUP, PK_TEMPLATE_THRESHOLD, popup) != null ||
            findBestUnionInPopup() != null ||
            NavigationVision.findTemplate(PK_MODE_ALL_POPUP, PK_TEMPLATE_THRESHOLD, popup) != null
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

    private suspend fun openPkMenuAndFindUnion(
        openAt: PcTemplateMatchResult,
    ): PkBarMatch? {
        if (!NavigationVision.tapScreen(openAt.centerX, openAt.centerY, label = "pk_open")) {
            return null
        }
        delay(BotTiming.ms(POST_PK_TAP_MS, BotTimingCategory.POST_TAP))

        waitPkUnionOption(
            BotTiming.ms(PK_POPUP_QUICK_WAIT_MS, BotTimingCategory.SCREEN_LOAD),
            logMiss = false,
        )?.let { return it }

        if (!isPkPopupVisible()) {
            Log.d(TAG, "[ELF_GIVER] pk popup absent after quick wait — retry open for Union")
            if (!NavigationVision.tapScreen(openAt.centerX, openAt.centerY, label = "pk_open_retry")) {
                return null
            }
            delay(BotTiming.ms(POST_PK_TAP_MS, BotTimingCategory.POST_TAP))
            waitPkUnionOption(
                BotTiming.ms(PK_POPUP_SECOND_WAIT_MS, BotTimingCategory.SCREEN_LOAD),
                logMiss = false,
            )?.let { return it }
        } else {
            Log.d(TAG, "[ELF_GIVER] pk popup visible; waiting for Union/UnionKuaFu")
        }

        return waitPkUnionOption(
            BotTiming.ms(PK_POPUP_FALLBACK_WAIT_MS, BotTimingCategory.SCREEN_LOAD),
            logMiss = true,
        )
    }

    /** True when the closed targeting bar shows [mode]. */
    suspend fun isPkMode(mode: CombatFocusPkMode): Boolean {
        return isPkBarMode(pkModeSpec(mode).barMode)
    }

    /**
     * Ensure the closed targeting bar shows [mode] (Peace / Team / Union / All).
     * Opens the PK popup when needed and taps the matching row.
     * Union accepts either UnionKuaFu or short Union on the closed bar.
     */
    suspend fun ensurePkMode(mode: CombatFocusPkMode): Boolean {
        val spec = pkModeSpec(mode)
        val currentBar = detectPkBarMode()
        if (currentBar?.mode == spec.barMode) {
            Log.d(TAG, "[ELF_GIVER] pk mode already ${currentBar.label}")
            return true
        }
        val popup = pkPopupRoi()
        val optionInPopup = if (mode == CombatFocusPkMode.UNION) {
            findBestUnionInPopup()
        } else {
            NavigationVision.findTemplate(
                spec.popupPath,
                PK_TEMPLATE_THRESHOLD,
                popup,
            )?.let { PkBarMatch(spec.barMode, it, spec.label) }
        }
        val popupWitness = listOf(
            PK_MODE_ALL_POPUP,
            PK_MODE_UNION_CROSS,
            PK_MODE_UNION_LOCAL,
            PK_MODE_PEACE_POPUP,
            PK_MODE_TEAM_POPUP,
        ).any { path ->
            NavigationVision.findTemplate(path, PK_TEMPLATE_THRESHOLD, popup) != null
        }

        when {
            optionInPopup != null && popupWitness -> {
                Log.d(TAG, "[ELF_GIVER] pk menu already open — tap ${optionInPopup.label}")
                if (!NavigationVision.tapScreen(
                        optionInPopup.match.centerX,
                        optionInPopup.match.centerY,
                        label = spec.tapLabel,
                    )
                ) {
                    return false
                }
            }
            currentBar != null && mode == CombatFocusPkMode.UNION -> {
                Log.d(
                    TAG,
                    "[ELF_GIVER] pk mode ${currentBar.label} — open menu to select Union/UnionKuaFu",
                )
                val opt = openPkMenuAndFindUnion(currentBar.match) ?: return false
                if (!NavigationVision.tapScreen(
                        opt.match.centerX,
                        opt.match.centerY,
                        label = spec.tapLabel,
                    )
                ) {
                    return false
                }
            }
            currentBar != null -> {
                Log.d(
                    TAG,
                    "[ELF_GIVER] pk mode ${currentBar.label} — open menu to select ${spec.label}",
                )
                val opt = openPkMenuAndFindOption(
                    currentBar.match,
                    spec.popupPath,
                    spec.label,
                ) ?: return false
                if (!NavigationVision.tapScreen(opt.centerX, opt.centerY, label = spec.tapLabel)) {
                    return false
                }
            }
            else -> {
                Log.w(
                    TAG,
                    "[ELF_GIVER] pk bar templates miss (All/Union/Peace/Team) — " +
                        "cannot ensure ${spec.label}",
                )
                return false
            }
        }
        delay(BotTiming.ms(POST_PK_TAP_MS, BotTimingCategory.POST_TAP))
        val confirmed = detectPkBarMode()
        val ok = confirmed?.mode == spec.barMode
        Log.d(
            TAG,
            "[ELF_GIVER] ensurePkMode ${spec.label} ok=$ok bar=${confirmed?.label}",
        )
        return ok
    }

    suspend fun ensurePkModeAll(): Boolean = ensurePkMode(CombatFocusPkMode.ALL)

    /**
     * Switch to Union with All as the required initial closed-bar state.
     * Prefers the map's Union variant but accepts the alternate label in popup and
     * on the closed bar (cross map can still show short Union during load).
     */
    suspend fun switchPkModeUnionFromAll(): Boolean {
        val preferCross = resolveIsCross()
        val preferLabel = preferredUnionLabel(preferCross)
        val currentBar = detectPkBarMode()
        val unionInPopup = findBestUnionInPopup()

        when {
            unionInPopup != null -> {
                Log.d(
                    TAG,
                    "[ELF_GIVER] pk menu open — tap ${unionInPopup.label} " +
                        "(prefer=$preferLabel)",
                )
                if (!NavigationVision.tapScreen(
                        unionInPopup.match.centerX,
                        unionInPopup.match.centerY,
                        label = "pk_union",
                    )
                ) {
                    return false
                }
            }
            currentBar?.mode == PkBarMode.ALL -> {
                Log.d(
                    TAG,
                    "[ELF_GIVER] pk mode ALL — open menu for Union/UnionKuaFu prefer=$preferLabel",
                )
                val unionOpt = openPkMenuAndFindUnion(currentBar.match) ?: return false
                if (!NavigationVision.tapScreen(
                        unionOpt.match.centerX,
                        unionOpt.match.centerY,
                        label = "pk_union",
                    )
                ) {
                    return false
                }
            }
            else -> {
                Log.w(
                    TAG,
                    "[ELF_GIVER] switch Union rejected: expected All, " +
                        "actual=${currentBar?.label}",
                )
                return false
            }
        }
        delay(BotTiming.ms(POST_PK_TAP_MS, BotTimingCategory.POST_TAP))
        val confirmed = detectPkBarMode()
        val ok = confirmed?.mode == PkBarMode.UNION
        Log.d(
            TAG,
            "[ELF_GIVER] switchPkModeUnion ok=$ok preferCross=$preferCross " +
                "prefer=$preferLabel bar=${confirmed?.label}",
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
            if (BotController.state.value != BotRuntimeState.RUNNING) {
                Log.d(TAG, "[ELF_GIVER] focus spam aborted — bot not running at attempt=$attempt")
                return ElfBuffFocusHud.isRedHpBarVisible()
            }
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
