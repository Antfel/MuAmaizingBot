package com.example.muamaizingbot.ui.components

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
import com.example.muamaizingbot.maps.MapDefinitionRepository
import com.example.muamaizingbot.ui.settings.components.ContextoDenseSearchSelect

/**
 * Dense Contexto typeahead map selector (replaces Material OutlinedTextField dropdown).
 */
@Composable
fun MapSearchField(
    maps: List<MapDefinition>,
    selectedMapId: String,
    onMapSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.spot_map),
    enabled: Boolean = true,
) {
    val selected = maps.firstOrNull { it.id == selectedMapId }
        ?: selectedMapId.takeIf { it.isNotBlank() }?.let { id ->
            MapDefinitionRepository.getById(id)
        }
    var query by remember(selectedMapId, selected?.name) {
        mutableStateOf(selected?.name.orEmpty())
    }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(selectedMapId, selected?.name) {
        if (!expanded) {
            query = selected?.name.orEmpty()
        }
    }

    val suggestions = remember(query, maps) {
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
        base.take(24).map { it.id to it.name }
    }

    ContextoDenseSearchSelect(
        label = label,
        query = query,
        onQueryChange = { text ->
            query = text
            expanded = true
        },
        suggestions = suggestions,
        onSelect = { id ->
            onMapSelected(id)
            query = maps.firstOrNull { it.id == id }?.name
                ?: MapDefinitionRepository.getById(id)?.name.orEmpty()
            expanded = false
        },
        enabled = enabled,
        placeholder = stringResource(R.string.profile_bosses_search_hint),
        expanded = expanded && enabled,
        onExpandedChange = { open ->
            if (enabled) {
                expanded = open
                if (!open) {
                    query = selected?.name.orEmpty()
                }
            }
        },
        modifier = modifier,
    )
}
