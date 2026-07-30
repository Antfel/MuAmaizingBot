package com.example.muamaizingbot.ui.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.muamaizingbot.R

@Composable
fun ConfigDrawerContent(
    profileLabel: String,
    farmSpotLabel: String,
    onOpenHome: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenLicense: () -> Unit,
    onOpenSystem: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.drawer_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = profileLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = farmSpotLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))

        NavigationDrawerItem(
            label = { Text(stringResource(R.string.drawer_home)) },
            selected = false,
            onClick = onOpenHome,
        )
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.drawer_profiles)) },
            selected = false,
            onClick = onOpenProfiles,
        )
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.drawer_system)) },
            selected = false,
            onClick = onOpenSystem,
        )
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.drawer_license)) },
            selected = false,
            onClick = onOpenLicense,
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.drawer_dpi_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
