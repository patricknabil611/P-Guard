package com.example.data.scanner

data class ScannedPackageInfo(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val firstInstallTimeMillis: Long,
    val lastUpdateTimeMillis: Long,
    val installerPackageName: String?,
    val isSystemApp: Boolean,
    val hasLauncherIcon: Boolean = true,
    val riskyPermissions: List<RiskyPermission> = emptyList(),
    val hasAccessibilityAccess: Boolean = false,
    val isDeviceAdmin: Boolean = false,
    val hasNotificationListener: Boolean = false,
    val hasOverlayPermission: Boolean = false
)
