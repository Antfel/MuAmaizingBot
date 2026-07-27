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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.muamaizingbot.telegram.TelegramEndpoint
import com.example.muamaizingbot.telegram.TelegramNotifier
import com.example.muamaizingbot.telegram.TelegramSendResult
import com.example.muamaizingbot.telegram.TelegramStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var speedMode by remember { mutableStateOf(BotSpeedMode.NORMAL) }
    var chatId by remember { mutableStateOf(TelegramStore.chatId()) }
    var alertsEnabled by remember { mutableStateOf(TelegramStore.alertsEnabled()) }
    var savedHint by remember { mutableStateOf("") }
    var testStatus by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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
            text = "Alertas Telegram",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Avisos si el juego se desconecta o entra en mantenimiento. " +
                "El token del bot va embebido en la app; solo configuras tu Chat ID.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            text = "1. Abre @MuAmaizingAlertBot en Telegram\n" +
                "2. Toca Start — el bot te responderá tu Chat ID\n" +
                "3. Pégalo abajo y usa «Probar Telegram»",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Alertas activas")
            Switch(
                checked = alertsEnabled,
                onCheckedChange = {
                    alertsEnabled = it
                    TelegramStore.setAlertsEnabled(it)
                    savedHint = ""
                },
            )
        }

        OutlinedTextField(
            value = chatId,
            onValueChange = {
                chatId = it.filter { ch -> ch.isDigit() || ch == '-' }
                savedHint = ""
                testStatus = null
            },
            label = { Text("Chat ID") },
            placeholder = { Text("6054316335") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = {
                    TelegramStore.setChatId(chatId)
                    chatId = TelegramStore.chatId()
                    savedHint = "Guardado"
                    testStatus = null
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("Guardar Chat ID")
            }
            OutlinedButton(
                onClick = {
                    TelegramStore.setChatId(chatId)
                    chatId = TelegramStore.chatId()
                    testing = true
                    testStatus = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            TelegramNotifier.sendTestMessage()
                        }
                        testing = false
                        testStatus = when (result) {
                            TelegramSendResult.Ok -> "Mensaje enviado — revisa Telegram"
                            is TelegramSendResult.Failed -> result.message
                        }
                    }
                },
                enabled = !testing && chatId.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) {
                Text(if (testing) "Enviando…" else "Probar Telegram")
            }
        }

        if (savedHint.isNotEmpty()) {
            Text(
                text = savedHint,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        testStatus?.let { status ->
            Text(
                text = status,
                color = if (status.startsWith("Mensaje enviado")) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (!TelegramEndpoint.isConfigured()) {
            Text(
                text = "Este build no tiene token de Telegram embebido (solo desarrollo).",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

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
