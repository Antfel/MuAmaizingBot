package com.example.muamaizingbot.ui.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.muamaizingbot.accessibility.AccessibilityHelper
import com.example.muamaizingbot.accessibility.BotAccessibilityService
import com.example.muamaizingbot.bot.BotController
import com.example.muamaizingbot.capture.ScreenCaptureManager
import com.example.muamaizingbot.maps.MapDefinitionRepository
import com.example.muamaizingbot.profile.LocationRepository
import com.example.muamaizingbot.profile.ProfileRepository
import com.example.muamaizingbot.overlay.OverlayManager
import com.example.muamaizingbot.overlay.OverlayPermission
import com.example.muamaizingbot.settings.ResolutionDetectionResult
import com.example.muamaizingbot.settings.ResolutionSettingsRepository

@Composable
fun HomeScreen(
    onRequestCapture: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var overlayGranted by remember { mutableStateOf(OverlayPermission.canDrawOverlays(context)) }
    var overlayRunning by remember { mutableStateOf(OverlayManager.isRunning) }
    var accessibilityEnabled by remember { mutableStateOf(AccessibilityHelper.isServiceEnabled(context)) }
    val captureActive by ScreenCaptureManager.isActive.collectAsState()
    val botState by BotController.state.collectAsState()
    val currentProfile by ProfileRepository.currentProfile.collectAsState()
    val farmSpot by LocationRepository.farmSpot.collectAsState()
    val resolutionPreset by ResolutionSettingsRepository.preset.collectAsState()
    val resolutionDetection by ResolutionSettingsRepository.detectionResult.collectAsState()

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* optional */ }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayGranted = OverlayPermission.canDrawOverlays(context)
                overlayRunning = OverlayManager.isRunning
                accessibilityEnabled = AccessibilityHelper.isServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Inicio",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )

        Text(
            text = "Usa el menú lateral (☰) para perfiles y farm spot.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        StatusCard(
            title = "Perfil",
            value = currentProfile?.displayName ?: "Ninguno",
        )

        StatusCard(
            title = "Farm spot",
            value = farmSpot?.summaryLabel(
                MapDefinitionRepository.getById(farmSpot?.map.orEmpty())?.name
            ) ?: "Sin configurar",
        )

        StatusCard(
            title = "Resolución",
            value = resolutionPreset.label,
        )

        resolutionDetection?.let { detection ->
            when (detection.matchType) {
                ResolutionDetectionResult.MatchType.UNSUPPORTED -> {
                    Text(
                        text = detection.userMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                ResolutionDetectionResult.MatchType.NEAREST -> {
                    Text(
                        text = detection.userMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ResolutionDetectionResult.MatchType.EXACT -> Unit
            }
        }

        StatusCard(
            title = "Bot",
            value = botState.label,
        )

        StatusCard(
            title = "Accesibilidad",
            value = when {
                BotAccessibilityService.isConnected -> "Conectado"
                accessibilityEnabled -> "Activado (reinicia servicio)"
                else -> "Pendiente"
            },
        )

        StatusCard(
            title = "Captura",
            value = if (captureActive) "Activa" else "Inactiva",
        )

        StatusCard(
            title = "Overlay",
            value = when {
                !overlayGranted -> "Permiso pendiente"
                overlayRunning -> "Activo"
                else -> "Inactivo"
            },
        )

        if (!accessibilityEnabled) {
            Button(
                onClick = { context.startActivity(AccessibilityHelper.createSettingsIntent()) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Activar accesibilidad")
            }
        }

        Button(
            onClick = onRequestCapture,
            modifier = Modifier.fillMaxWidth(),
            enabled = !captureActive,
        ) {
            Text(if (captureActive) "Captura activa" else "Iniciar captura")
        }

        OutlinedButton(
            onClick = { ScreenCaptureManager.stop(context) },
            modifier = Modifier.fillMaxWidth(),
            enabled = captureActive,
        ) {
            Text("Detener captura")
        }

        if (!overlayGranted) {
            Button(
                onClick = { context.startActivity(OverlayPermission.createSettingsIntent(context)) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Conceder permiso overlay")
            }
        }

        Button(
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (!granted) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                OverlayManager.start(context)
                overlayRunning = true
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = overlayGranted,
        ) {
            Text("Mostrar overlay")
        }

        OutlinedButton(
            onClick = {
                OverlayManager.stop(context)
                overlayRunning = false
                BotController.resetToIdle()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = overlayRunning,
        ) {
            Text("Ocultar overlay")
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "1) Configura perfil y spot en el menú.\n2) Activa accesibilidad y captura.\n3) Overlay → Start para farm.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusCard(title: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
