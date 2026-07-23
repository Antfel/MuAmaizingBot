package com.example.muamaizingbot.bot.bosses

import android.graphics.Rect
import android.util.Log
import com.example.muamaizingbot.bot.navigation.MapWindowActions
import com.example.muamaizingbot.bot.navigation.NavigationWaitActions
import com.example.muamaizingbot.capture.ScreenCaptureManager
import com.example.muamaizingbot.maps.CoordinateMapping
import com.example.muamaizingbot.maps.MapDefinition
import com.example.muamaizingbot.profile.FarmLocation
import com.example.muamaizingbot.vision.coord.RefCoords
import com.example.muamaizingbot.vision.navigation.NavigationVision
import com.example.muamaizingbot.vision.template.PcTemplateMatchResult
import kotlinx.coroutines.delay

/**
 * Open zone map → find alive boss icons → tap → wait HUD arrival via affine coords.
 */
object BossMapHuntActions {

    private const val TAG = "FarmBosses"
    const val BOSS_ALIVE = "templates/mu/ui/map/boss_alive.png"
    const val GOLDEN_ALIVE = "templates/mu/ui/map/golden_alive.png"
    private const val THRESHOLD = 0.96f
    private const val ARRIVAL_RADIUS = 10
    private const val ARRIVAL_TIMEOUT_MS = 90_000L

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
        val (w, h) = ScreenCaptureManager.peekLatestBitmapSize()
            ?: RefCoords.activeScreenSize()
        val roi = zoneMapContentRoi(w, h)

        val alive = NavigationVision.findAllTemplates(BOSS_ALIVE, THRESHOLD, roi)
        if (alive.isEmpty()) {
            NavigationVision.logBestScore(BOSS_ALIVE, roi)
        }
        Log.d(TAG, "[HUNT] boss_alive matches=${alive.size} best=${alive.firstOrNull()?.score}")
        val golden = if (includeGolden) {
            NavigationVision.findAllTemplates(GOLDEN_ALIVE, THRESHOLD, roi).also { hits ->
                if (hits.isEmpty()) {
                    NavigationVision.logBestScore(GOLDEN_ALIVE, roi)
                }
                Log.d(TAG, "[HUNT] golden_alive matches=${hits.size} best=${hits.firstOrNull()?.score}")
            }
        } else {
            emptyList()
        }
        return (alive + golden).sortedByDescending { it.score }
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
        delay(500)
        if (!MapWindowActions.closeMapWindow()) {
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
                    "r=$ARRIVAL_RADIUS",
            )
            val arrived = NavigationWaitActions.waitUntilArrivesAtCoord(
                target,
                mapDef,
                timeoutMs = ARRIVAL_TIMEOUT_MS,
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
