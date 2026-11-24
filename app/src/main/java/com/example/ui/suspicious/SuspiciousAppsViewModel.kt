package com.example.ui.suspicious

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

data class SuspiciousAppsUiState(
    val latestScan: ScanRecordEntity? = null,
    val isScanning: Boolean = false,
    val flaggedApps: List<AppSecurityAssessment> = emptyList(),
    val totalFlaggedCount: Int = 0,
    val trustedCount: Int = 0,
    val searchQuery: String = "",
    val sortOption: AppSortOption = AppSortOption.RISK
)

class SuspiciousAppsViewModel(
    private val repository: SecurityScanRepository
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val sortOption = MutableStateFlow(AppSortOption.RISK)

    val uiState: StateFlow<SuspiciousAppsUiState> = combine(
        repository.latestScan,
        repository.executionState,
        repository.securityAssessments,
        repository.trustedApps,
        combine(searchQuery, sortOption) { q, s -> q to s }
    ) { latestScan, execState, assessments, trusted, controls ->
        val (query, sort) = controls
        val normalized = query.trim().lowercase()

        val allFlagged = assessments.filter { it.isActiveWarning }
        val filtered = allFlagged.filter { item ->
            normalized.isEmpty() ||
                item.snapshot.appName.lowercase().contains(normalized) ||
                item.snapshot.packageName.lowercase().contains(normalized)
        }

        SuspiciousAppsUiState(
            latestScan = latestScan,
            isScanning = execState is ScanExecutionState.Scanning,
            flaggedApps = sortAssessments(filtered, sort),
            totalFlaggedCount = allFlagged.size,
            trustedCount = trusted.size,
            searchQuery = query,
            sortOption = sort
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SuspiciousAppsUiState()
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

    fun onMarkTrusted(packageName: String, appName: String) {
        viewModelScope.launch {
            repository.markAppTrusted(packageName, appName)
        }
    }

    class Factory(
        private val repository: SecurityScanRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SuspiciousAppsViewModel(repository) as T
        }
    }
}
