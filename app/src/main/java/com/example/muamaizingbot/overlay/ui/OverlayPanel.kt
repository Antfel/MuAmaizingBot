package com.example.muamaizingbot.overlay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.muamaizingbot.accessibility.BotAccessibilityService
import com.example.muamaizingbot.bot.BotAutoRestart
import com.example.muamaizingbot.bot.BotController
import com.example.muamaizingbot.bot.BotRuntimeState
import com.example.muamaizingbot.bot.maintenance.ElfBuffCastGate
import com.example.muamaizingbot.bot.maintenance.ElfBuffSeekGate
import com.example.muamaizingbot.bot.maintenance.ElfBuffSkillMapper
import com.example.muamaizingbot.capture.ScreenCaptureManager
import com.example.muamaizingbot.profile.ProfileRepository
import com.example.muamaizingbot.profile.isElfBuffGiverMode
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
        Text(
            text = stateShortLabel(botState, autoRestartPending),
            color = bubbleTextColor(botState),
            fontWeight = FontWeight.Bold,
            fontSize = OverlayHudStyle.statusFontSize,
        )
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
    val seekEnabled = ProfileRepository.shouldSeekElfBuff(profile)
    val seekStatus by ElfBuffSeekGate.status.collectAsState()
    val autoRestart by BotAutoRestart.status.collectAsState()
    val giverMode = profile?.isElfBuffGiverMode() == true
    val castStatus by ElfBuffCastGate.status.collectAsState()

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
                text = "MU Bot",
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
            text = botState.label,
            color = stateColor(botState),
            fontSize = OverlayHudStyle.statusFontSize,
            fontWeight = FontWeight.Medium,
        )

        if (autoRestart.detail.isNotEmpty()) {
            Text(
                text = autoRestart.detail,
                color = OverlayHudStyle.accentOrange,
                fontSize = OverlayHudStyle.metaFontSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        val ready = inputConnected && captureReady
        Text(
            text = when {
                !inputConnected -> "Input off"
                !captureActive -> "Captura off"
                !captureReady -> "Captura…"
                else -> "Listo"
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
                    contentDescription = "Play",
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
                    contentDescription = "Pause",
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
                    contentDescription = "Stop",
                    modifier = Modifier.size(OverlayHudStyle.controlIconSize),
                )
            }
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
            text = "Map",
            color = if (mapEnabled) OverlayHudStyle.accentOrange else OverlayHudStyle.textSecondary,
            fontWeight = FontWeight.SemiBold,
            fontSize = OverlayHudStyle.metaFontSize,
            modifier = Modifier
                .clip(RoundedCornerShape(3.dp))
                .clickable(enabled = mapEnabled, onClick = onMap)
                .padding(horizontal = 4.dp, vertical = 1.dp),
        )
        Text(
            text = "Cast",
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
                text = "Reset",
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

private fun stateShortLabel(state: BotRuntimeState, autoRestartPending: Boolean): String =
    when {
        autoRestartPending &&
            (state == BotRuntimeState.ERROR || state == BotRuntimeState.PAUSED) -> "R"
        state == BotRuntimeState.IDLE -> "ID"
        state == BotRuntimeState.RUNNING -> "ON"
        state == BotRuntimeState.PAUSED -> "||"
        state == BotRuntimeState.ERROR -> "!"
        else -> "?"
    }

private fun bubbleTextColor(state: BotRuntimeState) = when (state) {
    BotRuntimeState.RUNNING -> OverlayHudStyle.accentGreen
    BotRuntimeState.ERROR -> OverlayHudStyle.accentRed
    BotRuntimeState.PAUSED -> OverlayHudStyle.accentOrange
    else -> OverlayHudStyle.textPrimary
}

private fun stateColor(state: BotRuntimeState) = when (state) {
    BotRuntimeState.RUNNING -> OverlayHudStyle.accentGreen
    BotRuntimeState.PAUSED -> OverlayHudStyle.accentOrange
    BotRuntimeState.ERROR -> OverlayHudStyle.accentRed
    else -> OverlayHudStyle.textSecondary
}
