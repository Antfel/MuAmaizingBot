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
 * Capture / return to War (APEX) post using HUD game coords + minimap affine tap.
 */
object ElfBuffWarPostActions {

    private const val TAG = "ElfBuffWar"

    /**
     * Read current HUD X/Y and persist as [war_post] for this profile.
     * Map/wire come from the configured farm spot (Divine) or profile fallback.
     */
    suspend fun captureWarPost(): FarmLocation? {
        val profile = ProfileRepository.currentProfile.value
        if (profile == null) {
            Log.w(TAG, "[WAR] capture post skipped — no profile")
            return null
        }

        val farmSpot = LocationRepository.getFarmSpot(profile.filename)
        val mapId = farmSpot?.map?.takeIf { it.isNotBlank() }
            ?: profile.map.takeIf { it.isNotBlank() }
        if (mapId == null) {
            Log.w(TAG, "[WAR] capture post skipped — no Divine/farm map configured")
            return null
        }

        val mapDef = MapDefinitionRepository.getById(mapId)
        if (mapDef == null) {
            Log.w(TAG, "[WAR] capture post skipped — map def missing id=$mapId")
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
        val wire = farmSpot?.wire ?: profile.wire

        val saved = LocationRepository.upsertWarPost(
            profileFilename = profile.filename,
            mapId = mapId,
            wire = wire,
            x = px,
            y = py,
            coordX = gx,
            coordY = gy,
        )
        Log.i(
            TAG,
            "[WAR] war_post captured map=$mapId pixel=($px,$py) coords=($gx,$gy)",
        )
        return saved
    }

    suspend fun navigateToWarPost(reason: String): Boolean {
        val profile = ProfileRepository.currentProfile.value
        if (profile == null) {
            Log.w(TAG, "[WAR] navigate post skipped reason=$reason — no profile")
            return false
        }
        var post = LocationRepository.getWarPost(profile.filename)
            ?: LocationRepository.warPost.value

        if (post == null) {
            Log.w(TAG, "[WAR] no war_post yet reason=$reason — reach map then capture")
            if (!MapCheckActions.isInConfiguredMap()) {
                if (!NavigationOrchestrator.goToActiveFarmSpot(ensureAuto = false)) {
                    Log.w(TAG, "[WAR] reach Divine for capture failed reason=$reason")
                    return false
                }
            }
            post = captureWarPost()
            if (post == null) {
                return false
            }
            // Already near current position after capture — no minimap hop needed.
            return true
        }

        Log.d(
            TAG,
            "[WAR] navigate post reason=$reason map=${post.map} " +
                "pixel=(${post.x},${post.y}) coords=(${post.coordX},${post.coordY})",
        )
        return NavigationOrchestrator.goToWarPost(post)
    }
}
