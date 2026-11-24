package com.example.ui.recent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.DeviceStatusRepository
import com.example.data.repository.SecurityScanRepository
import com.example.data.scanner.ActiveForegroundServiceInfo
import com.example.data.scanner.AppSecurityAssessment
import com.example.data.scanner.RecentAppUsage
import com.example.data.scanner.RiskLevel
import com.example.data.scanner.UsageActivitySnapshot
import com.example.data.scanner.UsageTimeWindow
import com.example.ui.common.AppSortOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RecentAppDisplayItem(
    val usage: RecentAppUsage,
    val assessment: AppSecurityAssessment?
) {
    val displayName: String
        get() = assessment?.snapshot?.appName ?: usage.packageName

    val installTimeMillis: Long
        get() = assessment?.snapshot?.firstInstallTimeMillis ?: 0L

    val riskLevel: RiskLevel
        get() = assessment?.riskLevel ?: RiskLevel.NONE

    val riskScore: Int
        get() = assessment?.riskScore ?: 0
}

data class ForegroundServiceDisplayItem(
    val service: ActiveForegroundServiceInfo,
    val assessment: AppSecurityAssessment?
) {
    val displayName: String
        get() = assessment?.snapshot?.appName ?: service.packageName
}

data class RecentlyActiveUiState(
    val hasUsagePermission: Boolean = false,
    val isLoading: Boolean = true,
    val selectedWindow: UsageTimeWindow = UsageTimeWindow.ONE_HOUR,
    val recentApps: List<RecentAppDisplayItem> = emptyList(),
    val totalRecentCount: Int = 0,
    val foregroundServices: List<ForegroundServiceDisplayItem> = emptyList(),
    val searchQuery: String = "",
    val sortOption: AppSortOption = AppSortOption.INSTALL_DATE
)

class RecentlyActiveViewModel(
    private val scanRepository: SecurityScanRepository,
    private val deviceStatusRepository: DeviceStatusRepository
) : ViewModel() {

    private val selectedWindow = MutableStateFlow(UsageTimeWindow.ONE_HOUR)
    private val searchQuery = MutableStateFlow("")
    private val sortOption = MutableStateFlow(AppSortOption.INSTALL_DATE)
    private val isLoading = MutableStateFlow(true)
    private val usageSnapshot = MutableStateFlow(
        UsageActivitySnapshot(hasUsagePermission = false, window = UsageTimeWindow.ONE_HOUR)
    )

    private val controls = combine(selectedWindow, searchQuery, sortOption, isLoading) { w, q, s, l ->
        Controls(w, q, s, l)
    }

    val uiState: StateFlow<RecentlyActiveUiState> = combine(
        usageSnapshot,
        scanRepository.securityAssessments,
        controls
    ) { snapshot, assessments, ctrl ->
        val assessmentMap = assessments.associateBy { it.snapshot.packageName }
        val normalized = ctrl.searchQuery.trim().lowercase()

        val allItems = snapshot.recentApps.map { usage ->
            RecentAppDisplayItem(
                usage = usage,
                assessment = assessmentMap[usage.packageName]
            )
        }

        val filtered = allItems.filter { item ->
            normalized.isEmpty() ||
                item.displayName.lowercase().contains(normalized) ||
                item.usage.packageName.lowercase().contains(normalized)
        }

        val sorted = when (ctrl.sortOption) {
            AppSortOption.NAME -> filtered.sortedBy { it.displayName.lowercase() }
            AppSortOption.INSTALL_DATE -> filtered.sortedWith(
                compareByDescending<RecentAppDisplayItem> { it.usage.lastUsedMillis }
                    .thenByDescending { it.installTimeMillis }
                    .thenBy { it.displayName.lowercase() }
            )
            AppSortOption.RISK -> filtered.sortedWith(
                compareByDescending<RecentAppDisplayItem> { it.riskLevel.sortOrder }
                    .thenByDescending { it.riskScore }
                    .thenByDescending { it.usage.lastUsedMillis }
            )
        }

        val fgServices = snapshot.activeForegroundServices.map { svc ->
            ForegroundServiceDisplayItem(
                service = svc,
                assessment = assessmentMap[svc.packageName]
            )
        }.filter { item ->
            normalized.isEmpty() ||
                item.displayName.lowercase().contains(normalized) ||
                item.service.packageName.lowercase().contains(normalized)
        }

        RecentlyActiveUiState(
            hasUsagePermission = snapshot.hasUsagePermission,
            isLoading = ctrl.isLoading,
            selectedWindow = ctrl.selectedWindow,
            recentApps = sorted,
            totalRecentCount = allItems.size,
            foregroundServices = fgServices,
            searchQuery = ctrl.searchQuery,
            sortOption = ctrl.sortOption
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RecentlyActiveUiState()
    )

    init {
        refresh()
    }

    fun refresh() {
        val window = selectedWindow.value
        viewModelScope.launch {
            isLoading.value = true
            usageSnapshot.value = deviceStatusRepository.queryRecentActivity(window)
            isLoading.value = false
        }
    }

    fun onSelectWindow(window: UsageTimeWindow) {
        if (selectedWindow.value == window) return
        selectedWindow.value = window
        refresh()
    }

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    fun onSortOptionChange(option: AppSortOption) {
        sortOption.value = option
    }

    private data class Controls(
        val selectedWindow: UsageTimeWindow,
        val searchQuery: String,
        val sortOption: AppSortOption,
        val isLoading: Boolean
    )

    class Factory(
        private val scanRepository: SecurityScanRepository,
        private val deviceStatusRepository: DeviceStatusRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RecentlyActiveViewModel(scanRepository, deviceStatusRepository) as T
        }
    }
}
