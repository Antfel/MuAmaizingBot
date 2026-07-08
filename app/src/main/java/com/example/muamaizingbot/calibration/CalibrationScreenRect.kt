package com.example.muamaizingbot.calibration

data class CalibrationScreenRect(
    val centerX: Int,
    val centerY: Int,
    val width: Int,
    val height: Int,
) {
    fun anchorPoint(anchor: CalibrationAnchor): Pair<Int, Int> {
        return anchor.anchorPointForFrameCenter(centerX, centerY, width, height)
    }
}
