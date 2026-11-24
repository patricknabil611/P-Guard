package com.example.ui.trusted

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.TrustedAppEntity
import com.example.data.repository.SecurityScanRepository
import com.example.data.scanner.AppSecurityAssessment
import com.example.data.scanner.RiskLevel
import com.example.ui.common.AppSortOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TrustedAppItem(
    val entity: TrustedAppEntity,
    val assessment: AppSecurityAssessment?
) {
    val displayName: String
        get() = assessment?.snapshot?.appName ?: entity.appName

    val installTimeMillis: Long
        get() = assessment?.snapshot?.firstInstallTimeMillis ?: entity.trustedAtMillis

    val riskLevel: RiskLevel
        get() = assessment?.riskLevel ?: RiskLevel.NONE

    val riskScore: Int
        get() = assessment?.riskScore ?: 0
}

data class TrustedAppsUiState(
    val trustedItems: List<TrustedAppItem> = emptyList(),
    val totalTrustedCount: Int = 0,
    val searchQuery: String = "",
    val sortOption: AppSortOption = AppSortOption.NAME
)

class TrustedAppsViewModel(
    private val repository: SecurityScanRepository
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val sortOption = MutableStateFlow(AppSortOption.NAME)

    val uiState: StateFlow<TrustedAppsUiState> = combine(
        repository.trustedApps,
        repository.securityAssessments,
        searchQuery,
        sortOption
    ) { trustedList, assessments, query, sort ->
        val assessmentMap = assessments.associateBy { it.snapshot.packageName }
        val normalized = query.trim().lowercase()

        val items = trustedList.map { entity ->
            TrustedAppItem(
                entity = entity,
                assessment = assessmentMap[entity.packageName]
            )
        }.filter { item ->
            normalized.isEmpty() ||
                item.displayName.lowercase().contains(normalized) ||
                item.entity.packageName.lowercase().contains(normalized)
        }

        val sorted = when (sort) {
            AppSortOption.NAME -> items.sortedBy { it.displayName.lowercase() }
            AppSortOption.INSTALL_DATE -> items.sortedWith(
                compareByDescending<TrustedAppItem> { it.installTimeMillis }
                    .thenBy { it.displayName.lowercase() }
            )
            AppSortOption.RISK -> items.sortedWith(
                compareByDescending<TrustedAppItem> { it.riskLevel.sortOrder }
                    .thenByDescending { it.riskScore }
                    .thenBy { it.displayName.lowercase() }
            )
        }

        TrustedAppsUiState(
            trustedItems = sorted,
            totalTrustedCount = trustedList.size,
            searchQuery = query,
            sortOption = sort
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TrustedAppsUiState()
    )

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    fun onSortOptionChange(option: AppSortOption) {
        sortOption.value = option
    }

    fun onRemoveTrusted(packageName: String) {
        viewModelScope.launch {
            repository.removeTrustedApp(packageName)
        }
    }

    class Factory(
        private val repository: SecurityScanRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TrustedAppsViewModel(repository) as T
        }
    }
}
