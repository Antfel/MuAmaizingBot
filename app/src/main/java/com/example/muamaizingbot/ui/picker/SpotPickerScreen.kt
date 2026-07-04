package com.example.muamaizingbot.ui.picker

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.muamaizingbot.maps.CoordinateMapping
import com.example.muamaizingbot.maps.MapDefinition
import com.example.muamaizingbot.maps.MapDefinitionRepository
import com.example.muamaizingbot.profile.LocationRepository
import com.example.muamaizingbot.profile.ProfileRepository
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 6f
private val MAP_VIEWPORT_HEIGHT = 320.dp

enum class LocationPickerType {
    FARM_SPOT,
    ELF_BUFF,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotPickerScreen(
    profileStem: String,
    locationType: LocationPickerType,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val profileFilename = "$profileStem.json"
    val profile = remember(profileStem) { ProfileRepository.getProfile(profileFilename) }
    val existingLocation = remember(profileStem, locationType) {
        when (locationType) {
            LocationPickerType.FARM_SPOT -> LocationRepository.getFarmSpot(profileFilename)
            LocationPickerType.ELF_BUFF -> LocationRepository.getElfBuff(profileFilename)
        }
    }
    val maps = remember { MapDefinitionRepository.listForPicker() }

    var enableElfBuff by remember(profileFilename) {
        mutableStateOf(profile?.enableElfBuff ?: true)
    }

    var selectedMapId by remember(profileFilename, locationType) {
        mutableStateOf(existingLocation?.map ?: profile?.map ?: maps.firstOrNull()?.id.orEmpty())
    }
    var selectedWire by remember(profileFilename, locationType, selectedMapId) {
        mutableIntStateOf(existingLocation?.wire ?: profile?.wire ?: 1)
    }
    var spotName by remember(profileFilename, locationType) {
        mutableStateOf(
            existingLocation?.name ?: when (locationType) {
                LocationPickerType.FARM_SPOT -> "Farm Spot"
                LocationPickerType.ELF_BUFF -> "Elf Buff"
            }
        )
    }
    var selectedX by remember(profileFilename, locationType) {
        mutableIntStateOf(existingLocation?.x ?: -1)
    }
    var selectedY by remember(profileFilename, locationType) {
        mutableIntStateOf(existingLocation?.y ?: -1)
    }
    var coordX by remember(profileFilename, locationType) {
        mutableStateOf(existingLocation?.coordX)
    }
    var coordY by remember(profileFilename, locationType) {
        mutableStateOf(existingLocation?.coordY)
    }
    var statusMessage by remember { mutableStateOf("") }

    val mapDef = remember(selectedMapId) { MapDefinitionRepository.getById(selectedMapId) }
    val wires = remember(mapDef) { mapDef?.availableWires().orEmpty() }

    LaunchedEffect(mapDef, wires) {
        if (wires.isNotEmpty() && selectedWire !in wires) {
            selectedWire = wires.first()
        }
    }

    LaunchedEffect(existingLocation) {
        existingLocation?.let { spot ->
            selectedMapId = spot.map
            selectedWire = spot.wire
            selectedX = spot.x
            selectedY = spot.y
            coordX = spot.coordX
            coordY = spot.coordY
            spotName = spot.name
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = when (locationType) {
                LocationPickerType.FARM_SPOT -> "Farm Spot"
                LocationPickerType.ELF_BUFF -> "Elf Buff Zone"
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = profile?.displayName ?: profileStem,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (locationType == LocationPickerType.ELF_BUFF) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Elf buff automático", fontWeight = FontWeight.Medium)
                    Text(
                        text = "El bot irá a esta zona si el buff no está activo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                androidx.compose.material3.Switch(
                    checked = enableElfBuff,
                    onCheckedChange = { enableElfBuff = it },
                )
            }
        }

        MapDropdown(
            maps = maps,
            selectedMapId = selectedMapId,
            onMapSelected = { mapId ->
                selectedMapId = mapId
                selectedX = -1
                selectedY = -1
                coordX = null
                coordY = null
            },
        )

        WireDropdown(
            wires = wires,
            selectedWire = selectedWire,
            onWireSelected = { selectedWire = it },
        )

        OutlinedTextField(
            value = spotName,
            onValueChange = { spotName = it },
            label = { Text("Nombre del spot") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        if (mapDef?.hasMaintenanceImage() == true) {
            ZoomableMapPicker(
                assetPath = mapDef.maintenance!!.mapUiImageAssetPath,
                imageWidth = mapDef.maintenance.imageWidth,
                imageHeight = mapDef.maintenance.imageHeight,
                selectedX = selectedX,
                selectedY = selectedY,
                onSelect = { x, y ->
                    selectedX = x
                    selectedY = y
                    if (CoordinateMapping.hasMapping(mapDef)) {
                        val coords = CoordinateMapping.pixelToMapCoord(mapDef, x, y)
                        coordX = coords?.first
                        coordY = coords?.second
                    } else {
                        coordX = null
                        coordY = null
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(
                text = "Este mapa no tiene imagen de mantenimiento.",
                color = MaterialTheme.colorScheme.error,
            )
        }

        Text(
            text = buildString {
                append("Pixel: ")
                if (selectedX >= 0 && selectedY >= 0) {
                    append("($selectedX, $selectedY)")
                } else {
                    append("-")
                }
                if (coordX != null && coordY != null) {
                    append("  |  Coord: ($coordX, $coordY)")
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )

        Text(
            text = "Usa el slider para zoom. Arrastra el mapa para mover. Toca para marcar el spot.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (statusMessage.isNotEmpty()) {
            Text(
                text = statusMessage,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Button(
            onClick = {
                val currentProfile = profile
                if (currentProfile == null) {
                    statusMessage = "Perfil no encontrado"
                    return@Button
                }
                if (selectedMapId.isBlank()) {
                    statusMessage = "Selecciona un mapa"
                    return@Button
                }
                if (selectedX < 0 || selectedY < 0) {
                    statusMessage = "Marca un punto en el mapa"
                    return@Button
                }
                val trimmedName = spotName.trim().ifBlank {
                    when (locationType) {
                        LocationPickerType.FARM_SPOT -> "Farm Spot"
                        LocationPickerType.ELF_BUFF -> "Elf Buff"
                    }
                }
                when (locationType) {
                    LocationPickerType.FARM_SPOT -> {
                        LocationRepository.upsertFarmSpot(
                            profileFilename = currentProfile.filename,
                            mapId = selectedMapId,
                            wire = selectedWire,
                            x = selectedX,
                            y = selectedY,
                            name = trimmedName,
                            coordX = coordX,
                            coordY = coordY,
                        )
                        ProfileRepository.updateProfileMapWire(currentProfile, selectedMapId, selectedWire)
                    }
                    LocationPickerType.ELF_BUFF -> {
                        LocationRepository.upsertElfBuff(
                            profileFilename = currentProfile.filename,
                            mapId = selectedMapId,
                            wire = selectedWire,
                            x = selectedX,
                            y = selectedY,
                            name = trimmedName,
                            coordX = coordX,
                            coordY = coordY,
                        )
                        ProfileRepository.saveProfile(currentProfile.copy(enableElfBuff = enableElfBuff))
                    }
                }
                statusMessage = "Ubicación guardada"
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = profile != null,
        ) {
            Text("Guardar")
        }

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Volver")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapDropdown(
    maps: List<MapDefinition>,
    selectedMapId: String,
    onMapSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = maps.firstOrNull { it.id == selectedMapId }?.name ?: "Seleccionar mapa"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Mapa") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            maps.forEach { map ->
                DropdownMenuItem(
                    text = { Text(map.name) },
                    onClick = {
                        onMapSelected(map.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WireDropdown(
    wires: List<Int>,
    selectedWire: Int,
    onWireSelected: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = "Wire $selectedWire",
            onValueChange = {},
            readOnly = true,
            label = { Text("Wire") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            wires.forEach { wire ->
                DropdownMenuItem(
                    text = { Text("Wire $wire") },
                    onClick = {
                        onWireSelected(wire)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ZoomableMapPicker(
    assetPath: String,
    imageWidth: Int,
    imageHeight: Int,
    selectedX: Int,
    selectedY: Int,
    onSelect: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bitmap = remember(assetPath) {
        runCatching {
            context.assets.open(assetPath).use { stream ->
                BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        }.getOrNull()
    }

    var zoom by remember(assetPath) { mutableFloatStateOf(MIN_ZOOM) }
    var panX by remember(assetPath) { mutableFloatStateOf(0f) }
    var panY by remember(assetPath) { mutableFloatStateOf(0f) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Zoom",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.width(44.dp),
            )
            Slider(
                value = zoom,
                onValueChange = { newZoom ->
                    zoom = newZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
                    if (zoom <= MIN_ZOOM) {
                        panX = 0f
                        panY = 0f
                    }
                },
                valueRange = MIN_ZOOM..MAX_ZOOM,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = String.format(Locale.US, "%.1fx", zoom),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.width(44.dp),
            )
            TextButton(
                onClick = {
                    zoom = MIN_ZOOM
                    panX = 0f
                    panY = 0f
                },
                enabled = zoom > MIN_ZOOM || panX != 0f || panY != 0f,
            ) {
                Text("Reset")
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(MAP_VIEWPORT_HEIGHT)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            val viewportW = constraints.maxWidth.toFloat()
            val viewportH = constraints.maxHeight.toFloat()
            val baseScale = min(viewportW / imageWidth, viewportH / imageHeight)
            val totalScale = baseScale * zoom
            val scaledW = imageWidth * totalScale
            val scaledH = imageHeight * totalScale
            val maxPanX = maxOf(0f, (scaledW - viewportW) / 2f)
            val maxPanY = maxOf(0f, (scaledH - viewportH) / 2f)

            LaunchedEffect(scaledW, scaledH, viewportW, viewportH) {
                panX = panX.coerceIn(-maxPanX, maxPanX)
                panY = panY.coerceIn(-maxPanY, maxPanY)
            }

            val currentPanX by rememberUpdatedState(panX)
            val currentPanY by rememberUpdatedState(panY)
            val currentTotalScale by rememberUpdatedState(totalScale)
            val currentScaledW by rememberUpdatedState(scaledW)
            val currentScaledH by rememberUpdatedState(scaledH)
            val currentMaxPanX by rememberUpdatedState(maxPanX)
            val currentMaxPanY by rememberUpdatedState(maxPanY)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(assetPath, zoom, viewportW, viewportH) {
                        coroutineScope {
                            launch {
                                detectTapGestures { tap ->
                                    val left = (viewportW - currentScaledW) / 2f + currentPanX
                                    val top = (viewportH - currentScaledH) / 2f + currentPanY
                                    if (tap.x !in left..(left + currentScaledW) ||
                                        tap.y !in top..(top + currentScaledH)
                                    ) {
                                        return@detectTapGestures
                                    }
                                    val relX = (tap.x - left) / currentTotalScale
                                    val relY = (tap.y - top) / currentTotalScale
                                    val realX = relX.roundToInt().coerceIn(0, imageWidth)
                                    val realY = relY.roundToInt().coerceIn(0, imageHeight)
                                    onSelect(realX, realY)
                                }
                            }
                            if (zoom > MIN_ZOOM) {
                                launch {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        panX = (panX + dragAmount.x).coerceIn(-currentMaxPanX, currentMaxPanX)
                                        panY = (panY + dragAmount.y).coerceIn(-currentMaxPanY, currentMaxPanY)
                                    }
                                }
                            }
                        }
                    },
            ) {
                if (bitmap != null) {
                    val imageLeft = (viewportW - scaledW) / 2f + panX
                    val imageTop = (viewportH - scaledH) / 2f + panY
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawImage(
                            image = bitmap,
                            dstOffset = IntOffset(imageLeft.roundToInt(), imageTop.roundToInt()),
                            dstSize = IntSize(scaledW.roundToInt(), scaledH.roundToInt()),
                        )
                        if (selectedX >= 0 && selectedY >= 0) {
                            val markerX = imageLeft + selectedX * totalScale
                            val markerY = imageTop + selectedY * totalScale
                            drawCircle(
                                color = Color(0xFF22C55E),
                                radius = 10f,
                                center = Offset(markerX, markerY),
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 10f,
                                center = Offset(markerX, markerY),
                                style = Stroke(width = 2f),
                            )
                        }
                    }
                }
            }
        }
    }
}
