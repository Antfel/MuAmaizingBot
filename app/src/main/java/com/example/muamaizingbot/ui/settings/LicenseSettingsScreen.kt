package com.example.muamaizingbot.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.muamaizingbot.R
import com.example.muamaizingbot.content.MapContentSync
import com.example.muamaizingbot.license.LicenseGate
import com.example.muamaizingbot.license.LicenseStore
import com.example.muamaizingbot.ui.settings.components.ContextoDenseField
import com.example.muamaizingbot.ui.settings.components.ContextoPrimaryButton
import com.example.muamaizingbot.ui.settings.components.ContextoSecondaryButton
import com.example.muamaizingbot.ui.settings.components.ContextoSectionTitle
import com.example.muamaizingbot.ui.theme.ContextoColors
import com.example.muamaizingbot.ui.theme.ContextoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LicenseSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ContextoTheme {
        LicenseSettingsScreenContent(
            onBack = onBack,
            modifier = modifier,
        )
    }
}

@Composable
private fun LicenseSettingsScreenContent(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var licenseKey by remember { mutableStateOf(LicenseStore.licenseKey()) }
    var savedHint by remember { mutableStateOf("") }
    val savedLabel = stringResource(R.string.saved)
    val scope = rememberCoroutineScope()

    val hasSession by LicenseGate.hasSession.collectAsState()
    val sessionId by LicenseGate.sessionId.collectAsState()
    val userMessage by LicenseGate.userMessage.collectAsState()
    val contentStatus by MapContentSync.status.collectAsState()
    val deviceId = remember { LicenseStore.deviceId() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ContextoSectionTitle(
            title = stringResource(R.string.license_title),
            subtitle = stringResource(R.string.license_hint),
        )

        ContextoDenseField(
            value = licenseKey,
            onValueChange = {
                licenseKey = it
                savedHint = ""
            },
            label = stringResource(R.string.license_key_label),
            modifier = Modifier.fillMaxWidth(),
        )

        ContextoPrimaryButton(
            text = stringResource(R.string.action_save),
            onClick = {
                LicenseStore.setLicenseKey(licenseKey)
                licenseKey = LicenseStore.licenseKey()
                savedHint = savedLabel
            },
            modifier = Modifier.fillMaxWidth(),
        )

        if (savedHint.isNotEmpty()) {
            Text(
                text = savedHint,
                color = ContextoColors.Teal,
                fontSize = 12.sp,
            )
        }

        Text(
            text = stringResource(R.string.license_device_id),
            color = ContextoColors.TextPrimary,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
        )
        Text(
            text = deviceId,
            color = ContextoColors.TextMuted,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
        )

        Text(
            text = if (hasSession) {
                stringResource(
                    R.string.license_session_active,
                    sessionId?.take(8).orEmpty(),
                )
            } else {
                stringResource(R.string.license_session_none)
            },
            color = if (hasSession) ContextoColors.Teal else ContextoColors.TextSecondary,
            fontSize = 13.sp,
        )

        Text(
            text = stringResource(R.string.content_maps_title),
            color = ContextoColors.TextPrimary,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
        )
        Text(
            text = stringResource(
                R.string.content_maps_version,
                contentStatus.localPackVersion,
            ),
            color = ContextoColors.TextSecondary,
            fontSize = 13.sp,
        )
        val syncMsg = contentStatus.lastSyncMessage
        if (!syncMsg.isNullOrBlank()) {
            Text(
                text = syncMsg,
                fontSize = 12.sp,
                color = when (contentStatus.lastSyncOk) {
                    true -> ContextoColors.Teal
                    false -> ContextoColors.Danger
                    null -> ContextoColors.TextMuted
                },
            )
        }

        ContextoSecondaryButton(
            text = if (contentStatus.syncing) {
                stringResource(R.string.content_maps_syncing)
            } else {
                stringResource(R.string.content_maps_check_updates)
            },
            onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        MapContentSync.sync(
                            baseUrl = LicenseStore.serverUrl(),
                            licenseKey = LicenseStore.licenseKey(),
                            sessionId = LicenseGate.sessionId.value,
                        )
                    }
                }
            },
            enabled = !contentStatus.syncing && LicenseStore.licenseKey().isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )

        val msg = userMessage
        if (!msg.isNullOrBlank()) {
            Text(
                text = msg,
                color = ContextoColors.Danger,
                fontSize = 13.sp,
            )
        }

        ContextoSecondaryButton(
            text = stringResource(R.string.action_back),
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
