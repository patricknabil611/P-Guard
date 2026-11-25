package com.example.ui.dashboard

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.R
import com.example.ui.common.formatScanDateTime
import com.example.ui.common.openBatteryOptimizationSettings
import com.example.ui.common.openPrivacyDashboard
import com.example.ui.theme.PGuardTheme
import com.example.worker.BackgroundScanScheduler
import com.example.worker.NotificationHelper

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onOpenSettings: () -> Unit,
    onOpenSecurityScore: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenNewApps: () -> Unit,
    onOpenSuspiciousApps: () -> Unit,
    onOpenPermissionChanges: () -> Unit,
    onOpenHiddenApps: () -> Unit,
    onOpenRecentlyActive: () -> Unit,
    onOpenSensitiveAccess: () -> Unit,
    onOpenKeyboards: () -> Unit,
    onOpenDataUsage: () -> Unit,
    onOpenDeveloperMode: () -> Unit,
    onOpenTrustedApps: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var hasNotificationPermission by remember {
        mutableStateOf(NotificationHelper.hasNotificationPermission(context))
    }
    var batteryOptRestricting by remember {
        mutableStateOf(BackgroundScanScheduler.isBatteryOptimizationRestricting(context))
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasNotificationPermission = NotificationHelper.hasNotificationPermission(context)
        batteryOptRestricting = BackgroundScanScheduler.isBatteryOptimizationRestricting(context)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted || NotificationHelper.hasNotificationPermission(context)
    }

    val showNotificationPrompt = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        uiState.backgroundScanEnabled &&
        !hasNotificationPermission &&
        !uiState.notificationPromptDismissed

    val showBatteryOptCard = uiState.backgroundScanEnabled &&
        batteryOptRestricting &&
        !uiState.batteryPromptDismissed

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp)
                    .testTag("dashboard_list"),
                contentPadding = PaddingValues(
                    horizontal = 20.dp,
                    vertical = 20.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "header") {
                    DashboardHeader(
                        latestScanMillis = uiState.latestScan?.scannedAtMillis,
                        isScanning = uiState.isScanning,
                        onScanAgainClick = viewModel::onScanAgainClick,
                        onOpenSettings = onOpenSettings
                    )
                }

                item(key = "scan_progress") {
                    AnimatedVisibility(
                        visible = uiState.isScanning,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(CircleShape)
                                .testTag("scan_progress_indicator"),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    }
                }

                if (uiState.scanError != null) {
                    item(key = "scan_error") {
                        ScanErrorBanner(
                            message = stringResource(R.string.scan_error_message)
                        )
                    }
                }

                item(key = "status_card") {
                    StatusOverviewCard(
                        uiState = uiState,
                        onClick = onOpenSecurityScore,
                        modifier = Modifier.testTag("status_summary_card")
                    )
                }

                if (showNotificationPrompt) {
                    item(key = "notification_prompt_card") {
                        NotificationExplanationCard(
                            onAllowClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(
                                        Manifest.permission.POST_NOTIFICATIONS
                                    )
                                }
                            },
                            onDismissClick = viewModel::dismissNotificationPrompt
                        )
                    }
                }

                if (showBatteryOptCard) {
                    item(key = "battery_opt_card") {
                        BatteryOptimizationCard(
                            onOpenSettings = { openBatteryOptimizationSettings(context) },
                            onDismiss = viewModel::dismissBatteryPrompt
                        )
                    }
                }

                item(key = "active_section_header") {
                    SectionLabel(
                        text = stringResource(R.string.section_active_checks),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                item(key = "new_apps_card") {
                    val count = uiState.unreviewedNewAppsCount
                    val latestScan = uiState.latestScan
                    val subtitle = when {
                        count == 1 -> stringResource(R.string.module_new_apps_found_single)
                        count > 1 -> stringResource(R.string.module_new_apps_found_plural, count)
                        latestScan?.isBaseline == true -> stringResource(
                            R.string.module_new_apps_baseline_subtitle,
                            latestScan.totalAppsCount
                        )
                        latestScan != null -> stringResource(
                            R.string.module_new_apps_empty_subtitle,
                            latestScan.totalAppsCount
                        )
                        else -> stringResource(R.string.status_scanning_title)
                    }
                    ActiveModuleSummaryCard(
                        title = stringResource(R.string.module_new_apps_title),
                        subtitle = subtitle,
                        badgeText = if (count > 0) {
                            stringResource(R.string.badge_new_count, count)
                        } else {
                            stringResource(R.string.badge_none_new)
                        },
                        icon = Icons.Outlined.Apps,
                        isWarning = count > 0,
                        onClick = onOpenNewApps,
                        modifier = Modifier.testTag("new_apps_summary_card")
                    )
                }

                item(key = "suspicious_apps_card") {
                    val flagged = uiState.suspiciousAppsCount
                    val trusted = uiState.trustedAppsCount
                    val subtitle = when {
                        flagged == 0 -> stringResource(R.string.module_suspicious_empty_subtitle)
                        flagged == 1 && trusted > 0 -> stringResource(
                            R.string.module_suspicious_found_single,
                            flagged,
                            trusted
                        )
                        flagged > 1 && trusted > 0 -> stringResource(
                            R.string.module_suspicious_found_plural,
                            flagged,
                            trusted
                        )
                        flagged == 1 -> stringResource(R.string.module_suspicious_found_single_no_trusted)
                        else -> stringResource(R.string.module_suspicious_found_plural_no_trusted, flagged)
                    }
                    ActiveModuleSummaryCard(
                        title = stringResource(R.string.module_suspicious_apps_title),
                        subtitle = subtitle,
                        badgeText = if (flagged > 0) {
                            stringResource(R.string.badge_flagged_count, flagged)
                        } else {
                            stringResource(R.string.badge_none_flagged)
                        },
                        icon = Icons.Outlined.ReportProblem,
                        isWarning = flagged > 0,
                        onClick = onOpenSuspiciousApps,
                        modifier = Modifier.testTag("suspicious_apps_summary_card")
                    )
                }

                item(key = "permission_changes_card") {
                    val changes = uiState.permissionChangesCount
                    val subtitle = when (changes) {
                        0 -> stringResource(R.string.module_permission_changes_empty)
                        1 -> stringResource(R.string.module_permission_changes_single)
                        else -> stringResource(R.string.module_permission_changes_plural, changes)
                    }
                    ActiveModuleSummaryCard(
                        title = stringResource(R.string.module_permission_changes_title),
                        subtitle = subtitle,
                        badgeText = if (changes > 0) {
                            stringResource(R.string.badge_changes_count, changes)
                        } else {
                            stringResource(R.string.badge_no_changes)
                        },
                        icon = Icons.Outlined.Key,
                        isWarning = changes > 0,
                        onClick = onOpenPermissionChanges,
                        modifier = Modifier.testTag("permission_changes_summary_card")
                    )
                }

                item(key = "hidden_apps_card") {
                    val hidden = uiState.hiddenUserAppsCount
                    val subtitle = when {
                        hidden == 0 -> stringResource(R.string.module_hidden_empty_subtitle)
                        hidden == 1 -> stringResource(R.string.module_hidden_found_single)
                        else -> stringResource(R.string.module_hidden_found_plural, hidden)
                    }
                    ActiveModuleSummaryCard(
                        title = stringResource(R.string.module_hidden_apps_title),
                        subtitle = subtitle,
                        badgeText = stringResource(R.string.badge_hidden_count, hidden),
                        icon = Icons.Outlined.VisibilityOff,
                        isWarning = hidden > 0,
                        onClick = onOpenHiddenApps,
                        modifier = Modifier.testTag("hidden_apps_summary_card")
                    )
                }

                item(key = "recently_active_card") {
                    val hasAccess = uiState.hasUsageAccess
                    val recentCount = uiState.recentActiveAppsLastHourCount
                    val subtitle = when {
                        !hasAccess -> stringResource(R.string.module_recently_active_needs_access)
                        recentCount == 0 -> stringResource(R.string.module_recently_active_empty)
                        recentCount == 1 -> stringResource(R.string.module_recently_active_single)
                        else -> stringResource(R.string.module_recently_active_plural, recentCount)
                    }
                    val badge = if (hasAccess) {
                        stringResource(R.string.badge_active_count, recentCount)
                    } else {
                        stringResource(R.string.badge_grant_access)
                    }
                    ActiveModuleSummaryCard(
                        title = stringResource(R.string.module_recently_active_title),
                        subtitle = subtitle,
                        badgeText = badge,
                        icon = Icons.Outlined.History,
                        isWarning = false,
                        onClick = onOpenRecentlyActive,
                        modifier = Modifier.testTag("recently_active_summary_card")
                    )
                }

                item(key = "data_usage_card") {
                    val hasAccess = uiState.hasUsageAccess
                    val highCount = uiState.highBackgroundDataAppsCount
                    val activeCount = uiState.activeBackgroundDataAppsCount
                    val subtitle = when {
                        !hasAccess -> stringResource(R.string.module_data_usage_needs_access)
                        highCount == 1 -> stringResource(R.string.module_data_usage_flagged_single)
                        highCount > 1 -> stringResource(R.string.module_data_usage_flagged_plural, highCount)
                        activeCount > 0 -> stringResource(R.string.module_data_usage_ok_count, activeCount)
                        else -> stringResource(R.string.module_data_usage_empty)
                    }
                    val badge = when {
                        !hasAccess -> stringResource(R.string.badge_grant_access)
                        highCount > 0 -> stringResource(R.string.badge_high_data_count, highCount)
                        else -> stringResource(R.string.badge_data_ok)
                    }
                    ActiveModuleSummaryCard(
                        title = stringResource(R.string.module_data_usage_title),
                        subtitle = subtitle,
                        badgeText = badge,
                        icon = Icons.Outlined.DataUsage,
                        isWarning = highCount > 0,
                        onClick = onOpenDataUsage,
                        modifier = Modifier.testTag("data_usage_summary_card")
                    )
                }

                item(key = "sensitive_access_card") {
                    val activeGrants = uiState.sensitiveAccessCount
                    val subtitle = when {
                        activeGrants == 0 -> stringResource(R.string.module_sensitive_empty_subtitle)
                        activeGrants == 1 -> stringResource(R.string.module_sensitive_found_single)
                        else -> stringResource(R.string.module_sensitive_found_plural, activeGrants)
                    }
                    ActiveModuleSummaryCard(
                        title = stringResource(R.string.module_sensitive_access_title),
                        subtitle = subtitle,
                        badgeText = stringResource(R.string.badge_active_count, activeGrants),
                        icon = Icons.Outlined.Lock,
                        isWarning = activeGrants > 0,
                        onClick = onOpenSensitiveAccess,
                        modifier = Modifier.testTag("sensitive_access_summary_card")
                    )
                }

                item(key = "keyboards_card") {
                    val thirdParty = uiState.thirdPartyKeyboardsCount
                    val total = uiState.enabledKeyboardsCount
                    val subtitle = when {
                        thirdParty == 1 -> stringResource(R.string.module_keyboards_third_party_single)
                        thirdParty > 1 -> stringResource(R.string.module_keyboards_third_party_plural, thirdParty)
                        else -> stringResource(R.string.module_keyboards_all_known, total)
                    }
                    val badge = if (thirdParty > 0) {
                        stringResource(R.string.badge_keyboard_third_party, thirdParty)
                    } else {
                        stringResource(R.string.badge_keyboard_known)
                    }
                    ActiveModuleSummaryCard(
                        title = stringResource(R.string.module_keyboards_title),
                        subtitle = subtitle,
                        badgeText = badge,
                        icon = Icons.Outlined.Keyboard,
                        isWarning = thirdParty > 0,
                        onClick = onOpenKeyboards,
                        modifier = Modifier.testTag("keyboards_summary_card")
                    )
                }

                item(key = "privacy_dashboard_card") {
                    val isAndroid12Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    ActiveModuleSummaryCard(
                        title = stringResource(R.string.module_privacy_dashboard_title),
                        subtitle = stringResource(
                            if (isAndroid12Plus) {
                                R.string.module_privacy_dashboard_subtitle
                            } else {
                                R.string.module_privacy_dashboard_legacy
                            }
                        ),
                        badgeText = stringResource(R.string.badge_open),
                        icon = Icons.Outlined.PrivacyTip,
                        trailingIcon = Icons.Outlined.OpenInNew,
                        isWarning = false,
                        onClick = { openPrivacyDashboard(context) },
                        modifier = Modifier.testTag("privacy_dashboard_summary_card")
                    )
                }

                item(key = "developer_mode_card") {
                    val dev = uiState.developerModeStatus
                    val subtitle = when {
                        dev.isUsbDebuggingEnabled -> stringResource(R.string.module_dev_mode_adb_on)
                        dev.isDeveloperOptionsEnabled -> stringResource(R.string.module_dev_mode_dev_on)
                        else -> stringResource(R.string.module_dev_mode_off)
                    }
                    val badge = when {
                        dev.isUsbDebuggingEnabled -> stringResource(R.string.badge_adb_on)
                        dev.isDeveloperOptionsEnabled -> stringResource(R.string.badge_dev_on)
                        else -> stringResource(R.string.badge_off)
                    }
                    ActiveModuleSummaryCard(
                        title = stringResource(R.string.module_developer_mode_title),
                        subtitle = subtitle,
                        badgeText = badge,
                        icon = Icons.Outlined.Code,
                        isWarning = dev.isUsbDebuggingEnabled,
                        onClick = onOpenDeveloperMode,
                        modifier = Modifier.testTag("developer_mode_summary_card")
                    )
                }

                item(key = "history_card") {
                    val count = uiState.scanHistoryCount
                    val subtitle = when (count) {
                        0 -> stringResource(R.string.module_history_empty)
                        1 -> stringResource(R.string.module_history_single)
                        else -> stringResource(R.string.module_history_plural, count)
                    }
                    ActiveModuleSummaryCard(
                        title = stringResource(R.string.module_history_title),
                        subtitle = subtitle,
                        badgeText = stringResource(R.string.badge_history_count, count),
                        icon = Icons.Outlined.Timeline,
                        isWarning = false,
                        onClick = onOpenHistory,
                        modifier = Modifier.testTag("history_summary_card")
                    )
                }

                item(key = "trusted_apps_card") {
                    val trusted = uiState.trustedAppsCount
                    val subtitle = when (trusted) {
                        0 -> stringResource(R.string.module_trusted_apps_subtitle_empty)
                        1 -> stringResource(R.string.module_trusted_apps_subtitle_single)
                        else -> stringResource(R.string.module_trusted_apps_subtitle_plural, trusted)
                    }
                    ActiveModuleSummaryCard(
                        title = stringResource(R.string.module_trusted_apps_title),
                        subtitle = subtitle,
                        badgeText = stringResource(R.string.badge_trusted_count, trusted),
                        icon = Icons.Outlined.VerifiedUser,
                        isWarning = false,
                        onClick = onOpenTrustedApps,
                        modifier = Modifier.testTag("trusted_apps_summary_card")
                    )
                }

                item(key = "bottom_spacer") {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun DashboardHeader(
    latestScanMillis: Long?,
    isScanning: Boolean,
    onScanAgainClick: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scan_icon_spin")
    val spinDegrees by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin_angle"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .testTag("dashboard_brand_logo"),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_background),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                    Image(
                        painter = painterResource(R.drawable.ic_p_guard_logo),
                        contentDescription = stringResource(R.string.app_name),
                        modifier = Modifier.size(32.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text(
                        text = "${stringResource(R.string.app_offline_badge)} · ${
                            stringResource(R.string.app_version_format, BuildConfig.VERSION_NAME)
                        }",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }

                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.testTag("open_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = stringResource(R.string.cd_open_settings),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val scanSubtitle = when {
                isScanning && latestScanMillis == null -> stringResource(R.string.last_scan_scanning)
                latestScanMillis != null -> stringResource(
                    R.string.last_scan_label,
                    formatScanDateTime(latestScanMillis)
                )
                else -> stringResource(R.string.last_scan_never)
            }

            Text(
                text = scanSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
                    .testTag("last_scan_timestamp")
            )

            FilledTonalButton(
                onClick = onScanAgainClick,
                enabled = !isScanning,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                modifier = Modifier.testTag("scan_again_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = null,
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(if (isScanning) spinDegrees else 0f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isScanning) {
                        stringResource(R.string.action_scanning)
                    } else {
                        stringResource(R.string.action_scan_again)
                    },
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun StatusOverviewCard(
    uiState: DashboardUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = PGuardTheme.semanticColors
    val latestScan = uiState.latestScan
    val attentionCount = uiState.totalAttentionCount
    val isInitialScan = uiState.isScanning && latestScan == null
    val score = uiState.securityScoreReport.score
    val isCritical = uiState.rootResult.isRooted || uiState.signatureChangesCount > 0 || score < 50

    val containerStart: Color
    val containerEnd: Color
    val borderColor: Color
    val accentColor: Color
    val contentTextColor: Color
    val title: String
    val subtitle: String

    when {
        isInitialScan -> {
            containerStart = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
            containerEnd = MaterialTheme.colorScheme.surface
            borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
            accentColor = MaterialTheme.colorScheme.primary
            contentTextColor = MaterialTheme.colorScheme.onSurface
            title = stringResource(R.string.status_scanning_title)
            subtitle = stringResource(R.string.status_scanning_subtitle)
        }

        isCritical -> {
            containerStart = semantic.statusDangerContainer
            containerEnd = semantic.statusDangerContainer.copy(alpha = 0.55f)
            borderColor = semantic.statusDangerBorder
            accentColor = semantic.statusDanger
            contentTextColor = semantic.onStatusDangerContainer
            title = if (attentionCount == 1) {
                stringResource(R.string.status_attention_single)
            } else {
                stringResource(R.string.status_attention_plural, attentionCount.coerceAtLeast(1))
            }
            subtitle = buildAttentionSubtitle(uiState)
        }

        attentionCount > 0 -> {
            containerStart = semantic.statusAttentionContainer
            containerEnd = semantic.statusAttentionContainer.copy(alpha = 0.55f)
            borderColor = semantic.statusAttentionBorder
            accentColor = semantic.statusAttention
            contentTextColor = semantic.onStatusAttentionContainer
            title = if (attentionCount == 1) {
                stringResource(R.string.status_attention_single)
            } else {
                stringResource(R.string.status_attention_plural, attentionCount)
            }
            subtitle = buildAttentionSubtitle(uiState)
        }

        else -> {
            containerStart = semantic.statusClearContainer
            containerEnd = semantic.statusClearContainer.copy(alpha = 0.55f)
            borderColor = semantic.statusClearBorder
            accentColor = semantic.statusClear
            contentTextColor = semantic.onStatusClearContainer
            title = stringResource(R.string.status_all_clear)
            val totalApps = latestScan?.totalAppsCount ?: 0
            subtitle = if (latestScan?.isBaseline == true) {
                stringResource(R.string.status_all_clear_baseline, totalApps)
            } else {
                stringResource(R.string.status_all_clear_monitored, totalApps)
            }
        }
    }

    val scoreRingDescription = stringResource(R.string.cd_security_score_ring, score)

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = Color.Transparent,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(containerStart, containerEnd)
                    )
                )
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .semantics { contentDescription = scoreRingDescription },
                    contentAlignment = Alignment.Center
                ) {
                    if (isInitialScan) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(44.dp),
                            strokeWidth = 4.dp,
                            color = accentColor
                        )
                    } else {
                        CircularProgressIndicator(
                            progress = { (score / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxSize(),
                            color = accentColor,
                            trackColor = accentColor.copy(alpha = 0.18f),
                            strokeWidth = 6.dp,
                            strokeCap = StrokeCap.Round
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = score.toString(),
                                style = MaterialTheme.typography.titleLarge,
                                color = contentTextColor
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = contentTextColor
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentTextColor.copy(alpha = 0.84f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.security_score_screen_title),
                    tint = contentTextColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(22.dp)
                )
            }

            if (!isInitialScan) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${stringResource(R.string.security_score_label)}: $score/100",
                        style = MaterialTheme.typography.labelLarge,
                        color = accentColor
                    )
                    Text(
                        text = stringResource(R.string.security_score_tap_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = contentTextColor.copy(alpha = 0.72f)
                    )
                }
            }
        }
    }
}

@Composable
private fun buildAttentionSubtitle(uiState: DashboardUiState): String {
    val parts = buildList {
        if (uiState.rootResult.isRooted) {
            add(stringResource(R.string.status_attention_part_rooted))
        }
        val sig = uiState.signatureChangesCount
        if (sig == 1) add(stringResource(R.string.status_attention_part_sig_change_single))
        else if (sig > 1) add(stringResource(R.string.status_attention_part_sig_change_plural, sig))

        val susp = uiState.suspiciousAppsCount
        if (susp == 1) add(stringResource(R.string.status_attention_part_suspicious_single))
        else if (susp > 1) add(stringResource(R.string.status_attention_part_suspicious_plural, susp))

        val newly = uiState.unreviewedNewAppsCount
        if (newly == 1) add(stringResource(R.string.status_attention_part_new_single))
        else if (newly > 1) add(stringResource(R.string.status_attention_part_new_plural, newly))

        val perms = uiState.permissionChangesCount
        if (perms == 1) add(stringResource(R.string.status_attention_part_perm_changes_single))
        else if (perms > 1) add(stringResource(R.string.status_attention_part_perm_changes_plural, perms))

        val kbs = uiState.thirdPartyKeyboardsCount
        if (kbs == 1) add(stringResource(R.string.status_attention_part_keyboard_single))
        else if (kbs > 1) add(stringResource(R.string.status_attention_part_keyboard_plural, kbs))

        if (uiState.developerModeStatus.isUsbDebuggingEnabled) {
            add(stringResource(R.string.status_attention_part_usb_debugging))
        }
    }
    return parts.ifEmpty { listOf(stringResource(R.string.security_score_fair)) }.joinToString(" · ")
}

@Composable
private fun ActiveModuleSummaryCard(
    title: String,
    subtitle: String,
    badgeText: String,
    icon: ImageVector,
    isWarning: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingIcon: ImageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight
) {
    val semantic = PGuardTheme.semanticColors

    val iconBgColor = if (isWarning) {
        semantic.statusAttentionContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
    }
    val iconTint = if (isWarning) {
        semantic.statusAttention
    } else {
        MaterialTheme.colorScheme.primary
    }
    val badgeBg = if (isWarning) {
        semantic.statusAttentionContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val badgeTextColor = if (isWarning) {
        semantic.statusAttention
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (isWarning) semantic.statusAttentionBorder else MaterialTheme.colorScheme.outlineVariant
        ),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 17.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(iconBgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = badgeBg
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelMedium,
                        color = badgeTextColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Icon(
                    imageVector = trailingIcon,
                    contentDescription = stringResource(R.string.cd_open_module, title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun ScanErrorBanner(message: String) {
    val semantic = PGuardTheme.semanticColors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("scan_error_banner"),
        shape = MaterialTheme.shapes.medium,
        color = semantic.statusDangerContainer,
        border = BorderStroke(1.dp, semantic.statusDangerBorder)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = semantic.onStatusDangerContainer,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 4.dp)
    )
}

@Composable
private fun NotificationExplanationCard(
    onAllowClick: () -> Unit,
    onDismissClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("notification_permission_card"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = stringResource(R.string.notif_prompt_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = stringResource(R.string.notif_prompt_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDismissClick,
                    modifier = Modifier.testTag("dismiss_notification_prompt_button")
                ) {
                    Text(stringResource(R.string.action_dismiss))
                }
                Spacer(modifier = Modifier.width(6.dp))
                Button(
                    onClick = onAllowClick,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("allow_notifications_button")
                ) {
                    Text(
                        text = stringResource(R.string.action_allow_notifications),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun BatteryOptimizationCard(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val semantic = PGuardTheme.semanticColors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("battery_optimization_card"),
        shape = MaterialTheme.shapes.large,
        color = semantic.statusAttentionContainer.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, semantic.statusAttentionBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.BatteryAlert,
                    contentDescription = null,
                    tint = semantic.statusAttention,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.battery_opt_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = semantic.onStatusAttentionContainer
                )
            }
            Text(
                text = stringResource(R.string.battery_opt_body),
                style = MaterialTheme.typography.bodySmall,
                color = semantic.onStatusAttentionContainer.copy(alpha = 0.85f)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("dismiss_battery_card_button")
                ) {
                    Text(stringResource(R.string.action_dismiss))
                }
                Spacer(modifier = Modifier.width(6.dp))
                FilledTonalButton(
                    onClick = onOpenSettings,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("open_battery_settings_button")
                ) {
                    Text(
                        text = stringResource(R.string.action_battery_settings),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}
