package com.example.data.repository

import com.example.data.scanner.AppBackgroundDataUsage
import com.example.data.scanner.DataUsageWindow
import com.example.data.scanner.DeveloperModeReader
import com.example.data.scanner.DeveloperModeStatus
import com.example.data.scanner.DevicePostureReader
import com.example.data.scanner.DevicePostureSnapshot
import com.example.data.scanner.EnabledKeyboardInfo
import com.example.data.scanner.KeyboardInspector
import com.example.data.scanner.NetworkUsageScanner
import com.example.data.scanner.NetworkUsageSnapshot
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
    private val keyboardInspector: KeyboardInspector,
    private val networkUsageScanner: NetworkUsageScanner,
    private val devicePostureReader: DevicePostureReader,
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

    private val _enabledKeyboards = MutableStateFlow<List<EnabledKeyboardInfo>>(emptyList())
    val enabledKeyboards: StateFlow<List<EnabledKeyboardInfo>> = _enabledKeyboards.asStateFlow()

    private val _dashboardDataUsage24h = MutableStateFlow(
        NetworkUsageSnapshot(hasUsagePermission = false, window = DataUsageWindow.TWENTY_FOUR_HOURS)
    )
    val dashboardDataUsage24h: StateFlow<NetworkUsageSnapshot> = _dashboardDataUsage24h.asStateFlow()

    private val _devicePosture = MutableStateFlow(DevicePostureSnapshot())
    val devicePosture: StateFlow<DevicePostureSnapshot> = _devicePosture.asStateFlow()

    suspend fun refreshSystemStatus() {
        withContext(ioDispatcher) {
            _developerModeStatus.value = developerModeReader.readStatus()
            val granted = usageActivityScanner.hasUsageAccessPermission()
            _hasUsageAccess.value = granted
            _dashboardRecentUsage.value = usageActivityScanner.queryRecentActivity(UsageTimeWindow.ONE_HOUR)
            _enabledKeyboards.value = keyboardInspector.getEnabledKeyboards()
            _dashboardDataUsage24h.value =
                networkUsageScanner.queryBackgroundDataUsage(DataUsageWindow.TWENTY_FOUR_HOURS)
            _devicePosture.value = devicePostureReader.readPosture()
        }
    }

    suspend fun refreshKeyboards() {
        withContext(ioDispatcher) {
            _enabledKeyboards.value = keyboardInspector.getEnabledKeyboards()
        }
    }

    suspend fun refreshDevicePosture() {
        withContext(ioDispatcher) {
            _developerModeStatus.value = developerModeReader.readStatus()
            _devicePosture.value = devicePostureReader.readPosture()
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

    suspend fun queryBackgroundDataUsage(window: DataUsageWindow): NetworkUsageSnapshot {
        return withContext(ioDispatcher) {
            val snapshot = networkUsageScanner.queryBackgroundDataUsage(window)
            _hasUsageAccess.value = snapshot.hasUsagePermission
            if (window == DataUsageWindow.TWENTY_FOUR_HOURS) {
                _dashboardDataUsage24h.value = snapshot
            }
            snapshot
        }
    }

    suspend fun queryLastUsedAndDataForPackage(packageName: String): Triple<Boolean, Long?, AppBackgroundDataUsage?> {
        return withContext(ioDispatcher) {
            val granted = usageActivityScanner.hasUsageAccessPermission()
            _hasUsageAccess.value = granted
            if (!granted) {
                Triple(false, null, null)
            } else {
                val lastUsed = usageActivityScanner.queryLastUsedTimeMillis(packageName)
                val bgData24h = networkUsageScanner.queryBackgroundDataForPackage(
                    packageName = packageName,
                    window = DataUsageWindow.TWENTY_FOUR_HOURS
                )
                Triple(true, lastUsed, bgData24h)
            }
        }
    }
}
