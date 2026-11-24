package com.example.ui.newapps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.DetectedNewAppEntity
import com.example.data.local.entity.ScanRecordEntity
import com.example.data.repository.ScanExecutionState
import com.example.data.repository.SecurityScanRepository
import com.example.data.scanner.AppSecurityAssessment
import com.example.data.scanner.RiskLevel
import com.example.ui.common.AppSortOption
import com.example.ui.common.sortAssessments
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class NewAppsTab {
    NEW_APPS,
    SNAPSHOT
}

enum class SnapshotFilter {
    ALL,
    USER_APPS,
    SYSTEM_APPS
}

data class DetectedAppDisplayItem(
    val entity: DetectedNewAppEntity,
    val assessment: AppSecurityAssessment?
) {
    val riskLevel: RiskLevel
        get() = assessment?.riskLevel ?: RiskLevel.NONE

    val riskScore: Int
        get() = assessment?.riskScore ?: 0
}

data class NewAppsUiState(
    val latestScan: ScanRecordEntity? = null,
    val isScanning: Boolean = false,
    val unreviewedApps: List<DetectedAppDisplayItem> = emptyList(),
    val reviewedOrRemovedApps: List<DetectedAppDisplayItem> = emptyList(),
    val totalUnreviewedCount: Int = 0,
    val filteredSnapshotAssessments: List<AppSecurityAssessment> = emptyList(),
    val totalSnapshotCount: Int = 0,
    val selectedTab: NewAppsTab = NewAppsTab.NEW_APPS,
    val searchQuery: String = "",
    val sortOption: AppSortOption = AppSortOption.INSTALL_DATE,
    val snapshotFilter: SnapshotFilter = SnapshotFilter.ALL
)

class NewAppsViewModel(
    private val repository: SecurityScanRepository
) : ViewModel() {

    private val selectedTab = MutableStateFlow(NewAppsTab.NEW_APPS)
    private val searchQuery = MutableStateFlow("")
    private val sortOption = MutableStateFlow(AppSortOption.INSTALL_DATE)
    private val snapshotFilter = MutableStateFlow(SnapshotFilter.ALL)

    private val controls = combine(
        selectedTab,
        searchQuery,
        sortOption,
        snapshotFilter
    ) { tab, query, sort, filter ->
        FilterControls(tab, query, sort, filter)
    }

    val uiState: StateFlow<NewAppsUiState> = combine(
        repository.latestScan,
        repository.executionState,
        repository.unreviewedNewApps,
        repository.reviewedOrRemovedApps,
        combine(repository.securityAssessments, controls) { assessments, ctrl ->
            assessments to ctrl
        }
    ) { latestScan, execState, unreviewed, reviewed, assessmentAndCtrl ->
        val (assessments, ctrl) = assessmentAndCtrl
        val assessmentMap = assessments.associateBy { it.snapshot.packageName }
        val normalizedQuery = ctrl.searchQuery.trim().lowercase()

        fun processDetected(list: List<DetectedNewAppEntity>): List<DetectedAppDisplayItem> {
            val filtered = list.map { entity ->
                DetectedAppDisplayItem(entity = entity, assessment = assessmentMap[entity.packageName])
            }.filter { item ->
                normalizedQuery.isEmpty() ||
                    item.entity.appName.lowercase().contains(normalizedQuery) ||
                    item.entity.packageName.lowercase().contains(normalizedQuery)
            }

            return when (ctrl.sortOption) {
                AppSortOption.NAME -> filtered.sortedBy { it.entity.appName.lowercase() }
                AppSortOption.INSTALL_DATE -> filtered.sortedWith(
                    compareByDescending<DetectedAppDisplayItem> { it.entity.firstInstallTimeMillis }
                        .thenBy { it.entity.appName.lowercase() }
                )
                AppSortOption.RISK -> filtered.sortedWith(
                    compareByDescending<DetectedAppDisplayItem> { it.riskLevel.sortOrder }
                        .thenByDescending { it.riskScore }
                        .thenBy { it.entity.appName.lowercase() }
                )
            }
        }

        val filteredSnapshots = assessments.filter { item ->
            val pkg = item.snapshot
            val matchesFilter = when (ctrl.snapshotFilter) {
                SnapshotFilter.ALL -> true
                SnapshotFilter.USER_APPS -> !pkg.isSystemApp
                SnapshotFilter.SYSTEM_APPS -> pkg.isSystemApp
            }
            val matchesQuery = normalizedQuery.isEmpty() ||
                pkg.appName.lowercase().contains(normalizedQuery) ||
                pkg.packageName.lowercase().contains(normalizedQuery)
            matchesFilter && matchesQuery
        }

        NewAppsUiState(
            latestScan = latestScan,
            isScanning = execState is ScanExecutionState.Scanning,
            unreviewedApps = processDetected(unreviewed),
            reviewedOrRemovedApps = processDetected(reviewed),
            totalUnreviewedCount = unreviewed.size,
            filteredSnapshotAssessments = sortAssessments(filteredSnapshots, ctrl.sortOption),
            totalSnapshotCount = assessments.size,
            selectedTab = ctrl.tab,
            searchQuery = ctrl.searchQuery,
            sortOption = ctrl.sortOption,
            snapshotFilter = ctrl.snapshotFilter
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = NewAppsUiState()
    )

    fun onScanAgain() {
        viewModelScope.launch { repository.runScan(force = true) }
    }

    fun onSelectTab(tab: NewAppsTab) {
        selectedTab.value = tab
    }

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    fun onSortOptionChange(option: AppSortOption) {
        sortOption.value = option
    }

    fun onSnapshotFilterChange(filter: SnapshotFilter) {
        snapshotFilter.value = filter
    }

    fun onMarkReviewed(packageName: String) {
        viewModelScope.launch { repository.markAppReviewed(packageName) }
    }

    fun onMarkAllReviewed() {
        viewModelScope.launch { repository.markAllAppsReviewed() }
    }

    fun onClearHistory() {
        viewModelScope.launch { repository.clearReviewedHistory() }
    }

    private data class FilterControls(
        val tab: NewAppsTab,
        val searchQuery: String,
        val sortOption: AppSortOption,
        val snapshotFilter: SnapshotFilter
    )

    class Factory(
        private val repository: SecurityScanRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return NewAppsViewModel(repository) as T
        }
    }
}
