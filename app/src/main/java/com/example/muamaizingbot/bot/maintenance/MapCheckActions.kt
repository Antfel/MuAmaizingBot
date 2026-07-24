package com.example.muamaizingbot.bot.maintenance

import android.util.Log
import com.example.muamaizingbot.bot.bosses.FarmBossesLoop
import com.example.muamaizingbot.bot.navigation.NavigationWaitActions
import com.example.muamaizingbot.maps.MapDefinitionRepository
import com.example.muamaizingbot.profile.LocationRepository
import com.example.muamaizingbot.profile.ProfileRepository
import com.example.muamaizingbot.profile.isElfBuffWarMode
import com.example.muamaizingbot.profile.isFarmBossesMode

object MapCheckActions {

    private const val TAG = "MapCheck"

    suspend fun isInConfiguredMap(): Boolean {
        val profile = ProfileRepository.currentProfile.value
        if (profile?.isFarmBossesMode() == true) {
            val cp = FarmBossesLoop.currentCheckpointOrCursor(profile)
            if (cp == null) {
                Log.w(TAG, "[MAP_CHECK] farm_bosses mode but no map configured")
                return false
            }
            val mapDef = MapDefinitionRepository.getById(cp.mapId)
            if (mapDef == null) {
                Log.w(TAG, "[MAP_CHECK] map definition missing id=${cp.mapId}")
                return false
            }
            val presence = NavigationWaitActions.detectMapPresence(mapDef, null)
            val onMap = presence != NavigationWaitActions.MapPresence.NONE
            Log.d(TAG, "[MAP_CHECK] boss expected=${cp.mapId} onMap=$onMap via=$presence")
            return onMap
        }

        // War / APEX: always inside the event — no map validation.
        if (profile?.isElfBuffWarMode() == true) {
            Log.d(TAG, "[MAP_CHECK] war mode — skip map validation")
            return true
        }

        val farmSpot = LocationRepository.farmSpot.value
        val mapId = farmSpot?.map?.takeIf { it.isNotBlank() }
            ?: profile?.map?.takeIf { it.isNotBlank() }

        if (mapId == null) {
            Log.w(TAG, "[MAP_CHECK] no expected map configured")
            return false
        }

        val mapDef = MapDefinitionRepository.getById(mapId)
        if (mapDef == null) {
            Log.w(TAG, "[MAP_CHECK] map definition missing id=$mapId")
            return false
        }

        val presence = NavigationWaitActions.detectMapPresence(mapDef, farmSpot)
        val onMap = presence != NavigationWaitActions.MapPresence.NONE
        Log.d(TAG, "[MAP_CHECK] expected=$mapId onMap=$onMap via=$presence name=\"${mapDef.name}\"")
        return onMap
    }
}
