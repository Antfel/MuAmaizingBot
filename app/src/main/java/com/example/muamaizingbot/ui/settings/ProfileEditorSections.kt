package com.example.muamaizingbot.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.muamaizingbot.R
import com.example.muamaizingbot.maps.MapDefinitionRepository
import com.example.muamaizingbot.profile.BotMode
import com.example.muamaizingbot.profile.BotProfile
import com.example.muamaizingbot.profile.CombatFocusPkMode
import com.example.muamaizingbot.profile.FarmLocation
import com.example.muamaizingbot.profile.ModeRotationConfig
import com.example.muamaizingbot.profile.ModeRotationStrategy
import com.example.muamaizingbot.profile.PetType
import com.example.muamaizingbot.profile.ProfileRepository
import com.example.muamaizingbot.profile.isElfBuffPostMode
import com.example.muamaizingbot.profile.isElfBuffWarMode
import com.example.muamaizingbot.profile.isFarmBossesMode
import com.example.muamaizingbot.ui.settings.components.ContextoSegmentedTab
import com.example.muamaizingbot.ui.settings.components.DenseSettingsRow
import com.example.muamaizingbot.ui.settings.components.InlineStepper
import com.example.muamaizingbot.ui.settings.components.LugaresNavRow
import com.example.muamaizingbot.ui.settings.components.StyleIntentCard
import com.example.muamaizingbot.ui.theme.ContextoColors

private enum class PlayStyleIntent {
    FARM_ONLY,
    BOSSES_ONLY,
    ROTATION,
    ELF,
}

@Composable
fun ContextoEstiloSection(
    profile: BotProfile?,
    profileFilename: String,
) {
    val intent = when {
        profile?.modeRotation?.enabled == true -> PlayStyleIntent.ROTATION
        profile?.isElfBuffPostMode() == true -> PlayStyleIntent.ELF
        profile?.isFarmBossesMode() == true -> PlayStyleIntent.BOSSES_ONLY
        else -> PlayStyleIntent.FARM_ONLY
    }
    val rotation = profile?.modeRotation ?: ModeRotationConfig()

    Text(
        text = stringResource(R.string.profile_estilo_title),
        color = ContextoColors.TextPrimary,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
    )

    Text(
        text = stringResource(R.string.profile_estilo_choose),
        color = ContextoColors.TextSecondary,
        fontSize = 12.sp,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StyleIntentCard(
            title = stringResource(R.string.assistant_style_farm),
            subtitle = stringResource(R.string.profile_estilo_farm_sub),
            selected = intent == PlayStyleIntent.FARM_ONLY,
            onClick = {
                ProfileRepository.setBotMode(profileFilename, BotMode.FARM)
                ProfileRepository.setModeRotationConfig(
                    profileFilename,
                    rotation.copy(enabled = false),
                )
            },
            modifier = Modifier.weight(1f),
        )
        StyleIntentCard(
            title = stringResource(R.string.assistant_style_bosses),
            subtitle = stringResource(R.string.profile_estilo_bosses_sub),
            selected = intent == PlayStyleIntent.BOSSES_ONLY,
            onClick = {
                ProfileRepository.setBotMode(profileFilename, BotMode.FARM_BOSSES)
                ProfileRepository.setModeRotationConfig(
                    profileFilename,
                    rotation.copy(enabled = false),
                )
            },
            modifier = Modifier.weight(1f),
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StyleIntentCard(
            title = stringResource(R.string.assistant_style_rotation),
            subtitle = stringResource(R.string.profile_estilo_rotation_sub),
            selected = intent == PlayStyleIntent.ROTATION,
            onClick = {
                ProfileRepository.setBotMode(profileFilename, BotMode.FARM_BOSSES)
                ProfileRepository.setModeRotationConfig(
                    profileFilename,
                    rotation.copy(
                        enabled = true,
                        strategy = ModeRotationStrategy.MAP_LAP,
                        segment = ModeRotationConfig.SEGMENT_BOSSES,
                    ),
                )
            },
            modifier = Modifier.weight(1f),
        )
        StyleIntentCard(
            title = stringResource(R.string.assistant_style_elf),
            subtitle = stringResource(R.string.profile_estilo_elf_sub),
            selected = intent == PlayStyleIntent.ELF,
            onClick = {
                ProfileRepository.setBotMode(profileFilename, BotMode.ELF_BUFF_GIVER)
                ProfileRepository.setModeRotationConfig(
                    profileFilename,
                    rotation.copy(enabled = false),
                )
            },
            modifier = Modifier.weight(1f),
        )
    }

    if (intent == PlayStyleIntent.ROTATION || intent == PlayStyleIntent.BOSSES_ONLY ||
        intent == PlayStyleIntent.FARM_ONLY
    ) {
        Text(
            text = stringResource(R.string.profile_mode_rotation_title),
            color = ContextoColors.TextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
        val tabLabels = listOf(
            stringResource(R.string.profile_programacion_off),
            stringResource(R.string.profile_mode_rotation_map_lap),
            stringResource(R.string.profile_mode_rotation_clock),
        )
        val tabIndex = when {
            !rotation.enabled -> 0
            rotation.strategy == ModeRotationStrategy.CLOCK -> 2
            else -> 1
        }
        ContextoSegmentedTab(
            labels = tabLabels,
            selectedIndex = tabIndex,
            onSelect = { index ->
                when (index) {
                    0 -> ProfileRepository.setModeRotationConfig(
                        profileFilename,
                        rotation.copy(enabled = false),
                    )
                    1 -> {
                        if (intent == PlayStyleIntent.FARM_ONLY) {
                            ProfileRepository.setBotMode(profileFilename, BotMode.FARM_BOSSES)
                        }
                        ProfileRepository.setModeRotationConfig(
                            profileFilename,
                            rotation.copy(
                                enabled = true,
                                strategy = ModeRotationStrategy.MAP_LAP,
                            ),
                        )
                    }
                    2 -> {
                        if (intent == PlayStyleIntent.FARM_ONLY) {
                            ProfileRepository.setBotMode(profileFilename, BotMode.FARM_BOSSES)
                        }
                        ProfileRepository.setModeRotationConfig(
                            profileFilename,
                            rotation.copy(
                                enabled = true,
                                strategy = ModeRotationStrategy.CLOCK,
                            ),
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (rotation.enabled && rotation.strategy == ModeRotationStrategy.MAP_LAP) {
            DenseSettingsRow(
                title = stringResource(R.string.profile_mode_rotation_rest_label),
                subtitle = stringResource(R.string.profile_mode_rotation_map_lap_note),
                trailing = {
                    InlineStepper(
                        value = rotation.restMinutes,
                        range = ModeRotationConfig.MIN_REST_MINUTES..ModeRotationConfig.MAX_REST_MINUTES,
                        step = 15,
                        onChange = { minutes ->
                            ProfileRepository.setModeRotationConfig(
                                profileFilename,
                                rotation.copy(restMinutes = minutes),
                            )
                        },
                        suffix = "min",
                    )
                },
            )
            Text(
                text = stringResource(R.string.profile_summary_ciclos_map_lap_note),
                color = ContextoColors.Teal,
                fontSize = 11.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ContextoColors.TealDim, RoundedCornerShape(8.dp))
                    .padding(10.dp),
            )
        }
        if (rotation.enabled && rotation.strategy == ModeRotationStrategy.CLOCK) {
            DenseSettingsRow(
                title = stringResource(R.string.profile_mode_rotation_spot_time),
                trailing = {
                    Text(
                        text = ModeRotationConfig.primaryTime(rotation.farmWindows).ifBlank { "HH:MM" },
                        color = ContextoColors.TextSecondary,
                        fontSize = 12.sp,
                    )
                },
            )
            DenseSettingsRow(
                title = stringResource(R.string.profile_mode_rotation_bosses_time),
                trailing = {
                    Text(
                        text = ModeRotationConfig.primaryTime(rotation.bossesWindows).ifBlank { "HH:MM" },
                        color = ContextoColors.TextSecondary,
                        fontSize = 12.sp,
                    )
                },
            )
            Text(
                text = stringResource(R.string.profile_mode_rotation_clock_note),
                color = ContextoColors.TextMuted,
                fontSize = 11.sp,
            )
        }
    }

    if (intent == PlayStyleIntent.ELF) {
        ContextoSegmentedTab(
            labels = listOf(
                stringResource(R.string.profile_elf_open_world),
                stringResource(R.string.profile_elf_war),
            ),
            selectedIndex = if (profile?.isElfBuffWarMode() == true) 1 else 0,
            onSelect = { index ->
                ProfileRepository.setBotMode(
                    profileFilename,
                    if (index == 1) BotMode.ELF_BUFF_WAR else BotMode.ELF_BUFF_GIVER,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun ContextoLugaresSection(
    profile: BotProfile?,
    farmSpot: FarmLocation?,
    elfBuff: FarmLocation?,
    onOpenSpotSheet: () -> Unit,
    onOpenBossesSheet: () -> Unit,
    onOpenElf: () -> Unit,
) {
    Text(
        text = stringResource(R.string.profile_nav_lugares),
        color = ContextoColors.TextPrimary,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
    )
    val farmSummary = farmSpot?.summaryLabel(
        MapDefinitionRepository.getById(farmSpot.map)?.name,
    ) ?: stringResource(R.string.profiles_unset)
    val bossesCount = profile?.killBossesConfig?.maps?.size ?: 0
    val bossesPet = profile?.killBossesConfig?.pet
    val bossesSub = buildString {
        append(stringResource(R.string.profile_lugares_bosses_sub))
        if (bossesCount > 0) append(" · $bossesCount")
        if (bossesPet?.enablePet == true) {
            append(" · ")
            append(
                when (bossesPet.petType) {
                    PetType.IMP -> stringResource(R.string.profile_pet_imp)
                    else -> stringResource(R.string.profile_pet_angel)
                },
            )
        }
    }
    val elfSummary = elfBuff?.summaryLabel(
        MapDefinitionRepository.getById(elfBuff.map)?.name,
    ) ?: stringResource(R.string.profiles_unset)

    LugaresNavRow(
        title = stringResource(R.string.profile_farm_spot),
        subtitle = "$farmSummary · ${stringResource(R.string.profile_lugares_farm_sub)}",
        onClick = onOpenSpotSheet,
    )
    LugaresNavRow(
        title = stringResource(R.string.mode_farm_bosses),
        subtitle = bossesSub,
        onClick = onOpenBossesSheet,
    )
    LugaresNavRow(
        title = stringResource(R.string.profile_elf_buff),
        subtitle = "$elfSummary · ${stringResource(R.string.profile_lugares_elf_sub)}",
        onClick = onOpenElf,
    )
}

@Composable
fun ContextoMantenimientoSection(
    profile: BotProfile?,
    profileFilename: String,
    onOpenPotionConfig: () -> Unit,
    onOpenSpotSheet: () -> Unit,
    onOpenBossesSheet: () -> Unit,
    onOpenLugaresElf: () -> Unit,
) {
    Text(
        text = stringResource(R.string.profile_nav_mantenimiento),
        color = ContextoColors.TextPrimary,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
    )
    DenseSettingsRow(
        title = stringResource(R.string.potion_config_title),
        subtitle = stringResource(R.string.potion_auto_recovery),
        onClick = onOpenPotionConfig,
        trailing = {
            ContextoSwitch(
                checked = profile?.enablePotionRecovery == true,
                onCheckedChange = { on ->
                    profile?.let {
                        ProfileRepository.saveProfile(it.copy(enablePotionRecovery = on))
                    }
                },
            )
        },
    )
    if (profile?.enablePotionRecovery == true) {
        DenseSettingsRow(
            title = "HP / MP",
            trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InlineStepper(
                        value = profile.hpPotionStacks,
                        range = 1..99,
                        onChange = { v ->
                            ProfileRepository.saveProfile(profile.copy(hpPotionStacks = v))
                        },
                        suffix = "HP",
                    )
                    InlineStepper(
                        value = profile.mpPotionStacks,
                        range = 1..99,
                        onChange = { v ->
                            ProfileRepository.saveProfile(profile.copy(mpPotionStacks = v))
                        },
                        suffix = "MP",
                    )
                }
            },
        )
    }
    if (profile?.isElfBuffPostMode() != true) {
        DenseSettingsRow(
            title = stringResource(R.string.profile_enable_elf_buff),
            trailing = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.profile_zone_in_lugares),
                        color = ContextoColors.Teal,
                        fontSize = 11.sp,
                        modifier = Modifier.clickable(onClick = onOpenLugaresElf),
                    )
                    ContextoSwitch(
                        checked = profile?.enableElfBuff == true,
                        onCheckedChange = { on ->
                            ProfileRepository.setElfBuffEnabled(profileFilename, on)
                        },
                    )
                }
            },
        )
    }
    DenseSettingsRow(
        title = stringResource(R.string.profile_pet_farm_title),
        subtitle = if (profile?.enablePet == true) {
            when (profile.petType) {
                PetType.IMP -> stringResource(R.string.profile_pet_imp)
                else -> stringResource(R.string.profile_pet_angel)
            }
        } else {
            stringResource(R.string.profile_summary_pet_off)
        },
        onClick = onOpenSpotSheet,
        trailing = { Text(text = "›", color = ContextoColors.TextSecondary, fontSize = 20.sp) },
    )
    DenseSettingsRow(
        title = stringResource(R.string.profile_pet_bosses_title),
        subtitle = profile?.killBossesConfig?.pet?.let { pet ->
            if (pet.enablePet) {
                when (pet.petType) {
                    PetType.IMP -> stringResource(R.string.profile_pet_imp)
                    else -> stringResource(R.string.profile_pet_angel)
                }
            } else {
                stringResource(R.string.profile_summary_pet_off)
            }
        },
        onClick = onOpenBossesSheet,
        trailing = { Text(text = "›", color = ContextoColors.TextSecondary, fontSize = 20.sp) },
    )
    DenseSettingsRow(
        title = stringResource(R.string.profile_random_title),
        trailing = {
            ContextoSwitch(
                checked = profile?.enableRandomTeleport == true,
                onCheckedChange = { on ->
                    ProfileRepository.setRandomTeleportEnabled(profileFilename, on)
                },
            )
        },
    )
    if (profile?.enableRandomTeleport == true) {
        DenseSettingsRow(
            title = stringResource(R.string.profile_random_dots_label),
            trailing = {
                InlineStepper(
                    value = profile.randomTeleportFarMinDots,
                    range = BotProfile.MIN_RANDOM_FAR_MIN_DOTS..BotProfile.MAX_RANDOM_FAR_MIN_DOTS,
                    onChange = { v ->
                        ProfileRepository.setRandomTeleportFarMinDots(profileFilename, v)
                    },
                )
            },
        )
    }
    val interval = profile?.effectivePetConfig()?.petCheckIntervalMinutes
        ?: BotProfile.DEFAULT_PET_CHECK_INTERVAL_MINUTES
    DenseSettingsRow(
        title = stringResource(R.string.profile_pet_check_interval_label),
        trailing = {
            InlineStepper(
                value = interval,
                range = BotProfile.MIN_PET_CHECK_INTERVAL_MINUTES..BotProfile.MAX_PET_CHECK_INTERVAL_MINUTES,
                step = 5,
                onChange = { v ->
                    if (profile?.isFarmBossesMode() == true) {
                        ProfileRepository.setPetCheckIntervalForBosses(profileFilename, v)
                    } else {
                        ProfileRepository.setPetCheckIntervalForFarm(profileFilename, v)
                    }
                },
                suffix = "min",
            )
        },
    )
}

@Composable
fun ContextoAvanzadoSection(
    profile: BotProfile?,
    profileFilename: String,
    onOpenDevilSquareConfig: () -> Unit,
) {
    Text(
        text = stringResource(R.string.profile_nav_avanzado),
        color = ContextoColors.TextPrimary,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
    )
    DenseSettingsRow(
        title = stringResource(R.string.profile_combat_focus_title),
        subtitle = profile?.combatFocusPkMode?.name?.lowercase()?.replaceFirstChar { it.uppercase() },
        trailing = {
            ContextoSwitch(
                checked = profile?.enableCombatFocus == true,
                onCheckedChange = { on ->
                    ProfileRepository.setCombatFocusEnabled(profileFilename, on)
                },
            )
        },
    )
    if (profile?.enableCombatFocus == true) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CombatFocusPkMode.entries.forEach { mode ->
                val selected = profile.combatFocusPkMode == mode
                Text(
                    text = mode.name.lowercase().replaceFirstChar { it.uppercase() },
                    color = if (selected) ContextoColors.Bg else ContextoColors.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .background(
                            if (selected) ContextoColors.Teal else ContextoColors.Surface,
                            RoundedCornerShape(6.dp),
                        )
                        .clickable {
                            ProfileRepository.setCombatFocusPkMode(profileFilename, mode)
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
    DenseSettingsRow(
        title = stringResource(R.string.profile_devil_square_title),
        onClick = onOpenDevilSquareConfig,
        trailing = {
            ContextoSwitch(
                checked = profile?.devilSquare?.enabled == true,
                onCheckedChange = { on ->
                    ProfileRepository.setDevilSquareEnabled(profileFilename, on)
                },
            )
        },
    )
    if (profile?.isFarmBossesMode() == true || profile?.modeRotation?.enabled == true) {
        DenseSettingsRow(
            title = stringResource(R.string.profile_bosses_golden),
            subtitle = stringResource(R.string.profile_bosses_golden_hint),
            trailing = {
                ContextoSwitch(
                    checked = profile.killBossesConfig.includeGoldenMobs,
                    onCheckedChange = { on ->
                        ProfileRepository.setKillBossesConfig(
                            profileFilename,
                            profile.killBossesConfig.copy(includeGoldenMobs = on),
                        )
                    },
                )
            },
        )
    }
}

@Composable
private fun ContextoSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = ContextoColors.Bg,
            checkedTrackColor = ContextoColors.Teal,
            uncheckedThumbColor = ContextoColors.TextSecondary,
            uncheckedTrackColor = ContextoColors.Border,
        ),
    )
}
