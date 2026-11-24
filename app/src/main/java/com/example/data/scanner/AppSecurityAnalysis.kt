package com.example.data.scanner

import com.example.data.local.entity.PackageSnapshotEntity

enum class RiskyPermission(val androidPermissions: Set<String>) {
    CAMERA(setOf("android.permission.CAMERA")),
    MICROPHONE(setOf("android.permission.RECORD_AUDIO")),
    FINE_LOCATION(setOf("android.permission.ACCESS_FINE_LOCATION")),
    SMS(setOf("android.permission.READ_SMS", "android.permission.SEND_SMS", "android.permission.RECEIVE_SMS")),
    CALL_LOG(setOf("android.permission.READ_CALL_LOG", "android.permission.WRITE_CALL_LOG")),
    CONTACTS(setOf("android.permission.READ_CONTACTS", "android.permission.WRITE_CONTACTS")),
    PHONE_STATE(setOf("android.permission.READ_PHONE_STATE")),
    OVERLAY(setOf("android.permission.SYSTEM_ALERT_WINDOW")),
    INSTALL_UNKNOWN_APPS(setOf("android.permission.REQUEST_INSTALL_PACKAGES"));

    companion object {
        val specialDeclarationPermissions: Set<RiskyPermission> = setOf(
            OVERLAY,
            INSTALL_UNKNOWN_APPS
        )

        fun parseCsv(csv: String): List<RiskyPermission> {
            if (csv.isBlank()) return emptyList()
            return csv.split(',')
                .mapNotNull { token -> entries.firstOrNull { it.name == token.trim() } }
        }

        fun toCsv(permissions: Collection<RiskyPermission>): String =
            permissions.distinct().joinToString(",") { it.name }
    }
}

enum class RiskLevel(val sortOrder: Int) {
    NONE(0),
    LOW(1),
    MEDIUM(2),
    HIGH(3)
}

sealed interface SuspiciousReason {
    data class Sideloaded(val installerPackageName: String?) : SuspiciousReason
    data class RiskyPermissions(val permissions: List<RiskyPermission>) : SuspiciousReason
    data object HiddenNoLauncher : SuspiciousReason
    data object AccessibilityEnabled : SuspiciousReason
    data object DeviceAdminActive : SuspiciousReason
    data object NotificationListenerEnabled : SuspiciousReason
    data class RecentWithManyPermissions(val permissionCount: Int) : SuspiciousReason
}

data class AppSecurityAssessment(
    val snapshot: PackageSnapshotEntity,
    val riskyPermissions: List<RiskyPermission>,
    val reasons: List<SuspiciousReason>,
    val riskLevel: RiskLevel,
    val riskScore: Int,
    val isTrusted: Boolean
) {
    val isSuspicious: Boolean
        get() = reasons.isNotEmpty()

    val isActiveWarning: Boolean
        get() = isSuspicious && !isTrusted

    val hasAnySpecialAccess: Boolean
        get() = snapshot.hasAccessibilityAccess ||
            snapshot.isDeviceAdmin ||
            snapshot.hasNotificationListener
}

object SecurityEvaluator {
    private const val SEVEN_DAYS_MILLIS: Long = 7L * 24L * 60L * 60L * 1_000L

    private val officialStoreInstallers = setOf(
        "com.android.vending",
        "com.sec.android.app.samsungapps"
    )

    fun isOfficialStoreInstaller(installerPackageName: String?): Boolean {
        return installerPackageName != null && installerPackageName in officialStoreInstallers
    }

    fun evaluate(
        snapshot: PackageSnapshotEntity,
        trustedPackages: Set<String>,
        ownPackageName: String,
        nowMillis: Long = System.currentTimeMillis()
    ): AppSecurityAssessment {
        val riskyPermissions = RiskyPermission.parseCsv(snapshot.riskyPermissionsCsv)
        val isTrusted = snapshot.packageName in trustedPackages

        // Never flag P Guard itself as suspicious
        if (snapshot.packageName == ownPackageName) {
            return AppSecurityAssessment(
                snapshot = snapshot,
                riskyPermissions = riskyPermissions,
                reasons = emptyList(),
                riskLevel = RiskLevel.NONE,
                riskScore = 0,
                isTrusted = isTrusted
            )
        }

        val reasons = mutableListOf<SuspiciousReason>()
        var score = 0

        // 1. Sideloaded: user app not installed by Google Play or Samsung Galaxy Store
        //    (pre-installed system apps with null installer are part of the OS image, not sideloaded)
        val isSideloaded = if (snapshot.isSystemApp) {
            snapshot.installerPackageName != null && !isOfficialStoreInstaller(snapshot.installerPackageName)
        } else {
            !isOfficialStoreInstaller(snapshot.installerPackageName)
        }
        if (isSideloaded) {
            reasons += SuspiciousReason.Sideloaded(snapshot.installerPackageName)
            score += 2
        }

        // 2. Holds risky permissions (flag user apps; system apps are not suspicious merely for OS permissions)
        if (!snapshot.isSystemApp && riskyPermissions.isNotEmpty()) {
            reasons += SuspiciousReason.RiskyPermissions(riskyPermissions)
            score += if (riskyPermissions.size >= 3) 2 else 1
        }

        // 3. Hidden: user-installed app with no launcher icon
        if (!snapshot.isSystemApp && !snapshot.hasLauncherIcon) {
            reasons += SuspiciousReason.HiddenNoLauncher
            score += 2
        }

        // 4. Special sensitive access enabled (Accessibility, Device Admin, or Notification Listener)
        if (snapshot.hasAccessibilityAccess) {
            reasons += SuspiciousReason.AccessibilityEnabled
            score += if (snapshot.isSystemApp) 1 else 3
        }
        if (snapshot.isDeviceAdmin) {
            reasons += SuspiciousReason.DeviceAdminActive
            score += if (snapshot.isSystemApp) 1 else 3
        }
        if (snapshot.hasNotificationListener) {
            reasons += SuspiciousReason.NotificationListenerEnabled
            score += if (snapshot.isSystemApp) 1 else 3
        }

        // 5. Installed in the last 7 days AND has 3 or more risky permissions
        val installedRecently = snapshot.firstInstallTimeMillis > 0L &&
            (nowMillis - snapshot.firstInstallTimeMillis) in 0L..SEVEN_DAYS_MILLIS
        if (!snapshot.isSystemApp && installedRecently && riskyPermissions.size >= 3) {
            reasons += SuspiciousReason.RecentWithManyPermissions(riskyPermissions.size)
            score += 3
        }

        // Pre-installed system components with only standard OS services shouldn't spam Suspicious list
        // unless they were updated from an unofficial installer; they still show in Sensitive Access.
        val effectiveReasons = if (snapshot.isSystemApp && !isSideloaded) {
            emptyList()
        } else {
            reasons
        }

        val riskLevel = when {
            effectiveReasons.isEmpty() -> RiskLevel.NONE
            score >= 5 || effectiveReasons.size >= 3 -> RiskLevel.HIGH
            score >= 3 || effectiveReasons.size == 2 -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }

        return AppSecurityAssessment(
            snapshot = snapshot,
            riskyPermissions = riskyPermissions,
            reasons = effectiveReasons,
            riskLevel = riskLevel,
            riskScore = if (effectiveReasons.isEmpty()) 0 else score,
            isTrusted = isTrusted
        )
    }
}
