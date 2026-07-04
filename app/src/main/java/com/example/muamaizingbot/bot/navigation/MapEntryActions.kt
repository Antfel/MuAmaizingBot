package com.example.muamaizingbot.bot.navigation

import android.util.Log
import com.example.muamaizingbot.maps.MapDefinition
import com.example.muamaizingbot.maps.MapNavigation
import com.example.muamaizingbot.vision.navigation.NavigationVision
import kotlinx.coroutines.delay

object MapEntryActions {

    private const val TAG = "MapEntry"
    private const val LIST_THRESHOLD = 0.8f
    private const val SUB_LIST_WAIT_MS = 2000L

    suspend fun enterMap(mapDef: MapDefinition): Boolean {
        val navigation = mapDef.navigation
        if (navigation == null || !navigation.isImplemented) {
            Log.w(TAG, "[MAP_ENTRY] not navigable map=${mapDef.id}")
            return false
        }

        return when {
            navigation.isDirectTeleport -> enterDirectTeleport(mapDef, navigation)
            navigation.isModalEnter -> enterModalEnter(mapDef, navigation)
            else -> {
                Log.w(TAG, "[MAP_ENTRY] unsupported behavior=${navigation.behavior}")
                false
            }
        }
    }

    private suspend fun enterDirectTeleport(mapDef: MapDefinition, navigation: MapNavigation): Boolean {
        if (navigation.mapHeadTemplate.isNotBlank()) {
            val head = NavigationVision.findTemplateWithScroll(
                assetPath = navigation.mapHeadTemplate,
                threshold = LIST_THRESHOLD,
                swipe = navigation.mapListSwipe,
            )
            if (head == null) {
                Log.w(TAG, "[MAP_ENTRY] map head not found")
                return false
            }
            NavigationVision.tapMatch(head)
            delay(SUB_LIST_WAIT_MS)
            Log.d(TAG, "[MAP_ENTRY] map head selected")
        }

        val mapOption = NavigationVision.findTemplateWithScroll(
            assetPath = navigation.mapOptionTemplate,
            threshold = LIST_THRESHOLD,
            swipe = navigation.mapListSwipe,
        ) ?: run {
            Log.w(TAG, "[MAP_ENTRY] map option not found")
            return false
        }

        NavigationVision.tapMatch(mapOption)
        Log.d(TAG, "[MAP_ENTRY] direct teleport selected")

        return NavigationWaitActions.waitUntilMapLoaded(mapDef)
    }

    private suspend fun enterModalEnter(mapDef: MapDefinition, navigation: MapNavigation): Boolean {
        val head = NavigationVision.findTemplateWithScroll(
            assetPath = navigation.mapHeadTemplate,
            threshold = LIST_THRESHOLD,
            swipe = navigation.mapListSwipe,
        ) ?: run {
            Log.w(TAG, "[MAP_ENTRY] map head not found")
            return false
        }

        NavigationVision.tapMatch(head)
        delay(SUB_LIST_WAIT_MS)

        val mapOption = NavigationVision.findTemplateWithScroll(
            assetPath = navigation.mapOptionTemplate,
            threshold = LIST_THRESHOLD,
            swipe = navigation.mapListSwipe,
        ) ?: run {
            Log.w(TAG, "[MAP_ENTRY] map option not found")
            return false
        }

        NavigationVision.tapMatch(mapOption)
        delay(SUB_LIST_WAIT_MS)

        if (navigation.checkedTemplate.isNotBlank()) {
            val checked = NavigationVision.findTemplate(navigation.checkedTemplate, LIST_THRESHOLD)
            if (checked == null) {
                Log.w(TAG, "[MAP_ENTRY] checked template not found")
                return false
            }
            Log.d(TAG, "[MAP_ENTRY] map option checked")
        }

        val enter = NavigationVision.findTemplate(navigation.enterTemplate, LIST_THRESHOLD)
            ?: run {
                Log.w(TAG, "[MAP_ENTRY] enter button not found")
                return false
            }

        NavigationVision.tapMatch(enter)
        Log.d(TAG, "[MAP_ENTRY] entering map")

        return NavigationWaitActions.waitUntilMapLoaded(mapDef)
    }
}
