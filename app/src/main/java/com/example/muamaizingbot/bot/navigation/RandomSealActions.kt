package com.example.muamaizingbot.bot.navigation

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.example.muamaizingbot.capture.ScreenCaptureManager
import com.example.muamaizingbot.settings.BotTiming
import com.example.muamaizingbot.settings.BotTimingCategory
import com.example.muamaizingbot.vision.coord.RefCoords
import com.example.muamaizingbot.vision.map.MapPathLengthVision
import com.example.muamaizingbot.vision.navigation.NavigationVision
import com.example.muamaizingbot.vision.roi.MuCombatRois
import com.example.muamaizingbot.vision.store.StoreMuCoinOcr
import com.example.muamaizingbot.vision.template.PcTemplateMatchResult
import kotlinx.coroutines.delay

/**
 * After a map destination tap, keep using map **Random** (Teleport Seal) while the
 * green path is Far (`dots >= threshold`), until Near (`dots < threshold`).
 *
 * When Random is empty, taps the empty button to open the MU Coin Store, checks
 * MU Coin balances (need ≥2000 in at least one), buys one pack (50 seals) if
 * affordable, closes the store, reopens the map, and continues measuring the path.
 * Auto-navigating keeps running during the purchase. Insufficient coins abort the
 * buy for that navigation hop (walk the rest of the way).
 *
 * Shared by all open-map navigation ([NavigationOrchestrator.tapVisualLocation],
 * Farm Bosses hunt, elf buff spots, etc.). War posts opt out.
 */
object RandomSealActions {

    private const val TAG = "RandomSeal"

    const val MAP_RANDOM_BUTTON = "templates/mu/ui/common/map_random_button.png"
    const val MAP_RANDOM_BUTTON_EMPTY = "templates/mu/ui/common/map_random_button_empty.png"
    private const val STORE_TAB = "templates/mu/ui/store/store_open_tab.png"
    private const val STORE_TITLE = "templates/mu/ui/store/store_title.png"

    private const val RANDOM_THRESHOLD = 0.82f
    private const val STORE_OPEN_THRESHOLD = 0.75f
    /** Same idea as [BossMapHuntActions] alive-over-dead; device deltas are tiny. */
    private const val ACTIVE_OVER_EMPTY_MARGIN = 0.0f

    /** Initial wait after destination tap / Random before first path sample. */
    private const val PATH_SETTLE_MS = 500L
    /** Poll until path dots appear (game paints green trail after tap/TP). */
    private const val PATH_WAIT_MS = 2_500L
    private const val PATH_POLL_MS = 200L
    /** Extra gap between the two Far confirmations before the first seal. */
    private const val STABLE_FAR_GAP_MS = 400L
    /** Settle after Random so path re-paints from new position. */
    private const val POST_RANDOM_MS = 1_400L

    private const val STORE_OPEN_TIMEOUT_MS = 8_000L
    private const val POST_EMPTY_TAP_MS = 800L
    private const val POST_ITEM_TAP_MS = 700L
    /** "Obtain Item" animation swallows taps right after Purchase. */
    private const val POST_PURCHASE_MS = 2_000L
    private const val CLOSE_SHOP_WAIT_MS = 1_000L
    private const val CLOSE_STORE_ATTEMPTS = 3
    private const val STORE_CLOSE_TIMEOUT_MS = 2_500L
    /** Store window close X @ 1280×720 (1120,110) in reference coords. */
    private const val STORE_CLOSE_X = 2240
    private const val STORE_CLOSE_Y = 220

    /** Walk-only arrival wait (no Random used this hop). */
    const val ARRIVAL_TIMEOUT_WALK_MS = 90_000L
    /** Arrival wait after at least one Random Teleport Seal this hop. */
    const val ARRIVAL_TIMEOUT_AFTER_RANDOM_MS = 30_000L

    fun arrivalTimeoutMs(sealsUsed: Int): Long =
        if (sealsUsed > 0) ARRIVAL_TIMEOUT_AFTER_RANDOM_MS else ARRIVAL_TIMEOUT_WALK_MS

    /**
     * While path is Far and Random is stocked, tap Random repeatedly until Near.
     * If Random is empty, buy one pack once, reopen the map, and keep going.
     * Map must stay open (caller closes after this returns).
     *
     * @param farMinDots profile threshold: dots >= this → Far.
     * @return number of Random taps performed (0 if Near already / skipped / buy failed).
     */
    suspend fun maybeUseRandomIfFarPath(
        farMinDots: Int = MapPathLengthVision.FAR_MIN_DOTS,
    ): Int {
        val threshold = farMinDots.coerceAtLeast(1)
        var sealsUsed = 0
        var purchasedThisHop = false
        while (true) {
            val requireStableFar = sealsUsed == 0 && !purchasedThisHop
            val measure = waitForPathMeasure(threshold, requireStableFar = requireStableFar)
            Log.d(
                TAG,
                "[SEAL] measure dots=${measure.dots} class=${measure.pathClass} " +
                    "threshold=$threshold sealsUsed=$sealsUsed stableFar=$requireStableFar",
            )
            when (measure.pathClass) {
                MapPathLengthVision.PathClass.NEAR -> {
                    Log.d(
                        TAG,
                        "[SEAL] path Near dots=${measure.dots} threshold=$threshold " +
                            "sealsUsed=$sealsUsed — done",
                    )
                    return sealsUsed
                }
                MapPathLengthVision.PathClass.UNKNOWN -> {
                    Log.w(
                        TAG,
                        "[SEAL] path not visible dots=${measure.dots} " +
                            "maskPix=${measure.maskPixels} sealsUsed=$sealsUsed — stop, walk",
                    )
                    return sealsUsed
                }
                MapPathLengthVision.PathClass.FAR -> {
                    Log.d(
                        TAG,
                        "[SEAL] path Far dots=${measure.dots} threshold=$threshold " +
                            "attempt=${sealsUsed + 1} — Random",
                    )
                }
            }

            val frame = NavigationVision.captureFrame()
            if (frame == null) {
                Log.w(TAG, "[SEAL] no frame for Random tap — stop, walk")
                return sealsUsed
            }
            val decision = try {
                resolveRandomTap(frame)
            } finally {
                frame.recycle()
            }

            when (decision.kind) {
                RandomDecision.Kind.TAP -> {
                    val match = decision.match ?: return sealsUsed
                    if (!NavigationVision.tapMatch(match)) {
                        Log.w(TAG, "[SEAL] Random tap failed — stop, walk")
                        return sealsUsed
                    }
                    sealsUsed++
                    Log.d(TAG, "[SEAL] Random tapped (stocked) sealsUsed=$sealsUsed")
                    delay(POST_RANDOM_MS)
                }
                RandomDecision.Kind.EMPTY -> {
                    if (purchasedThisHop) {
                        Log.d(
                            TAG,
                            "[SEAL] Random still empty after purchase — walk " +
                                "sealsUsed=$sealsUsed",
                        )
                        return sealsUsed
                    }
                    Log.d(TAG, "[SEAL] Random empty — buy pack (nav keeps walking)")
                    if (!purchaseSealPack(decision.match)) {
                        Log.w(TAG, "[SEAL] purchase failed — walk sealsUsed=$sealsUsed")
                        return sealsUsed
                    }
                    purchasedThisHop = true
                    // Map reopened inside purchase; loop re-measures path.
                }
                RandomDecision.Kind.MISS -> {
                    Log.w(TAG, "[SEAL] Random button miss — stop, walk sealsUsed=$sealsUsed")
                    return sealsUsed
                }
            }
        }
    }

    /**
     * Empty Random → MU Coin Store (item highlighted) → tap item → Purchase (1 pack / 50)
     * → close store → reopen map. Character keeps auto-navigating underneath.
     */
    private suspend fun purchaseSealPack(emptyMatch: PcTemplateMatchResult?): Boolean {
        val tappedEmpty = when {
            emptyMatch != null -> NavigationVision.tapMatch(emptyMatch)
            else -> NavigationVision.tapTemplate(
                MAP_RANDOM_BUTTON_EMPTY,
                RANDOM_THRESHOLD,
                randomButtonRoi(),
            )
        }
        if (!tappedEmpty) {
            Log.w(TAG, "[SEAL_BUY] empty Random tap failed")
            return false
        }
        delay(BotTiming.ms(POST_EMPTY_TAP_MS, BotTimingCategory.POST_TAP))

        if (!waitForStoreOpen()) {
            Log.w(TAG, "[SEAL_BUY] store did not open")
            ensureMapOpenAfterShop()
            return false
        }
        Log.d(TAG, "[SEAL_BUY] store open — check MU Coin balance")

        if (!hasEnoughMuCoinsForPack()) {
            Log.w(
                TAG,
                "[SEAL_BUY] insufficient MU coins (<${StoreMuCoinOcr.SEAL_PACK_COST}) — " +
                    "skip buy this nav, walk",
            )
            closeShop()
            ensureMapOpenAfterShop()
            return false
        }
        Log.d(TAG, "[SEAL_BUY] balance ok — tap Random Teleport Seal")

        if (!tapSealItem()) {
            Log.w(TAG, "[SEAL_BUY] seal item static tap failed")
            closeShop()
            ensureMapOpenAfterShop()
            return false
        }
        delay(BotTiming.ms(POST_ITEM_TAP_MS, BotTimingCategory.POST_TAP))

        if (!tapPurchaseButton()) {
            Log.w(TAG, "[SEAL_BUY] Purchase static tap failed")
            closeShop()
            ensureMapOpenAfterShop()
            return false
        }
        delay(BotTiming.ms(POST_PURCHASE_MS, BotTimingCategory.FIXED_SETTLE))
        Log.d(TAG, "[SEAL_BUY] Purchase tapped (1 pack = 50 seals)")

        if (!closeShop()) {
            Log.w(TAG, "[SEAL_BUY] store still open — cannot reopen map")
            return false
        }
        if (!ensureMapOpenAfterShop()) {
            Log.w(TAG, "[SEAL_BUY] map reopen failed after purchase")
            return false
        }
        Log.d(TAG, "[SEAL_BUY] done — map open, resume path check")
        return true
    }

    /**
     * Need ≥ [StoreMuCoinOcr.SEAL_PACK_COST] in at least one of the two MU Coin
     * balances (unbound / bound). OCR failure → treat as insufficient (fail closed).
     */
    private suspend fun hasEnoughMuCoinsForPack(): Boolean {
        val frame = NavigationVision.captureFrame() ?: run {
            Log.w(TAG, "[SEAL_BUY] no frame for MU Coin OCR")
            return false
        }
        return try {
            val balances = StoreMuCoinOcr.readBalances(frame)
            val ok = balances.canAfford()
            Log.d(
                TAG,
                "[SEAL_BUY] MU coins primary=${balances.primary} " +
                    "secondary=${balances.secondary} " +
                    "need=${StoreMuCoinOcr.SEAL_PACK_COST} afford=$ok " +
                    "raw=\"${balances.raw}\"",
            )
            ok
        } finally {
            frame.recycle()
        }
    }

    /** MU Coin Store window: identified by its tab strip or the "Store" title. */
    private suspend fun waitForStoreOpen(): Boolean {
        val deadline = System.currentTimeMillis() + BotTiming.ms(
            STORE_OPEN_TIMEOUT_MS,
            BotTimingCategory.SCREEN_LOAD,
        )
        while (System.currentTimeMillis() < deadline) {
            val frame = NavigationVision.captureFrame() ?: run {
                delay(PATH_POLL_MS)
                continue
            }
            try {
                val hit = NavigationVision.findOnFrame(
                    frame,
                    STORE_TAB,
                    STORE_OPEN_THRESHOLD,
                    MuCombatRois.storeTabRoi(frame),
                ) ?: NavigationVision.findOnFrame(
                    frame,
                    STORE_TITLE,
                    STORE_OPEN_THRESHOLD,
                    MuCombatRois.storeTitleRoi(frame),
                )
                if (hit != null) {
                    Log.d(TAG, "[SEAL_BUY] store detected score=${"%.3f".format(hit.score)}")
                    return true
                }
            } finally {
                frame.recycle()
            }
            delay(PATH_POLL_MS)
        }
        NavigationVision.logBestScore(STORE_TAB)
        NavigationVision.logBestScore(STORE_TITLE)
        return false
    }

    private suspend fun tapSealItem(): Boolean {
        val refX = MuCombatRois.STORE_SEAL_ITEM_TAP_REF_X
        val refY = MuCombatRois.STORE_SEAL_ITEM_TAP_REF_Y
        Log.d(TAG, "[SEAL_BUY] seal item STATIC tap ref=($refX,$refY)")
        return NavigationVision.tap(refX, refY, label = "store_seal_item")
    }

    private suspend fun tapPurchaseButton(): Boolean {
        val refX = MuCombatRois.STORE_PURCHASE_TAP_REF_X
        val refY = MuCombatRois.STORE_PURCHASE_TAP_REF_Y
        Log.d(TAG, "[SEAL_BUY] Purchase STATIC tap ref=($refX,$refY)")
        return NavigationVision.tap(refX, refY, label = "store_purchase")
    }

    /** Retries because the post-purchase item animation can swallow the first tap. */
    private suspend fun closeShop(): Boolean {
        repeat(CLOSE_STORE_ATTEMPTS) { attempt ->
            // Prefer close_x template (same orange X as map/store); fallback to store coords.
            val (w, h) = ScreenCaptureManager.peekLatestBitmapSize()
                ?: RefCoords.activeScreenSize()
            val closeRoi = MuCombatRois.storeCloseXRoi(w, h)
            val closedByTemplate = NavigationVision.tapTemplate(
                MapWindowActions.CLOSE_X,
                NavigationTemplateThresholds.closeX(),
                closeRoi,
            )
            if (!closedByTemplate) {
                Log.d(TAG, "[SEAL_BUY] close_x miss — fallback store close tap")
                NavigationVision.tap(STORE_CLOSE_X, STORE_CLOSE_Y)
            }
            delay(BotTiming.ms(CLOSE_SHOP_WAIT_MS, BotTimingCategory.POST_TAP))
            val gone = NavigationVision.waitUntilAbsent(
                STORE_TAB,
                STORE_OPEN_THRESHOLD,
                BotTiming.ms(STORE_CLOSE_TIMEOUT_MS, BotTimingCategory.SCREEN_LOAD),
            )
            Log.d(TAG, "[SEAL_BUY] store closed=$gone attempt=${attempt + 1}")
            if (gone) {
                return true
            }
        }
        return false
    }

    /**
     * Closing the store also leaves the map closed, and the store's close X sits where
     * the map's does, so [MapWindowActions.isMapWindowOpen] cannot be trusted until the
     * store is confirmed gone (callers check [closeShop] first).
     */
    private suspend fun ensureMapOpenAfterShop(): Boolean =
        MapWindowActions.openMapWindow(retries = 2, timeoutMs = 4_000)

    /**
     * Wait for green path to paint; return last measure (Far/Near/Unknown).
     * When [requireStableFar] and first sample is Far, confirm with a second Far
     * reading so stale trail paint after destination tap does not trigger Random.
     */
    private suspend fun waitForPathMeasure(
        farMinDots: Int,
        requireStableFar: Boolean,
    ): MapPathLengthVision.PathMeasure {
        delay(PATH_SETTLE_MS)
        val deadline = System.currentTimeMillis() + PATH_WAIT_MS
        var last = MapPathLengthVision.PathMeasure(
            0,
            MapPathLengthVision.PathClass.UNKNOWN,
            farMinDots = farMinDots,
        )
        while (true) {
            val frame = NavigationVision.captureFrame()
            if (frame != null) {
                try {
                    last = MapPathLengthVision.measure(frame, farMinDots)
                    if (last.dots > 0) {
                        if (!requireStableFar ||
                            last.pathClass != MapPathLengthVision.PathClass.FAR
                        ) {
                            return last
                        }
                        Log.d(
                            TAG,
                            "[SEAL] Far candidate dots=${last.dots} — confirm stable",
                        )
                        delay(STABLE_FAR_GAP_MS)
                        val confirmFrame = NavigationVision.captureFrame()
                        if (confirmFrame == null) {
                            return last
                        }
                        return try {
                            val confirmed = MapPathLengthVision.measure(confirmFrame, farMinDots)
                            Log.d(
                                TAG,
                                "[SEAL] Far confirm dots=${confirmed.dots} " +
                                    "class=${confirmed.pathClass} threshold=$farMinDots",
                            )
                            confirmed
                        } finally {
                            confirmFrame.recycle()
                        }
                    }
                } finally {
                    frame.recycle()
                }
            }
            if (System.currentTimeMillis() >= deadline) {
                return last
            }
            delay(PATH_POLL_MS)
        }
    }

    private data class RandomDecision(
        val kind: Kind,
        val match: PcTemplateMatchResult? = null,
    ) {
        enum class Kind { TAP, EMPTY, MISS }

        companion object {
            val MISS = RandomDecision(Kind.MISS)
            fun tap(match: PcTemplateMatchResult) = RandomDecision(Kind.TAP, match)
            fun empty(match: PcTemplateMatchResult? = null) = RandomDecision(Kind.EMPTY, match)
        }
    }

    /**
     * Find active Random in ROI; keep only if active score beats empty at the same patch.
     */
    private fun resolveRandomTap(frame: Bitmap): RandomDecision {
        val roi = randomButtonRoi(frame.width, frame.height)
        val activeHit = NavigationVision.findOnFrame(frame, MAP_RANDOM_BUTTON, RANDOM_THRESHOLD, roi)
        if (activeHit == null) {
            val activeProbe = NavigationVision.probeOnFrame(frame, MAP_RANDOM_BUTTON, roi)
            Log.w(
                TAG,
                "[SEAL] active miss best=${"%.3f".format(activeProbe.score)} " +
                    "at=(${activeProbe.bestX},${activeProbe.bestY})",
            )
            val emptyOnly = NavigationVision.findOnFrame(
                frame,
                MAP_RANDOM_BUTTON_EMPTY,
                RANDOM_THRESHOLD,
                roi,
            )
            if (emptyOnly != null) {
                Log.d(
                    TAG,
                    "[SEAL] active miss, empty hit score=${"%.3f".format(emptyOnly.score)} — EMPTY",
                )
                return RandomDecision.empty(emptyOnly)
            }
            return RandomDecision.MISS
        }

        val pad = 6
        val local = Rect(
            (activeHit.bestX - pad).coerceAtLeast(0),
            (activeHit.bestY - pad).coerceAtLeast(0),
            (activeHit.bestX + activeHit.templateWidth + pad).coerceAtMost(frame.width),
            (activeHit.bestY + activeHit.templateHeight + pad).coerceAtMost(frame.height),
        )
        val empty = NavigationVision.probeOnFrame(frame, MAP_RANDOM_BUTTON_EMPTY, local)
        val activeWins = activeHit.score >= empty.score + ACTIVE_OVER_EMPTY_MARGIN
        Log.d(
            TAG,
            "[SEAL] active_vs_empty at=(${activeHit.centerX},${activeHit.centerY}) " +
                "active=${"%.3f".format(activeHit.score)} empty=${"%.3f".format(empty.score)} " +
                "tap=$activeWins",
        )
        return if (activeWins) {
            RandomDecision.tap(activeHit)
        } else {
            val emptyHit = NavigationVision.findOnFrame(
                frame,
                MAP_RANDOM_BUTTON_EMPTY,
                RANDOM_THRESHOLD,
                roi,
            )
            RandomDecision.empty(emptyHit ?: activeHit)
        }
    }

    /** Bottom-right of parchment where Random sits @ 1280×720. */
    fun randomButtonRoi(
        frameWidth: Int = ScreenCaptureManager.peekLatestBitmapSize()?.first
            ?: RefCoords.activeScreenSize().first,
        frameHeight: Int = ScreenCaptureManager.peekLatestBitmapSize()?.second
            ?: RefCoords.activeScreenSize().second,
    ): Rect {
        val sx = frameWidth.toFloat() / 1280f
        val sy = frameHeight.toFloat() / 720f
        return Rect(
            (1040 * sx).toInt().coerceIn(0, frameWidth),
            (520 * sy).toInt().coerceIn(0, frameHeight),
            (1180 * sx).toInt().coerceIn(0, frameWidth),
            (680 * sy).toInt().coerceIn(0, frameHeight),
        )
    }
}
