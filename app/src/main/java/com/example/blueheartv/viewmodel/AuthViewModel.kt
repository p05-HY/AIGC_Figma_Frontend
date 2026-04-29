package com.example.blueheartv.viewmodel

import android.Manifest
import androidx.lifecycle.ViewModel
import com.example.blueheartv.model.PermissionItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private enum class PermissionKey {
    CALENDAR,
    LOCATION,
    NOTIFICATION,
    CAMERA_AND_MEDIA,
    OVERLAY,
    SCREEN_CAPTURE,
}

private enum class PermissionType {
    RUNTIME,
    SPECIAL_OVERLAY,
    ON_DEMAND,
}

private data class PermissionDefinition(
    val key: PermissionKey,
    val name: String,
    val type: PermissionType,
    val required: Boolean = true,
    val runtimePermissionsForSdk: (Int) -> List<String> = { emptyList() },
    val isGranted: (grantedRuntimePermissions: Set<String>, canDrawOverlays: Boolean, sdk: Int) -> Boolean,
)

data class AuthUiState(
    val permissions: List<PermissionItem> = emptyList(),
    val isAuthorized: Boolean = false,
    val runtimePermissionQueue: List<String> = emptyList(),
    val requestOverlayPermission: Boolean = false,
    val authorizationHint: String? = null,
)

class AuthViewModel : ViewModel() {
    private val permissionDefinitions = listOf(
        PermissionDefinition(
            key = PermissionKey.CALENDAR,
            name = "日历读写",
            type = PermissionType.RUNTIME,
            runtimePermissionsForSdk = {
                listOf(
                    Manifest.permission.READ_CALENDAR,
                    Manifest.permission.WRITE_CALENDAR,
                )
            },
            isGranted = { grantedRuntimePermissions, _, _ ->
                grantedRuntimePermissions.contains(Manifest.permission.READ_CALENDAR) &&
                        grantedRuntimePermissions.contains(Manifest.permission.WRITE_CALENDAR)
            },
        ),
        PermissionDefinition(
            key = PermissionKey.LOCATION,
            name = "位置信息",
            type = PermissionType.RUNTIME,
            runtimePermissionsForSdk = {
                listOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                )
            },
            isGranted = { grantedRuntimePermissions, _, _ ->
                grantedRuntimePermissions.contains(Manifest.permission.ACCESS_COARSE_LOCATION) ||
                        grantedRuntimePermissions.contains(Manifest.permission.ACCESS_FINE_LOCATION)
            },
        ),
        PermissionDefinition(
            key = PermissionKey.NOTIFICATION,
            name = "通知读取",
            type = PermissionType.RUNTIME,
            runtimePermissionsForSdk = { sdk ->
                if (sdk >= 33) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()
            },
            isGranted = { grantedRuntimePermissions, _, sdk ->
                sdk < 33 || grantedRuntimePermissions.contains(Manifest.permission.POST_NOTIFICATIONS)
            },
        ),
        PermissionDefinition(
            key = PermissionKey.CAMERA_AND_MEDIA,
            name = "相机/相册",
            type = PermissionType.RUNTIME,
            runtimePermissionsForSdk = { sdk ->
                if (sdk >= 33) {
                    listOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.READ_MEDIA_IMAGES,
                    )
                } else {
                    listOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                    )
                }
            },
            isGranted = { grantedRuntimePermissions, _, sdk ->
                if (sdk >= 33) {
                    grantedRuntimePermissions.contains(Manifest.permission.CAMERA) &&
                            grantedRuntimePermissions.contains(Manifest.permission.READ_MEDIA_IMAGES)
                } else {
                    grantedRuntimePermissions.contains(Manifest.permission.CAMERA) &&
                            grantedRuntimePermissions.contains(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            },
        ),
        PermissionDefinition(
            key = PermissionKey.OVERLAY,
            name = "系统悬浮窗",
            type = PermissionType.SPECIAL_OVERLAY,
            isGranted = { _, canDrawOverlays, _ -> canDrawOverlays },
        ),
        PermissionDefinition(
            key = PermissionKey.SCREEN_CAPTURE,
            name = "屏幕内容读取（按需）",
            type = PermissionType.ON_DEMAND,
            required = false,
            isGranted = { _, _, _ -> true },
        ),
    )

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private var pendingOverlayRequestAfterRuntime: Boolean = false

    init {
        _uiState.value = _uiState.value.copy(
            permissions = permissionDefinitions.map { PermissionItem(name = it.name, isGranted = false) },
            authorizationHint = "请同意必要权限后继续",
        )
    }

    fun runtimePermissionsForSdk(sdk: Int): List<String> {
        return permissionDefinitions
            .filter { it.type == PermissionType.RUNTIME }
            .flatMap { it.runtimePermissionsForSdk(sdk) }
            .distinct()
    }

    fun initializePermissionState(
        grantedRuntimePermissions: Set<String>,
        canDrawOverlays: Boolean,
        sdk: Int,
    ) {
        publishPermissionState(
            grantedRuntimePermissions = grantedRuntimePermissions,
            canDrawOverlays = canDrawOverlays,
            sdk = sdk,
            hint = null,
        )
    }

    fun onPermissionRowClick(
        index: Int,
        grantedRuntimePermissions: Set<String>,
        canDrawOverlays: Boolean,
        sdk: Int,
    ) {
        val definition = permissionDefinitions.getOrNull(index) ?: return

        publishPermissionState(
            grantedRuntimePermissions = grantedRuntimePermissions,
            canDrawOverlays = canDrawOverlays,
            sdk = sdk,
            hint = null,
        )

        when (definition.type) {
            PermissionType.RUNTIME -> {
                val missing = definition.runtimePermissionsForSdk(sdk)
                    .filterNot(grantedRuntimePermissions::contains)
                if (missing.isNotEmpty()) {
                    _uiState.update {
                        it.copy(
                            runtimePermissionQueue = missing,
                            authorizationHint = "请授权：${definition.name}",
                        )
                    }
                }
            }

            PermissionType.SPECIAL_OVERLAY -> {
                val overlayGranted = definition.isGranted(grantedRuntimePermissions, canDrawOverlays, sdk)
                if (!overlayGranted) {
                    _uiState.update {
                        it.copy(
                            requestOverlayPermission = true,
                            authorizationHint = "请开启系统悬浮窗权限",
                        )
                    }
                }
            }

            PermissionType.ON_DEMAND -> {
                _uiState.update {
                    it.copy(authorizationHint = "该权限会在使用相关功能时再申请")
                }
            }
        }
    }

    fun onAuthorizeClick(
        grantedRuntimePermissions: Set<String>,
        canDrawOverlays: Boolean,
        sdk: Int,
    ) {
        publishPermissionState(
            grantedRuntimePermissions = grantedRuntimePermissions,
            canDrawOverlays = canDrawOverlays,
            sdk = sdk,
            hint = null,
        )

        val missingRuntimePermissions = permissionDefinitions
            .filter { it.type == PermissionType.RUNTIME && it.required }
            .flatMap { definition ->
                definition.runtimePermissionsForSdk(sdk).filterNot(grantedRuntimePermissions::contains)
            }
            .distinct()

        val overlayDefinition = permissionDefinitions.first { it.key == PermissionKey.OVERLAY }
        val overlayMissing = overlayDefinition.required &&
                !overlayDefinition.isGranted(grantedRuntimePermissions, canDrawOverlays, sdk)

        when {
            missingRuntimePermissions.isNotEmpty() -> {
                pendingOverlayRequestAfterRuntime = overlayMissing
                _uiState.update {
                    it.copy(
                        runtimePermissionQueue = missingRuntimePermissions,
                        authorizationHint = "正在申请运行时权限",
                    )
                }
            }

            overlayMissing -> {
                _uiState.update {
                    it.copy(
                        requestOverlayPermission = true,
                        authorizationHint = "请开启系统悬浮窗权限",
                    )
                }
            }

            else -> {
                _uiState.update { it.copy(authorizationHint = null) }
            }
        }
    }

    fun onRuntimePermissionRequestConsumed() {
        _uiState.update { it.copy(runtimePermissionQueue = emptyList()) }
    }

    fun onOverlayPermissionRequestConsumed() {
        _uiState.update { it.copy(requestOverlayPermission = false) }
    }

    fun onRuntimePermissionResult(
        grantedRuntimePermissions: Set<String>,
        canDrawOverlays: Boolean,
        sdk: Int,
    ) {
        publishPermissionState(
            grantedRuntimePermissions = grantedRuntimePermissions,
            canDrawOverlays = canDrawOverlays,
            sdk = sdk,
            hint = "仍有权限未授权，可继续点击完成授权",
        )

        val overlayDefinition = permissionDefinitions.first { it.key == PermissionKey.OVERLAY }
        val overlayMissing = overlayDefinition.required &&
                !overlayDefinition.isGranted(grantedRuntimePermissions, canDrawOverlays, sdk)

        if (pendingOverlayRequestAfterRuntime && overlayMissing) {
            pendingOverlayRequestAfterRuntime = false
            _uiState.update {
                it.copy(
                    requestOverlayPermission = true,
                    authorizationHint = "请开启系统悬浮窗权限",
                )
            }
        } else {
            pendingOverlayRequestAfterRuntime = false
        }
    }

    fun onOverlayPermissionResult(
        grantedRuntimePermissions: Set<String>,
        canDrawOverlays: Boolean,
        sdk: Int,
    ) {
        publishPermissionState(
            grantedRuntimePermissions = grantedRuntimePermissions,
            canDrawOverlays = canDrawOverlays,
            sdk = sdk,
            hint = "仍有权限未授权，可继续点击完成授权",
        )
    }

    private fun publishPermissionState(
        grantedRuntimePermissions: Set<String>,
        canDrawOverlays: Boolean,
        sdk: Int,
        hint: String?,
    ) {
        val permissionItems = permissionDefinitions.map { definition ->
            PermissionItem(
                name = definition.name,
                isGranted = definition.isGranted(grantedRuntimePermissions, canDrawOverlays, sdk),
            )
        }

        val requiredGranted = permissionDefinitions
            .withIndex()
            .filter { (_, definition) -> definition.required }
            .all { (index, _) -> permissionItems[index].isGranted }

        _uiState.update {
            it.copy(
                permissions = permissionItems,
                isAuthorized = requiredGranted,
                authorizationHint = if (requiredGranted) null else (hint ?: "请同意必要权限后继续"),
            )
        }
    }
}
