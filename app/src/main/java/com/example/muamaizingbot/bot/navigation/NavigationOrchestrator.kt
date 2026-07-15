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

    suspend fun goToActiveFarmSpot(): Boolean {
        Log.d(TAG, "[NAV] go_to_active_farm_spot started")

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
            if (!NavigationWaitActions.waitUntilUiSettled()) {
                Log.w(TAG, "[NAV] UI not settled; continuing")
            }
            GameActions.ensureAutoMode()
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
            if (!NavigationWaitActions.waitUntilUiSettled()) {
                Log.w(TAG, "[NAV] UI not settled before auto; continuing")
            }
            GameActions.ensureAutoMode()
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

        if (!NavigationWaitActions.waitUntilUiSettled()) {
            Log.w(TAG, "[NAV] UI not settled before auto; continuing")
        }

        if (!GameActions.ensureAutoMode()) {
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
    ): Boolean {
        if (!ensureMapOpenForSpotTap()) {
            Log.w(TAG, "[NAV] failed to open map for spot tap")
            return false
        }

        Log.d(TAG, "[NAV] tapping farm spot at ($x,$y)")
        if (!NavigationVision.tap(x, y)) {
            return false
        }

        if (!MapWindowActions.closeMapWindow()) {
            Log.w(TAG, "[NAV] failed to close map after spot tap")
            return false
        }

        if (location != null) {
            return NavigationWaitActions.waitForSpotArrival(location, mapDef)
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
