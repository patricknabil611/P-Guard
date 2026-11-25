package com.example.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.ScanRecordEntity
import com.example.data.preferences.BackgroundScanInterval
import com.example.data.preferences.PGuardPreferencesRepository
import com.example.data.preferences.PGuardSettings
import com.example.data.repository.DeviceStatusRepository
import com.example.data.repository.ScanExecutionState
import com.example.data.repository.SecurityScanRepository
import com.example.data.scanner.DeveloperModeStatus
import com.example.data.scanner.DevicePostureSnapshot
import com.example.data.scanner.EnabledKeyboardInfo
import com.example.data.scanner.NetworkUsageScanner
import com.example.data.scanner.NetworkUsageSnapshot
import com.example.data.scanner.RootDetectionResult
import com.example.data.scanner.SecurityScoreEvaluator
import com.example.data.scanner.SecurityScoreReport
import com.example.data.scanner.UsageActivitySnapshot
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardUiState(
    val latestScan: ScanRecordEntity? = null,
    val isScanning: Boolean = true,
    val scanError: String? = null,
    val unreviewedNewAppsCount: Int = 0,
    val suspiciousAppsCount: Int = 0,
    val signatureChangesCount: Int = 0,
    val permissionChangesCount: Int = 0,
    val hiddenUserAppsCount: Int = 0,
    val sensitiveAccessCount: Int = 0,
    val trustedAppsCount: Int = 0,
    val scanHistoryCount: Int = 0,
    val hasUsageAccess: Boolean = false,
    val recentActiveAppsLastHourCount: Int = 0,
    val enabledKeyboardsCount: Int = 0,
    val thirdPartyKeyboardsCount: Int = 0,
    val highBackgroundDataAppsCount: Int = 0,
    val activeBackgroundDataAppsCount: Int = 0,
    val developerModeStatus: DeveloperModeStatus = DeveloperModeStatus(),
    val rootResult: RootDetectionResult = RootDetectionResult(),
    val securityScoreReport: SecurityScoreReport = SecurityScoreReport(),
    val backgroundScanEnabled: Boolean = true,
    val notificationPromptDismissed: Boolean = false,
    val batteryPromptDismissed: Boolean = false
) {
    val totalAttentionCount: Int
        get() = suspiciousAppsCount +
            unreviewedNewAppsCount +
            permissionChangesCount +
            thirdPartyKeyboardsCount +
            (if (developerModeStatus.isUsbDebuggingEnabled) 1 else 0) +
            (if (rootResult.isRooted) 1 else 0)

    val isAllClear: Boolean
        get() = latestScan != null && totalAttentionCount == 0
}

class DashboardViewModel(
    private val scanRepository: SecurityScanRepository,
    private val deviceStatusRepository: DeviceStatusRepository,
    private val preferencesRepository: PGuardPreferencesRepository
) : ViewModel() {

    private val systemLiveState = combine(
        deviceStatusRepository.developerModeStatus,
        deviceStatusRepository.dashboardRecentUsage,
        deviceStatusRepository.enabledKeyboards,
        deviceStatusRepository.dashboardDataUsage24h,
        deviceStatusRepository.devicePosture
    ) { devMode, recentUsage, keyboards, dataUsage24h, posture ->
        SystemLiveSnapshot(
            developerMode = devMode,
            recentUsage = recentUsage,
            keyboards = keyboards,
            dataUsage24h = dataUsage24h,
            posture = posture
        )
    }

    private val scanCoreState = combine(
        scanRepository.latestScan,
        scanRepository.unreviewedNewApps,
        scanRepository.securityAssessments,
        scanRepository.trustedApps,
        scanRepository.executionState
    ) { latestScan, unreviewedNewApps, assessments, trustedApps, executionState ->
        ScanCoreSnapshot(
            latestScan = latestScan,
            unreviewedCount = unreviewedNewApps.size,
            suspiciousCount = assessments.count { it.isActiveWarning },
            suspiciousNonSigCount = assessments.count { it.isActiveWarning && !it.hasSignatureChanged },
            hiddenUserCount = assessments.count { item ->
                val pkg = item.snapshot
                !pkg.hasLauncherIcon && !pkg.isSystemApp && pkg.packageName != scanRepository.ownPackageName
            },
            sensitiveGrantsCount = assessments.sumOf { item ->
                var grants = 0
                if (item.snapshot.hasAccessibilityAccess) grants++
                if (item.snapshot.isDeviceAdmin) grants++
                if (item.snapshot.hasNotificationListener) grants++
                grants
            },
            trustedPackages = trustedApps.map { it.packageName }.toSet(),
            executionState = executionState
        )
    }

    private val phase4And5State = combine(
        scanRepository.activeSignatureChanges,
        scanRepository.unreviewedPermissionChanges,
        scanRepository.scanHistoryCount,
        preferencesRepository.settings
    ) { sigChanges, permChanges, historyCount, settings ->
        PhaseAlertsSnapshot(
            signatureChangesCount = sigChanges.size,
            permissionChangesCount = permChanges.size,
            scanHistoryCount = historyCount,
            settings = settings
        )
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        scanCoreState,
        phase4And5State,
        systemLiveState
    ) { core, alerts, system ->
        val scoreReport = SecurityScoreEvaluator.evaluate(
            posture = system.posture,
            developerMode = system.developerMode,
            untrustedSuspiciousCount = core.suspiciousNonSigCount,
            signatureChangeCount = alerts.signatureChangesCount,
            trustedPackages = core.trustedPackages
        )

        val highBgAppsCount = if (system.dataUsage24h.hasUsagePermission) {
            system.dataUsage24h.apps.count { usage ->
                usage.packageName !in core.trustedPackages &&
                    usage.totalBackgroundBytes > NetworkUsageScanner.HIGH_BACKGROUND_DATA_24H_BYTES
            }
        } else {
            0
        }

        DashboardUiState(
            latestScan = core.latestScan,
            isScanning = core.executionState is ScanExecutionState.Scanning,
            scanError = (core.executionState as? ScanExecutionState.Error)?.message,
            unreviewedNewAppsCount = core.unreviewedCount,
            suspiciousAppsCount = core.suspiciousCount,
            signatureChangesCount = alerts.signatureChangesCount,
            permissionChangesCount = alerts.permissionChangesCount,
            hiddenUserAppsCount = core.hiddenUserCount,
            sensitiveAccessCount = core.sensitiveGrantsCount,
            trustedAppsCount = core.trustedPackages.size,
            scanHistoryCount = alerts.scanHistoryCount,
            hasUsageAccess = system.recentUsage.hasUsagePermission,
            recentActiveAppsLastHourCount = system.recentUsage.recentApps.size,
            enabledKeyboardsCount = system.keyboards.size,
            thirdPartyKeyboardsCount = system.keyboards.count { !it.isKnownPublisher },
            highBackgroundDataAppsCount = highBgAppsCount,
            activeBackgroundDataAppsCount = system.dataUsage24h.apps.size,
            developerModeStatus = system.developerMode,
            rootResult = system.posture.rootResult,
            securityScoreReport = scoreReport,
            backgroundScanEnabled = alerts.settings.scanInterval != BackgroundScanInterval.OFF,
            notificationPromptDismissed = alerts.settings.notificationPromptDismissed,
            batteryPromptDismissed = alerts.settings.batteryPromptDismissed
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState()
    )

    fun onAppOpened() {
        viewModelScope.launch {
            runFullScan(force = false)
        }
    }

    fun onAppResumed() {
        viewModelScope.launch {
            deviceStatusRepository.refreshSystemStatus()
        }
    }

    fun onScanAgainClick() {
        viewModelScope.launch {
            runFullScan(force = true)
        }
    }

    fun dismissNotificationPrompt() {
        viewModelScope.launch {
            preferencesRepository.dismissNotificationPrompt()
        }
    }

    fun dismissBatteryPrompt() {
        viewModelScope.launch {
            preferencesRepository.dismissBatteryPrompt()
        }
    }

    private suspend fun runFullScan(force: Boolean) {
        deviceStatusRepository.refreshSystemStatus()
        val posture = deviceStatusRepository.devicePosture.value
        val devMode = deviceStatusRepository.developerModeStatus.value
        val settings = preferencesRepository.getSettingsOnce()
        val result = scanRepository.runScanDetailed(
            force = force,
            isBackground = false,
            devicePosture = posture,
            developerModeStatus = devMode,
            previousDevOptionsOn = settings.lastKnownDevOptionsOn,
            previousUsbDebuggingOn = settings.lastKnownUsbDebuggingOn,
            hasDevBaseline = settings.hasRecordedDevBaseline
        )
        if (result.succeeded) {
            preferencesRepository.updateLastKnownDeveloperMode(
                devOptionsOn = devMode.isDeveloperOptionsEnabled,
                usbDebuggingOn = devMode.isUsbDebuggingEnabled
            )
        }
    }

    private data class ScanCoreSnapshot(
        val latestScan: ScanRecordEntity?,
        val unreviewedCount: Int,
        val suspiciousCount: Int,
        val suspiciousNonSigCount: Int,
        val hiddenUserCount: Int,
        val sensitiveGrantsCount: Int,
        val trustedPackages: Set<String>,
        val executionState: ScanExecutionState
    )

    private data class PhaseAlertsSnapshot(
        val signatureChangesCount: Int,
        val permissionChangesCount: Int,
        val scanHistoryCount: Int,
        val settings: PGuardSettings
    )

    private data class SystemLiveSnapshot(
        val developerMode: DeveloperModeStatus,
        val recentUsage: UsageActivitySnapshot,
        val keyboards: List<EnabledKeyboardInfo>,
        val dataUsage24h: NetworkUsageSnapshot,
        val posture: DevicePostureSnapshot
    )

    class Factory(
        private val scanRepository: SecurityScanRepository,
        private val deviceStatusRepository: DeviceStatusRepository,
        private val preferencesRepository: PGuardPreferencesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DashboardViewModel(
                scanRepository,
                deviceStatusRepository,
                preferencesRepository
            ) as T
        }
    }
}
