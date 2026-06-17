package com.example.blueheartv.ui.screens

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.example.blueheartv.R
import com.example.blueheartv.ui.theme.*
import com.example.blueheartv.viewmodel.AuthViewModel

@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    onAuthorized: () -> Unit,
) {
    val context = LocalContext.current
    val sdk = Build.VERSION.SDK_INT
    val uiState by viewModel.uiState.collectAsState()

    fun grantedRuntimePermissions(): Set<String> {
        return viewModel.runtimePermissionsForSdk(sdk)
            .filter { permission ->
                ContextCompat.checkSelfPermission(context, permission) ==
                        PackageManager.PERMISSION_GRANTED
            }
            .toSet()
    }

    fun canDrawOverlays(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    val runtimePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        viewModel.onRuntimePermissionResult(
            grantedRuntimePermissions = grantedRuntimePermissions(),
            canDrawOverlays = canDrawOverlays(),
            sdk = sdk,
        )
    }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.onOverlayPermissionResult(
            grantedRuntimePermissions = grantedRuntimePermissions(),
            canDrawOverlays = canDrawOverlays(),
            sdk = sdk,
        )
    }

    LaunchedEffect(Unit) {
        viewModel.initializePermissionState(
            grantedRuntimePermissions = grantedRuntimePermissions(),
            canDrawOverlays = canDrawOverlays(),
            sdk = sdk,
        )
    }

    LaunchedEffect(uiState.runtimePermissionQueue) {
        if (uiState.runtimePermissionQueue.isNotEmpty()) {
            runtimePermissionLauncher.launch(uiState.runtimePermissionQueue.toTypedArray())
            viewModel.onRuntimePermissionRequestConsumed()
        }
    }

    LaunchedEffect(uiState.requestOverlayPermission) {
        if (uiState.requestOverlayPermission) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:${context.packageName}".toUri(),
            )
            overlayPermissionLauncher.launch(intent)
            viewModel.onOverlayPermissionRequestConsumed()
        }
    }

    LaunchedEffect(uiState.isAuthorized) {
        if (uiState.isAuthorized) {
            onAuthorized()
        }
    }

    if (uiState.isAuthorized) return

    val gradientBrush = Brush.radialGradient(
        colors = listOf(GradientBlueStart, GradientBlueEnd),
        center = Offset(0.5f, 0.5f),
        radius = 800f,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBrush)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(44.dp))

            // Robot mascot logo
            Box(
                modifier = Modifier
                    .size(129.dp)
                    .shadow(4.dp, CircleShape)
                    .background(SurfaceWhite, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_echo_face),
                    contentDescription = "Mascot",
                    modifier = Modifier.size(64.dp),
                    contentScale = ContentScale.Crop,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Title
            Text(
                text = "欢迎使用Echo",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = DarkPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "您的系统级AI智能助手",
                fontSize = 14.sp,
                color = MutedText,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Permission list card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = SurfaceWhite,
                shadowElevation = 4.dp,
            ) {
                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                    val icons = listOf(
                        Icons.Outlined.CalendarMonth,
                        Icons.Outlined.LocationOn,
                        Icons.Outlined.Notifications,
                        Icons.Outlined.Screenshot,
                        Icons.Outlined.CameraAlt,
                        Icons.Outlined.Api,
                    )
                    uiState.permissions.forEachIndexed { index, permission ->
                        PermissionRow(
                            icon = icons.getOrElse(index) { Icons.Outlined.Settings },
                            name = permission.name,
                            isGranted = permission.isGranted,
                            onToggle = {
                                viewModel.onPermissionRowClick(
                                    index = index,
                                    grantedRuntimePermissions = grantedRuntimePermissions(),
                                    canDrawOverlays = canDrawOverlays(),
                                    sdk = sdk,
                                )
                            },
                        )
                        if (index < uiState.permissions.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 24.dp),
                                thickness = 0.5.dp,
                                color = DividerColor.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Privacy notice
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_shield),
                    contentDescription = null,
                    modifier = Modifier.size(23.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "端对端用户隐私保护，所有数据加密处理",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Light,
                    color = PrivacyBlue,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // CTA button
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(45.dp)
                    .clickable {
                        viewModel.onAuthorizeClick(
                            grantedRuntimePermissions = grantedRuntimePermissions(),
                            canDrawOverlays = canDrawOverlays(),
                            sdk = sdk,
                        )
                    },
                shape = RoundedCornerShape(22.dp),
                color = SurfaceWhite,
                shadowElevation = 4.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 26.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "一键授权，开始使用",
                        fontSize = 16.sp,
                        color = BlueAccent,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                    )
                    Image(
                        painter = painterResource(R.drawable.ic_arrow_right),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            val authorizationHint = uiState.authorizationHint
            if (!authorizationHint.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = authorizationHint,
                    fontSize = 12.sp,
                    color = MutedText,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    name: String,
    isGranted: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = 32.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = TextBlack,
        )
        Spacer(modifier = Modifier.width(18.dp))
        Text(
            text = name,
            fontSize = 18.sp,
            color = TextBlack,
            modifier = Modifier.weight(1f),
        )
        if (isGranted) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(AuthCheckGreenStart, AuthCheckGreenEnd)
                        ),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = SurfaceWhite,
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(AuthUncheckedGray, CircleShape),
            )
        }
    }
}
