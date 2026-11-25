package com.example.ui.datausage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.DeviceStatusRepository
import com.example.data.repository.SecurityScanRepository
import com.example.data.scanner.AppBackgroundDataUsage
import com.example.data.scanner.AppSecurityAssessment
import com.example.data.scanner.DataUsageWindow
import com.example.data.scanner.NetworkUsageScanner
import com.example.data.scanner.NetworkUsageSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class DataUsageSortOption {
    MOST_DATA,
    NAME,
    RISK
}

data class DataUsageDisplayItem(
    val usage: AppBackgroundDataUsage,
    val displayName: String,
    val isTrusted: Boolean,
    val isHighBackgroundFlagged: Boolean,
    val assessment: AppSecurityAssessment?
)

data class DataUsageUiState(
    val hasUsagePermission: Boolean = false,
    val selectedWindow: DataUsageWindow = DataUsageWindow.TWENTY_FOUR_HOURS,
    val isLoading: Boolean = true,
    val items: List<DataUsageDisplayItem> = emptyList(),
    val totalItemCount: Int = 0,
    val flaggedCount: Int = 0,
    val searchQuery: String = "",
    val sortOption: DataUsageSortOption = DataUsageSortOption.MOST_DATA
)

class DataUsageViewModel(
    private val scanRepository: SecurityScanRepository,
    private val deviceStatusRepository: DeviceStatusRepository
) : ViewModel() {

    private val selectedWindow = MutableStateFlow(DataUsageWindow.TWENTY_FOUR_HOURS)
    private val isLoading = MutableStateFlow(true)
    private val currentSnapshot = MutableStateFlow(
        NetworkUsageSnapshot(hasUsagePermission = false, window = DataUsageWindow.TWENTY_FOUR_HOURS)
    )
    private val searchQuery = MutableStateFlow("")
    private val sortOption = MutableStateFlow(DataUsageSortOption.MOST_DATA)

    private val controlsFlow = combine(
        selectedWindow,
        isLoading,
        searchQuery,
        sortOption
    ) { window, loading, query, sort ->
        Controls(window, loading, query, sort)
    }

    val uiState: StateFlow<DataUsageUiState> = combine(
        currentSnapshot,
        deviceStatusRepository.dashboardDataUsage24h,
        scanRepository.securityAssessments,
        scanRepository.trustedApps,
        controlsFlow
    ) { snapshot, snapshot24h, assessments, trustedApps, controls ->
        val assessmentsByPkg = assessments.associateBy { it.snapshot.packageName }
        val trustedSet = trustedApps.map { it.packageName }.toSet()
        val high24hPackages = snapshot24h.apps
            .filter { it.totalBackgroundBytes > NetworkUsageScanner.HIGH_BACKGROUND_DATA_24H_BYTES }
            .map { it.packageName }
            .toSet()

        val normalized = controls.searchQuery.trim().lowercase()

        val allItems = snapshot.apps.map { usage ->
            val assessment = assessmentsByPkg[usage.packageName]
            val displayName = assessment?.snapshot?.appName ?: usage.packageName
            val isTrusted = usage.packageName in trustedSet
            val exceeded24h = if (snapshot.window == DataUsageWindow.TWENTY_FOUR_HOURS) {
                usage.totalBackgroundBytes > NetworkUsageScanner.HIGH_BACKGROUND_DATA_24H_BYTES
            } else {
                usage.packageName in high24hPackages
            }
            DataUsageDisplayItem(
                usage = usage,
                displayName = displayName,
                isTrusted = isTrusted,
                isHighBackgroundFlagged = !isTrusted && exceeded24h,
                assessment = assessment
            )
        }

        val filtered = allItems.filter { item ->
            normalized.isEmpty() ||
                item.displayName.lowercase().contains(normalized) ||
                item.usage.packageName.lowercase().contains(normalized)
        }

        val sorted = when (controls.sortOption) {
            DataUsageSortOption.MOST_DATA -> filtered.sortedWith(
                compareByDescending<DataUsageDisplayItem> { it.usage.totalBackgroundBytes }
                    .thenBy { it.displayName.lowercase() }
            )
            DataUsageSortOption.NAME -> filtered.sortedBy { it.displayName.lowercase() }
            DataUsageSortOption.RISK -> filtered.sortedWith(
                compareByDescending<DataUsageDisplayItem> { it.isHighBackgroundFlagged }
                    .thenByDescending { it.assessment?.riskLevel?.sortOrder ?: 0 }
                    .thenByDescending { it.usage.totalBackgroundBytes }
            )
        }

        DataUsageUiState(
            hasUsagePermission = snapshot.hasUsagePermission,
            selectedWindow = controls.window,
            isLoading = controls.isLoading,
            items = sorted,
            totalItemCount = allItems.size,
            flaggedCount = allItems.count { it.isHighBackgroundFlagged },
            searchQuery = controls.searchQuery,
            sortOption = controls.sortOption
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DataUsageUiState()
    )

    init {
        refresh()
    }

    fun onSelectWindow(window: DataUsageWindow) {
        if (selectedWindow.value == window) return
        selectedWindow.value = window
        refresh()
    }

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    fun onSortOptionChange(option: DataUsageSortOption) {
        sortOption.value = option
    }

    fun refresh() {
        val window = selectedWindow.value
        viewModelScope.launch {
            isLoading.value = true
            try {
                // Keep 24h baseline fresh for accurate 24h >50 MB flagging even when viewing 7d
                if (window != DataUsageWindow.TWENTY_FOUR_HOURS) {
                    deviceStatusRepository.queryBackgroundDataUsage(DataUsageWindow.TWENTY_FOUR_HOURS)
                }
                currentSnapshot.value = deviceStatusRepository.queryBackgroundDataUsage(window)
            } finally {
                isLoading.value = false
            }
        }
    }

    private data class Controls(
        val window: DataUsageWindow,
        val isLoading: Boolean,
        val searchQuery: String,
        val sortOption: DataUsageSortOption
    )

    class Factory(
        private val scanRepository: SecurityScanRepository,
        private val deviceStatusRepository: DeviceStatusRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DataUsageViewModel(scanRepository, deviceStatusRepository) as T
        }
    }
}
