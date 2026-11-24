package com.example.ui.newapps

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.scanner.AppSecurityAssessment
import com.example.ui.common.AppPackageIcon
import com.example.ui.common.ListSearchAndSortBar
import com.example.ui.common.RiskLevelBadge
import com.example.ui.common.formatInstallerSource
import com.example.ui.common.formatScanDateTime
import com.example.ui.common.formatShortDate
import com.example.ui.common.openAppInfoSettings
import com.example.ui.theme.PGuardTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewAppsScreen(
    viewModel: NewAppsViewModel,
    onNavigateBack: () -> Unit,
    onOpenAppDetail: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.new_apps_screen_title),
                            style = MaterialTheme.typography.titleLarge
                        )
                        val latestScan = uiState.latestScan
                        if (latestScan != null) {
                            Text(
                                text = stringResource(
                                    R.string.last_scan_label,
                                    formatScanDateTime(latestScan.scannedAtMillis)
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
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
                actions = {
                    IconButton(
                        onClick = viewModel::onScanAgain,
                        enabled = !uiState.isScanning,
                        modifier = Modifier.testTag("detail_scan_again_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.cd_scan_again)
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp)
            ) {
                AnimatedVisibility(
                    visible = uiState.isScanning,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .height(3.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                }

                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                        .testTag("new_apps_tab_switcher")
                ) {
                    SegmentedButton(
                        selected = uiState.selectedTab == NewAppsTab.NEW_APPS,
                        onClick = { viewModel.onSelectTab(NewAppsTab.NEW_APPS) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        modifier = Modifier.testTag("tab_new_apps")
                    ) {
                        Text(
                            text = stringResource(R.string.tab_new_apps, uiState.totalUnreviewedCount),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    SegmentedButton(
                        selected = uiState.selectedTab == NewAppsTab.SNAPSHOT,
                        onClick = { viewModel.onSelectTab(NewAppsTab.SNAPSHOT) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        modifier = Modifier.testTag("tab_snapshot")
                    ) {
                        Text(
                            text = stringResource(R.string.tab_all_installed, uiState.totalSnapshotCount),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                ListSearchAndSortBar(
                    searchQuery = uiState.searchQuery,
                    onSearchQueryChange = viewModel::onSearchQueryChange,
                    sortOption = uiState.sortOption,
                    onSortOptionChange = viewModel::onSortOptionChange,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )

                when (uiState.selectedTab) {
                    NewAppsTab.NEW_APPS -> {
                        NewAppsTabContent(
                            uiState = uiState,
                            onMarkReviewed = viewModel::onMarkReviewed,
                            onMarkAllReviewed = viewModel::onMarkAllReviewed,
                            onClearHistory = viewModel::onClearHistory,
                            onOpenAppDetail = onOpenAppDetail,
                            onOpenAppInfo = { pkg -> openAppInfoSettings(context, pkg) }
                        )
                    }

                    NewAppsTab.SNAPSHOT -> {
                        SnapshotTabContent(
                            uiState = uiState,
                            onFilterChange = viewModel::onSnapshotFilterChange,
                            onOpenAppDetail = onOpenAppDetail
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NewAppsTabContent(
    uiState: NewAppsUiState,
    onMarkReviewed: (String) -> Unit,
    onMarkAllReviewed: () -> Unit,
    onClearHistory: () -> Unit,
    onOpenAppDetail: (String) -> Unit,
    onOpenAppInfo: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("new_apps_list"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (uiState.unreviewedApps.isEmpty()) {
            item(key = "empty_new_apps") {
                EmptyNewAppsCard(
                    isBaseline = uiState.latestScan?.isBaseline == true,
                    totalAppsCount = uiState.latestScan?.totalAppsCount ?: uiState.totalSnapshotCount,
                    hasSearchQuery = uiState.searchQuery.isNotBlank() && uiState.totalUnreviewedCount > 0
                )
            }
        } else {
            item(key = "needs_review_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.section_needs_review),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = onMarkAllReviewed,
                        modifier = Modifier.testTag("mark_all_reviewed_button")
                    ) {
                        Text(
                            text = stringResource(R.string.action_mark_all_reviewed),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            items(
                items = uiState.unreviewedApps,
                key = { "unreviewed_${it.entity.packageName}" }
            ) { item ->
                DetectedNewAppCard(
                    item = item,
                    onOpenDetail = { onOpenAppDetail(item.entity.packageName) },
                    onMarkReviewed = { onMarkReviewed(item.entity.packageName) },
                    onOpenAppInfo = { onOpenAppInfo(item.entity.packageName) }
                )
            }
        }

        if (uiState.reviewedOrRemovedApps.isNotEmpty()) {
            item(key = "reviewed_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.section_previously_reviewed),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = onClearHistory,
                        modifier = Modifier.testTag("clear_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.action_clear_history),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            items(
                items = uiState.reviewedOrRemovedApps,
                key = { "reviewed_${it.entity.packageName}" }
            ) { item ->
                DetectedNewAppCard(
                    item = item,
                    onOpenDetail = if (item.entity.isStillInstalled) {
                        { onOpenAppDetail(item.entity.packageName) }
                    } else {
                        null
                    },
                    onMarkReviewed = null,
                    onOpenAppInfo = if (item.entity.isStillInstalled) {
                        { onOpenAppInfo(item.entity.packageName) }
                    } else {
                        null
                    }
                )
            }
        }

        item(key = "bottom_pad") {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun EmptyNewAppsCard(
    isBaseline: Boolean,
    totalAppsCount: Int,
    hasSearchQuery: Boolean
) {
    val semantic = PGuardTheme.semanticColors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("empty_new_apps_card"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(semantic.statusClearContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.VerifiedUser,
                    contentDescription = null,
                    tint = semantic.statusClear,
                    modifier = Modifier.size(28.dp)
                )
            }

            Text(
                text = if (hasSearchQuery) {
                    stringResource(R.string.empty_search_results)
                } else {
                    stringResource(R.string.empty_new_apps_title)
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (!hasSearchQuery) {
                Text(
                    text = if (isBaseline) {
                        stringResource(R.string.empty_new_apps_baseline_body, totalAppsCount)
                    } else {
                        stringResource(R.string.empty_new_apps_monitored_body, totalAppsCount)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DetectedNewAppCard(
    item: DetectedAppDisplayItem,
    onOpenDetail: (() -> Unit)?,
    onMarkReviewed: (() -> Unit)?,
    onOpenAppInfo: (() -> Unit)?
) {
    val app = item.entity
    val semantic = PGuardTheme.semanticColors
    val needsAttention = !app.isReviewed && app.isStillInstalled

    Surface(
        onClick = { onOpenDetail?.invoke() },
        enabled = onOpenDetail != null,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("detected_app_${app.packageName}"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (needsAttention) semantic.statusAttentionBorder else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppPackageIcon(
                    packageName = app.packageName,
                    appName = app.appName,
                    size = 44.dp
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = app.appName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        val assessment = item.assessment
                        if (assessment != null && assessment.isSuspicious) {
                            RiskLevelBadge(
                                riskLevel = assessment.riskLevel,
                                isTrusted = assessment.isTrusted
                            )
                        } else {
                            StatusPill(
                                text = when {
                                    !app.isStillInstalled -> stringResource(R.string.badge_uninstalled)
                                    app.isReviewed -> stringResource(R.string.badge_reviewed)
                                    app.isSystemApp -> stringResource(R.string.badge_system_app)
                                    else -> stringResource(R.string.badge_user_app)
                                },
                                isWarning = needsAttention
                            )
                        }
                    }

                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val sourceLabel = formatInstallerSource(app.installerPackageName, app.isSystemApp)
                Text(
                    text = "${stringResource(R.string.meta_source_label, sourceLabel)} · ${
                        stringResource(R.string.meta_version_label, app.versionName)
                    }",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${
                        stringResource(
                            R.string.meta_installed_on,
                            formatScanDateTime(app.firstInstallTimeMillis)
                        )
                    } · ${
                        stringResource(
                            R.string.meta_detected_on,
                            formatShortDate(app.detectedAtMillis)
                        )
                    }",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (onOpenAppInfo != null || onMarkReviewed != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (onOpenAppInfo != null) {
                        OutlinedButton(
                            onClick = onOpenAppInfo,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("app_info_${app.packageName}")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.action_app_info),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    if (onMarkReviewed != null) {
                        FilledTonalButton(
                            onClick = onMarkReviewed,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("mark_reviewed_${app.packageName}")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.action_mark_reviewed),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SnapshotTabContent(
    uiState: NewAppsUiState,
    onFilterChange: (SnapshotFilter) -> Unit,
    onOpenAppDetail: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = uiState.snapshotFilter == SnapshotFilter.ALL,
                onClick = { onFilterChange(SnapshotFilter.ALL) },
                label = { Text(stringResource(R.string.filter_all_apps)) },
                modifier = Modifier.testTag("filter_all")
            )
            FilterChip(
                selected = uiState.snapshotFilter == SnapshotFilter.USER_APPS,
                onClick = { onFilterChange(SnapshotFilter.USER_APPS) },
                label = { Text(stringResource(R.string.filter_user_apps)) },
                modifier = Modifier.testTag("filter_user")
            )
            FilterChip(
                selected = uiState.snapshotFilter == SnapshotFilter.SYSTEM_APPS,
                onClick = { onFilterChange(SnapshotFilter.SYSTEM_APPS) },
                label = { Text(stringResource(R.string.filter_system_apps)) },
                modifier = Modifier.testTag("filter_system")
            )
        }

        if (uiState.filteredSnapshotAssessments.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.empty_search_results),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("snapshot_packages_list"),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = uiState.filteredSnapshotAssessments,
                    key = { it.snapshot.packageName }
                ) { assessment ->
                    SnapshotPackageRow(
                        assessment = assessment,
                        onClick = { onOpenAppDetail(assessment.snapshot.packageName) }
                    )
                }

                item(key = "snapshot_bottom_pad") {
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
private fun SnapshotPackageRow(
    assessment: AppSecurityAssessment,
    onClick: () -> Unit
) {
    val pkg = assessment.snapshot
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppPackageIcon(
                packageName = pkg.packageName,
                appName = pkg.appName,
                size = 40.dp
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = pkg.appName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (assessment.isTrusted) {
                        RiskLevelBadge(riskLevel = assessment.riskLevel, isTrusted = true)
                    } else if (assessment.isSuspicious) {
                        RiskLevelBadge(riskLevel = assessment.riskLevel)
                    } else {
                        StatusPill(
                            text = if (pkg.isSystemApp) {
                                stringResource(R.string.badge_system_app)
                            } else {
                                stringResource(R.string.badge_user_app)
                            },
                            isWarning = false
                        )
                    }
                }

                Text(
                    text = pkg.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val source = formatInstallerSource(pkg.installerPackageName, pkg.isSystemApp)
                Text(
                    text = "$source · ${stringResource(R.string.meta_version_label, pkg.versionName)} · ${
                        formatShortDate(pkg.firstInstallTimeMillis)
                    }",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun StatusPill(
    text: String,
    isWarning: Boolean
) {
    val semantic = PGuardTheme.semanticColors
    val bg = if (isWarning) semantic.statusAttentionContainer else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (isWarning) semantic.statusAttention else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = bg
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}
