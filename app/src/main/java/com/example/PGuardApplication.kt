package com.example

import android.app.Application
import com.example.data.local.PGuardDatabase
import com.example.data.repository.DeviceStatusRepository
import com.example.data.repository.SecurityScanRepository
import com.example.data.scanner.DeviceDeveloperModeReader
import com.example.data.scanner.DevicePackageScanner
import com.example.data.scanner.DeviceUsageActivityScanner

class AppContainer(application: Application) {
    val database: PGuardDatabase by lazy {
        PGuardDatabase.create(application)
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

    val securityScanRepository: SecurityScanRepository by lazy {
        SecurityScanRepository(
            scanDao = database.scanDao(),
            packageScanner = packageScanner
        )
    }

    val deviceStatusRepository: DeviceStatusRepository by lazy {
        DeviceStatusRepository(
            developerModeReader = developerModeReader,
            usageActivityScanner = usageActivityScanner
        )
    }
}

class PGuardApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
