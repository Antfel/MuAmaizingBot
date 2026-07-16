package com.example.muamaizingbot.overlay.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object OverlayHudStyle {
    val panelBackground = Color(0xE6121212)
    val bubbleBackground = Color(0xE6121212)
    val accentGreen = Color(0xFF43A047)
    val accentOrange = Color(0xFFFB8C00)
    val accentRed = Color(0xFFE53935)
    val textPrimary = Color(0xFFFFFFFF)
    val textSecondary = Color(0xB3FFFFFF)
    val panelWidth = 118.dp
    val bubbleSize = 36.dp
    val controlButtonSize = 28.dp
    val controlIconSize = 16.dp
    val cornerRadius = 8.dp
    val titleFontSize = 11.sp
    val statusFontSize = 10.sp
    val metaFontSize = 9.sp
    /** Collapse expanded panel after this idle time (game-style HUD hide). */
    const val AUTO_COLLAPSE_MS = 4_000L
}
