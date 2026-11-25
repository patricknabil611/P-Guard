package com.example.ui.keyboards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.DeviceStatusRepository
import com.example.data.repository.SecurityScanRepository
import com.example.data.scanner.AppSecurityAssessment
import com.example.data.scanner.EnabledKeyboardInfo
import com.example.ui.common.AppSortOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class KeyboardDisplayItem(
    val keyboard: EnabledKeyboardInfo,
    val assessment: AppSecurityAssessment?
)

data class KeyboardsUiState(
    val keyboards: List<KeyboardDisplayItem> = emptyList(),
    val totalCount: Int = 0,
    val thirdPartyCount: Int = 0,
    val searchQuery: String = "",
    val sortOption: AppSortOption = AppSortOption.RISK
)

class KeyboardsViewModel(
    private val scanRepository: SecurityScanRepository,
    private val deviceStatusRepository: DeviceStatusRepository
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val sortOption = MutableStateFlow(AppSortOption.RISK)

    val uiState: StateFlow<KeyboardsUiState> = combine(
        deviceStatusRepository.enabledKeyboards,
        scanRepository.securityAssessments,
        combine(searchQuery, sortOption) { q, s -> q to s }
    ) { keyboards, assessments, controls ->
        val (query, sort) = controls
        val normalized = query.trim().lowercase()
        val assessmentsByPkg = assessments.associateBy { it.snapshot.packageName }

        val mapped = keyboards.map { kb ->
            KeyboardDisplayItem(
                keyboard = kb,
                assessment = assessmentsByPkg[kb.packageName]
            )
        }.filter { item ->
            normalized.isEmpty() ||
                item.keyboard.displayName.lowercase().contains(normalized) ||
                item.keyboard.packageName.lowercase().contains(normalized)
        }

        val sorted = when (sort) {
            AppSortOption.NAME -> mapped.sortedBy { it.keyboard.displayName.lowercase() }
            AppSortOption.INSTALL_DATE -> mapped.sortedWith(
                compareByDescending<KeyboardDisplayItem> {
                    it.assessment?.snapshot?.firstInstallTimeMillis ?: 0L
                }.thenBy { it.keyboard.displayName.lowercase() }
            )
            AppSortOption.RISK -> mapped.sortedWith(
                compareByDescending<KeyboardDisplayItem> { !it.keyboard.isKnownPublisher }
                    .thenByDescending { it.keyboard.isDefault }
                    .thenBy { it.keyboard.displayName.lowercase() }
            )
        }

        KeyboardsUiState(
            keyboards = sorted,
            totalCount = keyboards.size,
            thirdPartyCount = keyboards.count { !it.isKnownPublisher },
            searchQuery = query,
            sortOption = sort
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = KeyboardsUiState()
    )

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            deviceStatusRepository.refreshKeyboards()
        }
    }

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    fun onSortOptionChange(option: AppSortOption) {
        sortOption.value = option
    }

    class Factory(
        private val scanRepository: SecurityScanRepository,
        private val deviceStatusRepository: DeviceStatusRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return KeyboardsViewModel(scanRepository, deviceStatusRepository) as T
        }
    }
}
