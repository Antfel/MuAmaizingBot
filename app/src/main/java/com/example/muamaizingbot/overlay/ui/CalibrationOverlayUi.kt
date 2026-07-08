package com.example.muamaizingbot.overlay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    scalePercent: Int,
    anchorOffsetXFraction: Float,
    anchorOffsetYFraction: Float,
    onDragBy: (Int, Int) -> Unit,
    onScaleBy: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(frameWidth)
            .height(frameHeight)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    if (pan.x != 0f || pan.y != 0f) {
                        onDragBy(pan.x.roundToInt(), pan.y.roundToInt())
                    }
                    if (kotlin.math.abs(zoom - 1f) > 0.01f) {
                        onScaleBy(zoom)
                    }
                }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(2.dp, Color(0xFF00E5FF), RoundedCornerShape(2.dp))
                .background(Color(0x4400E5FF)),
        )
        val crossX = frameWidth * anchorOffsetXFraction
        val crossY = frameHeight * anchorOffsetYFraction
        Box(
            modifier = Modifier
                .offset(x = crossX - 1.dp, y = crossY - frameHeight * 0.3f)
                .width(2.dp)
                .height(frameHeight * 0.6f)
                .background(Color(0xFFFF5252)),
        )
        Box(
            modifier = Modifier
                .offset(x = crossX - frameWidth * 0.3f, y = crossY - 1.dp)
                .width(frameWidth * 0.6f)
                .height(2.dp)
                .background(Color(0xFFFF5252)),
        )
        Text(
            text = "${scalePercent}%",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .background(Color(0xAA000000), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}

@Composable
fun CalibrationMinimizedBubble(
    stepIndex: Int,
    totalSteps: Int,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(OverlayHudStyle.bubbleSize)
            .clip(CircleShape)
            .background(OverlayHudStyle.bubbleBackground)
            .border(2.dp, Color(0xFF00E5FF), CircleShape)
            .clickable(onClick = onExpand),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "${stepIndex + 1}/$totalSteps",
            color = Color(0xFF00E5FF),
            fontWeight = FontWeight.Bold,
            fontSize = OverlayHudStyle.statusFontSize,
        )
    }
}

@Composable
fun CalibrationInstructionPanel(
    stepIndex: Int,
    stepLabel: String,
    totalSteps: Int,
    frameCenterX: Int,
    frameCenterY: Int,
    anchorX: Int,
    anchorY: Int,
    markerWidth: Int,
    markerHeight: Int,
    scalePercent: Int,
    panelAtBottom: Boolean,
    onMinimize: () -> Unit,
    onScalePercentChange: (Int) -> Unit,
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Calibración ${stepIndex + 1}/$totalSteps",
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Minimizar",
                color = Color(0xFF00E5FF),
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clickable(onClick = onMinimize)
                    .padding(4.dp),
            )
        }

        Text(
            text = "Encaja el rectángulo sobre el template y alinea la cruz roja en: $stepLabel",
            color = Color(0xFFB0BEC5),
        )
        Text(
            text = "Referencia: ($anchorX, $anchorY) · Marco: ${markerWidth}×$markerHeight",
            color = Color(0xFF78909C),
        )
        Text(
            text = "Tamaño del rectángulo: $scalePercent%",
            color = Color(0xFFB0BEC5),
            fontWeight = FontWeight.Medium,
        )
        Slider(
            value = scalePercent.toFloat(),
            onValueChange = { onScalePercentChange(it.roundToInt()) },
            valueRange = MIN_SCALE_PERCENT.toFloat()..MAX_SCALE_PERCENT.toFloat(),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "Arrastra para mover · Pellizca para escalar (proporción fija del template)",
            color = Color(0xFF546E7A),
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

private const val MIN_SCALE_PERCENT = 50
private const val MAX_SCALE_PERCENT = 200
