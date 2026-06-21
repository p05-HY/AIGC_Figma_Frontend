package com.example.blueheartv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blueheartv.R
import com.example.blueheartv.ui.theme.BrandPrimary
import com.example.blueheartv.ui.theme.DarkPrimary
import com.example.blueheartv.ui.theme.DividerColor
import com.example.blueheartv.ui.theme.GlassFillTranslucent
import com.example.blueheartv.ui.theme.GradientWhite00
import com.example.blueheartv.ui.theme.GradientWhite40
import com.example.blueheartv.ui.theme.MutedText
import com.example.blueheartv.ui.theme.Radius
import com.example.blueheartv.ui.theme.Slate700Stroke
import com.example.blueheartv.ui.theme.SurfaceWhite

@Composable
fun DeviceCapabilityPanel(
    needsShizuku: Boolean,
    needsAccessibility: Boolean,
    needsOverlay: Boolean,
    onRequestShizuku: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onRequestOverlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!needsShizuku && !needsAccessibility && !needsOverlay) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(Radius.large.dp))
            .background(GlassFillTranslucent)
            .background(Brush.verticalGradient(listOf(GradientWhite40, GradientWhite00, SurfaceWhite)))
            .border(0.5.dp, Slate700Stroke, RoundedCornerShape(Radius.large.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.capability_panel_title),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = DarkPrimary,
        )
        Text(
            text = stringResource(R.string.capability_panel_subtitle),
            fontSize = 12.sp,
            color = MutedText,
            lineHeight = 17.sp,
        )

        if (needsShizuku) {
            CapabilityRow(
                icon = Icons.Outlined.Security,
                title = stringResource(R.string.capability_shizuku_title),
                description = stringResource(R.string.capability_shizuku_desc),
                action = stringResource(R.string.capability_shizuku_action),
                onClick = onRequestShizuku,
            )
        }
        if (needsAccessibility) {
            CapabilityRow(
                icon = Icons.Outlined.Accessibility,
                title = stringResource(R.string.capability_accessibility_title),
                description = stringResource(R.string.capability_accessibility_desc),
                action = stringResource(R.string.capability_accessibility_action),
                onClick = onRequestAccessibility,
            )
        }
        if (needsOverlay) {
            CapabilityRow(
                icon = Icons.Outlined.Smartphone,
                title = stringResource(R.string.capability_overlay_title),
                description = stringResource(R.string.capability_overlay_desc),
                action = stringResource(R.string.capability_overlay_action),
                onClick = onRequestOverlay,
            )
        }
    }
}

@Composable
private fun CapabilityRow(
    icon: ImageVector,
    title: String,
    description: String,
    action: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, DividerColor, RoundedCornerShape(Radius.medium.dp))
            .background(SurfaceWhite.copy(alpha = 0.72f), RoundedCornerShape(Radius.medium.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(BrandPrimary.copy(alpha = 0.12f), CircleShape),
            )
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = BrandPrimary,
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = DarkPrimary)
            Text(text = description, fontSize = 11.sp, color = MutedText, lineHeight = 15.sp)
        }
        TextButton(
            onClick = onClick,
            colors = ButtonDefaults.textButtonColors(contentColor = BrandPrimary),
        ) {
            Text(text = action, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}
