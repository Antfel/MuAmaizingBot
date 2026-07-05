package com.example.muamaizingbot.calibration

/**
 * Fixed HUD anchors authored on the 2560×1440 reference capture.
 * [refX]/[refY] is the center of each calibration template on that frame.
 */
enum class CalibrationAnchor(
    val id: String,
    val label: String,
    val templateAssetPath: String,
    val refX: Int,
    val refY: Int,
    /** Template crop size on the 2560×1440 reference frame. */
    val refTemplateWidth: Int,
    val refTemplateHeight: Int,
    /** When true, Cancel/Confirm bar is pinned to the bottom (keeps top HUD visible). */
    val panelAtBottom: Boolean,
) {
    TOP_LEFT_ATK(
        id = "atk",
        label = "ATK (arriba izquierda)",
        templateAssetPath = "templates/2560x1440/mu/calibration/atk.png",
        refX = 131,
        refY = 40,
        refTemplateWidth = 115,
        refTemplateHeight = 47,
        panelAtBottom = true,
    ),
    TOP_RIGHT_SWITCH(
        id = "switch",
        label = "Switch (arriba derecha)",
        templateAssetPath = "templates/2560x1440/mu/calibration/switch.png",
        refX = 2499,
        refY = 30,
        refTemplateWidth = 99,
        refTemplateHeight = 50,
        panelAtBottom = true,
    ),
    BOTTOM_LEFT_LEVEL(
        id = "level",
        label = "Level (abajo izquierda)",
        templateAssetPath = "templates/2560x1440/mu/calibration/level.png",
        refX = 47,
        refY = 1400,
        refTemplateWidth = 85,
        refTemplateHeight = 46,
        panelAtBottom = false,
    ),
    BOTTOM_RIGHT_MOUNT(
        id = "mount",
        label = "Mount (abajo derecha)",
        templateAssetPath = "templates/2560x1440/mu/calibration/mount.png",
        refX = 2506,
        refY = 1311,
        refTemplateWidth = 105,
        refTemplateHeight = 90,
        panelAtBottom = false,
    ),
    ;

    companion object {
        val ordered: List<CalibrationAnchor> = entries.toList()
    }
}
