package com.example.muamaizingbot.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import com.example.muamaizingbot.R
import androidx.compose.ui.unit.dp
import com.example.muamaizingbot.maps.MapDefinition
import com.example.muamaizingbot.maps.MapDefinitionRepository
import com.example.muamaizingbot.bot.maintenance.ElfBuffTargetingActions
import com.example.muamaizingbot.profile.BotMode
import com.example.muamaizingbot.profile.BotProfile
import com.example.muamaizingbot.profile.CombatFocusPkMode
import com.example.muamaizingbot.profile.FarmLocation
import com.example.muamaizingbot.profile.KillBossesConfig
import com.example.muamaizingbot.profile.LocationRepository
import com.example.muamaizingbot.profile.ModeRotationConfig
import com.example.muamaizingbot.profile.ModeRotationStrategy
import com.example.muamaizingbot.profile.PetType
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
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CompactInfoTitle(
            title = stringResource(R.string.profile_config_title),
            info = stringResource(R.string.profile_config_hint),
            titleStyle = MaterialTheme.typography.headlineSmall,
        )

        Text(
            text = profile?.displayName ?: profileStem,
            style = MaterialTheme.typography.titleMedium,
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

        CombatFocusConfigCard(
            profile = profile,
            profileFilename = profileFilename,
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompactInfoTitle(
                    title = stringResource(R.string.profile_bot_mode),
                    info = stringResource(R.string.profile_bot_mode_hint),
                    modifier = Modifier.weight(1f),
                )
                val botModeOptions = listOf(
                    stringResource(R.string.profile_mode_farm),
                    stringResource(R.string.profile_mode_elf),
                    stringResource(R.string.profile_mode_bosses),
                )
                val botModeIndex = when {
                    profile?.isFarmBossesMode() == true -> 2
                    profile?.isElfBuffPostMode() == true -> 1
                    else -> 0
                }
                CompactDropdown(
                    selected = botModeOptions[botModeIndex],
                    options = botModeOptions,
                    enabled = profile != null,
                    modifier = Modifier.width(190.dp),
                    onSelect = { index ->
                        when (index) {
                            0 -> ProfileRepository.setBotMode(profileFilename, BotMode.FARM)
                            1 -> {
                                if (profile?.isElfBuffPostMode() != true) {
                                    ProfileRepository.setBotMode(
                                        profileFilename,
                                        BotMode.ELF_BUFF_GIVER,
                                    )
                                }
                            }
                            2 -> ProfileRepository.setBotMode(profileFilename, BotMode.FARM_BOSSES)
                        }
                    },
                )
            }
        }

        ModeRotationConfigCard(
            profile = profile,
            profileFilename = profileFilename,
        )

        SectionHeader(stringResource(R.string.profile_section_mode_settings))

        val rotationOn = profile?.modeRotation?.enabled == true
        when {
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
                PetConfigCard(
                    profile = profile,
                    profileFilename = profileFilename,
                )
                ElfBuffParamsCard(
                    profile = profile,
                    profileFilename = profileFilename,
                )
            }
            rotationOn -> {
                ConfigOptionCard(
                    title = stringResource(R.string.profile_farm_spot),
                    summary = farmSpot?.summaryLabel(
                        MapDefinitionRepository.getById(farmSpot.map)?.name
                    ) ?: stringResource(R.string.profiles_unset),
                    onClick = onOpenFarmSpot,
                )
                FarmBossesConfigCard(
                    profile = profile,
                    profileFilename = profileFilename,
                )
                PetConfigCard(
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
            profile?.isFarmBossesMode() == true -> {
                FarmBossesConfigCard(
                    profile = profile,
                    profileFilename = profileFilename,
                )
                PetConfigCard(
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
            else -> {
                ConfigOptionCard(
                    title = stringResource(R.string.profile_farm_spot),
                    summary = farmSpot?.summaryLabel(
                        MapDefinitionRepository.getById(farmSpot.map)?.name
                    ) ?: stringResource(R.string.profiles_unset),
                    onClick = onOpenFarmSpot,
                )
                PetConfigCard(
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
        }

        OutlinedButton(
            onClick = { showDeleteDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp),
            enabled = profile != null && profiles.size > 1,
        ) {
            Text(stringResource(R.string.profile_delete))
        }

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp),
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
        verticalArrangement = Arrangement.spacedBy(3.dp),
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
private fun CompactInfoTitle(
    title: String,
    info: String,
    modifier: Modifier = Modifier,
    titleStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = title,
            style = titleStyle,
            fontWeight = FontWeight.SemiBold,
        )
        CompactInfoButton(title = title, info = info)
    }
}

@Composable
private fun CompactInfoButton(title: String, info: String) {
    var showInfo by remember { mutableStateOf(false) }
    IconButton(
        onClick = { showInfo = true },
        modifier = Modifier.size(30.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = stringResource(R.string.profile_info),
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text(title) },
            text = { Text(info) },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) {
                    Text(stringResource(R.string.profile_info_close))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactDropdown(
    selected: String,
    options: List<String>,
    onSelect: (Int) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option, style = MaterialTheme.typography.bodyMedium) },
                    onClick = {
                        onSelect(index)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun RandomTeleportConfigCard(
    profile: BotProfile?,
    profileFilename: String,
) {
    val randomEnabled = profile?.enableRandomTeleport != false
    var dotsText by remember(profile?.filename, profile?.randomTeleportFarMinDots) {
        mutableStateOf(
            (profile?.randomTeleportFarMinDots ?: BotProfile.DEFAULT_RANDOM_FAR_MIN_DOTS).toString(),
        )
    }
    LaunchedEffect(profile?.filename, profile?.randomTeleportFarMinDots) {
        dotsText = (profile?.randomTeleportFarMinDots ?: BotProfile.DEFAULT_RANDOM_FAR_MIN_DOTS).toString()
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
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompactInfoTitle(
                    title = stringResource(R.string.profile_random_title),
                    info = stringResource(R.string.profile_random_hint),
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = randomEnabled,
                    onCheckedChange = { enabled ->
                        ProfileRepository.setRandomTeleportEnabled(profileFilename, enabled)
                    },
                    enabled = profile != null,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = dotsText,
                    onValueChange = { dotsText = it.filter { ch -> ch.isDigit() }.take(2) },
                    modifier = Modifier.weight(0.32f),
                    enabled = profile != null && randomEnabled,
                    singleLine = true,
                    label = { Text(stringResource(R.string.profile_random_dots_label)) },
                )
                CompactInfoButton(
                    title = stringResource(R.string.profile_random_dots_label),
                    info = stringResource(
                        R.string.profile_random_dots_hint,
                        BotProfile.MIN_RANDOM_FAR_MIN_DOTS,
                        BotProfile.MAX_RANDOM_FAR_MIN_DOTS,
                    ),
                )
                OutlinedButton(
                    onClick = {
                        val dots = dotsText.toIntOrNull() ?: return@OutlinedButton
                        ProfileRepository.setRandomTeleportFarMinDots(profileFilename, dots)
                    },
                    modifier = Modifier
                        .weight(0.68f)
                        .height(38.dp),
                    enabled = profile != null &&
                        randomEnabled &&
                        dotsText.toIntOrNull() != null,
                ) {
                    Text(stringResource(R.string.profile_random_dots_save))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModeRotationConfigCard(
    profile: BotProfile?,
    profileFilename: String,
) {
    val config = profile?.modeRotation ?: ModeRotationConfig()
    val enabled = config.enabled
    val strategies = ModeRotationStrategy.entries
    val strategyLabels = listOf(
        stringResource(R.string.profile_mode_rotation_map_lap),
        stringResource(R.string.profile_mode_rotation_clock),
    )
    var restText by remember(profile?.filename, config.restMinutes) {
        mutableStateOf(config.restMinutes.toString())
    }
    var spotTimeText by remember(profile?.filename, config.farmWindows) {
        mutableStateOf(ModeRotationConfig.primaryTime(config.farmWindows))
    }
    var bossesTimeText by remember(profile?.filename, config.bossesWindows) {
        mutableStateOf(ModeRotationConfig.primaryTime(config.bossesWindows))
    }
    LaunchedEffect(config.restMinutes, config.farmWindows, config.bossesWindows) {
        restText = config.restMinutes.toString()
        spotTimeText = ModeRotationConfig.primaryTime(config.farmWindows)
        bossesTimeText = ModeRotationConfig.primaryTime(config.bossesWindows)
    }

    fun persist(update: ModeRotationConfig.() -> ModeRotationConfig) {
        if (profile == null) return
        ProfileRepository.setModeRotationConfig(profileFilename, config.update())
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
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompactInfoTitle(
                    title = stringResource(R.string.profile_mode_rotation_title),
                    info = stringResource(R.string.profile_mode_rotation_hint),
                    modifier = Modifier.weight(1f),
                )
                if (enabled) {
                    CompactDropdown(
                        selected = strategyLabels[
                            strategies.indexOf(config.strategy).coerceAtLeast(0),
                        ],
                        options = strategyLabels,
                        enabled = profile != null,
                        modifier = Modifier.width(130.dp),
                        onSelect = { index ->
                            persist { copy(strategy = strategies[index]) }
                        },
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { on ->
                        if (on) {
                            val segment = if (profile?.isFarmBossesMode() == true) {
                                ModeRotationConfig.SEGMENT_BOSSES
                            } else {
                                ModeRotationConfig.SEGMENT_REST
                            }
                            persist {
                                copy(
                                    enabled = true,
                                    segment = segment,
                                    lapCompletePending = false,
                                )
                            }
                        } else {
                            persist { copy(enabled = false) }
                        }
                    },
                    enabled = profile != null,
                )
            }
            if (enabled) {
                when (config.strategy) {
                    ModeRotationStrategy.MAP_LAP -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = restText,
                                onValueChange = {
                                    restText = it.filter { ch -> ch.isDigit() }.take(4)
                                },
                                modifier = Modifier.weight(0.36f),
                                enabled = profile != null,
                                singleLine = true,
                                label = {
                                    Text(stringResource(R.string.profile_mode_rotation_rest_label))
                                },
                            )
                            CompactInfoButton(
                                title = stringResource(R.string.profile_mode_rotation_rest_label),
                                info = stringResource(
                                    R.string.profile_mode_rotation_rest_hint,
                                    ModeRotationConfig.MIN_REST_MINUTES,
                                    ModeRotationConfig.MAX_REST_MINUTES,
                                ),
                            )
                            OutlinedButton(
                                onClick = {
                                    val minutes = restText.toIntOrNull() ?: return@OutlinedButton
                                    persist {
                                        copy(
                                            restMinutes = minutes.coerceIn(
                                                ModeRotationConfig.MIN_REST_MINUTES,
                                                ModeRotationConfig.MAX_REST_MINUTES,
                                            ),
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .weight(0.64f)
                                    .height(38.dp),
                                enabled = profile != null && restText.toIntOrNull() != null,
                            ) {
                                Text(stringResource(R.string.profile_mode_rotation_rest_save))
                            }
                        }
                    }
                    ModeRotationStrategy.CLOCK -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = spotTimeText,
                                onValueChange = { raw ->
                                    val formatted = ModeRotationConfig.formatHhMmInput(raw)
                                    spotTimeText = formatted
                                    if (ModeRotationConfig.isValidHhMm(formatted)) {
                                        persist {
                                            copy(farmWindows = listOf(formatted))
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = profile != null,
                                singleLine = true,
                                placeholder = { Text("HH:MM") },
                                label = {
                                    Text(stringResource(R.string.profile_mode_rotation_spot_time))
                                },
                            )
                            OutlinedTextField(
                                value = bossesTimeText,
                                onValueChange = { raw ->
                                    val formatted = ModeRotationConfig.formatHhMmInput(raw)
                                    bossesTimeText = formatted
                                    if (ModeRotationConfig.isValidHhMm(formatted)) {
                                        persist {
                                            copy(bossesWindows = listOf(formatted))
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = profile != null,
                                singleLine = true,
                                placeholder = { Text("HH:MM") },
                                label = {
                                    Text(stringResource(R.string.profile_mode_rotation_bosses_time))
                                },
                            )
                            CompactInfoButton(
                                title = stringResource(R.string.profile_mode_rotation_clock),
                                info = stringResource(R.string.profile_mode_rotation_time_hint),
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.profile_mode_rotation_preview_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CombatFocusConfigCard(
    profile: BotProfile?,
    profileFilename: String,
) {
    val enabled = profile?.enableCombatFocus == true
    val selected = profile?.combatFocusPkMode ?: CombatFocusPkMode.DEFAULT
    val unionLabel = if (ElfBuffTargetingActions.resolveIsCross()) {
        stringResource(R.string.profile_combat_focus_pk_union_kuafu)
    } else {
        stringResource(R.string.profile_combat_focus_pk_union)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompactInfoTitle(
                title = stringResource(R.string.profile_combat_focus_title),
                info = stringResource(R.string.profile_combat_focus_hint),
                modifier = Modifier.weight(1f),
            )
            val pkModes = CombatFocusPkMode.entries
            val pkLabels = listOf(
                stringResource(R.string.profile_combat_focus_pk_peace),
                stringResource(R.string.profile_combat_focus_pk_team),
                unionLabel,
                stringResource(R.string.profile_combat_focus_pk_all),
            )
            CompactDropdown(
                selected = pkLabels[pkModes.indexOf(selected).coerceAtLeast(0)],
                options = pkLabels,
                enabled = profile != null && enabled,
                modifier = Modifier.width(190.dp),
                onSelect = { index ->
                    ProfileRepository.setCombatFocusPkMode(profileFilename, pkModes[index])
                },
            )
            Switch(
                checked = enabled,
                onCheckedChange = { on ->
                    ProfileRepository.setCombatFocusEnabled(profileFilename, on)
                },
                enabled = profile != null,
            )
        }
    }
}

@Composable
private fun PetConfigCard(
    profile: BotProfile?,
    profileFilename: String,
) {
    val pet = profile?.effectivePetConfig()
    val enabled = pet?.enablePet == true
    val selected = pet?.petType ?: PetType.DEFAULT
    val petTypes = PetType.entries
    val petLabels = listOf(
        stringResource(R.string.profile_pet_angel),
        stringResource(R.string.profile_pet_imp),
    )
    var intervalText by remember(profile?.filename, profile?.botMode, pet?.petCheckIntervalMinutes) {
        mutableStateOf(
            (pet?.petCheckIntervalMinutes
                ?: BotProfile.DEFAULT_PET_CHECK_INTERVAL_MINUTES).toString(),
        )
    }
    LaunchedEffect(profile?.botMode, pet?.petCheckIntervalMinutes) {
        intervalText = (pet?.petCheckIntervalMinutes
            ?: BotProfile.DEFAULT_PET_CHECK_INTERVAL_MINUTES).toString()
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
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompactInfoTitle(
                    title = stringResource(R.string.profile_pet_title),
                    info = stringResource(R.string.profile_pet_hint),
                    modifier = Modifier.weight(1f),
                )
                CompactDropdown(
                    selected = petLabels[petTypes.indexOf(selected).coerceAtLeast(0)],
                    options = petLabels,
                    enabled = profile != null && enabled,
                    modifier = Modifier.width(120.dp),
                    onSelect = { index ->
                        ProfileRepository.setPetType(profileFilename, petTypes[index])
                    },
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = { on ->
                        ProfileRepository.setPetEnabled(profileFilename, on)
                    },
                    enabled = profile != null,
                )
            }
            if (enabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = intervalText,
                        onValueChange = { intervalText = it.filter { ch -> ch.isDigit() }.take(3) },
                        modifier = Modifier.weight(0.32f),
                        enabled = profile != null,
                        singleLine = true,
                        label = { Text(stringResource(R.string.profile_pet_check_interval_label)) },
                    )
                    CompactInfoButton(
                        title = stringResource(R.string.profile_pet_check_interval_label),
                        info = stringResource(
                            R.string.profile_pet_check_interval_hint,
                            BotProfile.MIN_PET_CHECK_INTERVAL_MINUTES,
                            BotProfile.MAX_PET_CHECK_INTERVAL_MINUTES,
                        ),
                    )
                    OutlinedButton(
                        onClick = {
                            val minutes = intervalText.toIntOrNull() ?: return@OutlinedButton
                            ProfileRepository.setPetCheckIntervalMinutes(profileFilename, minutes)
                        },
                        modifier = Modifier
                            .weight(0.68f)
                            .height(38.dp),
                        enabled = profile != null && intervalText.toIntOrNull() != null,
                    ) {
                        Text(stringResource(R.string.profile_pet_check_interval_save))
                    }
                }
            }
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompactInfoTitle(
                title = stringResource(R.string.profile_elf_auto),
                info = stringResource(R.string.profile_elf_auto_hint),
            )
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = onOpenElfBuff,
                modifier = Modifier
                    .width(92.dp)
                    .height(38.dp),
            ) {
                Text(
                    if (elfBuff != null) stringResource(R.string.profile_elf_edit_zone) else stringResource(R.string.profile_elf_set_zone),
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
                pet = config.pet,
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
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            CompactInfoTitle(
                title = stringResource(R.string.mode_farm_bosses),
                info = stringResource(R.string.profile_bosses_hint),
            )

            if (selectedMaps.isEmpty()) {
                Text(
                    text = stringResource(R.string.profile_bosses_no_maps),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = holdText,
                    onValueChange = { holdText = it.filter { ch -> ch.isDigit() }.take(3) },
                    modifier = Modifier.weight(0.32f),
                    label = { Text(stringResource(R.string.profile_bosses_hold)) },
                    singleLine = true,
                    enabled = profile != null,
                )
                CompactInfoButton(
                    title = stringResource(R.string.profile_bosses_hold),
                    info = stringResource(
                        R.string.profile_bosses_hold_hint,
                        KillBossesConfig.DEFAULT_HOLD_SEC,
                    ),
                )
                OutlinedButton(
                    onClick = {
                        val hold = holdText.toIntOrNull()
                            ?.coerceIn(KillBossesConfig.MIN_HOLD_SEC, KillBossesConfig.MAX_HOLD_SEC)
                            ?: KillBossesConfig.DEFAULT_HOLD_SEC
                        holdText = hold.toString()
                        persist(holdOverride = hold)
                    },
                    modifier = Modifier
                        .weight(0.68f)
                        .height(38.dp),
                    enabled = profile != null,
                ) {
                    Text(stringResource(R.string.profile_bosses_save_hold))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompactInfoTitle(
                    title = stringResource(R.string.profile_bosses_golden),
                    info = stringResource(R.string.profile_bosses_golden_hint),
                    modifier = Modifier.weight(1f),
                    titleStyle = MaterialTheme.typography.bodyMedium,
                )
                Switch(
                    checked = golden,
                    onCheckedChange = { enabled ->
                        golden = enabled
                        persist(goldenOverride = enabled)
                    },
                    enabled = profile != null,
                )
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
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            CompactInfoTitle(
                title = stringResource(R.string.profile_elf_params),
                info = stringResource(R.string.profile_elf_params_hint),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                CompactInfoTitle(
                    title = stringResource(R.string.profile_elf_war),
                    info = stringResource(R.string.profile_elf_war_hint),
                    titleStyle = MaterialTheme.typography.bodyMedium,
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

    CompactInfoTitle(
        title = stringResource(R.string.profile_elf_open_world),
        info = stringResource(R.string.profile_elf_giver_hint),
        titleStyle = MaterialTheme.typography.bodyMedium,
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = intervalText,
            onValueChange = { intervalText = it.filter { ch -> ch.isDigit() }.take(3) },
            modifier = Modifier.weight(0.32f),
            label = { Text(stringResource(R.string.profile_elf_cast_pause)) },
            singleLine = true,
            enabled = profile != null,
        )
        CompactInfoButton(
            title = stringResource(R.string.profile_elf_cast_pause),
            info = stringResource(R.string.profile_elf_cast_pause_hint),
        )
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
            modifier = Modifier
                .weight(0.68f)
                .height(38.dp),
            enabled = profile != null,
        ) {
            Text(stringResource(R.string.profile_elf_save_cast))
        }
    }

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
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
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
