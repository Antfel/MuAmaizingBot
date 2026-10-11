package com.example.muamaizingbot.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.example.muamaizingbot.profile.ProfileRepository
import com.example.muamaizingbot.ui.settings.components.ContextoDenseField
import com.example.muamaizingbot.ui.settings.components.ContextoPrimaryButton
import com.example.muamaizingbot.ui.settings.components.ContextoSecondaryButton
import com.example.muamaizingbot.ui.settings.components.ContextoSectionTitle
import com.example.muamaizingbot.ui.settings.components.ContextoSurfaceCard
import com.example.muamaizingbot.ui.theme.ContextoColors
import com.example.muamaizingbot.ui.theme.ContextoTheme

@Composable
fun PotionConfigScreen(
    profileStem: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ContextoTheme {
        PotionConfigScreenContent(
            profileStem = profileStem,
            onBack = onBack,
            modifier = modifier,
        )
    }
}

@Composable
private fun PotionConfigScreenContent(
    profileStem: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val profileFilename = "$profileStem.json"
    val profile = ProfileRepository.getProfile(profileFilename)

    var enableRecovery by remember(profileFilename) {
        mutableStateOf(profile?.enablePotionRecovery ?: true)
    }
    var hpStacks by remember(profileFilename) {
        mutableIntStateOf(profile?.hpPotionStacks ?: 10)
    }
    var mpStacks by remember(profileFilename) {
        mutableIntStateOf(profile?.mpPotionStacks ?: 10)
    }
    var statusMessage by remember { mutableStateOf("") }
    val profileMissing = stringResource(R.string.potion_profile_missing)
    val savedMessage = stringResource(R.string.potion_saved)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ContextoSectionTitle(
            title = stringResource(R.string.potion_config_title),
            subtitle = profile?.displayName ?: profileStem,
        )

        ContextoSurfaceCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.potion_auto_recovery),
                        color = ContextoColors.TextPrimary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                    )
                    Text(
                        text = stringResource(R.string.potion_auto_recovery_hint),
                        color = ContextoColors.TextMuted,
                        fontSize = 11.sp,
                    )
                }
                Switch(
                    checked = enableRecovery,
                    onCheckedChange = { enableRecovery = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ContextoColors.Bg,
                        checkedTrackColor = ContextoColors.Teal,
                        uncheckedThumbColor = ContextoColors.TextSecondary,
                        uncheckedTrackColor = ContextoColors.Border,
                    ),
                )
            }
            ContextoDenseField(
                value = hpStacks.toString(),
                onValueChange = { text ->
                    text.toIntOrNull()?.let { hpStacks = it.coerceIn(1, 99) }
                },
                label = stringResource(R.string.potion_stacks_hp),
                keyboardType = KeyboardType.Number,
            )
            ContextoDenseField(
                value = mpStacks.toString(),
                onValueChange = { text ->
                    text.toIntOrNull()?.let { mpStacks = it.coerceIn(1, 99) }
                },
                label = stringResource(R.string.potion_stacks_mp),
                keyboardType = KeyboardType.Number,
            )
        }

        if (statusMessage.isNotEmpty()) {
            Text(text = statusMessage, color = ContextoColors.Teal, fontSize = 12.sp)
        }

        ContextoPrimaryButton(
            text = stringResource(R.string.action_save),
            onClick = {
                val current = profile
                if (current == null) {
                    statusMessage = profileMissing
                    return@ContextoPrimaryButton
                }
                ProfileRepository.saveProfile(
                    current.copy(
                        enablePotionRecovery = enableRecovery,
                        hpPotionStacks = hpStacks,
                        mpPotionStacks = mpStacks,
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
