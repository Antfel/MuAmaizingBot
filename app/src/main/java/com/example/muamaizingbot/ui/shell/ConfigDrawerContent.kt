package com.example.muamaizingbot.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.muamaizingbot.R
import com.example.muamaizingbot.ui.theme.ContextoColors

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
            .background(ContextoColors.Canvas)
            .padding(horizontal = 14.dp, vertical = 20.dp),
    ) {
        Text(
            text = stringResource(R.string.drawer_title),
            color = ContextoColors.TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = profileLabel,
            color = ContextoColors.TextSecondary,
            fontSize = 13.sp,
        )
        Text(
            text = farmSpotLabel,
            color = ContextoColors.TextMuted,
            fontSize = 12.sp,
        )

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = ContextoColors.Border)
        Spacer(modifier = Modifier.height(8.dp))

        DrawerItem(stringResource(R.string.drawer_home), onOpenHome)
        DrawerItem(stringResource(R.string.drawer_profiles), onOpenProfiles)
        DrawerItem(stringResource(R.string.drawer_system), onOpenSystem)
        DrawerItem(stringResource(R.string.drawer_license), onOpenLicense)

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.drawer_dpi_note),
            color = ContextoColors.TextMuted,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun DrawerItem(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .border(1.dp, ContextoColors.Border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            color = ContextoColors.TextPrimary,
            fontSize = 14.sp,
        )
    }
}
