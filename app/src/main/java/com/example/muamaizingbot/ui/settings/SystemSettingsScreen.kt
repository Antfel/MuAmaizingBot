package com.example.muamaizingbot.ui.settings

import android.app.Activity
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.muamaizingbot.R
import com.example.muamaizingbot.settings.AppLanguage
import com.example.muamaizingbot.settings.AppSettingsStore
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
    val context = LocalContext.current
    val activity = context as? Activity
    val language by AppSettingsStore.language.collectAsState()

    var speedMode by remember { mutableStateOf(BotSpeedMode.NORMAL) }
    var chatId by remember { mutableStateOf(TelegramStore.chatId()) }
    var alertsEnabled by remember { mutableStateOf(TelegramStore.alertsEnabled()) }
    var savedHint by remember { mutableStateOf("") }
    var testStatus by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val savedLabel = stringResource(R.string.saved)
    val testOkLabel = stringResource(R.string.system_test_ok)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.system_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = stringResource(R.string.system_language),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.system_language_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppLanguage.entries.forEach { lang ->
                FilterChip(
                    selected = language == lang,
                    onClick = {
                        if (language != lang) {
                            AppSettingsStore.setLanguage(lang)
                            activity?.recreate()
                        }
                    },
                    label = { Text(lang.nativeLabel) },
                )
            }
        }

        Text(
            text = stringResource(R.string.system_telegram_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.system_telegram_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.system_telegram_steps),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.system_alerts_enabled))
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
            label = { Text(stringResource(R.string.system_chat_id)) },
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
                    savedHint = savedLabel
                    testStatus = null
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.system_save_chat_id))
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
                            TelegramSendResult.Ok -> testOkLabel
                            is TelegramSendResult.Failed -> result.message
                        }
                    }
                },
                enabled = !testing && chatId.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    if (testing) {
                        stringResource(R.string.system_testing)
                    } else {
                        stringResource(R.string.system_test_telegram)
                    },
                )
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
                color = if (status == testOkLabel) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (!TelegramEndpoint.isConfigured()) {
            Text(
                text = stringResource(R.string.system_token_missing),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Text(
            text = stringResource(R.string.system_speed_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.system_speed_hint),
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
                label = { Text(stringResource(R.string.system_speed_normal)) },
            )
            FilterChip(
                selected = speedMode == BotSpeedMode.FAST,
                onClick = { speedMode = BotSpeedMode.FAST },
                label = { Text(stringResource(R.string.system_speed_fast)) },
            )
        }

        Text(
            text = when (speedMode) {
                BotSpeedMode.NORMAL -> stringResource(R.string.system_speed_selected_normal)
                BotSpeedMode.FAST -> stringResource(R.string.system_speed_selected_fast)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        TextButton(onClick = onBack) {
            Text(stringResource(R.string.action_back))
        }
    }
}
