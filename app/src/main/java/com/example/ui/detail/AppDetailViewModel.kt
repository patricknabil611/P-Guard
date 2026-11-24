package com.example.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.DeviceStatusRepository
import com.example.data.repository.SecurityScanRepository
import com.example.data.scanner.AppSecurityAssessment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppDetailUiState(
    val packageName: String = "",
    val assessment: AppSecurityAssessment? = null,
    val hasUsageAccess: Boolean = false,
    val lastUsedMillis: Long? = null,
    val isLoading: Boolean = true
)

class AppDetailViewModel(
    private val packageName: String,
    private val scanRepository: SecurityScanRepository,
    private val deviceStatusRepository: DeviceStatusRepository
) : ViewModel() {

    private val usageInfo = MutableStateFlow<Pair<Boolean, Long?>>(false to null)

    val uiState: StateFlow<AppDetailUiState> = combine(
        scanRepository.observeAssessmentForPackage(packageName),
        usageInfo
    ) { assessment, usage ->
        val (hasUsageAccess, lastUsedMillis) = usage
        AppDetailUiState(
            packageName = packageName,
            assessment = assessment,
            hasUsageAccess = hasUsageAccess,
            lastUsedMillis = lastUsedMillis,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppDetailUiState(packageName = packageName, isLoading = true)
    )

    init {
        refreshUsageInfo()
    }

    fun refreshUsageInfo() {
        viewModelScope.launch {
            usageInfo.value = deviceStatusRepository.queryLastUsedForPackage(packageName)
        }
    }

    fun onToggleTrusted() {
        val current = uiState.value.assessment ?: return
        viewModelScope.launch {
            if (current.isTrusted) {
                scanRepository.removeTrustedApp(current.snapshot.packageName)
            } else {
                scanRepository.markAppTrusted(
                    packageName = current.snapshot.packageName,
                    appName = current.snapshot.appName
                )
            }
        }
    }

    class Factory(
        private val packageName: String,
        private val scanRepository: SecurityScanRepository,
        private val deviceStatusRepository: DeviceStatusRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AppDetailViewModel(packageName, scanRepository, deviceStatusRepository) as T
        }
    }
}
