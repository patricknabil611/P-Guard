package com.example.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.ScanHistoryEntity
import com.example.data.preferences.BackgroundScanInterval
import com.example.data.preferences.PGuardPreferencesRepository
import com.example.data.preferences.PGuardSettings
import com.example.data.repository.DeviceStatusRepository
import com.example.data.repository.SecurityScanRepository
import com.example.security.AppLockManager
import com.example.worker.BackgroundScanScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: PGuardSettings = PGuardSettings(),
    val trustedAppsCount: Int = 0,
    val historyEntriesCount: Int = 0
)

class SettingsViewModel(
    private val preferencesRepository: PGuardPreferencesRepository,
    private val scanRepository: SecurityScanRepository,
    private val deviceStatusRepository: DeviceStatusRepository,
    private val appLockManager: AppLockManager
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesRepository.settings,
        scanRepository.trustedApps,
        scanRepository.scanHistoryCount
    ) { settings, trustedApps, historyCount ->
        SettingsUiState(
            settings = settings,
            trustedAppsCount = trustedApps.size,
            historyEntriesCount = historyCount
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState()
    )

    fun onSelectScanInterval(context: Context, interval: BackgroundScanInterval) {
        viewModelScope.launch {
            preferencesRepository.setScanInterval(interval)
            BackgroundScanScheduler.syncSchedule(context, interval)
        }
    }

    fun onToggleNotifyNewApp(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setNotifyNewApp(enabled) }
    }

    fun onToggleNotifySignature(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setNotifySignatureChanged(enabled) }
    }

    fun onToggleNotifySuspicious(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setNotifyNewSuspicious(enabled) }
    }

    fun onToggleNotifyRiskyPermission(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setNotifyNewRiskyPermission(enabled) }
    }

    fun onToggleNotifyDevMode(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setNotifyDeveloperMode(enabled) }
    }

    fun setAppLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setAppLockEnabled(enabled)
            appLockManager.onLockSettingChanged(enabled)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            scanRepository.clearAllScanHistory()
        }
    }

    suspend fun getOrCreateLatestHistoryForExport(): ScanHistoryEntity? {
        val existing = scanRepository.getLatestScanHistoryOnce()
        if (existing != null) return existing
        deviceStatusRepository.refreshSystemStatus()
        scanRepository.runScanDetailed(
            force = true,
            isBackground = false,
            devicePosture = deviceStatusRepository.devicePosture.value,
            developerModeStatus = deviceStatusRepository.developerModeStatus.value
        )
        return scanRepository.getLatestScanHistoryOnce()
    }

    class Factory(
        private val preferencesRepository: PGuardPreferencesRepository,
        private val scanRepository: SecurityScanRepository,
        private val deviceStatusRepository: DeviceStatusRepository,
        private val appLockManager: AppLockManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(
                preferencesRepository,
                scanRepository,
                deviceStatusRepository,
                appLockManager
            ) as T
        }
    }
}
