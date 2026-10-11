package com.example.muamaizingbot.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.muamaizingbot.R
import com.example.muamaizingbot.bot.maintenance.ElfBuffTargetingActions
import com.example.muamaizingbot.maps.MapDefinitionRepository
import com.example.muamaizingbot.profile.BotMode
import com.example.muamaizingbot.profile.BotProfile
import com.example.muamaizingbot.profile.CombatFocusPkMode
import com.example.muamaizingbot.profile.FarmLocation
import com.example.muamaizingbot.profile.LocationRepository
import com.example.muamaizingbot.profile.ModeRotationConfig
import com.example.muamaizingbot.profile.ModeRotationStrategy
import com.example.muamaizingbot.profile.PetType
import com.example.muamaizingbot.profile.ProfileRepository
import com.example.muamaizingbot.profile.isElfBuffWarMode
import com.example.muamaizingbot.profile.isFarmBossesMode
import com.example.muamaizingbot.ui.settings.components.ContextoChip
import com.example.muamaizingbot.ui.settings.components.ContextoDenseField
import com.example.muamaizingbot.ui.settings.components.ContextoDenseSelect
import com.example.muamaizingbot.ui.settings.components.ContextoSurfaceCard
import com.example.muamaizingbot.ui.settings.components.ProfileEditorNav
import com.example.muamaizingbot.ui.settings.components.ProfileEditorSidebar
import com.example.muamaizingbot.ui.theme.ContextoColors
import com.example.muamaizingbot.ui.theme.ContextoTheme

private enum class ProfileEditSheet {
    SPOT,
    BOSSES,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileConfigureScreen(
    profileStem: String,
    onOpenFarmSpot: () -> Unit,
    onOpenElfBuff: () -> Unit,
    onOpenPotionConfig: () -> Unit,
    onOpenDevilSquareConfig: () -> Unit,
    onRearmWithAssistant: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ContextoTheme {
        ProfileConfigureScreenContent(
            profileStem = profileStem,
            onOpenFarmSpot = onOpenFarmSpot,
            onOpenElfBuff = onOpenElfBuff,
            onOpenPotionConfig = onOpenPotionConfig,
            onOpenDevilSquareConfig = onOpenDevilSquareConfig,
            onRearmWithAssistant = onRearmWithAssistant,
            onBack = onBack,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileConfigureScreenContent(
    profileStem: String,
    onOpenFarmSpot: () -> Unit,
    onOpenElfBuff: () -> Unit,
    onOpenPotionConfig: () -> Unit,
    onOpenDevilSquareConfig: () -> Unit,
    onRearmWithAssistant: () -> Unit,
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
    var section by remember { mutableStateOf(ProfileEditorNav.ESTILO) }
    var editSheet by remember { mutableStateOf<ProfileEditSheet?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val contentScroll = rememberScrollState()
    val sheetScroll = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val (summaryChips, summaryFootnote) = rememberProfileSummaryChips(profile, farmSpot)

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            ProfileEditorSidebar(
                profileName = profile?.displayName ?: profileStem,
                selected = section,
                onSelect = { section = it },
                onRearm = onRearmWithAssistant,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(contentScroll)
                    .padding(start = 12.dp, end = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ProfileSummaryChipRow(
                    chips = summaryChips,
                    footnote = summaryFootnote,
                    onChipClick = { kind ->
                        when (kind) {
                            ProfileSummaryChip.Kind.CICLOS -> {
                                section = ProfileEditorNav.ESTILO
                            }
                            ProfileSummaryChip.Kind.SPOT -> {
                                section = ProfileEditorNav.LUGARES
                                editSheet = ProfileEditSheet.SPOT
                            }
                            ProfileSummaryChip.Kind.BOSSES -> {
                                section = ProfileEditorNav.LUGARES
                                editSheet = ProfileEditSheet.BOSSES
                            }
                        }
                    },
                )
                when (section) {
                    ProfileEditorNav.ESTILO -> {
                        ContextoEstiloSection(
                            profile = profile,
                            profileFilename = profileFilename,
                        )
                    }
                    ProfileEditorNav.LUGARES -> {
                        ContextoLugaresSection(
                            profile = profile,
                            farmSpot = farmSpot,
                            elfBuff = elfBuff,
                            onOpenSpotSheet = { editSheet = ProfileEditSheet.SPOT },
                            onOpenBossesSheet = { editSheet = ProfileEditSheet.BOSSES },
                            onOpenElf = onOpenElfBuff,
                        )
                    }
                    ProfileEditorNav.MANTENIMIENTO -> {
                        ContextoMantenimientoSection(
                            profile = profile,
                            profileFilename = profileFilename,
                            onOpenPotionConfig = onOpenPotionConfig,
                            onOpenSpotSheet = { editSheet = ProfileEditSheet.SPOT },
                            onOpenBossesSheet = { editSheet = ProfileEditSheet.BOSSES },
                            onOpenLugaresElf = {
                                section = ProfileEditorNav.LUGARES
                                onOpenElfBuff()
                            },
                        )
                    }
                    ProfileEditorNav.AVANZADO -> {
                        ContextoAvanzadoSection(
                            profile = profile,
                            profileFilename = profileFilename,
                            onOpenDevilSquareConfig = onOpenDevilSquareConfig,
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp),
                enabled = profile != null && profiles.size > 1,
            ) {
                Text(
                    stringResource(R.string.profile_delete),
                    color = ContextoColors.Danger,
                )
            }
            TextButton(
                onClick = onBack,
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp),
            ) {
                Text(
                    stringResource(R.string.action_back),
                    color = ContextoColors.TextSecondary,
                )
            }
        }
    }

    if (editSheet != null) {
        ModalBottomSheet(
            onDismissRequest = { editSheet = null },
            sheetState = sheetState,
            containerColor = ContextoColors.Surface,
            contentColor = ContextoColors.TextPrimary,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(sheetScroll)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (editSheet) {
                    ProfileEditSheet.SPOT -> {
                        Text(
                            text = stringResource(R.string.profile_sheet_spot_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = ContextoColors.TextPrimary,
                        )
                        Text(
                            text = stringResource(R.string.profile_sheet_spot_sub),
                            color = ContextoColors.TextMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        PetConfigCard(
                            profile = profile,
                            profileFilename = profileFilename,
                            forceFarmPet = true,
                            titleRes = R.string.profile_pet_farm_title,
                            hintRes = R.string.profile_pet_farm_hint,
                        )
                        Button(
                            onClick = {
                                editSheet = null
                                onOpenFarmSpot()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = ContextoColors.Teal,
                                contentColor = ContextoColors.Bg,
                            ),
                        ) {
                            Text(stringResource(R.string.profile_sheet_edit_point))
                        }
                    }
                    ProfileEditSheet.BOSSES -> {
                        Text(
                            text = stringResource(R.string.profile_sheet_bosses_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = ContextoColors.TextPrimary,
                        )
                        Text(
                            text = stringResource(R.string.profile_sheet_bosses_sub),
                            color = ContextoColors.TextMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        PetConfigCard(
                            profile = profile,
                            profileFilename = profileFilename,
                            forceBossesPet = true,
                            titleRes = R.string.profile_pet_bosses_title,
                            hintRes = R.string.profile_pet_bosses_hint,
                        )
                        FarmBossesConfigCard(
                            profile = profile,
                            profileFilename = profileFilename,
                        )
                    }
                    null -> Unit
                }
                Button(
                    onClick = { editSheet = null },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = ContextoColors.Teal,
                        contentColor = ContextoColors.Bg,
                    ),
                ) {
                    Text(stringResource(R.string.profile_sheet_listo))
                }
                TextButton(
                    onClick = { editSheet = null },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(
                        stringResource(R.string.profiles_cancel),
                        color = ContextoColors.TextSecondary,
                    )
                }
            }
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

// Legacy section enum removed — use ProfileEditorNav.

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

@Composable
private fun CompactDropdown(
    selected: String,
    options: List<String>,
    onSelect: (Int) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    ContextoDenseSelect(
        label = null,
        value = selected,
        options = options.mapIndexed { index, option -> index.toString() to option },
        onSelect = { id -> id.toIntOrNull()?.let(onSelect) },
        enabled = enabled,
        modifier = modifier,
    )
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

    ContextoSurfaceCard {
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
            verticalAlignment = Alignment.Bottom,
        ) {
            ContextoDenseField(
                value = dotsText,
                onValueChange = { dotsText = it.filter { ch -> ch.isDigit() }.take(2) },
                label = stringResource(R.string.profile_random_dots_label),
                enabled = profile != null && randomEnabled,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(0.32f),
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
                    .height(36.dp),
                enabled = profile != null &&
                    randomEnabled &&
                    dotsText.toIntOrNull() != null,
            ) {
                Text(stringResource(R.string.profile_random_dots_save))
            }
        }
    }
}

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

    ContextoSurfaceCard {
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
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        ContextoDenseField(
                            value = restText,
                            onValueChange = {
                                restText = it.filter { ch -> ch.isDigit() }.take(4)
                            },
                            label = stringResource(R.string.profile_mode_rotation_rest_label),
                            enabled = profile != null,
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(0.36f),
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
                                .height(36.dp),
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
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        ContextoDenseField(
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
                            label = stringResource(R.string.profile_mode_rotation_spot_time),
                            placeholder = "HH:MM",
                            enabled = profile != null,
                            modifier = Modifier.weight(1f),
                        )
                        ContextoDenseField(
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
                            label = stringResource(R.string.profile_mode_rotation_bosses_time),
                            placeholder = "HH:MM",
                            enabled = profile != null,
                            modifier = Modifier.weight(1f),
                        )
                        CompactInfoButton(
                            title = stringResource(R.string.profile_mode_rotation_clock),
                            info = stringResource(R.string.profile_mode_rotation_time_hint),
                        )
                    }
                }
            }
            Text(
                text = when (config.strategy) {
                    ModeRotationStrategy.MAP_LAP ->
                        stringResource(R.string.profile_mode_rotation_map_lap_note)
                    ModeRotationStrategy.CLOCK ->
                        stringResource(R.string.profile_mode_rotation_clock_note)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DevilSquareConfigCard(
    profile: BotProfile?,
    profileFilename: String,
    onEdit: () -> Unit,
) {
    val cfg = profile?.devilSquare
    ContextoSurfaceCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompactInfoTitle(
                title = stringResource(R.string.profile_devil_square_title),
                info = stringResource(R.string.profile_devil_square_hint),
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = onEdit,
                modifier = Modifier
                    .width(92.dp)
                    .height(38.dp),
                enabled = profile != null,
            ) {
                Text(stringResource(R.string.profile_elf_edit_zone))
            }
            Switch(
                checked = cfg?.enabled == true,
                onCheckedChange = { on ->
                    ProfileRepository.setDevilSquareEnabled(profileFilename, on)
                },
                enabled = profile != null,
            )
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

    ContextoSurfaceCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
    forceFarmPet: Boolean = false,
    forceBossesPet: Boolean = false,
    titleRes: Int = R.string.profile_pet_title,
    hintRes: Int = R.string.profile_pet_hint,
) {
    val editBosses = forceBossesPet || (!forceFarmPet && profile?.isFarmBossesMode() == true)
    val pet = when {
        editBosses -> profile?.killBossesConfig?.pet
        else -> profile?.let {
            com.example.muamaizingbot.profile.PetConfig(
                enablePet = it.enablePet,
                petType = it.petType,
                petCheckIntervalMinutes = it.petCheckIntervalMinutes,
            )
        }
    }
    val enabled = pet?.enablePet == true
    val selected = pet?.petType ?: PetType.DEFAULT
    val petTypes = PetType.entries
    val petLabels = listOf(
        stringResource(R.string.profile_pet_angel),
        stringResource(R.string.profile_pet_imp),
    )
    var intervalText by remember(
        profile?.filename,
        editBosses,
        pet?.petCheckIntervalMinutes,
    ) {
        mutableStateOf(
            (pet?.petCheckIntervalMinutes
                ?: BotProfile.DEFAULT_PET_CHECK_INTERVAL_MINUTES).toString(),
        )
    }
    LaunchedEffect(editBosses, pet?.petCheckIntervalMinutes) {
        intervalText = (pet?.petCheckIntervalMinutes
            ?: BotProfile.DEFAULT_PET_CHECK_INTERVAL_MINUTES).toString()
    }

    fun persistEnabled(on: Boolean) {
        if (editBosses) {
            ProfileRepository.setPetEnabledForBosses(profileFilename, on)
        } else {
            ProfileRepository.setPetEnabledForFarm(profileFilename, on)
        }
    }

    fun persistType(type: PetType) {
        if (editBosses) {
            ProfileRepository.setPetTypeForBosses(profileFilename, type)
        } else {
            ProfileRepository.setPetTypeForFarm(profileFilename, type)
        }
    }

    fun persistInterval(minutes: Int) {
        if (editBosses) {
            ProfileRepository.setPetCheckIntervalForBosses(profileFilename, minutes)
        } else {
            ProfileRepository.setPetCheckIntervalForFarm(profileFilename, minutes)
        }
    }

    ContextoSurfaceCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompactInfoTitle(
                title = stringResource(titleRes),
                info = stringResource(hintRes),
                modifier = Modifier.weight(1f),
            )
            ContextoDenseSelect(
                label = null,
                value = petLabels[petTypes.indexOf(selected).coerceAtLeast(0)],
                options = petTypes.mapIndexed { i, type -> type.name to petLabels[i] },
                onSelect = { id ->
                    PetType.entries.firstOrNull { it.name == id }?.let { persistType(it) }
                },
                enabled = profile != null && enabled,
                modifier = Modifier.width(120.dp),
            )
            Switch(
                checked = enabled,
                onCheckedChange = { on -> persistEnabled(on) },
                enabled = profile != null,
            )
        }
        if (enabled) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                ContextoDenseField(
                    value = intervalText,
                    onValueChange = { intervalText = it.filter { ch -> ch.isDigit() }.take(3) },
                    label = stringResource(R.string.profile_pet_check_interval_label),
                    enabled = profile != null,
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(0.32f),
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
                        persistInterval(minutes)
                    },
                    modifier = Modifier
                        .weight(0.68f)
                        .height(36.dp),
                    enabled = profile != null && intervalText.toIntOrNull() != null,
                ) {
                    Text(stringResource(R.string.profile_pet_check_interval_save))
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
    ContextoSurfaceCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
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

@Composable
private fun FarmBossesConfigCard(
    profile: BotProfile?,
    profileFilename: String,
) {
    BossesMapsPanel(
        profile = profile,
        profileFilename = profileFilename,
    )
}

@Composable
private fun ElfBuffParamsCard(
    profile: BotProfile?,
    profileFilename: String,
) {
    val war = profile?.isElfBuffWarMode() == true

    ContextoSurfaceCard {
        CompactInfoTitle(
            title = stringResource(R.string.profile_elf_params),
            info = stringResource(R.string.profile_elf_params_hint),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ContextoChip(
                label = stringResource(R.string.profile_elf_open_world),
                selected = !war,
                onClick = {
                    ProfileRepository.setBotMode(profileFilename, BotMode.ELF_BUFF_GIVER)
                },
                enabled = profile != null,
            )
            ContextoChip(
                label = stringResource(R.string.profile_elf_war),
                selected = war,
                onClick = {
                    ProfileRepository.setBotMode(profileFilename, BotMode.ELF_BUFF_WAR)
                },
                enabled = profile != null,
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
        verticalAlignment = Alignment.Bottom,
    ) {
        ContextoDenseField(
            value = intervalText,
            onValueChange = { intervalText = it.filter { ch -> ch.isDigit() }.take(3) },
            label = stringResource(R.string.profile_elf_cast_pause),
            enabled = profile != null,
            keyboardType = KeyboardType.Number,
            modifier = Modifier.weight(0.32f),
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
                .height(36.dp),
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
    ContextoSurfaceCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
