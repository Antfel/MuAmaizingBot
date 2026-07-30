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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.example.muamaizingbot.R
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
            text = stringResource(R.string.profile_config_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = profile?.displayName ?: profileStem,
            style = MaterialTheme.typography.titleMedium,
        )

        Text(
            text = stringResource(R.string.profile_config_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionHeader(stringResource(R.string.profile_section_general))

        val potionStatus = if (profile?.enablePotionRecovery == true) {
            stringResource(R.string.status_active)
        } else {
            stringResource(R.string.status_disabled)
        }
        ConfigOptionCard(
            title = stringResource(R.string.potion_config_title),
            summary = buildString {
                append(potionStatus)
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
                    text = stringResource(R.string.profile_bot_mode),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.profile_bot_mode_hint),
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
                        label = { Text(stringResource(R.string.profile_mode_farm)) },
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
                        label = { Text(stringResource(R.string.profile_mode_elf)) },
                    )
                    FilterChip(
                        selected = profile?.isFarmBossesMode() == true,
                        onClick = {
                            ProfileRepository.setBotMode(profileFilename, BotMode.FARM_BOSSES)
                        },
                        enabled = profile != null,
                        label = { Text(stringResource(R.string.profile_mode_bosses)) },
                    )
                }
            }
        }

        SectionHeader(stringResource(R.string.profile_section_mode_settings))

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
                        profile.isElfBuffWarMode() -> stringResource(R.string.profile_war_post_title)
                        else -> stringResource(R.string.profile_buff_post_title)
                    },
                    summary = farmSpot?.summaryLabel(
                        MapDefinitionRepository.getById(farmSpot.map)?.name
                    ) ?: stringResource(R.string.profiles_unset),
                    onClick = onOpenFarmSpot,
                )
                ElfBuffParamsCard(
                    profile = profile,
                    profileFilename = profileFilename,
                )
            }
            else -> {
                ConfigOptionCard(
                    title = stringResource(R.string.profile_farm_spot),
                    summary = farmSpot?.summaryLabel(
                        MapDefinitionRepository.getById(farmSpot.map)?.name
                    ) ?: stringResource(R.string.profiles_unset),
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
            Text(stringResource(R.string.profile_delete))
        }

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.action_back))
        }
    }

    if (showDeleteDialog && profile != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.profile_delete_title)) },
            text = { Text(stringResource(R.string.profile_delete_confirm, profile.displayName)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        ProfileRepository.deleteProfile(profile.filename)
                        LocationRepository.refreshForCurrentProfile()
                        showDeleteDialog = false
                        onBack()
                    },
                ) {
                    Text(stringResource(R.string.profile_delete_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.profiles_cancel))
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
                    text = stringResource(R.string.profile_random_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.profile_random_hint),
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
                        text = stringResource(R.string.profile_elf_auto),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.profile_elf_auto_hint),
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
                    profile?.enableElfBuff != true -> stringResource(R.string.profile_elf_off)
                    elfBuff != null -> elfBuff.summaryLabel(
                        MapDefinitionRepository.getById(elfBuff.map)?.name
                    )
                    else -> stringResource(R.string.profile_elf_on_no_zone)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedButton(
                onClick = onOpenElfBuff,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (elfBuff != null) stringResource(R.string.profile_elf_edit_zone) else stringResource(R.string.profile_elf_set_zone),
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
                text = stringResource(R.string.mode_farm_bosses),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.profile_bosses_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (selectedMaps.isEmpty()) {
                Text(
                    text = stringResource(R.string.profile_bosses_no_maps),
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
                                    contentDescription = stringResource(R.string.profile_bosses_remove_map, name),
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
                label = { Text(stringResource(R.string.profile_bosses_search)) },
                placeholder = { Text(stringResource(R.string.profile_bosses_search_hint)) },
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
                    text = stringResource(R.string.profile_bosses_no_match),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                value = holdText,
                onValueChange = { holdText = it.filter { ch -> ch.isDigit() }.take(3) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.profile_bosses_hold)) },
                supportingText = {
                    Text(
                        stringResource(
                            R.string.profile_bosses_hold_hint,
                            KillBossesConfig.DEFAULT_HOLD_SEC,
                        ),
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
                        text = stringResource(R.string.profile_bosses_golden),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(R.string.profile_bosses_golden_hint),
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
                Text(stringResource(R.string.profile_bosses_save_hold))
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
                text = stringResource(R.string.profile_elf_params),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.profile_elf_params_hint),
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
                    label = { Text(stringResource(R.string.profile_elf_open_world)) },
                )
                FilterChip(
                    selected = war,
                    onClick = {
                        ProfileRepository.setBotMode(profileFilename, BotMode.ELF_BUFF_WAR)
                    },
                    enabled = profile != null,
                    label = { Text(stringResource(R.string.profile_elf_war)) },
                )
            }

            if (war) {
                Text(
                    text = stringResource(R.string.profile_elf_war_hint),
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
    val savedLabel = stringResource(R.string.saved)

    LaunchedEffect(profile?.elfBuffCastIntervalSec, profile?.elfBuffAutoCast) {
        intervalText = (profile?.elfBuffCastIntervalSec ?: BotProfile.DEFAULT_ELF_CAST_INTERVAL_SEC).toString()
        autoCast = profile?.elfBuffAutoCast ?: true
    }

    Text(
        text = stringResource(R.string.profile_elf_giver_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    OutlinedTextField(
        value = intervalText,
        onValueChange = { intervalText = it.filter { ch -> ch.isDigit() }.take(3) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.profile_elf_cast_pause)) },
        supportingText = {
            Text(stringResource(R.string.profile_elf_cast_pause_hint))
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
            text = stringResource(R.string.profile_elf_auto_cast),
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
            saveHint = savedLabel
        },
        modifier = Modifier.fillMaxWidth(),
        enabled = profile != null,
    ) {
        Text(stringResource(R.string.profile_elf_save_cast))
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
