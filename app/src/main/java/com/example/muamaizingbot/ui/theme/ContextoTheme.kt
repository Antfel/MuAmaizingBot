package com.example.muamaizingbot.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

private val ContextoColorScheme = darkColorScheme(
    primary = ContextoColors.Teal,
    onPrimary = ContextoColors.Bg,
    primaryContainer = ContextoColors.TealDim,
    onPrimaryContainer = ContextoColors.Teal,
    secondary = ContextoColors.Amber,
    onSecondary = ContextoColors.Bg,
    secondaryContainer = ContextoColors.AmberDim,
    onSecondaryContainer = ContextoColors.Amber,
    background = ContextoColors.Bg,
    onBackground = ContextoColors.TextPrimary,
    surface = ContextoColors.Surface,
    onSurface = ContextoColors.TextPrimary,
    surfaceVariant = ContextoColors.SurfaceElevated,
    onSurfaceVariant = ContextoColors.TextSecondary,
    outline = ContextoColors.Border,
    error = ContextoColors.Danger,
    onError = ContextoColors.TextPrimary,
)

/** Scoped dark Contexto theme for Perfiles + profile editor (+ sheets). */
@Composable
fun ContextoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ContextoColorScheme,
        typography = Typography,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ContextoColors.Bg),
        ) {
            content()
        }
    }
}
