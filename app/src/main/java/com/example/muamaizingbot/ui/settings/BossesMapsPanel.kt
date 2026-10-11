package com.example.muamaizingbot.ui.settings

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.muamaizingbot.R
import com.example.muamaizingbot.content.ContentAssetResolver
import com.example.muamaizingbot.maps.MapDefinition
import com.example.muamaizingbot.maps.MapDefinitionRepository
import com.example.muamaizingbot.profile.BotProfile
import com.example.muamaizingbot.profile.KillBossesConfig
import com.example.muamaizingbot.profile.ProfileRepository
import com.example.muamaizingbot.ui.settings.components.ContextoDenseField
import com.example.muamaizingbot.ui.theme.ContextoColors
import com.example.muamaizingbot.vision.template.TemplateAssets

/**
 * Two-pane bosses maps editor (mock: Tu ruta | Agregar mapa).
 * Persistencia via [ProfileRepository.setKillBossesConfig] — misma lógica que antes.
 */
@Composable
fun BossesMapsPanel(
    profile: BotProfile?,
    profileFilename: String,
    modifier: Modifier = Modifier,
) {
    val config = profile?.killBossesConfig ?: KillBossesConfig()
    val pickerMaps = remember { MapDefinitionRepository.listForPicker() }
    var selectedMaps by remember(profile?.filename, config.maps) {
        mutableStateOf(config.maps)
    }
    var golden by remember(profile?.filename, config.includeGoldenMobs) {
        mutableStateOf(config.includeGoldenMobs)
    }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(config.maps, config.includeGoldenMobs) {
        selectedMaps = config.maps
        golden = config.includeGoldenMobs
    }

    fun persist(
        maps: List<String> = selectedMaps,
        goldenOverride: Boolean? = null,
    ) {
        if (profile == null) return
        ProfileRepository.setKillBossesConfig(
            profileFilename,
            config.copy(
                includeGoldenMobs = goldenOverride ?: golden,
                maps = maps,
            ),
        )
    }

    fun addMap(map: MapDefinition) {
        if (map.id in selectedMaps) return
        val next = selectedMaps + map.id
        selectedMaps = next
        persist(maps = next)
    }

    fun removeMap(mapId: String) {
        val next = selectedMaps.filterNot { it == mapId }
        selectedMaps = next
        persist(maps = next)
    }

    fun moveMap(from: Int, to: Int) {
        if (from == to) return
        if (from !in selectedMaps.indices || to !in selectedMaps.indices) return
        val next = selectedMaps.toMutableList()
        val item = next.removeAt(from)
        next.add(to, item)
        selectedMaps = next
        persist(maps = next)
    }

    fun clearRoute() {
        selectedMaps = emptyList()
        persist(maps = emptyList())
    }

    val filteredCatalog = remember(searchQuery, pickerMaps, selectedMaps) {
        val q = searchQuery.trim()
        pickerMaps.filter { map ->
            q.isEmpty() ||
                map.name.contains(q, ignoreCase = true) ||
                map.id.contains(q, ignoreCase = true)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.profile_bosses_maps_title),
            color = ContextoColors.TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
        )
        Text(
            text = stringResource(R.string.profile_bosses_reorder_hint),
            color = ContextoColors.TextMuted,
            fontSize = 11.sp,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 220.dp, max = 320.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Tu ruta
            Column(
                modifier = Modifier
                    .weight(0.95f)
                    .fillMaxHeight()
                    .background(ContextoColors.Canvas, RoundedCornerShape(10.dp))
                    .border(1.dp, ContextoColors.Border, RoundedCornerShape(10.dp))
                    .padding(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.profile_bosses_your_route),
                    color = ContextoColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(6.dp))
                if (selectedMaps.isEmpty()) {
                    Text(
                        text = stringResource(R.string.profile_bosses_no_maps),
                        color = ContextoColors.Danger,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        selectedMaps.forEachIndexed { index, mapId ->
                            val map = MapDefinitionRepository.getById(mapId)
                            RouteMapRow(
                                index = index + 1,
                                name = map?.name ?: mapId,
                                assetPath = map?.maintenance?.mapUiImageAssetPath,
                                canMoveUp = index > 0 && profile != null,
                                canMoveDown = index < selectedMaps.lastIndex && profile != null,
                                onMoveUp = { moveMap(index, index - 1) },
                                onMoveDown = { moveMap(index, index + 1) },
                                onRemove = { removeMap(mapId) },
                                enabled = profile != null,
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.profile_bosses_drag_hint),
                    color = ContextoColors.TextMuted,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // Agregar mapa
            Column(
                modifier = Modifier
                    .weight(1.05f)
                    .fillMaxHeight()
                    .background(ContextoColors.Canvas, RoundedCornerShape(10.dp))
                    .border(1.dp, ContextoColors.Border, RoundedCornerShape(10.dp))
                    .padding(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.profile_bosses_add_map),
                    color = ContextoColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
                Spacer(modifier.height(6.dp))
                ContextoDenseField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = stringResource(R.string.profile_bosses_search_hint),
                    enabled = profile != null,
                    trailing = {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = null,
                            tint = ContextoColors.TextMuted,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier.height(6.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    filteredCatalog.chunked(3).forEach { rowMaps ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            rowMaps.forEach { map ->
                                CatalogMapCard(
                                    map = map,
                                    selected = map.id in selectedMaps,
                                    onClick = {
                                        if (map.id in selectedMaps) {
                                            removeMap(map.id)
                                        } else {
                                            addMap(map)
                                        }
                                    },
                                    enabled = profile != null,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            repeat(3 - rowMaps.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    if (filteredCatalog.isEmpty()) {
                        Text(
                            text = stringResource(R.string.profile_bosses_no_match),
                            color = ContextoColors.TextMuted,
                            fontSize = 11.sp,
                        )
                    }
                }
                TextButton(
                    onClick = { clearRoute() },
                    enabled = profile != null && selectedMaps.isNotEmpty(),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = null,
                        tint = ContextoColors.Teal,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = stringResource(R.string.profile_bosses_clear),
                        color = ContextoColors.Teal,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.profile_bosses_autosave),
                color = ContextoColors.TextMuted,
                fontSize = 10.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.profile_bosses_golden),
                    color = ContextoColors.TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Switch(
                    checked = golden,
                    onCheckedChange = { enabled ->
                        golden = enabled
                        persist(goldenOverride = enabled)
                    },
                    enabled = profile != null,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ContextoColors.Bg,
                        checkedTrackColor = ContextoColors.Teal,
                        uncheckedThumbColor = ContextoColors.TextSecondary,
                        uncheckedTrackColor = ContextoColors.Border,
                    ),
                )
            }
        }
    }
}

@Composable
private fun RouteMapRow(
    index: Int,
    name: String,
    assetPath: String?,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ContextoColors.Surface, RoundedCornerShape(8.dp))
            .border(1.dp, ContextoColors.Border, RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = "⋮⋮", color = ContextoColors.TextMuted, fontSize = 12.sp)
        Column {
            IconButton(
                onClick = onMoveUp,
                enabled = canMoveUp,
                modifier = Modifier.size(22.dp),
            ) {
                Icon(
                    Icons.Default.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.profile_bosses_move_up),
                    tint = ContextoColors.TextSecondary,
                    modifier = Modifier.size(14.dp),
                )
            }
            IconButton(
                onClick = onMoveDown,
                enabled = canMoveDown,
                modifier = Modifier.size(22.dp),
            ) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.profile_bosses_move_down),
                    tint = ContextoColors.TextSecondary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(ContextoColors.Teal, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = index.toString(),
                color = ContextoColors.Bg,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = name,
            color = ContextoColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        MapThumb(assetPath = assetPath, modifier = Modifier.size(width = 40.dp, height = 28.dp))
        IconButton(
            onClick = onRemove,
            enabled = enabled,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.profile_bosses_remove_map, name),
                tint = ContextoColors.TextMuted,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun CatalogMapCard(
    map: MapDefinition,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(
                1.dp,
                if (selected) ContextoColors.Teal else ContextoColors.Border,
                RoundedCornerShape(8.dp),
            )
            .background(ContextoColors.Surface)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(ContextoColors.SurfaceElevated),
        ) {
            MapThumb(
                assetPath = map.maintenance?.mapUiImageAssetPath,
                modifier = Modifier.fillMaxSize(),
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(16.dp)
                        .background(ContextoColors.Teal, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = ContextoColors.Bg,
                        modifier = Modifier.size(10.dp),
                    )
                }
            }
        }
        Text(
            text = map.name,
            color = ContextoColors.TextSecondary,
            fontSize = 9.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

@Composable
private fun MapThumb(
    assetPath: String?,
    modifier: Modifier = Modifier,
) {
    val bitmap = remember(assetPath) {
        val path = assetPath?.takeIf { it.isNotBlank() } ?: return@remember null
        runCatching {
            ContentAssetResolver.open(TemplateAssets.normalizeToCanonical(path))?.use { stream ->
                BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(RoundedCornerShape(4.dp)),
        )
    } else {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(4.dp))
                .background(ContextoColors.SurfaceElevated),
        )
    }
}
