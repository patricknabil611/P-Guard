package com.example.ui.score

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.scanner.ChecklistStatus
import com.example.data.scanner.RootCheckTrigger
import com.example.data.scanner.RootDetectionResult
import com.example.data.scanner.SecurityCheckFixAction
import com.example.data.scanner.SecurityChecklistId
import com.example.data.scanner.SecurityChecklistItem
import com.example.ui.common.openDeviceInfoSettings
import com.example.ui.common.openSecuritySettings
import com.example.ui.common.openUnknownAppSourcesSettings
import com.example.ui.theme.PGuardTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityScoreScreen(
    viewModel: SecurityScoreViewModel,
    onNavigateBack: () -> Unit,
    onOpenDeveloperMode: () -> Unit,
    onOpenSuspiciousApps: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refresh()
    }

    val handleFix: (SecurityCheckFixAction) -> Unit = { action ->
        when (action) {
            SecurityCheckFixAction.OPEN_SECURITY_SETTINGS -> openSecuritySettings(context)
            SecurityCheckFixAction.OPEN_DEVICE_INFO_SETTINGS -> openDeviceInfoSettings(context)
            SecurityCheckFixAction.OPEN_UNKNOWN_APP_SOURCES -> openUnknownAppSourcesSettings(context)
            SecurityCheckFixAction.OPEN_DEVELOPER_MODE_SCREEN -> onOpenDeveloperMode()
            SecurityCheckFixAction.OPEN_SUSPICIOUS_APPS_SCREEN -> onOpenSuspiciousApps()
            SecurityCheckFixAction.NONE -> Unit
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.security_score_screen_title),
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
                        modifier = Modifier.testTag("security_score_refresh_button")
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp)
                    .testTag("security_score_list"),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "score_hero") {
                    ScoreHeroCard(score = uiState.report.score)
                }

                item(key = "root_header") {
                    Text(
                        text = stringResource(R.string.section_root_status),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }

                item(key = "root_card") {
                    RootDetectionCard(rootResult = uiState.report.rootResult)
                }

                item(key = "checklist_header") {
                    Text(
                        text = stringResource(R.string.section_security_checklist),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }

                items(
                    items = uiState.report.items,
                    key = { it.id.name }
                ) { item ->
                    ChecklistItemCard(
                        item = item,
                        onFixClick = { handleFix(item.fixAction) }
                    )
                }

                item(key = "bottom_spacer") {
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
private fun ScoreHeroCard(score: Int) {
    val semantic = PGuardTheme.semanticColors
    val ringColor: Color
    val trackColor: Color
    val headline: String

    when {
        score >= 80 -> {
            ringColor = semantic.statusClear
            trackColor = semantic.statusClearContainer
            headline = stringResource(R.string.security_score_good)
        }
        score >= 50 -> {
            ringColor = semantic.statusAttention
            trackColor = semantic.statusAttentionContainer
            headline = stringResource(R.string.security_score_fair)
        }
        else -> {
            ringColor = semantic.statusDanger
            trackColor = semantic.statusDangerContainer
            headline = stringResource(R.string.security_score_poor)
        }
    }

    val ringDescription = stringResource(R.string.cd_security_score_ring, score)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("security_score_hero_card"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 26.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(144.dp)
                    .semantics { contentDescription = ringDescription },
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { (score / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxSize(),
                    color = ringColor,
                    trackColor = trackColor,
                    strokeWidth = 11.dp,
                    strokeCap = StrokeCap.Round
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = score.toString(),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.security_score_out_of),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = headline,
                style = MaterialTheme.typography.titleMedium,
                color = ringColor
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RootDetectionCard(rootResult: RootDetectionResult) {
    val semantic = PGuardTheme.semanticColors
    val isRooted = rootResult.isRooted

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("root_detection_card"),
        shape = MaterialTheme.shapes.large,
        color = if (isRooted) semantic.statusDangerContainer.copy(alpha = 0.7f) else semantic.statusClearContainer.copy(alpha = 0.65f),
        border = BorderStroke(
            width = 1.dp,
            color = if (isRooted) semantic.statusDangerBorder else semantic.statusClearBorder
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isRooted) semantic.statusDanger.copy(alpha = 0.14f)
                            else semantic.statusClear.copy(alpha = 0.14f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isRooted) Icons.Outlined.ErrorOutline else Icons.Outlined.VerifiedUser,
                        contentDescription = null,
                        tint = if (isRooted) semantic.statusDanger else semantic.statusClear,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(
                            if (isRooted) R.string.root_status_detected else R.string.root_status_not_rooted
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isRooted) semantic.onStatusDangerContainer else semantic.onStatusClearContainer
                    )
                    Text(
                        text = stringResource(
                            if (isRooted) R.string.root_status_detected_body else R.string.root_status_not_rooted_body
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isRooted) {
                            semantic.onStatusDangerContainer.copy(alpha = 0.85f)
                        } else {
                            semantic.onStatusClearContainer.copy(alpha = 0.85f)
                        }
                    )
                }
            }

            if (isRooted && rootResult.triggeredChecks.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    rootResult.triggeredChecks.forEach { trigger ->
                        val label = when (trigger) {
                            RootCheckTrigger.SU_BINARY_FOUND -> stringResource(R.string.root_trigger_su_binary)
                            RootCheckTrigger.ROOT_MANAGER_APP_INSTALLED -> stringResource(R.string.root_trigger_root_manager)
                            RootCheckTrigger.TEST_KEYS_BUILD_TAGS -> stringResource(R.string.root_trigger_test_keys)
                            RootCheckTrigger.WRITABLE_SYSTEM_PARTITION -> stringResource(R.string.root_trigger_writable_system)
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = semantic.statusDangerContainer,
                            border = BorderStroke(1.dp, semantic.statusDangerBorder)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                color = semantic.onStatusDangerContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChecklistItemCard(
    item: SecurityChecklistItem,
    onFixClick: () -> Unit
) {
    val semantic = PGuardTheme.semanticColors
    val (icon, iconTint, borderColor) = when (item.status) {
        ChecklistStatus.PASS -> Triple(
            Icons.Outlined.CheckCircle,
            semantic.statusClear,
            MaterialTheme.colorScheme.outlineVariant
        )
        ChecklistStatus.WARNING -> Triple(
            Icons.Outlined.WarningAmber,
            semantic.statusAttention,
            semantic.statusAttentionBorder
        )
        ChecklistStatus.FAIL -> Triple(
            Icons.Outlined.Cancel,
            semantic.statusDanger,
            semantic.statusDangerBorder
        )
        ChecklistStatus.INFO -> Triple(
            Icons.Outlined.Info,
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.outlineVariant
        )
    }

    val title = checklistTitle(item.id)
    val explanation = checklistExplanation(item)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("checklist_${item.id.name.lowercase()}"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = explanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (item.fixAction != SecurityCheckFixAction.NONE) {
                FilledTonalButton(
                    onClick = onFixClick,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("fix_${item.id.name.lowercase()}")
                ) {
                    Text(
                        text = stringResource(R.string.action_fix),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            } else if (item.status == ChecklistStatus.INFO) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = stringResource(R.string.badge_info),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun checklistTitle(id: SecurityChecklistId): String = when (id) {
    SecurityChecklistId.SCREEN_LOCK -> stringResource(R.string.check_screen_lock_title)
    SecurityChecklistId.SECURITY_PATCH -> stringResource(R.string.check_security_patch_title)
    SecurityChecklistId.UNKNOWN_APP_INSTALLERS -> stringResource(R.string.check_unknown_installers_title)
    SecurityChecklistId.USER_CA_CERTS -> stringResource(R.string.check_user_ca_title)
    SecurityChecklistId.VPN_ACTIVE -> stringResource(R.string.check_vpn_title)
    SecurityChecklistId.DEVELOPER_MODE -> stringResource(R.string.check_dev_mode_title)
    SecurityChecklistId.ROOT_STATUS -> stringResource(R.string.check_root_title)
    SecurityChecklistId.SUSPICIOUS_APPS -> stringResource(R.string.check_suspicious_title)
    SecurityChecklistId.SIGNATURE_CHANGES -> stringResource(R.string.check_signature_title)
}

@Composable
private fun checklistExplanation(item: SecurityChecklistItem): String = when (item.id) {
    SecurityChecklistId.SCREEN_LOCK -> if (item.status == ChecklistStatus.PASS) {
        stringResource(R.string.check_screen_lock_pass)
    } else {
        stringResource(R.string.check_screen_lock_fail)
    }

    SecurityChecklistId.SECURITY_PATCH -> {
        if (item.detailValue.isBlank()) {
            stringResource(R.string.check_security_patch_unknown)
        } else {
            when (item.status) {
                ChecklistStatus.PASS -> stringResource(R.string.check_security_patch_pass, item.detailValue)
                ChecklistStatus.WARNING -> stringResource(R.string.check_security_patch_warn, item.detailValue)
                else -> stringResource(R.string.check_security_patch_fail, item.detailValue)
            }
        }
    }

    SecurityChecklistId.UNKNOWN_APP_INSTALLERS -> when {
        item.status == ChecklistStatus.PASS -> stringResource(R.string.check_unknown_installers_pass)
        item.count == 1 -> stringResource(R.string.check_unknown_installers_warn_single)
        else -> stringResource(R.string.check_unknown_installers_warn_plural, item.count)
    }

    SecurityChecklistId.USER_CA_CERTS -> when {
        item.status == ChecklistStatus.PASS -> stringResource(R.string.check_user_ca_pass)
        item.count == 1 -> stringResource(R.string.check_user_ca_fail_single)
        else -> stringResource(R.string.check_user_ca_fail_plural, item.count)
    }

    SecurityChecklistId.VPN_ACTIVE -> if (item.count > 0) {
        stringResource(R.string.check_vpn_on)
    } else {
        stringResource(R.string.check_vpn_off)
    }

    SecurityChecklistId.DEVELOPER_MODE -> when (item.status) {
        ChecklistStatus.PASS -> stringResource(R.string.check_dev_mode_pass)
        ChecklistStatus.WARNING -> stringResource(R.string.check_dev_mode_warn)
        else -> stringResource(R.string.check_dev_mode_fail)
    }

    SecurityChecklistId.ROOT_STATUS -> when {
        item.status == ChecklistStatus.PASS -> stringResource(R.string.check_root_pass)
        item.count == 1 -> stringResource(R.string.check_root_fail_single)
        else -> stringResource(R.string.check_root_fail_plural, item.count)
    }

    SecurityChecklistId.SUSPICIOUS_APPS -> when {
        item.status == ChecklistStatus.PASS -> stringResource(R.string.check_suspicious_pass)
        item.count == 1 -> stringResource(R.string.check_suspicious_warn_single)
        else -> stringResource(R.string.check_suspicious_warn_plural, item.count)
    }

    SecurityChecklistId.SIGNATURE_CHANGES -> when {
        item.status == ChecklistStatus.PASS -> stringResource(R.string.check_signature_pass)
        item.count == 1 -> stringResource(R.string.check_signature_fail_single)
        else -> stringResource(R.string.check_signature_fail_plural, item.count)
    }
}
