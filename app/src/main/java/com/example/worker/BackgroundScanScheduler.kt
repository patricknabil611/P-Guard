package com.example.worker

import android.content.Context
import android.os.PowerManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.preferences.BackgroundScanInterval
import java.util.concurrent.TimeUnit

object BackgroundScanScheduler {
    const val UNIQUE_WORK_NAME = "p_guard_periodic_security_scan"

    fun syncSchedule(context: Context, interval: BackgroundScanInterval) {
        val workManager = try {
            WorkManager.getInstance(context.applicationContext)
        } catch (_: Exception) {
            return
        }

        if (interval == BackgroundScanInterval.OFF || interval.hours <= 0L) {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }

        val request = PeriodicWorkRequestBuilder<BackgroundScanWorker>(
            repeatInterval = interval.hours,
            repeatIntervalTimeUnit = TimeUnit.HOURS
        ).build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun isBatteryOptimizationRestricting(context: Context): Boolean {
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
            !pm.isIgnoringBatteryOptimizations(context.packageName)
        } catch (_: Exception) {
            false
        }
    }
}
