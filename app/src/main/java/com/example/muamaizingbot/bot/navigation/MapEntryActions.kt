package com.example.muamaizingbot.bot.navigation

import android.util.Log
import com.example.muamaizingbot.maps.MapDefinition
import com.example.muamaizingbot.maps.MapDefinitionRepository
import com.example.muamaizingbot.maps.MapNavigation
import com.example.muamaizingbot.vision.coord.RefCoords
import com.example.muamaizingbot.vision.navigation.NavigationVision
import com.example.muamaizingbot.vision.navigation.ScrollSettleWait
import com.example.muamaizingbot.vision.template.PcTemplateMatchResult
import kotlinx.coroutines.delay

object MapEntryActions {

    private const val TAG = "MapEntry"
    private const val MAP_HEAD_CANDIDATE_THRESHOLD = 0.38f
    /** Lower bar when match is low on screen (past Lorencia / early zones). */
    private const val MAP_HEAD_DEEP_CANDIDATE_THRESHOLD = 0.55f
    private const val MAP_OPTION_THRESHOLD = 0.68f
    private const val MAP_SUB_OPTION_THRESHOLD = 0.55f
    /** Same-row only: sibling score must beat target by this to block (digit templates share "Winds"). */
    private const val MAP_SUB_SAME_ROW_MARGIN = 0.03f
    private const val SUB_LIST_WAIT_MS = 2000L
    private const val SUB_HEAD_VERIFY_MS = 3000L
    private const val SUB_HEAD_COLLAPSE_MS = 600L
    private const val SUB_OPTION_SEARCH_MS = 8000L
    private const val MODAL_DIALOG_WAIT_MS = 20_000L
    private const val CHECKBOX_THRESHOLD = 0.68f
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
            findSubMapOptionUnique(mapDef, navigation)
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

                val sub = findSubMapOptionUnique(mapDef, navigation, timeoutMs = SUB_HEAD_VERIFY_MS)
                if (sub != null) {
                    Log.d(
                        TAG,
                        "[MAP_ENTRY] head verified sub-map=${subPath.substringAfterLast('/')} " +
                            "subScore=${sub.score} attempt=$attempt",
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

    /**
     * Pick the intended sub-map row (e.g. Plains 2).
     *
     * Sibling templates share most pixels ("…Winds 1" vs "…Winds 2"), so plains_1 can
     * score higher than plains_2 while BOTH rows are visible. Compare scores only when
     * siblings peak on the SAME row; otherwise tap the target's own best location.
     */
    private suspend fun findSubMapOptionUnique(
        mapDef: MapDefinition,
        navigation: MapNavigation,
        timeoutMs: Long = SUB_OPTION_SEARCH_MS,
    ): PcTemplateMatchResult? {
        val targetPath = navigation.mapOptionTemplate
        val roi = NavigationVision.mapListRoi(navigation.mapListSwipe)
        val siblings = MapDefinitionRepository.siblingsSharingHead(mapDef)
            .mapNotNull { it.navigation?.mapOptionTemplate?.takeIf(String::isNotBlank) }
            .distinct()

        val deadline = System.currentTimeMillis() + timeoutMs
        var bestLogged = ""
        while (System.currentTimeMillis() < deadline) {
            val frame = NavigationVision.captureFrame() ?: break
            try {
                val probes = siblings.map { path ->
                    path to NavigationVision.probeOnFrame(frame, path, roi)
                }
                bestLogged = probes.joinToString { (p, m) ->
                    "${p.substringAfterLast('/')}=${"%.3f".format(m.score)}@${m.centerY}"
                }
                val target = probes.firstOrNull { it.first == targetPath }?.second
                if (target != null && target.score >= MAP_SUB_OPTION_THRESHOLD) {
                    val sameRowRival = probes
                        .filter { it.first != targetPath }
                        .filter { isSameMapRow(it.second, target, frame.height) }
                        .maxByOrNull { it.second.score }
                    if (sameRowRival == null ||
                        target.score + MAP_SUB_SAME_ROW_MARGIN >= sameRowRival.second.score
                    ) {
                        Log.d(
                            TAG,
                            "[MAP_ENTRY] sub accept target=${"%.3f".format(target.score)} " +
                                "at=(${target.centerX},${target.centerY}) probes=[$bestLogged]",
                        )
                        return target
                    }
                    Log.d(
                        TAG,
                        "[MAP_ENTRY] sub same-row clash target=${"%.3f".format(target.score)} " +
                            "rival=${"%.3f".format(sameRowRival.second.score)} probes=[$bestLogged]",
                    )
                }
            } finally {
                frame.recycle()
            }
            delay(350L)
        }

        Log.w(TAG, "[MAP_ENTRY] sub unique miss path=${targetPath.substringAfterLast('/')} last=[$bestLogged]")
        return null
    }

    private fun isSameMapRow(
        a: PcTemplateMatchResult,
        b: PcTemplateMatchResult,
        screenHeight: Int,
    ): Boolean {
        val tol = RefCoords.scaleY(36, screenHeight)
        return kotlin.math.abs(a.centerY - b.centerY) <= tol
    }

    private suspend fun enterModalEnter(mapDef: MapDefinition, navigation: MapNavigation): Boolean {
        if (navigation.modalOptionTemplate.isBlank()) {
            Log.w(TAG, "[MAP_ENTRY] modal_option_template missing map=${mapDef.id}")
            return false
        }

        if (!expandZoneHeadVerified(mapDef, navigation)) {
            NavigationVision.logBestScore(navigation.mapHeadTemplate)
            Log.w(TAG, "[MAP_ENTRY] map head not verified path=${navigation.mapHeadTemplate}")
            return false
        }

        Log.d(TAG, "[MAP_ENTRY] map head selected path=${navigation.mapHeadTemplate}")

        val mapOption = findSubMapOptionUnique(mapDef, navigation)
            ?: run {
                NavigationVision.logBestScore(navigation.mapOptionTemplate)
                Log.w(TAG, "[MAP_ENTRY] map option not found path=${navigation.mapOptionTemplate}")
                return false
            }

        NavigationVision.tapMatch(mapOption)
        Log.d(TAG, "[MAP_ENTRY] mapsui hub tapped; waiting for Kalima dialog")

        val modalRow = NavigationVision.waitForTemplate(
            assetPath = navigation.modalOptionTemplate,
            threshold = MAP_OPTION_THRESHOLD,
            timeoutMs = MODAL_DIALOG_WAIT_MS,
            pollMs = 400L,
        ) ?: run {
            NavigationVision.logBestScore(navigation.modalOptionTemplate)
            Log.w(
                TAG,
                "[MAP_ENTRY] modal option not found path=${navigation.modalOptionTemplate}",
            )
            return false
        }
        Log.d(
            TAG,
            "[MAP_ENTRY] modal row found score=${modalRow.score} " +
                "at=(${modalRow.bestX},${modalRow.bestY})",
        )

        if (!ensureModalRowChecked(navigation, modalRow)) {
            return false
        }

        val enter = NavigationVision.findTemplate(
            navigation.enterTemplate,
            MAP_OPTION_THRESHOLD,
        ) ?: run {
            NavigationVision.logBestScore(navigation.enterTemplate)
            Log.w(TAG, "[MAP_ENTRY] enter button not found")
            return false
        }

        NavigationVision.tapMatch(enter)
        Log.d(TAG, "[MAP_ENTRY] entering map")

        return NavigationWaitActions.waitUntilMapLoaded(mapDef)
    }

    private suspend fun ensureModalRowChecked(
        navigation: MapNavigation,
        modalRow: PcTemplateMatchResult,
    ): Boolean {
        val roi = NavigationVision.checkboxLeftOfRow(modalRow)

        val checked = if (navigation.checkedTemplate.isNotBlank()) {
            NavigationVision.findTemplate(navigation.checkedTemplate, CHECKBOX_THRESHOLD, roi)
        } else {
            null
        }
        if (checked != null) {
            Log.d(TAG, "[MAP_ENTRY] modal row already checked score=${checked.score}")
            return true
        }

        val unchecked = if (navigation.uncheckedTemplate.isNotBlank()) {
            NavigationVision.findTemplate(navigation.uncheckedTemplate, CHECKBOX_THRESHOLD, roi)
        } else {
            null
        }
        if (unchecked != null) {
            Log.d(TAG, "[MAP_ENTRY] modal row unchecked; tapping to select")
            NavigationVision.tapMatch(unchecked)
            delay(SUB_HEAD_COLLAPSE_MS)

            val confirmed = NavigationVision.waitForTemplate(
                assetPath = navigation.checkedTemplate,
                threshold = CHECKBOX_THRESHOLD,
                timeoutMs = 3000L,
                pollMs = 250L,
                roi = roi,
            )
            if (confirmed == null) {
                NavigationVision.logBestScore(navigation.checkedTemplate, roi)
                Log.w(TAG, "[MAP_ENTRY] checked not confirmed after tap")
                return false
            }
            Log.d(TAG, "[MAP_ENTRY] modal row checked after tap score=${confirmed.score}")
            return true
        }

        NavigationVision.logBestScore(navigation.checkedTemplate, roi)
        NavigationVision.logBestScore(navigation.uncheckedTemplate, roi)
        Log.w(TAG, "[MAP_ENTRY] neither checked nor unchecked found left of modal row")
        return false
    }
}
