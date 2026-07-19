package com.example.muamaizingbot.bot.maintenance

import android.graphics.Rect
import com.example.muamaizingbot.vision.coord.RefCoords

/**
 * No-go zones for **nameplate detection** and **focus taps** (1280×720 capture contract).
 *
 * Derived from user-marked red rects on `avoid_tap_zones_base.png`.
 * Skill-pad taps for casting are allowed separately (not gated by these zones).
 */
object ElfBuffExclusionZones {

    /** Authoring size of the marked screenshot. */
    private const val BASE_W = 1280
    private const val BASE_H = 720

    data class Zone(val id: String, val left: Int, val top: Int, val right: Int, val bottom: Int)

    /**
     * Absolute pixels @ 1280×720. Scaled to live frame via [scaled].
     */
    val BASE_ZONES: List<Zone> = listOf(
        Zone("minimap_ui", 953, 2, 1276, 154),
        Zone("right_icons", 1204, 161, 1271, 335),
        Zone("self", 590, 155, 705, 345),
        Zone("chat", 5, 257, 340, 416),
        Zone("skills", 945, 339, 1271, 714),
        Zone("bottom_left", 4, 420, 459, 718),
        Zone("potions_auto", 468, 466, 797, 718),
        Zone("bottom_mid_hud", 801, 575, 941, 717),
    )

    fun scaled(frameWidth: Int, frameHeight: Int): List<Rect> {
        return BASE_ZONES.map { z ->
            Rect(
                z.left * frameWidth / BASE_W,
                z.top * frameHeight / BASE_H,
                z.right * frameWidth / BASE_W,
                z.bottom * frameHeight / BASE_H,
            )
        }
    }

    fun containsPoint(x: Int, y: Int, frameWidth: Int, frameHeight: Int): Boolean {
        return scaled(frameWidth, frameHeight).any { it.contains(x, y) }
    }

    fun zoneIdAt(x: Int, y: Int, frameWidth: Int, frameHeight: Int): String? {
        val zones = scaled(frameWidth, frameHeight)
        for ((i, rect) in zones.withIndex()) {
            if (rect.contains(x, y)) return BASE_ZONES[i].id
        }
        return null
    }

    /** True if the nameplate rect overlaps any exclusion zone. */
    fun intersectsPlate(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        frameWidth: Int,
        frameHeight: Int,
    ): Boolean {
        val plate = Rect(left, top, right, bottom)
        return scaled(frameWidth, frameHeight).any { Rect.intersects(it, plate) }
    }

    /** Logical-ref fallback for docs / overlay (2560×1440). */
    fun asRefRects(): List<Rect> {
        return BASE_ZONES.map { z ->
            Rect(
                RefCoords.REF_WIDTH * z.left / BASE_W,
                RefCoords.REF_HEIGHT * z.top / BASE_H,
                RefCoords.REF_WIDTH * z.right / BASE_W,
                RefCoords.REF_HEIGHT * z.bottom / BASE_H,
            )
        }
    }
}
