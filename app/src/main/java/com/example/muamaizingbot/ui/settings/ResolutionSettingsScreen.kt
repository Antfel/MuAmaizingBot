package com.example.muamaizingbot.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.muamaizingbot.calibration.CalibrationRepository
import com.example.muamaizingbot.capture.ScreenCaptureManager
import com.example.muamaizingbot.settings.ResolutionPreset
import com.example.muamaizingbot.settings.ResolutionSettingsRepository
import com.example.muamaizingbot.settings.ResolutionDetectionResult
import com.example.muamaizingbot.vision.coord.RefCoords
import com.example.muamaizingbot.vision.template.TemplateRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResolutionSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val selectedPreset by ResolutionSettingsRepository.preset.collectAsState()
    val detection by ResolutionSettingsRepository.detectionResult.collectAsState()
    val captureActive by ScreenCaptureManager.isActive.collectAsState()
    val calibration by CalibrationRepository.state.collectAsState()
    val detectedSize = ResolutionSettingsRepository.detectedCaptureSize()
    val activeSize = ResolutionSettingsRepository.activeScreenSize()
    val captureMatches = ResolutionSettingsRepository.captureMatchesPreset()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Resolución") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Todos los píxeles del bot están calibrados para ${RefCoords.REF_WIDTH}×${RefCoords.REF_HEIGHT}. " +
                    "Al abrir la app se detecta la pantalla y se preselecciona el preset más cercano.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            detection?.let { result ->
                ResolutionDetectionBanner(result)
            }

            InfoCard(
                title = "Referencia de assets",
                value = "${RefCoords.REF_WIDTH} × ${RefCoords.REF_HEIGHT}",
            )

            InfoCard(
                title = "Captura activa",
                value = when {
                    detectedSize != null -> "${detectedSize.first} × ${detectedSize.second}"
                    captureActive -> "Esperando frame…"
                    else -> "Inactiva (inicia captura en Inicio)"
                },
            )

            InfoCard(
                title = "Escala en uso",
                value = "${activeSize.first} × ${activeSize.second}",
            )

            InfoCard(
                title = "Templates cargados",
                value = when {
                    TemplateRepository.isUsingCalibratedTemplates() -> {
                        val size = TemplateRepository.loadedCaptureSize()
                        "Calibrados en dispositivo (${size?.first ?: "?"}×${size?.second ?: "?"})"
                    }
                    else -> "Preset templates/${TemplateRepository.currentResolutionKey()}/mu/"
                },
            )

            if (calibration.isComplete && TemplateRepository.isUsingCalibratedTemplates()) {
                Text(
                    text = "Con calibración HUD activa, los templates vienen del dispositivo " +
                        "(${calibration.captureWidth}×${calibration.captureHeight}). " +
                        "El preset solo aplica sin calibrar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (detectedSize != null && selectedPreset != ResolutionPreset.AUTO && !captureMatches) {
                Text(
                    text = "Aviso: la captura (${detectedSize.first}×${detectedSize.second}) no coincide con " +
                        "el preset elegido. Con captura activa se usa el tamaño real del frame.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Preset",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            Column(Modifier.selectableGroup()) {
                ResolutionPreset.entries.forEach { preset ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selectedPreset == preset,
                                onClick = {
                                    ResolutionSettingsRepository.setPreset(preset, context)
                                },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selectedPreset == preset,
                            onClick = null,
                        )
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(
                                text = preset.label,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            if (!preset.isAuto) {
                                Text(
                                    text = "Factor ≈ ${"%.0f".format(preset.width * 100.0 / RefCoords.REF_WIDTH)}% ancho",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            Text(
                text = "Los PNG se pre-generan por resolución (reescalado en frío). El código sigue usando " +
                    "paths canónicos templates/mu/...; la app carga la carpeta física según preset o captura.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ResolutionDetectionBanner(result: ResolutionDetectionResult) {
    val containerColor = when (result.matchType) {
        ResolutionDetectionResult.MatchType.EXACT ->
            MaterialTheme.colorScheme.primaryContainer
        ResolutionDetectionResult.MatchType.NEAREST ->
            MaterialTheme.colorScheme.secondaryContainer
        ResolutionDetectionResult.MatchType.UNSUPPORTED ->
            MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = when (result.matchType) {
        ResolutionDetectionResult.MatchType.EXACT ->
            MaterialTheme.colorScheme.onPrimaryContainer
        ResolutionDetectionResult.MatchType.NEAREST ->
            MaterialTheme.colorScheme.onSecondaryContainer
        ResolutionDetectionResult.MatchType.UNSUPPORTED ->
            MaterialTheme.colorScheme.onErrorContainer
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = when (result.matchType) {
                    ResolutionDetectionResult.MatchType.EXACT -> "Pantalla detectada"
                    ResolutionDetectionResult.MatchType.NEAREST -> "Preset aproximado"
                    ResolutionDetectionResult.MatchType.UNSUPPORTED -> "Resolución no soportada"
                },
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
            )
            Text(
                text = result.userMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
            )
        }
    }
}

@Composable
private fun InfoCard(title: String, value: String) {
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
