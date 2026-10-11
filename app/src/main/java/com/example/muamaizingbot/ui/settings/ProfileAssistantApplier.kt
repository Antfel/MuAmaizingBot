package com.example.muamaizingbot.ui.settings

import com.example.muamaizingbot.profile.BotMode
import com.example.muamaizingbot.profile.BotProfile
import com.example.muamaizingbot.profile.CombatFocusPkMode
import com.example.muamaizingbot.profile.KillBossesConfig
import com.example.muamaizingbot.profile.ModeRotationConfig
import com.example.muamaizingbot.profile.ModeRotationStrategy
import com.example.muamaizingbot.profile.PetConfig
import com.example.muamaizingbot.profile.PetType
import com.example.muamaizingbot.profile.ProfileRepository
import com.example.muamaizingbot.profile.isElfBuffPostMode
import com.example.muamaizingbot.profile.isElfBuffWarMode
import com.example.muamaizingbot.profile.isFarmBossesMode

/**
 * Intent templates for the config assistant (P1).
 * Maps to existing [BotProfile] fields — no new JSON schema.
 */
enum class AssistantStyle {
    FARM_ONLY,
    BOSSES_ONLY,
    FARM_BOSSES_ROTATION,
    ELF,
}

data class AssistantDraft(
    val displayName: String = "",
    val style: AssistantStyle? = null,
    val profileFilename: String? = null,
    /** Rotation: MAP_LAP vs CLOCK (only when [AssistantStyle.FARM_BOSSES_ROTATION]). */
    val rotationStrategy: ModeRotationStrategy = ModeRotationStrategy.MAP_LAP,
    val restMinutes: Int = ModeRotationConfig.DEFAULT_REST_MINUTES,
    val farmWindow: String = "08:00",
    val bossesWindow: String = "14:00",
    val bossMapIds: List<String> = emptyList(),
    val elfWar: Boolean = false,
    val enablePotions: Boolean = true,
    val hpStacks: Int = 10,
    val mpStacks: Int = 10,
    val enableElfSeek: Boolean = true,
    val farmPetEnabled: Boolean = false,
    val farmPetType: PetType = PetType.DEFAULT,
    val bossesPetEnabled: Boolean = false,
    val bossesPetType: PetType = PetType.DEFAULT,
    val enableCombatFocus: Boolean = false,
    val combatPkMode: CombatFocusPkMode = CombatFocusPkMode.DEFAULT,
)

object ProfileAssistantApplier {

    fun draftFromProfile(profile: BotProfile): AssistantDraft {
        val rotation = profile.modeRotation
        val style = when {
            rotation.enabled -> AssistantStyle.FARM_BOSSES_ROTATION
            profile.isElfBuffPostMode() -> AssistantStyle.ELF
            profile.isFarmBossesMode() -> AssistantStyle.BOSSES_ONLY
            else -> AssistantStyle.FARM_ONLY
        }
        return AssistantDraft(
            displayName = profile.displayName,
            style = style,
            profileFilename = profile.filename,
            rotationStrategy = rotation.strategy,
            restMinutes = rotation.restMinutes,
            farmWindow = ModeRotationConfig.primaryTime(rotation.farmWindows)
                .ifBlank { "08:00" },
            bossesWindow = ModeRotationConfig.primaryTime(rotation.bossesWindows)
                .ifBlank { "14:00" },
            bossMapIds = profile.killBossesConfig.maps,
            elfWar = profile.isElfBuffWarMode(),
            enablePotions = profile.enablePotionRecovery,
            hpStacks = profile.hpPotionStacks,
            mpStacks = profile.mpPotionStacks,
            enableElfSeek = profile.enableElfBuff,
            farmPetEnabled = profile.enablePet,
            farmPetType = profile.petType,
            bossesPetEnabled = profile.killBossesConfig.pet.enablePet,
            bossesPetType = profile.killBossesConfig.pet.petType,
            enableCombatFocus = profile.enableCombatFocus,
            combatPkMode = profile.combatFocusPkMode,
        )
    }

    fun botModeFor(style: AssistantStyle, elfWar: Boolean): String = when (style) {
        AssistantStyle.FARM_ONLY -> BotMode.FARM
        AssistantStyle.BOSSES_ONLY -> BotMode.FARM_BOSSES
        AssistantStyle.FARM_BOSSES_ROTATION -> BotMode.FARM_BOSSES
        AssistantStyle.ELF -> if (elfWar) BotMode.ELF_BUFF_WAR else BotMode.ELF_BUFF_GIVER
    }

    fun modeRotationFor(draft: AssistantDraft): ModeRotationConfig {
        if (draft.style != AssistantStyle.FARM_BOSSES_ROTATION) {
            return ModeRotationConfig(enabled = false)
        }
        return ModeRotationConfig(
            enabled = true,
            strategy = draft.rotationStrategy,
            restMinutes = draft.restMinutes.coerceIn(
                ModeRotationConfig.MIN_REST_MINUTES,
                ModeRotationConfig.MAX_REST_MINUTES,
            ),
            farmWindows = listOf(draft.farmWindow).filter { ModeRotationConfig.isValidHhMm(it) },
            bossesWindows = listOf(draft.bossesWindow).filter { ModeRotationConfig.isValidHhMm(it) },
            segment = ModeRotationConfig.SEGMENT_BOSSES,
        )
    }

    /** Creates the profile (if needed) and applies style + core mode fields. */
    fun ensureProfileAndApplyStyle(draft: AssistantDraft): AssistantDraft {
        val style = draft.style ?: return draft
        val name = draft.displayName.trim().ifBlank { "Perfil" }
        val filename = draft.profileFilename ?: ProfileRepository.createProfile(name).filename
        ProfileRepository.setBotMode(filename, botModeFor(style, draft.elfWar))
        ProfileRepository.setModeRotationConfig(filename, modeRotationFor(draft))
        return draft.copy(
            displayName = name,
            profileFilename = filename,
        )
    }

    fun applyCore(draft: AssistantDraft) {
        val filename = draft.profileFilename ?: return
        val style = draft.style ?: return
        ProfileRepository.setBotMode(filename, botModeFor(style, draft.elfWar))
        ProfileRepository.setModeRotationConfig(filename, modeRotationFor(draft))
        when (style) {
            AssistantStyle.BOSSES_ONLY,
            AssistantStyle.FARM_BOSSES_ROTATION,
            -> {
                val profile = ProfileRepository.getProfile(filename) ?: return
                ProfileRepository.setKillBossesConfig(
                    filename,
                    profile.killBossesConfig.copy(maps = draft.bossMapIds.distinct()),
                )
            }
            else -> Unit
        }
    }

    fun applyMaintenance(draft: AssistantDraft) {
        val filename = draft.profileFilename ?: return
        val profile = ProfileRepository.getProfile(filename) ?: return
        val farmPet = PetConfig(
            enablePet = draft.farmPetEnabled,
            petType = draft.farmPetType,
            petCheckIntervalMinutes = profile.petCheckIntervalMinutes,
        )
        val bossesPet = PetConfig(
            enablePet = draft.bossesPetEnabled,
            petType = draft.bossesPetType,
            petCheckIntervalMinutes = profile.killBossesConfig.pet.petCheckIntervalMinutes,
        )
        ProfileRepository.saveProfile(
            profile.copy(
                enablePotionRecovery = draft.enablePotions,
                hpPotionStacks = draft.hpStacks.coerceIn(1, 99),
                mpPotionStacks = draft.mpStacks.coerceIn(1, 99),
                enableElfBuff = when (draft.style) {
                    AssistantStyle.ELF -> false // giver/war never seek NPC
                    else -> draft.enableElfSeek
                },
                enablePet = farmPet.enablePet,
                petType = farmPet.petType,
                killBossesConfig = profile.killBossesConfig.copy(
                    maps = when (draft.style) {
                        AssistantStyle.BOSSES_ONLY,
                        AssistantStyle.FARM_BOSSES_ROTATION,
                        -> draft.bossMapIds.distinct()
                        else -> profile.killBossesConfig.maps
                    },
                    pet = bossesPet,
                ),
            ),
        )
    }

    fun applyCombat(draft: AssistantDraft) {
        val filename = draft.profileFilename ?: return
        ProfileRepository.setCombatFocusEnabled(filename, draft.enableCombatFocus)
        if (draft.enableCombatFocus) {
            ProfileRepository.setCombatFocusPkMode(filename, draft.combatPkMode)
        }
    }

    fun applyAll(draft: AssistantDraft): BotProfile? {
        val withProfile = ensureProfileAndApplyStyle(draft)
        applyCore(withProfile)
        applyMaintenance(withProfile)
        applyCombat(withProfile)
        return withProfile.profileFilename?.let { ProfileRepository.getProfile(it) }
    }

    fun coreReady(draft: AssistantDraft, hasFarmSpot: Boolean): Boolean {
        return when (draft.style) {
            AssistantStyle.FARM_ONLY -> hasFarmSpot
            AssistantStyle.BOSSES_ONLY -> draft.bossMapIds.isNotEmpty()
            AssistantStyle.FARM_BOSSES_ROTATION -> {
                val scheduleOk = when (draft.rotationStrategy) {
                    ModeRotationStrategy.MAP_LAP -> true
                    ModeRotationStrategy.CLOCK ->
                        ModeRotationConfig.isValidHhMm(draft.farmWindow) &&
                            ModeRotationConfig.isValidHhMm(draft.bossesWindow)
                }
                hasFarmSpot && draft.bossMapIds.isNotEmpty() && scheduleOk
            }
            AssistantStyle.ELF -> hasFarmSpot
            null -> false
        }
    }
}
