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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.example.muamaizingbot.R
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.muamaizingbot.maps.CoordinateMapping
import com.example.muamaizingbot.maps.MapDefinitionRepository
import com.example.muamaizingbot.profile.LocationRepository
import com.example.muamaizingbot.profile.ProfileRepository
import com.example.muamaizingbot.ui.components.MapSearchField
import com.example.muamaizingbot.vision.coord.RefCoords
import com.example.muamaizingbot.vision.template.TemplateAssets
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
    val maps = remember { MapDefinitionRepository.listForSpotPicker() }

    var enableElfBuff by remember(profileFilename) {
        mutableStateOf(profile?.enableElfBuff ?: true)
    }
    val defaultFarmName = stringResource(R.string.spot_default_farm)
    val defaultElfName = stringResource(R.string.spot_default_elf)
    val msgUncalibratedSave = stringResource(R.string.spot_uncalibrated_save)
    val msgUncalibratedLocate = stringResource(R.string.spot_uncalibrated_locate)
    val msgAffineFail = stringResource(R.string.spot_affine_fail)
    val msgProfileMissing = stringResource(R.string.potion_profile_missing)
    val msgSelectMap = stringResource(R.string.spot_select_map_required)
    val msgMarkPoint = stringResource(R.string.spot_mark_point)
    val msgSaved = stringResource(R.string.spot_saved)

    var selectedMapId by remember(profileFilename, locationType) {
        mutableStateOf(existingLocation?.map ?: profile?.map ?: maps.firstOrNull()?.id.orEmpty())
    }
    var selectedWire by remember(profileFilename, locationType, selectedMapId) {
        mutableIntStateOf(existingLocation?.wire ?: profile?.wire ?: 1)
    }
    var spotName by remember(profileFilename, locationType) {
        mutableStateOf(
            existingLocation?.name ?: when (locationType) {
                LocationPickerType.FARM_SPOT -> defaultFarmName
                LocationPickerType.ELF_BUFF -> defaultElfName
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
    var coordXText by remember(profileFilename, locationType) {
        mutableStateOf(existingLocation?.coordX?.toString().orEmpty())
    }
    var coordYText by remember(profileFilename, locationType) {
        mutableStateOf(existingLocation?.coordY?.toString().orEmpty())
    }
    var isCross by remember(profileFilename, locationType) {
        mutableStateOf(
            existingLocation?.isCross
                ?: MapDefinitionRepository.getById(
                    existingLocation?.map ?: profile?.map ?: maps.firstOrNull()?.id.orEmpty(),
                )?.isCross
                ?: true,
        )
    }
    var statusMessage by remember { mutableStateOf("") }

    val mapDef = remember(selectedMapId) { MapDefinitionRepository.getById(selectedMapId) }
    val wires = remember(mapDef) { mapDef?.availableWires().orEmpty() }
    val hasMapping = remember(mapDef) { CoordinateMapping.hasMapping(mapDef) }

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
            coordXText = spot.coordX?.toString().orEmpty()
            coordYText = spot.coordY?.toString().orEmpty()
            isCross = spot.isCross
            spotName = spot.name
        }
    }

    fun applyPixelSelection(refX: Int, refY: Int) {
        selectedX = refX
        selectedY = refY
        if (CoordinateMapping.hasMapping(mapDef)) {
            val coords = CoordinateMapping.pixelToMapCoord(mapDef!!, refX, refY)
            coordX = coords?.first
            coordY = coords?.second
            coordXText = coords?.first?.toString().orEmpty()
            coordYText = coords?.second?.toString().orEmpty()
            statusMessage = ""
        } else {
            coordX = null
            coordY = null
            coordXText = ""
            coordYText = ""
            statusMessage = msgUncalibratedSave
        }
    }

    fun applyGameCoordTexts(xText: String, yText: String) {
        coordXText = xText
        coordYText = yText
        val gx = xText.trim().toIntOrNull()
        val gy = yText.trim().toIntOrNull()
        if (gx == null || gy == null) {
            return
        }
        if (!CoordinateMapping.hasMapping(mapDef)) {
            statusMessage = msgUncalibratedLocate
            return
        }
        val pixel = CoordinateMapping.mapCoordToPixel(mapDef!!, gx, gy)
        if (pixel == null) {
            statusMessage = msgAffineFail
            return
        }
        selectedX = pixel.first
        selectedY = pixel.second
        coordX = gx
        coordY = gy
        statusMessage = ""
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
                LocationPickerType.FARM_SPOT -> stringResource(R.string.spot_title_farm)
                LocationPickerType.ELF_BUFF -> stringResource(R.string.spot_title_elf)
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
                    Text(text = stringResource(R.string.profile_elf_auto), fontWeight = FontWeight.Medium)
                    Text(
                        text = stringResource(R.string.spot_elf_zone_hint),
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

        MapSearchField(
            maps = maps,
            selectedMapId = selectedMapId,
            onMapSelected = { mapId ->
                selectedMapId = mapId
                selectedX = -1
                selectedY = -1
                coordX = null
                coordY = null
                coordXText = ""
                coordYText = ""
                isCross = MapDefinitionRepository.getById(mapId)?.isCross ?: true
                statusMessage = ""
            },
            enabled = profile != null,
        )
        if (maps.isEmpty()) {
            Text(
                text = stringResource(R.string.spot_no_configured_maps),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        UnionCrossDropdown(
            isCross = isCross,
            onSelected = { isCross = it },
        )

        WireDropdown(
            wires = wires,
            selectedWire = selectedWire,
            onWireSelected = { selectedWire = it },
        )

        OutlinedTextField(
            value = spotName,
            onValueChange = { spotName = it },
            label = { Text(stringResource(R.string.spot_name)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = coordXText,
                onValueChange = { applyGameCoordTexts(it, coordYText) },
                label = { Text(stringResource(R.string.spot_coord_x)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                enabled = hasMapping,
            )
            OutlinedTextField(
                value = coordYText,
                onValueChange = { applyGameCoordTexts(coordXText, it) },
                label = { Text(stringResource(R.string.spot_coord_y)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                enabled = hasMapping,
            )
        }

        if (!hasMapping && mapDef?.hasMaintenanceImage() == true) {
            Text(
                text = stringResource(R.string.spot_uncalibrated),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (mapDef?.hasMaintenanceImage() == true) {
            ZoomableMapPicker(
                canonicalAssetPath = mapDef.maintenance!!.mapUiImageAssetPath,
                refImageWidth = RefCoords.REF_WIDTH,
                refImageHeight = RefCoords.REF_HEIGHT,
                selectedX = selectedX,
                selectedY = selectedY,
                onSelect = { refX, refY -> applyPixelSelection(refX, refY) },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(
                text = stringResource(R.string.spot_no_image),
                color = MaterialTheme.colorScheme.error,
            )
        }

        val pixelPart = if (selectedX >= 0 && selectedY >= 0) {
            "($selectedX, $selectedY)"
        } else {
            "-"
        }
        val pixelStatus = stringResource(R.string.spot_pixel_label, pixelPart)
        val coordStatus = if (coordX != null && coordY != null) {
            "  |  " + stringResource(R.string.spot_coord_label, coordX!!, coordY!!)
        } else {
            ""
        }
        Text(
            text = pixelStatus + coordStatus,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )

        Text(
            text = stringResource(R.string.spot_zoom_hint),
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
                    statusMessage = msgProfileMissing
                    return@Button
                }
                if (selectedMapId.isBlank()) {
                    statusMessage = msgSelectMap
                    return@Button
                }
                if (selectedX < 0 || selectedY < 0) {
                    statusMessage = msgMarkPoint
                    return@Button
                }
                val trimmedName = spotName.trim().ifBlank {
                    when (locationType) {
                        LocationPickerType.FARM_SPOT -> defaultFarmName
                        LocationPickerType.ELF_BUFF -> defaultElfName
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
                            isCross = isCross,
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
                            isCross = isCross,
                        )
                        ProfileRepository.saveProfile(currentProfile.copy(enableElfBuff = enableElfBuff))
                    }
                }
                statusMessage = msgSaved
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = profile != null,
        ) {
            Text(stringResource(R.string.action_save))
        }

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.action_back))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnionCrossDropdown(
    isCross: Boolean,
    onSelected: (Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = if (isCross) {
        stringResource(R.string.spot_pk_cross)
    } else {
        stringResource(R.string.spot_pk_local)
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.spot_pk_template)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            supportingText = {
                Text(stringResource(R.string.spot_pk_hint))
            },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.spot_pk_cross)) },
                onClick = {
                    onSelected(true)
                    expanded = false
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.spot_pk_local)) },
                onClick = {
                    onSelected(false)
                    expanded = false
                },
            )
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
            value = stringResource(R.string.spot_wire_n, selectedWire),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.spot_wire)) },
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
                    text = { Text(stringResource(R.string.spot_wire_n, wire)) },
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
    canonicalAssetPath: String,
    refImageWidth: Int,
    refImageHeight: Int,
    selectedX: Int,
    selectedY: Int,
    onSelect: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val assetPath = TemplateAssets.normalizeToCanonical(canonicalAssetPath)
    val bitmap = remember(assetPath) {
        runCatching {
            com.example.muamaizingbot.content.ContentAssetResolver.open(assetPath)?.use { stream ->
                BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        }.getOrNull()
    }

    val imageWidth = bitmap?.width ?: refImageWidth
    val imageHeight = bitmap?.height ?: refImageHeight
    val displaySelectedX = if (selectedX >= 0) {
        selectedX * imageWidth / refImageWidth
    } else {
        -1
    }
    val displaySelectedY = if (selectedY >= 0) {
        selectedY * imageHeight / refImageHeight
    } else {
        -1
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
                text = stringResource(R.string.spot_zoom),
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
                Text(stringResource(R.string.spot_reset))
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
                                    val localX = relX.roundToInt().coerceIn(0, imageWidth)
                                    val localY = relY.roundToInt().coerceIn(0, imageHeight)
                                    val refX = (localX.toLong() * refImageWidth / imageWidth).toInt()
                                    val refY = (localY.toLong() * refImageHeight / imageHeight).toInt()
                                    onSelect(refX, refY)
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
                        if (displaySelectedX >= 0 && displaySelectedY >= 0) {
                            val markerX = imageLeft + displaySelectedX * totalScale
                            val markerY = imageTop + displaySelectedY * totalScale
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
