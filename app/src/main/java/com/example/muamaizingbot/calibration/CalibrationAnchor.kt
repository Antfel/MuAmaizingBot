package com.example.muamaizingbot.calibration

import kotlin.math.roundToInt

/**
 * Fixed HUD anchors authored on the 2560×1440 reference capture.
 *
 * - [refAnchorX]/[refAnchorY]: canonical alignment point (crosshair target).
 * - [refTemplateLeft]/[refTemplateTop]/[refTemplateWidth]/[refTemplateHeight]: template crop on ref frame.
 *
 * Author with: ./scripts/run_calibration_tool.sh
 * Source: debug_capture/ref_full.png (2026-07-05)
 */
enum class CalibrationAnchor(
    val id: String,
    val label: String,
    val templateAssetPath: String,
    val refAnchorX: Int,
    val refAnchorY: Int,
    val refTemplateLeft: Int,
    val refTemplateTop: Int,
    val refTemplateWidth: Int,
    val refTemplateHeight: Int,
    /** When true, controls bubble is pinned to the bottom (keeps top HUD visible). */
    val panelAtBottom: Boolean,
) {
    TOP_LEFT_ATK(
        id = "atk",
        label = "ATK (arriba izquierda)",
        templateAssetPath = "templates/2560x1440/mu/calibration/atk.png",
        refAnchorX = 134,
        refAnchorY = 39,
        refTemplateLeft = 81,
        refTemplateTop = 23,
        refTemplateWidth = 103,
        refTemplateHeight = 35,
        panelAtBottom = true,
    ),
    TOP_RIGHT_SWITCH(
        id = "switch",
        label = "Switch (arriba derecha)",
        templateAssetPath = "templates/2560x1440/mu/calibration/switch.png",
        refAnchorX = 2501,
        refAnchorY = 30,
        refTemplateLeft = 2454,
        refTemplateTop = 8,
        refTemplateWidth = 92,
        refTemplateHeight = 46,
        panelAtBottom = true,
    ),
    BOTTOM_LEFT_LEVEL(
        id = "level",
        label = "Level (abajo izquierda)",
        templateAssetPath = "templates/2560x1440/mu/calibration/level.png",
        refAnchorX = 46,
        refAnchorY = 1400,
        refTemplateLeft = 6,
        refTemplateTop = 1383,
        refTemplateWidth = 78,
        refTemplateHeight = 35,
        panelAtBottom = false,
    ),
    BOTTOM_RIGHT_MOUNT(
        id = "mount",
        label = "Mount (abajo derecha)",
        templateAssetPath = "templates/2560x1440/mu/calibration/mount.png",
        refAnchorX = 2513,
        refAnchorY = 1316,
        refTemplateLeft = 2464,
        refTemplateTop = 1266,
        refTemplateWidth = 95,
        refTemplateHeight = 97,
        panelAtBottom = false,
    ),
    ;

    val refTemplateCenterX: Int get() = refTemplateLeft + refTemplateWidth / 2
    val refTemplateCenterY: Int get() = refTemplateTop + refTemplateHeight / 2
    val refAnchorOffsetX: Int get() = refAnchorX - refTemplateCenterX
    val refAnchorOffsetY: Int get() = refAnchorY - refTemplateCenterY
    val templateAspectRatio: Float get() = refTemplateWidth.toFloat() / refTemplateHeight.coerceAtLeast(1)

    fun frameCenterForAnchorPoint(
        anchorScreenX: Int,
        anchorScreenY: Int,
        frameWidthPx: Int,
        frameHeightPx: Int,
    ): Pair<Int, Int> {
        val offX = scaledAnchorOffsetX(frameWidthPx)
        val offY = scaledAnchorOffsetY(frameHeightPx)
        return (anchorScreenX - offX) to (anchorScreenY - offY)
    }

    fun anchorPointForFrameCenter(
        frameCenterX: Int,
        frameCenterY: Int,
        frameWidthPx: Int,
        frameHeightPx: Int,
    ): Pair<Int, Int> {
        val offX = scaledAnchorOffsetX(frameWidthPx)
        val offY = scaledAnchorOffsetY(frameHeightPx)
        return (frameCenterX + offX) to (frameCenterY + offY)
    }

    fun scaledAnchorOffsetX(frameWidthPx: Int): Int {
        return (refAnchorOffsetX.toFloat() * frameWidthPx / refTemplateWidth).roundToInt()
    }

    fun scaledAnchorOffsetY(frameHeightPx: Int): Int {
        return (refAnchorOffsetY.toFloat() * frameHeightPx / refTemplateHeight).roundToInt()
    }

    fun heightForWidth(widthPx: Int): Int {
        return (widthPx / templateAspectRatio).roundToInt().coerceAtLeast(1)
    }

    companion object {
        val ordered: List<CalibrationAnchor> = entries.toList()
    }
}
