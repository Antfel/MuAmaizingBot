package com.example.muamaizingbot.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.muamaizingbot.R
import com.example.muamaizingbot.content.MapContentSync
import com.example.muamaizingbot.license.LicenseGate
import com.example.muamaizingbot.license.LicenseStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LicenseSettingsScreen(
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
        Text(
            text = stringResource(R.string.license_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = stringResource(R.string.license_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = licenseKey,
            onValueChange = {
                licenseKey = it
                savedHint = ""
            },
            label = { Text(stringResource(R.string.license_key_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedButton(
            onClick = {
                LicenseStore.setLicenseKey(licenseKey)
                licenseKey = LicenseStore.licenseKey()
                savedHint = savedLabel
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.action_save))
        }

        if (savedHint.isNotEmpty()) {
            Text(
                text = savedHint,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Text(
            text = stringResource(R.string.license_device_id),
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = deviceId,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            style = MaterialTheme.typography.bodyMedium,
            color = if (hasSession) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )

        Text(
            text = stringResource(R.string.content_maps_title),
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = stringResource(
                R.string.content_maps_version,
                contentStatus.localPackVersion,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val syncMsg = contentStatus.lastSyncMessage
        if (!syncMsg.isNullOrBlank()) {
            Text(
                text = syncMsg,
                style = MaterialTheme.typography.bodySmall,
                color = when (contentStatus.lastSyncOk) {
                    true -> MaterialTheme.colorScheme.primary
                    false -> MaterialTheme.colorScheme.error
                    null -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        OutlinedButton(
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
        ) {
            Text(
                text = if (contentStatus.syncing) {
                    stringResource(R.string.content_maps_syncing)
                } else {
                    stringResource(R.string.content_maps_check_updates)
                },
            )
        }

        val msg = userMessage
        if (!msg.isNullOrBlank()) {
            Text(
                text = msg,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        TextButton(onClick = onBack) {
            Text(stringResource(R.string.action_back))
        }
    }
}
