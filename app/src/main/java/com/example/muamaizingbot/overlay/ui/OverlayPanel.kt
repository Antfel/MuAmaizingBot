package com.example.muamaizingbot.overlay.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.muamaizingbot.R
import com.example.muamaizingbot.accessibility.BotAccessibilityService
import com.example.muamaizingbot.bot.BotAutoRestart
import com.example.muamaizingbot.bot.BotController
import com.example.muamaizingbot.bot.BotRuntimeState
import com.example.muamaizingbot.bot.bosses.BossHuntState
import com.example.muamaizingbot.bot.maintenance.ElfBuffCastGate
import com.example.muamaizingbot.bot.maintenance.ElfBuffSeekGate
import com.example.muamaizingbot.bot.maintenance.ElfBuffSkillMapper
import com.example.muamaizingbot.capture.ScreenCaptureManager
import com.example.muamaizingbot.license.LicenseGate
import com.example.muamaizingbot.overlay.OverlayModeSlot
import com.example.muamaizingbot.overlay.OverlayModeSwitch
import com.example.muamaizingbot.profile.BotProfile
import com.example.muamaizingbot.profile.LocationRepository
import com.example.muamaizingbot.profile.ProfileRepository
import com.example.muamaizingbot.profile.isElfBuffGiverMode
import com.example.muamaizingbot.profile.isFarmBossesMode
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun OverlayPanel(
    onDragBy: (Int, Int) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var lastInteractionAtMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val botState by BotController.state.collectAsState()
    val captureActive by ScreenCaptureManager.isActive.collectAsState()
    val captureReady by ScreenCaptureManager.isReadyFlow.collectAsState()
    val inputConnected = BotAccessibilityService.isConnected

    fun markInteraction() {
        lastInteractionAtMs = System.currentTimeMillis()
    }

    LaunchedEffect(expanded, lastInteractionAtMs) {
        if (!expanded) return@LaunchedEffect
        while (true) {
            val remaining = OverlayHudStyle.AUTO_COLLAPSE_MS -
                (System.currentTimeMillis() - lastInteractionAtMs)
            if (remaining <= 0L) {
                expanded = false
                break
            }
            delay(remaining.coerceAtMost(500L).coerceAtLeast(50L))
        }
    }

    val dragModifier = Modifier.pointerInput(expanded) {
        detectDragGestures(
            onDragStart = { markInteraction() },
            onDragEnd = { onDragEnd() },
            onDragCancel = { onDragEnd() },
        ) { change, dragAmount ->
            change.consume()
            markInteraction()
            onDragBy(dragAmount.x.roundToInt(), dragAmount.y.roundToInt())
        }
    }

    if (expanded) {
        ExpandedOverlay(
            modifier = modifier.then(dragModifier),
            botState = botState,
            captureActive = captureActive,
            captureReady = captureReady,
            inputConnected = inputConnected,
            onCollapse = { expanded = false },
            onInteract = { markInteraction() },
            onStart = {
                markInteraction()
                BotController.start()
                expanded = false
            },
            onPause = {
                markInteraction()
                BotController.pause()
            },
            onStop = {
                markInteraction()
                BotController.stop()
            },
        )
    } else {
        val autoRestart by BotAutoRestart.status.collectAsState()
        BubbleOverlay(
            modifier = modifier.then(dragModifier),
            botState = botState,
            autoRestartPending = autoRestart.isPending,
            onExpand = {
                markInteraction()
                expanded = true
            },
        )
    }
}

@Composable
private fun BubbleOverlay(
    botState: BotRuntimeState,
    autoRestartPending: Boolean,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(OverlayHudStyle.bubbleSize)
            .clip(CircleShape)
            .background(OverlayHudStyle.bubbleBackground)
            .clickable(onClick = onExpand),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = bubbleIconRes(botState)),
            contentDescription = stringResource(botState.labelRes()),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        if (autoRestartPending &&
            (botState == BotRuntimeState.ERROR || botState == BotRuntimeState.PAUSED)
        ) {
            Text(
                text = "R",
                color = OverlayHudStyle.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = OverlayHudStyle.statusFontSize,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(2.dp),
            )
        }
    }
}

@Composable
private fun ExpandedOverlay(
    botState: BotRuntimeState,
    captureActive: Boolean,
    captureReady: Boolean,
    inputConnected: Boolean,
    onCollapse: () -> Unit,
    onInteract: () -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val profile by ProfileRepository.currentProfile.collectAsState()
    val farmSpot by LocationRepository.farmSpot.collectAsState()
    val seekEnabled = ProfileRepository.shouldSeekElfBuff(profile)
    val seekStatus by ElfBuffSeekGate.status.collectAsState()
    val autoRestart by BotAutoRestart.status.collectAsState()
    val licenseMessage by LicenseGate.userMessage.collectAsState()
    val giverMode = profile?.isElfBuffGiverMode() == true
    val castStatus by ElfBuffCastGate.status.collectAsState()
    val farmBossesMode = profile?.isFarmBossesMode() == true
    val bossesKilled by BossHuntState.bossesKilled.collectAsState()
    val modeSwitching by OverlayModeSwitch.switching.collectAsState()

    LaunchedEffect(profile?.botMode, profile?.filename) {
        OverlayModeSwitch.rememberElfSubtype(profile)
    }

    LaunchedEffect(seekEnabled, seekStatus.isOnCooldown) {
        if (!seekEnabled) return@LaunchedEffect
        while (true) {
            ElfBuffSeekGate.refreshStatus()
            delay(1_000L)
        }
    }

    LaunchedEffect(giverMode) {
        if (!giverMode) return@LaunchedEffect
        while (true) {
            ElfBuffCastGate.refreshStatus(profile)
            delay(1_000L)
        }
    }

    Column(
        modifier = modifier
            .width(OverlayHudStyle.panelWidth)
            .clip(RoundedCornerShape(OverlayHudStyle.cornerRadius))
            .background(OverlayHudStyle.panelBackground)
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.overlay_title),
                color = OverlayHudStyle.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = OverlayHudStyle.titleFontSize,
            )
            Text(
                text = "−",
                color = OverlayHudStyle.textSecondary,
                fontSize = OverlayHudStyle.titleFontSize,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable {
                        onInteract()
                        onCollapse()
                    }
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }

        Text(
            text = stringResource(botState.labelRes()),
            color = stateColor(botState),
            fontSize = OverlayHudStyle.statusFontSize,
            fontWeight = FontWeight.Medium,
        )

        val activeProfile = profile
        if (activeProfile != null) {
            // farmSpot collected so chips refresh when locations change.
            OverlayModeRow(
                profile = activeProfile,
                farmSpotConfigured = farmSpot != null,
                switching = modeSwitching,
                onSelect = { slot ->
                    onInteract()
                    OverlayModeSwitch.apply(slot)
                },
            )
        }

        if (farmBossesMode) {
            Text(
                text = stringResource(R.string.overlay_bosses_killed, bossesKilled),
                color = OverlayHudStyle.accentGreen,
                fontSize = OverlayHudStyle.metaFontSize,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (autoRestart.detail.isNotEmpty()) {
            Text(
                text = autoRestart.detail,
                color = OverlayHudStyle.accentOrange,
                fontSize = OverlayHudStyle.metaFontSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        val licenseDetail = licenseMessage.orEmpty()
        if (licenseDetail.isNotEmpty()) {
            Text(
                text = licenseDetail,
                color = OverlayHudStyle.accentOrange,
                fontSize = OverlayHudStyle.metaFontSize,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        val ready = inputConnected && captureReady
        Text(
            text = when {
                !inputConnected -> stringResource(R.string.overlay_input_off)
                !captureActive -> stringResource(R.string.overlay_capture_off)
                !captureReady -> stringResource(R.string.overlay_capture_wait)
                else -> stringResource(R.string.overlay_ready)
            },
            color = if (ready) OverlayHudStyle.accentGreen else OverlayHudStyle.textSecondary,
            fontSize = OverlayHudStyle.metaFontSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (seekEnabled) {
            ElfSeekRow(
                status = seekStatus,
                onReset = {
                    onInteract()
                    ElfBuffSeekGate.reset()
                },
            )
        }

        if (giverMode) {
            ElfCastRow(
                status = castStatus,
                castEnabled = botState == BotRuntimeState.RUNNING && castStatus.hasSkillCoords,
                mapEnabled = botState == BotRuntimeState.RUNNING && captureReady,
                onCast = {
                    onInteract()
                    ElfBuffCastGate.requestCastNow()
                },
                onMap = {
                    onInteract()
                    ElfBuffSkillMapper.requestRemap()
                },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        ) {
            FilledIconButton(
                onClick = onStart,
                modifier = Modifier.size(OverlayHudStyle.controlButtonSize),
                enabled = botState != BotRuntimeState.RUNNING && ready,
                interactionSource = remember { MutableInteractionSource() },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = OverlayHudStyle.accentGreen,
                    disabledContainerColor = OverlayHudStyle.accentGreen.copy(alpha = 0.4f),
                    contentColor = OverlayHudStyle.textPrimary,
                ),
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.overlay_play),
                    modifier = Modifier.size(OverlayHudStyle.controlIconSize),
                )
            }

            FilledIconButton(
                onClick = onPause,
                modifier = Modifier.size(OverlayHudStyle.controlButtonSize),
                enabled = botState == BotRuntimeState.RUNNING,
                interactionSource = remember { MutableInteractionSource() },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = OverlayHudStyle.accentOrange,
                    disabledContainerColor = OverlayHudStyle.accentOrange.copy(alpha = 0.4f),
                    contentColor = OverlayHudStyle.textPrimary,
                ),
            ) {
                Icon(
                    imageVector = Icons.Default.Pause,
                    contentDescription = stringResource(R.string.overlay_pause),
                    modifier = Modifier.size(OverlayHudStyle.controlIconSize),
                )
            }

            FilledIconButton(
                onClick = onStop,
                modifier = Modifier.size(OverlayHudStyle.controlButtonSize),
                enabled = botState != BotRuntimeState.IDLE,
                interactionSource = remember { MutableInteractionSource() },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = OverlayHudStyle.accentRed,
                    disabledContainerColor = OverlayHudStyle.accentRed.copy(alpha = 0.4f),
                    contentColor = OverlayHudStyle.textPrimary,
                ),
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = stringResource(R.string.overlay_stop),
                    modifier = Modifier.size(OverlayHudStyle.controlIconSize),
                )
            }
        }
    }
}

@Composable
private fun OverlayModeRow(
    profile: BotProfile,
    farmSpotConfigured: Boolean,
    switching: Boolean,
    onSelect: (OverlayModeSlot) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        OverlayModeSlot.entries.forEach { slot ->
            val selected = slot.isSelected(profile)
            val configured = when (slot) {
                OverlayModeSlot.FARM, OverlayModeSlot.ELF -> farmSpotConfigured
                OverlayModeSlot.BOSSES -> profile.killBossesConfig.maps.isNotEmpty()
            }
            val label = when (slot) {
                OverlayModeSlot.FARM -> stringResource(R.string.overlay_mode_farm)
                OverlayModeSlot.ELF -> stringResource(R.string.overlay_mode_elf)
                OverlayModeSlot.BOSSES -> stringResource(R.string.overlay_mode_bosses)
            }
            Text(
                text = label,
                color = when {
                    selected -> OverlayHudStyle.accentGreen
                    !configured -> OverlayHudStyle.textSecondary.copy(alpha = 0.45f)
                    switching -> OverlayHudStyle.textSecondary.copy(alpha = 0.45f)
                    else -> OverlayHudStyle.textPrimary
                },
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = OverlayHudStyle.metaFontSize,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        if (selected) {
                            OverlayHudStyle.accentGreen.copy(alpha = 0.22f)
                        } else {
                            Color.Transparent
                        },
                    )
                    .clickable(
                        enabled = configured && !selected && !switching,
                        onClick = { onSelect(slot) },
                    )
                    .padding(horizontal = 2.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun ElfCastRow(
    status: ElfBuffCastGate.Status,
    castEnabled: Boolean,
    mapEnabled: Boolean,
    onCast: () -> Unit,
    onMap: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = status.label(),
            color = when {
                !status.hasSkillCoords -> OverlayHudStyle.accentOrange
                status.forcePending || status.isReady -> OverlayHudStyle.accentGreen
                else -> OverlayHudStyle.textSecondary
            },
            fontSize = OverlayHudStyle.metaFontSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.overlay_map),
            color = if (mapEnabled) OverlayHudStyle.accentOrange else OverlayHudStyle.textSecondary,
            fontWeight = FontWeight.SemiBold,
            fontSize = OverlayHudStyle.metaFontSize,
            modifier = Modifier
                .clip(RoundedCornerShape(3.dp))
                .clickable(enabled = mapEnabled, onClick = onMap)
                .padding(horizontal = 4.dp, vertical = 1.dp),
        )
        Text(
            text = stringResource(R.string.overlay_cast),
            color = if (castEnabled) OverlayHudStyle.accentGreen else OverlayHudStyle.textSecondary,
            fontWeight = FontWeight.SemiBold,
            fontSize = OverlayHudStyle.metaFontSize,
            modifier = Modifier
                .clip(RoundedCornerShape(3.dp))
                .clickable(enabled = castEnabled, onClick = onCast)
                .padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}

@Composable
private fun ElfSeekRow(
    status: ElfBuffSeekGate.Status,
    onReset: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = status.label(),
            color = if (status.isOnCooldown) {
                OverlayHudStyle.accentOrange
            } else {
                OverlayHudStyle.textSecondary
            },
            fontSize = OverlayHudStyle.metaFontSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (status.isOnCooldown) {
            Text(
                text = stringResource(R.string.overlay_reset),
                color = OverlayHudStyle.accentGreen,
                fontWeight = FontWeight.SemiBold,
                fontSize = OverlayHudStyle.metaFontSize,
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .clickable(onClick = onReset)
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}

private fun bubbleIconRes(state: BotRuntimeState): Int = when (state) {
    BotRuntimeState.RUNNING -> R.drawable.ic_bot_running
    BotRuntimeState.PAUSED -> R.drawable.ic_bot_paused
    BotRuntimeState.IDLE,
    BotRuntimeState.ERROR,
    -> R.drawable.ic_bot_stopped
}

private fun stateColor(state: BotRuntimeState) = when (state) {
    BotRuntimeState.RUNNING -> OverlayHudStyle.accentGreen
    BotRuntimeState.PAUSED -> OverlayHudStyle.accentOrange
    BotRuntimeState.ERROR -> OverlayHudStyle.accentRed
    else -> OverlayHudStyle.textSecondary
}
