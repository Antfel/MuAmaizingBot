package com.example.muamaizingbot.bot.navigation

import android.util.Log
import com.example.muamaizingbot.bot.combat.GameActions
import com.example.muamaizingbot.maps.MapDefinition
import com.example.muamaizingbot.maps.MapDefinitionRepository
import com.example.muamaizingbot.profile.FarmLocation
import com.example.muamaizingbot.profile.LocationRepository
import com.example.muamaizingbot.profile.ProfileRepository
import com.example.muamaizingbot.vision.navigation.NavigationVision
import kotlinx.coroutines.delay

object NavigationOrchestrator {

    private const val TAG = "Navigation"
    /** Fixed HUD settle after teleport / closing world map (PC-style wait(1)). */
    private const val PRE_WIRE_SETTLE_MS = 2500L

    suspend fun goToActiveFarmSpot(ensureAuto: Boolean = true): Boolean {
        Log.d(TAG, "[NAV] go_to_active_farm_spot started ensureAuto=$ensureAuto")

        val profile = ProfileRepository.currentProfile.value
        if (profile == null) {
            Log.w(TAG, "[NAV] no active profile")
            return false
        }

        val farmSpot = LocationRepository.farmSpot.value
        val mapId: String
        val wireId: Int

        if (farmSpot != null) {
            mapId = farmSpot.map
            wireId = farmSpot.wire
            Log.d(
                TAG,
                "[NAV] using visual spot map=$mapId wire=$wireId " +
                    "pixel=(${farmSpot.x},${farmSpot.y})"
            )
        } else {
            mapId = profile.map
            wireId = profile.wire
            if (mapId.isBlank()) {
                Log.w(TAG, "[NAV] no farm spot or profile map configured")
                return false
            }
            Log.d(TAG, "[NAV] using profile map=$mapId wire=$wireId (legacy)")
        }

        val mapDef = MapDefinitionRepository.getById(mapId)
        if (mapDef == null) {
            Log.w(TAG, "[NAV] map definition missing id=$mapId")
            return false
        }

        val onMap = NavigationWaitActions.isOnConfiguredMap(mapDef, farmSpot)
        val atSpot = farmSpot?.let { NavigationWaitActions.isAtFarmSpot(it, mapDef) } == true

        if (onMap && atSpot) {
            Log.d(TAG, "[NAV] already on map and at farm spot; ensure auto only")
            if (ensureAuto) {
                GameActions.ensureAutoMode()
            }
            Log.d(TAG, "[NAV] go_to_active_farm_spot finished=true (on spot)")
            return true
        }

        if (onMap) {
            Log.d(TAG, "[NAV] on configured map; wire + spot tap only")
            if (!WireSwitchActions.switchToWire(mapDef, wireId)) {
                Log.w(TAG, "[NAV] switch_to_wire failed (spot-only path)")
                return false
            }
            val destination = resolveDestination(farmSpot, profile.spot, mapDef)
                ?: run {
                    Log.w(TAG, "[NAV] no farm destination")
                    return false
                }
            val (destX, destY) = destination
            if (!tapVisualLocation(destX, destY, farmSpot, mapDef)) {
                Log.w(TAG, "[NAV] tap_visual_location failed (spot-only path)")
                return false
            }
            if (ensureAuto) {
                GameActions.ensureAutoMode()
            }
            Log.d(TAG, "[NAV] go_to_active_farm_spot finished=true (spot-only)")
            return true
        }

        if (!navigateToMapAndWire(mapDef, wireId, farmSpot)) {
            Log.w(TAG, "[NAV] navigate_to_map_and_wire failed")
            return false
        }

        val destination = resolveDestination(farmSpot, profile.spot, mapDef)
        if (destination == null) {
            Log.w(TAG, "[NAV] no farm destination")
            return false
        }

        val (destX, destY) = destination
        if (!tapVisualLocation(destX, destY, farmSpot, mapDef)) {
            Log.w(TAG, "[NAV] tap_visual_location failed")
            return false
        }

        if (ensureAuto && !GameActions.ensureAutoMode()) {
            Log.w(TAG, "[NAV] ensure_auto_mode failed; farming loop will retry")
        }

        Log.d(TAG, "[NAV] go_to_active_farm_spot finished=true")
        return true
    }

    suspend fun goToVisualLocation(location: FarmLocation): Boolean {
        val mapDef = MapDefinitionRepository.getById(location.map)
        if (mapDef == null) {
            Log.w(TAG, "[NAV] map definition missing id=${location.map}")
            return false
        }

        if (!navigateToMapAndWire(mapDef, location.wire)) {
            Log.w(TAG, "[NAV] navigate_to_map_and_wire failed for visual location")
            return false
        }

        if (!tapVisualLocation(location.x, location.y, location, mapDef)) {
            Log.w(TAG, "[NAV] tap_visual_location failed")
            return false
        }

        return true
    }

    /**
     * Navigate to a boss spot (map/wire/tap). Mirrors farm-spot short paths when already on map.
     * Does not force Auto — caller runs Focus Boss + ensureAuto.
     */
    suspend fun goToBossSpot(location: FarmLocation, ensureAuto: Boolean = false): Boolean {
        Log.d(
            TAG,
            "[NAV] go_to_boss_spot map=${location.map} wire=${location.wire} " +
                "pixel=(${location.x},${location.y}) ensureAuto=$ensureAuto",
        )
        val mapDef = MapDefinitionRepository.getById(location.map)
        if (mapDef == null) {
            Log.w(TAG, "[NAV] boss spot map missing id=${location.map}")
            return false
        }

        val onMap = NavigationWaitActions.isOnConfiguredMap(mapDef, location)
        val atSpot = NavigationWaitActions.isAtFarmSpot(location, mapDef)

        if (onMap && atSpot) {
            Log.d(TAG, "[NAV] already at boss spot")
            if (ensureAuto) {
                GameActions.ensureAutoMode()
            }
            return true
        }

        if (onMap) {
            Log.d(TAG, "[NAV] on boss map; wire + spot tap only")
            if (!WireSwitchActions.switchToWire(mapDef, location.wire)) {
                Log.w(TAG, "[NAV] switch_to_wire failed (boss spot-only)")
                return false
            }
            if (!tapVisualLocation(location.x, location.y, location, mapDef)) {
                Log.w(TAG, "[NAV] boss spot tap failed (spot-only)")
                return false
            }
            if (ensureAuto) {
                GameActions.ensureAutoMode()
            }
            return true
        }

        if (!navigateToMapAndWire(mapDef, location.wire, location)) {
            Log.w(TAG, "[NAV] navigate_to_map_and_wire failed for boss spot")
            return false
        }
        if (!tapVisualLocation(location.x, location.y, location, mapDef)) {
            Log.w(TAG, "[NAV] boss spot tap failed")
            return false
        }
        if (ensureAuto && !GameActions.ensureAutoMode()) {
            Log.w(TAG, "[NAV] ensure_auto_mode failed after boss nav")
        }
        Log.d(TAG, "[NAV] go_to_boss_spot finished=true")
        return true
    }

    /**
     * Return to a War/APEX post via open map + affine pixel tap only.
     * No map presence check / teleport — War starts and stays inside the event.
     * Does **not** force Auto ON.
     */
    suspend fun goToWarPost(location: FarmLocation): Boolean {
        val mapId = location.map.takeIf { it.isNotBlank() } ?: "divine_realm_1"
        val mapDef = MapDefinitionRepository.getById(mapId)
        if (mapDef == null) {
            Log.w(TAG, "[NAV] war_post map missing id=$mapId")
            return false
        }

        Log.d(
            TAG,
            "[NAV] war_post minimap tap pixel=(${location.x},${location.y}) " +
                "coords=(${location.coordX},${location.coordY})",
        )
        if (!tapVisualLocation(location.x, location.y, location, mapDef, allowRandomSeal = false)) {
            Log.w(TAG, "[NAV] war_post tap failed pixel=(${location.x},${location.y})")
            return false
        }

        Log.d(TAG, "[NAV] war_post arrival ok coords=(${location.coordX},${location.coordY})")
        return true
    }

    suspend fun navigateToMapAndWire(
        mapDef: MapDefinition,
        wireId: Int,
        farmSpot: FarmLocation? = LocationRepository.farmSpot.value,
    ): Boolean {
        Log.d(TAG, "[NAV] navigate map=${mapDef.name} wire=$wireId")

        if (!mapDef.isNavigable()) {
            Log.w(TAG, "[NAV] map not navigable id=${mapDef.id}")
            return false
        }

        if (NavigationWaitActions.isOnConfiguredMap(mapDef, farmSpot)) {
            Log.d(TAG, "[NAV] skip teleport; already on configured map")
            if (!WireSwitchActions.switchToWire(mapDef, wireId)) {
                Log.w(TAG, "[NAV] switch_to_wire failed")
                return false
            }
            return true
        }

        cleanGameUi()

        if (!MapWindowActions.openMapWindow()) {
            Log.w(TAG, "[NAV] open_map_window failed")
            return false
        }

        if (!MapEntryActions.enterMap(mapDef)) {
            Log.w(TAG, "[NAV] enter_map failed")
            return false
        }

        MapWindowActions.closeMapWindowIfOpen()
        Log.d(TAG, "[NAV] pre-wire HUD settle ${PRE_WIRE_SETTLE_MS}ms")
        delay(PRE_WIRE_SETTLE_MS)

        if (!WireSwitchActions.switchToWire(mapDef, wireId)) {
            Log.w(TAG, "[NAV] switch_to_wire failed")
            return false
        }

        return true
    }

    private suspend fun tapVisualLocation(
        x: Int,
        y: Int,
        location: FarmLocation?,
        mapDef: MapDefinition,
        allowRandomSeal: Boolean = true,
    ): Boolean {
        if (!ensureMapOpenForSpotTap()) {
            Log.w(TAG, "[NAV] failed to open map for spot tap")
            return false
        }

        Log.d(TAG, "[NAV] tapping farm spot at ($x,$y)")
        if (!NavigationVision.tap(x, y)) {
            return false
        }

        val randomEnabled =
            allowRandomSeal &&
                ProfileRepository.currentProfile.value?.enableRandomTeleport != false
        val sealsUsed = if (randomEnabled) {
            RandomSealActions.maybeUseRandomIfFarPath()
        } else {
            if (allowRandomSeal) {
                Log.d(TAG, "[NAV] Random Teleport disabled in profile — walk only")
            }
            0
        }
        val arrivalTimeoutMs = RandomSealActions.arrivalTimeoutMs(sealsUsed)

        if (!MapWindowActions.closeMapWindow()) {
            Log.w(TAG, "[NAV] failed to close map after spot tap")
            return false
        }

        if (location != null) {
            Log.d(
                TAG,
                "[NAV] wait arrival sealsUsed=$sealsUsed timeoutMs=$arrivalTimeoutMs",
            )
            return NavigationWaitActions.waitForSpotArrival(
                location,
                mapDef,
                timeoutMs = arrivalTimeoutMs,
            )
        }

        return NavigationWaitActions.waitUntilNavigationComplete()
    }

    private fun resolveDestination(
        farmSpot: FarmLocation?,
        legacySpotId: String,
        mapDef: MapDefinition,
    ): Pair<Int, Int>? {
        if (farmSpot != null) {
            return farmSpot.x to farmSpot.y
        }

        Log.w(TAG, "[NAV] visual spot missing; legacy spots not yet supported")
        return null
    }

    /** After wire switch the zone map is often still open — skip reopen when possible. */
    private suspend fun ensureMapOpenForSpotTap(): Boolean {
        if (MapWindowActions.isMapWindowOpen()) {
            Log.d(TAG, "[NAV] map already open for spot tap")
            return true
        }

        Log.d(TAG, "[NAV] opening map for spot tap")
        return MapWindowActions.openMapWindow(retries = 2, timeoutMs = 4000)
    }

    suspend fun cleanGameUi() {
        Log.d(TAG, "[NAV] cleaning UI")
        repeat(3) {
            if (!MapWindowActions.isMapWindowOpen()) {
                return
            }
            val close = NavigationVision.findTemplate(
                MapWindowActions.CLOSE_X,
                NavigationTemplateThresholds.closeX(),
            ) ?: return
            if (!MapWindowActions.isLikelyPanelCloseButton(close.centerX, close.centerY)) {
                Log.d(TAG, "[NAV] skip UI clean; close_x outside panel at=(${close.centerX},${close.centerY})")
                return
            }
            NavigationVision.tapMatch(close)
            if (NavigationVision.waitUntilAbsent(
                    MapWindowActions.CLOSE_X,
                    NavigationTemplateThresholds.closeX(),
                    1500,
                )
            ) {
                return
            }
            Log.d(TAG, "[NAV] close_x still visible after tap; stopping UI clean")
            return
        }
    }
}
