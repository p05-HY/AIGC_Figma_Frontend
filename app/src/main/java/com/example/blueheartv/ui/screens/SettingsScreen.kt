package com.example.blueheartv.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Help
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blueheartv.R
import com.example.blueheartv.floating.FloatingServiceLauncher
import com.example.blueheartv.ui.theme.*

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit = {},
    onNavigateToCalibration: () -> Unit = {},
) {
    val context = LocalContext.current
    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (FloatingServiceLauncher.isOverlayGranted(context)) {
            FloatingServiceLauncher.launch(context)
        }
    }
    val gradientBrush = Brush.radialGradient(
        colors = listOf(GradientBlueStart, GradientBlueEnd),
        center = Offset(0.5f, 0.5f),
        radius = 800f,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBrush),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { onBack() },
                    tint = DarkPrimary,
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = stringResource(R.string.settings_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkPrimary,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsSection(title = stringResource(R.string.settings_section_account)) {
                Column {
                    SettingsItem(Icons.Outlined.Person, stringResource(R.string.settings_profile)) { onNavigateToDetail("profile") }
                    SettingsDivider()
                    SettingsItem(Icons.Outlined.Notifications, stringResource(R.string.settings_notifications)) { onNavigateToDetail("notifications") }
                    SettingsDivider()
                    SettingsItem(Icons.Outlined.Security, stringResource(R.string.settings_privacy)) { onNavigateToDetail("privacy") }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsSection(title = stringResource(R.string.settings_section_service)) {
                Column {
                    SettingsItem(Icons.Outlined.Language, stringResource(R.string.settings_language)) { onNavigateToDetail("language") }
                    SettingsDivider()
                    SettingsItem(Icons.Outlined.Cloud, stringResource(R.string.settings_agent_service)) { onNavigateToDetail("agent_server") }
                    SettingsDivider()
                    SettingsItem(Icons.Outlined.Storage, stringResource(R.string.settings_storage)) { onNavigateToDetail("storage") }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsSection(title = stringResource(R.string.settings_section_phone_control)) {
                Column {
                    SettingsItem(Icons.Outlined.ChatBubble, stringResource(R.string.floating_open_ball)) {
                        FloatingServiceLauncher.launch(context, overlayPermissionLauncher)
                    }
                    SettingsDivider()
                    SettingsItem(Icons.Outlined.Accessibility, stringResource(R.string.settings_accessibility)) {
                        onNavigateToDetail("accessibility")
                    }
                    SettingsDivider()
                    SettingsItem(Icons.Outlined.GpsFixed, stringResource(R.string.settings_calibration)) {
                        onNavigateToCalibration()
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsSection(title = stringResource(R.string.settings_section_support)) {
                Column {
                    SettingsItem(Icons.AutoMirrored.Outlined.Help, stringResource(R.string.settings_help)) { onNavigateToDetail("help") }
                    SettingsDivider()
                    SettingsItem(Icons.Outlined.Info, stringResource(R.string.settings_about)) { onNavigateToDetail("about") }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Version info
            Text(
                text = stringResource(R.string.settings_version_name, "1.0.0"),
                fontSize = 12.sp,
                color = MutedText,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MutedText,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = SurfaceWhite,
            shadowElevation = 2.dp,
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = DarkPrimary,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            fontSize = 16.sp,
            color = DarkPrimary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MutedText,
        )
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 20.dp),
        thickness = 0.5.dp,
        color = DividerColor
    )
}
