package com.example.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.R
import com.example.data.preferences.BackgroundScanInterval
import com.example.report.PdfReportGenerator
import com.example.security.BiometricAuthHelper
import com.example.ui.common.openAppNotificationSettings
import com.example.ui.common.openBatteryOptimizationSettings
import com.example.ui.common.sharePdfReport
import com.example.ui.theme.PGuardTheme
import com.example.worker.BackgroundScanScheduler
import com.example.worker.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onOpenTrustedApps: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val semantic = PGuardTheme.semanticColors

    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var hasNotificationPerm by remember {
        mutableStateOf(NotificationHelper.hasNotificationPermission(context))
    }
    var batteryRestricted by remember {
        mutableStateOf(BackgroundScanScheduler.isBatteryOptimizationRestricting(context))
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasNotificationPerm = NotificationHelper.hasNotificationPermission(context)
        batteryRestricted = BackgroundScanScheduler.isBatteryOptimizationRestricting(context)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPerm = granted || NotificationHelper.hasNotificationPermission(context)
    }

    val exportSuccessMsg = stringResource(R.string.export_success_message)
    val exportErrorMsg = stringResource(R.string.export_error_message)
    val shareChooserTitle = stringResource(R.string.share_report_chooser_title)
    val historyClearedMsg = stringResource(R.string.history_cleared_message)
    val biometricUnavailableMsg = stringResource(R.string.biometric_unavailable_message)
    val biometricFailedMsg = stringResource(R.string.biometric_auth_failed)
    val disableLockTitle = stringResource(R.string.biometric_disable_lock_title)
    val disableLockSubtitle = stringResource(R.string.biometric_disable_lock_subtitle)

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val entry = viewModel.getOrCreateLatestHistoryForExport()
                val ok = if (entry != null) {
                    withContext(Dispatchers.IO) {
                        PdfReportGenerator.writePdfToUri(context, uri, entry)
                    }
                } else {
                    false
                }
                snackbarHostState.showSnackbar(if (ok) exportSuccessMsg else exportErrorMsg)
            }
        }
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text(stringResource(R.string.dialog_clear_history_title)) },
            text = { Text(stringResource(R.string.dialog_clear_history_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearHistoryDialog = false
                        viewModel.clearHistory()
                        scope.launch { snackbarHostState.showSnackbar(historyClearedMsg) }
                    },
                    modifier = Modifier.testTag("confirm_clear_history_button")
                ) {
                    Text(
                        text = stringResource(R.string.action_clear),
                        color = semantic.statusDanger
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_screen_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            val settings = uiState.settings

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp)
                    .testTag("settings_list"),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Background Scan Interval
                item(key = "background_scan") {
                    SettingsGroupCard(title = stringResource(R.string.settings_section_background_scan)) {
                        Text(
                            text = stringResource(R.string.settings_scan_interval_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(
                                BackgroundScanInterval.HOURS_3 to R.string.interval_3h,
                                BackgroundScanInterval.HOURS_6 to R.string.interval_6h,
                                BackgroundScanInterval.HOURS_12 to R.string.interval_12h,
                                BackgroundScanInterval.HOURS_24 to R.string.interval_24h,
                                BackgroundScanInterval.OFF to R.string.interval_off
                            ).forEach { (interval, labelRes) ->
                                val selected = settings.scanInterval == interval
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.onSelectScanInterval(context, interval) },
                                    label = { Text(stringResource(labelRes)) },
                                    leadingIcon = if (selected) {
                                        {
                                            Icon(
                                                imageVector = Icons.Outlined.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                    modifier = Modifier.testTag("interval_${interval.name.lowercase()}")
                                )
                            }
                        }

                        if (batteryRestricted && settings.scanInterval != BackgroundScanInterval.OFF) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
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
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = { openBatteryOptimizationSettings(context) },
                                    modifier = Modifier.testTag("settings_battery_button")
                                ) {
                                    Text(stringResource(R.string.action_battery_settings))
                                }
                            }
                        }
                    }
                }

                // 2. Notification Alert Toggles
                item(key = "notifications") {
                    SettingsGroupCard(title = stringResource(R.string.settings_section_notifications)) {
                        if (!hasNotificationPerm) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = semantic.statusAttentionContainer.copy(alpha = 0.7f),
                                border = BorderStroke(1.dp, semantic.statusAttentionBorder)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.NotificationsOff,
                                        contentDescription = null,
                                        tint = semantic.statusAttention,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_notifications_permission_needed),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = semantic.onStatusAttentionContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(
                                        onClick = {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                notificationPermissionLauncher.launch(
                                                    Manifest.permission.POST_NOTIFICATIONS
                                                )
                                            } else {
                                                openAppNotificationSettings(context)
                                            }
                                        },
                                        modifier = Modifier.testTag("settings_allow_notifications_button")
                                    ) {
                                        Text(stringResource(R.string.action_allow_notifications))
                                    }
                                }
                            }
                        }

                        SettingsSwitchRow(
                            title = stringResource(R.string.settings_notif_new_app),
                            checked = settings.notifyNewApp,
                            onCheckedChange = viewModel::onToggleNotifyNewApp,
                            testTag = "switch_notify_new_app"
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SettingsSwitchRow(
                            title = stringResource(R.string.settings_notif_signature),
                            checked = settings.notifySignatureChanged,
                            onCheckedChange = viewModel::onToggleNotifySignature,
                            testTag = "switch_notify_signature"
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SettingsSwitchRow(
                            title = stringResource(R.string.settings_notif_suspicious),
                            checked = settings.notifyNewSuspicious,
                            onCheckedChange = viewModel::onToggleNotifySuspicious,
                            testTag = "switch_notify_suspicious"
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SettingsSwitchRow(
                            title = stringResource(R.string.settings_notif_risky_perm),
                            checked = settings.notifyNewRiskyPermission,
                            onCheckedChange = viewModel::onToggleNotifyRiskyPermission,
                            testTag = "switch_notify_risky_perm"
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SettingsSwitchRow(
                            title = stringResource(R.string.settings_notif_dev_mode),
                            checked = settings.notifyDeveloperMode,
                            onCheckedChange = viewModel::onToggleNotifyDevMode,
                            testTag = "switch_notify_dev_mode"
                        )
                    }
                }

                // 3. App Protection (App Lock + Trusted Apps)
                item(key = "security") {
                    SettingsGroupCard(title = stringResource(R.string.settings_section_security)) {
                        SettingsSwitchRow(
                            title = stringResource(R.string.settings_lock_p_guard_title),
                            subtitle = stringResource(R.string.settings_lock_p_guard_desc),
                            checked = settings.appLockEnabled,
                            onCheckedChange = { wantEnabled ->
                                if (wantEnabled) {
                                    if (!BiometricAuthHelper.canAuthenticate(context)) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar(biometricUnavailableMsg)
                                        }
                                    } else {
                                        viewModel.setAppLockEnabled(true)
                                    }
                                } else {
                                    // Turning Lock P Guard OFF ALWAYS requires biometric / PIN auth
                                    val activity = BiometricAuthHelper.findFragmentActivity(context)
                                    if (activity == null || !BiometricAuthHelper.canAuthenticate(context)) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar(biometricUnavailableMsg)
                                        }
                                    } else {
                                        BiometricAuthHelper.authenticate(
                                            activity = activity,
                                            title = disableLockTitle,
                                            subtitle = disableLockSubtitle,
                                            onSuccess = { viewModel.setAppLockEnabled(false) },
                                            onFailure = {
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(biometricFailedMsg)
                                                }
                                            }
                                        )
                                    }
                                }
                            },
                            testTag = "switch_lock_p_guard"
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(onClick = onOpenTrustedApps)
                                .padding(vertical = 6.dp)
                                .testTag("settings_trusted_apps_row"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.VerifiedUser,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_trusted_apps_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.settings_trusted_apps_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = uiState.trustedAppsCount.toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // 4. Reports & Data
                item(key = "data_reports") {
                    SettingsGroupCard(title = stringResource(R.string.settings_section_data)) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = stringResource(R.string.settings_export_report_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.settings_export_report_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        savePdfLauncher.launch(PdfReportGenerator.defaultReportFilename())
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("settings_export_report_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.PictureAsPdf,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.action_export_report),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            val entry = viewModel.getOrCreateLatestHistoryForExport()
                                            val uri = if (entry != null) {
                                                withContext(Dispatchers.IO) {
                                                    PdfReportGenerator.createShareablePdfUri(context, entry)
                                                }
                                            } else {
                                                null
                                            }
                                            if (uri != null) {
                                                sharePdfReport(context, uri, shareChooserTitle)
                                            } else {
                                                snackbarHostState.showSnackbar(exportErrorMsg)
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("settings_share_report_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Share,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.action_share_pdf),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { showClearHistoryDialog = true }
                                .padding(vertical = 6.dp)
                                .testTag("settings_clear_history_row"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = null,
                                tint = semantic.statusDanger,
                                modifier = Modifier.size(22.dp)
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_clear_history_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = semantic.statusDanger
                                )
                                Text(
                                    text = stringResource(R.string.settings_clear_history_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = uiState.historyEntriesCount.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 5. About
                item(key = "about") {
                    SettingsGroupCard(title = stringResource(R.string.settings_section_about)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp)),
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
                                    modifier = Modifier.size(34.dp)
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(
                                        R.string.settings_about_version,
                                        BuildConfig.VERSION_NAME,
                                        BuildConfig.VERSION_CODE
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = stringResource(R.string.settings_about_offline),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = stringResource(R.string.settings_about_repo),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                item(key = "bottom_spacer") {
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
private fun SettingsGroupCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            content()
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    subtitle: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
