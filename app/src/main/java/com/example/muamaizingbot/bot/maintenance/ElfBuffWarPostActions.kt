package com.example.muamaizingbot.bot.maintenance

import android.util.Log
import com.example.muamaizingbot.bot.navigation.NavigationOrchestrator
import com.example.muamaizingbot.bot.navigation.NavigationWaitActions
import com.example.muamaizingbot.maps.CoordinateMapping
import com.example.muamaizingbot.maps.MapDefinitionRepository
import com.example.muamaizingbot.profile.FarmLocation
import com.example.muamaizingbot.profile.LocationRepository
import com.example.muamaizingbot.profile.ProfileRepository

/**
 * War / APEX post: capture HUD coords at Start, convert to minimap pixels via Divine affine.
 *
 * No map validation / teleport — the bot is started already inside the Divine event.
 * After death, only open map + tap the saved pixel to return to the start point.
 */
object ElfBuffWarPostActions {

    private const val TAG = "ElfBuffWar"

    /** Affine / bounds source for HUD→pixel (Divine event). */
    const val WAR_MAP_ID = "divine_realm_1"

    /**
     * Read current HUD X/Y and persist as [war_post].
     * Assumes the character is already inside the War event.
     */
    suspend fun captureWarPost(): FarmLocation? {
        val profile = ProfileRepository.currentProfile.value
        if (profile == null) {
            Log.w(TAG, "[WAR] capture post skipped — no profile")
            return null
        }

        val mapDef = MapDefinitionRepository.getById(WAR_MAP_ID)
        if (mapDef == null) {
            Log.w(TAG, "[WAR] capture post skipped — map def missing id=$WAR_MAP_ID")
            return null
        }

        val coords = NavigationWaitActions.readHudGameCoordinates(mapDef)
        if (coords == null) {
            Log.w(TAG, "[WAR] capture post failed — HUD coords unreadable")
            return null
        }
        val (gx, gy) = coords

        val pixel = if (CoordinateMapping.hasMapping(mapDef)) {
            CoordinateMapping.mapCoordToPixel(mapDef, gx, gy)
        } else {
            null
        }
        if (pixel == null) {
            Log.w(TAG, "[WAR] capture post failed — no affine pixel for ($gx,$gy)")
            return null
        }
        val (px, py) = pixel
        val existing = LocationRepository.getWarPost(profile.filename)
        val wire = existing?.wire?.takeIf { it > 0 }
            ?: mapDef.availableWires().firstOrNull()
            ?: 1

        val saved = LocationRepository.upsertWarPost(
            profileFilename = profile.filename,
            mapId = WAR_MAP_ID,
            wire = wire,
            x = px,
            y = py,
            coordX = gx,
            coordY = gy,
            isCross = mapDef.isCross,
        )
        Log.i(
            TAG,
            "[WAR] war_post captured map=$WAR_MAP_ID wire=$wire " +
                "pixel=($px,$py) coords=($gx,$gy)",
        )
        return saved
    }

    /**
     * Return to the Start capture point via minimap tap only (no map teleport).
     * If no post yet (e.g. first start), captures current HUD instead.
     */
    suspend fun navigateToWarPost(reason: String): Boolean {
        val profile = ProfileRepository.currentProfile.value
        if (profile == null) {
            Log.w(TAG, "[WAR] navigate post skipped reason=$reason — no profile")
            return false
        }

        var post = LocationRepository.getWarPost(profile.filename)
            ?: LocationRepository.warPost.value

        if (post == null) {
            Log.d(TAG, "[WAR] no war_post yet reason=$reason — capture current HUD")
            post = captureWarPost()
            if (post == null) {
                return false
            }
            return true
        }

        Log.d(
            TAG,
            "[WAR] return to post reason=$reason " +
                "pixel=(${post.x},${post.y}) coords=(${post.coordX},${post.coordY})",
        )
        return NavigationOrchestrator.goToWarPost(post)
    }
}
