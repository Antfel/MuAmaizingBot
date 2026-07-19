package com.example.muamaizingbot.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.muamaizingbot.profile.BotMode
import com.example.muamaizingbot.profile.BotProfile
import com.example.muamaizingbot.profile.LocationRepository
import com.example.muamaizingbot.profile.ProfileRepository
import com.example.muamaizingbot.profile.isElfBuffGiverMode
import com.example.muamaizingbot.profile.isElfBuffPostMode
import com.example.muamaizingbot.profile.isElfBuffWarMode
import com.example.muamaizingbot.profile.normalizedBotMode

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
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .imePadding()
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
                Text(
                    text = "Modo del bot",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Farm farmea y puede buscar buff. Elf Buff da buff (mundo abierto o War/APEX).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = profile?.normalizedBotMode() == BotMode.FARM,
                        onClick = {
                            ProfileRepository.setBotMode(profileFilename, BotMode.FARM)
                        },
                        enabled = profile != null,
                        label = { Text("Farm") },
                    )
                    FilterChip(
                        selected = profile?.isElfBuffPostMode() == true,
                        onClick = {
                            if (profile?.isElfBuffPostMode() != true) {
                                ProfileRepository.setBotMode(
                                    profileFilename,
                                    BotMode.ELF_BUFF_GIVER,
                                )
                            }
                        },
                        enabled = profile != null,
                        label = { Text("Elf Buff") },
                    )
                }
            }
        }

        ConfigOptionCard(
            title = when {
                profile?.isElfBuffWarMode() == true -> "Mapa Divine (Farm Spot)"
                profile?.isElfBuffGiverMode() == true -> "Buff post (Farm Spot)"
                else -> "Farm Spot"
            },
            summary = farmSpot?.summaryLabel(
                MapDefinitionRepository.getById(farmSpot.map)?.name
            ) ?: "Sin configurar",
            onClick = onOpenFarmSpot,
        )

        if (profile?.isElfBuffPostMode() != true) {
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
        } else {
            ElfBuffParamsCard(
                profile = profile,
                profileFilename = profileFilename,
            )
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
private fun ElfBuffParamsCard(
    profile: BotProfile?,
    profileFilename: String,
) {
    val war = profile?.isElfBuffWarMode() == true

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
            Text(
                text = "Parámetros Elf Buff",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Elegí la variante: mundo abierto (PK All/Union) o War/APEX en Divine.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = !war,
                    onClick = {
                        ProfileRepository.setBotMode(profileFilename, BotMode.ELF_BUFF_GIVER)
                    },
                    enabled = profile != null,
                    label = { Text("Mundo abierto") },
                )
                FilterChip(
                    selected = war,
                    onClick = {
                        ProfileRepository.setBotMode(profileFilename, BotMode.ELF_BUFF_WAR)
                    },
                    enabled = profile != null,
                    label = { Text("War (APEX)") },
                )
            }

            if (war) {
                Text(
                    text = "Configura Farm Spot en Divine. Al Start captura tus coords HUD " +
                        "como war post. Tras morir revive → minimapa → vuelve al post. " +
                        "No cambia PK ni fuerza Auto.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                ElfGiverCastFields(
                    profile = profile,
                    profileFilename = profileFilename,
                )
            }
        }
    }
}

@Composable
private fun ElfGiverCastFields(
    profile: BotProfile?,
    profileFilename: String,
) {
    var intervalText by remember(profile?.filename, profile?.elfBuffCastIntervalSec) {
        mutableStateOf(
            (profile?.elfBuffCastIntervalSec ?: BotProfile.DEFAULT_ELF_CAST_INTERVAL_SEC).toString(),
        )
    }
    var autoCast by remember(profile?.filename, profile?.elfBuffAutoCast) {
        mutableStateOf(profile?.elfBuffAutoCast ?: true)
    }
    var saveHint by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(profile?.elfBuffCastIntervalSec, profile?.elfBuffAutoCast) {
        intervalText = (profile?.elfBuffCastIntervalSec ?: BotProfile.DEFAULT_ELF_CAST_INTERVAL_SEC).toString()
        autoCast = profile?.elfBuffAutoCast ?: true
    }

    Text(
        text = "Al Start mapea Greater Defense / Greater Damage. " +
            "Ciclo UI: All → Focus → Union → buff aliado (verde) → Focus Boss → All.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    OutlinedTextField(
        value = intervalText,
        onValueChange = { intervalText = it.filter { ch -> ch.isDigit() }.take(3) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Pausa entre ciclos (seg)") },
        supportingText = {
            Text("Tras terminar un ciclo, espera esto y vuelve a buscar. Default 1s.")
        },
        singleLine = true,
        enabled = profile != null,
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Auto-cast por timer",
            style = MaterialTheme.typography.bodyMedium,
        )
        Switch(
            checked = autoCast,
            onCheckedChange = { autoCast = it },
            enabled = profile != null,
        )
    }

    OutlinedButton(
        onClick = {
            val interval = intervalText.toIntOrNull()
                ?: BotProfile.DEFAULT_ELF_CAST_INTERVAL_SEC
            ProfileRepository.setElfGiverCastConfig(
                profileFilename = profileFilename,
                skillRefX = null,
                skillRefY = null,
                intervalSec = interval,
                autoCast = autoCast,
            )
            saveHint = "Guardado"
        },
        modifier = Modifier.fillMaxWidth(),
        enabled = profile != null,
    ) {
        Text("Guardar casteo")
    }

    saveHint?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
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
        modifier = modifier.fillMaxWidth(),
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
