package com.example.data.scanner

import android.app.AppOpsManager
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import java.security.KeyStore
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class ChecklistStatus {
    PASS,
    WARNING,
    FAIL,
    INFO
}

enum class SecurityCheckFixAction {
    NONE,
    OPEN_SECURITY_SETTINGS,
    OPEN_DEVICE_INFO_SETTINGS,
    OPEN_UNKNOWN_APP_SOURCES,
    OPEN_DEVELOPER_MODE_SCREEN,
    OPEN_SUSPICIOUS_APPS_SCREEN
}

enum class SecurityChecklistId {
    SCREEN_LOCK,
    SECURITY_PATCH,
    UNKNOWN_APP_INSTALLERS,
    USER_CA_CERTS,
    VPN_ACTIVE,
    DEVELOPER_MODE,
    ROOT_STATUS,
    SUSPICIOUS_APPS,
    SIGNATURE_CHANGES
}

data class SecurityChecklistItem(
    val id: SecurityChecklistId,
    val status: ChecklistStatus,
    val fixAction: SecurityCheckFixAction,
    val detailValue: String = "",
    val count: Int = 0
)

data class DevicePostureSnapshot(
    val isDeviceSecure: Boolean = true,
    val securityPatchDate: String = "",
    val unknownAppInstallerPackages: List<String> = emptyList(),
    val userCaCertificateCount: Int = 0,
    val isVpnActive: Boolean = false,
    val rootResult: RootDetectionResult = RootDetectionResult()
)

data class SecurityScoreReport(
    val score: Int = 100,
    val items: List<SecurityChecklistItem> = emptyList(),
    val rootResult: RootDetectionResult = RootDetectionResult()
)

interface DevicePostureReader {
    fun readPosture(): DevicePostureSnapshot
}

class AndroidDevicePostureReader(
    private val context: Context,
    private val rootDetector: RootDetector
) : DevicePostureReader {

    override fun readPosture(): DevicePostureSnapshot {
        return DevicePostureSnapshot(
            isDeviceSecure = readIsDeviceSecure(),
            securityPatchDate = Build.VERSION.SECURITY_PATCH.orEmpty(),
            unknownAppInstallerPackages = readAllowedUnknownAppInstallers(),
            userCaCertificateCount = readUserCaCertificateCount(),
            isVpnActive = readIsVpnActive(),
            rootResult = rootDetector.detectRoot()
        )
    }

    private fun readIsDeviceSecure(): Boolean {
        return try {
            val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            km?.isDeviceSecure ?: false
        } catch (_: Exception) {
            false
        }
    }

    private fun readAllowedUnknownAppInstallers(): List<String> {
        val pm = context.packageManager
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
        val allowed = mutableListOf<String>()

        val apps = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledApplications(0)
            }
        } catch (_: Exception) {
            return emptyList()
        }

        val opName = "android:request_install_packages"
        for (app in apps) {
            val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            if (isSystem || app.packageName == context.packageName) continue

            val isAllowed = try {
                if (appOps != null) {
                    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        appOps.unsafeCheckOpNoThrow(opName, app.uid, app.packageName)
                    } else {
                        @Suppress("DEPRECATION")
                        appOps.checkOpNoThrow(opName, app.uid, app.packageName)
                    }
                    mode == AppOpsManager.MODE_ALLOWED
                } else {
                    false
                }
            } catch (_: Exception) {
                false
            }

            if (isAllowed) {
                allowed += app.packageName
            }
        }
        return allowed
    }

    private fun readUserCaCertificateCount(): Int {
        return try {
            val keyStore = KeyStore.getInstance("AndroidCAStore")
            keyStore.load(null)
            var count = 0
            val aliases = keyStore.aliases()
            while (aliases.hasMoreElements()) {
                val alias = aliases.nextElement()
                if (alias.startsWith("user:")) {
                    count++
                }
            }
            count
        } catch (_: Exception) {
            0
        }
    }

    private fun readIsVpnActive(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val activeNetwork = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        } catch (_: Exception) {
            false
        }
    }
}

object SecurityScoreEvaluator {
    fun evaluate(
        posture: DevicePostureSnapshot,
        developerMode: DeveloperModeStatus,
        untrustedSuspiciousCount: Int,
        signatureChangeCount: Int,
        trustedPackages: Set<String>,
        today: LocalDate = LocalDate.now()
    ): SecurityScoreReport {
        var deductions = 0
        val items = mutableListOf<SecurityChecklistItem>()

        // 1. Screen lock set (serious: -18)
        val screenLockPass = posture.isDeviceSecure
        if (!screenLockPass) deductions += 18
        items += SecurityChecklistItem(
            id = SecurityChecklistId.SCREEN_LOCK,
            status = if (screenLockPass) ChecklistStatus.PASS else ChecklistStatus.FAIL,
            fixAction = if (screenLockPass) SecurityCheckFixAction.NONE else SecurityCheckFixAction.OPEN_SECURITY_SETTINGS
        )

        // 2. Security patch age: < 3 months PASS, 3-6 months WARNING (-5), > 6 months FAIL (-10)
        val patchStatus = evaluateSecurityPatchStatus(posture.securityPatchDate, today)
        when (patchStatus) {
            ChecklistStatus.WARNING -> deductions += 5
            ChecklistStatus.FAIL -> deductions += 10
            else -> Unit
        }
        items += SecurityChecklistItem(
            id = SecurityChecklistId.SECURITY_PATCH,
            status = patchStatus,
            fixAction = if (patchStatus == ChecklistStatus.PASS) {
                SecurityCheckFixAction.NONE
            } else {
                SecurityCheckFixAction.OPEN_DEVICE_INFO_SETTINGS
            },
            detailValue = posture.securityPatchDate
        )

        // 3. Apps allowed to "Install unknown apps" besides trusted (-8)
        val untrustedInstallers = posture.unknownAppInstallerPackages.filter { it !in trustedPackages }
        val installersPass = untrustedInstallers.isEmpty()
        if (!installersPass) deductions += 8
        items += SecurityChecklistItem(
            id = SecurityChecklistId.UNKNOWN_APP_INSTALLERS,
            status = if (installersPass) ChecklistStatus.PASS else ChecklistStatus.WARNING,
            fixAction = if (installersPass) SecurityCheckFixAction.NONE else SecurityCheckFixAction.OPEN_UNKNOWN_APP_SOURCES,
            count = untrustedInstallers.size
        )

        // 4. User-installed CA certificates (serious: -15)
        val caPass = posture.userCaCertificateCount == 0
        if (!caPass) deductions += 15
        items += SecurityChecklistItem(
            id = SecurityChecklistId.USER_CA_CERTS,
            status = if (caPass) ChecklistStatus.PASS else ChecklistStatus.FAIL,
            fixAction = if (caPass) SecurityCheckFixAction.NONE else SecurityCheckFixAction.OPEN_SECURITY_SETTINGS,
            count = posture.userCaCertificateCount
        )

        // 5. VPN active (info only — 0 deduction)
        items += SecurityChecklistItem(
            id = SecurityChecklistId.VPN_ACTIVE,
            status = ChecklistStatus.INFO,
            fixAction = SecurityCheckFixAction.NONE,
            count = if (posture.isVpnActive) 1 else 0
        )

        // 6. Developer Options off and USB Debugging off (-10 if ADB on, -4 if only Dev Options on)
        val devStatus = when {
            developerMode.isUsbDebuggingEnabled -> {
                deductions += 10
                ChecklistStatus.FAIL
            }
            developerMode.isDeveloperOptionsEnabled -> {
                deductions += 4
                ChecklistStatus.WARNING
            }
            else -> ChecklistStatus.PASS
        }
        items += SecurityChecklistItem(
            id = SecurityChecklistId.DEVELOPER_MODE,
            status = devStatus,
            fixAction = if (devStatus == ChecklistStatus.PASS) {
                SecurityCheckFixAction.NONE
            } else {
                SecurityCheckFixAction.OPEN_DEVELOPER_MODE_SCREEN
            }
        )

        // 7. Device not rooted (serious: -20)
        val rootPass = !posture.rootResult.isRooted
        if (!rootPass) deductions += 20
        items += SecurityChecklistItem(
            id = SecurityChecklistId.ROOT_STATUS,
            status = if (rootPass) ChecklistStatus.PASS else ChecklistStatus.FAIL,
            fixAction = SecurityCheckFixAction.NONE,
            count = posture.rootResult.triggeredChecks.size
        )

        // 8. No suspicious apps (excluding trusted) (up to -10)
        val suspiciousPass = untrustedSuspiciousCount == 0
        if (!suspiciousPass) {
            deductions += when {
                untrustedSuspiciousCount == 1 -> 4
                untrustedSuspiciousCount == 2 -> 7
                else -> 10
            }
        }
        items += SecurityChecklistItem(
            id = SecurityChecklistId.SUSPICIOUS_APPS,
            status = if (suspiciousPass) ChecklistStatus.PASS else ChecklistStatus.WARNING,
            fixAction = if (suspiciousPass) SecurityCheckFixAction.NONE else SecurityCheckFixAction.OPEN_SUSPICIOUS_APPS_SCREEN,
            count = untrustedSuspiciousCount
        )

        // 9. No signature changes (serious: -15)
        val sigPass = signatureChangeCount == 0
        if (!sigPass) deductions += 15
        items += SecurityChecklistItem(
            id = SecurityChecklistId.SIGNATURE_CHANGES,
            status = if (sigPass) ChecklistStatus.PASS else ChecklistStatus.FAIL,
            fixAction = if (sigPass) SecurityCheckFixAction.NONE else SecurityCheckFixAction.OPEN_SUSPICIOUS_APPS_SCREEN,
            count = signatureChangeCount
        )

        val finalScore = (100 - deductions).coerceIn(0, 100)
        return SecurityScoreReport(
            score = finalScore,
            items = items,
            rootResult = posture.rootResult
        )
    }

    fun evaluateSecurityPatchStatus(
        patchDateStr: String,
        today: LocalDate = LocalDate.now()
    ): ChecklistStatus {
        if (patchDateStr.isBlank()) return ChecklistStatus.WARNING
        return try {
            val patchDate = LocalDate.parse(patchDateStr.trim())
            val monthsOld = ChronoUnit.MONTHS.between(patchDate, today)
            when {
                monthsOld < 3L -> ChecklistStatus.PASS
                monthsOld <= 6L -> ChecklistStatus.WARNING
                else -> ChecklistStatus.FAIL
            }
        } catch (_: Exception) {
            ChecklistStatus.WARNING
        }
    }
}
