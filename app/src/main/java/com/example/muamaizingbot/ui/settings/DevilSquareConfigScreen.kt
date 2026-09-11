package com.example.muamaizingbot.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import com.example.muamaizingbot.R
import com.example.muamaizingbot.profile.DevilSquareConfig
import com.example.muamaizingbot.profile.PetType
import com.example.muamaizingbot.profile.ProfileRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevilSquareConfigScreen(
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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.profile_devil_square_edit_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = profile?.displayName ?: profileStem,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            text = stringResource(R.string.profile_devil_square_pet),
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = stringResource(R.string.profile_devil_square_pet_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PetTypeDropdown(
            selected = petLabels[petTypes.indexOf(petType).coerceAtLeast(0)],
            options = petLabels,
            onSelect = { petType = petTypes[it] },
        )

        SettingSwitch(
            title = stringResource(R.string.profile_devil_square_buy_potions),
            hint = stringResource(R.string.profile_devil_square_buy_potions_hint),
            checked = buyPotions,
            onCheckedChange = { buyPotions = it },
        )
        if (buyPotions) {
            StackField(
                label = stringResource(R.string.potion_stacks_hp),
                value = hpStacks,
                onValueChange = {
                    hpStacks = it.coerceIn(
                        DevilSquareConfig.MIN_POTION_STACKS,
                        DevilSquareConfig.MAX_POTION_STACKS,
                    )
                },
            )
            StackField(
                label = stringResource(R.string.potion_stacks_mp),
                value = mpStacks,
                onValueChange = {
                    mpStacks = it.coerceIn(
                        DevilSquareConfig.MIN_POTION_STACKS,
                        DevilSquareConfig.MAX_POTION_STACKS,
                    )
                },
            )
        }

        SettingSwitch(
            title = stringResource(R.string.profile_devil_square_prep_elf),
            hint = stringResource(R.string.profile_devil_square_prep_elf_hint),
            checked = prepElfBuff,
            onCheckedChange = { prepElfBuff = it },
        )

        if (statusMessage.isNotEmpty()) {
            Text(
                text = statusMessage,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        TextButton(
            onClick = {
                if (profile == null) {
                    statusMessage = profileMissing
                    return@TextButton
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
            modifier = Modifier.fillMaxWidth(),
            enabled = profile != null,
        ) {
            Text(stringResource(R.string.action_save))
        }
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.action_back))
        }
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
            Text(text = title, fontWeight = FontWeight.Medium)
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PetTypeDropdown(
    selected: String,
    options: List<String>,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
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
                    text = { Text(option) },
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
private fun StackField(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { text ->
            text.toIntOrNull()?.let(onValueChange)
        },
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}
