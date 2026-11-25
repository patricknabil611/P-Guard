package com.example

import android.app.Application
import com.example.data.local.PGuardDatabase
import com.example.data.preferences.DataStorePGuardPreferencesRepository
import com.example.data.preferences.PGuardPreferencesRepository
import com.example.data.repository.DeviceStatusRepository
import com.example.data.repository.SecurityScanRepository
import com.example.data.scanner.AndroidDevicePostureReader
import com.example.data.scanner.DeviceDeveloperModeReader
import com.example.data.scanner.DeviceKeyboardInspector
import com.example.data.scanner.DeviceNetworkUsageScanner
import com.example.data.scanner.DevicePackageScanner
import com.example.data.scanner.DeviceRootDetector
import com.example.data.scanner.DeviceUsageActivityScanner
import com.example.security.AppLockManager
import com.example.worker.BackgroundScanScheduler
import com.example.worker.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(application: Application) {
    val database: PGuardDatabase by lazy {
        PGuardDatabase.create(application)
    }

    val preferencesRepository: PGuardPreferencesRepository by lazy {
        DataStorePGuardPreferencesRepository(application)
    }

    val appLockManager: AppLockManager by lazy {
        AppLockManager()
    }

    val packageScanner: DevicePackageScanner by lazy {
        DevicePackageScanner(application)
    }

    val developerModeReader: DeviceDeveloperModeReader by lazy {
        DeviceDeveloperModeReader(application)
    }

    val usageActivityScanner: DeviceUsageActivityScanner by lazy {
        DeviceUsageActivityScanner(application)
    }

    val keyboardInspector: DeviceKeyboardInspector by lazy {
        DeviceKeyboardInspector(application)
    }

    val networkUsageScanner: DeviceNetworkUsageScanner by lazy {
        DeviceNetworkUsageScanner(application, usageActivityScanner)
    }

    val rootDetector: DeviceRootDetector by lazy {
        DeviceRootDetector(application)
    }

    val devicePostureReader: AndroidDevicePostureReader by lazy {
        AndroidDevicePostureReader(application, rootDetector)
    }

    val securityScanRepository: SecurityScanRepository by lazy {
        SecurityScanRepository(
            scanDao = database.scanDao(),
            packageScanner = packageScanner
        )
    }

    val deviceStatusRepository: DeviceStatusRepository by lazy {
        DeviceStatusRepository(
            developerModeReader = developerModeReader,
            usageActivityScanner = usageActivityScanner,
            keyboardInspector = keyboardInspector,
            networkUsageScanner = networkUsageScanner,
            devicePostureReader = devicePostureReader
        )
    }
}

class PGuardApplication : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationHelper.ensureChannels(this)
        appScope.launch {
            val settings = container.preferencesRepository.getSettingsOnce()
            BackgroundScanScheduler.syncSchedule(this@PGuardApplication, settings.scanInterval)
        }
    }
}
