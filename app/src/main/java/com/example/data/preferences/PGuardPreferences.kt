package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.pGuardDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "p_guard_settings"
)

enum class BackgroundScanInterval(val hours: Long) {
    HOURS_3(3L),
    HOURS_6(6L),
    HOURS_12(12L),
    HOURS_24(24L),
    OFF(0L);

    companion object {
        fun fromHours(hours: Int): BackgroundScanInterval =
            entries.firstOrNull { it.hours.toInt() == hours } ?: HOURS_6
    }
}

data class PGuardSettings(
    val scanInterval: BackgroundScanInterval = BackgroundScanInterval.HOURS_6,
    val notifyNewApp: Boolean = true,
    val notifySignatureChanged: Boolean = true,
    val notifyNewSuspicious: Boolean = true,
    val notifyNewRiskyPermission: Boolean = true,
    val notifyDeveloperMode: Boolean = true,
    val appLockEnabled: Boolean = false,
    val lastKnownDevOptionsOn: Boolean = false,
    val lastKnownUsbDebuggingOn: Boolean = false,
    val hasRecordedDevBaseline: Boolean = false,
    val notificationPromptDismissed: Boolean = false,
    val batteryPromptDismissed: Boolean = false
)

interface PGuardPreferencesRepository {
    val settings: Flow<PGuardSettings>
    suspend fun getSettingsOnce(): PGuardSettings
    suspend fun setScanInterval(interval: BackgroundScanInterval)
    suspend fun setNotifyNewApp(enabled: Boolean)
    suspend fun setNotifySignatureChanged(enabled: Boolean)
    suspend fun setNotifyNewSuspicious(enabled: Boolean)
    suspend fun setNotifyNewRiskyPermission(enabled: Boolean)
    suspend fun setNotifyDeveloperMode(enabled: Boolean)
    suspend fun setAppLockEnabled(enabled: Boolean)
    suspend fun updateLastKnownDeveloperMode(devOptionsOn: Boolean, usbDebuggingOn: Boolean)
    suspend fun dismissNotificationPrompt()
    suspend fun dismissBatteryPrompt()
}

class DataStorePGuardPreferencesRepository(
    private val context: Context
) : PGuardPreferencesRepository {

    private object Keys {
        val SCAN_INTERVAL_HOURS = intPreferencesKey("scan_interval_hours")
        val NOTIFY_NEW_APP = booleanPreferencesKey("notify_new_app")
        val NOTIFY_SIGNATURE_CHANGED = booleanPreferencesKey("notify_signature_changed")
        val NOTIFY_NEW_SUSPICIOUS = booleanPreferencesKey("notify_new_suspicious")
        val NOTIFY_NEW_RISKY_PERMISSION = booleanPreferencesKey("notify_new_risky_permission")
        val NOTIFY_DEVELOPER_MODE = booleanPreferencesKey("notify_developer_mode")
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val LAST_KNOWN_DEV_OPTIONS_ON = booleanPreferencesKey("last_known_dev_options_on")
        val LAST_KNOWN_USB_DEBUGGING_ON = booleanPreferencesKey("last_known_usb_debugging_on")
        val HAS_RECORDED_DEV_BASELINE = booleanPreferencesKey("has_recorded_dev_baseline")
        val NOTIFICATION_PROMPT_DISMISSED = booleanPreferencesKey("notification_prompt_dismissed")
        val BATTERY_PROMPT_DISMISSED = booleanPreferencesKey("battery_prompt_dismissed")
    }

    override val settings: Flow<PGuardSettings> = context.pGuardDataStore.data.map { prefs ->
        PGuardSettings(
            scanInterval = BackgroundScanInterval.fromHours(
                prefs[Keys.SCAN_INTERVAL_HOURS] ?: BackgroundScanInterval.HOURS_6.hours.toInt()
            ),
            notifyNewApp = prefs[Keys.NOTIFY_NEW_APP] ?: true,
            notifySignatureChanged = prefs[Keys.NOTIFY_SIGNATURE_CHANGED] ?: true,
            notifyNewSuspicious = prefs[Keys.NOTIFY_NEW_SUSPICIOUS] ?: true,
            notifyNewRiskyPermission = prefs[Keys.NOTIFY_NEW_RISKY_PERMISSION] ?: true,
            notifyDeveloperMode = prefs[Keys.NOTIFY_DEVELOPER_MODE] ?: true,
            appLockEnabled = prefs[Keys.APP_LOCK_ENABLED] ?: false,
            lastKnownDevOptionsOn = prefs[Keys.LAST_KNOWN_DEV_OPTIONS_ON] ?: false,
            lastKnownUsbDebuggingOn = prefs[Keys.LAST_KNOWN_USB_DEBUGGING_ON] ?: false,
            hasRecordedDevBaseline = prefs[Keys.HAS_RECORDED_DEV_BASELINE] ?: false,
            notificationPromptDismissed = prefs[Keys.NOTIFICATION_PROMPT_DISMISSED] ?: false,
            batteryPromptDismissed = prefs[Keys.BATTERY_PROMPT_DISMISSED] ?: false
        )
    }

    override suspend fun getSettingsOnce(): PGuardSettings = settings.first()

    override suspend fun setScanInterval(interval: BackgroundScanInterval) {
        context.pGuardDataStore.edit { it[Keys.SCAN_INTERVAL_HOURS] = interval.hours.toInt() }
    }

    override suspend fun setNotifyNewApp(enabled: Boolean) {
        context.pGuardDataStore.edit { it[Keys.NOTIFY_NEW_APP] = enabled }
    }

    override suspend fun setNotifySignatureChanged(enabled: Boolean) {
        context.pGuardDataStore.edit { it[Keys.NOTIFY_SIGNATURE_CHANGED] = enabled }
    }

    override suspend fun setNotifyNewSuspicious(enabled: Boolean) {
        context.pGuardDataStore.edit { it[Keys.NOTIFY_NEW_SUSPICIOUS] = enabled }
    }

    override suspend fun setNotifyNewRiskyPermission(enabled: Boolean) {
        context.pGuardDataStore.edit { it[Keys.NOTIFY_NEW_RISKY_PERMISSION] = enabled }
    }

    override suspend fun setNotifyDeveloperMode(enabled: Boolean) {
        context.pGuardDataStore.edit { it[Keys.NOTIFY_DEVELOPER_MODE] = enabled }
    }

    override suspend fun setAppLockEnabled(enabled: Boolean) {
        context.pGuardDataStore.edit { it[Keys.APP_LOCK_ENABLED] = enabled }
    }

    override suspend fun updateLastKnownDeveloperMode(
        devOptionsOn: Boolean,
        usbDebuggingOn: Boolean
    ) {
        context.pGuardDataStore.edit {
            it[Keys.LAST_KNOWN_DEV_OPTIONS_ON] = devOptionsOn
            it[Keys.LAST_KNOWN_USB_DEBUGGING_ON] = usbDebuggingOn
            it[Keys.HAS_RECORDED_DEV_BASELINE] = true
        }
    }

    override suspend fun dismissNotificationPrompt() {
        context.pGuardDataStore.edit { it[Keys.NOTIFICATION_PROMPT_DISMISSED] = true }
    }

    override suspend fun dismissBatteryPrompt() {
        context.pGuardDataStore.edit { it[Keys.BATTERY_PROMPT_DISMISSED] = true }
    }
}
