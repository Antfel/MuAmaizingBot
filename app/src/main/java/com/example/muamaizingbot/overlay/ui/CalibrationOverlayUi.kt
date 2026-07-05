package com.example.muamaizingbot.overlay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun CalibrationMarker(
    frameWidth: Dp,
    frameHeight: Dp,
    onDragBy: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(frameWidth)
            .height(frameHeight)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDragBy(dragAmount.x.roundToInt(), dragAmount.y.roundToInt())
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(2.dp, Color(0xFF00E5FF), RoundedCornerShape(2.dp))
                .background(Color(0x4400E5FF)),
        )
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(frameHeight * 0.6f),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF00E5FF)),
            )
        }
        Box(
            modifier = Modifier
                .width(frameWidth * 0.6f)
                .height(2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF00E5FF)),
            )
        }
    }
}

@Composable
fun CalibrationInstructionPanel(
    stepIndex: Int,
    stepLabel: String,
    totalSteps: Int,
    markerX: Int,
    markerY: Int,
    panelAtBottom: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = if (panelAtBottom) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    } else {
        RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xEE101820), shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Calibración HUD — paso ${stepIndex + 1}/$totalSteps",
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Encaja el rectángulo sobre: $stepLabel",
            color = Color(0xFFB0BEC5),
        )
        Text(
            text = "Centro: ($markerX, $markerY)",
            color = Color(0xFF78909C),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
            ) {
                Text("Cancelar")
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (stepIndex + 1 >= totalSteps) "Finalizar" else "Confirmar")
            }
        }
    }
}
