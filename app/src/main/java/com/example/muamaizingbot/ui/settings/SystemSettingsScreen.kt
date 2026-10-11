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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.muamaizingbot.R
import com.example.muamaizingbot.settings.AppLanguage
import com.example.muamaizingbot.settings.AppSettingsStore
import com.example.muamaizingbot.settings.BotSpeedMode
import com.example.muamaizingbot.telegram.TelegramEndpoint
import com.example.muamaizingbot.telegram.TelegramNotifier
import com.example.muamaizingbot.telegram.TelegramSendResult
import com.example.muamaizingbot.telegram.TelegramStore
import com.example.muamaizingbot.ui.settings.components.ContextoChip
import com.example.muamaizingbot.ui.settings.components.ContextoDenseField
import com.example.muamaizingbot.ui.settings.components.ContextoSecondaryButton
import com.example.muamaizingbot.ui.settings.components.ContextoSectionTitle
import com.example.muamaizingbot.ui.theme.ContextoColors
import com.example.muamaizingbot.ui.theme.ContextoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SystemSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ContextoTheme {
        SystemSettingsScreenContent(
            onBack = onBack,
            modifier = modifier,
        )
    }
}

@Composable
private fun SystemSettingsScreenContent(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val language by AppSettingsStore.language.collectAsState()
    val speedMode by AppSettingsStore.botSpeed.collectAsState()

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
        ContextoSectionTitle(title = stringResource(R.string.system_title))

        Text(
            text = stringResource(R.string.system_language),
            color = ContextoColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.system_language_hint),
            color = ContextoColors.TextSecondary,
            fontSize = 13.sp,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppLanguage.entries.forEach { lang ->
                ContextoChip(
                    label = lang.nativeLabel,
                    selected = language == lang,
                    onClick = {
                        if (language != lang) {
                            AppSettingsStore.setLanguage(lang)
                            activity?.recreate()
                        }
                    },
                )
            }
        }

        Text(
            text = stringResource(R.string.system_telegram_title),
            color = ContextoColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.system_telegram_hint),
            color = ContextoColors.TextSecondary,
            fontSize = 13.sp,
        )
        Text(
            text = stringResource(R.string.system_telegram_steps),
            color = ContextoColors.TextMuted,
            fontSize = 12.sp,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.system_alerts_enabled),
                color = ContextoColors.TextPrimary,
                fontSize = 13.sp,
            )
            Switch(
                checked = alertsEnabled,
                onCheckedChange = {
                    alertsEnabled = it
                    TelegramStore.setAlertsEnabled(it)
                    savedHint = ""
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ContextoColors.Bg,
                    checkedTrackColor = ContextoColors.Teal,
                    uncheckedThumbColor = ContextoColors.TextSecondary,
                    uncheckedTrackColor = ContextoColors.Border,
                ),
            )
        }

        ContextoDenseField(
            value = chatId,
            onValueChange = {
                chatId = it.filter { ch -> ch.isDigit() || ch == '-' }
                savedHint = ""
                testStatus = null
            },
            label = stringResource(R.string.system_chat_id),
            placeholder = "6054316335",
            modifier = Modifier.fillMaxWidth(),
            keyboardType = KeyboardType.Number,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ContextoSecondaryButton(
                text = stringResource(R.string.system_save_chat_id),
                onClick = {
                    TelegramStore.setChatId(chatId)
                    chatId = TelegramStore.chatId()
                    savedHint = savedLabel
                    testStatus = null
                },
                modifier = Modifier.weight(1f),
            )
            ContextoSecondaryButton(
                text = if (testing) {
                    stringResource(R.string.system_testing)
                } else {
                    stringResource(R.string.system_test_telegram)
                },
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
            )
        }

        if (savedHint.isNotEmpty()) {
            Text(
                text = savedHint,
                color = ContextoColors.Teal,
                fontSize = 12.sp,
            )
        }

        testStatus?.let { status ->
            Text(
                text = status,
                color = if (status == testOkLabel) {
                    ContextoColors.Teal
                } else {
                    ContextoColors.Danger
                },
                fontSize = 12.sp,
            )
        }

        if (!TelegramEndpoint.isConfigured()) {
            Text(
                text = stringResource(R.string.system_token_missing),
                color = ContextoColors.Danger,
                fontSize = 12.sp,
            )
        }

        Text(
            text = stringResource(R.string.system_speed_title),
            color = ContextoColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.system_speed_hint),
            color = ContextoColors.TextSecondary,
            fontSize = 13.sp,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ContextoChip(
                label = stringResource(R.string.system_speed_normal),
                selected = speedMode == BotSpeedMode.NORMAL,
                onClick = { AppSettingsStore.setBotSpeed(BotSpeedMode.NORMAL) },
            )
            ContextoChip(
                label = stringResource(R.string.system_speed_fast),
                selected = speedMode == BotSpeedMode.FAST,
                onClick = { AppSettingsStore.setBotSpeed(BotSpeedMode.FAST) },
            )
        }

        Text(
            text = when (speedMode) {
                BotSpeedMode.NORMAL -> stringResource(R.string.system_speed_selected_normal)
                BotSpeedMode.FAST -> stringResource(R.string.system_speed_selected_fast)
            },
            color = ContextoColors.TextMuted,
            fontSize = 12.sp,
        )

        ContextoSecondaryButton(
            text = stringResource(R.string.action_back),
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
