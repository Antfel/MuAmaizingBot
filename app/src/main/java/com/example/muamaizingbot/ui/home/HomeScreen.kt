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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.muamaizingbot.R
import com.example.muamaizingbot.accessibility.AccessibilityHelper
import com.example.muamaizingbot.accessibility.BotAccessibilityService
import com.example.muamaizingbot.bot.BotController
import com.example.muamaizingbot.capture.ScreenCaptureManager
import com.example.muamaizingbot.maps.MapDefinitionRepository
import com.example.muamaizingbot.profile.BotMode
import com.example.muamaizingbot.profile.LocationRepository
import com.example.muamaizingbot.profile.ProfileRepository
import com.example.muamaizingbot.profile.isElfBuffGiverMode
import com.example.muamaizingbot.profile.isElfBuffWarMode
import com.example.muamaizingbot.profile.isFarmBossesMode
import com.example.muamaizingbot.overlay.OverlayManager
import com.example.muamaizingbot.overlay.OverlayPermission

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
            text = stringResource(R.string.home_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )

        Text(
            text = stringResource(R.string.home_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        StatusCard(
            title = stringResource(R.string.home_profile),
            value = currentProfile?.displayName ?: stringResource(R.string.label_none),
        )

        StatusCard(
            title = stringResource(R.string.home_mode),
            value = currentProfile?.let { stringResource(BotMode.labelRes(it.botMode)) }
                ?: stringResource(R.string.label_em_dash),
        )

        StatusCard(
            title = when {
                currentProfile?.isFarmBossesMode() == true -> stringResource(R.string.home_boss_maps)
                currentProfile?.isElfBuffWarMode() == true -> stringResource(R.string.home_war_post)
                currentProfile?.isElfBuffGiverMode() == true -> stringResource(R.string.home_buff_post)
                else -> stringResource(R.string.home_farm_spot)
            },
            value = when {
                currentProfile?.isFarmBossesMode() == true -> {
                    val n = currentProfile?.killBossesConfig?.maps?.size ?: 0
                    if (n == 0) {
                        stringResource(R.string.home_not_configured)
                    } else {
                        stringResource(R.string.home_maps_configured, n)
                    }
                }
                else -> farmSpot?.summaryLabel(
                    MapDefinitionRepository.getById(farmSpot?.map.orEmpty())?.name
                ) ?: stringResource(R.string.home_not_configured)
            },
        )

        StatusCard(
            title = stringResource(R.string.home_bot),
            value = stringResource(botState.labelRes()),
        )

        StatusCard(
            title = stringResource(R.string.home_accessibility),
            value = when {
                BotAccessibilityService.isConnected -> stringResource(R.string.home_accessibility_connected)
                accessibilityEnabled -> stringResource(R.string.home_accessibility_restart)
                else -> stringResource(R.string.home_accessibility_pending)
            },
        )

        StatusCard(
            title = stringResource(R.string.home_capture),
            value = if (captureActive) {
                stringResource(R.string.home_capture_on)
            } else {
                stringResource(R.string.home_capture_off)
            },
        )

        StatusCard(
            title = stringResource(R.string.home_overlay),
            value = when {
                !overlayGranted -> stringResource(R.string.home_overlay_denied)
                overlayRunning -> stringResource(R.string.home_overlay_running)
                else -> stringResource(R.string.home_overlay_stopped)
            },
        )

        if (!accessibilityEnabled) {
            Button(
                onClick = { context.startActivity(AccessibilityHelper.createSettingsIntent()) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.home_open_accessibility))
            }
        }

        Button(
            onClick = onRequestCapture,
            modifier = Modifier.fillMaxWidth(),
            enabled = !captureActive,
        ) {
            Text(
                if (captureActive) {
                    stringResource(R.string.home_capture_active)
                } else {
                    stringResource(R.string.home_request_capture)
                },
            )
        }

        OutlinedButton(
            onClick = { ScreenCaptureManager.stop(context) },
            modifier = Modifier.fillMaxWidth(),
            enabled = captureActive,
        ) {
            Text(stringResource(R.string.home_stop_capture))
        }

        if (!overlayGranted) {
            Button(
                onClick = { context.startActivity(OverlayPermission.createSettingsIntent(context)) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.home_grant_overlay))
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
            Text(stringResource(R.string.home_start_overlay))
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
            Text(stringResource(R.string.home_stop_overlay))
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = stringResource(R.string.home_steps),
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
