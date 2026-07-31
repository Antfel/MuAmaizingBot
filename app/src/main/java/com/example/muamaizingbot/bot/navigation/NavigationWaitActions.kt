package com.example.muamaizingbot.bot.navigation

import android.graphics.Bitmap
import android.util.Log
import com.example.muamaizingbot.bot.combat.DeathActions
import com.example.muamaizingbot.maps.MapDefinition
import com.example.muamaizingbot.maps.MapDefinitionRepository
import com.example.muamaizingbot.profile.FarmLocation
import com.example.muamaizingbot.settings.BotTiming
import com.example.muamaizingbot.settings.BotTimingCategory
import com.example.muamaizingbot.util.AdaptiveWait
import com.example.muamaizingbot.vision.BitmapRegionSimilarity
import com.example.muamaizingbot.vision.coordinate.CoordinateReader
import com.example.muamaizingbot.vision.map.CurrentMapOcr
import com.example.muamaizingbot.vision.navigation.NavigationVision
import com.example.muamaizingbot.vision.roi.ScaledRoi
import kotlin.math.abs
import kotlinx.coroutines.delay

object NavigationWaitActions {

    private const val TAG = "NavWait"
    /** Near spot tolerance when HUD OCR failed but farm coords still align (map check only). */
    private const val MAP_CHECK_NEAR_SPOT_TOLERANCE = 25
    private const val OPEN_MAP_OCR_SETTLE_MS = 500L
    private const val AUTO_NAV_TEMPLATE = "templates/mu/ui/common/auto_navigating.png"
    private const val AUTO_NAV_THRESHOLD = 0.70f
    private const val AUTO_NAV_TIMEOUT_MS = 180_000L
    private const val AUTO_NAV_POLL_MS = 1000L
    private const val AUTO_NAV_INITIAL_WAIT_MS = 2000L
    private const val AUTO_NAV_MISSES_TO_FINISH = 3
    private const val AUTO_NAV_START_ATTEMPTS = 4
    private const val AUTO_NAV_FINISH_GRACE_MS = 1500L

    private const val STABILITY_SAMPLES = 2
    private const val STABILITY_INTERVAL_MS = 500L
    private const val STABILITY_THRESHOLD = 0.98f
    private const val STABILITY_TIMEOUT_MS = 5000L

    /**
     * During spot arrival: if HUD coords stay unchanged this long, probe death screen.
     * Null OCR reads are ignored (do not reset the timer).
     */
    private const val COORD_STUCK_DEATH_CHECK_MS = 3_000L

    suspend fun waitUntilMapLoaded(mapDef: MapDefinition): Boolean {
        val navigation = mapDef.navigation ?: return false
        val timeoutMs = BotTiming.ms(
            navigation.enterWaitSeconds * 1000L,
            BotTimingCategory.SCREEN_LOAD,
        )

        val loaded = AdaptiveWait.until(
            timeoutMs = timeoutMs,
            pollMs = 500L,
            label = "map_loaded",
        ) {
            isOnConfiguredMap(mapDef, null) && !MapWindowActions.isMapWindowOpen()
        }
        if (loaded) {
            rememberMap(mapDef.id, "map_load")
            Log.d(TAG, "[MAP_LOAD] confirmed map=${mapDef.id}")
        } else {
            Log.w(TAG, "[MAP_LOAD] timeout map=${mapDef.id}")
        }
        return loaded
    }

    /** Poll until the in-world map name OCR matches (after teleport / loading). */
    suspend fun waitUntilWorldReady(mapDef: MapDefinition): Boolean {
        val navigation = mapDef.navigation ?: return true
        val timeoutMs = BotTiming.ms(
            navigation.enterWaitSeconds * 1000L,
            BotTimingCategory.SCREEN_LOAD,
        )

        val ready = AdaptiveWait.until(timeoutMs = timeoutMs, label = "world_ready") {
            isOnConfiguredMap(mapDef, null)
        }
        if (ready) {
            Log.d(TAG, "[WORLD_READY] map=${mapDef.id}")
        } else {
            Log.w(TAG, "[WORLD_READY] timeout map=${mapDef.id}; continuing")
        }
        return true
    }

    /** HUD + screen stable — safe to open zone map / wire UI (avoids false taps while loading). */
    suspend fun waitUntilZoneUiReady(mapDef: MapDefinition): Boolean {
        val navigation = mapDef.navigation ?: return waitUntilUiSettled()
        val timeoutMs = BotTiming.ms(
            navigation.enterWaitSeconds * 1000L,
            BotTimingCategory.SCREEN_LOAD,
        )
        Log.d(TAG, "[ZONE_UI] waiting map=${mapDef.id}")

        var hudStable = 0
        val hudReady = AdaptiveWait.until(timeoutMs = timeoutMs, label = "zone_hud_stable") {
            if (isOnConfiguredMap(mapDef, null)) {
                hudStable++
                hudStable >= 2
            } else {
                hudStable = 0
                false
            }
        }

        if (!hudReady) {
            Log.w(TAG, "[ZONE_UI] HUD not stable map=${mapDef.id}")
        }

        val settled = waitForScreenStability()
        if (settled) {
            Log.d(TAG, "[ZONE_UI] ready map=${mapDef.id}")
        } else {
            Log.w(TAG, "[ZONE_UI] screen not settled map=${mapDef.id}; continuing")
        }
        return hudReady && settled
    }

    /** World teleport list open while already in-zone (wrong UI for wire switch). */
    suspend fun isWorldMapListVisible(mapDef: MapDefinition): Boolean {
        val headTemplate = mapDef.navigation?.mapHeadTemplate ?: return false
        if (headTemplate.isBlank() || !MapWindowActions.isMapWindowOpen()) {
            return false
        }
        return NavigationVision.findTemplate(headTemplate, 0.75f) != null
    }

    suspend fun waitUntilUiSettled(): Boolean {
        return waitForScreenStability()
    }

    enum class MapPresence {
        /** HUD zone-name OCR matches [MapDefinition.name]. */
        OCR,
        @Deprecated("Use OCR", ReplaceWith("OCR"))
        TEMPLATE,
        COORDS_AT_SPOT,
        COORDS_NEAR_SPOT,
        /** HUD OCR was garbage, but the recent positively observed map still matches. */
        TRUSTED_MEMORY,
        NONE,
    }

    /**
     * True when top-right HUD OCR reads [MapDefinition.name] (digit-safe).
     * On weak HUD read, opens the zone map and OCRs the title band.
     * [threshold] is ignored — kept for call-site compatibility.
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun isCurrentMap(mapDef: MapDefinition, threshold: Float? = null): Boolean {
        val frame = NavigationVision.captureFrame() ?: return false
        val hud = try {
            CurrentMapOcr.read(frame, mapDef)
        } finally {
            frame.recycle()
        }
        if (hud.matched) {
            rememberMap(mapDef.id, "hud_ocr")
            return true
        }
        val knownHudMapId = resolveKnownMapId(hud.rawText)
        if (knownHudMapId != null) {
            rememberMap(knownHudMapId, "hud_other")
            Log.d(TAG, "[MAP_MEMORY] clear other map=$knownHudMapId expected=${mapDef.id}")
            return false
        }
        val trustedMapId = TrustedCurrentMapMemory.trustedMapId()
        if (trustedMapId != null) {
            val matches = trustedMapId == mapDef.id
            Log.d(
                TAG,
                "[MAP_MEMORY] trusted map=$trustedMapId expected=${mapDef.id} " +
                    "matched=$matches source=hud_garbage",
            )
            return matches
        }
        if (!CurrentMapOcr.isWeak(hud)) {
            return false
        }
        val openOcr = confirmViaOpenMapOcr(mapDef) ?: return false
        if (openOcr.matched) {
            rememberMap(mapDef.id, "open_map_ocr")
            return true
        }
        resolveKnownMapId(openOcr.rawText)?.let { rememberMap(it, "open_map_other") }
        return false
    }

    suspend fun detectMapPresence(
        mapDef: MapDefinition,
        farmSpot: FarmLocation?,
    ): MapPresence {
        val frame = NavigationVision.captureFrame() ?: return MapPresence.NONE
        val ocr = try {
            CurrentMapOcr.read(frame, mapDef)
        } finally {
            frame.recycle()
        }
        if (ocr.matched) {
            rememberMap(mapDef.id, "hud_ocr")
            return MapPresence.OCR
        }

        val knownHudMapId = resolveKnownMapId(ocr.rawText)
        if (knownHudMapId != null) {
            rememberMap(knownHudMapId, "hud_other")
            Log.d(TAG, "[MAP_MEMORY] clear other map=$knownHudMapId expected=${mapDef.id}")
            return MapPresence.NONE
        }

        // Weak HUD (empty / garbage / wrong digit) → open zone map title OCR.
        if (CurrentMapOcr.isWeak(ocr)) {
            val trustedMapId = TrustedCurrentMapMemory.trustedMapId()
            if (trustedMapId != null) {
                val matches = trustedMapId == mapDef.id
                Log.d(
                    TAG,
                    "[MAP_MEMORY] trusted map=$trustedMapId expected=${mapDef.id} " +
                        "matched=$matches source=hud_garbage",
                )
                return if (matches) MapPresence.TRUSTED_MEMORY else MapPresence.NONE
            }
            val openOcr = confirmViaOpenMapOcr(mapDef)
            if (openOcr?.matched == true) {
                rememberMap(mapDef.id, "open_map_ocr")
                return MapPresence.OCR
            }
            val knownOpenMapId = openOcr?.let { resolveKnownMapId(it.rawText) }
            if (knownOpenMapId != null) {
                rememberMap(knownOpenMapId, "open_map_other")
                Log.d(TAG, "[MAP_MEMORY] clear other map=$knownOpenMapId expected=${mapDef.id}")
                return MapPresence.NONE
            }
            // Clear wrong-map text from open panel — do not soft-match coords.
            if (openOcr != null && openOcr.rawText.isNotBlank()) {
                return MapPresence.NONE
            }
        }

        // OCR empty/garbage only: allow farm-spot coords as soft presence.
        // If OCR clearly read another map name, do not fall back (sibling false path).
        if (ocr.rawText.isNotBlank()) {
            return MapPresence.NONE
        }
        if (farmSpot == null || farmSpot.map != mapDef.id) {
            return MapPresence.NONE
        }
        if (isAtFarmSpot(farmSpot, mapDef)) {
            return MapPresence.COORDS_AT_SPOT
        }
        if (isNearFarmSpot(farmSpot, mapDef, MAP_CHECK_NEAR_SPOT_TOLERANCE)) {
            return MapPresence.COORDS_NEAR_SPOT
        }
        return MapPresence.NONE
    }

    /**
     * Opens the zone map, OCRs native ROI (400,90)–(750,120) @ 1280×720, then closes.
     * Returns null if the map window could not be opened / no frame.
     */
    private suspend fun confirmViaOpenMapOcr(mapDef: MapDefinition): CurrentMapOcr.ReadResult? {
        Log.d(TAG, "[MAP_OCR] HUD weak — open-map title fallback map=${mapDef.id}")
        if (!MapWindowActions.ensureMapWindowOpen(retries = 2, timeoutMs = 4_000)) {
            Log.w(TAG, "[MAP_OCR] open-map fallback: map window failed")
            return null
        }
        delay(BotTiming.ms(OPEN_MAP_OCR_SETTLE_MS, BotTimingCategory.POST_TAP))
        val frame = NavigationVision.captureFrame()
        if (frame == null) {
            MapWindowActions.closeMapWindowIfOpen()
            return null
        }
        val result = try {
            CurrentMapOcr.readOpenMap(frame, mapDef)
        } finally {
            frame.recycle()
            MapWindowActions.closeMapWindowIfOpen()
        }
        return result
    }

    private fun resolveKnownMapId(raw: String): String? {
        return CurrentMapOcr.resolveKnownMapId(
            raw,
            MapDefinitionRepository.allMaps().map { map ->
                map.id to map.name.ifBlank { map.id }
            },
        )
    }

    private fun rememberMap(mapId: String, source: String) {
        TrustedCurrentMapMemory.record(mapId)
        Log.d(TAG, "[MAP_MEMORY] record map=$mapId source=$source")
    }

    suspend fun isOnConfiguredMap(mapDef: MapDefinition, farmSpot: FarmLocation?): Boolean {
        return detectMapPresence(mapDef, farmSpot) != MapPresence.NONE
    }

    suspend fun isAtFarmSpot(location: FarmLocation, mapDef: MapDefinition?): Boolean {
        return coordDistanceToSpot(location, mapDef)?.let { it <= location.arrivalRadius } == true
    }

    /** True when HUD coords are within the farm radius (looser than arrival). */
    suspend fun isWithinFarmRadius(location: FarmLocation, mapDef: MapDefinition?): Boolean {
        return coordDistanceToSpot(location, mapDef)?.let { it <= location.farmRadius } == true
    }

    private suspend fun isNearFarmSpot(
        location: FarmLocation,
        mapDef: MapDefinition?,
        tolerance: Int,
    ): Boolean {
        return coordDistanceToSpot(location, mapDef)?.let { it <= tolerance } == true
    }

    private suspend fun coordDistanceToSpot(
        location: FarmLocation,
        mapDef: MapDefinition?,
    ): Int? {
        if (location.coordX == null || location.coordY == null) {
            return null
        }
        val current = readHudCoordinates(mapDef) ?: return null
        return manhattanDistance(
            current.first,
            current.second,
            location.coordX,
            location.coordY,
        )
    }

    suspend fun waitUntilNavigationComplete(): Boolean {
        Log.d(TAG, "[NAV_COMPLETE] started")
        delay(BotTiming.ms(AUTO_NAV_INITIAL_WAIT_MS, BotTimingCategory.FIXED_SETTLE))

        var tracking = false
        repeat(AUTO_NAV_START_ATTEMPTS) { attempt ->
            if (isAutoNavigating()) {
                Log.d(TAG, "[NAV_COMPLETE] auto navigating detected")
                tracking = true
                return@repeat
            }
            if (attempt < AUTO_NAV_START_ATTEMPTS - 1) {
                delay(AUTO_NAV_POLL_MS)
            }
        }

        if (!tracking) {
            Log.w(TAG, "[NAV_COMPLETE] auto nav not detected; using stability fallback")
            waitForScreenStability()
            return true
        }

        val start = System.currentTimeMillis()
        var misses = 0

        while (System.currentTimeMillis() - start < AUTO_NAV_TIMEOUT_MS) {
            if (isAutoNavigating()) {
                misses = 0
            } else {
                misses++
                Log.d(TAG, "[NAV_COMPLETE] miss $misses/$AUTO_NAV_MISSES_TO_FINISH")
                if (misses >= AUTO_NAV_MISSES_TO_FINISH) {
                    if (waitForScreenStability()) {
                        delay(BotTiming.ms(AUTO_NAV_FINISH_GRACE_MS, BotTimingCategory.FIXED_SETTLE))
                        Log.d(TAG, "[NAV_COMPLETE] finished=true")
                        return true
                    }
                    misses = 0
                }
            }
            delay(AUTO_NAV_POLL_MS)
        }

        Log.w(TAG, "[NAV_COMPLETE] timeout")
        return false
    }

    suspend fun waitUntilArrivesAtCoord(
        location: FarmLocation,
        mapDef: MapDefinition?,
        timeoutMs: Long = RandomSealActions.ARRIVAL_TIMEOUT_WALK_MS,
    ): Boolean {
        if (location.coordX == null || location.coordY == null) {
            Log.w(TAG, "[COORD_ARRIVAL] no coordinates")
            return false
        }

        val targetX = location.coordX
        val targetY = location.coordY
        val radius = location.arrivalRadius
        Log.d(TAG, "[COORD_ARRIVAL] target=($targetX,$targetY) radius=$radius")

        var lastCoord: Pair<Int, Int>? = null
        var stableSinceMs = 0L
        var lastDeathCheckAtMs = 0L

        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val current = readHudCoordinates(mapDef)
            if (current != null) {
                val dist = manhattanDistance(current.first, current.second, targetX, targetY)
                Log.d(TAG, "[COORD_ARRIVAL] current=(${current.first},${current.second}) dist=$dist")
                if (dist <= radius) {
                    Log.d(TAG, "[COORD_ARRIVAL] arrived")
                    return true
                }

                val now = System.currentTimeMillis()
                if (lastCoord != null &&
                    current.first == lastCoord.first &&
                    current.second == lastCoord.second
                ) {
                    if (stableSinceMs == 0L) {
                        stableSinceMs = now
                    }
                    val stableFor = now - stableSinceMs
                    if (stableFor >= COORD_STUCK_DEATH_CHECK_MS &&
                        now - lastDeathCheckAtMs >= COORD_STUCK_DEATH_CHECK_MS
                    ) {
                        lastDeathCheckAtMs = now
                        Log.d(
                            TAG,
                            "[COORD_ARRIVAL] coords unchanged ${stableFor}ms " +
                                "at=(${current.first},${current.second}) → death check",
                        )
                        if (DeathActions.isDead()) {
                            Log.w(
                                TAG,
                                "[COORD_ARRIVAL] dead mid-nav — wait auto-revive " +
                                    "(${DeathActions.DEATH_LOCKOUT_MS}ms lockout)",
                            )
                            DeathActions.waitForAutoRevive()
                            return false
                        }
                        // Alive but stuck: re-arm check every COORD_STUCK_DEATH_CHECK_MS.
                        stableSinceMs = now
                    }
                } else {
                    lastCoord = current
                    stableSinceMs = now
                }
            }
            delay(AdaptiveWait.POLL_MS)
        }

        Log.w(TAG, "[COORD_ARRIVAL] timeout")
        return false
    }

    suspend fun waitForSpotArrival(
        location: FarmLocation,
        mapDef: MapDefinition?,
        timeoutMs: Long = RandomSealActions.ARRIVAL_TIMEOUT_WALK_MS,
    ): Boolean {
        if (location.coordX != null && location.coordY != null) {
            Log.d(TAG, "[SPOT_ARRIVAL] waiting by HUD coordinate OCR timeoutMs=$timeoutMs")
            return waitUntilArrivesAtCoord(location, mapDef, timeoutMs = timeoutMs)
        }
        Log.d(TAG, "[SPOT_ARRIVAL] no coords saved; fallback auto_nav template")
        return waitUntilNavigationComplete()
    }

    private suspend fun isAutoNavigating(): Boolean {
        return NavigationVision.findTemplate(AUTO_NAV_TEMPLATE, AUTO_NAV_THRESHOLD) != null
    }

    private suspend fun waitForScreenStability(): Boolean {
        var stableCount = 0
        val deadline = System.currentTimeMillis() + BotTiming.ms(
            STABILITY_TIMEOUT_MS,
            BotTimingCategory.FIXED_SETTLE,
        )
        var lastRegion: Bitmap? = null

        while (System.currentTimeMillis() < deadline) {
            val frameA = NavigationVision.captureFrame() ?: break
            val regionA = cropMovementRegion(frameA)
            frameA.recycle()
            delay(STABILITY_INTERVAL_MS)
            val frameB = NavigationVision.captureFrame() ?: break
            val regionB = cropMovementRegion(frameB)
            frameB.recycle()

            val similarity = BitmapRegionSimilarity.compare(regionA, regionB)
            regionA?.recycle()
            regionB?.recycle()

            if (similarity >= STABILITY_THRESHOLD) {
                stableCount++
                if (stableCount >= STABILITY_SAMPLES) {
                    Log.d(TAG, "[STABILITY] screen stable")
                    return true
                }
            } else {
                stableCount = 0
            }
            lastRegion?.recycle()
            lastRegion = null
        }
        lastRegion?.recycle()
        return false
    }

    private fun cropMovementRegion(frame: Bitmap): Bitmap? {
        val roi = ScaledRoi.fromRefRect(400, 250, 1600, 1000, frame.width, frame.height)
        val width = roi.width()
        val height = roi.height()
        if (width <= 0 || height <= 0) {
            return null
        }
        return Bitmap.createBitmap(frame, roi.left, roi.top, width, height)
    }

    private suspend fun readHudCoordinates(mapDef: MapDefinition?): Pair<Int, Int>? {
        val frame = NavigationVision.captureFrame() ?: return null
        return try {
            CoordinateReader.readCurrentCoordinates(frame, mapDef)
        } finally {
            frame.recycle()
        }
    }

    /** Public HUD game-coordinate read for War post capture / arrival checks. */
    suspend fun readHudGameCoordinates(mapDef: MapDefinition?): Pair<Int, Int>? {
        return readHudCoordinates(mapDef)
    }

    private fun manhattanDistance(ax: Int, ay: Int, bx: Int, by: Int): Int {
        return abs(ax - bx) + abs(ay - by)
    }
}
