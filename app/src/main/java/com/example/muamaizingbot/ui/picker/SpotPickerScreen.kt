package com.example.muamaizingbot.ui.picker

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import com.example.muamaizingbot.R
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.muamaizingbot.maps.CoordinateMapping
import com.example.muamaizingbot.maps.MapDefinitionRepository
import com.example.muamaizingbot.profile.LocationRepository
import com.example.muamaizingbot.profile.ProfileRepository
import com.example.muamaizingbot.ui.settings.components.ContextoDenseField
import com.example.muamaizingbot.ui.settings.components.ContextoDenseSearchSelect
import com.example.muamaizingbot.ui.settings.components.ContextoDenseSelect
import com.example.muamaizingbot.ui.theme.ContextoColors
import com.example.muamaizingbot.vision.coord.RefCoords
import com.example.muamaizingbot.vision.template.TemplateAssets
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.text.input.KeyboardType

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 6f
private val MAP_VIEWPORT_HEIGHT = 360.dp

enum class LocationPickerType {
    FARM_SPOT,
    ELF_BUFF,
}

@Composable
fun SpotPickerScreen(
    profileStem: String,
    locationType: LocationPickerType,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    com.example.muamaizingbot.ui.theme.ContextoTheme {
        SpotPickerScreenContent(
            profileStem = profileStem,
            locationType = locationType,
            onBack = onBack,
            modifier = modifier,
        )
    }
}

@Composable
private fun SpotPickerScreenContent(
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
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = ContextoColors.TextPrimary,
                )
            }
            Text(
                text = when (locationType) {
                    LocationPickerType.FARM_SPOT -> stringResource(R.string.spot_title_farm)
                    LocationPickerType.ELF_BUFF -> stringResource(R.string.spot_title_elf)
                },
                color = ContextoColors.TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.weight(1f),
            )
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
                    val trimmedName = when (locationType) {
                        LocationPickerType.FARM_SPOT -> defaultFarmName
                        LocationPickerType.ELF_BUFF -> defaultElfName
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
                            ProfileRepository.updateProfileMapWire(
                                currentProfile,
                                selectedMapId,
                                selectedWire,
                            )
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
                        }
                    }
                    statusMessage = msgSaved
                },
                enabled = profile != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ContextoColors.Teal,
                    contentColor = ContextoColors.Bg,
                ),
                modifier = Modifier.height(36.dp),
            ) {
                Icon(
                    Icons.Outlined.Save,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = stringResource(R.string.action_save),
                    modifier = Modifier.padding(start = 4.dp),
                    fontSize = 13.sp,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            var mapMenuOpen by remember { mutableStateOf(false) }
            var mapQuery by remember(selectedMapId) {
                mutableStateOf(maps.firstOrNull { it.id == selectedMapId }?.name.orEmpty())
            }
            val mapSuggestions = remember(mapQuery, maps) {
                val q = mapQuery.trim()
                maps.filter { map ->
                    q.isEmpty() ||
                        map.name.contains(q, ignoreCase = true) ||
                        map.id.contains(q, ignoreCase = true)
                }.take(24).map { it.id to it.name }
            }
            ContextoDenseSearchSelect(
                label = stringResource(R.string.spot_map),
                query = mapQuery,
                onQueryChange = { mapQuery = it },
                suggestions = mapSuggestions,
                onSelect = { mapId ->
                    selectedMapId = mapId
                    mapQuery = maps.firstOrNull { it.id == mapId }?.name.orEmpty()
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
                placeholder = stringResource(R.string.profile_bosses_search_hint),
                expanded = mapMenuOpen,
                onExpandedChange = { mapMenuOpen = it },
                modifier = Modifier.weight(2f),
            )
            ContextoDenseSelect(
                label = stringResource(R.string.spot_wire),
                value = if (wires.isEmpty()) {
                    ""
                } else {
                    stringResource(R.string.spot_wire_n, selectedWire)
                },
                options = wires.map { it.toString() to stringResource(R.string.spot_wire_n, it) },
                onSelect = { id -> id.toIntOrNull()?.let { selectedWire = it } },
                enabled = profile != null && wires.isNotEmpty(),
                modifier = Modifier.weight(1f),
            )
            ContextoDenseField(
                value = coordXText,
                onValueChange = { applyGameCoordTexts(it, coordYText) },
                label = stringResource(R.string.spot_coord_x),
                enabled = hasMapping,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(0.7f),
            )
            ContextoDenseField(
                value = coordYText,
                onValueChange = { applyGameCoordTexts(coordXText, it) },
                label = stringResource(R.string.spot_coord_y),
                enabled = hasMapping,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(0.7f),
            )
        }
        if (maps.isEmpty()) {
            Text(
                text = stringResource(R.string.spot_no_configured_maps),
                color = ContextoColors.Danger,
                fontSize = 12.sp,
            )
        }

        if (!hasMapping && mapDef?.hasMaintenanceImage() == true) {
            Text(
                text = stringResource(R.string.spot_uncalibrated),
                color = ContextoColors.Danger,
                fontSize = 12.sp,
            )
        }

        if (mapDef?.hasMaintenanceImage() == true) {
            Box(modifier = Modifier.fillMaxWidth()) {
                ZoomableMapPicker(
                    canonicalAssetPath = mapDef.maintenance!!.mapUiImageAssetPath,
                    refImageWidth = RefCoords.REF_WIDTH,
                    refImageHeight = RefCoords.REF_HEIGHT,
                    selectedX = selectedX,
                    selectedY = selectedY,
                    onSelect = { refX, refY -> applyPixelSelection(refX, refY) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.spot_tap_to_mark),
                    color = ContextoColors.TextPrimary,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(ContextoColors.Bg.copy(alpha = 0.75f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        } else {
            Text(
                text = stringResource(R.string.spot_no_image),
                color = ContextoColors.Danger,
            )
        }

        if (coordX != null && coordY != null) {
            Text(
                text = stringResource(R.string.spot_coord_badge, coordX!!, coordY!!),
                color = ContextoColors.Teal,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .background(ContextoColors.Surface, RoundedCornerShape(8.dp))
                    .border(
                        1.dp,
                        ContextoColors.Border,
                        RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        } else if (selectedX >= 0 && selectedY >= 0) {
            Text(
                text = "px ($selectedX, $selectedY)",
                color = ContextoColors.TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }

        if (statusMessage.isNotEmpty()) {
            Text(
                text = statusMessage,
                color = ContextoColors.Teal,
                fontSize = 12.sp,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ContextoColors.Surface, RoundedCornerShape(10.dp))
                .border(1.dp, ContextoColors.Border, RoundedCornerShape(10.dp))
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "ⓘ",
                color = ContextoColors.Teal,
                modifier = Modifier.padding(end = 8.dp),
            )
            Text(
                text = stringResource(R.string.spot_elf_same_flow),
                color = ContextoColors.TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f),
            )
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
    val zoomStep = 0.5f

    fun applyZoom(delta: Float) {
        val next = (zoom + delta).coerceIn(MIN_ZOOM, MAX_ZOOM)
        zoom = next
        if (next <= MIN_ZOOM) {
            panX = 0f
            panY = 0f
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(MAP_VIEWPORT_HEIGHT)
            .clip(RoundedCornerShape(8.dp))
            .background(ContextoColors.SurfaceElevated),
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
                            color = Color(0xFF2BB8A9),
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

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .background(ContextoColors.Bg.copy(alpha = 0.82f), RoundedCornerShape(8.dp))
                .border(1.dp, ContextoColors.Border, RoundedCornerShape(8.dp)),
        ) {
            MapZoomButton(
                label = "+",
                enabled = zoom < MAX_ZOOM,
                onClick = { applyZoom(zoomStep) },
            )
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(1.dp)
                    .background(ContextoColors.Border),
            )
            MapZoomButton(
                label = "−",
                enabled = zoom > MIN_ZOOM,
                onClick = { applyZoom(-zoomStep) },
            )
        }
    }
}

@Composable
private fun MapZoomButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        color = if (enabled) ContextoColors.Teal else ContextoColors.TextMuted,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        modifier = Modifier
            .width(36.dp)
            .height(36.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(top = 6.dp),
        textAlign = TextAlign.Center,
    )
}
