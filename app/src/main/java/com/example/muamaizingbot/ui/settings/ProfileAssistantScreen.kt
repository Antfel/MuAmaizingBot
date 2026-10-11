package com.example.muamaizingbot.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.muamaizingbot.R
import com.example.muamaizingbot.maps.MapDefinitionRepository
import com.example.muamaizingbot.profile.CombatFocusPkMode
import com.example.muamaizingbot.profile.LocationRepository
import com.example.muamaizingbot.profile.ModeRotationConfig
import com.example.muamaizingbot.profile.ModeRotationStrategy
import com.example.muamaizingbot.profile.PetType
import com.example.muamaizingbot.profile.ProfileRepository
import com.example.muamaizingbot.ui.settings.components.ContextoChip
import com.example.muamaizingbot.ui.settings.components.ContextoDenseField
import com.example.muamaizingbot.ui.settings.components.ContextoDenseSearchSelect
import com.example.muamaizingbot.ui.settings.components.ContextoPrimaryButton
import com.example.muamaizingbot.ui.settings.components.ContextoSecondaryButton
import com.example.muamaizingbot.ui.settings.components.ContextoSectionTitle
import com.example.muamaizingbot.ui.settings.components.ContextoSurfaceCard
import com.example.muamaizingbot.ui.settings.components.InlineStepper
import com.example.muamaizingbot.ui.settings.components.StyleIntentCard
import com.example.muamaizingbot.ui.theme.ContextoColors
import com.example.muamaizingbot.ui.theme.ContextoTheme

@Composable
fun ProfileAssistantScreen(
    initialProfileStem: String?,
    onOpenFarmSpot: (profileStem: String) -> Unit,
    onOpenElfZone: (profileStem: String) -> Unit,
    onFinishedEditDetail: (profileStem: String) -> Unit,
    onFinishedDone: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ContextoTheme {
        ProfileAssistantScreenContent(
            initialProfileStem = initialProfileStem,
            onOpenFarmSpot = onOpenFarmSpot,
            onOpenElfZone = onOpenElfZone,
            onFinishedEditDetail = onFinishedEditDetail,
            onFinishedDone = onFinishedDone,
            onCancel = onCancel,
            modifier = modifier,
        )
    }
}

@Composable
private fun ProfileAssistantScreenContent(
    initialProfileStem: String?,
    onOpenFarmSpot: (profileStem: String) -> Unit,
    onOpenElfZone: (profileStem: String) -> Unit,
    onFinishedEditDetail: (profileStem: String) -> Unit,
    onFinishedDone: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var step by remember {
        mutableIntStateOf(ProfileAssistantSession.step)
    }
    var draft by remember {
        mutableStateOf(ProfileAssistantSession.draft)
    }

    LaunchedEffect(Unit) {
        if (!ProfileAssistantSession.active) {
            val seeded = initialProfileStem
                ?.let { ProfileRepository.getProfile("$it.json") }
                ?.let { ProfileAssistantApplier.draftFromProfile(it) }
                ?: AssistantDraft()
            ProfileAssistantSession.begin(seeded)
            draft = ProfileAssistantSession.draft
            step = ProfileAssistantSession.step
        }
    }

    fun setStep(value: Int) {
        step = value
        ProfileAssistantSession.step = value
    }

    fun setDraft(value: AssistantDraft) {
        draft = value
        ProfileAssistantSession.draft = value
    }

    val filename = draft.profileFilename
    val farmSpot = filename?.let { LocationRepository.getFarmSpot(it) }
    val elfZone = filename?.let { LocationRepository.getElfBuff(it) }
    val hasFarmSpot = farmSpot != null

    LaunchedEffect(filename) {
        if (filename != null) {
            LocationRepository.refreshForCurrentProfile()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ContextoSectionTitle(
            title = stringResource(R.string.assistant_title),
            subtitle = stringResource(R.string.assistant_step_label, step + 1, 5),
        )

        when (step) {
            0 -> StepStyle(
                draft = draft,
                onDraftChange = { setDraft(it) },
            )
            1 -> StepCore(
                draft = draft,
                onDraftChange = { setDraft(it) },
                farmSummary = farmSpot?.summaryLabel(
                    MapDefinitionRepository.getById(farmSpot.map)?.name,
                ),
                hasFarmSpot = hasFarmSpot,
                onPickFarmSpot = {
                    val stem = ensureStem(draft) { setDraft(it) } ?: return@StepCore
                    onOpenFarmSpot(stem)
                },
            )
            2 -> StepMaintenance(
                draft = draft,
                onDraftChange = { setDraft(it) },
                elfSummary = elfZone?.summaryLabel(
                    MapDefinitionRepository.getById(elfZone.map)?.name,
                ),
                onPickElfZone = {
                    val stem = ensureStem(draft) { setDraft(it) } ?: return@StepMaintenance
                    onOpenElfZone(stem)
                },
            )
            3 -> StepCombat(
                draft = draft,
                onDraftChange = { setDraft(it) },
                onSkip = {
                    val skipped = draft.copy(enableCombatFocus = false)
                    setDraft(skipped)
                    ProfileAssistantApplier.applyCombat(skipped)
                    setStep(4)
                },
            )
            else -> StepSummary(
                draft = draft,
                farmSummary = farmSpot?.summaryLabel(
                    MapDefinitionRepository.getById(farmSpot.map)?.name,
                ),
            )
        }

        if (step == 1 && !ProfileAssistantApplier.coreReady(draft, hasFarmSpot)) {
            Text(
                text = stringResource(R.string.assistant_core_incomplete),
                color = ContextoColors.Danger,
                fontSize = 12.sp,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ContextoSecondaryButton(
                text = if (step == 0) {
                    stringResource(R.string.profiles_cancel)
                } else {
                    stringResource(R.string.action_back)
                },
                onClick = {
                    if (step == 0) {
                        ProfileAssistantSession.clear()
                        onCancel()
                    } else {
                        setStep(step - 1)
                    }
                },
                modifier = Modifier.weight(1f),
            )

            when (step) {
                3 -> {
                    ContextoPrimaryButton(
                        text = stringResource(R.string.assistant_next),
                        onClick = {
                            ProfileAssistantApplier.applyCombat(draft)
                            setStep(4)
                        },
                        modifier = Modifier.weight(1f),
                        enabled = draft.style != null,
                    )
                }
                4 -> {
                    ContextoSecondaryButton(
                        text = stringResource(R.string.assistant_edit_detail),
                        onClick = {
                            val applied = ProfileAssistantApplier.applyAll(draft) ?: return@ContextoSecondaryButton
                            ProfileAssistantSession.clear()
                            onFinishedEditDetail(applied.fileStem)
                        },
                        modifier = Modifier.weight(1f),
                    )
                    ContextoPrimaryButton(
                        text = stringResource(R.string.assistant_finish),
                        onClick = {
                            ProfileAssistantApplier.applyAll(draft) ?: return@ContextoPrimaryButton
                            ProfileAssistantSession.clear()
                            onFinishedDone()
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                else -> {
                    ContextoPrimaryButton(
                        text = stringResource(R.string.assistant_next),
                        onClick = {
                            when (step) {
                                0 -> {
                                    if (draft.style == null || draft.displayName.trim().length < 3) {
                                        return@ContextoPrimaryButton
                                    }
                                    val next = ProfileAssistantApplier.ensureProfileAndApplyStyle(draft)
                                    setDraft(next)
                                    ProfileRepository.setCurrentProfile(next.profileFilename!!)
                                    LocationRepository.refreshForCurrentProfile()
                                    setStep(1)
                                }
                                1 -> {
                                    if (!ProfileAssistantApplier.coreReady(draft, hasFarmSpot)) {
                                        return@ContextoPrimaryButton
                                    }
                                    val next = ProfileAssistantApplier.ensureProfileAndApplyStyle(draft)
                                    setDraft(next)
                                    ProfileAssistantApplier.applyCore(next)
                                    setStep(2)
                                }
                                2 -> {
                                    ProfileAssistantApplier.applyMaintenance(draft)
                                    setStep(3)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = when (step) {
                            0 -> draft.style != null && draft.displayName.trim().length >= 3
                            1 -> ProfileAssistantApplier.coreReady(draft, hasFarmSpot)
                            else -> true
                        },
                    )
                }
            }
        }
    }
}

private fun ensureStem(
    draft: AssistantDraft,
    update: (AssistantDraft) -> Unit,
): String? {
    val next = if (draft.profileFilename == null) {
        if (draft.style == null || draft.displayName.trim().length < 3) return null
        ProfileAssistantApplier.ensureProfileAndApplyStyle(draft).also(update)
    } else {
        draft
    }
    val filename = next.profileFilename ?: return null
    ProfileRepository.setCurrentProfile(filename)
    LocationRepository.refreshForCurrentProfile()
    return filename.removeSuffix(".json")
}

@Composable
private fun StepStyle(
    draft: AssistantDraft,
    onDraftChange: (AssistantDraft) -> Unit,
) {
    Text(
        text = stringResource(R.string.assistant_step0_title),
        color = ContextoColors.TextPrimary,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
    )
    ContextoDenseField(
        value = draft.displayName,
        onValueChange = { onDraftChange(draft.copy(displayName = it.take(40))) },
        modifier = Modifier.fillMaxWidth(),
        label = stringResource(R.string.profiles_name),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StyleIntentCard(
            title = stringResource(R.string.assistant_style_farm),
            subtitle = stringResource(R.string.assistant_style_farm_sub),
            selected = draft.style == AssistantStyle.FARM_ONLY,
            onClick = { onDraftChange(draft.copy(style = AssistantStyle.FARM_ONLY)) },
            modifier = Modifier.weight(1f),
        )
        StyleIntentCard(
            title = stringResource(R.string.assistant_style_bosses),
            subtitle = stringResource(R.string.assistant_style_bosses_sub),
            selected = draft.style == AssistantStyle.BOSSES_ONLY,
            onClick = { onDraftChange(draft.copy(style = AssistantStyle.BOSSES_ONLY)) },
            modifier = Modifier.weight(1f),
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StyleIntentCard(
            title = stringResource(R.string.assistant_style_rotation),
            subtitle = stringResource(R.string.assistant_style_rotation_sub),
            selected = draft.style == AssistantStyle.FARM_BOSSES_ROTATION,
            onClick = { onDraftChange(draft.copy(style = AssistantStyle.FARM_BOSSES_ROTATION)) },
            modifier = Modifier.weight(1f),
        )
        StyleIntentCard(
            title = stringResource(R.string.assistant_style_elf),
            subtitle = stringResource(R.string.assistant_style_elf_sub),
            selected = draft.style == AssistantStyle.ELF,
            onClick = { onDraftChange(draft.copy(style = AssistantStyle.ELF)) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StepCore(
    draft: AssistantDraft,
    onDraftChange: (AssistantDraft) -> Unit,
    farmSummary: String?,
    hasFarmSpot: Boolean,
    onPickFarmSpot: () -> Unit,
) {
    Text(
        text = stringResource(R.string.assistant_step1_title),
        color = ContextoColors.TextPrimary,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
    )
    when (draft.style) {
        AssistantStyle.FARM_ONLY -> {
            SpotCta(
                label = stringResource(R.string.profile_farm_spot),
                summary = farmSummary,
                onClick = onPickFarmSpot,
            )
        }
        AssistantStyle.BOSSES_ONLY -> {
            BossMapsEditor(draft = draft, onDraftChange = onDraftChange)
        }
        AssistantStyle.FARM_BOSSES_ROTATION -> {
            SpotCta(
                label = stringResource(R.string.profile_farm_spot),
                summary = farmSummary,
                onClick = onPickFarmSpot,
            )
            BossMapsEditor(draft = draft, onDraftChange = onDraftChange)
            Text(
                text = stringResource(R.string.profile_mode_rotation_title),
                color = ContextoColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ContextoChip(
                    label = stringResource(R.string.profile_mode_rotation_map_lap),
                    selected = draft.rotationStrategy == ModeRotationStrategy.MAP_LAP,
                    onClick = {
                        onDraftChange(draft.copy(rotationStrategy = ModeRotationStrategy.MAP_LAP))
                    },
                )
                ContextoChip(
                    label = stringResource(R.string.profile_mode_rotation_clock),
                    selected = draft.rotationStrategy == ModeRotationStrategy.CLOCK,
                    onClick = {
                        onDraftChange(draft.copy(rotationStrategy = ModeRotationStrategy.CLOCK))
                    },
                )
            }
            when (draft.rotationStrategy) {
                ModeRotationStrategy.MAP_LAP -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.profile_mode_rotation_rest_label),
                            color = ContextoColors.TextSecondary,
                            fontSize = 13.sp,
                        )
                        InlineStepper(
                            value = draft.restMinutes,
                            range = ModeRotationConfig.MIN_REST_MINUTES..ModeRotationConfig.MAX_REST_MINUTES,
                            step = 15,
                            onChange = { onDraftChange(draft.copy(restMinutes = it)) },
                        )
                    }
                    Text(
                        text = stringResource(R.string.profile_mode_rotation_map_lap_note),
                        color = ContextoColors.TextMuted,
                        fontSize = 11.sp,
                    )
                }
                ModeRotationStrategy.CLOCK -> {
                    ContextoDenseField(
                        value = draft.farmWindow,
                        onValueChange = {
                            onDraftChange(
                                draft.copy(farmWindow = ModeRotationConfig.formatHhMmInput(it)),
                            )
                        },
                        label = stringResource(R.string.profile_mode_rotation_spot_time),
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = "HH:MM",
                    )
                    ContextoDenseField(
                        value = draft.bossesWindow,
                        onValueChange = {
                            onDraftChange(
                                draft.copy(bossesWindow = ModeRotationConfig.formatHhMmInput(it)),
                            )
                        },
                        label = stringResource(R.string.profile_mode_rotation_bosses_time),
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = "HH:MM",
                    )
                }
            }
        }
        AssistantStyle.ELF -> {
            SpotCta(
                label = stringResource(R.string.profile_buff_post_title),
                summary = farmSummary,
                onClick = onPickFarmSpot,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ContextoChip(
                    label = stringResource(R.string.profile_elf_open_world),
                    selected = !draft.elfWar,
                    onClick = { onDraftChange(draft.copy(elfWar = false)) },
                )
                ContextoChip(
                    label = stringResource(R.string.profile_elf_war),
                    selected = draft.elfWar,
                    onClick = { onDraftChange(draft.copy(elfWar = true)) },
                )
            }
        }
        null -> Unit
    }
    if (!hasFarmSpot && draft.style != AssistantStyle.BOSSES_ONLY && draft.style != null) {
        Text(
            text = stringResource(R.string.assistant_need_spot),
            color = ContextoColors.Danger,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun StepMaintenance(
    draft: AssistantDraft,
    onDraftChange: (AssistantDraft) -> Unit,
    elfSummary: String?,
    onPickElfZone: () -> Unit,
) {
    Text(
        text = stringResource(R.string.assistant_step2_title),
        color = ContextoColors.TextPrimary,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
    )
    ContextoSurfaceCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.potion_auto_recovery),
                color = ContextoColors.TextPrimary,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            ContextoSwitch(
                checked = draft.enablePotions,
                onCheckedChange = { onDraftChange(draft.copy(enablePotions = it)) },
            )
        }
        if (draft.enablePotions) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("HP", color = ContextoColors.TextSecondary, fontSize = 13.sp)
                InlineStepper(
                    value = draft.hpStacks,
                    range = 1..99,
                    step = 1,
                    onChange = { onDraftChange(draft.copy(hpStacks = it)) },
                )
                Text("MP", color = ContextoColors.TextSecondary, fontSize = 13.sp)
                InlineStepper(
                    value = draft.mpStacks,
                    range = 1..99,
                    step = 1,
                    onChange = { onDraftChange(draft.copy(mpStacks = it)) },
                )
            }
        }
    }

    if (draft.style != AssistantStyle.ELF) {
        ContextoSurfaceCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.profile_enable_elf_buff),
                    color = ContextoColors.TextPrimary,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                ContextoSwitch(
                    checked = draft.enableElfSeek,
                    onCheckedChange = { onDraftChange(draft.copy(enableElfSeek = it)) },
                )
            }
            if (draft.enableElfSeek) {
                SpotCta(
                    label = stringResource(R.string.profile_elf_buff),
                    summary = elfSummary,
                    onClick = onPickElfZone,
                )
            }
        }
    }

    when (draft.style) {
        AssistantStyle.FARM_BOSSES_ROTATION -> {
            PetRow(
                title = stringResource(R.string.profile_pet_farm_title),
                enabled = draft.farmPetEnabled,
                type = draft.farmPetType,
                onEnabled = { onDraftChange(draft.copy(farmPetEnabled = it)) },
                onType = { onDraftChange(draft.copy(farmPetType = it)) },
            )
            PetRow(
                title = stringResource(R.string.profile_pet_bosses_title),
                enabled = draft.bossesPetEnabled,
                type = draft.bossesPetType,
                onEnabled = { onDraftChange(draft.copy(bossesPetEnabled = it)) },
                onType = { onDraftChange(draft.copy(bossesPetType = it)) },
            )
        }
        AssistantStyle.BOSSES_ONLY -> {
            PetRow(
                title = stringResource(R.string.profile_pet_bosses_title),
                enabled = draft.bossesPetEnabled,
                type = draft.bossesPetType,
                onEnabled = { onDraftChange(draft.copy(bossesPetEnabled = it)) },
                onType = { onDraftChange(draft.copy(bossesPetType = it)) },
            )
        }
        AssistantStyle.FARM_ONLY, AssistantStyle.ELF -> {
            PetRow(
                title = stringResource(R.string.profile_pet_title),
                enabled = draft.farmPetEnabled,
                type = draft.farmPetType,
                onEnabled = { onDraftChange(draft.copy(farmPetEnabled = it)) },
                onType = { onDraftChange(draft.copy(farmPetType = it)) },
            )
        }
        null -> Unit
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepCombat(
    draft: AssistantDraft,
    onDraftChange: (AssistantDraft) -> Unit,
    onSkip: () -> Unit,
) {
    Text(
        text = stringResource(R.string.assistant_step3_title),
        color = ContextoColors.TextPrimary,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
    )
    Text(
        text = stringResource(R.string.assistant_step3_hint),
        color = ContextoColors.TextSecondary,
        fontSize = 13.sp,
    )
    ContextoPrimaryButton(
        text = stringResource(R.string.assistant_skip_defaults),
        onClick = onSkip,
        modifier = Modifier.fillMaxWidth(),
    )
    if (draft.style == AssistantStyle.ELF) {
        Text(
            text = stringResource(R.string.assistant_combat_elf_na),
            color = ContextoColors.TextMuted,
            fontSize = 12.sp,
        )
        return
    }
    ContextoSurfaceCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.profile_combat_focus_title),
                color = ContextoColors.TextPrimary,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            ContextoSwitch(
                checked = draft.enableCombatFocus,
                onCheckedChange = { onDraftChange(draft.copy(enableCombatFocus = it)) },
            )
        }
        if (draft.enableCombatFocus) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CombatFocusPkMode.entries.forEach { mode ->
                    ContextoChip(
                        label = when (mode) {
                            CombatFocusPkMode.PEACE ->
                                stringResource(R.string.profile_combat_focus_pk_peace)
                            CombatFocusPkMode.TEAM ->
                                stringResource(R.string.profile_combat_focus_pk_team)
                            CombatFocusPkMode.UNION ->
                                stringResource(R.string.profile_combat_focus_pk_union)
                            CombatFocusPkMode.ALL ->
                                stringResource(R.string.profile_combat_focus_pk_all)
                        },
                        selected = draft.combatPkMode == mode,
                        onClick = { onDraftChange(draft.copy(combatPkMode = mode)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun StepSummary(
    draft: AssistantDraft,
    farmSummary: String?,
) {
    Text(
        text = stringResource(R.string.assistant_step4_title),
        color = ContextoColors.TextPrimary,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
    )
    val styleLabel = when (draft.style) {
        AssistantStyle.FARM_ONLY -> stringResource(R.string.assistant_style_farm)
        AssistantStyle.BOSSES_ONLY -> stringResource(R.string.assistant_style_bosses)
        AssistantStyle.FARM_BOSSES_ROTATION -> stringResource(R.string.assistant_style_rotation)
        AssistantStyle.ELF -> stringResource(R.string.assistant_style_elf)
        null -> "—"
    }
    Text(
        text = stringResource(R.string.assistant_summary_name, draft.displayName),
        color = ContextoColors.TextPrimary,
        fontSize = 14.sp,
    )
    Text(
        text = stringResource(R.string.assistant_summary_style, styleLabel),
        color = ContextoColors.TextPrimary,
        fontSize = 14.sp,
    )
    farmSummary?.let {
        Text(
            text = stringResource(R.string.assistant_summary_spot, it),
            color = ContextoColors.TextSecondary,
            fontSize = 13.sp,
        )
    }
    if (draft.bossMapIds.isNotEmpty()) {
        Text(
            text = stringResource(R.string.assistant_summary_maps, draft.bossMapIds.size),
            color = ContextoColors.TextSecondary,
            fontSize = 13.sp,
        )
    }
    if (draft.style == AssistantStyle.FARM_BOSSES_ROTATION) {
        Text(
            text = when (draft.rotationStrategy) {
                ModeRotationStrategy.MAP_LAP ->
                    stringResource(R.string.assistant_summary_ciclos, draft.restMinutes)
                ModeRotationStrategy.CLOCK ->
                    stringResource(
                        R.string.assistant_summary_clock,
                        draft.farmWindow,
                        draft.bossesWindow,
                    )
            },
            color = ContextoColors.TextSecondary,
            fontSize = 13.sp,
        )
        Text(
            text = stringResource(R.string.profile_summary_ciclos_map_lap_note),
            color = ContextoColors.TextMuted,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun SpotCta(
    label: String,
    summary: String?,
    onClick: () -> Unit,
) {
    ContextoSurfaceCard(
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            color = ContextoColors.TextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
        )
        Text(
            text = summary ?: stringResource(R.string.profiles_unset),
            color = ContextoColors.TextSecondary,
            fontSize = 13.sp,
        )
        Text(
            text = stringResource(R.string.assistant_tap_to_set),
            color = ContextoColors.Teal,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BossMapsEditor(
    draft: AssistantDraft,
    onDraftChange: (AssistantDraft) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    val pickerMaps = remember { MapDefinitionRepository.listForPicker() }
    val selected = draft.bossMapIds
    val suggestions = remember(searchQuery, selected, pickerMaps) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            emptyList()
        } else {
            pickerMaps
                .filter { map ->
                    map.id !in selected &&
                        (map.name.contains(q, ignoreCase = true) ||
                            map.id.contains(q, ignoreCase = true))
                }
                .take(8)
                .map { it.id to it.name }
        }
    }

    Text(
        text = stringResource(R.string.mode_farm_bosses),
        color = ContextoColors.TextPrimary,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
    )
    if (selected.isEmpty()) {
        Text(
            text = stringResource(R.string.profile_bosses_no_maps),
            color = ContextoColors.Danger,
            fontSize = 12.sp,
        )
    } else {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            selected.forEachIndexed { index, mapId ->
                val name = MapDefinitionRepository.getById(mapId)?.name ?: mapId
                ContextoChip(
                    label = "${index + 1}. $name ×",
                    selected = false,
                    onClick = {
                        onDraftChange(draft.copy(bossMapIds = selected - mapId))
                    },
                )
            }
        }
    }
    ContextoDenseSearchSelect(
        label = stringResource(R.string.profile_bosses_search),
        query = searchQuery,
        onQueryChange = { searchQuery = it },
        suggestions = suggestions,
        onSelect = { mapId ->
            onDraftChange(draft.copy(bossMapIds = selected + mapId))
            searchQuery = ""
        },
        modifier = Modifier.fillMaxWidth(),
        expanded = searchExpanded,
        onExpandedChange = { searchExpanded = it },
    )
}

@Composable
private fun PetRow(
    title: String,
    enabled: Boolean,
    type: PetType,
    onEnabled: (Boolean) -> Unit,
    onType: (PetType) -> Unit,
) {
    ContextoSurfaceCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = ContextoColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            ContextoSwitch(checked = enabled, onCheckedChange = onEnabled)
        }
        if (enabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ContextoChip(
                    label = stringResource(R.string.profile_pet_angel),
                    selected = type == PetType.ANGEL,
                    onClick = { onType(PetType.ANGEL) },
                )
                ContextoChip(
                    label = stringResource(R.string.profile_pet_imp),
                    selected = type == PetType.IMP,
                    onClick = { onType(PetType.IMP) },
                )
            }
        }
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
