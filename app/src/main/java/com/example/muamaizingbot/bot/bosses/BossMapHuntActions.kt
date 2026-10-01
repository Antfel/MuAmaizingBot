package com.example.muamaizingbot.bot.bosses

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import com.example.muamaizingbot.bot.navigation.MapWindowActions
import com.example.muamaizingbot.bot.navigation.NavigationWaitActions
import com.example.muamaizingbot.bot.navigation.RandomSealActions
import com.example.muamaizingbot.capture.ScreenCaptureManager
import com.example.muamaizingbot.maps.CoordinateMapping
import com.example.muamaizingbot.maps.MapDefinition
import com.example.muamaizingbot.profile.FarmLocation
import com.example.muamaizingbot.profile.ProfileRepository
import com.example.muamaizingbot.vision.coord.RefCoords
import com.example.muamaizingbot.vision.map.MapPathLengthVision
import com.example.muamaizingbot.vision.navigation.NavigationVision
import com.example.muamaizingbot.vision.template.PcTemplateMatchResult
import kotlinx.coroutines.delay

/**
 * Open zone map → layout of all boss icons → classify each ROI → tap lives.
 *
 * Wire 1 captures parchment geometry. Boss slots (red) and golden slots (yellow)
 * are discovered separately. Classification never mixes colors: bosses use light
 * vs dark red; goldens use light vs dark yellow. Hunt order is bosses then goldens.
 */
object BossMapHuntActions {

    private const val TAG = "FarmBosses"
    const val BOSS_ALIVE = "templates/mu/ui/map/boss_alive.png"
    const val BOSS_DEAD = "templates/mu/ui/map/boss_dead.png"
    const val GOLDEN_ALIVE = "templates/mu/ui/map/golden_alive.png"
    const val GOLDEN_DEAD = "templates/mu/ui/map/golden_dead.png"
    /** Hunt / classify floor — below this, alive is a miss. */
    private const val HUNT_THRESHOLD = 0.90f
    /** Full-parchment discovery (boss templates). */
    internal const val DISCOVERY_THRESHOLD = 0.80f
    /** Golden icons are smaller / yellower — slightly looser discovery. */
    internal const val GOLDEN_DISCOVERY_THRESHOLD = 0.72f
    /** |alive-dead| at or above this trusts scores; below uses family color. */
    internal const val SCORE_DELTA_LARGE = 0.08f
    /** Boss: mean R-max(G,B) — light red = alive; dark / below floor = dead. */
    internal const val REDNESS_ALIVE_MIN = 25f
    /**
     * When ranking boss alive∪dead hits for NMS, boost alive so a live red face
     * is not replaced by a nearby boss_dead / yellow false match.
     */
    private const val ALIVE_NMS_SCORE_BOOST = 0.08f
    /** Golden: mean min(R,G)-B — light yellow = alive, dark yellow = dead. */
    internal const val YELLOWNESS_ALIVE_MIN = 28f
    /** Dedup union hits that sit on the same skull. */
    internal const val SLOT_NMS_RADIUS_PX = 48
    /** Padding around a planned icon for follow-up alive/dead checks. */
    internal const val PLAN_ROI_PAD_PX = 28
    private const val ARRIVAL_RADIUS = 10

    data class AliveDecision(
        val keep: Boolean,
        val path: String,
        val aliveScore: Float?,
        val deadScore: Float,
        val delta: Float,
        val redness: Float,
        val yellowness: Float = 0f,
    )

    /** Parchment map canvas @ 1280×720 (excludes left teleport list + chrome). */
    fun zoneMapContentRoi(frameWidth: Int, frameHeight: Int): Rect {
        val left = RefCoords.scaleX(560, frameWidth)
        // Divine Realm N calib ~y=140@1280; icons above ~120 are chrome / extrapolate OOB.
        val top = RefCoords.scaleY(240, frameHeight)
        val right = RefCoords.scaleX(2200, frameWidth)
        val bottom = RefCoords.scaleY(1280, frameHeight)
        return Rect(left, top, right.coerceAtMost(frameWidth), bottom.coerceAtMost(frameHeight))
    }

    suspend fun ensureMapOpen(): Boolean {
        if (MapWindowActions.isMapWindowOpen()) return true
        if (!MapWindowActions.openMapWindow(retries = 2, timeoutMs = 4_000)) {
            Log.w(TAG, "[HUNT] open map failed")
            return false
        }
        delay(400)
        return true
    }

    /** Full parchment scan: boss union and golden union kept as separate kinds. */
    suspend fun scanMapLayout(mapId: String, includeGolden: Boolean): MapHuntLayout {
        if (!ensureMapOpen()) {
            return MapHuntLayout(mapId, emptyList())
        }
        val frame = ScreenCaptureManager.getLatestBitmap() ?: run {
            Log.w(TAG, "[HUNT] no frame for layout scan")
            return MapHuntLayout(mapId, emptyList())
        }
        return try {
            val roi = zoneMapContentRoi(frame.width, frame.height)
            val rawAlive = NavigationVision.findAllOnFrame(frame, BOSS_ALIVE, DISCOVERY_THRESHOLD, roi)
            val rawDead = NavigationVision.findAllOnFrame(frame, BOSS_DEAD, DISCOVERY_THRESHOLD, roi)
            if (rawAlive.isEmpty() && rawDead.isEmpty()) {
                NavigationVision.logBestScore(BOSS_ALIVE, roi)
                NavigationVision.logBestScore(BOSS_DEAD, roi)
            }
            val bossSlots = mergeSlots(rawAlive + rawDead, HuntTargetKind.BOSS)
            val goldenSlots = if (includeGolden) {
                val rawGoldAlive =
                    NavigationVision.findAllOnFrame(
                        frame,
                        GOLDEN_ALIVE,
                        GOLDEN_DISCOVERY_THRESHOLD,
                        roi,
                    )
                val rawGoldDead =
                    NavigationVision.findAllOnFrame(
                        frame,
                        GOLDEN_DEAD,
                        GOLDEN_DISCOVERY_THRESHOLD,
                        roi,
                    )
                if (rawGoldAlive.isEmpty() && rawGoldDead.isEmpty()) {
                    NavigationVision.logBestScore(GOLDEN_ALIVE, roi)
                    NavigationVision.logBestScore(GOLDEN_DEAD, roi)
                }
                mergeSlots(rawGoldAlive + rawGoldDead, HuntTargetKind.GOLDEN)
            } else {
                emptyList()
            }
            // Goldens claim yellow icons first so boss_dead cannot steal them as BOSS.
            val merged = mergeBossAndGoldenSlots(bossSlots, goldenSlots)
            val slots = if (includeGolden) {
                retagYellowBossSlotsAsGolden(frame, merged)
            } else {
                merged
            }
            Log.d(
                TAG,
                "[HUNT] layout scan map=$mapId includeGolden=$includeGolden " +
                    "bossAlive=${rawAlive.size} bossDead=${rawDead.size} " +
                    "goldenTpl=${goldenSlots.size} slots=${slots.size} " +
                    "boss=${slots.count { it.kind == HuntTargetKind.BOSS }} " +
                    "gold=${slots.count { it.kind == HuntTargetKind.GOLDEN }} " +
                    slots.joinToString { "(${it.centerX},${it.centerY}:${it.kind.name.take(1)})" },
            )
            MapHuntLayout(mapId, slots)
        } finally {
            frame.recycle()
        }
    }

    internal fun mergeSlots(
        hits: List<PcTemplateMatchResult>,
        kind: HuntTargetKind,
    ): List<HuntSlot> {
        val r2 = SLOT_NMS_RADIUS_PX * SLOT_NMS_RADIUS_PX
        val kept = ArrayList<HuntSlot>(hits.size)
        val ranked = hits.sortedByDescending { hit ->
            val boost =
                if (kind == HuntTargetKind.BOSS &&
                    hit.templateName.contains("alive", ignoreCase = true)
                ) {
                    ALIVE_NMS_SCORE_BOOST
                } else {
                    0f
                }
            hit.score + boost
        }
        for (hit in ranked) {
            val tooClose = kept.any { slot ->
                val dx = slot.centerX - hit.centerX
                val dy = slot.centerY - hit.centerY
                dx * dx + dy * dy <= r2
            }
            if (!tooClose) {
                kept += HuntSlot(
                    centerX = hit.centerX,
                    centerY = hit.centerY,
                    bestX = hit.bestX,
                    bestY = hit.bestY,
                    templateWidth = hit.templateWidth,
                    templateHeight = hit.templateHeight,
                    templateName = hit.templateName,
                    kind = kind,
                )
            }
        }
        return kept
    }

    /**
     * Goldens claim parchment points first (yellow icons). Bosses only fill gaps
     * so a boss_dead false match cannot swallow a golden slot.
     */
    internal fun mergeBossAndGoldenSlots(
        bosses: List<HuntSlot>,
        goldens: List<HuntSlot>,
    ): List<HuntSlot> {
        val r2 = SLOT_NMS_RADIUS_PX * SLOT_NMS_RADIUS_PX
        val out = goldens.toMutableList()
        for (b in bosses) {
            val clash = out.any { g ->
                val dx = g.centerX - b.centerX
                val dy = g.centerY - b.centerY
                dx * dx + dy * dy <= r2
            }
            if (!clash) out += b
        }
        return out
    }

    /**
     * boss_dead often matches yellow skulls. Re-tag those slots to GOLDEN by color
     * so classify uses light/dark yellow instead of red.
     */
    internal fun retagYellowBossSlotsAsGolden(
        frame: Bitmap,
        slots: List<HuntSlot>,
    ): List<HuntSlot> {
        if (slots.isEmpty()) return slots
        var retagged = 0
        val out = ArrayList<HuntSlot>(slots.size)
        for (slot in slots) {
            if (slot.kind != HuntTargetKind.BOSS) {
                out += slot
                continue
            }
            val rect = probeRectPx(
                centerX = slot.centerX,
                centerY = slot.centerY,
                templateWidth = slot.templateWidth,
                templateHeight = slot.templateHeight,
                frameW = frame.width,
                frameH = frame.height,
                padPx = PLAN_ROI_PAD_PX,
            )
            val l = rect[0]
            val t = rect[1]
            val w = rect[2]
            val h = rect[3]
            if (w <= 0 || h <= 0) {
                out += slot
                continue
            }
            val crop = try {
                Bitmap.createBitmap(frame, l, t, w, h)
            } catch (_: Exception) {
                out += slot
                continue
            }
            try {
                val (redness, yellowness) = meanColorSignals(crop)
                if (isGoldenColorFamily(yellowness, redness)) {
                    retagged++
                    out += slot.copy(kind = HuntTargetKind.GOLDEN)
                } else {
                    out += slot
                }
            } finally {
                crop.recycle()
            }
        }
        if (retagged > 0) {
            Log.d(TAG, "[HUNT] retag yellow→GOLDEN n=$retagged")
        }
        return out
    }

    fun huntIconFrom(hit: PcTemplateMatchResult): HuntIcon = HuntIcon(
        centerX = hit.centerX,
        centerY = hit.centerY,
        bestX = hit.bestX,
        bestY = hit.bestY,
        templateWidth = hit.templateWidth,
        templateHeight = hit.templateHeight,
        score = hit.score,
        templateName = hit.templateName,
    )

    /**
     * Classify every layout slot. Returns live icons with **bosses first**, then goldens.
     */
    suspend fun classifyLayoutLives(layout: MapHuntLayout): List<HuntIcon> {
        if (layout.slots.isEmpty()) return emptyList()
        if (!ensureMapOpen()) return emptyList()
        val bosses = ArrayList<HuntIcon>()
        val goldens = ArrayList<HuntIcon>()
        for (slot in layout.slots) {
            when (val keep = probeSlotAlive(slot)) {
                true -> {
                    val icon = slot.toHuntIcon()
                    if (slot.kind == HuntTargetKind.GOLDEN) goldens += icon else bosses += icon
                }
                false, null -> Unit
            }
        }
        val lives = bosses + goldens
        Log.d(
            TAG,
            "[HUNT] classify map=${layout.mapId} lives=${lives.size}/${layout.slots.size} " +
                "bosses=${bosses.size} goldens=${goldens.size}",
        )
        return lives
    }

    /** True if any boss-kind slot on the layout is currently light-red alive. */
    suspend fun anyLiveBoss(layout: MapHuntLayout): Boolean {
        if (!ensureMapOpen()) return false
        for (slot in layout.slots) {
            if (slot.kind != HuntTargetKind.BOSS) continue
            if (probeSlotAlive(slot) == true) return true
        }
        return false
    }

    /**
     * Probe only a small ROI around [icon] (map must be open or will be opened).
     * `null` = could not read; `true`/`false` = still a hunt target.
     */
    suspend fun probePlannedIconAlive(icon: HuntIcon): Boolean? {
        return probeSlotAlive(
            HuntSlot(
                centerX = icon.centerX,
                centerY = icon.centerY,
                bestX = icon.bestX,
                bestY = icon.bestY,
                templateWidth = icon.templateWidth,
                templateHeight = icon.templateHeight,
                templateName = icon.templateName,
                kind = icon.kind,
            ),
        )
    }

    private suspend fun probeSlotAlive(slot: HuntSlot): Boolean? {
        if (!ensureMapOpen()) {
            Log.w(TAG, "[HUNT] roi open map failed")
            return null
        }
        val size = ScreenCaptureManager.peekLatestBitmapSize() ?: return null
        val rect = probeRectPx(
            centerX = slot.centerX,
            centerY = slot.centerY,
            templateWidth = slot.templateWidth,
            templateHeight = slot.templateHeight,
            frameW = size.first,
            frameH = size.second,
            padPx = PLAN_ROI_PAD_PX,
        )
        val crop = ScreenCaptureManager.copyRegion(rect[0], rect[1], rect[2], rect[3])
            ?: return null
        return try {
            val decision = classifyCrop(crop, slot.kind)
            Log.d(
                TAG,
                "[HUNT] roi (${slot.centerX},${slot.centerY}) kind=${slot.kind} " +
                    "alive=${decision.aliveScore?.let { "%.3f".format(it) } ?: "miss"} " +
                    "dead=${"%.3f".format(decision.deadScore)} " +
                    "delta=${"%.3f".format(decision.delta)} path=${decision.path} " +
                    "redness=${"%.1f".format(decision.redness)} " +
                    "yellow=${"%.1f".format(decision.yellowness)} keep=${decision.keep}",
            )
            decision.keep
        } finally {
            crop.recycle()
        }
    }

    private fun classifyCrop(crop: Bitmap, kind: HuntTargetKind): AliveDecision {
        val (redness, yellowness) = meanColorSignals(crop)
        return when (kind) {
            HuntTargetKind.GOLDEN -> {
                val aliveProbe = NavigationVision.probeOnFrame(crop, GOLDEN_ALIVE, roi = null)
                val deadProbe = NavigationVision.probeOnFrame(crop, GOLDEN_DEAD, roi = null)
                val aliveScore = aliveProbe.score.takeIf { it >= HUNT_THRESHOLD }
                decideGoldenAlive(aliveScore, deadProbe.score, yellowness, redness)
            }
            HuntTargetKind.BOSS -> {
                val aliveProbe = NavigationVision.probeOnFrame(crop, BOSS_ALIVE, roi = null)
                val deadProbe = NavigationVision.probeOnFrame(crop, BOSS_DEAD, roi = null)
                val aliveScore = aliveProbe.score.takeIf { it >= HUNT_THRESHOLD }
                decideBossAlive(aliveScore, deadProbe.score, redness, yellowness)
            }
        }
    }

    /**
     * ROI around an icon: `[left, top, width, height]` clipped to the frame.
     */
    internal fun probeRectPx(
        centerX: Int,
        centerY: Int,
        templateWidth: Int,
        templateHeight: Int,
        frameW: Int,
        frameH: Int,
        padPx: Int = PLAN_ROI_PAD_PX,
    ): IntArray {
        val halfW = (templateWidth / 2 + padPx).coerceAtLeast(padPx)
        val halfH = (templateHeight / 2 + padPx).coerceAtLeast(padPx)
        var l = (centerX - halfW).coerceAtLeast(0)
        var t = (centerY - halfH).coerceAtLeast(0)
        var r = (centerX + halfW).coerceAtMost(frameW)
        var b = (centerY + halfH).coerceAtMost(frameH)
        if (r <= l) {
            l = 0
            r = frameW.coerceAtLeast(1)
        }
        if (b <= t) {
            t = 0
            b = frameH.coerceAtLeast(1)
        }
        return intArrayOf(l, t, r - l, b - t)
    }

    suspend fun navigateToPlannedIcon(
        mapDef: MapDefinition,
        wireId: Int,
        icon: HuntIcon,
    ): Boolean {
        if (!MapWindowActions.isMapWindowOpen()) {
            if (!MapWindowActions.openMapWindow(retries = 2, timeoutMs = 4_000)) {
                Log.w(TAG, "[HUNT] planned tap open map failed")
                return false
            }
            delay(400)
        }
        val match = PcTemplateMatchResult(
            score = icon.score,
            bestX = icon.bestX,
            bestY = icon.bestY,
            templateWidth = icon.templateWidth,
            templateHeight = icon.templateHeight,
            templateName = icon.templateName,
            category = "plan",
        )
        return navigateToMatch(mapDef, wireId, match, candidateCount = 1)
    }

    /**
     * Boss only: light red vs dark red. Never compares to yellow.
     * Large score delta trusts templates; close scores need light-red **and**
     * alive template ≥ dead (dead winning on a "red" crop = dead icon / false light).
     */
    internal fun decideBossAlive(
        aliveScore: Float?,
        deadScore: Float,
        redness: Float,
        yellowness: Float = 0f,
    ): AliveDecision {
        val alive = aliveScore ?: 0f
        val delta = abs(alive - deadScore)
        if (delta >= SCORE_DELTA_LARGE) {
            val keep = aliveScore != null && alive > deadScore
            return AliveDecision(
                keep = keep,
                path = "score",
                aliveScore = aliveScore,
                deadScore = deadScore,
                delta = delta,
                redness = redness,
                yellowness = yellowness,
            )
        }
        val keep = aliveScore != null &&
            redness >= REDNESS_ALIVE_MIN &&
            alive >= deadScore
        return AliveDecision(
            keep = keep,
            path = "red",
            aliveScore = aliveScore,
            deadScore = deadScore,
            delta = delta,
            redness = redness,
            yellowness = yellowness,
        )
    }

    /**
     * Golden only: light yellow vs dark yellow. Never compares to red.
     */
    internal fun decideGoldenAlive(
        aliveScore: Float?,
        deadScore: Float,
        yellowness: Float,
        redness: Float = 0f,
    ): AliveDecision {
        val alive = aliveScore ?: 0f
        val delta = abs(alive - deadScore)
        if (delta >= SCORE_DELTA_LARGE) {
            val keep = when {
                aliveScore != null && alive > deadScore -> true
                // Template miss but clear light-yellow family (boss_dead often steals discovery).
                aliveScore == null && isGoldenColorFamily(yellowness, redness) -> true
                else -> false
            }
            return AliveDecision(
                keep = keep,
                path = if (aliveScore == null) "yellow" else "score",
                aliveScore = aliveScore,
                deadScore = deadScore,
                delta = delta,
                redness = redness,
                yellowness = yellowness,
            )
        }
        val keep = yellowness >= YELLOWNESS_ALIVE_MIN &&
            (aliveScore != null || isGoldenColorFamily(yellowness, redness))
        return AliveDecision(
            keep = keep,
            path = "yellow",
            aliveScore = aliveScore,
            deadScore = deadScore,
            delta = delta,
            redness = redness,
            yellowness = yellowness,
        )
    }

    /** Yellow family: yellowness floor and yellowness beats redness. */
    internal fun isGoldenColorFamily(yellowness: Float, redness: Float): Boolean =
        yellowness >= YELLOWNESS_ALIVE_MIN && yellowness > redness

    /** @deprecated Use [decideBossAlive] — kept name for older call sites/tests. */
    internal fun decideAlive(
        aliveScore: Float?,
        deadScore: Float,
        redness: Float,
        yellowness: Float = 0f,
    ): AliveDecision = decideBossAlive(aliveScore, deadScore, redness, yellowness)

    internal fun meanRedness(bitmap: Bitmap): Float = meanColorSignals(bitmap).first

    internal fun meanColorSignals(bitmap: Bitmap): Pair<Float, Float> {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return 0f to 0f
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        return meanColorSignalsArgb(pixels, w, h)
    }

    /** Mean of R - max(G, B) inside the inscribed circle. */
    internal fun meanRednessArgb(pixels: IntArray, width: Int, height: Int): Float =
        meanColorSignalsArgb(pixels, width, height).first

    /**
     * Inscribed-circle means: redness = R-max(G,B), yellowness = min(R,G)-B.
     */
    internal fun meanColorSignalsArgb(
        pixels: IntArray,
        width: Int,
        height: Int,
    ): Pair<Float, Float> {
        if (width <= 0 || height <= 0 || pixels.isEmpty()) return 0f to 0f
        val cx = (width - 1) / 2.0
        val cy = (height - 1) / 2.0
        val radius = min(width, height) / 2.0
        val r2 = radius * radius
        var sumRed = 0.0
        var sumYellow = 0.0
        var n = 0
        var i = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val dx = x - cx
                val dy = y - cy
                if (dx * dx + dy * dy <= r2) {
                    val c = pixels[i]
                    val r = (c shr 16) and 0xff
                    val g = (c shr 8) and 0xff
                    val b = c and 0xff
                    sumRed += r - max(g, b)
                    sumYellow += min(r, g) - b
                    n++
                }
                i++
            }
        }
        if (n == 0) return 0f to 0f
        return (sumRed / n).toFloat() to (sumYellow / n).toFloat()
    }

    /**
     * Tap best boss icon, convert pixel→game coords, close map, wait until HUD arrival.
     * Stores target on [BossHuntState].
     */
    suspend fun navigateToBestBoss(
        mapDef: MapDefinition,
        wireId: Int,
        includeGolden: Boolean,
    ): Boolean {
        val layout = scanMapLayout(mapDef.id, includeGolden)
        val lives = classifyLayoutLives(layout)
        if (lives.isEmpty()) {
            Log.d(TAG, "[HUNT] no boss icons on map")
            MapWindowActions.closeMapWindow()
            BossHuntState.clearBossTarget()
            return false
        }
        val first = lives.first()
        val match = PcTemplateMatchResult(
            score = first.score,
            bestX = first.bestX,
            bestY = first.bestY,
            templateWidth = first.templateWidth,
            templateHeight = first.templateHeight,
            templateName = first.templateName,
            category = "best",
        )
        return navigateToMatch(mapDef, wireId, match, candidateCount = lives.size)
    }

    private suspend fun navigateToMatch(
        mapDef: MapDefinition,
        wireId: Int,
        best: PcTemplateMatchResult,
        candidateCount: Int,
    ): Boolean {
        BossHuntState.noteHuntTap(best.centerX, best.centerY)
        val (fw, fh) = ScreenCaptureManager.peekLatestBitmapSize()
            ?: RefCoords.activeScreenSize()
        val refX = best.centerX * RefCoords.REF_WIDTH / fw
        val refY = best.centerY * RefCoords.REF_HEIGHT / fh
        val gameCoords = if (CoordinateMapping.hasMapping(mapDef)) {
            CoordinateMapping.pixelToMapCoord(mapDef, refX, refY)
        } else {
            null
        }

        Log.d(
            TAG,
            "[HUNT] tap boss score=${"%.3f".format(best.score)} " +
                "screen=(${best.centerX},${best.centerY}) ref=($refX,$refY) " +
                "game=${gameCoords?.let { "(${it.first},${it.second})" } ?: "null"} " +
                "tpl=${best.templateName} candidates=$candidateCount",
        )

        val bounds = mapDef.coordinateBounds
        if (gameCoords != null && bounds != null && !bounds.contains(gameCoords.first, gameCoords.second)) {
            Log.w(
                TAG,
                "[HUNT] game coords out of bounds " +
                    "(${gameCoords.first},${gameCoords.second}) " +
                    "vs [${bounds.xMin}..${bounds.xMax}]x[${bounds.yMin}..${bounds.yMax}] " +
                    "— skip icon (no arrival wait)",
            )
            return false
        }

        if (!NavigationVision.tapScreen(best.centerX, best.centerY, label = "boss_map_icon")) {
            return false
        }
        // Path paints while map is open → Far path may use Random Teleport Seal.
        val profile = ProfileRepository.currentProfile.value
        val randomEnabled = profile?.enableRandomTeleport != false
        val sealsUsed = if (randomEnabled) {
            val farMinDots = profile?.randomTeleportFarMinDots
                ?: MapPathLengthVision.FAR_MIN_DOTS
            RandomSealActions.maybeUseRandomIfFarPath(farMinDots)
        } else {
            Log.d(TAG, "[HUNT] Random Teleport disabled in profile — walk only")
            0
        }
        val arrivalTimeoutMs = RandomSealActions.arrivalTimeoutMs(sealsUsed)
        // Let the game start Auto Navigating; it often closes the map by itself.
        // Only tap close_x if the panel is still open — a blind close cancels pathing.
        delay(400)
        if (!MapWindowActions.closeMapWindowIfOpen()) {
            Log.w(TAG, "[HUNT] close map after boss tap failed — continuing")
        }

        if (gameCoords != null) {
            BossHuntState.setBossTarget(gameCoords.first, gameCoords.second)
            val target = FarmLocation(
                id = "boss_target",
                profile = "",
                type = "boss_target",
                name = "Boss",
                map = mapDef.id,
                wire = wireId,
                x = refX,
                y = refY,
                coordX = gameCoords.first,
                coordY = gameCoords.second,
                arrivalRadius = ARRIVAL_RADIUS,
            )
            Log.d(
                TAG,
                "[HUNT] wait arrival game=(${gameCoords.first},${gameCoords.second}) " +
                    "r=$ARRIVAL_RADIUS sealsUsed=$sealsUsed timeoutMs=$arrivalTimeoutMs",
            )
            val arrival = NavigationWaitActions.waitUntilArrivesAtCoordResult(
                target,
                mapDef,
                timeoutMs = arrivalTimeoutMs,
                acceptPathEndedAsArrival = true,
            )
            return when (arrival) {
                NavigationWaitActions.CoordArrivalResult.ARRIVED -> {
                    Log.d(TAG, "[HUNT] arrival confirmed → FIGHT (ensureFocusBoss next)")
                    true
                }
                // OCR often wrong at destination; hand off so FIGHT taps Focus Boss.
                NavigationWaitActions.CoordArrivalResult.TIMEOUT -> {
                    Log.w(TAG, "[HUNT] arrival timeout — proceed FIGHT for Focus Boss tap")
                    true
                }
                NavigationWaitActions.CoordArrivalResult.STUCK,
                NavigationWaitActions.CoordArrivalResult.DEAD,
                NavigationWaitActions.CoordArrivalResult.NO_COORDS,
                -> {
                    Log.w(TAG, "[HUNT] arrival $arrival — abort hop")
                    BossHuntState.clearBossTarget()
                    false
                }
            }
        }

        Log.w(TAG, "[HUNT] no affine for ${mapDef.id} — fallback auto_nav wait")
        BossHuntState.clearBossTarget()
        NavigationWaitActions.waitUntilNavigationComplete()
        delay(1_500)
        return true
    }

    /**
     * After combat-focus cleared an enemy, walk back to the last stored boss HUD coords.
     * Does not clear [BossHuntState] target. Returns false when no target / no affine.
     */
    suspend fun returnToStoredBossTarget(mapDef: MapDefinition, wireId: Int): Boolean {
        val gx = BossHuntState.targetCoordX
        val gy = BossHuntState.targetCoordY
        if (gx == null || gy == null) {
            Log.w(TAG, "[COMBAT_FOCUS] returnToBoss skipped — no stored target")
            return false
        }
        if (!CoordinateMapping.hasMapping(mapDef)) {
            Log.w(TAG, "[COMBAT_FOCUS] returnToBoss skipped — no affine for ${mapDef.id}")
            return false
        }
        val pixel = CoordinateMapping.mapCoordToPixel(mapDef, gx, gy)
        if (pixel == null) {
            Log.w(TAG, "[COMBAT_FOCUS] returnToBoss skipped — pixel null for ($gx,$gy)")
            return false
        }
        val (refX, refY) = pixel

        if (!MapWindowActions.openMapWindow(retries = 2, timeoutMs = 4_000)) {
            Log.w(TAG, "[COMBAT_FOCUS] returnToBoss open map failed")
            return false
        }
        delay(400)
        Log.d(
            TAG,
            "[COMBAT_FOCUS] returnToBoss game=($gx,$gy) ref=($refX,$refY)",
        )
        if (!NavigationVision.tap(refX, refY, label = "boss_return_tap")) {
            return false
        }
        val profile = ProfileRepository.currentProfile.value
        val randomEnabled = profile?.enableRandomTeleport != false
        val sealsUsed = if (randomEnabled) {
            val farMinDots = profile?.randomTeleportFarMinDots
                ?: MapPathLengthVision.FAR_MIN_DOTS
            RandomSealActions.maybeUseRandomIfFarPath(farMinDots)
        } else {
            0
        }
        val arrivalTimeoutMs = RandomSealActions.arrivalTimeoutMs(sealsUsed)
        delay(400)
        MapWindowActions.closeMapWindowIfOpen()

        val target = FarmLocation(
            id = "boss_return",
            profile = "",
            type = "boss_target",
            name = "BossReturn",
            map = mapDef.id,
            wire = wireId,
            x = refX,
            y = refY,
            coordX = gx,
            coordY = gy,
            arrivalRadius = ARRIVAL_RADIUS,
        )
        val arrival = NavigationWaitActions.waitUntilArrivesAtCoordResult(
            target,
            mapDef,
            timeoutMs = arrivalTimeoutMs,
            acceptPathEndedAsArrival = true,
        )
        Log.d(TAG, "[COMBAT_FOCUS] returnToBoss arrival=$arrival")
        return when (arrival) {
            NavigationWaitActions.CoordArrivalResult.ARRIVED,
            NavigationWaitActions.CoordArrivalResult.TIMEOUT,
            -> true
            NavigationWaitActions.CoordArrivalResult.STUCK,
            NavigationWaitActions.CoordArrivalResult.DEAD,
            NavigationWaitActions.CoordArrivalResult.NO_COORDS,
            -> false
        }
    }
}
