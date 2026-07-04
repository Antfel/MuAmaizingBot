package com.example.muamaizingbot.overlay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.muamaizingbot.accessibility.BotAccessibilityService
import com.example.muamaizingbot.bot.BotController
import com.example.muamaizingbot.bot.BotRuntimeState
import com.example.muamaizingbot.capture.ScreenCaptureManager
import kotlin.math.roundToInt

@Composable
fun OverlayPanel(
    onDragBy: (Int, Int) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val botState by BotController.state.collectAsState()
    val captureActive by ScreenCaptureManager.isActive.collectAsState()
    val inputConnected = BotAccessibilityService.isConnected

    val dragModifier = Modifier.pointerInput(Unit) {
        detectDragGestures(
            onDragEnd = { onDragEnd() },
            onDragCancel = { onDragEnd() },
        ) { change, dragAmount ->
            change.consume()
            onDragBy(dragAmount.x.roundToInt(), dragAmount.y.roundToInt())
        }
    }

    if (expanded) {
        ExpandedOverlay(
            modifier = modifier.then(dragModifier),
            botState = botState,
            captureActive = captureActive,
            inputConnected = inputConnected,
            onCollapse = { expanded = false },
            onStart = {
                BotController.start()
                expanded = false
            },
            onPause = { BotController.pause() },
        )
    } else {
        BubbleOverlay(
            modifier = modifier.then(dragModifier),
            botState = botState,
            onExpand = { expanded = true },
        )
    }
}

@Composable
private fun BubbleOverlay(
    botState: BotRuntimeState,
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
            text = stateShortLabel(botState),
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
    inputConnected: Boolean,
    onCollapse: () -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(OverlayHudStyle.panelWidth)
            .clip(RoundedCornerShape(OverlayHudStyle.cornerRadius))
            .background(OverlayHudStyle.panelBackground)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
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
                fontSize = OverlayHudStyle.statusFontSize,
            )
            Text(
                text = "−",
                color = OverlayHudStyle.textSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onCollapse)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }

        Text(
            text = botState.label,
            color = stateColor(botState),
            fontSize = OverlayHudStyle.statusFontSize,
            fontWeight = FontWeight.Medium,
        )

        val ready = inputConnected && captureActive
        Text(
            text = when {
                !inputConnected -> "Input: no listo"
                !captureActive -> "Captura: inactiva"
                else -> "Listo"
            },
            color = if (ready) OverlayHudStyle.accentGreen else OverlayHudStyle.textSecondary,
            fontSize = OverlayHudStyle.statusFontSize,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            FilledIconButton(
                onClick = onStart,
                modifier = Modifier.size(OverlayHudStyle.controlButtonSize),
                enabled = botState != BotRuntimeState.RUNNING && ready,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = OverlayHudStyle.accentGreen,
                    disabledContainerColor = OverlayHudStyle.accentGreen.copy(alpha = 0.4f),
                    contentColor = OverlayHudStyle.textPrimary,
                ),
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Start",
                )
            }

            FilledIconButton(
                onClick = onPause,
                modifier = Modifier.size(OverlayHudStyle.controlButtonSize),
                enabled = botState == BotRuntimeState.RUNNING,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = OverlayHudStyle.accentOrange,
                    disabledContainerColor = OverlayHudStyle.accentOrange.copy(alpha = 0.4f),
                    contentColor = OverlayHudStyle.textPrimary,
                ),
            ) {
                Icon(
                    imageVector = Icons.Default.Pause,
                    contentDescription = "Pause",
                )
            }
        }
    }
}

private fun stateShortLabel(state: BotRuntimeState): String = when (state) {
    BotRuntimeState.IDLE -> "ID"
    BotRuntimeState.RUNNING -> "ON"
    BotRuntimeState.PAUSED -> "||"
    BotRuntimeState.ERROR -> "!"
}

private fun bubbleTextColor(state: BotRuntimeState) = when (state) {
    BotRuntimeState.RUNNING -> OverlayHudStyle.accentGreen
    BotRuntimeState.ERROR -> OverlayHudStyle.accentRed
    BotRuntimeState.PAUSED -> OverlayHudStyle.accentOrange
    BotRuntimeState.IDLE -> OverlayHudStyle.textPrimary
}

@Composable
private fun stateColor(state: BotRuntimeState) = when (state) {
    BotRuntimeState.IDLE -> OverlayHudStyle.textSecondary
    BotRuntimeState.RUNNING -> OverlayHudStyle.accentGreen
    BotRuntimeState.PAUSED -> OverlayHudStyle.accentOrange
    BotRuntimeState.ERROR -> OverlayHudStyle.accentRed
}
