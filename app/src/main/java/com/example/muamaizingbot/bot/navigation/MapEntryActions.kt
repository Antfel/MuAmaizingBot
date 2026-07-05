package com.example.muamaizingbot.bot.navigation

import android.util.Log
import com.example.muamaizingbot.maps.MapDefinition
import com.example.muamaizingbot.maps.MapNavigation
import com.example.muamaizingbot.vision.coord.RefCoords
import com.example.muamaizingbot.vision.navigation.NavigationVision
import com.example.muamaizingbot.vision.navigation.ScrollSettleWait
import kotlinx.coroutines.delay

object MapEntryActions {

    private const val TAG = "MapEntry"
    private const val MAP_HEAD_CANDIDATE_THRESHOLD = 0.38f
    /** Lower bar when match is low on screen (past Lorencia / early zones). */
    private const val MAP_HEAD_DEEP_CANDIDATE_THRESHOLD = 0.24f
    private const val MAP_OPTION_THRESHOLD = 0.68f
    private const val MAP_SUB_OPTION_THRESHOLD = 0.55f
    private const val SUB_LIST_WAIT_MS = 2000L
    private const val SUB_HEAD_VERIFY_MS = 3000L
    private const val SUB_HEAD_COLLAPSE_MS = 600L
    private const val SUB_OPTION_SEARCH_MS = 8000L
    private const val HEAD_SCROLL_ATTEMPTS = 18
    private const val MAP_LIST_SCROLL_WAIT_MS = 1000L

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
        val hasHead = navigation.mapHeadTemplate.isNotBlank()

        if (hasHead) {
            if (!expandZoneHeadVerified(mapDef, navigation)) {
                NavigationVision.logBestScore(navigation.mapHeadTemplate)
                Log.w(TAG, "[MAP_ENTRY] map head not verified path=${navigation.mapHeadTemplate}")
                return false
            }
        }

        val mapOption = if (hasHead) {
            findSubMapOption(navigation)
        } else {
            NavigationVision.findTemplateWithScroll(
                assetPath = navigation.mapOptionTemplate,
                threshold = MAP_OPTION_THRESHOLD,
                swipe = navigation.mapListSwipe,
            )
        } ?: run {
            NavigationVision.logBestScore(navigation.mapOptionTemplate)
            Log.w(TAG, "[MAP_ENTRY] map option not found path=${navigation.mapOptionTemplate}")
            return false
        }

        NavigationVision.tapMatch(mapOption)
        Log.d(TAG, "[MAP_ENTRY] direct teleport selected score=${mapOption.score}")

        return NavigationWaitActions.waitUntilMapLoaded(mapDef)
    }

    /**
     * Tap zone headers only when position + sub-map verify (rejects Lorencia at top of list).
     */
    private suspend fun expandZoneHeadVerified(
        mapDef: MapDefinition,
        navigation: MapNavigation,
    ): Boolean {
        val swipe = navigation.mapListSwipe
        val headPath = navigation.mapHeadTemplate
        val subPath = navigation.mapOptionTemplate
        val minInitialScrolls = minInitialScrollsForOrder(mapDef.order)

        repeat(minInitialScrolls) { index ->
            if (swipe == null) {
                return@repeat
            }
            Log.d(TAG, "[MAP_ENTRY] pre-scroll ${index + 1}/$minInitialScrolls order=${mapDef.order}")
            NavigationVision.swipe(swipe)
            delay(MAP_LIST_SCROLL_WAIT_MS)
        }

        for (attempt in 1..HEAD_SCROLL_ATTEMPTS) {
            val frame = NavigationVision.captureFrame()
            if (frame == null) {
                Log.w(TAG, "[MAP_ENTRY] head verify no frame attempt=$attempt")
                break
            }

            val roi = ScrollSettleWait.listRegionRect(swipe, frame.width, frame.height)
            val probe = try {
                NavigationVision.probeOnFrame(frame, headPath, roi)
            } finally {
                frame.recycle()
            }

            val minHeadY = minHeadYForOrder(mapDef.order, frame.height)
            val deepEnough = minHeadY == 0 || probe.bestY >= minHeadY
            val threshold = if (deepEnough) {
                MAP_HEAD_DEEP_CANDIDATE_THRESHOLD
            } else {
                MAP_HEAD_CANDIDATE_THRESHOLD
            }

            Log.d(
                TAG,
                "[MAP_ENTRY] head probe attempt=$attempt/${HEAD_SCROLL_ATTEMPTS} " +
                    "score=${"%.3f".format(probe.score)} at=(${probe.bestX},${probe.bestY}) " +
                    "minY=$minHeadY deep=$deepEnough need=${"%.2f".format(threshold)}"
            )

            if (!deepEnough && probe.score >= MAP_HEAD_CANDIDATE_THRESHOLD) {
                Log.d(
                    TAG,
                    "[MAP_ENTRY] skip head (too high on list, likely Lorencia) y=${probe.bestY} minY=$minHeadY"
                )
            } else if (probe.score >= threshold) {
                Log.d(TAG, "[MAP_ENTRY] trying head expand score=${probe.score} y=${probe.bestY}")
                NavigationVision.tapScreen(probe.centerX, probe.centerY)
                delay(SUB_LIST_WAIT_MS)

                val sub = NavigationVision.waitForTemplate(
                    assetPath = subPath,
                    threshold = MAP_SUB_OPTION_THRESHOLD,
                    timeoutMs = SUB_HEAD_VERIFY_MS,
                    roi = roi,
                )
                if (sub != null) {
                    Log.d(
                        TAG,
                        "[MAP_ENTRY] head verified sub-map=${subPath.substringAfterLast('/')} " +
                            "subScore=${sub.score} attempt=$attempt"
                    )
                    return true
                }

                val subBest = NavigationVision.captureFrame()?.let { f ->
                    try {
                        NavigationVision.probeOnFrame(f, subPath, roi).score
                    } finally {
                        f.recycle()
                    }
                } ?: 0f

                Log.w(
                    TAG,
                    "[MAP_ENTRY] head rejected (wrong zone) headScore=${probe.score} " +
                        "subBest=${"%.3f".format(subBest)} expected=${subPath.substringAfterLast('/')}"
                )
                NavigationVision.tapScreen(probe.centerX, probe.centerY)
                delay(SUB_HEAD_COLLAPSE_MS)
                if (swipe != null) {
                    repeat(2) {
                        NavigationVision.swipe(swipe)
                        delay(MAP_LIST_SCROLL_WAIT_MS)
                    }
                }
                continue
            }

            if (swipe != null && attempt < HEAD_SCROLL_ATTEMPTS) {
                NavigationVision.swipe(swipe)
                delay(MAP_LIST_SCROLL_WAIT_MS)
            }
        }

        return false
    }

    /** Skip Lorencia / early zones before probing (map list order in JSON). */
    private fun minInitialScrollsForOrder(mapOrder: Int): Int {
        return when {
            mapOrder <= 8 -> 0
            mapOrder <= 15 -> 1
            mapOrder <= 20 -> 2
            else -> 3
        }
    }

    /** Ref-Y floor for zone header — Lorencia ~y194 @ 1080p; Plains sits lower after scroll. */
    private fun minHeadYForOrder(mapOrder: Int, screenHeight: Int): Int {
        val refMinY = when {
            mapOrder <= 8 -> 0
            mapOrder <= 15 -> 280
            mapOrder <= 20 -> 340
            else -> 400
        }
        return if (refMinY == 0) 0 else RefCoords.scaleY(refMinY, screenHeight)
    }

    private suspend fun findSubMapOption(navigation: MapNavigation) =
        NavigationVision.waitForTemplate(
            assetPath = navigation.mapOptionTemplate,
            threshold = MAP_SUB_OPTION_THRESHOLD,
            timeoutMs = SUB_OPTION_SEARCH_MS,
            pollMs = 350L,
            roi = NavigationVision.mapListRoi(navigation.mapListSwipe),
        )

    private suspend fun enterModalEnter(mapDef: MapDefinition, navigation: MapNavigation): Boolean {
        if (!expandZoneHeadVerified(mapDef, navigation)) {
            NavigationVision.logBestScore(navigation.mapHeadTemplate)
            Log.w(TAG, "[MAP_ENTRY] map head not verified path=${navigation.mapHeadTemplate}")
            return false
        }

        Log.d(TAG, "[MAP_ENTRY] map head selected path=${navigation.mapHeadTemplate}")

        val mapOption = findSubMapOption(navigation)
            ?: run {
                NavigationVision.logBestScore(navigation.mapOptionTemplate)
                Log.w(TAG, "[MAP_ENTRY] map option not found path=${navigation.mapOptionTemplate}")
                return false
            }

        NavigationVision.tapMatch(mapOption)
        delay(SUB_LIST_WAIT_MS)

        if (navigation.checkedTemplate.isNotBlank()) {
            val checked = NavigationVision.findTemplate(
                navigation.checkedTemplate,
                MAP_OPTION_THRESHOLD,
                NavigationVision.mapListRoi(navigation.mapListSwipe),
            )
            if (checked == null) {
                Log.w(TAG, "[MAP_ENTRY] checked template not found")
                return false
            }
            Log.d(TAG, "[MAP_ENTRY] map option checked")
        }

        val enter = NavigationVision.findTemplate(
            navigation.enterTemplate,
            MAP_OPTION_THRESHOLD,
            NavigationVision.mapListRoi(navigation.mapListSwipe),
        ) ?: run {
            Log.w(TAG, "[MAP_ENTRY] enter button not found")
            return false
        }

        NavigationVision.tapMatch(enter)
        Log.d(TAG, "[MAP_ENTRY] entering map")

        return NavigationWaitActions.waitUntilMapLoaded(mapDef)
    }
}
