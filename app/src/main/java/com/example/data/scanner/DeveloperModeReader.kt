package com.example.data.scanner

import android.content.Context
import android.provider.Settings

data class DeveloperModeStatus(
    val isDeveloperOptionsEnabled: Boolean = false,
    val isUsbDebuggingEnabled: Boolean = false
)

interface DeveloperModeReader {
    fun readStatus(): DeveloperModeStatus
}

class DeviceDeveloperModeReader(
    private val context: Context
) : DeveloperModeReader {

    override fun readStatus(): DeveloperModeStatus {
        val resolver = context.contentResolver
        val devEnabled = try {
            Settings.Global.getInt(
                resolver,
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                0
            ) != 0
        } catch (_: Exception) {
            false
        }

        val adbEnabled = try {
            Settings.Global.getInt(
                resolver,
                Settings.Global.ADB_ENABLED,
                0
            ) != 0
        } catch (_: Exception) {
            false
        }

        return DeveloperModeStatus(
            isDeveloperOptionsEnabled = devEnabled,
            isUsbDebuggingEnabled = adbEnabled
        )
    }
}
