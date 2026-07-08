package com.example.muamaizingbot.calibration

import com.example.muamaizingbot.vision.coord.RefCoords

/** Approximate ref-frame center for template PNGs when rescaling per device calibration. */
object TemplateRefRegions {

    fun estimateRefCenter(relativePath: String): Pair<Int, Int> {
        val normalized = relativePath.replace('\\', '/')
        return when {
            normalized.contains("ui/common/close") -> 2480 to 72
            // "Map" title bar on the zone map panel (upper-right, same band as close_x).
            normalized.contains("ui/common/map_window") -> 2350 to 96
            normalized.contains("ui/common/") && normalized.contains("map") -> 2440 to 120
            normalized.contains("/mapsui/") -> 900 to 540
            normalized.contains("/current/") -> 280 to 1380
            normalized.contains("/wires/") -> 1280 to 680
            normalized.startsWith("ui/") -> 1280 to 720
            normalized.startsWith("maps/") -> 900 to 540
            else -> RefCoords.REF_WIDTH / 2 to RefCoords.REF_HEIGHT / 2
        }
    }
}
