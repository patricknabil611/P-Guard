package com.example.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.ScanRecordEntity
import com.example.data.repository.DeviceStatusRepository
import com.example.data.repository.ScanExecutionState
import com.example.data.repository.SecurityScanRepository
import com.example.data.scanner.DeveloperModeStatus
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
    val hiddenUserAppsCount: Int = 0,
    val sensitiveAccessCount: Int = 0,
    val trustedAppsCount: Int = 0,
    val hasUsageAccess: Boolean = false,
    val recentActiveAppsLastHourCount: Int = 0,
    val developerModeStatus: DeveloperModeStatus = DeveloperModeStatus()
) {
    /**
     * Status card counts untrusted suspicious apps + unreviewed new apps + USB Debugging ON (if enabled).
     */
    val totalAttentionCount: Int
        get() = suspiciousAppsCount +
            unreviewedNewAppsCount +
            if (developerModeStatus.isUsbDebuggingEnabled) 1 else 0

    val isAllClear: Boolean
        get() = latestScan != null && totalAttentionCount == 0
}

class DashboardViewModel(
    private val scanRepository: SecurityScanRepository,
    private val deviceStatusRepository: DeviceStatusRepository
) : ViewModel() {

    private val systemLiveState = combine(
        deviceStatusRepository.developerModeStatus,
        deviceStatusRepository.dashboardRecentUsage
    ) { devMode, recentUsage ->
        devMode to recentUsage
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
            trustedCount = trustedApps.size,
            executionState = executionState
        )
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        scanCoreState,
        systemLiveState
    ) { core, system ->
        val (devStatus, recentUsage) = system
        DashboardUiState(
            latestScan = core.latestScan,
            isScanning = core.executionState is ScanExecutionState.Scanning,
            scanError = (core.executionState as? ScanExecutionState.Error)?.message,
            unreviewedNewAppsCount = core.unreviewedCount,
            suspiciousAppsCount = core.suspiciousCount,
            hiddenUserAppsCount = core.hiddenUserCount,
            sensitiveAccessCount = core.sensitiveGrantsCount,
            trustedAppsCount = core.trustedCount,
            hasUsageAccess = recentUsage.hasUsagePermission,
            recentActiveAppsLastHourCount = recentUsage.recentApps.size,
            developerModeStatus = devStatus
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState()
    )

    fun onAppOpened() {
        viewModelScope.launch {
            deviceStatusRepository.refreshSystemStatus()
            scanRepository.runScan(force = false)
        }
    }

    fun onAppResumed() {
        viewModelScope.launch {
            deviceStatusRepository.refreshSystemStatus()
        }
    }

    fun onScanAgainClick() {
        viewModelScope.launch {
            deviceStatusRepository.refreshSystemStatus()
            scanRepository.runScan(force = true)
        }
    }

    private data class ScanCoreSnapshot(
        val latestScan: ScanRecordEntity?,
        val unreviewedCount: Int,
        val suspiciousCount: Int,
        val hiddenUserCount: Int,
        val sensitiveGrantsCount: Int,
        val trustedCount: Int,
        val executionState: ScanExecutionState
    )

    class Factory(
        private val scanRepository: SecurityScanRepository,
        private val deviceStatusRepository: DeviceStatusRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DashboardViewModel(scanRepository, deviceStatusRepository) as T
        }
    }
}
