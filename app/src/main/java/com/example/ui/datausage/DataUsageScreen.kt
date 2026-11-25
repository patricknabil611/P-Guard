package com.example.ui.datausage

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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.scanner.DataUsageWindow
import com.example.ui.common.AppPackageIcon
import com.example.ui.common.RiskLevelBadge
import com.example.ui.common.formatBytes
import com.example.ui.common.openUsageAccessSettings
import com.example.ui.theme.PGuardTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataUsageScreen(
    viewModel: DataUsageViewModel,
    onNavigateBack: () -> Unit,
    onOpenAppDetail: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refresh()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.data_usage_screen_title),
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
                        onClick = viewModel::refresh,
                        enabled = !uiState.isLoading,
                        modifier = Modifier.testTag("data_usage_refresh_button")
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
                    visible = uiState.isLoading,
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

                if (!uiState.hasUsagePermission && !uiState.isLoading) {
                    DataUsagePermissionPrompt(
                        onGrantClick = { openUsageAccessSettings(context) },
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = uiState.selectedWindow == DataUsageWindow.TWENTY_FOUR_HOURS,
                            onClick = { viewModel.onSelectWindow(DataUsageWindow.TWENTY_FOUR_HOURS) },
                            label = { Text(stringResource(R.string.data_window_24h)) },
                            modifier = Modifier.testTag("data_window_24h")
                        )
                        FilterChip(
                            selected = uiState.selectedWindow == DataUsageWindow.SEVEN_DAYS,
                            onClick = { viewModel.onSelectWindow(DataUsageWindow.SEVEN_DAYS) },
                            label = { Text(stringResource(R.string.data_window_7d)) },
                            modifier = Modifier.testTag("data_window_7d")
                        )
                    }

                    DataUsageSearchAndSortBar(
                        searchQuery = uiState.searchQuery,
                        onSearchQueryChange = viewModel::onSearchQueryChange,
                        sortOption = uiState.sortOption,
                        onSortOptionChange = viewModel::onSortOptionChange,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("data_usage_list"),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (uiState.items.isEmpty() && !uiState.isLoading) {
                            item(key = "empty_data_usage") {
                                EmptyDataUsageCard(
                                    hasSearchQuery = uiState.searchQuery.isNotBlank() && uiState.totalItemCount > 0
                                )
                            }
                        } else {
                            items(
                                items = uiState.items,
                                key = { it.usage.packageName }
                            ) { item ->
                                DataUsageAppCard(
                                    item = item,
                                    onClick = { onOpenAppDetail(item.usage.packageName) }
                                )
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
}

@Composable
private fun DataUsageSearchAndSortBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    sortOption: DataUsageSortOption,
    onSortOptionChange: (DataUsageSortOption) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = {
                Text(
                    text = stringResource(R.string.search_packages_placeholder),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            leadingIcon = {
                Icon(imageVector = Icons.Outlined.Search, contentDescription = null)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { onSearchQueryChange("") },
                        modifier = Modifier.testTag("clear_search_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.cd_clear_search)
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("list_search_input")
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.sort_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            DataSortChip(
                label = stringResource(R.string.sort_most_data),
                selected = sortOption == DataUsageSortOption.MOST_DATA,
                onClick = { onSortOptionChange(DataUsageSortOption.MOST_DATA) },
                testTag = "sort_chip_most_data"
            )
            DataSortChip(
                label = stringResource(R.string.sort_name),
                selected = sortOption == DataUsageSortOption.NAME,
                onClick = { onSortOptionChange(DataUsageSortOption.NAME) },
                testTag = "sort_chip_name"
            )
            DataSortChip(
                label = stringResource(R.string.sort_risk),
                selected = sortOption == DataUsageSortOption.RISK,
                onClick = { onSortOptionChange(DataUsageSortOption.RISK) },
                testTag = "sort_chip_risk"
            )
        }
    }
}

@Composable
private fun DataSortChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text = label, style = MaterialTheme.typography.labelMedium) },
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
        modifier = Modifier.testTag(testTag)
    )
}

@Composable
private fun DataUsageAppCard(
    item: DataUsageDisplayItem,
    onClick: () -> Unit
) {
    val semantic = PGuardTheme.semanticColors
    val isFlagged = item.isHighBackgroundFlagged

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("data_usage_${item.usage.packageName}"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (isFlagged) semantic.statusAttentionBorder else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppPackageIcon(
                    packageName = item.usage.packageName,
                    appName = item.displayName,
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
                            text = item.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        )

                        Text(
                            text = formatBytes(item.usage.totalBackgroundBytes),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isFlagged) semantic.statusAttention else MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(
                        text = item.usage.packageName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(
                                R.string.meta_mobile_data,
                                formatBytes(item.usage.mobileBackgroundBytes)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(
                                R.string.meta_wifi_data,
                                formatBytes(item.usage.wifiBackgroundBytes)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    val assessment = item.assessment
                    if (isFlagged || item.isTrusted || (assessment != null && assessment.isSuspicious)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            if (isFlagged) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = semantic.statusAttentionContainer,
                                    border = BorderStroke(1.dp, semantic.statusAttentionBorder)
                                ) {
                                    Text(
                                        text = stringResource(R.string.badge_high_bg_data),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = semantic.onStatusAttentionContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            if (assessment != null && assessment.isTrusted) {
                                RiskLevelBadge(riskLevel = assessment.riskLevel, isTrusted = true)
                            } else if (assessment != null && assessment.isSuspicious) {
                                RiskLevelBadge(riskLevel = assessment.riskLevel)
                            }
                        }
                    }
                }
            }

            if (isFlagged) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = semantic.statusAttentionContainer.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, semantic.statusAttentionBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.WarningAmber,
                            contentDescription = null,
                            tint = semantic.statusAttention,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.data_high_usage_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = semantic.onStatusAttentionContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DataUsagePermissionPrompt(
    onGrantClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("data_usage_permission_card"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.DataUsage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = stringResource(R.string.usage_permission_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.data_usage_permission_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onGrantClick,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("grant_usage_access_button")
            ) {
                Text(
                    text = stringResource(R.string.action_grant_usage_access),
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Outlined.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyDataUsageCard(hasSearchQuery: Boolean) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("empty_data_usage_card"),
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
                    imageVector = Icons.Outlined.DataUsage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = if (hasSearchQuery) {
                    stringResource(R.string.empty_search_results)
                } else {
                    stringResource(R.string.empty_data_usage_title)
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!hasSearchQuery) {
                Text(
                    text = stringResource(R.string.empty_data_usage_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
