package com.example.muamaizingbot.bot.maintenance

import android.util.Log
import com.example.muamaizingbot.bot.navigation.NavigationWaitActions
import com.example.muamaizingbot.maps.MapDefinitionRepository
import com.example.muamaizingbot.profile.LocationRepository
import com.example.muamaizingbot.profile.ProfileRepository
import com.example.muamaizingbot.vision.navigation.NavigationVision

object MapCheckActions {

    private const val TAG = "MapCheck"

    suspend fun isInConfiguredMap(): Boolean {
        val farmSpot = LocationRepository.farmSpot.value
        val profile = ProfileRepository.currentProfile.value
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
        if (onMap) {
            Log.d(TAG, "[MAP_CHECK] expected=$mapId onMap=true via=$presence")
        } else {
            mapDef.navigation?.currentMapTemplate?.takeIf { it.isNotBlank() }?.let { path ->
                NavigationVision.logBestScore(path)
            }
            Log.d(TAG, "[MAP_CHECK] expected=$mapId onMap=false")
        }
        return onMap
    }
}
