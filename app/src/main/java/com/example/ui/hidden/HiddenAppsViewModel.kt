package com.example.ui.hidden

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

data class HiddenAppsUiState(
    val latestScan: ScanRecordEntity? = null,
    val isScanning: Boolean = false,
    val hiddenApps: List<AppSecurityAssessment> = emptyList(),
    val includeSystemApps: Boolean = false,
    val searchQuery: String = "",
    val sortOption: AppSortOption = AppSortOption.NAME
)

class HiddenAppsViewModel(
    private val repository: SecurityScanRepository
) : ViewModel() {

    private val includeSystemApps = MutableStateFlow(false)
    private val searchQuery = MutableStateFlow("")
    private val sortOption = MutableStateFlow(AppSortOption.NAME)

    private val controls = combine(includeSystemApps, searchQuery, sortOption) { sys, q, sort ->
        Triple(sys, q, sort)
    }

    val uiState: StateFlow<HiddenAppsUiState> = combine(
        repository.latestScan,
        repository.executionState,
        repository.securityAssessments,
        controls
    ) { latestScan, execState, assessments, ctrl ->
        val (showSystem, query, sort) = ctrl
        val normalized = query.trim().lowercase()

        val hidden = assessments.filter { item ->
            val pkg = item.snapshot
            !pkg.hasLauncherIcon &&
                pkg.packageName != repository.ownPackageName &&
                (showSystem || !pkg.isSystemApp)
        }.filter { item ->
            normalized.isEmpty() ||
                item.snapshot.appName.lowercase().contains(normalized) ||
                item.snapshot.packageName.lowercase().contains(normalized)
        }

        HiddenAppsUiState(
            latestScan = latestScan,
            isScanning = execState is ScanExecutionState.Scanning,
            hiddenApps = sortAssessments(hidden, sort),
            includeSystemApps = showSystem,
            searchQuery = query,
            sortOption = sort
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HiddenAppsUiState()
    )

    fun onScanAgain() {
        viewModelScope.launch { repository.runScan(force = true) }
    }

    fun onToggleIncludeSystemApps(include: Boolean) {
        includeSystemApps.value = include
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
            return HiddenAppsViewModel(repository) as T
        }
    }
}
