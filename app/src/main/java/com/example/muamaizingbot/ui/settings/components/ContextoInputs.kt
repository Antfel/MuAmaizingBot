package com.example.muamaizingbot.ui.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.muamaizingbot.ui.theme.ContextoColors

private val FieldShape = RoundedCornerShape(8.dp)
private val FieldHeight = 36.dp

@Composable
fun ContextoDenseField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    enabled: Boolean = true,
    singleLine: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                color = ContextoColors.TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(FieldHeight)
                .background(
                    if (enabled) ContextoColors.Surface else ContextoColors.Canvas,
                    FieldShape,
                )
                .border(1.dp, ContextoColors.Border, FieldShape)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(
                        text = placeholder,
                        color = ContextoColors.TextMuted,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = singleLine,
                    textStyle = TextStyle(
                        color = ContextoColors.TextPrimary,
                        fontSize = 13.sp,
                    ),
                    cursorBrush = SolidColor(ContextoColors.Teal),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            trailing?.invoke()
        }
    }
}

@Composable
fun ContextoDenseSelect(
    label: String?,
    value: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String = "",
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                color = ContextoColors.TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(FieldHeight)
                    .background(
                        if (enabled) ContextoColors.Surface else ContextoColors.Canvas,
                        FieldShape,
                    )
                    .border(1.dp, ContextoColors.Border, FieldShape)
                    .clickable(enabled = enabled) { expanded = true }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = value.ifBlank { placeholder },
                    color = if (value.isBlank()) {
                        ContextoColors.TextMuted
                    } else {
                        ContextoColors.TextPrimary
                    },
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "▾",
                    color = ContextoColors.TextSecondary,
                    fontSize = 12.sp,
                )
            }
            DropdownMenu(
                expanded = expanded && enabled,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(ContextoColors.SurfaceElevated),
            ) {
                options.forEach { (id, title) ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = title,
                                color = ContextoColors.TextPrimary,
                                fontSize = 13.sp,
                            )
                        },
                        onClick = {
                            onSelect(id)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun ContextoDenseSearchSelect(
    label: String?,
    query: String,
    onQueryChange: (String) -> Unit,
    suggestions: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String = "",
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                color = ContextoColors.TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(FieldHeight)
                    .background(
                        if (enabled) ContextoColors.Surface else ContextoColors.Canvas,
                        FieldShape,
                    )
                    .border(1.dp, ContextoColors.Border, FieldShape)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty() && placeholder.isNotEmpty()) {
                        Text(
                            text = placeholder,
                            color = ContextoColors.TextMuted,
                            fontSize = 13.sp,
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = {
                            onQueryChange(it)
                            onExpandedChange(true)
                        },
                        enabled = enabled,
                        singleLine = true,
                        textStyle = TextStyle(
                            color = ContextoColors.TextPrimary,
                            fontSize = 13.sp,
                        ),
                        cursorBrush = SolidColor(ContextoColors.Teal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    text = "▾",
                    color = ContextoColors.TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(enabled = enabled) { onExpandedChange(!expanded) },
                )
            }
            DropdownMenu(
                expanded = expanded && enabled && suggestions.isNotEmpty(),
                onDismissRequest = { onExpandedChange(false) },
                modifier = Modifier.background(ContextoColors.SurfaceElevated),
            ) {
                suggestions.forEach { (id, title) ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = title,
                                color = ContextoColors.TextPrimary,
                                fontSize = 13.sp,
                            )
                        },
                        onClick = {
                            onSelect(id)
                            onExpandedChange(false)
                        },
                    )
                }
            }
        }
    }
}
