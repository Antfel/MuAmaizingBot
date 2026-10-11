package com.example.muamaizingbot.ui.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.muamaizingbot.R
import com.example.muamaizingbot.ui.theme.ContextoColors

enum class ProfileEditorNav {
    ESTILO,
    LUGARES,
    MANTENIMIENTO,
    AVANZADO,
}

private val SidebarShape = RoundedCornerShape(8.dp)
private val RowHeight = 38.dp

@Composable
fun ProfileEditorSidebar(
    profileName: String,
    selected: ProfileEditorNav,
    onSelect: (ProfileEditorNav) -> Unit,
    onRearm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = listOf(
        Triple(ProfileEditorNav.ESTILO, R.string.profile_nav_estilo, Icons.Outlined.Tune),
        Triple(ProfileEditorNav.LUGARES, R.string.profile_nav_lugares, Icons.Outlined.Place),
        Triple(ProfileEditorNav.MANTENIMIENTO, R.string.profile_nav_mantenimiento, Icons.Outlined.Build),
        Triple(ProfileEditorNav.AVANZADO, R.string.profile_nav_avanzado, Icons.Outlined.Settings),
    )
    Column(
        modifier = modifier
            .width(240.dp)
            .fillMaxHeight()
            .background(ContextoColors.Canvas)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.profile_editor_sidebar_label),
            color = ContextoColors.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = profileName,
            color = ContextoColors.TextSecondary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        items.forEach { (nav, labelRes, icon) ->
            val active = selected == nav
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(RowHeight)
                    .then(
                        if (active) {
                            Modifier
                                .border(1.5.dp, ContextoColors.Teal, SidebarShape)
                                .background(ContextoColors.TealDim, SidebarShape)
                        } else {
                            Modifier
                        },
                    )
                    .clickable { onSelect(nav) }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (active) ContextoColors.Teal else ContextoColors.TextSecondary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(labelRes),
                    color = if (active) ContextoColors.Teal else ContextoColors.TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
        Box(modifier = Modifier.weight(1f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .border(1.dp, ContextoColors.Amber, SidebarShape)
                .clickable(onClick = onRearm)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoFixHigh,
                contentDescription = null,
                tint = ContextoColors.Amber,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(R.string.profile_rearm_assistant),
                color = ContextoColors.Amber,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
            )
        }
    }
}

@Composable
fun InlineStepper(
    value: Int,
    range: IntRange,
    step: Int = 1,
    onChange: (Int) -> Unit,
    suffix: String = "",
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        StepperButton("−") {
            onChange((value - step).coerceIn(range.first, range.last))
        }
        Text(
            text = if (suffix.isEmpty()) value.toString() else "$value $suffix",
            color = ContextoColors.TextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        StepperButton("+") {
            onChange((value + step).coerceIn(range.first, range.last))
        }
    }
}

@Composable
private fun StepperButton(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        color = ContextoColors.Teal,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .size(26.dp)
            .background(ContextoColors.SurfaceElevated, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
    )
}

@Composable
fun DenseSettingsRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = RowHeight)
            .background(ContextoColors.Surface, RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ContextoColors.Teal,
                modifier = Modifier.size(16.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = ContextoColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = ContextoColors.TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun StyleIntentCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .height(64.dp)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) ContextoColors.Teal else ContextoColors.Border,
                shape = RoundedCornerShape(8.dp),
            )
            .background(
                if (selected) ContextoColors.TealDim else ContextoColors.Surface,
                RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            color = if (selected) ContextoColors.Teal else ContextoColors.TextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            maxLines = 1,
        )
        Text(
            text = subtitle,
            color = ContextoColors.TextMuted,
            fontSize = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun LugaresNavRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(ContextoColors.Surface, RoundedCornerShape(8.dp))
            .border(1.dp, ContextoColors.Border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = ContextoColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
            Text(
                text = subtitle,
                color = ContextoColors.TextMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(text = "›", color = ContextoColors.TextSecondary, fontSize = 20.sp)
    }
}

@Composable
fun ContextoSegmentedTab(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(ContextoColors.SurfaceElevated, RoundedCornerShape(8.dp))
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Text(
                text = label,
                color = if (selected) ContextoColors.Bg else ContextoColors.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (selected) ContextoColors.Teal else ContextoColors.SurfaceElevated,
                        RoundedCornerShape(6.dp),
                    )
                    .clickable { onSelect(index) }
                    .padding(vertical = 7.dp),
            )
        }
    }
}
