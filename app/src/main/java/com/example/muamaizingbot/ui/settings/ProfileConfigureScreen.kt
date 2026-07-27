package com.example.muamaizingbot.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.muamaizingbot.maps.MapDefinition
import com.example.muamaizingbot.maps.MapDefinitionRepository
import com.example.muamaizingbot.profile.BotMode
import com.example.muamaizingbot.profile.BotProfile
import com.example.muamaizingbot.profile.FarmLocation
import com.example.muamaizingbot.profile.KillBossesConfig
import com.example.muamaizingbot.profile.LocationRepository
import com.example.muamaizingbot.profile.ProfileRepository
import com.example.muamaizingbot.profile.isElfBuffPostMode
import com.example.muamaizingbot.profile.isElfBuffWarMode
import com.example.muamaizingbot.profile.isFarmBossesMode
import com.example.muamaizingbot.profile.normalizedBotMode

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileConfigureScreen(
    profileStem: String,
    onOpenFarmSpot: () -> Unit,
    onOpenElfBuff: () -> Unit,
    onOpenPotionConfig: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val profileFilename = "$profileStem.json"
    val profiles by ProfileRepository.profiles.collectAsState()
    val profile = profiles.firstOrNull { it.filename == profileFilename }
        ?: ProfileRepository.getProfile(profileFilename)
    val farmSpot = LocationRepository.getFarmSpot(profileFilename)
    val elfBuff = LocationRepository.getElfBuff(profileFilename)

    var showDeleteDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Configurar perfil",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = profile?.displayName ?: profileStem,
            style = MaterialTheme.typography.titleMedium,
        )

        Text(
            text = "Auto ataque y revive siempre activos para todos los perfiles.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionHeader("Generales del PJ")

        ConfigOptionCard(
            title = "Config Pociones",
            summary = buildString {
                append(if (profile?.enablePotionRecovery == true) "Activo" else "Desactivado")
                profile?.let {
                    append(" | HP ${it.hpPotionStacks} | MP ${it.mpPotionStacks}")
                }
            },
            onClick = onOpenPotionConfig,
        )

        RandomTeleportConfigCard(
            profile = profile,
            profileFilename = profileFilename,
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Modo del bot",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Farm farmea y puede buscar buff. Elf Buff da buff. Farm Bosses cicla spots de boss.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = profile?.normalizedBotMode() == BotMode.FARM,
                        onClick = {
                            ProfileRepository.setBotMode(profileFilename, BotMode.FARM)
                        },
                        enabled = profile != null,
                        label = { Text("Farm") },
                    )
                    FilterChip(
                        selected = profile?.isElfBuffPostMode() == true,
                        onClick = {
                            if (profile?.isElfBuffPostMode() != true) {
                                ProfileRepository.setBotMode(
                                    profileFilename,
                                    BotMode.ELF_BUFF_GIVER,
                                )
                            }
                        },
                        enabled = profile != null,
                        label = { Text("Elf Buff") },
                    )
                    FilterChip(
                        selected = profile?.isFarmBossesMode() == true,
                        onClick = {
                            ProfileRepository.setBotMode(profileFilename, BotMode.FARM_BOSSES)
                        },
                        enabled = profile != null,
                        label = { Text("Bosses") },
                    )
                }
            }
        }

        SectionHeader("Este modo")

        when {
            profile?.isFarmBossesMode() == true -> {
                FarmBossesConfigCard(
                    profile = profile,
                    profileFilename = profileFilename,
                )
                ElfBuffSeekConfigCard(
                    profile = profile,
                    profileFilename = profileFilename,
                    elfBuff = elfBuff,
                    onOpenElfBuff = onOpenElfBuff,
                )
            }
            profile?.isElfBuffPostMode() == true -> {
                ConfigOptionCard(
                    title = when {
                        profile.isElfBuffWarMode() -> "War event (post al Start)"
                        else -> "Buff post (Farm Spot)"
                    },
                    summary = farmSpot?.summaryLabel(
                        MapDefinitionRepository.getById(farmSpot.map)?.name
                    ) ?: "Sin configurar",
                    onClick = onOpenFarmSpot,
                )
                ElfBuffParamsCard(
                    profile = profile,
                    profileFilename = profileFilename,
                )
            }
            else -> {
                ConfigOptionCard(
                    title = "Farm Spot",
                    summary = farmSpot?.summaryLabel(
                        MapDefinitionRepository.getById(farmSpot.map)?.name
                    ) ?: "Sin configurar",
                    onClick = onOpenFarmSpot,
                )
                ElfBuffSeekConfigCard(
                    profile = profile,
                    profileFilename = profileFilename,
                    elfBuff = elfBuff,
                    onOpenElfBuff = onOpenElfBuff,
                )
            }
        }

        OutlinedButton(
            onClick = { showDeleteDialog = true },
            modifier = Modifier.fillMaxWidth(),
            enabled = profile != null && profiles.size > 1,
        ) {
            Text("Borrar perfil")
        }

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Volver")
        }
    }

    if (showDeleteDialog && profile != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar perfil") },
            text = { Text("¿Eliminar \"${profile.displayName}\" y sus ubicaciones guardadas?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        ProfileRepository.deleteProfile(profile.filename)
                        LocationRepository.refreshForCurrentProfile()
                        showDeleteDialog = false
                        onBack()
                    },
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        HorizontalDivider()
    }
}

@Composable
private fun RandomTeleportConfigCard(
    profile: BotProfile?,
    profileFilename: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = "Random Teleport Seal",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Si el path verde es largo, usa Random en el mapa hasta acercarse. " +
                        "Con seal usado, la espera de llegada es 30s (sin seal, 90s).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = profile?.enableRandomTeleport != false,
                onCheckedChange = { enabled ->
                    ProfileRepository.setRandomTeleportEnabled(profileFilename, enabled)
                },
                enabled = profile != null,
            )
        }
    }
}

@Composable
private fun ElfBuffSeekConfigCard(
    profile: BotProfile?,
    profileFilename: String,
    elfBuff: FarmLocation?,
    onOpenElfBuff: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = "Elf buff automático",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Zona compartida entre Farm y Farm Bosses. " +
                            "Desactívalo si la elf está offline o no quieres ir por buff.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = profile?.enableElfBuff == true,
                    onCheckedChange = { enabled ->
                        ProfileRepository.setElfBuffEnabled(profileFilename, enabled)
                    },
                    enabled = profile != null,
                )
            }

            Text(
                text = when {
                    profile?.enableElfBuff != true -> "Desactivado — el bot no buscará elf buff"
                    elfBuff != null -> elfBuff.summaryLabel(
                        MapDefinitionRepository.getById(elfBuff.map)?.name
                    )
                    else -> "Activo, pero sin zona configurada"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedButton(
                onClick = onOpenElfBuff,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (elfBuff != null) "Editar zona elf buff" else "Configurar zona elf buff",
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun FarmBossesConfigCard(
    profile: BotProfile?,
    profileFilename: String,
) {
    val config = profile?.killBossesConfig ?: KillBossesConfig()
    val pickerMaps = remember { MapDefinitionRepository.listForPicker() }
    var selectedMaps by remember(profile?.filename, config.maps) {
        mutableStateOf(config.maps)
    }
    var holdText by remember(profile?.filename, config.holdSec) {
        mutableStateOf(config.holdSec.toString())
    }
    var golden by remember(profile?.filename, config.includeGoldenMobs) {
        mutableStateOf(config.includeGoldenMobs)
    }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(config.maps, config.holdSec, config.includeGoldenMobs) {
        selectedMaps = config.maps
        holdText = config.holdSec.toString()
        golden = config.includeGoldenMobs
    }

    fun persist(
        maps: List<String> = selectedMaps,
        holdOverride: Int? = null,
        goldenOverride: Boolean? = null,
    ) {
        if (profile == null) return
        val hold = holdOverride
            ?: holdText.toIntOrNull()
                ?.coerceIn(KillBossesConfig.MIN_HOLD_SEC, KillBossesConfig.MAX_HOLD_SEC)
            ?: KillBossesConfig.DEFAULT_HOLD_SEC
        ProfileRepository.setKillBossesConfig(
            profileFilename,
            KillBossesConfig(
                includeGoldenMobs = goldenOverride ?: golden,
                holdSec = hold,
                maps = maps,
            ),
        )
    }

    fun addMap(map: MapDefinition) {
        if (map.id in selectedMaps) {
            searchQuery = ""
            return
        }
        val next = selectedMaps + map.id
        selectedMaps = next
        searchQuery = ""
        persist(maps = next)
    }

    fun removeMap(mapId: String) {
        val next = selectedMaps.filterNot { it == mapId }
        selectedMaps = next
        persist(maps = next)
    }

    val suggestions = remember(searchQuery, selectedMaps, pickerMaps) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            emptyList()
        } else {
            pickerMaps
                .filter { map ->
                    map.id !in selectedMaps &&
                        (map.name.contains(q, ignoreCase = true) ||
                            map.id.contains(q, ignoreCase = true))
                }
                .take(8)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Farm Bosses",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Agregá mapas en el orden del ciclo. El bot teleporta → wires → " +
                    "busca bosses vivos → Focus+Auto. Tras cada kill corre buff/pociones " +
                    "y vuelve al checkpoint.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (selectedMaps.isEmpty()) {
                Text(
                    text = "Sin mapas — buscá y agregá al menos uno",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    selectedMaps.forEachIndexed { index, mapId ->
                        val name = MapDefinitionRepository.getById(mapId)?.name ?: mapId
                        InputChip(
                            selected = false,
                            onClick = { },
                            enabled = profile != null,
                            label = { Text("${index + 1}. $name") },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Quitar $name",
                                    modifier = Modifier.clickable(enabled = profile != null) {
                                        removeMap(mapId)
                                    },
                                )
                            },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Buscar mapa") },
                placeholder = { Text("Ej. Kalima, Atlans…") },
                singleLine = true,
                enabled = profile != null,
            )

            if (suggestions.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    suggestions.forEach { map ->
                        Text(
                            text = map.name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = profile != null) { addMap(map) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                        )
                    }
                }
            } else if (searchQuery.isNotBlank()) {
                Text(
                    text = "Sin coincidencias",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                value = holdText,
                onValueChange = { holdText = it.filter { ch -> ch.isDigit() }.take(3) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Timeout pelea (seg)") },
                supportingText = {
                    Text(
                        "Si el focus no se pierde, rota tras este tiempo. " +
                            "Default ${KillBossesConfig.DEFAULT_HOLD_SEC}s.",
                    )
                },
                singleLine = true,
                enabled = profile != null,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = "Incluir golden mobs",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "También busca iconos golden en el mapa.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = golden,
                    onCheckedChange = { enabled ->
                        golden = enabled
                        persist(goldenOverride = enabled)
                    },
                    enabled = profile != null,
                )
            }

            OutlinedButton(
                onClick = {
                    val hold = holdText.toIntOrNull()
                        ?.coerceIn(KillBossesConfig.MIN_HOLD_SEC, KillBossesConfig.MAX_HOLD_SEC)
                        ?: KillBossesConfig.DEFAULT_HOLD_SEC
                    holdText = hold.toString()
                    persist(holdOverride = hold)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = profile != null,
            ) {
                Text("Guardar timeout")
            }
        }
    }
}

@Composable
private fun ElfBuffParamsCard(
    profile: BotProfile?,
    profileFilename: String,
) {
    val war = profile?.isElfBuffWarMode() == true

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Parámetros Elf Buff",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Elegí la variante: mundo abierto (PK All/Union) o War/APEX en Divine.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = !war,
                    onClick = {
                        ProfileRepository.setBotMode(profileFilename, BotMode.ELF_BUFF_GIVER)
                    },
                    enabled = profile != null,
                    label = { Text("Mundo abierto") },
                )
                FilterChip(
                    selected = war,
                    onClick = {
                        ProfileRepository.setBotMode(profileFilename, BotMode.ELF_BUFF_WAR)
                    },
                    enabled = profile != null,
                    label = { Text("War (APEX)") },
                )
            }

            if (war) {
                Text(
                    text = "Activá el bot ya dentro del evento War. Al Start solo guarda " +
                        "tus coords HUD (y píxel de minimapa). Tras morir revive y vuelve " +
                        "a ese punto — sin teleport ni check de mapa. No cambia PK ni Auto.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                ElfGiverCastFields(
                    profile = profile,
                    profileFilename = profileFilename,
                )
            }
        }
    }
}

@Composable
private fun ElfGiverCastFields(
    profile: BotProfile?,
    profileFilename: String,
) {
    var intervalText by remember(profile?.filename, profile?.elfBuffCastIntervalSec) {
        mutableStateOf(
            (profile?.elfBuffCastIntervalSec ?: BotProfile.DEFAULT_ELF_CAST_INTERVAL_SEC).toString(),
        )
    }
    var autoCast by remember(profile?.filename, profile?.elfBuffAutoCast) {
        mutableStateOf(profile?.elfBuffAutoCast ?: true)
    }
    var saveHint by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(profile?.elfBuffCastIntervalSec, profile?.elfBuffAutoCast) {
        intervalText = (profile?.elfBuffCastIntervalSec ?: BotProfile.DEFAULT_ELF_CAST_INTERVAL_SEC).toString()
        autoCast = profile?.elfBuffAutoCast ?: true
    }

    Text(
        text = "Al Start mapea Greater Defense / Greater Damage. " +
            "Ciclo UI: All → Focus → Union → buff aliado (verde) → Focus Boss → All.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    OutlinedTextField(
        value = intervalText,
        onValueChange = { intervalText = it.filter { ch -> ch.isDigit() }.take(3) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Pausa entre ciclos (seg)") },
        supportingText = {
            Text("Tras terminar un ciclo, espera esto y vuelve a buscar. Default 1s.")
        },
        singleLine = true,
        enabled = profile != null,
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Auto-cast por timer",
            style = MaterialTheme.typography.bodyMedium,
        )
        Switch(
            checked = autoCast,
            onCheckedChange = { autoCast = it },
            enabled = profile != null,
        )
    }

    OutlinedButton(
        onClick = {
            val interval = intervalText.toIntOrNull()
                ?: BotProfile.DEFAULT_ELF_CAST_INTERVAL_SEC
            ProfileRepository.setElfGiverCastConfig(
                profileFilename = profileFilename,
                skillRefX = null,
                skillRefY = null,
                intervalSec = interval,
                autoCast = autoCast,
            )
            saveHint = "Guardado"
        },
        modifier = Modifier.fillMaxWidth(),
        enabled = profile != null,
    ) {
        Text("Guardar casteo")
    }

    saveHint?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ConfigOptionCard(
    title: String,
    summary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
