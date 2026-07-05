package com.example.muamaizingbot.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.muamaizingbot.calibration.CalibrationAnchor
import com.example.muamaizingbot.calibration.CalibrationRepository
import com.example.muamaizingbot.capture.ScreenCaptureManager
import com.example.muamaizingbot.overlay.CalibrationOverlayManager
import com.example.muamaizingbot.overlay.OverlayPermission
import com.example.muamaizingbot.vision.coord.RefCoords

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationScreen(
    onBack: () -> Unit,
    onRequestCapture: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val calibration by CalibrationRepository.state.collectAsState()
    val captureActive by ScreenCaptureManager.isActive.collectAsState()
    val captureSize = ScreenCaptureManager.peekLatestBitmapSize()
    var overlayGranted by remember { mutableStateOf(OverlayPermission.canDrawOverlays(context)) }
    var overlayRunning by remember { mutableStateOf(CalibrationOverlayManager.isRunning) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayGranted = OverlayPermission.canDrawOverlays(context)
                overlayRunning = CalibrationOverlayManager.isRunning
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val captureMatches = captureSize?.let { (w, h) ->
        calibration.isComplete && calibration.captureWidth == w && calibration.captureHeight == h
    } ?: false

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calibración HUD") },
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Alinea 4 puntos fijos del HUD (ATK, Switch, Level, Mount) para corregir " +
                    "taps en pantallas que no coinciden exactamente con ${RefCoords.REF_WIDTH}×${RefCoords.REF_HEIGHT}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            StatusCard(
                title = "Captura",
                value = when {
                    !captureActive -> "Inactiva — actívala desde Inicio"
                    captureSize != null -> "${captureSize.first} × ${captureSize.second}"
                    else -> "Activa"
                },
            )

            StatusCard(
                title = "Calibración guardada",
                value = when {
                    calibration.isComplete ->
                        "${calibration.captureWidth}×${calibration.captureHeight}"
                    calibration.inProgress -> "En progreso…"
                    else -> "Sin calibrar"
                },
            )

            if (calibration.isComplete && captureSize != null && !captureMatches) {
                Text(
                    text = "La captura actual (${captureSize.first}×${captureSize.second}) no coincide " +
                        "con la calibración guardada. Vuelve a calibrar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (captureMatches) {
                Text(
                    text = "Calibración activa para la captura actual.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "Puntos de referencia",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    CalibrationAnchor.ordered.forEach { anchor ->
                        val saved = calibration.screenPoints[anchor]
                        Text(
                            text = if (saved != null) {
                                "${anchor.label}: (${saved.first}, ${saved.second})"
                            } else {
                                "${anchor.label}: ref (${anchor.refX}, ${anchor.refY})"
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            Text(
                text = "1. Abre MU Immortal en fullscreen.\n" +
                    "2. Activa captura de pantalla.\n" +
                    "3. Inicia calibración y encaja cada rectángulo sobre el HUD.\n" +
                    "4. Confirmar los 4 pasos (controles se mueven según el punto).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = {
                    when {
                        !overlayGranted -> {
                            context.startActivity(OverlayPermission.createSettingsIntent(context))
                            Toast.makeText(context, "Concede permiso de superposición", Toast.LENGTH_SHORT).show()
                        }
                        !captureActive || captureSize == null -> {
                            onRequestCapture()
                            Toast.makeText(context, "Activa la captura de pantalla primero", Toast.LENGTH_SHORT).show()
                        }
                        else -> when (CalibrationOverlayManager.start(context)) {
                            CalibrationOverlayManager.StartResult.OK ->
                                Toast.makeText(context, "Calibración iniciada", Toast.LENGTH_SHORT).show()
                            CalibrationOverlayManager.StartResult.NO_CAPTURE ->
                                Toast.makeText(context, "Sin frame de captura", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !CalibrationOverlayManager.isRunning,
            ) {
                Text("Iniciar calibración")
            }

            if (calibration.isComplete) {
                OutlinedButton(
                    onClick = {
                        CalibrationRepository.clear(context)
                        Toast.makeText(context, "Calibración borrada", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Borrar calibración")
                }
            }

            if (overlayRunning) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Overlay de calibración activo sobre el juego.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun StatusCard(title: String, value: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = title, style = MaterialTheme.typography.labelMedium)
            Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        }
    }
}
