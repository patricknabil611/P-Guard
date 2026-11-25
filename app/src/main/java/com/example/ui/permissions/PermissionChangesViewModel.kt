package com.example.ui.permissions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.PermissionChangeEntity
import com.example.data.local.entity.ScanRecordEntity
import com.example.data.repository.ScanExecutionState
import com.example.data.repository.SecurityScanRepository
import com.example.data.scanner.AppSecurityAssessment
import com.example.data.scanner.RiskyPermission
import com.example.ui.common.AppSortOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AddedPermissionEntry(
    val permissionName: String,
    val isRisky: Boolean
)

data class PermissionChangeDisplayItem(
    val entity: PermissionChangeEntity,
    val addedPermissions: List<AddedPermissionEntry>,
    val assessment: AppSecurityAssessment?
) {
    val hasRiskyAddition: Boolean
        get() = addedPermissions.any { it.isRisky }
}

data class PermissionChangesUiState(
    val latestScan: ScanRecordEntity? = null,
    val isScanning: Boolean = false,
    val unreviewedItems: List<PermissionChangeDisplayItem> = emptyList(),
    val reviewedItems: List<PermissionChangeDisplayItem> = emptyList(),
    val totalUnreviewedCount: Int = 0,
    val totalReviewedCount: Int = 0,
    val searchQuery: String = "",
    val sortOption: AppSortOption = AppSortOption.INSTALL_DATE
)

class PermissionChangesViewModel(
    private val repository: SecurityScanRepository
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val sortOption = MutableStateFlow(AppSortOption.INSTALL_DATE)

    private val listsFlow = combine(
        repository.unreviewedPermissionChanges,
        repository.reviewedPermissionChanges,
        repository.securityAssessments
    ) { unreviewed, reviewed, assessments ->
        Triple(unreviewed, reviewed, assessments)
    }

    val uiState: StateFlow<PermissionChangesUiState> = combine(
        repository.latestScan,
        repository.executionState,
        listsFlow,
        combine(searchQuery, sortOption) { q, s -> q to s }
    ) { latestScan, execState, lists, controls ->
        val (unreviewed, reviewed, assessments) = lists
        val (query, sort) = controls
        val normalized = query.trim().lowercase()
        val assessmentsByPkg = assessments.associateBy { it.snapshot.packageName }

        fun toDisplay(items: List<PermissionChangeEntity>): List<PermissionChangeDisplayItem> {
            val mapped = items.mapNotNull { entity ->
                val perms = entity.addedPermissionsCsv
                    .split(',')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .map { perm ->
                        AddedPermissionEntry(
                            permissionName = perm,
                            isRisky = RiskyPermission.isRiskyAndroidPermission(perm)
                        )
                    }
                    .sortedByDescending { it.isRisky }
                if (perms.isEmpty()) return@mapNotNull null

                val matches = normalized.isEmpty() ||
                    entity.appName.lowercase().contains(normalized) ||
                    entity.packageName.lowercase().contains(normalized) ||
                    perms.any { it.permissionName.lowercase().contains(normalized) }
                if (!matches) return@mapNotNull null

                PermissionChangeDisplayItem(
                    entity = entity,
                    addedPermissions = perms,
                    assessment = assessmentsByPkg[entity.packageName]
                )
            }

            return when (sort) {
                AppSortOption.NAME -> mapped.sortedBy { it.entity.appName.lowercase() }
                AppSortOption.INSTALL_DATE -> mapped.sortedWith(
                    compareByDescending<PermissionChangeDisplayItem> { it.entity.detectedAtMillis }
                        .thenByDescending { it.hasRiskyAddition }
                        .thenBy { it.entity.appName.lowercase() }
                )
                AppSortOption.RISK -> mapped.sortedWith(
                    compareByDescending<PermissionChangeDisplayItem> { it.hasRiskyAddition }
                        .thenByDescending { it.addedPermissions.count { p -> p.isRisky } }
                        .thenByDescending { it.assessment?.riskLevel?.sortOrder ?: 0 }
                        .thenByDescending { it.entity.detectedAtMillis }
                )
            }
        }

        PermissionChangesUiState(
            latestScan = latestScan,
            isScanning = execState is ScanExecutionState.Scanning,
            unreviewedItems = toDisplay(unreviewed),
            reviewedItems = toDisplay(reviewed),
            totalUnreviewedCount = unreviewed.size,
            totalReviewedCount = reviewed.size,
            searchQuery = query,
            sortOption = sort
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PermissionChangesUiState()
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

    fun onMarkReviewed(id: Long) {
        viewModelScope.launch { repository.markPermissionChangeReviewed(id) }
    }

    fun onMarkAllReviewed() {
        viewModelScope.launch { repository.markAllPermissionChangesReviewed() }
    }

    fun onClearReviewedHistory() {
        viewModelScope.launch { repository.clearReviewedPermissionChanges() }
    }

    class Factory(
        private val repository: SecurityScanRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PermissionChangesViewModel(repository) as T
        }
    }
}
