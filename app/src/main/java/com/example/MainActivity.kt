package com.example

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.preferences.PGuardSettings
import com.example.ui.dashboard.DashboardViewModel
import com.example.ui.lock.AppLockOverlay
import com.example.ui.navigation.PGuardNavHost
import com.example.ui.theme.PGuardTheme
import com.example.worker.NotificationHelper
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : FragmentActivity() {

    private val pendingNotificationRoute = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        extractNotificationRoute(intent)

        val container = (application as PGuardApplication).container
        val scanRepository = container.securityScanRepository
        val deviceStatusRepository = container.deviceStatusRepository
        val preferencesRepository = container.preferencesRepository
        val appLockManager = container.appLockManager

        setContent {
            PGuardTheme {
                val settings by preferencesRepository.settings.collectAsStateWithLifecycle(
                    initialValue = PGuardSettings()
                )
                val isLocked by appLockManager.isLocked.collectAsStateWithLifecycle()
                val targetRoute by pendingNotificationRoute.collectAsStateWithLifecycle()

                // Hide app content in the recent apps switcher whenever App Lock is enabled or locked
                LaunchedEffect(settings.appLockEnabled, isLocked) {
                    if (settings.appLockEnabled || isLocked) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }

                val dashboardViewModel: DashboardViewModel = viewModel(
                    factory = DashboardViewModel.Factory(
                        scanRepository = scanRepository,
                        deviceStatusRepository = deviceStatusRepository,
                        preferencesRepository = preferencesRepository
                    )
                )

                // Automatically run a local package scan & check app lock every time the app opens
                LifecycleEventEffect(Lifecycle.Event.ON_START) {
                    appLockManager.onSessionStarted(settings.appLockEnabled)
                    dashboardViewModel.onAppOpened()
                }

                // Refresh live device posture & permissions whenever returning from Android Settings
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                    dashboardViewModel.onAppResumed()
                }

                // Record background timestamp for 1-minute app lock timeout
                LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
                    appLockManager.onAppBackgrounded(settings.appLockEnabled)
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    if (isLocked) {
                        AppLockOverlay(
                            onUnlocked = { appLockManager.unlock() }
                        )
                    } else {
                        PGuardNavHost(
                            scanRepository = scanRepository,
                            deviceStatusRepository = deviceStatusRepository,
                            preferencesRepository = preferencesRepository,
                            appLockManager = appLockManager,
                            dashboardViewModel = dashboardViewModel,
                            pendingNavigationRoute = targetRoute,
                            onPendingRouteConsumed = { pendingNotificationRoute.value = null }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractNotificationRoute(intent)
    }

    private fun extractNotificationRoute(intent: Intent?) {
        val route = intent?.getStringExtra(NotificationHelper.EXTRA_TARGET_ROUTE)
        if (!route.isNullOrBlank()) {
            pendingNotificationRoute.value = route
        }
    }
}
