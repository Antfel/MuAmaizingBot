package com.example.muamaizingbot.ui.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.muamaizingbot.overlay.OverlayManager
import com.example.muamaizingbot.overlay.OverlayPermission
import com.example.muamaizingbot.profile.BotMode
import com.example.muamaizingbot.profile.LocationRepository
import com.example.muamaizingbot.profile.ProfileRepository
import com.example.muamaizingbot.profile.isElfBuffGiverMode
import com.example.muamaizingbot.profile.isElfBuffWarMode
import com.example.muamaizingbot.profile.isFarmBossesMode

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

    val locationTitle = when {
        currentProfile?.isFarmBossesMode() == true -> stringResource(R.string.home_boss_maps)
        currentProfile?.isElfBuffWarMode() == true -> stringResource(R.string.home_war_post)
        currentProfile?.isElfBuffGiverMode() == true -> stringResource(R.string.home_buff_post)
        else -> stringResource(R.string.home_farm_spot)
    }
    val locationValue = when {
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
    }
    val accessibilityValue = when {
        BotAccessibilityService.isConnected -> stringResource(R.string.home_accessibility_connected)
        accessibilityEnabled -> stringResource(R.string.home_accessibility_restart)
        else -> stringResource(R.string.home_accessibility_pending)
    }
    val overlayValue = when {
        !overlayGranted -> stringResource(R.string.home_overlay_denied)
        overlayRunning -> stringResource(R.string.home_overlay_running)
        else -> stringResource(R.string.home_overlay_stopped)
    }
    val homeInfo = stringResource(R.string.home_hint) + "\n\n" + stringResource(R.string.home_steps)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CompactInfoTitle(
            title = stringResource(R.string.home_title),
            info = homeInfo,
            titleStyle = MaterialTheme.typography.headlineSmall,
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                StatusRow(
                    title = stringResource(R.string.home_profile),
                    value = currentProfile?.displayName ?: stringResource(R.string.label_none),
                )
                HorizontalDivider()
                StatusRow(
                    title = stringResource(R.string.home_mode),
                    value = currentProfile?.let { stringResource(BotMode.labelRes(it.botMode)) }
                        ?: stringResource(R.string.label_em_dash),
                )
                HorizontalDivider()
                StatusRow(title = locationTitle, value = locationValue)
                HorizontalDivider()
                StatusRow(
                    title = stringResource(R.string.home_bot),
                    value = stringResource(botState.labelRes()),
                )
                HorizontalDivider()
                StatusRow(
                    title = stringResource(R.string.home_accessibility),
                    value = accessibilityValue,
                )
                HorizontalDivider()
                StatusRow(
                    title = stringResource(R.string.home_capture),
                    value = if (captureActive) {
                        stringResource(R.string.home_capture_on)
                    } else {
                        stringResource(R.string.home_capture_off)
                    },
                )
                HorizontalDivider()
                StatusRow(
                    title = stringResource(R.string.home_overlay),
                    value = overlayValue,
                )
            }
        }

        if (!accessibilityEnabled) {
            Button(
                onClick = { context.startActivity(AccessibilityHelper.createSettingsIntent()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp),
            ) {
                Text(stringResource(R.string.home_open_accessibility))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onRequestCapture,
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp),
                enabled = !captureActive,
            ) {
                Text(
                    if (captureActive) {
                        stringResource(R.string.home_capture_active)
                    } else {
                        stringResource(R.string.home_request_capture)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OutlinedButton(
                onClick = { ScreenCaptureManager.stop(context) },
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp),
                enabled = captureActive,
            ) {
                Text(
                    stringResource(R.string.home_stop_capture),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (!overlayGranted) {
            Button(
                onClick = { context.startActivity(OverlayPermission.createSettingsIntent(context)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp),
            ) {
                Text(stringResource(R.string.home_grant_overlay))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp),
                enabled = overlayGranted,
            ) {
                Text(
                    stringResource(R.string.home_start_overlay),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OutlinedButton(
                onClick = {
                    OverlayManager.stop(context)
                    overlayRunning = false
                    BotController.resetToIdle()
                },
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp),
                enabled = overlayRunning,
            ) {
                Text(
                    stringResource(R.string.home_stop_overlay),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun StatusRow(
    title: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.58f),
        )
    }
}

@Composable
private fun CompactInfoTitle(
    title: String,
    info: String,
    modifier: Modifier = Modifier,
    titleStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = title,
            style = titleStyle,
            fontWeight = FontWeight.SemiBold,
        )
        CompactInfoButton(title = title, info = info)
    }
}

@Composable
private fun CompactInfoButton(title: String, info: String) {
    var showInfo by remember { mutableStateOf(false) }
    IconButton(
        onClick = { showInfo = true },
        modifier = Modifier.size(30.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = stringResource(R.string.profile_info),
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text(title) },
            text = { Text(info) },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) {
                    Text(stringResource(R.string.profile_info_close))
                }
            },
        )
    }
}
