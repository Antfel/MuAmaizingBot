package com.example.muamaizingbot.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.muamaizingbot.profile.ProfileRepository

@Composable
fun PotionConfigScreen(
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Config Pociones",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = profile?.displayName ?: profileStem,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Recuperación automática",
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "Compra pociones cuando HP o MP estén vacíos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = enableRecovery,
                onCheckedChange = { enableRecovery = it },
            )
        }

        StackField(
            label = "Stacks HP a comprar",
            value = hpStacks,
            onValueChange = { hpStacks = it.coerceIn(1, 99) },
        )

        StackField(
            label = "Stacks MP a comprar",
            value = mpStacks,
            onValueChange = { mpStacks = it.coerceIn(1, 99) },
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
                val current = profile
                if (current == null) {
                    statusMessage = "Perfil no encontrado"
                    return@TextButton
                }
                ProfileRepository.saveProfile(
                    current.copy(
                        enablePotionRecovery = enableRecovery,
                        hpPotionStacks = hpStacks,
                        mpPotionStacks = mpStacks,
                    )
                )
                statusMessage = "Configuración guardada"
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = profile != null,
        ) {
            Text("Guardar")
        }

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Volver")
        }
    }
}

@Composable
private fun StackField(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { text ->
            text.toIntOrNull()?.let(onValueChange)
        },
        label = { Text(label) },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
    )
}
