package com.example.blueheartv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blueheartv.chat.AgentServerConfigStore
import com.example.blueheartv.control.AdbWebSocketService
import com.example.blueheartv.system.SystemService
import com.example.blueheartv.ui.theme.*
import com.example.blueheartv.util.ToastType
import com.example.blueheartv.util.ToastUtil

@Composable
fun SettingsDetailScreen(
    settingKey: String,
    onBack: () -> Unit,
    onClearHistory: () -> Unit = {},
    onLogout: () -> Unit = {},
) {
    val gradientBrush = Brush.radialGradient(
        colors = listOf(GradientBlueStart, GradientBlueEnd),
        center = Offset(0.5f, 0.5f),
        radius = 800f,
    )

    val (title, content) = remember(settingKey) { resolveDetail(settingKey, onClearHistory, onLogout) }

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
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkPrimary,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            content()

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

private fun resolveDetail(
    key: String,
    onClearHistory: () -> Unit,
    onLogout: () -> Unit,
): Pair<String, @Composable () -> Unit> = when (key) {
    "profile" -> "个人信息" to { ProfileDetailContent() }
    "notifications" -> "通知设置" to { NotificationsDetailContent() }
    "privacy" -> "隐私与安全" to { PrivacyDetailContent(onClearHistory, onLogout) }
    "language" -> "语言设置" to { LanguageDetailContent() }
    "agent_server" -> "Agent 服务" to { AgentServerDetailContent() }
    "storage" -> "存储管理" to { StorageDetailContent() }
    "help" -> "帮助与反馈" to { HelpDetailContent() }
    "about" -> "关于" to { AboutDetailContent() }
    else -> "设置详情" to { DefaultDetailContent(key) }
}

// ── Profile ──────────────────────────────────────────────────────────────────

@Composable
private fun ProfileDetailContent() {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        // Avatar placeholder
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(GradientBlueStart, CircleShape)
                .align(Alignment.CenterHorizontally),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Person,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = Color.White,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        DetailCard {
            DetailRow(label = "昵称", value = "蓝心小V用户")
            DetailDivider()
            DetailRow(label = "账号", value = "user@blueheartv.com")
            DetailDivider()
            DetailRow(label = "手机号", value = "138****8888")
        }
    }
}

// ── Notifications ─────────────────────────────────────────────────────────────

@Composable
private fun NotificationsDetailContent() {
    var pushEnabled by remember { mutableStateOf(true) }
    var soundEnabled by remember { mutableStateOf(true) }
    var vibrationEnabled by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        DetailCard {
            SwitchRow(label = "推送通知", checked = pushEnabled) { pushEnabled = it }
            DetailDivider()
            SwitchRow(label = "消息声音", checked = soundEnabled) { soundEnabled = it }
            DetailDivider()
            SwitchRow(label = "震动提醒", checked = vibrationEnabled) { vibrationEnabled = it }
        }
    }
}

// ── Privacy ───────────────────────────────────────────────────────────────────

@Composable
private fun PrivacyDetailContent(
    onClearHistory: () -> Unit = {},
    onLogout: () -> Unit = {},
) {
    var saveHistory by remember { mutableStateOf(true) }
    var analyticsEnabled by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清除对话记录") },
            text = { Text("确定要清除所有对话记录吗？此操作无法撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    onClearHistory()
                }) { Text("确定", color = Color(0xFFE53935)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("取消") }
            },
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("注销账号") },
            text = { Text("确定要注销当前账号吗？注销后需要重新登录。") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    onLogout()
                }) { Text("确定", color = Color(0xFFE53935)) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("取消") }
            },
        )
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        DetailCard {
            SwitchRow(label = "保存对话历史", checked = saveHistory) { saveHistory = it }
            DetailDivider()
            SwitchRow(label = "数据分析（帮助改善产品）", checked = analyticsEnabled) { analyticsEnabled = it }
        }

        Spacer(modifier = Modifier.height(16.dp))

        DetailCard {
            DetailActionRow(
                label = "清除所有对话记录",
                icon = Icons.Outlined.Delete,
                tint = Color(0xFFE53935),
                onClick = { showClearDialog = true },
            )
            DetailDivider()
            DetailActionRow(
                label = "注销账号",
                icon = Icons.AutoMirrored.Outlined.ExitToApp,
                tint = Color(0xFFE53935),
                onClick = { showLogoutDialog = true },
            )
        }
    }
}

// ── Language ──────────────────────────────────────────────────────────────────

@Composable
private fun LanguageDetailContent() {
    val languages = listOf("简体中文", "繁體中文", "English", "日本語", "한국어")
    var selected by remember { mutableStateOf("简体中文") }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        DetailCard {
            languages.forEachIndexed { index, lang ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selected = lang }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = lang,
                        fontSize = 16.sp,
                        color = DarkPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    if (lang == selected) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = GradientBlueStart,
                        )
                    }
                }
                if (index < languages.lastIndex) DetailDivider()
            }
        }
    }
}

// ── Agent Server ─────────────────────────────────────────────────────────────

@Composable
private fun AgentServerDetailContent() {
    val context = LocalContext.current
    val config by AgentServerConfigStore.config.collectAsState()
    var baseUrl by remember(config.baseUrl) { mutableStateOf(config.baseUrl) }
    var apiKey by remember(config.apiKey) { mutableStateOf(config.apiKey) }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        DetailCard {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Server URL") },
                    placeholder = { Text("http://127.0.0.1:8124") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("X-Api-Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        AgentServerConfigStore.update(context, baseUrl, apiKey)
                        AdbWebSocketService.start(context)
                        SystemService.start(context, baseUrl)
                        ToastUtil.show("Agent 服务配置已保存", ToastType.SUCCESS)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("保存并连接")
                }
            }
        }
    }
}

// ── Storage ───────────────────────────────────────────────────────────────────

@Composable
private fun StorageDetailContent() {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        DetailCard {
            DetailRow(label = "缓存大小", value = "12.4 MB")
            DetailDivider()
            DetailRow(label = "对话记录", value = "3.2 MB")
            DetailDivider()
            DetailRow(label = "总占用", value = "15.6 MB")
        }

        Spacer(modifier = Modifier.height(16.dp))

        DetailCard {
            DetailActionRow(label = "清除缓存", icon = Icons.Outlined.CleaningServices)
        }
    }
}

// ── Help ──────────────────────────────────────────────────────────────────────

@Composable
private fun HelpDetailContent() {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        DetailCard {
            DetailActionRow(label = "使用教程", icon = Icons.AutoMirrored.Outlined.MenuBook)
            DetailDivider()
            DetailActionRow(label = "常见问题", icon = Icons.Outlined.QuestionAnswer)
            DetailDivider()
            DetailActionRow(label = "意见反馈", icon = Icons.Outlined.Feedback)
            DetailDivider()
            DetailActionRow(label = "联系客服", icon = Icons.Outlined.SupportAgent)
        }
    }
}

// ── About ─────────────────────────────────────────────────────────────────────

@Composable
private fun AboutDetailContent() {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        // App icon placeholder
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(GradientBlueStart, RoundedCornerShape(18.dp))
                .align(Alignment.CenterHorizontally),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Favorite,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = Color.White,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Echo",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = DarkPrimary,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Text(
            text = "Echo",
            fontSize = 13.sp,
            color = MutedText,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        Spacer(modifier = Modifier.height(24.dp))

        DetailCard {
            DetailRow(label = "版本号", value = "1.0.0")
            DetailDivider()
            DetailRow(label = "构建号", value = "20240101")
            DetailDivider()
            DetailRow(label = "开发者", value = "BlueHeartV Team")
        }

        Spacer(modifier = Modifier.height(16.dp))

        DetailCard {
            DetailActionRow(label = "用户协议", icon = Icons.Outlined.Description)
            DetailDivider()
            DetailActionRow(label = "隐私政策", icon = Icons.Outlined.PrivacyTip)
            DetailDivider()
            DetailActionRow(label = "开源许可", icon = Icons.Outlined.Code)
        }
    }
}

// ── Default ───────────────────────────────────────────────────────────────────

@Composable
private fun DefaultDetailContent(key: String) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        DetailCard {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "暂无内容（key: $key）",
                    fontSize = 14.sp,
                    color = MutedText,
                )
            }
        }
    }
}

// ── Shared UI helpers ─────────────────────────────────────────────────────────

@Composable
private fun DetailCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceWhite,
        shadowElevation = 2.dp,
    ) {
        Column(content = content)
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            color = DarkPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            fontSize = 14.sp,
            color = MutedText,
        )
    }
}

@Composable
private fun DetailActionRow(
    label: String,
    icon: ImageVector,
    tint: Color = DarkPrimary,
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
            modifier = Modifier.size(20.dp),
            tint = tint,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            fontSize = 16.sp,
            color = tint,
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
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            color = DarkPrimary,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = GradientBlueStart,
            ),
        )
    }
}

@Composable
private fun DetailDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 20.dp),
        thickness = 0.5.dp,
        color = DividerColor
    )
}
