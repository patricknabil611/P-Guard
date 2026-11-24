package com.example.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.repository.DeviceStatusRepository
import com.example.data.repository.SecurityScanRepository
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.dashboard.DashboardViewModel
import com.example.ui.detail.AppDetailScreen
import com.example.ui.detail.AppDetailViewModel
import com.example.ui.developer.DeveloperModeScreen
import com.example.ui.developer.DeveloperModeViewModel
import com.example.ui.hidden.HiddenAppsScreen
import com.example.ui.hidden.HiddenAppsViewModel
import com.example.ui.newapps.NewAppsScreen
import com.example.ui.newapps.NewAppsViewModel
import com.example.ui.recent.RecentlyActiveScreen
import com.example.ui.recent.RecentlyActiveViewModel
import com.example.ui.sensitive.SensitiveAccessScreen
import com.example.ui.sensitive.SensitiveAccessViewModel
import com.example.ui.suspicious.SuspiciousAppsScreen
import com.example.ui.suspicious.SuspiciousAppsViewModel
import com.example.ui.trusted.TrustedAppsScreen
import com.example.ui.trusted.TrustedAppsViewModel

object PGuardRoutes {
    const val DASHBOARD = "dashboard"
    const val NEW_APPS = "new_apps"
    const val SUSPICIOUS_APPS = "suspicious_apps"
    const val HIDDEN_APPS = "hidden_apps"
    const val RECENTLY_ACTIVE = "recently_active"
    const val SENSITIVE_ACCESS = "sensitive_access"
    const val DEVELOPER_MODE = "developer_mode"
    const val TRUSTED_APPS = "trusted_apps"
    const val ARG_PACKAGE_NAME = "packageName"
    const val APP_DETAIL = "app_detail/{$ARG_PACKAGE_NAME}"

    fun appDetail(packageName: String): String =
        "app_detail/${Uri.encode(packageName)}"
}

@Composable
fun PGuardNavHost(
    scanRepository: SecurityScanRepository,
    deviceStatusRepository: DeviceStatusRepository,
    dashboardViewModel: DashboardViewModel,
    navController: NavHostController = rememberNavController()
) {
    val openAppDetail: (String) -> Unit = { packageName ->
        navController.navigate(PGuardRoutes.appDetail(packageName)) {
            launchSingleTop = true
        }
    }

    NavHost(
        navController = navController,
        startDestination = PGuardRoutes.DASHBOARD
    ) {
        composable(route = PGuardRoutes.DASHBOARD) {
            DashboardScreen(
                viewModel = dashboardViewModel,
                onOpenNewApps = {
                    navController.navigate(PGuardRoutes.NEW_APPS) { launchSingleTop = true }
                },
                onOpenSuspiciousApps = {
                    navController.navigate(PGuardRoutes.SUSPICIOUS_APPS) { launchSingleTop = true }
                },
                onOpenHiddenApps = {
                    navController.navigate(PGuardRoutes.HIDDEN_APPS) { launchSingleTop = true }
                },
                onOpenRecentlyActive = {
                    navController.navigate(PGuardRoutes.RECENTLY_ACTIVE) { launchSingleTop = true }
                },
                onOpenSensitiveAccess = {
                    navController.navigate(PGuardRoutes.SENSITIVE_ACCESS) { launchSingleTop = true }
                },
                onOpenDeveloperMode = {
                    navController.navigate(PGuardRoutes.DEVELOPER_MODE) { launchSingleTop = true }
                },
                onOpenTrustedApps = {
                    navController.navigate(PGuardRoutes.TRUSTED_APPS) { launchSingleTop = true }
                }
            )
        }

        composable(route = PGuardRoutes.NEW_APPS) {
            val newAppsViewModel: NewAppsViewModel = viewModel(
                factory = NewAppsViewModel.Factory(scanRepository)
            )
            NewAppsScreen(
                viewModel = newAppsViewModel,
                onNavigateBack = { navController.popBackStack() },
                onOpenAppDetail = openAppDetail
            )
        }

        composable(route = PGuardRoutes.SUSPICIOUS_APPS) {
            val suspiciousViewModel: SuspiciousAppsViewModel = viewModel(
                factory = SuspiciousAppsViewModel.Factory(scanRepository)
            )
            SuspiciousAppsScreen(
                viewModel = suspiciousViewModel,
                onNavigateBack = { navController.popBackStack() },
                onOpenAppDetail = openAppDetail,
                onOpenTrustedApps = {
                    navController.navigate(PGuardRoutes.TRUSTED_APPS) { launchSingleTop = true }
                }
            )
        }

        composable(route = PGuardRoutes.HIDDEN_APPS) {
            val hiddenViewModel: HiddenAppsViewModel = viewModel(
                factory = HiddenAppsViewModel.Factory(scanRepository)
            )
            HiddenAppsScreen(
                viewModel = hiddenViewModel,
                onNavigateBack = { navController.popBackStack() },
                onOpenAppDetail = openAppDetail
            )
        }

        composable(route = PGuardRoutes.RECENTLY_ACTIVE) {
            val recentViewModel: RecentlyActiveViewModel = viewModel(
                factory = RecentlyActiveViewModel.Factory(scanRepository, deviceStatusRepository)
            )
            RecentlyActiveScreen(
                viewModel = recentViewModel,
                onNavigateBack = { navController.popBackStack() },
                onOpenAppDetail = openAppDetail
            )
        }

        composable(route = PGuardRoutes.SENSITIVE_ACCESS) {
            val sensitiveViewModel: SensitiveAccessViewModel = viewModel(
                factory = SensitiveAccessViewModel.Factory(scanRepository)
            )
            SensitiveAccessScreen(
                viewModel = sensitiveViewModel,
                onNavigateBack = { navController.popBackStack() },
                onOpenAppDetail = openAppDetail
            )
        }

        composable(route = PGuardRoutes.DEVELOPER_MODE) {
            val devModeViewModel: DeveloperModeViewModel = viewModel(
                factory = DeveloperModeViewModel.Factory(deviceStatusRepository)
            )
            DeveloperModeScreen(
                viewModel = devModeViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(route = PGuardRoutes.TRUSTED_APPS) {
            val trustedViewModel: TrustedAppsViewModel = viewModel(
                factory = TrustedAppsViewModel.Factory(scanRepository)
            )
            TrustedAppsScreen(
                viewModel = trustedViewModel,
                onNavigateBack = { navController.popBackStack() },
                onOpenAppDetail = openAppDetail
            )
        }

        composable(
            route = PGuardRoutes.APP_DETAIL,
            arguments = listOf(
                navArgument(PGuardRoutes.ARG_PACKAGE_NAME) { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val encodedPkg = backStackEntry.arguments?.getString(PGuardRoutes.ARG_PACKAGE_NAME).orEmpty()
            val packageName = Uri.decode(encodedPkg)
            val detailViewModel: AppDetailViewModel = viewModel(
                key = "app_detail_$packageName",
                factory = AppDetailViewModel.Factory(packageName, scanRepository, deviceStatusRepository)
            )
            AppDetailScreen(
                viewModel = detailViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
