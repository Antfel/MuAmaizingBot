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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.muamaizingbot.license.LicenseGate
import com.example.muamaizingbot.license.LicenseStore

@Composable
fun LicenseSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var licenseKey by remember { mutableStateOf(LicenseStore.licenseKey()) }
    var savedHint by remember { mutableStateOf("") }

    val hasSession by LicenseGate.hasSession.collectAsState()
    val sessionId by LicenseGate.sessionId.collectAsState()
    val userMessage by LicenseGate.userMessage.collectAsState()
    val deviceId = remember { LicenseStore.deviceId() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Licencia",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = "Play (cold start) exige una sesión activa con tu license key.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = licenseKey,
            onValueChange = {
                licenseKey = it
                savedHint = ""
            },
            label = { Text("License key") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedButton(
            onClick = {
                LicenseStore.setLicenseKey(licenseKey)
                licenseKey = LicenseStore.licenseKey()
                savedHint = "Guardado"
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Guardar")
        }

        if (savedHint.isNotEmpty()) {
            Text(
                text = savedHint,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Text(
            text = "Device ID",
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
                "Sesión activa: ${sessionId?.take(8).orEmpty()}…"
            } else {
                "Sin sesión activa"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (hasSession) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )

        val msg = userMessage
        if (!msg.isNullOrBlank()) {
            Text(
                text = msg,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        TextButton(onClick = onBack) {
            Text("Volver")
        }
    }
}
