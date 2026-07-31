package com.example.muamaizingbot.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.muamaizingbot.R
import com.example.muamaizingbot.maps.MapDefinition

/**
 * Typeahead map selector: type to filter fully-configured maps by name or id.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapSearchField(
    maps: List<MapDefinition>,
    selectedMapId: String,
    onMapSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.spot_map),
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = maps.firstOrNull { it.id == selectedMapId }
        ?: selectedMapId.takeIf { it.isNotBlank() }?.let { id ->
            // Keep label if an older selection is no longer in the filtered list.
            com.example.muamaizingbot.maps.MapDefinitionRepository.getById(id)
        }
    var query by remember(selectedMapId, selected?.name) {
        mutableStateOf(selected?.name.orEmpty())
    }

    LaunchedEffect(selectedMapId, selected?.name) {
        if (!expanded) {
            query = selected?.name.orEmpty()
        }
    }

    val suggestions = remember(query, maps, expanded) {
        val q = query.trim()
        val base = if (q.isEmpty()) {
            maps
        } else {
            maps.filter { map ->
                map.name.contains(q, ignoreCase = true) ||
                    map.id.contains(q, ignoreCase = true) ||
                    map.group.contains(q, ignoreCase = true)
            }
        }
        base.take(24)
    }

    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { open ->
            if (!enabled) return@ExposedDropdownMenuBox
            expanded = open
            if (open && query == selected?.name) {
                // Leave query as-is so user can edit or clear to browse.
            }
        },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { text ->
                query = text
                expanded = true
            },
            enabled = enabled,
            singleLine = true,
            label = { Text(label) },
            placeholder = { Text(stringResource(R.string.profile_bosses_search_hint)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded && enabled) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded && enabled && suggestions.isNotEmpty(),
            onDismissRequest = {
                expanded = false
                query = selected?.name.orEmpty()
            },
        ) {
            suggestions.forEach { map ->
                DropdownMenuItem(
                    text = {
                        Text(
                            if (map.isCross) {
                                "${map.name} · Cross"
                            } else {
                                "${map.name} · Local"
                            },
                        )
                    },
                    onClick = {
                        onMapSelected(map.id)
                        query = map.name
                        expanded = false
                    },
                )
            }
        }
    }
}
