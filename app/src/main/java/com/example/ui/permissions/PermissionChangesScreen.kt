package com.example.ui.permissions

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.common.AppPackageIcon
import com.example.ui.common.ListSearchAndSortBar
import com.example.ui.common.RiskLevelBadge
import com.example.ui.common.formatScanDateTime
import com.example.ui.common.formatShortPermissionName
import com.example.ui.theme.PGuardTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionChangesScreen(
    viewModel: PermissionChangesViewModel,
    onNavigateBack: () -> Unit,
    onOpenAppDetail: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.permission_changes_screen_title),
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
                actions = {
                    IconButton(
                        onClick = viewModel::onScanAgain,
                        enabled = !uiState.isScanning,
                        modifier = Modifier.testTag("perm_changes_scan_button")
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
                        .testTag("permission_changes_list"),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val hasAnyResults = uiState.unreviewedItems.isNotEmpty() || uiState.reviewedItems.isNotEmpty()
                    val hasAnyData = uiState.totalUnreviewedCount > 0 || uiState.totalReviewedCount > 0

                    if (!hasAnyResults) {
                        item(key = "empty_perm_changes") {
                            EmptyPermissionChangesCard(
                                hasSearchQuery = uiState.searchQuery.isNotBlank() && hasAnyData
                            )
                        }
                    } else {
                        if (uiState.unreviewedItems.isNotEmpty()) {
                            item(key = "needs_review_header") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = stringResource(R.string.section_needs_review),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    FilledTonalButton(
                                        onClick = viewModel::onMarkAllReviewed,
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.testTag("perm_mark_all_reviewed_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.DoneAll,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(R.string.action_mark_all_reviewed),
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                            }

                            items(
                                items = uiState.unreviewedItems,
                                key = { "unrev_${it.entity.id}" }
                            ) { item ->
                                PermissionChangeCard(
                                    item = item,
                                    onClick = { onOpenAppDetail(item.entity.packageName) },
                                    onMarkReviewed = { viewModel.onMarkReviewed(item.entity.id) }
                                )
                            }
                        }

                        if (uiState.reviewedItems.isNotEmpty()) {
                            item(key = "reviewed_header") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = stringResource(R.string.section_previously_reviewed),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    TextButton(
                                        onClick = viewModel::onClearReviewedHistory,
                                        modifier = Modifier.testTag("perm_clear_history_button")
                                    ) {
                                        Text(
                                            text = stringResource(R.string.action_clear_history),
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                }
                            }

                            items(
                                items = uiState.reviewedItems,
                                key = { "rev_${it.entity.id}" }
                            ) { item ->
                                PermissionChangeCard(
                                    item = item,
                                    onClick = { onOpenAppDetail(item.entity.packageName) },
                                    onMarkReviewed = null
                                )
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PermissionChangeCard(
    item: PermissionChangeDisplayItem,
    onClick: () -> Unit,
    onMarkReviewed: (() -> Unit)?
) {
    val semantic = PGuardTheme.semanticColors
    val borderColor = when {
        !item.entity.isReviewed && item.hasRiskyAddition -> semantic.statusAttentionBorder
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("perm_change_${item.entity.id}"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppPackageIcon(
                    packageName = item.entity.packageName,
                    appName = item.entity.appName,
                    size = 44.dp
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = item.entity.appName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        )
                        val assessment = item.assessment
                        if (assessment != null && assessment.isTrusted) {
                            RiskLevelBadge(riskLevel = assessment.riskLevel, isTrusted = true)
                        } else if (assessment != null && assessment.isSuspicious) {
                            RiskLevelBadge(riskLevel = assessment.riskLevel)
                        }
                    }

                    Text(
                        text = item.entity.packageName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = stringResource(
                            R.string.meta_detected_on,
                            formatScanDateTime(item.entity.detectedAtMillis)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item.addedPermissions.forEach { entry ->
                    val label = formatShortPermissionName(entry.permissionName)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (entry.isRisky) {
                            semantic.statusAttentionContainer.copy(alpha = 0.8f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (entry.isRisky) {
                                semantic.statusAttentionBorder
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            }
                        )
                    ) {
                        Text(
                            text = "+ $label",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (entry.isRisky) {
                                semantic.onStatusAttentionContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (onMarkReviewed != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onMarkReviewed,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("perm_mark_reviewed_${item.entity.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.action_mark_reviewed),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyPermissionChangesCard(hasSearchQuery: Boolean) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("empty_perm_changes_card"),
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
                    .size(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = if (hasSearchQuery) {
                    stringResource(R.string.empty_search_results)
                } else {
                    stringResource(R.string.empty_permission_changes_title)
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!hasSearchQuery) {
                Text(
                    text = stringResource(R.string.empty_permission_changes_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
