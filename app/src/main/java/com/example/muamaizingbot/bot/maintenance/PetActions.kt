package com.example.muamaizingbot.bot.maintenance

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.example.muamaizingbot.bot.disconnect.DisconnectDetector
import com.example.muamaizingbot.bot.navigation.MapWindowActions
import com.example.muamaizingbot.bot.navigation.NavigationTemplateThresholds
import com.example.muamaizingbot.profile.BotProfile
import com.example.muamaizingbot.profile.PetType
import com.example.muamaizingbot.settings.BotTiming
import com.example.muamaizingbot.settings.BotTimingCategory
import com.example.muamaizingbot.vision.navigation.NavigationVision
import com.example.muamaizingbot.vision.navigation.ScrollSettleWait
import com.example.muamaizingbot.vision.store.StoreMuCoinOcr
import com.example.muamaizingbot.vision.template.PcTemplateMatchResult
import kotlinx.coroutines.delay

/**
 * Opens Gear (via Inventory HUD), validates the equipped pet slot against the
 * profile, and if missing/wrong tries to equip from the inventory bag:
 * wait for bag icons to finish loading → find inv icon (swipe if needed) →
 * tap → Equip → re-check slot.
 *
 * If the pet is not in inventory, opens the MU Coin Store (HUD expand → Store),
 * buys the configured pet (same Purchase flow as Random Teleport Seal), then
 * re-opens Gear and equips (again waiting for bag load + swipe search).
 *
 * Inventory chrome often appears before item art paints — searches never start
 * until the bag ROI has settled.
 */
object PetActions {

    private const val TAG = "PetActions"

    private const val INVENTORY_BUTTON = "templates/mu/ui/inventory.png"
    private const val INVENTORY_OPEN = "templates/mu/ui/inventory_open.png"
    private const val GEAR_OPEN = "templates/mu/ui/pet/gear_open.png"
    private const val PET_EMPTY = "templates/mu/ui/pet/pet_slot_empty.png"
    private const val PET_ANGEL = "templates/mu/ui/pet/pet_slot_angel.png"
    private const val PET_IMP = "templates/mu/ui/pet/pet_slot_imp.png"
    private const val PET_INV_ANGEL = "templates/mu/ui/pet/pet_inv_angel.png"
    /** Angel in bag without the green “better equip” arrow badge. */
    private const val PET_INV_ANGEL_PLAIN = "templates/mu/ui/pet/pet_inv_angel_plain.png"
    private const val PET_INV_IMP = "templates/mu/ui/pet/pet_inv_imp.png"
    /** Imp in bag without the green arrow badge. */
    private const val PET_INV_IMP_PLAIN = "templates/mu/ui/pet/pet_inv_imp_plain.png"
    private const val PET_EQUIP_BUTTON = "templates/mu/ui/pet/pet_equip_button.png"

    private const val HUD_EXPAND_ARROW = "templates/mu/ui/store/hud_expand_arrow.png"
    private const val HUD_STORE_ICON = "templates/mu/ui/store/hud_store_icon.png"
    private const val STORE_TAB = "templates/mu/ui/store/store_open_tab.png"
    private const val STORE_TITLE = "templates/mu/ui/store/store_title.png"
    private const val STORE_IMP_ITEM = "templates/mu/ui/store/store_imp_item.png"
    private const val STORE_IMP_ICON = "templates/mu/ui/store/store_imp_icon.png"
    private const val STORE_ANGEL_ITEM = "templates/mu/ui/store/store_angel_item.png"
    private const val STORE_ANGEL_ICON = "templates/mu/ui/store/store_angel_icon.png"
    private const val PURCHASE_BUTTON = "templates/mu/ui/store/store_purchase_button.png"

    private const val INVENTORY_BUTTON_THRESHOLD = 0.80f
    private const val PANEL_THRESHOLD = 0.75f
    private const val PET_THRESHOLD = 0.72f
    private const val INV_PET_THRESHOLD = 0.82f
    /** Equip vs Recycle share chrome; keep high to avoid Recycle (~0.39–0.86). */
    private const val EQUIP_BUTTON_THRESHOLD = 0.90f
    private const val HUD_EXPAND_THRESHOLD = 0.72f
    private const val HUD_STORE_THRESHOLD = 0.75f
    private const val STORE_OPEN_THRESHOLD = 0.75f
    private const val STORE_ITEM_THRESHOLD = 0.80f
    private const val PURCHASE_THRESHOLD = 0.80f
    /** Title band sits above the tile centre; tap the icon area instead. */
    private const val STORE_TITLE_TAP_OFFSET_RATIO = 0.10f

    private const val OPEN_TIMEOUT_MS = 5_000L
    private const val UI_SETTLE_MS = 700L
    private const val POLL_MS = 200L
    /**
     * Inventory item art can take several seconds to paint after the grid appears
     * (empty slots show first). Wait for the bag to finish loading before search.
     */
    private const val INV_LOAD_MIN_MS = 2_000L
    private const val INV_LOAD_SETTLE_TIMEOUT_MS = 8_000L
    /** Shorter settle after each bag swipe before probing again. */
    private const val INV_SWIPE_SETTLE_TIMEOUT_MS = 2_500L
    private const val INV_ICON_LOAD_TIMEOUT_MS = 2_500L
    /** Shorter probe after each bag swipe (icons already painted). */
    private const val INV_PAGE_PROBE_MS = 1_200L
    private const val INV_ICON_POLL_MS = 400L
    /** Swipes to reveal lower bag rows (new purchases often land off-screen). */
    private const val INV_SWIPE_MAX = 8
    private const val INV_SWIPE_SETTLE_MS = 400L
    private const val INV_SWIPE_DURATION_MS = 350L
    private const val EQUIP_POPUP_TIMEOUT_MS = 3_000L
    /** Extra settle after Store buy before reopening Gear / searching bag. */
    private const val POST_BUY_EQUIP_SETTLE_MS = 1_200L
    private const val STORE_OPEN_TIMEOUT_MS = 8_000L
    private const val PURCHASE_TIMEOUT_MS = 5_000L
    private const val POST_EXPAND_MS = 800L
    private const val POST_ITEM_TAP_MS = 700L
    private const val POST_PURCHASE_MS = 2_000L
    private const val CLOSE_STORE_WAIT_MS = 1_000L
    private const val CLOSE_STORE_ATTEMPTS = 3
    private const val STORE_CLOSE_TIMEOUT_MS = 2_500L
    /** Store window close X @ 1280×720 (1120,110) in reference coords. */
    private const val STORE_CLOSE_X = 2240
    private const val STORE_CLOSE_Y = 220
    /** Fallback tap for collapsed HUD expand @ 1280×720. */
    private const val EXPAND_FALLBACK_X = 1064
    private const val EXPAND_FALLBACK_Y = 58

    enum class PetSlotState {
        EMPTY,
        ANGEL,
        IMP,
        UNKNOWN,
    }

    enum class CheckResult {
        /** Pet disabled in profile. */
        SKIPPED,
        /** Equipped pet already matches profile. */
        MATCH,
        /** Equipped from inventory during this check. */
        EQUIPPED,
        /** Bought from Store and equipped. */
        PURCHASED,
        /** Empty/wrong, not in inventory, and Store buy failed / skipped. */
        NEED_PURCHASE,
        /** Store open/buy/equip after buy failed. */
        BUY_FAILED,
        /** Could not open Gear / Inventory. */
        OPEN_FAILED,
        /** Gear open but slot read failed. */
        READ_FAILED,
        /** Found in inventory but Equip tap / confirm failed. */
        EQUIP_FAILED,
    }

    /**
     * When [BotProfile.enablePet] is true: open Gear, validate slot, equip from
     * inventory if needed (else buy from Store then equip), then close panels.
     */
    suspend fun validateIfEnabled(profile: BotProfile): CheckResult {
        if (!profile.enablePet) {
            return CheckResult.SKIPPED
        }
        return DisconnectDetector.withUiAction("pet-validate") {
            val want = profile.petType
            Log.d(TAG, "[PET] validate start want=${want.toStorage()}")

            if (!openGearPanel()) {
                Log.w(TAG, "[PET] could not open Gear panel")
                closePanels()
                return@withUiAction CheckResult.OPEN_FAILED
            }

            val result = try {
                val slot = readPetSlot()
                when {
                    slot == PetSlotState.UNKNOWN -> {
                        Log.w(TAG, "[PET] slot read failed")
                        CheckResult.READ_FAILED
                    }
                    slotMatches(slot, want) -> {
                        Log.d(TAG, "[PET] already equipped want=${want.toStorage()}")
                        CheckResult.MATCH
                    }
                    else -> {
                        Log.d(
                            TAG,
                            "[PET] slot=$slot want=${want.toStorage()} → try inventory equip",
                        )
                        when (val equip = equipFromInventory(want)) {
                            CheckResult.NEED_PURCHASE -> buyAndEquip(want)
                            else -> equip
                        }
                    }
                }
            } finally {
                closePanels()
                closeStoreIfOpen()
            }
            Log.d(TAG, "[PET] validate done want=${want.toStorage()} result=$result")
            result
        }
    }

    private fun slotMatches(slot: PetSlotState, want: PetType): Boolean =
        when (want) {
            PetType.ANGEL -> slot == PetSlotState.ANGEL
            PetType.IMP -> slot == PetSlotState.IMP
        }

    /**
     * Inventory empty of wanted pet → close Gear → Store buy → Gear equip
     * (with bag swipe search — purchased pets are often below the first page).
     */
    private suspend fun buyAndEquip(want: PetType): CheckResult {
        Log.d(TAG, "[PET_BUY] not in inventory — Store purchase want=${want.toStorage()}")
        closePanels()
        DisconnectDetector.markBusy("pet-buy")

        if (!purchasePetFromStore(want)) {
            return CheckResult.BUY_FAILED
        }

        Log.d(TAG, "[PET_BUY] purchase ok — settle then equip want=${want.toStorage()}")
        delay(BotTiming.ms(POST_BUY_EQUIP_SETTLE_MS, BotTimingCategory.FIXED_SETTLE))

        if (!openGearPanel()) {
            Log.w(TAG, "[PET_BUY] could not reopen Gear after purchase")
            return CheckResult.BUY_FAILED
        }
        Log.d(TAG, "[PET_BUY] Gear reopened — searching inventory (with swipe)")
        return when (val equip = equipFromInventory(want)) {
            CheckResult.EQUIPPED -> {
                Log.d(TAG, "[PET_BUY] purchased and equipped want=${want.toStorage()}")
                CheckResult.PURCHASED
            }
            CheckResult.NEED_PURCHASE -> {
                Log.w(TAG, "[PET_BUY] bought but pet still not in inventory after swipe search")
                CheckResult.BUY_FAILED
            }
            else -> {
                Log.w(TAG, "[PET_BUY] bought but equip result=$equip")
                equip
            }
        }
    }

    /**
     * HUD expand → Store → MU Coin balance → tap pet tile → Purchase → close.
     * Mirrors [com.example.muamaizingbot.bot.navigation.RandomSealActions] buy path.
     */
    private suspend fun purchasePetFromStore(want: PetType): Boolean {
        if (!openStoreFromHud()) {
            Log.w(TAG, "[PET_BUY] could not open Store")
            return false
        }
        Log.d(TAG, "[PET_BUY] store open — check MU Coin balance")

        if (!hasEnoughMuCoinsForPet()) {
            Log.w(
                TAG,
                "[PET_BUY] insufficient MU coins (<${StoreMuCoinOcr.PET_COST})",
            )
            closeStoreIfOpen()
            return false
        }
        Log.d(TAG, "[PET_BUY] balance ok — tap pet item want=${want.toStorage()}")

        if (!tapStorePetItem(want)) {
            Log.w(TAG, "[PET_BUY] pet item not found want=${want.toStorage()}")
            closeStoreIfOpen()
            return false
        }
        delay(BotTiming.ms(POST_ITEM_TAP_MS, BotTimingCategory.POST_TAP))

        if (!tapPurchaseButton()) {
            Log.w(TAG, "[PET_BUY] Purchase button not found")
            closeStoreIfOpen()
            return false
        }
        delay(BotTiming.ms(POST_PURCHASE_MS, BotTimingCategory.FIXED_SETTLE))
        Log.d(TAG, "[PET_BUY] Purchase tapped want=${want.toStorage()}")

        if (!closeStoreIfOpen()) {
            Log.w(TAG, "[PET_BUY] store still open after purchase")
            return false
        }
        Log.d(TAG, "[PET_BUY] store closed")
        return true
    }

    private suspend fun openStoreFromHud(): Boolean {
        if (isStoreOpen()) {
            Log.d(TAG, "[PET_BUY] Store already open")
            return true
        }

        val hudRoi = { w: Int, h: Int -> hudIconRoi(w, h) }

        // Rail may already be expanded.
        val storeHit = NavigationVision.findTemplate(HUD_STORE_ICON, HUD_STORE_THRESHOLD)
        if (storeHit != null) {
            Log.d(
                TAG,
                "[PET_BUY] Store HUD icon score=${"%.3f".format(storeHit.score)} " +
                    "at=(${storeHit.centerX},${storeHit.centerY})",
            )
            if (!NavigationVision.tapMatch(storeHit)) {
                return false
            }
            return waitForStoreOpen()
        }

        Log.d(TAG, "[PET_BUY] expanding HUD rail")
        val expanded = NavigationVision.tapTemplate(HUD_EXPAND_ARROW, HUD_EXPAND_THRESHOLD)
            || run {
                Log.d(TAG, "[PET_BUY] expand template miss — fallback tap")
                NavigationVision.tapScreen(EXPAND_FALLBACK_X, EXPAND_FALLBACK_Y)
            }
        if (!expanded) {
            Log.w(TAG, "[PET_BUY] expand tap failed")
            NavigationVision.logBestScore(HUD_EXPAND_ARROW)
            return false
        }
        delay(BotTiming.ms(POST_EXPAND_MS, BotTimingCategory.POST_TAP))

        val deadline = System.currentTimeMillis() + BotTiming.ms(
            OPEN_TIMEOUT_MS,
            BotTimingCategory.SCREEN_LOAD,
        )
        while (System.currentTimeMillis() < deadline) {
            val frame = NavigationVision.captureFrame() ?: run {
                delay(POLL_MS)
                continue
            }
            try {
                val hit = NavigationVision.findOnFrame(
                    frame,
                    HUD_STORE_ICON,
                    HUD_STORE_THRESHOLD,
                    hudRoi(frame.width, frame.height),
                )
                if (hit != null) {
                    Log.d(
                        TAG,
                        "[PET_BUY] Store HUD after expand score=${"%.3f".format(hit.score)} " +
                            "at=(${hit.centerX},${hit.centerY})",
                    )
                    if (!NavigationVision.tapMatch(hit)) {
                        return false
                    }
                    return waitForStoreOpen()
                }
            } finally {
                frame.recycle()
            }
            delay(POLL_MS)
        }
        NavigationVision.logBestScore(HUD_STORE_ICON)
        return false
    }

    private suspend fun isStoreOpen(): Boolean {
        val frame = NavigationVision.captureFrame() ?: return false
        return try {
            NavigationVision.findOnFrame(frame, STORE_TAB, STORE_OPEN_THRESHOLD) != null ||
                NavigationVision.findOnFrame(frame, STORE_TITLE, STORE_OPEN_THRESHOLD) != null
        } finally {
            frame.recycle()
        }
    }

    private suspend fun waitForStoreOpen(): Boolean {
        val deadline = System.currentTimeMillis() + BotTiming.ms(
            STORE_OPEN_TIMEOUT_MS,
            BotTimingCategory.SCREEN_LOAD,
        )
        while (System.currentTimeMillis() < deadline) {
            val frame = NavigationVision.captureFrame() ?: run {
                delay(POLL_MS)
                continue
            }
            try {
                val hit = NavigationVision.findOnFrame(frame, STORE_TAB, STORE_OPEN_THRESHOLD)
                    ?: NavigationVision.findOnFrame(frame, STORE_TITLE, STORE_OPEN_THRESHOLD)
                if (hit != null) {
                    Log.d(TAG, "[PET_BUY] store detected score=${"%.3f".format(hit.score)}")
                    return true
                }
            } finally {
                frame.recycle()
            }
            delay(POLL_MS)
        }
        NavigationVision.logBestScore(STORE_TAB)
        NavigationVision.logBestScore(STORE_TITLE)
        return false
    }

    private suspend fun hasEnoughMuCoinsForPet(): Boolean {
        val frame = NavigationVision.captureFrame() ?: run {
            Log.w(TAG, "[PET_BUY] no frame for MU Coin OCR")
            return false
        }
        return try {
            val balances = StoreMuCoinOcr.readBalances(frame)
            val ok = balances.canAfford(StoreMuCoinOcr.PET_COST)
            Log.d(
                TAG,
                "[PET_BUY] MU coins primary=${balances.primary} " +
                    "secondary=${balances.secondary} " +
                    "need=${StoreMuCoinOcr.PET_COST} afford=$ok " +
                    "raw=\"${balances.raw}\"",
            )
            ok
        } finally {
            frame.recycle()
        }
    }

    private suspend fun tapStorePetItem(want: PetType): Boolean {
        val (itemPath, iconPath) = storePetTemplates(want)
        val frame = NavigationVision.captureFrame() ?: return false
        val roi = storeItemRoi(frame)
        val frameHeight = frame.height
        val found = try {
            val title = NavigationVision.findOnFrame(frame, itemPath, STORE_ITEM_THRESHOLD, roi)
            if (title != null) {
                title to true
            } else {
                NavigationVision.findOnFrame(frame, iconPath, STORE_ITEM_THRESHOLD, roi)
                    ?.let { it to false }
            }
        } finally {
            frame.recycle()
        }
        if (found == null) {
            NavigationVision.logBestScore(itemPath, roi)
            NavigationVision.logBestScore(iconPath, roi)
            return false
        }
        val (item, byTitle) = found
        val tapY = if (byTitle) {
            item.centerY + (frameHeight * STORE_TITLE_TAP_OFFSET_RATIO).toInt()
        } else {
            item.centerY
        }
        Log.d(
            TAG,
            "[PET_BUY] pet item score=${"%.3f".format(item.score)} " +
                "at=(${item.centerX},${item.centerY}) tapY=$tapY byTitle=$byTitle " +
                "want=${want.toStorage()}",
        )
        return NavigationVision.tapScreen(item.centerX, tapY)
    }

    private fun storePetTemplates(want: PetType): Pair<String, String> =
        when (want) {
            PetType.ANGEL -> STORE_ANGEL_ITEM to STORE_ANGEL_ICON
            PetType.IMP -> STORE_IMP_ITEM to STORE_IMP_ICON
        }

    private suspend fun tapPurchaseButton(): Boolean {
        val deadline = System.currentTimeMillis() + BotTiming.ms(
            PURCHASE_TIMEOUT_MS,
            BotTimingCategory.SCREEN_LOAD,
        )
        while (System.currentTimeMillis() < deadline) {
            val frame = NavigationVision.captureFrame() ?: run {
                delay(POLL_MS)
                continue
            }
            try {
                val hit = NavigationVision.findOnFrame(
                    frame,
                    PURCHASE_BUTTON,
                    PURCHASE_THRESHOLD,
                )
                if (hit != null) {
                    Log.d(
                        TAG,
                        "[PET_BUY] Purchase score=${"%.3f".format(hit.score)} " +
                            "at=(${hit.centerX},${hit.centerY})",
                    )
                    return NavigationVision.tapMatch(hit)
                }
            } finally {
                frame.recycle()
            }
            delay(POLL_MS)
        }
        NavigationVision.logBestScore(PURCHASE_BUTTON)
        return false
    }

    private suspend fun closeStoreIfOpen(): Boolean {
        if (!isStoreOpen()) {
            return true
        }
        repeat(CLOSE_STORE_ATTEMPTS) { attempt ->
            val closedByTemplate = NavigationVision.tapTemplate(
                MapWindowActions.CLOSE_X,
                NavigationTemplateThresholds.closeX(),
            )
            if (!closedByTemplate) {
                Log.d(TAG, "[PET_BUY] close_x miss — fallback store close tap")
                NavigationVision.tap(STORE_CLOSE_X, STORE_CLOSE_Y)
            }
            delay(BotTiming.ms(CLOSE_STORE_WAIT_MS, BotTimingCategory.POST_TAP))
            val gone = NavigationVision.waitUntilAbsent(
                STORE_TAB,
                STORE_OPEN_THRESHOLD,
                BotTiming.ms(STORE_CLOSE_TIMEOUT_MS, BotTimingCategory.SCREEN_LOAD),
            )
            Log.d(TAG, "[PET_BUY] store closed=$gone attempt=${attempt + 1}")
            if (gone) {
                return true
            }
        }
        return !isStoreOpen()
    }

    /** Item grid (exclude left category sidebar). */
    private fun storeItemRoi(frame: Bitmap): Rect {
        return Rect(
            (frame.width * 0.28f).toInt(),
            (frame.height * 0.12f).toInt(),
            (frame.width * 0.92f).toInt(),
            (frame.height * 0.72f).toInt(),
        )
    }

    /** Top-right HUD rail (expand arrow + Store icon). */
    private fun hudIconRoi(frameWidth: Int, frameHeight: Int): Rect {
        return Rect(
            (frameWidth * 0.55f).toInt().coerceIn(0, frameWidth),
            0,
            frameWidth,
            (frameHeight * 0.22f).toInt().coerceIn(0, frameHeight),
        )
    }

    /**
     * Inventory bag already open beside Gear: wait until item icons finish
     * painting, then find/tap pet → Equip → confirm Gear slot.
     * Searches the current page then swipes the bag; a weak false-positive tap
     * that never shows Equip continues searching instead of aborting.
     */
    private suspend fun equipFromInventory(want: PetType): CheckResult {
        waitForInventoryIconsLoaded()

        val paths = invTemplates(want)
        var sawCandidate = false
        var lastEquipMiss = false

        suspend fun tryPage(timeoutMs: Long, pageLabel: String): CheckResult? {
            val invMatch = probeInventoryPage(want, paths, timeoutMs) ?: return null
            sawCandidate = true
            Log.d(
                TAG,
                "[PET] inv pet hit page=$pageLabel score=${"%.3f".format(invMatch.score)} " +
                    "at=(${invMatch.centerX},${invMatch.centerY}) tpl=${invMatch.templateName}",
            )
            if (!NavigationVision.tapMatch(invMatch)) {
                Log.w(TAG, "[PET] inv pet tap failed page=$pageLabel")
                lastEquipMiss = true
                return null
            }
            delay(UI_SETTLE_MS)

            if (!waitAndTapEquip()) {
                Log.w(TAG, "[PET] Equip button miss page=$pageLabel — continue search")
                NavigationVision.logBestScore(PET_EQUIP_BUTTON)
                dismissItemPopupIfAny()
                lastEquipMiss = true
                return null
            }
            delay(UI_SETTLE_MS)

            val after = readPetSlot()
            return if (slotMatches(after, want)) {
                Log.d(TAG, "[PET] equip confirmed slot=$after")
                CheckResult.EQUIPPED
            } else {
                Log.w(TAG, "[PET] equip not confirmed slot=$after want=${want.toStorage()}")
                CheckResult.EQUIP_FAILED
            }
        }

        tryPage(INV_ICON_LOAD_TIMEOUT_MS, "0")?.let { return it }

        Log.d(TAG, "[PET] inv pet not confirmed on first page — swipe bag want=${want.toStorage()}")
        repeat(INV_SWIPE_MAX) { swipeIndex ->
            if (!swipeInventoryBag(revealLower = true)) {
                Log.w(TAG, "[PET] inv swipe failed index=$swipeIndex")
                return@repeat
            }
            delay(BotTiming.ms(INV_SWIPE_SETTLE_MS, BotTimingCategory.POST_TAP))
            waitForInventoryBagSettled(
                timeoutMs = BotTiming.ms(
                    INV_SWIPE_SETTLE_TIMEOUT_MS,
                    BotTimingCategory.SCREEN_LOAD,
                ),
                label = "pet_inv_after_swipe_${swipeIndex + 1}",
            )
            tryPage(INV_PAGE_PROBE_MS, "${swipeIndex + 1}")?.let { return it }
            Log.d(TAG, "[PET] inv swipe ${swipeIndex + 1}/$INV_SWIPE_MAX — still missing")
        }

        return when {
            lastEquipMiss -> {
                Log.w(TAG, "[PET] candidates tapped but Equip never confirmed want=${want.toStorage()}")
                CheckResult.EQUIP_FAILED
            }
            sawCandidate -> CheckResult.EQUIP_FAILED
            else -> {
                Log.w(TAG, "[PET] inv pet not found want=${want.toStorage()} (after swipe search)")
                paths.forEach { NavigationVision.logBestScore(it) }
                CheckResult.NEED_PURCHASE
            }
        }
    }

    /**
     * Bag chrome can appear before item sprites paint. Hold a minimum delay, then
     * wait until the bag ROI stops changing so search/swipe does not run on empty
     * placeholders.
     */
    private suspend fun waitForInventoryIconsLoaded() {
        Log.d(TAG, "[PET] waiting for inventory icon load (min=${INV_LOAD_MIN_MS}ms)")
        delay(BotTiming.ms(INV_LOAD_MIN_MS, BotTimingCategory.SCREEN_LOAD))
        val settled = waitForInventoryBagSettled(
            timeoutMs = BotTiming.ms(INV_LOAD_SETTLE_TIMEOUT_MS, BotTimingCategory.SCREEN_LOAD),
            label = "pet_inv_initial_load",
        )
        Log.d(TAG, "[PET] inventory icons ready settled=$settled — start search")
    }

    private suspend fun waitForInventoryBagSettled(
        timeoutMs: Long,
        label: String,
    ): Boolean {
        return ScrollSettleWait.waitForRegionSettled(
            label = label,
            timeoutMs = timeoutMs,
            regionOf = { frame ->
                val roi = inventorySearchRoi(frame.width, frame.height)
                if (roi.width() <= 8 || roi.height() <= 8) {
                    null
                } else {
                    Bitmap.createBitmap(frame, roi.left, roi.top, roi.width(), roi.height())
                }
            },
        )
    }

    /** Close a leftover item popup after a false inventory tap. */
    private suspend fun dismissItemPopupIfAny() {
        NavigationVision.tapTemplate(
            MapWindowActions.CLOSE_X,
            NavigationTemplateThresholds.closeX(),
        )
        delay(UI_SETTLE_MS / 2)
    }

    /**
     * Poll one bag page until [timeoutMs] for icon paint / match ≥ threshold.
     */
    private suspend fun probeInventoryPage(
        want: PetType,
        paths: List<String>,
        timeoutMs: Long,
    ): PcTemplateMatchResult? {
        val deadline = System.currentTimeMillis() + timeoutMs
        var attempt = 0
        var bestScore = 0f
        while (System.currentTimeMillis() < deadline) {
            attempt++
            val frame = NavigationVision.captureFrame() ?: run {
                delay(INV_ICON_POLL_MS)
                continue
            }
            try {
                val roi = inventorySearchRoi(frame.width, frame.height)
                val probe = bestInvPetProbe(frame, paths, roi)
                if (probe.score > bestScore) {
                    bestScore = probe.score
                }
                Log.d(
                    TAG,
                    "[PET] inv icon wait want=${want.toStorage()} attempt=$attempt " +
                        "score=${"%.3f".format(probe.score)} best=${"%.3f".format(bestScore)} " +
                        "tpl=${probe.templateName}",
                )
                if (probe.score >= INV_PET_THRESHOLD) {
                    return probe
                }
            } finally {
                frame.recycle()
            }
            delay(INV_ICON_POLL_MS)
        }
        Log.d(
            TAG,
            "[PET] inv page miss want=${want.toStorage()} " +
                "best=${"%.3f".format(bestScore)} thr=$INV_PET_THRESHOLD",
        )
        return null
    }

    /**
     * Vertical swipe inside the right-side inventory bag.
     * [revealLower]=true: finger up → scroll content down (see items below).
     */
    private suspend fun swipeInventoryBag(revealLower: Boolean): Boolean {
        val frame = NavigationVision.captureFrame() ?: return false
        val (cx, yFrom, yTo) = try {
            val roi = inventorySearchRoi(frame.width, frame.height)
            val x = roi.centerX()
            val yHigh = roi.top + (roi.height() * 0.22f).toInt()
            val yLow = roi.top + (roi.height() * 0.78f).toInt()
            if (revealLower) {
                Triple(x, yLow, yHigh)
            } else {
                Triple(x, yHigh, yLow)
            }
        } finally {
            frame.recycle()
        }
        Log.d(
            TAG,
            "[PET] inv swipe revealLower=$revealLower ($cx,$yFrom)->($cx,$yTo)",
        )
        return NavigationVision.swipeScreen(
            cx,
            yFrom,
            cx,
            yTo,
            INV_SWIPE_DURATION_MS,
        )
    }

    private fun bestInvPetProbe(
        frame: Bitmap,
        paths: List<String>,
        roi: Rect,
    ): PcTemplateMatchResult =
        paths
            .map { NavigationVision.probeOnFrame(frame, it, roi) }
            .maxBy { it.score }

    private suspend fun waitAndTapEquip(): Boolean {
        val deadline = System.currentTimeMillis() + EQUIP_POPUP_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val match = NavigationVision.findTemplate(PET_EQUIP_BUTTON, EQUIP_BUTTON_THRESHOLD)
            if (match != null) {
                Log.d(
                    TAG,
                    "[PET] Equip score=${"%.3f".format(match.score)} " +
                        "at=(${match.centerX},${match.centerY})",
                )
                return NavigationVision.tapMatch(match)
            }
            delay(POLL_MS)
        }
        return false
    }

    /** Badge + plain inventory templates (green arrow may or may not be present). */
    private fun invTemplates(want: PetType): List<String> =
        when (want) {
            PetType.ANGEL -> listOf(PET_INV_ANGEL, PET_INV_ANGEL_PLAIN)
            PetType.IMP -> listOf(PET_INV_IMP, PET_INV_IMP_PLAIN)
        }

    suspend fun isGearOpen(): Boolean =
        NavigationVision.findTemplate(GEAR_OPEN, PANEL_THRESHOLD) != null

    private suspend fun openGearPanel(): Boolean {
        if (isGearOpen() || canReadPetSlot()) {
            Log.d(TAG, "[PET] Gear already open / pet slot visible")
            return true
        }

        Log.d(TAG, "[PET] tapping Inventory HUD")
        if (!NavigationVision.tapTemplate(INVENTORY_BUTTON, INVENTORY_BUTTON_THRESHOLD)) {
            NavigationVision.logBestScore(INVENTORY_BUTTON)
            Log.w(TAG, "[PET] inventory button miss")
            return false
        }
        delay(UI_SETTLE_MS)

        val deadline = System.currentTimeMillis() + OPEN_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (isGearOpen()) {
                Log.d(TAG, "[PET] Gear open (title)")
                return true
            }
            // Dual layout: Inventory bag on the right, Gear on the left — both appear together.
            val bag = NavigationVision.findTemplate(INVENTORY_OPEN, PANEL_THRESHOLD)
            if (bag != null) {
                Log.d(TAG, "[PET] Inventory bag open — waiting for Gear title / pet slot")
                if (canReadPetSlot()) {
                    Log.d(TAG, "[PET] Gear open (pet slot visible beside bag)")
                    return true
                }
            }
            delay(POLL_MS)
        }

        NavigationVision.logBestScore(GEAR_OPEN)
        NavigationVision.logBestScore(INVENTORY_OPEN)
        NavigationVision.logBestScore(PET_EMPTY)
        NavigationVision.logBestScore(PET_ANGEL)
        NavigationVision.logBestScore(PET_IMP)
        return isGearOpen() || canReadPetSlot()
    }

    private suspend fun canReadPetSlot(): Boolean =
        readPetSlot() != PetSlotState.UNKNOWN

    /**
     * Score empty / angel / imp templates; pick the best above [PET_THRESHOLD].
     */
    suspend fun readPetSlot(): PetSlotState {
        val frame = NavigationVision.captureFrame() ?: run {
            Log.w(TAG, "[PET] no frame for slot read")
            return PetSlotState.UNKNOWN
        }
        return try {
            readPetSlotOnFrame(frame)
        } finally {
            frame.recycle()
        }
    }

    fun readPetSlotOnFrame(frame: Bitmap): PetSlotState {
        val roi = petSlotSearchRoi(frame.width, frame.height)
        val empty = NavigationVision.probeOnFrame(frame, PET_EMPTY, roi)
        val angel = NavigationVision.probeOnFrame(frame, PET_ANGEL, roi)
        val imp = NavigationVision.probeOnFrame(frame, PET_IMP, roi)
        Log.d(
            TAG,
            "[PET] scores empty=${"%.3f".format(empty.score)} " +
                "angel=${"%.3f".format(angel.score)} " +
                "imp=${"%.3f".format(imp.score)} roi=$roi",
        )

        data class Cand(val state: PetSlotState, val match: PcTemplateMatchResult)
        val best = listOf(
            Cand(PetSlotState.EMPTY, empty),
            Cand(PetSlotState.ANGEL, angel),
            Cand(PetSlotState.IMP, imp),
        ).maxBy { it.match.score }

        if (best.match.score < PET_THRESHOLD) {
            Log.w(TAG, "[PET] no slot match best=${"%.3f".format(best.match.score)}")
            return PetSlotState.UNKNOWN
        }
        Log.d(
            TAG,
            "[PET] slot=${best.state} score=${"%.3f".format(best.match.score)} " +
                "at=(${best.match.centerX},${best.match.centerY})",
        )
        return best.state
    }

    /**
     * Left/center Gear panel (Inventory bag sits on the right when both open).
     */
    fun petSlotSearchRoi(frameWidth: Int, frameHeight: Int): Rect {
        return Rect(
            (frameWidth * 0.35f).toInt().coerceIn(0, frameWidth),
            (frameHeight * 0.05f).toInt().coerceIn(0, frameHeight),
            (frameWidth * 0.60f).toInt().coerceIn(0, frameWidth),
            (frameHeight * 0.40f).toInt().coerceIn(0, frameHeight),
        )
    }

    /** Right-side Inventory bag grid when Gear+Inventory dual layout is open. */
    fun inventorySearchRoi(frameWidth: Int, frameHeight: Int): Rect {
        return Rect(
            (frameWidth * 0.68f).toInt().coerceIn(0, frameWidth),
            (frameHeight * 0.08f).toInt().coerceIn(0, frameHeight),
            (frameWidth * 0.98f).toInt().coerceIn(0, frameWidth),
            (frameHeight * 0.82f).toInt().coerceIn(0, frameHeight),
        )
    }

    private suspend fun closePanels() {
        repeat(2) {
            val open = isGearOpen() ||
                NavigationVision.findTemplate(INVENTORY_OPEN, PANEL_THRESHOLD) != null
            if (!open) {
                return
            }
            if (!NavigationVision.tapTemplate(
                    MapWindowActions.CLOSE_X,
                    NavigationTemplateThresholds.closeX(),
                )
            ) {
                Log.d(TAG, "[PET] close_x miss")
                return
            }
            delay(UI_SETTLE_MS)
        }
    }
}
