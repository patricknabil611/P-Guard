package com.example.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.preferences.PGuardPreferencesRepository
import com.example.data.repository.DeviceStatusRepository
import com.example.data.repository.SecurityScanRepository
import com.example.security.AppLockManager
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.dashboard.DashboardViewModel
import com.example.ui.datausage.DataUsageScreen
import com.example.ui.datausage.DataUsageViewModel
import com.example.ui.detail.AppDetailScreen
import com.example.ui.detail.AppDetailViewModel
import com.example.ui.developer.DeveloperModeScreen
import com.example.ui.developer.DeveloperModeViewModel
import com.example.ui.hidden.HiddenAppsScreen
import com.example.ui.hidden.HiddenAppsViewModel
import com.example.ui.history.HistoryDetailScreen
import com.example.ui.history.HistoryDetailViewModel
import com.example.ui.history.HistoryScreen
import com.example.ui.history.HistoryViewModel
import com.example.ui.keyboards.KeyboardsScreen
import com.example.ui.keyboards.KeyboardsViewModel
import com.example.ui.newapps.NewAppsScreen
import com.example.ui.newapps.NewAppsViewModel
import com.example.ui.permissions.PermissionChangesScreen
import com.example.ui.permissions.PermissionChangesViewModel
import com.example.ui.recent.RecentlyActiveScreen
import com.example.ui.recent.RecentlyActiveViewModel
import com.example.ui.score.SecurityScoreScreen
import com.example.ui.score.SecurityScoreViewModel
import com.example.ui.sensitive.SensitiveAccessScreen
import com.example.ui.sensitive.SensitiveAccessViewModel
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SettingsViewModel
import com.example.ui.suspicious.SuspiciousAppsScreen
import com.example.ui.suspicious.SuspiciousAppsViewModel
import com.example.ui.trusted.TrustedAppsScreen
import com.example.ui.trusted.TrustedAppsViewModel

object PGuardRoutes {
    const val DASHBOARD = "dashboard"
    const val SETTINGS = "settings"
    const val SECURITY_SCORE = "security_score"
    const val HISTORY = "history"
    const val ARG_HISTORY_ID = "historyId"
    const val HISTORY_DETAIL = "history_detail/{$ARG_HISTORY_ID}"
    const val NEW_APPS = "new_apps"
    const val SUSPICIOUS_APPS = "suspicious_apps"
    const val PERMISSION_CHANGES = "permission_changes"
    const val HIDDEN_APPS = "hidden_apps"
    const val RECENTLY_ACTIVE = "recently_active"
    const val DATA_USAGE = "data_usage"
    const val SENSITIVE_ACCESS = "sensitive_access"
    const val KEYBOARDS = "keyboards"
    const val DEVELOPER_MODE = "developer_mode"
    const val TRUSTED_APPS = "trusted_apps"
    const val ARG_PACKAGE_NAME = "packageName"
    const val APP_DETAIL = "app_detail/{$ARG_PACKAGE_NAME}"

    fun appDetail(packageName: String): String =
        "app_detail/${Uri.encode(packageName)}"

    fun historyDetail(historyId: Long): String =
        "history_detail/$historyId"
}

@Composable
fun PGuardNavHost(
    scanRepository: SecurityScanRepository,
    deviceStatusRepository: DeviceStatusRepository,
    preferencesRepository: PGuardPreferencesRepository,
    appLockManager: AppLockManager,
    dashboardViewModel: DashboardViewModel,
    pendingNavigationRoute: String? = null,
    onPendingRouteConsumed: () -> Unit = {},
    navController: NavHostController = rememberNavController()
) {
    LaunchedEffect(pendingNavigationRoute) {
        val route = pendingNavigationRoute
        if (!route.isNullOrBlank()) {
            val allowedRoutes = setOf(
                PGuardRoutes.NEW_APPS,
                PGuardRoutes.SUSPICIOUS_APPS,
                PGuardRoutes.PERMISSION_CHANGES,
                PGuardRoutes.DEVELOPER_MODE,
                PGuardRoutes.HISTORY,
                PGuardRoutes.SECURITY_SCORE,
                PGuardRoutes.SETTINGS
            )
            if (route in allowedRoutes) {
                navController.navigate(route) { launchSingleTop = true }
            }
            onPendingRouteConsumed()
        }
    }

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
                onOpenSettings = {
                    navController.navigate(PGuardRoutes.SETTINGS) { launchSingleTop = true }
                },
                onOpenSecurityScore = {
                    navController.navigate(PGuardRoutes.SECURITY_SCORE) { launchSingleTop = true }
                },
                onOpenHistory = {
                    navController.navigate(PGuardRoutes.HISTORY) { launchSingleTop = true }
                },
                onOpenNewApps = {
                    navController.navigate(PGuardRoutes.NEW_APPS) { launchSingleTop = true }
                },
                onOpenSuspiciousApps = {
                    navController.navigate(PGuardRoutes.SUSPICIOUS_APPS) { launchSingleTop = true }
                },
                onOpenPermissionChanges = {
                    navController.navigate(PGuardRoutes.PERMISSION_CHANGES) { launchSingleTop = true }
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
                onOpenKeyboards = {
                    navController.navigate(PGuardRoutes.KEYBOARDS) { launchSingleTop = true }
                },
                onOpenDataUsage = {
                    navController.navigate(PGuardRoutes.DATA_USAGE) { launchSingleTop = true }
                },
                onOpenDeveloperMode = {
                    navController.navigate(PGuardRoutes.DEVELOPER_MODE) { launchSingleTop = true }
                },
                onOpenTrustedApps = {
                    navController.navigate(PGuardRoutes.TRUSTED_APPS) { launchSingleTop = true }
                }
            )
        }

        composable(route = PGuardRoutes.SETTINGS) {
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.Factory(
                    preferencesRepository = preferencesRepository,
                    scanRepository = scanRepository,
                    deviceStatusRepository = deviceStatusRepository,
                    appLockManager = appLockManager
                )
            )
            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() },
                onOpenTrustedApps = {
                    navController.navigate(PGuardRoutes.TRUSTED_APPS) { launchSingleTop = true }
                }
            )
        }

        composable(route = PGuardRoutes.HISTORY) {
            val historyViewModel: HistoryViewModel = viewModel(
                factory = HistoryViewModel.Factory(scanRepository)
            )
            HistoryScreen(
                viewModel = historyViewModel,
                onNavigateBack = { navController.popBackStack() },
                onOpenHistoryDetail = { historyId ->
                    navController.navigate(PGuardRoutes.historyDetail(historyId)) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(
            route = PGuardRoutes.HISTORY_DETAIL,
            arguments = listOf(
                navArgument(PGuardRoutes.ARG_HISTORY_ID) { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val historyId = backStackEntry.arguments?.getLong(PGuardRoutes.ARG_HISTORY_ID) ?: 0L
            val detailViewModel: HistoryDetailViewModel = viewModel(
                key = "history_detail_$historyId",
                factory = HistoryDetailViewModel.Factory(historyId, scanRepository)
            )
            HistoryDetailScreen(
                viewModel = detailViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(route = PGuardRoutes.SECURITY_SCORE) {
            val scoreViewModel: SecurityScoreViewModel = viewModel(
                factory = SecurityScoreViewModel.Factory(scanRepository, deviceStatusRepository)
            )
            SecurityScoreScreen(
                viewModel = scoreViewModel,
                onNavigateBack = { navController.popBackStack() },
                onOpenDeveloperMode = {
                    navController.navigate(PGuardRoutes.DEVELOPER_MODE) { launchSingleTop = true }
                },
                onOpenSuspiciousApps = {
                    navController.navigate(PGuardRoutes.SUSPICIOUS_APPS) { launchSingleTop = true }
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

        composable(route = PGuardRoutes.PERMISSION_CHANGES) {
            val permChangesViewModel: PermissionChangesViewModel = viewModel(
                factory = PermissionChangesViewModel.Factory(scanRepository)
            )
            PermissionChangesScreen(
                viewModel = permChangesViewModel,
                onNavigateBack = { navController.popBackStack() },
                onOpenAppDetail = openAppDetail
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

        composable(route = PGuardRoutes.DATA_USAGE) {
            val dataUsageViewModel: DataUsageViewModel = viewModel(
                factory = DataUsageViewModel.Factory(scanRepository, deviceStatusRepository)
            )
            DataUsageScreen(
                viewModel = dataUsageViewModel,
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

        composable(route = PGuardRoutes.KEYBOARDS) {
            val keyboardsViewModel: KeyboardsViewModel = viewModel(
                factory = KeyboardsViewModel.Factory(scanRepository, deviceStatusRepository)
            )
            KeyboardsScreen(
                viewModel = keyboardsViewModel,
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
