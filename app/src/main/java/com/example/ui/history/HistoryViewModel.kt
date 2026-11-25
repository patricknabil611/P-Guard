package com.example.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.ScanHistoryDetails
import com.example.data.local.entity.ScanHistoryEntity
import com.example.data.repository.SecurityScanRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class HistoryUiState(
    val entries: List<ScanHistoryEntity> = emptyList(),
    val last30DaysChartPoints: List<ScanHistoryEntity> = emptyList()
)

class HistoryViewModel(
    repository: SecurityScanRepository
) : ViewModel() {

    private val thirtyDaysAgoMillis: Long =
        System.currentTimeMillis() - (30L * 24L * 60L * 60L * 1_000L)

    val uiState: StateFlow<HistoryUiState> = combine(
        repository.scanHistory,
        repository.observeScanHistorySince(thirtyDaysAgoMillis)
    ) { allEntries, last30Days ->
        HistoryUiState(
            entries = allEntries,
            last30DaysChartPoints = last30Days
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HistoryUiState()
    )

    class Factory(
        private val repository: SecurityScanRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HistoryViewModel(repository) as T
        }
    }
}

data class HistoryDetailUiState(
    val isLoading: Boolean = true,
    val entry: ScanHistoryEntity? = null,
    val details: ScanHistoryDetails = ScanHistoryDetails()
)

class HistoryDetailViewModel(
    historyId: Long,
    repository: SecurityScanRepository
) : ViewModel() {

    val uiState: StateFlow<HistoryDetailUiState> = repository.observeScanHistoryById(historyId)
        .map { entity ->
            if (entity == null) {
                HistoryDetailUiState(isLoading = false, entry = null)
            } else {
                HistoryDetailUiState(
                    isLoading = false,
                    entry = entity,
                    details = ScanHistoryDetails.fromJson(entity.detailsJson)
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HistoryDetailUiState(isLoading = true)
        )

    class Factory(
        private val historyId: Long,
        private val repository: SecurityScanRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HistoryDetailViewModel(historyId, repository) as T
        }
    }
}
