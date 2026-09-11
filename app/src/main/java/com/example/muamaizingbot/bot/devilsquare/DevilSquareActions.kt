package com.example.muamaizingbot.bot.devilsquare

import android.graphics.Rect
import android.util.Log
import com.example.muamaizingbot.bot.combat.GameActions
import com.example.muamaizingbot.bot.disconnect.DisconnectDetector
import com.example.muamaizingbot.bot.maintenance.ElfBuffNavigationActions
import com.example.muamaizingbot.bot.maintenance.HudValidationGate
import com.example.muamaizingbot.bot.maintenance.PetActions
import com.example.muamaizingbot.bot.maintenance.PotionPurchaseActions
import com.example.muamaizingbot.bot.maintenance.TopHudRailActions
import com.example.muamaizingbot.bot.navigation.MapWindowActions
import com.example.muamaizingbot.profile.BotProfile
import com.example.muamaizingbot.vision.coord.RefCoords
import com.example.muamaizingbot.vision.map.CurrentMapOcr
import com.example.muamaizingbot.vision.navigation.NavigationVision
import com.example.muamaizingbot.vision.template.PcTemplateMatchResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine

object DevilSquareActions {

    private const val TAG = "DevilSquare"

    private const val DAILY_GOAL_ICON = "templates/mu/ui/devil_square/daily_goal_icon.png"
    /** Gold DS mark on the Daily Goal row — not the 0/10 / 10/10 card body. */
    private const val DS_ICON = "templates/mu/ui/devil_square/daily_goal_ds_icon.png"
    private const val GO = "templates/mu/ui/devil_square/daily_goal_go.png"
    private const val PANEL_TITLE = "templates/mu/ui/devil_square/panel_title.png"
    private const val ENTER = "templates/mu/ui/devil_square/enter.png"
    private const val EVENT_OVER = "templates/mu/ui/devil_square/event_is_over.png"
    private const val CLAIM = "templates/mu/ui/devil_square/claim.png"
    /** "You don't have enough potions. Enter anyway?" Clue over C5. */
    private const val LOW_POTION_CLUE = "templates/mu/ui/devil_square/low_potion_clue.png"
    /** Red "Enter" on that Clue (not the gold C5 Enter). */
    private const val LOW_POTION_ENTER = "templates/mu/ui/devil_square/low_potion_enter.png"

    private const val MATCH = 0.72f
    private const val DS_ICON_MATCH = 0.76f
    private const val GO_ROW_Y_PAD = 48
    private const val DAILY_GOAL_SCROLLS = 4
    /** Chrome title "Event is over" — not the decorative side bars. */
    private const val EVENT_OVER_MATCH = 0.80f
    private const val CLAIM_MATCH = 0.72f
    /** Window chrome "Devil Square" — not the inner banner, not Enter. */
    private const val C5_TITLE_MATCH = 0.80f
    /** Gold "Enter" on C5 — old crop was cobblestone under the panel. */
    private const val ENTER_MATCH = 0.76f
    private const val LOW_POTION_CLUE_MATCH = 0.72f
    private const val LOW_POTION_ENTER_MATCH = 0.74f
    private const val ELF_RETRIES = 3
    private const val ENTER_RETRIES = 3

    /** Native 1280×720 → REF 2560×1440. */
    private const val DAILY_GOAL_REF_X = 1330
    private const val DAILY_GOAL_REF_Y = 296
    /** Enter button center on C5 (~715, 520 @ 1280×720). */
    private const val ENTER_REF_X = 1430
    private const val ENTER_REF_Y = 1040
    /** Clue red Enter (~515, 470 @ 1280×720). */
    private const val LOW_POTION_ENTER_REF_X = 1030
    private const val LOW_POTION_ENTER_REF_Y = 940
    /** Claim Normal on Event-is-over (~630, 610 @ 1280×720). Never 2x–6x. */
    private const val CLAIM_REF_X = 1260
    private const val CLAIM_REF_Y = 1220
    private const val DIAMOND_REF_X = 1522
    private const val DIAMOND_REF_Y = 728

    private const val C5_TIMEOUT_MS = 30_000L
    private const val ENTER_WAIT_MS = 12_000L
    private const val POST_TAP_MS = 800L
    private const val AUTO_WAIT_MS = 5_000L

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    enum class TickResult { INACTIVE, RUNNING, FINISHED }

    fun shouldHoldPriority(profile: BotProfile): Boolean {
        if (!profile.devilSquare.enabled) return false
        DevilSquareState.clearDayOffIfNewAccessDay()
        if (DevilSquareState.isHoldingPriority()) return true
        return shouldStart(profile)
    }

    private fun shouldStart(profile: BotProfile): Boolean {
        if (!profile.devilSquare.enabled) return false
        DevilSquareState.clearDayOffIfNewAccessDay()
        if (DevilSquareState.dayOff) return false
        if (!DevilSquareClock.isInJoinWindow()) return false
        return DevilSquareState.slotConsumedHour != DevilSquareClock.evenHourKey()
    }

    suspend fun tick(profile: BotProfile): TickResult {
        if (!profile.devilSquare.enabled) return TickResult.INACTIVE
        DevilSquareState.clearDayOffIfNewAccessDay()

        if (!DevilSquareState.processActive) {
            if (!shouldStart(profile)) return TickResult.INACTIVE
            Log.d(
                TAG,
                "[DS] start hour=${DevilSquareClock.evenHourKey()} " +
                    "pet=${profile.devilSquare.petType.toStorage()} " +
                    "potions=${profile.devilSquare.buyPotions} " +
                    "elf=${profile.devilSquare.prepElfBuff}",
            )
            DevilSquareState.beginProcess()
        }

        return when (DevilSquareState.phase) {
            DevilSquareState.Phase.IDLE -> TickResult.INACTIVE
            DevilSquareState.Phase.PREP_PET -> runPet(profile)
            DevilSquareState.Phase.PREP_ELF -> runElf(profile)
            DevilSquareState.Phase.PREP_POTIONS -> runPotions(profile)
            DevilSquareState.Phase.OPEN_DAILY_GOAL -> runDailyGoal()
            DevilSquareState.Phase.WAIT_C5 -> runC5()
            DevilSquareState.Phase.INSIDE_SETUP -> runInsideSetup()
            DevilSquareState.Phase.IN_EVENT -> runInEvent()
        }
    }

    suspend fun handleClaimIfVisible(): Boolean {
        if (!DevilSquareState.isInEvent()) return false
        if (!isEventOverVisible()) return false
        Log.d(TAG, "[DS] Event is over — Claim Normal")
        DisconnectDetector.beginUiAction("ds-claim")
        try {
            tapClaimNormal()
            delay(1_200)
            val last = DevilSquareState.remainingAtEnter
            if (last != null && last <= 1) {
                Log.d(TAG, "[DS] last access remaining=$last — day off")
                DevilSquareState.markDayOff()
            } else {
                DevilSquareState.finishAttempt(consumeSlot = true)
            }
            return true
        } finally {
            DisconnectDetector.endUiAction("ds-claim")
        }
    }

    private fun phaseAfterPet(profile: BotProfile): DevilSquareState.Phase {
        val ds = profile.devilSquare
        return when {
            ds.buyPotions -> DevilSquareState.Phase.PREP_POTIONS
            ds.prepElfBuff -> DevilSquareState.Phase.PREP_ELF
            else -> DevilSquareState.Phase.OPEN_DAILY_GOAL
        }
    }

    private fun phaseAfterPotions(profile: BotProfile): DevilSquareState.Phase {
        return if (profile.devilSquare.prepElfBuff) {
            DevilSquareState.Phase.PREP_ELF
        } else {
            DevilSquareState.Phase.OPEN_DAILY_GOAL
        }
    }

    private suspend fun runPet(profile: BotProfile): TickResult {
        val want = profile.devilSquare.petType
        Log.d(TAG, "[DS] phase=prep_pet want=${want.toStorage()}")
        val result = PetActions.validateWanted(want)
        if (result != PetActions.CheckResult.MATCH &&
            result != PetActions.CheckResult.SKIPPED
        ) {
            Log.w(TAG, "[DS] pet prep result=$result — continue")
        }
        DevilSquareState.setPhase(phaseAfterPet(profile))
        return TickResult.RUNNING
    }

    private suspend fun runElf(profile: BotProfile): TickResult {
        if (!profile.devilSquare.prepElfBuff) {
            DevilSquareState.setPhase(DevilSquareState.Phase.OPEN_DAILY_GOAL)
            return TickResult.RUNNING
        }
        Log.d(TAG, "[DS] phase=prep_elf renew")
        DisconnectDetector.beginUiAction("ds-elf")
        try {
            var ok = false
            repeat(ELF_RETRIES) { attempt ->
                ok = ElfBuffNavigationActions.goToElfBuffWithoutReturn()
                if (ok) return@repeat
                Log.w(TAG, "[DS] elf renew miss attempt=${attempt + 1}/$ELF_RETRIES")
                delay(800)
            }
            if (!ok) {
                Log.w(TAG, "[DS] elf renew failed — skip slot")
                DevilSquareState.finishAttempt(consumeSlot = true)
                return TickResult.FINISHED
            }
        } finally {
            DisconnectDetector.endUiAction("ds-elf")
        }
        DevilSquareState.setPhase(DevilSquareState.Phase.OPEN_DAILY_GOAL)
        return TickResult.RUNNING
    }

    private suspend fun runPotions(profile: BotProfile): TickResult {
        val ds = profile.devilSquare
        if (!ds.buyPotions) {
            DevilSquareState.setPhase(phaseAfterPotions(profile))
            return TickResult.RUNNING
        }
        Log.d(TAG, "[DS] phase=prep_potions ${ds.hpPotionStacks}+${ds.mpPotionStacks}")
        val bought = PotionPurchaseActions.buyPrecautionPacks(
            hpStacks = ds.hpPotionStacks,
            mpStacks = ds.mpPotionStacks,
        )
        if (!bought) {
            Log.w(TAG, "[DS] precaution potions failed — continue")
        }
        DevilSquareState.setPhase(phaseAfterPotions(profile))
        return TickResult.RUNNING
    }

    private suspend fun runDailyGoal(): TickResult {
        Log.d(TAG, "[DS] phase=open_daily_goal")
        HudValidationGate.ensureClearForHudProbe()
        DisconnectDetector.beginUiAction("ds-daily-goal")
        try {
            if (findDsIcon() == null) {
                TopHudRailActions.ensureExpanded()
                delay(400)
                if (!NavigationVision.tapTemplate(DAILY_GOAL_ICON, MATCH)) {
                    NavigationVision.logBestScore(DAILY_GOAL_ICON)
                    NavigationVision.tap(DAILY_GOAL_REF_X, DAILY_GOAL_REF_Y, "ds-daily-goal")
                }
                delay(1_600)
            }
            val icon = findDsIconWithScroll()
            if (icon == null) {
                Log.w(TAG, "[DS] Devil Square icon not found")
                NavigationVision.logBestScore(DS_ICON)
                if (!DevilSquareClock.isInJoinWindow()) {
                    DevilSquareState.finishAttempt(consumeSlot = true)
                    return TickResult.FINISHED
                }
                return TickResult.RUNNING
            }
            Log.d(
                TAG,
                "[DS] daily goal icon score=${"%.3f".format(icon.score)} " +
                    "at=(${icon.centerX},${icon.centerY})",
            )
            tapGoOnIconRow(icon)
            delay(POST_TAP_MS)
            DevilSquareState.setPhase(DevilSquareState.Phase.WAIT_C5)
            return TickResult.RUNNING
        } finally {
            DisconnectDetector.endUiAction("ds-daily-goal")
        }
    }

    private suspend fun findDsIcon(): PcTemplateMatchResult? {
        return NavigationVision.findTemplate(DS_ICON, DS_ICON_MATCH)
    }

    private suspend fun findDsIconWithScroll(): PcTemplateMatchResult? {
        repeat(DAILY_GOAL_SCROLLS) { attempt ->
            val hit = findDsIcon()
            if (hit != null) return hit
            if (attempt == DAILY_GOAL_SCROLLS - 1) return null
            val (w, h) = RefCoords.activeScreenSize()
            Log.d(TAG, "[DS] daily goal scroll for icon attempt=${attempt + 1}")
            NavigationVision.swipeScreen(
                w / 2,
                (h * 0.72f).toInt(),
                w / 2,
                (h * 0.38f).toInt(),
                280,
            )
            delay(500)
        }
        return null
    }

    private suspend fun tapGoOnIconRow(icon: PcTemplateMatchResult) {
        val (w, h) = RefCoords.activeScreenSize()
        val row = Rect(
            (icon.centerX + 40).coerceAtMost(w - 8),
            (icon.centerY - GO_ROW_Y_PAD).coerceAtLeast(0),
            (w * 0.92f).toInt().coerceAtMost(w),
            (icon.centerY + GO_ROW_Y_PAD).coerceAtMost(h),
        )
        val gos = NavigationVision.findAllTemplates(GO, MATCH, row)
        val go = gos.minByOrNull { kotlin.math.abs(it.centerY - icon.centerY) }
        if (go != null && kotlin.math.abs(go.centerY - icon.centerY) < GO_ROW_Y_PAD) {
            Log.d(
                TAG,
                "[DS] daily goal Go score=${"%.3f".format(go.score)} " +
                    "at=(${go.centerX},${go.centerY})",
            )
            NavigationVision.tapMatch(go)
            return
        }
        val fallbackX = (icon.centerX + (w * 0.28f).toInt()).coerceAtMost((w * 0.78f).toInt())
        Log.w(TAG, "[DS] daily goal Go miss — fallback x=$fallbackX y=${icon.centerY}")
        NavigationVision.tapScreen(fallbackX, icon.centerY, "ds-card-go")
    }

    private suspend fun runC5(): TickResult {
        Log.d(TAG, "[DS] phase=wait_c5")
        val deadline = System.currentTimeMillis() + C5_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (isC5Visible()) {
                Log.d(TAG, "[DS] C5 title confirmed")
                break
            }
            if (isOnDevilSquareHud()) {
                DevilSquareState.setPhase(DevilSquareState.Phase.INSIDE_SETUP)
                return TickResult.RUNNING
            }
            delay(700)
        }
        if (!isC5Visible()) {
            Log.w(TAG, "[DS] C5 timeout")
            DevilSquareState.finishAttempt(consumeSlot = true)
            return TickResult.FINISHED
        }

        val counts = readC5Counts()
        val remaining = counts.remaining
        val ticketOwned = counts.ticketOwned
        Log.d(TAG, "[DS] C5 remaining=$remaining ticketOwned=$ticketOwned ocr=\"${counts.raw}\"")

        if (remaining == 0) {
            Log.d(TAG, "[DS] remaining 0 — day off")
            DevilSquareState.markDayOff()
            NavigationVision.tapTemplate(MapWindowActions.CLOSE_X, MATCH)
            delay(400)
            return TickResult.FINISHED
        }
        if (ticketOwned == null || ticketOwned <= 0) {
            Log.d(TAG, "[DS] ticket missing/0 — skip slot, keep day")
            NavigationVision.tapTemplate(MapWindowActions.CLOSE_X, MATCH)
            delay(400)
            DevilSquareState.finishAttempt(consumeSlot = true)
            return TickResult.FINISHED
        }

        DevilSquareState.remainingAtEnter = remaining
        DisconnectDetector.beginUiAction("ds-enter")
        try {
            tapEnterButton()
            delay(600)
            dismissLowPotionClueIfVisible()
            if (!waitUntilInsideSquare()) {
                Log.w(TAG, "[DS] Enter did not load Devil Square")
                if (isC5Visible()) {
                    NavigationVision.tapTemplate(MapWindowActions.CLOSE_X, MATCH)
                    delay(400)
                }
                DevilSquareState.finishAttempt(consumeSlot = true)
                return TickResult.FINISHED
            }
        } finally {
            DisconnectDetector.endUiAction("ds-enter")
        }
        DevilSquareState.setPhase(DevilSquareState.Phase.INSIDE_SETUP)
        return TickResult.RUNNING
    }

    private suspend fun runInsideSetup(): TickResult {
        Log.d(TAG, "[DS] phase=inside_setup")
        val enteredDeadline = System.currentTimeMillis() + 8_000L
        while (System.currentTimeMillis() < enteredDeadline) {
            if (isEventOverVisible()) {
                handleClaimIfVisible()
                return TickResult.FINISHED
            }
            if (isOnDevilSquareHud()) break
            delay(400)
        }
        if (!isOnDevilSquareHud() && !isEventOverVisible()) {
            Log.w(TAG, "[DS] inside_setup without Devil Square HUD — abort")
            DevilSquareState.finishAttempt(consumeSlot = true)
            return TickResult.FINISHED
        }

        if (!DevilSquareState.diamondDone) {
            if (MapWindowActions.openMapWindow(retries = 2, timeoutMs = 4_000)) {
                delay(400)
                NavigationVision.tap(DIAMOND_REF_X, DIAMOND_REF_Y, "ds-diamond")
                delay(600)
                MapWindowActions.closeMapWindow()
            }
            DevilSquareState.diamondDone = true
        }
        if (!DevilSquareState.autoDone) {
            delay(AUTO_WAIT_MS)
            GameActions.ensureAutoMode()
            DevilSquareState.autoDone = true
        }
        DevilSquareState.setPhase(DevilSquareState.Phase.IN_EVENT)
        return TickResult.RUNNING
    }

    private suspend fun runInEvent(): TickResult {
        if (handleClaimIfVisible()) return TickResult.FINISHED
        if (isEventOverVisible()) {
            delay(400)
            return TickResult.RUNNING
        }
        GameActions.ensureAutoMode()
        delay(800)
        return TickResult.RUNNING
    }

    private suspend fun isC5Visible(): Boolean {
        return NavigationVision.findTemplate(PANEL_TITLE, C5_TITLE_MATCH) != null
    }

    private fun enterRoi(w: Int, h: Int): Rect {
        return Rect(
            (w * 0.48f).toInt(),
            (h * 0.66f).toInt(),
            (w * 0.72f).toInt(),
            (h * 0.80f).toInt(),
        )
    }

    private suspend fun tapEnterButton(): Boolean {
        val frame = NavigationVision.captureFrame()
        if (frame != null) {
            try {
                val roi = enterRoi(frame.width, frame.height)
                val probe = NavigationVision.probeOnFrame(frame, ENTER, roi)
                Log.d(
                    TAG,
                    "[DS] enter score=${"%.3f".format(probe.score)} " +
                        "at=(${probe.centerX},${probe.centerY})",
                )
                if (probe.score >= ENTER_MATCH) {
                    return NavigationVision.tapScreen(probe.centerX, probe.centerY, "ds-enter")
                }
            } finally {
                frame.recycle()
            }
        }
        Log.w(TAG, "[DS] enter template miss — fallback ref")
        return NavigationVision.tap(ENTER_REF_X, ENTER_REF_Y, "ds-enter")
    }

    private suspend fun waitUntilInsideSquare(): Boolean {
        val deadline = System.currentTimeMillis() + ENTER_WAIT_MS
        var taps = 1
        while (System.currentTimeMillis() < deadline) {
            if (isOnDevilSquareHud()) {
                Log.d(TAG, "[DS] Devil Square HUD confirmed")
                return true
            }
            if (isEventOverVisible()) return true
            if (dismissLowPotionClueIfVisible()) {
                delay(800)
                continue
            }
            if (isC5Visible() && taps < ENTER_RETRIES) {
                Log.w(TAG, "[DS] C5 still open after Enter — retry $taps")
                tapEnterButton()
                delay(600)
                dismissLowPotionClueIfVisible()
                taps++
                delay(1_200)
                continue
            }
            delay(500)
        }
        return isOnDevilSquareHud()
    }

    /**
     * After C5 Enter, MU may show Clue: "You don't have enough potions. Enter anyway?"
     * Always confirm with the red Enter (never Purchase).
     */
    private suspend fun dismissLowPotionClueIfVisible(): Boolean {
        val clue = NavigationVision.findTemplate(LOW_POTION_CLUE, LOW_POTION_CLUE_MATCH)
            ?: return false
        Log.d(
            TAG,
            "[DS] low-potion Clue score=${"%.3f".format(clue.score)} " +
                "at=(${clue.centerX},${clue.centerY}) → Enter",
        )
        val frame = NavigationVision.captureFrame()
        if (frame != null) {
            try {
                val roi = Rect(
                    (frame.width * 0.28f).toInt(),
                    (frame.height * 0.45f).toInt(),
                    (frame.width * 0.55f).toInt(),
                    (frame.height * 0.72f).toInt(),
                )
                val probe = NavigationVision.probeOnFrame(frame, LOW_POTION_ENTER, roi)
                Log.d(
                    TAG,
                    "[DS] low-potion Enter score=${"%.3f".format(probe.score)} " +
                        "at=(${probe.centerX},${probe.centerY})",
                )
                if (probe.score >= LOW_POTION_ENTER_MATCH) {
                    NavigationVision.tapScreen(probe.centerX, probe.centerY, "ds-low-potion-enter")
                    delay(700)
                    return true
                }
            } finally {
                frame.recycle()
            }
        }
        Log.w(TAG, "[DS] low-potion Enter miss — fallback ref")
        NavigationVision.tap(LOW_POTION_ENTER_REF_X, LOW_POTION_ENTER_REF_Y, "ds-low-potion-enter")
        delay(700)
        return true
    }

    private fun eventOverTitleRoi(w: Int, h: Int): Rect {
        return Rect(
            (w * 0.40f).toInt(),
            (h * 0.10f).toInt(),
            (w * 0.62f).toInt(),
            (h * 0.25f).toInt(),
        )
    }

    private fun claimNormalRoi(w: Int, h: Int): Rect {
        return Rect(
            (w * 0.42f).toInt(),
            (h * 0.80f).toInt(),
            (w * 0.58f).toInt(),
            (h * 0.90f).toInt(),
        )
    }

    private suspend fun isEventOverVisible(): Boolean {
        val frame = NavigationVision.captureFrame() ?: return false
        return try {
            val roi = eventOverTitleRoi(frame.width, frame.height)
            val probe = NavigationVision.probeOnFrame(frame, EVENT_OVER, roi)
            if (probe.score >= EVENT_OVER_MATCH) {
                Log.d(
                    TAG,
                    "[DS] Event is over title score=${"%.3f".format(probe.score)} " +
                        "at=(${probe.centerX},${probe.centerY})",
                )
                true
            } else {
                false
            }
        } finally {
            frame.recycle()
        }
    }

    private suspend fun tapClaimNormal(): Boolean {
        val frame = NavigationVision.captureFrame()
        if (frame != null) {
            try {
                val roi = claimNormalRoi(frame.width, frame.height)
                val probe = NavigationVision.probeOnFrame(frame, CLAIM, roi)
                Log.d(
                    TAG,
                    "[DS] claim score=${"%.3f".format(probe.score)} " +
                        "at=(${probe.centerX},${probe.centerY})",
                )
                if (probe.score >= CLAIM_MATCH) {
                    return NavigationVision.tapScreen(probe.centerX, probe.centerY, "ds-claim")
                }
            } finally {
                frame.recycle()
            }
        }
        Log.w(TAG, "[DS] claim template miss — fallback ref")
        return NavigationVision.tap(CLAIM_REF_X, CLAIM_REF_Y, "ds-claim")
    }

    private suspend fun isOnDevilSquareHud(): Boolean {
        val frame = NavigationVision.captureFrame() ?: return false
        return try {
            val raw = CurrentMapOcr.readRaw(frame) ?: return false
            raw.replace(" ", "").contains("devil", ignoreCase = true) &&
                raw.replace(" ", "").contains("square", ignoreCase = true)
        } finally {
            frame.recycle()
        }
    }

    private data class C5Counts(
        val remaining: Int?,
        val ticketOwned: Int?,
        val raw: String,
    )

    /**
     * Remaining + ticket from the same C5 panel crop. Ticket is the `N/1` cost
     * box — never a full-screen `ticket_0` template (that matched empty chrome).
     */
    private suspend fun readC5Counts(): C5Counts {
        val frame = NavigationVision.captureFrame() ?: return C5Counts(null, null, "")
        return try {
            val w = frame.width
            val h = frame.height
            val panel = Rect(
                (w * 0.48f).toInt(),
                (h * 0.48f).toInt(),
                (w * 0.82f).toInt(),
                (h * 0.72f).toInt(),
            )
            val panelText = ocrRoi(frame, panel).orEmpty()
            var remaining = parseRemaining(panelText)
            var ticketOwned = parseTicketOwned(panelText)
            if (ticketOwned == null) {
                val ticket = Rect(
                    (w * 0.47f).toInt(),
                    (h * 0.63f).toInt(),
                    (w * 0.62f).toInt(),
                    (h * 0.73f).toInt(),
                )
                val ticketText = ocrRoi(frame, ticket).orEmpty()
                ticketOwned = parseTicketOwned(ticketText)
                Log.d(TAG, "[DS] ticket ocr=\"${ticketText.replace('\n', ' ')}\"")
            }
            C5Counts(remaining, ticketOwned, panelText.replace('\n', ' '))
        } finally {
            frame.recycle()
        }
    }

    private suspend fun ocrRoi(frame: android.graphics.Bitmap, roi: Rect): String? {
        val crop = BitmapRegion(frame, roi) ?: return null
        return try {
            recognize(crop)
        } finally {
            crop.recycle()
        }
    }

    private fun parseRemaining(text: String): Int? {
        val compact = text.lowercase().replace(" ", "")
        val labeled = Regex("""(?:remaining|attempts|today)[^\d]{0,12}(\d{1,2})""").find(compact)
        if (labeled != null) return labeled.groupValues[1].toIntOrNull()
        val time = Regex("""(\d{1,2})time""").find(compact)
        return time?.groupValues?.get(1)?.toIntOrNull()
    }

    /** Ticket Cost `owned/required` (e.g. 0/1 or 2/1). Prefers required == 1. */
    private fun parseTicketOwned(text: String): Int? {
        val compact = text.lowercase().replace(" ", "")
        val hits = Regex("""(\d{1,2})/(\d{1,2})""").findAll(compact).toList()
        if (hits.isEmpty()) return null
        val preferred = hits.lastOrNull { it.groupValues[2] == "1" } ?: hits.last()
        return preferred.groupValues[1].toIntOrNull()
    }

    private suspend fun recognize(bitmap: android.graphics.Bitmap): String? {
        val image = InputImage.fromBitmap(bitmap, 0)
        return suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { cont.resume(it.text) }
                .addOnFailureListener { cont.resume(null) }
        }
    }

    private fun BitmapRegion(src: android.graphics.Bitmap, roi: Rect): android.graphics.Bitmap? {
        val l = roi.left.coerceIn(0, src.width - 1)
        val t = roi.top.coerceIn(0, src.height - 1)
        val w = (roi.right - l).coerceAtLeast(1).coerceAtMost(src.width - l)
        val h = (roi.bottom - t).coerceAtLeast(1).coerceAtMost(src.height - t)
        return try {
            android.graphics.Bitmap.createBitmap(src, l, t, w, h)
        } catch (_: Exception) {
            null
        }
    }
}
