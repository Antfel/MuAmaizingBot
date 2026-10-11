package com.example.muamaizingbot.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.muamaizingbot.R
import com.example.muamaizingbot.profile.DevilSquareConfig
import com.example.muamaizingbot.profile.PetType
import com.example.muamaizingbot.profile.ProfileRepository
import com.example.muamaizingbot.ui.settings.components.ContextoDenseField
import com.example.muamaizingbot.ui.settings.components.ContextoDenseSelect
import com.example.muamaizingbot.ui.settings.components.ContextoPrimaryButton
import com.example.muamaizingbot.ui.settings.components.ContextoSecondaryButton
import com.example.muamaizingbot.ui.settings.components.ContextoSectionTitle
import com.example.muamaizingbot.ui.settings.components.ContextoSurfaceCard
import com.example.muamaizingbot.ui.theme.ContextoColors
import com.example.muamaizingbot.ui.theme.ContextoTheme

@Composable
fun DevilSquareConfigScreen(
    profileStem: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ContextoTheme {
        DevilSquareConfigScreenContent(
            profileStem = profileStem,
            onBack = onBack,
            modifier = modifier,
        )
    }
}

@Composable
private fun DevilSquareConfigScreenContent(
    profileStem: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val profileFilename = "$profileStem.json"
    val profile = ProfileRepository.getProfile(profileFilename)
    val initial = profile?.devilSquare ?: DevilSquareConfig()

    var petType by remember(profileFilename) { mutableStateOf(initial.petType) }
    var buyPotions by remember(profileFilename) { mutableStateOf(initial.buyPotions) }
    var hpStacks by remember(profileFilename) {
        mutableIntStateOf(initial.hpPotionStacks)
    }
    var mpStacks by remember(profileFilename) {
        mutableIntStateOf(initial.mpPotionStacks)
    }
    var prepElfBuff by remember(profileFilename) { mutableStateOf(initial.prepElfBuff) }
    var statusMessage by remember { mutableStateOf("") }
    val profileMissing = stringResource(R.string.potion_profile_missing)
    val savedMessage = stringResource(R.string.potion_saved)

    val petTypes = PetType.entries
    val petLabels = listOf(
        stringResource(R.string.profile_pet_angel),
        stringResource(R.string.profile_pet_imp),
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ContextoSectionTitle(
            title = stringResource(R.string.profile_devil_square_edit_title),
            subtitle = profile?.displayName ?: profileStem,
        )

        ContextoSurfaceCard {
            Text(
                text = stringResource(R.string.profile_devil_square_pet),
                color = ContextoColors.TextPrimary,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
            )
            Text(
                text = stringResource(R.string.profile_devil_square_pet_hint),
                color = ContextoColors.TextMuted,
                fontSize = 11.sp,
            )
            ContextoDenseSelect(
                label = null,
                value = petLabels[petTypes.indexOf(petType).coerceAtLeast(0)],
                options = petTypes.mapIndexed { i, type -> type.name to petLabels[i] },
                onSelect = { id ->
                    PetType.entries.firstOrNull { it.name == id }?.let { petType = it }
                },
            )
        }

        ContextoSurfaceCard {
            SettingSwitch(
                title = stringResource(R.string.profile_devil_square_buy_potions),
                hint = stringResource(R.string.profile_devil_square_buy_potions_hint),
                checked = buyPotions,
                onCheckedChange = { buyPotions = it },
            )
            if (buyPotions) {
                ContextoDenseField(
                    value = hpStacks.toString(),
                    onValueChange = { text ->
                        text.toIntOrNull()?.let {
                            hpStacks = it.coerceIn(
                                DevilSquareConfig.MIN_POTION_STACKS,
                                DevilSquareConfig.MAX_POTION_STACKS,
                            )
                        }
                    },
                    label = stringResource(R.string.potion_stacks_hp),
                    keyboardType = KeyboardType.Number,
                )
                ContextoDenseField(
                    value = mpStacks.toString(),
                    onValueChange = { text ->
                        text.toIntOrNull()?.let {
                            mpStacks = it.coerceIn(
                                DevilSquareConfig.MIN_POTION_STACKS,
                                DevilSquareConfig.MAX_POTION_STACKS,
                            )
                        }
                    },
                    label = stringResource(R.string.potion_stacks_mp),
                    keyboardType = KeyboardType.Number,
                )
            }
            SettingSwitch(
                title = stringResource(R.string.profile_devil_square_prep_elf),
                hint = stringResource(R.string.profile_devil_square_prep_elf_hint),
                checked = prepElfBuff,
                onCheckedChange = { prepElfBuff = it },
            )
        }

        if (statusMessage.isNotEmpty()) {
            Text(text = statusMessage, color = ContextoColors.Teal, fontSize = 12.sp)
        }

        ContextoPrimaryButton(
            text = stringResource(R.string.action_save),
            onClick = {
                if (profile == null) {
                    statusMessage = profileMissing
                    return@ContextoPrimaryButton
                }
                ProfileRepository.setDevilSquareConfig(
                    profileFilename,
                    DevilSquareConfig(
                        enabled = profile.devilSquare.enabled,
                        petType = petType,
                        buyPotions = buyPotions,
                        hpPotionStacks = hpStacks,
                        mpPotionStacks = mpStacks,
                        prepElfBuff = prepElfBuff,
                    ),
                )
                statusMessage = savedMessage
            },
            enabled = profile != null,
            modifier = Modifier.fillMaxWidth(),
        )
        ContextoSecondaryButton(
            text = stringResource(R.string.action_back),
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    hint: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = ContextoColors.TextPrimary,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
            )
            Text(text = hint, color = ContextoColors.TextMuted, fontSize = 11.sp)
        }
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
}
