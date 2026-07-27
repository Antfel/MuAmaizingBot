package com.example.muamaizingbot.bot.bosses

import android.graphics.Rect
import android.util.Log
import com.example.muamaizingbot.bot.navigation.MapWindowActions
import com.example.muamaizingbot.bot.navigation.NavigationWaitActions
import com.example.muamaizingbot.bot.navigation.RandomSealActions
import com.example.muamaizingbot.capture.ScreenCaptureManager
import com.example.muamaizingbot.maps.CoordinateMapping
import com.example.muamaizingbot.maps.MapDefinition
import com.example.muamaizingbot.profile.FarmLocation
import com.example.muamaizingbot.profile.ProfileRepository
import com.example.muamaizingbot.vision.coord.RefCoords
import com.example.muamaizingbot.vision.navigation.NavigationVision
import com.example.muamaizingbot.vision.template.PcTemplateMatchResult
import kotlinx.coroutines.delay

/**
 * Open zone map → find alive boss icons → tap → wait HUD arrival via affine coords.
 *
 * Dead boss icons look like dimmed copies of alive ones. Candidates must beat a
 * [BOSS_DEAD] score at the same map spot (alive wins only if clearly brighter match).
 */
object BossMapHuntActions {

    private const val TAG = "FarmBosses"
    const val BOSS_ALIVE = "templates/mu/ui/map/boss_alive.png"
    const val BOSS_DEAD = "templates/mu/ui/map/boss_dead.png"
    const val GOLDEN_ALIVE = "templates/mu/ui/map/golden_alive.png"
    private const val THRESHOLD = 0.90f
    /** Prefer alive when it outscores dead at the same spot (no extra margin — device deltas are tiny). */
    private const val ALIVE_OVER_DEAD_MARGIN = 0.0f
    private const val ARRIVAL_RADIUS = 10

    /** Parchment map canvas @ 1280×720 (excludes left teleport list + chrome). */
    fun zoneMapContentRoi(frameWidth: Int, frameHeight: Int): Rect {
        val left = RefCoords.scaleX(560, frameWidth)
        val top = RefCoords.scaleY(100, frameHeight)
        val right = RefCoords.scaleX(2200, frameWidth)
        val bottom = RefCoords.scaleY(1280, frameHeight)
        return Rect(left, top, right.coerceAtMost(frameWidth), bottom.coerceAtMost(frameHeight))
    }

    suspend fun findAliveBosses(includeGolden: Boolean): List<PcTemplateMatchResult> {
        if (!MapWindowActions.isMapWindowOpen()) {
            if (!MapWindowActions.openMapWindow(retries = 2, timeoutMs = 4_000)) {
                Log.w(TAG, "[HUNT] open map failed")
                return emptyList()
            }
        }
        delay(400)
        val frame = ScreenCaptureManager.getLatestBitmap() ?: run {
            Log.w(TAG, "[HUNT] no frame for boss scan")
            return emptyList()
        }
        return try {
            val roi = zoneMapContentRoi(frame.width, frame.height)
            val rawAlive = NavigationVision.findAllOnFrame(frame, BOSS_ALIVE, THRESHOLD, roi)
            if (rawAlive.isEmpty()) {
                NavigationVision.logBestScore(BOSS_ALIVE, roi)
            }
            val alive = filterAliveVsDead(frame, rawAlive)
            Log.d(
                TAG,
                "[HUNT] boss_alive raw=${rawAlive.size} kept=${alive.size} " +
                    "best=${alive.firstOrNull()?.score}",
            )
            val golden = if (includeGolden) {
                NavigationVision.findAllOnFrame(frame, GOLDEN_ALIVE, THRESHOLD, roi).also { hits ->
                    if (hits.isEmpty()) {
                        NavigationVision.logBestScore(GOLDEN_ALIVE, roi)
                    }
                    Log.d(TAG, "[HUNT] golden_alive matches=${hits.size} best=${hits.firstOrNull()?.score}")
                }
            } else {
                emptyList()
            }
            (alive + golden).sortedByDescending { it.score }
        } finally {
            frame.recycle()
        }
    }

    /**
     * Keep hits where alive score beats dead score at the same patch (+ margin).
     * Missing [BOSS_DEAD] template → keep raw alive hits (degraded).
     */
    private fun filterAliveVsDead(
        frame: android.graphics.Bitmap,
        candidates: List<PcTemplateMatchResult>,
    ): List<PcTemplateMatchResult> {
        if (candidates.isEmpty()) {
            return emptyList()
        }
        val kept = ArrayList<PcTemplateMatchResult>(candidates.size)
        for (hit in candidates) {
            val pad = 6
            val local = Rect(
                (hit.bestX - pad).coerceAtLeast(0),
                (hit.bestY - pad).coerceAtLeast(0),
                (hit.bestX + hit.templateWidth + pad).coerceAtMost(frame.width),
                (hit.bestY + hit.templateHeight + pad).coerceAtMost(frame.height),
            )
            val dead = NavigationVision.probeOnFrame(frame, BOSS_DEAD, local)
            val aliveWins = hit.score >= dead.score + ALIVE_OVER_DEAD_MARGIN
            Log.d(
                TAG,
                "[HUNT] alive_vs_dead at=(${hit.centerX},${hit.centerY}) " +
                    "alive=${"%.3f".format(hit.score)} dead=${"%.3f".format(dead.score)} " +
                    "keep=$aliveWins",
            )
            if (aliveWins) {
                kept += hit
            }
        }
        return kept
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
        val matches = findAliveBosses(includeGolden)
        if (matches.isEmpty()) {
            Log.d(TAG, "[HUNT] no boss icons on map")
            MapWindowActions.closeMapWindow()
            BossHuntState.clearBossTarget()
            return false
        }
        val best = matches.first()
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
                "tpl=${best.templateName} candidates=${matches.size}",
        )

        if (!NavigationVision.tapScreen(best.centerX, best.centerY, label = "boss_map_icon")) {
            return false
        }
        // Path paints while map is open → Far path may use Random Teleport Seal.
        val randomEnabled =
            ProfileRepository.currentProfile.value?.enableRandomTeleport != false
        val sealsUsed = if (randomEnabled) {
            RandomSealActions.maybeUseRandomIfFarPath()
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
            val arrived = NavigationWaitActions.waitUntilArrivesAtCoord(
                target,
                mapDef,
                timeoutMs = arrivalTimeoutMs,
            )
            if (!arrived) {
                Log.w(TAG, "[HUNT] arrival timeout — will still try Focus on next tick")
            }
            return true
        }

        Log.w(TAG, "[HUNT] no affine for ${mapDef.id} — fallback auto_nav wait")
        BossHuntState.clearBossTarget()
        NavigationWaitActions.waitUntilNavigationComplete()
        delay(1_500)
        return true
    }
}
