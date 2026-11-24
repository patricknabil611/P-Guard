package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.dashboard.DashboardViewModel
import com.example.ui.navigation.PGuardNavHost
import com.example.ui.theme.PGuardTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as PGuardApplication).container
        val scanRepository = container.securityScanRepository
        val deviceStatusRepository = container.deviceStatusRepository

        setContent {
            PGuardTheme {
                val dashboardViewModel: DashboardViewModel = viewModel(
                    factory = DashboardViewModel.Factory(scanRepository, deviceStatusRepository)
                )

                // Automatically run a local package scan every time the app opens
                LifecycleEventEffect(Lifecycle.Event.ON_START) {
                    dashboardViewModel.onAppOpened()
                }

                // Refresh Developer Mode & Usage Access state whenever returning from Android Settings
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                    dashboardViewModel.onAppResumed()
                }

                PGuardNavHost(
                    scanRepository = scanRepository,
                    deviceStatusRepository = deviceStatusRepository,
                    dashboardViewModel = dashboardViewModel
                )
            }
        }
    }
}
