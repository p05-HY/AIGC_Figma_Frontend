package com.example.blueheartv.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blueheartv.floating.FloatingServiceLauncher
import com.example.blueheartv.ui.theme.*

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit = {},
) {
    val context = LocalContext.current
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
                    contentDescription = "Back",
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { onBack() },
                    tint = DarkPrimary,
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "设置",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkPrimary,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Settings sections
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                color = SurfaceWhite,
                shadowElevation = 2.dp,
            ) {
                Column {
                    SettingsItem(Icons.Outlined.Person, "个人信息") { onNavigateToDetail("profile") }
                    SettingsDivider()
                    SettingsItem(Icons.Outlined.Notifications, "通知设置") { onNavigateToDetail("notifications") }
                    SettingsDivider()
                    SettingsItem(Icons.Outlined.Security, "隐私与安全") { onNavigateToDetail("privacy") }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                color = SurfaceWhite,
                shadowElevation = 2.dp,
            ) {
                Column {
                    SettingsItem(Icons.Outlined.Language, "语言设置") { onNavigateToDetail("language") }
                    SettingsDivider()
                    SettingsItem(Icons.Outlined.DarkMode, "主题外观") { onNavigateToDetail("theme") }
                    SettingsDivider()
                    SettingsItem(Icons.Outlined.Storage, "存储管理") { onNavigateToDetail("storage") }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                color = SurfaceWhite,
                shadowElevation = 2.dp,
            ) {
                Column {
                    SettingsItem(Icons.Outlined.ChatBubble, "开启悬浮球") {
                        FloatingServiceLauncher.launch(context)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                color = SurfaceWhite,
                shadowElevation = 2.dp,
            ) {
                Column {
                    SettingsItem(Icons.AutoMirrored.Outlined.Help, "帮助与反馈") { onNavigateToDetail("help") }
                    SettingsDivider()
                    SettingsItem(Icons.Outlined.Info, "关于") { onNavigateToDetail("about") }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Version info
            Text(
                text = "版本 1.0.0",
                fontSize = 12.sp,
                color = MutedText,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            Spacer(modifier = Modifier.height(40.dp))
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
