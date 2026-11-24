package com.example.data.repository

import com.example.data.scanner.DeveloperModeReader
import com.example.data.scanner.DeveloperModeStatus
import com.example.data.scanner.UsageActivityScanner
import com.example.data.scanner.UsageActivitySnapshot
import com.example.data.scanner.UsageTimeWindow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class DeviceStatusRepository(
    private val developerModeReader: DeveloperModeReader,
    private val usageActivityScanner: UsageActivityScanner,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val _developerModeStatus = MutableStateFlow(DeveloperModeStatus())
    val developerModeStatus: StateFlow<DeveloperModeStatus> = _developerModeStatus.asStateFlow()

    private val _hasUsageAccess = MutableStateFlow(false)
    val hasUsageAccess: StateFlow<Boolean> = _hasUsageAccess.asStateFlow()

    private val _dashboardRecentUsage = MutableStateFlow(
        UsageActivitySnapshot(hasUsagePermission = false, window = UsageTimeWindow.ONE_HOUR)
    )
    val dashboardRecentUsage: StateFlow<UsageActivitySnapshot> = _dashboardRecentUsage.asStateFlow()

    suspend fun refreshSystemStatus() {
        withContext(ioDispatcher) {
            _developerModeStatus.value = developerModeReader.readStatus()
            val granted = usageActivityScanner.hasUsageAccessPermission()
            _hasUsageAccess.value = granted
            _dashboardRecentUsage.value = usageActivityScanner.queryRecentActivity(UsageTimeWindow.ONE_HOUR)
        }
    }

    suspend fun queryRecentActivity(window: UsageTimeWindow): UsageActivitySnapshot {
        return withContext(ioDispatcher) {
            val snapshot = usageActivityScanner.queryRecentActivity(window)
            _hasUsageAccess.value = snapshot.hasUsagePermission
            if (window == UsageTimeWindow.ONE_HOUR) {
                _dashboardRecentUsage.value = snapshot
            }
            snapshot
        }
    }

    suspend fun queryLastUsedForPackage(packageName: String): Pair<Boolean, Long?> {
        return withContext(ioDispatcher) {
            val granted = usageActivityScanner.hasUsageAccessPermission()
            _hasUsageAccess.value = granted
            if (!granted) {
                false to null
            } else {
                true to usageActivityScanner.queryLastUsedTimeMillis(packageName)
            }
        }
    }
}
