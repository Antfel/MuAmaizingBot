package com.example.muamaizingbot.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.muamaizingbot.maps.MapDefinitionRepository
import com.example.muamaizingbot.profile.BotProfile
import com.example.muamaizingbot.profile.LocationRepository
import com.example.muamaizingbot.profile.ProfileRepository

@Composable
fun ProfileListScreen(
    onConfigureProfile: (String) -> Unit,
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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Perfiles",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = "Selecciona el perfil activo. Usa Configurar para farm spot, elf buff y pociones.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(profiles, key = { it.filename }) { profile ->
                ProfileCard(
                    profile = profile,
                    isActive = currentProfile?.filename == profile.filename,
                    farmSummary = LocationRepository.getFarmSpot(profile.filename)?.summaryLabel(
                        MapDefinitionRepository.getById(
                            LocationRepository.getFarmSpot(profile.filename)?.map.orEmpty()
                        )?.name
                    ),
                    elfSummary = LocationRepository.getElfBuff(profile.filename)?.summaryLabel(
                        MapDefinitionRepository.getById(
                            LocationRepository.getElfBuff(profile.filename)?.map.orEmpty()
                        )?.name
                    ),
                    onSelect = {
                        ProfileRepository.setCurrentProfile(profile.filename)
                        LocationRepository.refreshForCurrentProfile()
                    },
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
                onClick = { showCreateDialog = true },
                modifier = Modifier.weight(1f),
            ) {
                Text("Nuevo perfil")
            }

            OutlinedButton(
                onClick = {
                    currentProfile?.let { ProfileRepository.duplicateProfile(it.filename) }
                },
                modifier = Modifier.weight(1f),
                enabled = currentProfile != null,
            ) {
                Text("Duplicar")
            }
        }

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Volver")
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Nuevo perfil") },
            text = {
                OutlinedTextField(
                    value = newProfileName,
                    onValueChange = { newProfileName = it },
                    label = { Text("Nombre") },
                    singleLine = true,
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
                    Text("Crear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

@Composable
private fun ProfileCard(
    profile: BotProfile,
    isActive: Boolean,
    farmSummary: String?,
    elfSummary: String?,
    onSelect: () -> Unit,
    onConfigure: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RadioButton(selected = isActive, onClick = onSelect)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.displayName,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Mapa: ${profile.map.ifBlank { "-" }} | Wire ${profile.wire}",
                    style = MaterialTheme.typography.bodySmall,
                )
                farmSummary?.let {
                    Text(
                        text = "Farm: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                elfSummary?.let {
                    Text(
                        text = "Elf: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (profile.enableElfBuff && elfSummary == null) {
                    Text(
                        text = "Elf buff: sin configurar",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            TextButton(onClick = onConfigure) {
                Text("Configurar")
            }
        }
    }
}
