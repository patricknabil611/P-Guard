package com.example.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.PGuardApplication

class BackgroundScanWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? PGuardApplication ?: return Result.failure()
        val container = app.container

        return try {
            val settings = container.preferencesRepository.getSettingsOnce()
            val posture = container.devicePostureReader.readPosture()
            val devMode = container.developerModeReader.readStatus()

            val scanResult = container.securityScanRepository.runScanDetailed(
                force = true,
                isBackground = true,
                devicePosture = posture,
                developerModeStatus = devMode,
                previousDevOptionsOn = settings.lastKnownDevOptionsOn,
                previousUsbDebuggingOn = settings.lastKnownUsbDebuggingOn,
                hasDevBaseline = settings.hasRecordedDevBaseline
            )

            container.preferencesRepository.updateLastKnownDeveloperMode(
                devOptionsOn = devMode.isDeveloperOptionsEnabled,
                usbDebuggingOn = devMode.isUsbDebuggingEnabled
            )

            if (scanResult.succeeded) {
                NotificationHelper.dispatchBackgroundScanAlerts(
                    context = applicationContext,
                    result = scanResult,
                    settings = settings
                )
            }

            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
