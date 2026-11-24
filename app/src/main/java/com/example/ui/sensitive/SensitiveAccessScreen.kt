package com.example.ui.sensitive

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.example.ui.common.formatScanDateTime
import com.example.ui.common.openAccessibilitySettings
import com.example.ui.common.openDeviceAdminSettings
import com.example.ui.common.openNotificationListenerSettings
import com.example.ui.theme.PGuardTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensitiveAccessScreen(
    viewModel: SensitiveAccessViewModel,
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
                            text = stringResource(R.string.sensitive_screen_title),
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
                        modifier = Modifier.testTag("sensitive_scan_again_button")
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

                ListSearchAndSortBar(
                    searchQuery = uiState.searchQuery,
                    onSearchQueryChange = viewModel::onSearchQueryChange,
                    sortOption = uiState.sortOption,
                    onSortOptionChange = viewModel::onSortOptionChange,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("sensitive_access_list"),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    sensitiveAccessSection(
                        sectionKey = "accessibility",
                        titleRes = R.string.sensitive_section_accessibility,
                        explanationRes = R.string.sensitive_why_accessibility,
                        icon = Icons.Outlined.AccessibilityNew,
                        apps = uiState.accessibilityApps,
                        hasSearchQuery = uiState.searchQuery.isNotBlank(),
                        onOpenSettings = { openAccessibilitySettings(context) },
                        onOpenAppDetail = onOpenAppDetail
                    )

                    item(key = "spacer_1") { Spacer(modifier = Modifier.height(8.dp)) }

                    sensitiveAccessSection(
                        sectionKey = "device_admin",
                        titleRes = R.string.sensitive_section_device_admin,
                        explanationRes = R.string.sensitive_why_device_admin,
                        icon = Icons.Outlined.AdminPanelSettings,
                        apps = uiState.deviceAdminApps,
                        hasSearchQuery = uiState.searchQuery.isNotBlank(),
                        onOpenSettings = { openDeviceAdminSettings(context) },
                        onOpenAppDetail = onOpenAppDetail
                    )

                    item(key = "spacer_2") { Spacer(modifier = Modifier.height(8.dp)) }

                    sensitiveAccessSection(
                        sectionKey = "notification_listener",
                        titleRes = R.string.sensitive_section_notification_listener,
                        explanationRes = R.string.sensitive_why_notification_listener,
                        icon = Icons.Outlined.NotificationsActive,
                        apps = uiState.notificationListenerApps,
                        hasSearchQuery = uiState.searchQuery.isNotBlank(),
                        onOpenSettings = { openNotificationListenerSettings(context) },
                        onOpenAppDetail = onOpenAppDetail
                    )

                    item(key = "bottom_spacer") {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

private fun LazyListScope.sensitiveAccessSection(
    sectionKey: String,
    titleRes: Int,
    explanationRes: Int,
    icon: ImageVector,
    apps: List<AppSecurityAssessment>,
    hasSearchQuery: Boolean,
    onOpenSettings: () -> Unit,
    onOpenAppDetail: (String) -> Unit
) {
    item(key = "header_$sectionKey") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("section_$sectionKey"),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(titleRes),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                TextButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.testTag("settings_$sectionKey")
                ) {
                    Text(
                        text = stringResource(R.string.action_open_settings),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Outlined.OpenInNew,
                        contentDescription = stringResource(R.string.cd_open_system_settings),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Text(
                text = stringResource(explanationRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    }

    if (apps.isEmpty()) {
        item(key = "empty_$sectionKey") {
            val semantic = PGuardTheme.semanticColors
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = semantic.statusClear,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (hasSearchQuery) {
                            stringResource(R.string.empty_search_results)
                        } else {
                            stringResource(R.string.sensitive_none_enabled)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    } else {
        items(
            items = apps,
            key = { "${sectionKey}_${it.snapshot.packageName}" }
        ) { assessment ->
            SensitiveAccessAppCard(
                assessment = assessment,
                onClick = { onOpenAppDetail(assessment.snapshot.packageName) }
            )
        }
    }
}

@Composable
private fun SensitiveAccessAppCard(
    assessment: AppSecurityAssessment,
    onClick: () -> Unit
) {
    val pkg = assessment.snapshot
    val semantic = PGuardTheme.semanticColors
    val borderColor = if (assessment.isActiveWarning) {
        semantic.statusAttentionBorder
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sensitive_app_${pkg.packageName}"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, borderColor)
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
                    } else if (pkg.isSystemApp) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = stringResource(R.string.badge_system_app),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Text(
                    text = pkg.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
