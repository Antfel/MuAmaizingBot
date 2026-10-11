package com.example.muamaizingbot.ui.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.muamaizingbot.R
import com.example.muamaizingbot.maps.MapDefinitionRepository
import com.example.muamaizingbot.profile.BotProfile
import com.example.muamaizingbot.profile.FarmLocation
import com.example.muamaizingbot.profile.ModeRotationStrategy
import com.example.muamaizingbot.profile.PetType
import com.example.muamaizingbot.profile.isElfBuffPostMode
import com.example.muamaizingbot.profile.isFarmBossesMode
import com.example.muamaizingbot.ui.theme.ContextoColors

private val ChipShape = RoundedCornerShape(20.dp)

data class ProfileSummaryChip(
    val kind: Kind,
    val primary: String,
    val secondary: String? = null,
    val accent: Accent,
) {
    enum class Kind { CICLOS, SPOT, BOSSES }
    enum class Accent { TEAL, GOLD }
}

/**
 * Builds the three summary chips and optional Ciclos footnote.
 * Visibility follows mode + Programación (P0 dependencies).
 */
object ProfileSummaryModel {

    fun chips(
        profile: BotProfile?,
        farmSpot: FarmLocation?,
        ciclosOffLabel: String,
        ciclosMapLapLabel: (Int) -> String,
        ciclosClockLabel: String,
        spotUnset: String,
        spotPrimary: (String) -> String,
        petOff: String,
        petLine: (String) -> String,
        bossesUnset: String,
        bossesPrimary: (Int) -> String,
        petAngel: String,
        petImp: String,
    ): List<ProfileSummaryChip> {
        if (profile == null) return emptyList()
        val rotation = profile.modeRotation
        val rotationOn = rotation.enabled
        val bossesMode = profile.isFarmBossesMode()
        val elfMode = profile.isElfBuffPostMode()
        val out = mutableListOf<ProfileSummaryChip>()

        if (rotationOn) {
            val primary = when (rotation.strategy) {
                ModeRotationStrategy.MAP_LAP -> ciclosMapLapLabel(rotation.restMinutes)
                ModeRotationStrategy.CLOCK -> ciclosClockLabel
            }
            out += ProfileSummaryChip(
                kind = ProfileSummaryChip.Kind.CICLOS,
                primary = primary,
                accent = ProfileSummaryChip.Accent.TEAL,
            )
        }

        val showSpot = rotationOn || elfMode || !bossesMode
        if (showSpot) {
            val mapLabel = farmSpot?.let { spot ->
                MapDefinitionRepository.getById(spot.map)?.name ?: spot.map
            }
            val primary = if (farmSpot != null && !mapLabel.isNullOrBlank()) {
                spotPrimary(shortMapWire(mapLabel, farmSpot.wire))
            } else {
                spotUnset
            }
            val farmPet = if (profile.enablePet) {
                petLine(petName(profile.petType, petAngel, petImp))
            } else {
                petOff
            }
            out += ProfileSummaryChip(
                kind = ProfileSummaryChip.Kind.SPOT,
                primary = primary,
                secondary = farmPet,
                accent = ProfileSummaryChip.Accent.GOLD,
            )
        }

        val showBosses = rotationOn || bossesMode
        if (showBosses) {
            val maps = profile.killBossesConfig.maps
            val primary = if (maps.isEmpty()) {
                bossesUnset
            } else {
                bossesPrimary(maps.size)
            }
            val bossesPetCfg = profile.killBossesConfig.pet
            val bossesPet = if (bossesPetCfg.enablePet) {
                petLine(petName(bossesPetCfg.petType, petAngel, petImp))
            } else {
                petOff
            }
            out += ProfileSummaryChip(
                kind = ProfileSummaryChip.Kind.BOSSES,
                primary = primary,
                secondary = bossesPet,
                accent = ProfileSummaryChip.Accent.GOLD,
            )
        }

        return out
    }

    fun ciclosFootnote(
        profile: BotProfile?,
        mapLapNote: String,
        clockNote: String,
    ): String? {
        val rotation = profile?.modeRotation ?: return null
        if (!rotation.enabled) return null
        return when (rotation.strategy) {
            ModeRotationStrategy.MAP_LAP -> mapLapNote
            ModeRotationStrategy.CLOCK -> clockNote
        }
    }

    private fun petName(type: PetType, angel: String, imp: String): String =
        when (type) {
            PetType.ANGEL -> angel
            PetType.IMP -> imp
        }

    /** "Corrupted Lands" + wire 3 → "Corrupted w3". */
    fun shortMapWire(mapName: String, wire: Int): String {
        val short = mapName.trim().substringBefore(' ').ifBlank { mapName.trim() }
        return "$short w$wire"
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileSummaryChipRow(
    chips: List<ProfileSummaryChip>,
    footnote: String?,
    onChipClick: (ProfileSummaryChip.Kind) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (chips.isEmpty() && footnote.isNullOrBlank()) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            chips.forEach { chip ->
                SummaryChipPill(
                    chip = chip,
                    onClick = { onChipClick(chip.kind) },
                )
            }
        }
        if (!footnote.isNullOrBlank()) {
            Text(
                text = footnote,
                style = MaterialTheme.typography.bodySmall,
                color = ContextoColors.TextMuted,
            )
        }
    }
}

@Composable
private fun SummaryChipPill(
    chip: ProfileSummaryChip,
    onClick: () -> Unit,
) {
    val accent = when (chip.accent) {
        ProfileSummaryChip.Accent.TEAL -> ContextoColors.Teal
        ProfileSummaryChip.Accent.GOLD -> ContextoColors.Amber
    }
    val icon: ImageVector = when (chip.kind) {
        ProfileSummaryChip.Kind.CICLOS -> Icons.Outlined.Schedule
        ProfileSummaryChip.Kind.SPOT -> Icons.Outlined.LocationOn
        ProfileSummaryChip.Kind.BOSSES -> Icons.Outlined.Warning
    }
    Row(
        modifier = Modifier
            .border(1.dp, accent, ChipShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(16.dp),
        )
        Column {
            Text(
                text = chip.primary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (chip.accent == ProfileSummaryChip.Accent.TEAL) {
                    accent
                } else {
                    ContextoColors.TextPrimary
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            chip.secondary?.let { secondary ->
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.labelSmall,
                    color = ContextoColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun rememberProfileSummaryChips(
    profile: BotProfile?,
    farmSpot: FarmLocation?,
): Pair<List<ProfileSummaryChip>, String?> {
    val context = LocalContext.current
    val petAngel = stringResource(R.string.profile_pet_angel)
    val petImp = stringResource(R.string.profile_pet_imp)
    val chips = ProfileSummaryModel.chips(
        profile = profile,
        farmSpot = farmSpot,
        ciclosOffLabel = "",
        ciclosMapLapLabel = { min ->
            context.getString(R.string.profile_summary_ciclos_map_lap, min)
        },
        ciclosClockLabel = stringResource(R.string.profile_summary_ciclos_clock),
        spotUnset = stringResource(R.string.profile_summary_spot_unset),
        spotPrimary = { short ->
            context.getString(R.string.profile_summary_spot, short)
        },
        petOff = stringResource(R.string.profile_summary_pet_off),
        petLine = { name ->
            context.getString(R.string.profile_summary_pet_line, name)
        },
        bossesUnset = stringResource(R.string.profile_summary_bosses_unset),
        bossesPrimary = { n ->
            context.getString(R.string.profile_summary_bosses, n)
        },
        petAngel = petAngel,
        petImp = petImp,
    )
    val footnote = ProfileSummaryModel.ciclosFootnote(
        profile = profile,
        mapLapNote = stringResource(R.string.profile_summary_ciclos_map_lap_note),
        clockNote = stringResource(R.string.profile_summary_ciclos_clock_note),
    )
    return chips to footnote
}
