package com.example.muamaizingbot.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.muamaizingbot.R
import com.example.muamaizingbot.maps.MapDefinitionRepository
import com.example.muamaizingbot.profile.BotProfile
import com.example.muamaizingbot.profile.LocationRepository
import com.example.muamaizingbot.profile.ProfileRepository
import com.example.muamaizingbot.profile.isElfBuffPostMode
import com.example.muamaizingbot.profile.isFarmBossesMode
import com.example.muamaizingbot.ui.settings.components.ContextoDenseField
import com.example.muamaizingbot.ui.theme.ContextoColors
import com.example.muamaizingbot.ui.theme.ContextoTheme

@Composable
fun ProfileListScreen(
    onConfigureProfile: (String) -> Unit,
    onCreateWithAssistant: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ContextoTheme {
        ProfileListScreenContent(
            onConfigureProfile = onConfigureProfile,
            onCreateWithAssistant = onCreateWithAssistant,
            onBack = onBack,
            modifier = modifier,
        )
    }
}

@Composable
private fun ProfileListScreenContent(
    onConfigureProfile: (String) -> Unit,
    onCreateWithAssistant: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val profiles by ProfileRepository.profiles.collectAsState()
    val currentProfile by ProfileRepository.currentProfile.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var newProfileName by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.profiles_title),
            color = ContextoColors.TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(profiles, key = { it.filename }) { profile ->
                val isActive = currentProfile?.filename == profile.filename
                val farmSpot = LocationRepository.getFarmSpot(profile.filename)
                val farmLabel = farmSpot?.summaryLabel(
                    MapDefinitionRepository.getById(farmSpot.map)?.name,
                ) ?: stringResource(R.string.profiles_unset)
                val modeLabel = when {
                    profile.modeRotation.enabled -> stringResource(R.string.profile_mode_rotation_map_lap)
                    profile.isFarmBossesMode() -> stringResource(R.string.profile_mode_bosses)
                    profile.isElfBuffPostMode() -> stringResource(R.string.profile_mode_elf)
                    else -> stringResource(R.string.profile_mode_farm)
                }
                ProfileListCard(
                    profile = profile,
                    isActive = isActive,
                    summary = stringResource(
                        R.string.profiles_summary_line,
                        modeLabel,
                        farmLabel,
                        profile.killBossesConfig.maps.size,
                    ),
                    onConfigure = {
                        ProfileRepository.setCurrentProfile(profile.filename)
                        LocationRepository.refreshForCurrentProfile()
                        onConfigureProfile(profile.fileStem)
                    },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onCreateWithAssistant,
                modifier = Modifier
                    .weight(1.2f)
                    .height(44.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ContextoColors.Teal,
                    contentColor = ContextoColors.Bg,
                ),
            ) {
                Text(
                    text = stringResource(R.string.profiles_create_assistant),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                )
            }
            OutlinedButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
            ) {
                Text(
                    text = stringResource(R.string.profiles_create_empty),
                    fontSize = 12.sp,
                    color = ContextoColors.TextPrimary,
                )
            }
        }

        OutlinedButton(
            onClick = {
                currentProfile?.let { ProfileRepository.duplicateProfile(it.filename) }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = currentProfile != null,
        ) {
            Text(stringResource(R.string.profiles_duplicate), color = ContextoColors.TextSecondary)
        }

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.action_back), color = ContextoColors.TextSecondary)
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(stringResource(R.string.profiles_create_title)) },
            text = {
                ContextoDenseField(
                    value = newProfileName,
                    onValueChange = { newProfileName = it },
                    label = stringResource(R.string.profiles_name),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = newProfileName.trim()
                        if (name.length >= 3) {
                            ProfileRepository.createProfile(name)
                            LocationRepository.refreshForCurrentProfile()
                            newProfileName = ""
                            showCreateDialog = false
                        }
                    },
                ) {
                    Text(stringResource(R.string.profiles_create))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text(stringResource(R.string.profiles_cancel))
                }
            },
        )
    }
}

@Composable
private fun ProfileListCard(
    profile: BotProfile,
    isActive: Boolean,
    summary: String,
    onConfigure: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ContextoColors.Surface, RoundedCornerShape(12.dp))
            .then(
                if (isActive) {
                    Modifier.border(1.dp, ContextoColors.Teal, RoundedCornerShape(12.dp))
                } else {
                    Modifier.border(1.dp, ContextoColors.Border, RoundedCornerShape(12.dp))
                },
            )
            .clickable(onClick = onConfigure)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = profile.displayName,
                    color = ContextoColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isActive) {
                    Text(
                        text = stringResource(R.string.profiles_active_badge),
                        color = ContextoColors.Bg,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(ContextoColors.Teal, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                text = summary,
                color = ContextoColors.TextMuted,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "›",
            color = ContextoColors.TextSecondary,
            fontSize = 22.sp,
        )
    }
}
