package com.example.muamaizingbot.bot.navigation

import android.graphics.Bitmap
import android.util.Log
import com.example.muamaizingbot.maps.MapDefinition
import com.example.muamaizingbot.profile.FarmLocation
import com.example.muamaizingbot.util.AdaptiveWait
import com.example.muamaizingbot.vision.BitmapRegionSimilarity
import com.example.muamaizingbot.vision.coordinate.CoordinateReader
import com.example.muamaizingbot.vision.navigation.NavigationVision
import com.example.muamaizingbot.vision.roi.ScaledRoi
import kotlin.math.abs
import kotlinx.coroutines.delay

object NavigationWaitActions {

    private const val TAG = "NavWait"
    /** Min template score to trust OCR coords (avoids Lorencia / wrong zone false positives). */
    private const val WEAK_MAP_TEMPLATE_FLOOR = 0.35f
    /** Near spot tolerance when template is a weak match (map check only). */
    private const val MAP_CHECK_NEAR_SPOT_TOLERANCE = 25
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

    suspend fun waitUntilMapLoaded(mapDef: MapDefinition): Boolean {
        val navigation = mapDef.navigation ?: return false
        val timeoutMs = navigation.enterWaitSeconds * 1000L
        val template = navigation.currentMapTemplate

        if (template.isBlank()) {
            Log.w(TAG, "[MAP_LOAD] no current_map_template; waiting up to ${timeoutMs}ms")
            AdaptiveWait.until(timeoutMs = timeoutMs, label = "map_load_no_template") { false }
            return true
        }

        val loaded = AdaptiveWait.until(
            timeoutMs = timeoutMs,
            pollMs = 500L,
            label = "map_loaded",
        ) {
            isOnConfiguredMap(mapDef, null) && !MapWindowActions.isMapWindowOpen()
        }
        if (loaded) {
            Log.d(TAG, "[MAP_LOAD] confirmed map=${mapDef.id}")
        } else {
            Log.w(TAG, "[MAP_LOAD] timeout map=${mapDef.id}")
        }
        return loaded
    }

    /** Poll until the in-world map indicator is visible (after teleport / loading). */
    suspend fun waitUntilWorldReady(mapDef: MapDefinition): Boolean {
        val navigation = mapDef.navigation ?: return true
        val timeoutMs = navigation.enterWaitSeconds * 1000L
        val template = navigation.currentMapTemplate

        if (template.isBlank()) {
            return true
        }

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
        val timeoutMs = navigation.enterWaitSeconds * 1000L
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
        TEMPLATE,
        COORDS_AT_SPOT,
        COORDS_NEAR_SPOT,
        NONE,
    }

    suspend fun isCurrentMap(mapDef: MapDefinition, threshold: Float? = null): Boolean {
        val navigation = mapDef.navigation ?: return false
        val template = navigation.currentMapTemplate
        if (template.isBlank()) {
            return false
        }
        val effectiveThreshold = threshold ?: navigation.currentMapThreshold
        return NavigationVision.findTemplate(template, effectiveThreshold) != null
    }

    suspend fun detectMapPresence(
        mapDef: MapDefinition,
        farmSpot: FarmLocation?,
    ): MapPresence {
        val navigation = mapDef.navigation
        val mapThreshold = navigation?.currentMapThreshold?.takeIf { it > 0f } ?: 0.72f

        if (isCurrentMap(mapDef, mapThreshold)) {
            return MapPresence.TEMPLATE
        }

        val templateScore = currentMapTemplateScore(mapDef)
        if (templateScore < WEAK_MAP_TEMPLATE_FLOOR) {
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

    private suspend fun currentMapTemplateScore(mapDef: MapDefinition): Float {
        val template = mapDef.navigation?.currentMapTemplate ?: return 0f
        if (template.isBlank()) {
            return 0f
        }
        val frame = NavigationVision.captureFrame() ?: return 0f
        return try {
            NavigationVision.probeOnFrame(frame, template, null).score
        } finally {
            frame.recycle()
        }
    }

    suspend fun waitUntilNavigationComplete(): Boolean {
        Log.d(TAG, "[NAV_COMPLETE] started")
        delay(AUTO_NAV_INITIAL_WAIT_MS)

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
                        delay(AUTO_NAV_FINISH_GRACE_MS)
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
        timeoutMs: Long = 120_000L,
    ): Boolean {
        if (location.coordX == null || location.coordY == null) {
            Log.w(TAG, "[COORD_ARRIVAL] no coordinates")
            return false
        }

        val targetX = location.coordX
        val targetY = location.coordY
        val radius = location.arrivalRadius
        Log.d(TAG, "[COORD_ARRIVAL] target=($targetX,$targetY) radius=$radius")

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
            }
            delay(AdaptiveWait.POLL_MS)
        }

        Log.w(TAG, "[COORD_ARRIVAL] timeout")
        return false
    }

    suspend fun waitForSpotArrival(location: FarmLocation, mapDef: MapDefinition?): Boolean {
        if (location.coordX != null && location.coordY != null) {
            Log.d(TAG, "[SPOT_ARRIVAL] waiting by HUD coordinate OCR")
            return waitUntilArrivesAtCoord(location, mapDef)
        }
        Log.d(TAG, "[SPOT_ARRIVAL] no coords saved; fallback auto_nav template")
        return waitUntilNavigationComplete()
    }

    private suspend fun isAutoNavigating(): Boolean {
        return NavigationVision.findTemplate(AUTO_NAV_TEMPLATE, AUTO_NAV_THRESHOLD) != null
    }

    private suspend fun waitForScreenStability(): Boolean {
        var stableCount = 0
        val deadline = System.currentTimeMillis() + STABILITY_TIMEOUT_MS
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

    private fun manhattanDistance(ax: Int, ay: Int, bx: Int, by: Int): Int {
        return abs(ax - bx) + abs(ay - by)
    }
}
