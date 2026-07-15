package com.example.muamaizingbot.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import com.example.muamaizingbot.profile.LocationRepository
import com.example.muamaizingbot.profile.ProfileRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileConfigureScreen(
    profileStem: String,
    onOpenFarmSpot: () -> Unit,
    onOpenElfBuff: () -> Unit,
    onOpenPotionConfig: () -> Unit,
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Configurar perfil",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = profile?.displayName ?: profileStem,
            style = MaterialTheme.typography.titleMedium,
        )

        Text(
            text = "Auto ataque y revive siempre activos para todos los perfiles.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ConfigOptionCard(
            title = "Farm Spot",
            summary = farmSpot?.summaryLabel(
                MapDefinitionRepository.getById(farmSpot.map)?.name
            ) ?: "Sin configurar",
            onClick = onOpenFarmSpot,
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Elf buff automático",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Desactívalo si la elf está offline o no quieres ir por buff.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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

                Text(
                    text = when {
                        profile?.enableElfBuff != true -> "Desactivado — el bot no buscará elf buff"
                        elfBuff != null -> elfBuff.summaryLabel(
                            MapDefinitionRepository.getById(elfBuff.map)?.name
                        )
                        else -> "Activo, pero sin zona configurada"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedButton(
                    onClick = onOpenElfBuff,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (elfBuff != null) "Editar zona elf buff" else "Configurar zona elf buff",
                    )
                }
            }
        }

        ConfigOptionCard(
            title = "Config Pociones",
            summary = buildString {
                append(if (profile?.enablePotionRecovery == true) "Activo" else "Desactivado")
                profile?.let {
                    append(" | HP ${it.hpPotionStacks} | MP ${it.mpPotionStacks}")
                }
            },
            onClick = onOpenPotionConfig,
        )

        OutlinedButton(
            onClick = { showDeleteDialog = true },
            modifier = Modifier.fillMaxWidth(),
            enabled = profile != null && profiles.size > 1,
        ) {
            Text("Borrar perfil")
        }

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Volver")
        }
    }

    if (showDeleteDialog && profile != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar perfil") },
            text = { Text("¿Eliminar \"${profile.displayName}\" y sus ubicaciones guardadas?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        ProfileRepository.deleteProfile(profile.filename)
                        LocationRepository.refreshForCurrentProfile()
                        showDeleteDialog = false
                        onBack()
                    },
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancelar")
                }
            },
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
    Card(
        modifier = modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
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
}
