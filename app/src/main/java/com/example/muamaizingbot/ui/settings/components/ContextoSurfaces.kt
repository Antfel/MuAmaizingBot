package com.example.muamaizingbot.ui.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.muamaizingbot.ui.theme.ContextoColors

@Composable
fun ContextoSurfaceCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ContextoColors.Surface, RoundedCornerShape(10.dp))
            .border(1.dp, ContextoColors.Border, RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = { content() },
    )
}

@Composable
fun ContextoChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Text(
        text = label,
        color = when {
            !enabled -> ContextoColors.TextMuted
            selected -> ContextoColors.Bg
            else -> ContextoColors.TextSecondary
        },
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = modifier
            .background(
                when {
                    !enabled -> ContextoColors.Canvas
                    selected -> ContextoColors.Teal
                    else -> ContextoColors.SurfaceElevated
                },
                RoundedCornerShape(8.dp),
            )
            .border(
                1.dp,
                if (selected) ContextoColors.Teal else ContextoColors.Border,
                RoundedCornerShape(8.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
fun ContextoPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Text(
        text = text,
        color = if (enabled) ContextoColors.Bg else ContextoColors.TextMuted,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        modifier = modifier
            .background(
                if (enabled) ContextoColors.Teal else ContextoColors.Border,
                RoundedCornerShape(8.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
fun ContextoSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Text(
        text = text,
        color = if (enabled) ContextoColors.TextPrimary else ContextoColors.TextMuted,
        fontSize = 13.sp,
        modifier = modifier
            .border(1.dp, ContextoColors.Border, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
fun ContextoSectionTitle(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            color = ContextoColors.TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                color = ContextoColors.TextMuted,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
fun ContextoLabeledRow(
    label: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            color = ContextoColors.TextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}
