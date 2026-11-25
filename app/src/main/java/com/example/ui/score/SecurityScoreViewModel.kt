package com.example.ui.score

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.DeviceStatusRepository
import com.example.data.repository.SecurityScanRepository
import com.example.data.scanner.SecurityScoreEvaluator
import com.example.data.scanner.SecurityScoreReport
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SecurityScoreUiState(
    val report: SecurityScoreReport = SecurityScoreReport()
)

class SecurityScoreViewModel(
    private val scanRepository: SecurityScanRepository,
    private val deviceStatusRepository: DeviceStatusRepository
) : ViewModel() {

    val uiState: StateFlow<SecurityScoreUiState> = combine(
        deviceStatusRepository.devicePosture,
        deviceStatusRepository.developerModeStatus,
        scanRepository.securityAssessments,
        scanRepository.activeSignatureChanges,
        scanRepository.trustedApps
    ) { posture, devMode, assessments, sigChanges, trustedApps ->
        val trustedSet = trustedApps.map { it.packageName }.toSet()
        val untrustedSuspiciousCount = assessments.count { it.isActiveWarning && !it.hasSignatureChanged }
        val report = SecurityScoreEvaluator.evaluate(
            posture = posture,
            developerMode = devMode,
            untrustedSuspiciousCount = untrustedSuspiciousCount,
            signatureChangeCount = sigChanges.size,
            trustedPackages = trustedSet
        )
        SecurityScoreUiState(report = report)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SecurityScoreUiState()
    )

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            deviceStatusRepository.refreshDevicePosture()
        }
    }

    class Factory(
        private val scanRepository: SecurityScanRepository,
        private val deviceStatusRepository: DeviceStatusRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SecurityScoreViewModel(scanRepository, deviceStatusRepository) as T
        }
    }
}
