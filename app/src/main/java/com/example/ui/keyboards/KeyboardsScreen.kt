package com.example.ui.keyboards

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.example.data.scanner.KeyboardPublisher
import com.example.ui.common.AppPackageIcon
import com.example.ui.common.ListSearchAndSortBar
import com.example.ui.common.openInputMethodSettings
import com.example.ui.theme.PGuardTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyboardsScreen(
    viewModel: KeyboardsViewModel,
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
                        text = stringResource(R.string.keyboards_screen_title),
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
                        modifier = Modifier.testTag("keyboards_refresh_button")
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
                        .testTag("keyboards_list"),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(key = "open_settings_button") {
                        Button(
                            onClick = { openInputMethodSettings(context) },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("open_keyboard_settings_button")
                        ) {
                            Text(
                                text = stringResource(R.string.action_open_keyboard_settings),
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

                    if (uiState.keyboards.isEmpty()) {
                        item(key = "empty_keyboards") {
                            EmptyKeyboardsCard(
                                hasSearchQuery = uiState.searchQuery.isNotBlank() && uiState.totalCount > 0
                            )
                        }
                    } else {
                        items(
                            items = uiState.keyboards,
                            key = { it.keyboard.id }
                        ) { item ->
                            KeyboardCard(
                                item = item,
                                onClick = { onOpenAppDetail(item.keyboard.packageName) }
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

@Composable
private fun KeyboardCard(
    item: KeyboardDisplayItem,
    onClick: () -> Unit
) {
    val semantic = PGuardTheme.semanticColors
    val kb = item.keyboard
    val isThirdParty = !kb.isKnownPublisher

    val publisherLabel = when (kb.publisher) {
        KeyboardPublisher.GOOGLE -> stringResource(R.string.keyboard_publisher_google)
        KeyboardPublisher.SAMSUNG -> stringResource(R.string.keyboard_publisher_samsung)
        KeyboardPublisher.MICROSOFT -> stringResource(R.string.keyboard_publisher_microsoft)
        KeyboardPublisher.THIRD_PARTY -> stringResource(R.string.keyboard_publisher_third_party)
    }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("keyboard_${kb.packageName}"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (isThirdParty) semantic.statusAttentionBorder else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppPackageIcon(
                    packageName = kb.packageName,
                    appName = kb.displayName,
                    size = 44.dp
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = kb.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = kb.packageName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (kb.isDefault) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = stringResource(R.string.badge_default_keyboard),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (isThirdParty) {
                                semantic.statusAttentionContainer
                            } else {
                                semantic.statusClearContainer
                            },
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isThirdParty) {
                                    semantic.statusAttentionBorder
                                } else {
                                    semantic.statusClearBorder
                                }
                            )
                        ) {
                            Text(
                                text = publisherLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isThirdParty) {
                                    semantic.onStatusAttentionContainer
                                } else {
                                    semantic.onStatusClearContainer
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            if (isThirdParty) {
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.WarningAmber,
                            contentDescription = null,
                            tint = semantic.statusAttention,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.keyboard_third_party_warning),
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
private fun EmptyKeyboardsCard(hasSearchQuery: Boolean) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("empty_keyboards_card"),
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
                    imageVector = Icons.Outlined.Keyboard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = if (hasSearchQuery) {
                    stringResource(R.string.empty_search_results)
                } else {
                    stringResource(R.string.empty_keyboards_title)
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!hasSearchQuery) {
                Text(
                    text = stringResource(R.string.empty_keyboards_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
