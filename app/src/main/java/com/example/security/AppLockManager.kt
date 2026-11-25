package com.example.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppLockManager(
    private val clockMillis: () -> Long = { System.currentTimeMillis() }
) {
    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    @Volatile
    private var lastBackgroundedAtMillis: Long = 0L

    @Volatile
    private var hasInitializedForSession: Boolean = false

    fun onSessionStarted(appLockEnabled: Boolean) {
        if (!hasInitializedForSession) {
            hasInitializedForSession = true
            _isLocked.value = appLockEnabled
            return
        }
        onAppForegrounded(appLockEnabled)
    }

    fun onAppBackgrounded(appLockEnabled: Boolean) {
        if (appLockEnabled) {
            lastBackgroundedAtMillis = clockMillis()
        }
    }

    fun onAppForegrounded(appLockEnabled: Boolean) {
        if (!appLockEnabled) {
            _isLocked.value = false
            lastBackgroundedAtMillis = 0L
            return
        }
        val bgAt = lastBackgroundedAtMillis
        if (bgAt > 0L && (clockMillis() - bgAt) >= LOCK_TIMEOUT_MILLIS) {
            _isLocked.value = true
        }
    }

    fun unlock() {
        _isLocked.value = false
        lastBackgroundedAtMillis = 0L
    }

    fun onLockSettingChanged(enabled: Boolean) {
        if (!enabled) {
            _isLocked.value = false
            lastBackgroundedAtMillis = 0L
        }
    }

    companion object {
        const val LOCK_TIMEOUT_MILLIS: Long = 60_000L // 1 minute
    }
}
