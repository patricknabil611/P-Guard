package com.example.ui.history

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.entity.ScanHistoryDetails
import com.example.data.local.entity.ScanHistoryEntity
import com.example.report.PdfReportGenerator
import com.example.ui.common.formatScanDateTime
import com.example.ui.common.formatShortPermissionName
import com.example.ui.common.sharePdfReport
import com.example.ui.theme.PGuardTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryDetailScreen(
    viewModel: HistoryDetailViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val exportSuccessMsg = stringResource(R.string.export_success_message)
    val exportErrorMsg = stringResource(R.string.export_error_message)
    val shareChooserTitle = stringResource(R.string.share_report_chooser_title)

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val entry = uiState.entry
        if (uri != null && entry != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    PdfReportGenerator.writePdfToUri(context, uri, entry)
                }
                snackbarHostState.showSnackbar(if (ok) exportSuccessMsg else exportErrorMsg)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.history_detail_screen_title),
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
            when {
                uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                uiState.entry == null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.history_detail_not_found),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                else -> {
                    val entry = uiState.entry!!
                    HistoryDetailContent(
                        entry = entry,
                        details = uiState.details,
                        onExportSave = {
                            savePdfLauncher.launch(
                                PdfReportGenerator.defaultReportFilename(entry.scannedAtMillis)
                            )
                        },
                        onSharePdf = {
                            scope.launch {
                                val uri = withContext(Dispatchers.IO) {
                                    PdfReportGenerator.createShareablePdfUri(context, entry)
                                }
                                if (uri != null) {
                                    sharePdfReport(context, uri, shareChooserTitle)
                                } else {
                                    snackbarHostState.showSnackbar(exportErrorMsg)
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryDetailContent(
    entry: ScanHistoryEntity,
    details: ScanHistoryDetails,
    onExportSave: () -> Unit,
    onSharePdf: () -> Unit
) {
    val semantic = PGuardTheme.semanticColors
    val (scoreBg, scoreFg, scoreBorder) = when {
        entry.securityScore >= 80 -> Triple(
            semantic.statusClearContainer,
            semantic.statusClear,
            semantic.statusClearBorder
        )
        entry.securityScore >= 50 -> Triple(
            semantic.statusAttentionContainer,
            semantic.statusAttention,
            semantic.statusAttentionBorder
        )
        else -> Triple(
            semantic.statusDangerContainer,
            semantic.statusDanger,
            semantic.statusDangerBorder
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 640.dp)
            .testTag("history_detail_list"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Overview & Export actions
        item(key = "overview") {
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
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(56.dp),
                            shape = CircleShape,
                            color = scoreBg,
                            border = BorderStroke(1.dp, scoreBorder)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = entry.securityScore.toString(),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = scoreFg
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = formatScanDateTime(entry.scannedAtMillis),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = entry.summaryText.ifBlank {
                                    stringResource(R.string.history_no_changes)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${
                                    stringResource(
                                        if (entry.isBackground) R.string.badge_scan_background
                                        else R.string.badge_scan_manual
                                    )
                                } · ${entry.totalAppsCount} apps",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onExportSave,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("history_export_report_button")
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
                            onClick = onSharePdf,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("history_share_report_button")
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
            }
        }

        // New apps
        item(key = "new_apps") {
            HistorySectionCard(title = "${stringResource(R.string.history_section_new_apps)} (${details.newApps.size})") {
                if (details.newApps.isEmpty()) {
                    MutedNoneText()
                } else {
                    details.newApps.forEachIndexed { idx, app ->
                        if (idx > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(app.appName, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${app.packageName} · ${app.detail}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Permission changes
        item(key = "perm_changes") {
            HistorySectionCard(
                title = "${stringResource(R.string.history_section_permission_changes)} (${details.permissionChanges.size})"
            ) {
                if (details.permissionChanges.isEmpty()) {
                    MutedNoneText()
                } else {
                    details.permissionChanges.forEachIndexed { idx, change ->
                        if (idx > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(change.appName, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "+ " + change.addedPermissions.joinToString(", ") {
                                    formatShortPermissionName(it)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (change.hasRiskyPermission) {
                                    semantic.statusAttention
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        }

        // Suspicious apps
        item(key = "suspicious") {
            HistorySectionCard(
                title = "${stringResource(R.string.history_section_suspicious)} (${details.suspiciousApps.size})"
            ) {
                if (details.suspiciousApps.isEmpty()) {
                    MutedNoneText()
                } else {
                    details.suspiciousApps.forEachIndexed { idx, app ->
                        if (idx > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "${app.appName} · ${app.riskLevel}",
                                style = MaterialTheme.typography.titleSmall,
                                color = if (app.riskLevel == "HIGH") semantic.statusDanger else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                app.reasons.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Hidden apps
        item(key = "hidden") {
            HistorySectionCard(
                title = "${stringResource(R.string.history_section_hidden)} (${details.hiddenApps.size})"
            ) {
                if (details.hiddenApps.isEmpty()) {
                    MutedNoneText()
                } else {
                    details.hiddenApps.forEachIndexed { idx, app ->
                        if (idx > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(app.appName, style = MaterialTheme.typography.titleSmall)
                            Text(
                                app.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Checklist results
        if (details.checklist.isNotEmpty()) {
            item(key = "checklist") {
                HistorySectionCard(title = stringResource(R.string.history_section_checklist)) {
                    details.checklist.forEachIndexed { idx, item ->
                        if (idx > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        val (icon, tint) = when (item.status) {
                            "PASS" -> Icons.Outlined.CheckCircle to semantic.statusClear
                            "WARNING" -> Icons.Outlined.WarningAmber to semantic.statusAttention
                            "FAIL" -> Icons.Outlined.Cancel to semantic.statusDanger
                            else -> Icons.Outlined.Info to MaterialTheme.colorScheme.primary
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = tint,
                                modifier = Modifier.size(18.dp)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(item.title, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    item.explanation,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Developer mode & integrity
        item(key = "dev_mode") {
            HistorySectionCard(title = stringResource(R.string.history_section_dev_mode)) {
                Text(
                    text = "${stringResource(R.string.dev_row_developer_options)}: ${
                        stringResource(if (details.developerOptionsEnabled) R.string.status_on else R.string.status_off)
                    }",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "${stringResource(R.string.dev_row_usb_debugging)}: ${
                        stringResource(if (details.usbDebuggingEnabled) R.string.status_on else R.string.status_off)
                    }",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "${stringResource(R.string.check_root_title)}: ${
                        if (details.isRooted) stringResource(R.string.root_status_detected)
                        else stringResource(R.string.root_status_not_rooted)
                    }",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (details.isRooted) semantic.statusDanger else semantic.statusClear
                )
            }
        }

        item(key = "bottom_spacer") {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun HistorySectionCard(
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
private fun MutedNoneText() {
    Text(
        text = stringResource(R.string.history_none_in_section),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
