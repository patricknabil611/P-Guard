package com.example.ui.sensitive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.ScanRecordEntity
import com.example.data.repository.ScanExecutionState
import com.example.data.repository.SecurityScanRepository
import com.example.data.scanner.AppSecurityAssessment
import com.example.ui.common.AppSortOption
import com.example.ui.common.sortAssessments
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SensitiveAccessUiState(
    val latestScan: ScanRecordEntity? = null,
    val isScanning: Boolean = false,
    val accessibilityApps: List<AppSecurityAssessment> = emptyList(),
    val deviceAdminApps: List<AppSecurityAssessment> = emptyList(),
    val notificationListenerApps: List<AppSecurityAssessment> = emptyList(),
    val searchQuery: String = "",
    val sortOption: AppSortOption = AppSortOption.RISK
)

class SensitiveAccessViewModel(
    private val repository: SecurityScanRepository
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val sortOption = MutableStateFlow(AppSortOption.RISK)

    val uiState: StateFlow<SensitiveAccessUiState> = combine(
        repository.latestScan,
        repository.executionState,
        repository.securityAssessments,
        searchQuery,
        sortOption
    ) { latestScan, execState, assessments, query, sort ->
        val normalized = query.trim().lowercase()

        fun filterAndSort(predicate: (AppSecurityAssessment) -> Boolean): List<AppSecurityAssessment> {
            val matching = assessments.filter { item ->
                predicate(item) && (
                    normalized.isEmpty() ||
                        item.snapshot.appName.lowercase().contains(normalized) ||
                        item.snapshot.packageName.lowercase().contains(normalized)
                    )
            }
            return sortAssessments(matching, sort)
        }

        SensitiveAccessUiState(
            latestScan = latestScan,
            isScanning = execState is ScanExecutionState.Scanning,
            accessibilityApps = filterAndSort { it.snapshot.hasAccessibilityAccess },
            deviceAdminApps = filterAndSort { it.snapshot.isDeviceAdmin },
            notificationListenerApps = filterAndSort { it.snapshot.hasNotificationListener },
            searchQuery = query,
            sortOption = sort
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SensitiveAccessUiState()
    )

    fun onScanAgain() {
        viewModelScope.launch { repository.runScan(force = true) }
    }

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    fun onSortOptionChange(option: AppSortOption) {
        sortOption.value = option
    }

    class Factory(
        private val repository: SecurityScanRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SensitiveAccessViewModel(repository) as T
        }
    }
}
