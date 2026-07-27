package com.example.muamaizingbot.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * App-wide timing profile. UI stub — delays still use Normal timings until wired.
 */
enum class BotSpeedMode {
    NORMAL,
    FAST,
}

@Composable
fun SystemSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Local UI only; persistence + delay scaling come later.
    var speedMode by remember { mutableStateOf(BotSpeedMode.NORMAL) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Sistema",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = "Velocidad del bot",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Normal usa los delays actuales. Rápido acortará esperas en algunos " +
                "procesos (aún en análisis — no aplica cambios por ahora).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = speedMode == BotSpeedMode.NORMAL,
                onClick = { speedMode = BotSpeedMode.NORMAL },
                label = { Text("Normal") },
            )
            FilterChip(
                selected = speedMode == BotSpeedMode.FAST,
                onClick = { speedMode = BotSpeedMode.FAST },
                label = { Text("Rápido") },
            )
        }

        Text(
            text = when (speedMode) {
                BotSpeedMode.NORMAL -> "Seleccionado: Normal (comportamiento actual)."
                BotSpeedMode.FAST -> "Seleccionado: Rápido — pendiente de implementar."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        TextButton(onClick = onBack) {
            Text("Volver")
        }
    }
}
