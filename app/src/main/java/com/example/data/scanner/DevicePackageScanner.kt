package com.example.data.scanner

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

interface PackageScanner {
    val ownPackageName: String
    fun scanInstalledPackages(): List<ScannedPackageInfo>
}

class DevicePackageScanner(
    private val context: Context
) : PackageScanner {

    override val ownPackageName: String
        get() = context.packageName

    override fun scanInstalledPackages(): List<ScannedPackageInfo> {
        val packageManager = context.packageManager

        val installedPackages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledPackages(
                PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        }

        val launcherPackages = queryLauncherPackages(packageManager)
        val accessibilityPackages = queryEnabledAccessibilityPackages()
        val deviceAdminPackages = queryActiveDeviceAdminPackages()
        val notificationListenerPackages = queryNotificationListenerPackages()

        return installedPackages.map { pkg ->
            val appInfo = pkg.applicationInfo
            val label = try {
                appInfo?.loadLabel(packageManager)?.toString()?.trim()
            } catch (_: Exception) {
                null
            }
            val appName = if (!label.isNullOrEmpty()) label else pkg.packageName

            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkg.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pkg.versionCode.toLong()
            }

            val installer = resolveInstallerPackage(packageManager, pkg.packageName)

            val isSystemApp = appInfo != null && (
                (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                    (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                )

            val riskyPermissions = extractRiskyPermissions(pkg)
            val hasOverlay = RiskyPermission.OVERLAY in riskyPermissions

            ScannedPackageInfo(
                packageName = pkg.packageName,
                appName = appName,
                versionName = pkg.versionName?.takeIf { it.isNotBlank() } ?: "—",
                versionCode = versionCode,
                firstInstallTimeMillis = pkg.firstInstallTime,
                lastUpdateTimeMillis = pkg.lastUpdateTime,
                installerPackageName = installer,
                isSystemApp = isSystemApp,
                hasLauncherIcon = pkg.packageName in launcherPackages,
                riskyPermissions = riskyPermissions,
                hasAccessibilityAccess = pkg.packageName in accessibilityPackages,
                isDeviceAdmin = pkg.packageName in deviceAdminPackages,
                hasNotificationListener = pkg.packageName in notificationListenerPackages,
                hasOverlayPermission = hasOverlay
            )
        }
    }

    private fun queryLauncherPackages(packageManager: PackageManager): Set<String> {
        return try {
            val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentActivities(
                    launcherIntent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
            }
            resolveInfos.mapNotNull { it.activityInfo?.packageName }.toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun queryEnabledAccessibilityPackages(): Set<String> {
        val packages = mutableSetOf<String>()
        try {
            val raw = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            packages += parseComponentPackageNames(raw)
        } catch (_: Exception) {
            // Fall through to AccessibilityManager query
        }

        try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                ?.forEach { info ->
                    val pkg = info.resolveInfo?.serviceInfo?.packageName
                    if (!pkg.isNullOrBlank()) {
                        packages += pkg
                    }
                }
        } catch (_: Exception) {
            // Ignore on restricted OEM builds
        }
        return packages
    }

    private fun queryActiveDeviceAdminPackages(): Set<String> {
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            dpm?.activeAdmins?.mapNotNull { it.packageName }?.toSet() ?: emptySet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun queryNotificationListenerPackages(): Set<String> {
        return try {
            val raw = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            )
            parseComponentPackageNames(raw)
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun parseComponentPackageNames(colonSeparated: String?): Set<String> {
        if (colonSeparated.isNullOrBlank()) return emptySet()
        return colonSeparated.split(':')
            .mapNotNull { entry ->
                val trimmed = entry.trim()
                if (trimmed.isEmpty()) return@mapNotNull null
                ComponentName.unflattenFromString(trimmed)?.packageName
                    ?: trimmed.substringBefore('/').takeIf { it.isNotBlank() }
            }
            .toSet()
    }

    private fun extractRiskyPermissions(pkg: PackageInfo): List<RiskyPermission> {
        val requested = pkg.requestedPermissions ?: return emptyList()
        val flags = pkg.requestedPermissionsFlags

        val grantedSet = mutableSetOf<String>()
        val declaredSet = mutableSetOf<String>()

        for (i in requested.indices) {
            val perm = requested[i] ?: continue
            declaredSet += perm
            val isGranted = flags != null && i < flags.size &&
                (flags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
            if (isGranted) {
                grantedSet += perm
            }
        }

        return RiskyPermission.entries.filter { risky ->
            if (risky in RiskyPermission.specialDeclarationPermissions) {
                // Special app-op permissions (SYSTEM_ALERT_WINDOW, REQUEST_INSTALL_PACKAGES)
                // often do not set REQUESTED_PERMISSION_GRANTED; flag when declared or granted.
                risky.androidPermissions.any { it in grantedSet || it in declaredSet }
            } else {
                risky.androidPermissions.any { it in grantedSet }
            }
        }
    }

    private fun resolveInstallerPackage(
        packageManager: PackageManager,
        packageName: String
    ): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val info = packageManager.getInstallSourceInfo(packageName)
                info.installingPackageName ?: info.initiatingPackageName
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstallerPackageName(packageName)
            }
        } catch (_: Exception) {
            null
        }
    }
}
