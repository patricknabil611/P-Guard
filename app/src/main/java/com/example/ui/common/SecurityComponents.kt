package com.example.ui.common

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.scanner.AppSecurityAssessment
import com.example.data.scanner.RiskLevel
import com.example.data.scanner.RiskyPermission
import com.example.data.scanner.SuspiciousReason
import com.example.ui.theme.PGuardTheme

enum class AppSortOption {
    NAME,
    INSTALL_DATE,
    RISK
}

fun sortAssessments(
    items: List<AppSecurityAssessment>,
    sortOption: AppSortOption
): List<AppSecurityAssessment> = when (sortOption) {
    AppSortOption.NAME -> items.sortedBy { it.snapshot.appName.lowercase() }
    AppSortOption.INSTALL_DATE -> items.sortedWith(
        compareByDescending<AppSecurityAssessment> { it.snapshot.firstInstallTimeMillis }
            .thenBy { it.snapshot.appName.lowercase() }
    )
    AppSortOption.RISK -> items.sortedWith(
        compareByDescending<AppSecurityAssessment> { it.riskLevel.sortOrder }
            .thenByDescending { it.riskScore }
            .thenBy { it.snapshot.appName.lowercase() }
    )
}

@Composable
fun ListSearchAndSortBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    sortOption: AppSortOption,
    onSortOptionChange: (AppSortOption) -> Unit,
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
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null
                )
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

            SortChip(
                label = stringResource(R.string.sort_name),
                selected = sortOption == AppSortOption.NAME,
                onClick = { onSortOptionChange(AppSortOption.NAME) },
                testTag = "sort_chip_name"
            )
            SortChip(
                label = stringResource(R.string.sort_install_date),
                selected = sortOption == AppSortOption.INSTALL_DATE,
                onClick = { onSortOptionChange(AppSortOption.INSTALL_DATE) },
                testTag = "sort_chip_date"
            )
            SortChip(
                label = stringResource(R.string.sort_risk),
                selected = sortOption == AppSortOption.RISK,
                onClick = { onSortOptionChange(AppSortOption.RISK) },
                testTag = "sort_chip_risk"
            )
        }
    }
}

@Composable
private fun SortChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium
            )
        },
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
fun RiskLevelBadge(
    riskLevel: RiskLevel,
    isTrusted: Boolean = false,
    modifier: Modifier = Modifier
) {
    val semantic = PGuardTheme.semanticColors

    if (isTrusted) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Text(
                text = stringResource(R.string.badge_trusted),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
            )
        }
        return
    }

    if (riskLevel == RiskLevel.NONE) return

    val (bg, fg, border, label) = when (riskLevel) {
        RiskLevel.HIGH -> Quad(
            semantic.statusDangerContainer,
            semantic.statusDanger,
            semantic.statusDangerBorder,
            stringResource(R.string.risk_high)
        )
        RiskLevel.MEDIUM -> Quad(
            semantic.statusAttentionContainer,
            semantic.statusAttention,
            semantic.statusAttentionBorder,
            stringResource(R.string.risk_medium)
        )
        RiskLevel.LOW -> Quad(
            semantic.statusAttentionContainer.copy(alpha = 0.6f),
            semantic.onStatusAttentionContainer,
            semantic.statusAttentionBorder.copy(alpha = 0.7f),
            stringResource(R.string.risk_low)
        )
        RiskLevel.NONE -> return
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = bg,
        border = BorderStroke(1.dp, border)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
        )
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SuspiciousReasonChips(
    reasons: List<SuspiciousReason>,
    modifier: Modifier = Modifier
) {
    if (reasons.isEmpty()) return
    val semantic = PGuardTheme.semanticColors

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        reasons.forEach { reason ->
            val (label, isHighSeverity) = when (reason) {
                is SuspiciousReason.Sideloaded -> {
                    val source = formatInstallerSource(reason.installerPackageName, isSystemApp = false)
                    stringResource(R.string.reason_sideloaded_with_source, source) to false
                }
                is SuspiciousReason.RiskyPermissions -> {
                    val count = reason.permissions.size
                    val text = if (count == 1) {
                        stringResource(R.string.reason_risky_permissions_single)
                    } else {
                        stringResource(R.string.reason_risky_permissions_plural, count)
                    }
                    text to (count >= 3)
                }
                SuspiciousReason.HiddenNoLauncher ->
                    stringResource(R.string.reason_hidden_no_launcher) to false
                SuspiciousReason.AccessibilityEnabled ->
                    stringResource(R.string.reason_accessibility_enabled) to true
                SuspiciousReason.DeviceAdminActive ->
                    stringResource(R.string.reason_device_admin_enabled) to true
                SuspiciousReason.NotificationListenerEnabled ->
                    stringResource(R.string.reason_notification_listener_enabled) to true
                is SuspiciousReason.RecentWithManyPermissions ->
                    stringResource(R.string.reason_recent_high_perms, reason.permissionCount) to true
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isHighSeverity) {
                    semantic.statusDangerContainer.copy(alpha = 0.75f)
                } else {
                    semantic.statusAttentionContainer.copy(alpha = 0.75f)
                },
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isHighSeverity) {
                        semantic.statusDangerBorder
                    } else {
                        semantic.statusAttentionBorder
                    }
                )
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isHighSeverity) {
                        semantic.onStatusDangerContainer
                    } else {
                        semantic.onStatusAttentionContainer
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun riskyPermissionLabel(permission: RiskyPermission): String = when (permission) {
    RiskyPermission.CAMERA -> stringResource(R.string.perm_camera)
    RiskyPermission.MICROPHONE -> stringResource(R.string.perm_microphone)
    RiskyPermission.FINE_LOCATION -> stringResource(R.string.perm_fine_location)
    RiskyPermission.SMS -> stringResource(R.string.perm_sms)
    RiskyPermission.CALL_LOG -> stringResource(R.string.perm_call_log)
    RiskyPermission.CONTACTS -> stringResource(R.string.perm_contacts)
    RiskyPermission.PHONE_STATE -> stringResource(R.string.perm_phone_state)
    RiskyPermission.OVERLAY -> stringResource(R.string.perm_overlay)
    RiskyPermission.INSTALL_UNKNOWN_APPS -> stringResource(R.string.perm_install_unknown)
}

fun openAccessibilitySettings(context: Context) {
    launchSettingsIntent(context, Settings.ACTION_ACCESSIBILITY_SETTINGS)
}

fun openDeviceAdminSettings(context: Context) {
    launchSettingsIntent(context, Settings.ACTION_SECURITY_SETTINGS)
}

fun openNotificationListenerSettings(context: Context) {
    launchSettingsIntent(context, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
}

fun openUsageAccessSettings(context: Context) {
    val targeted = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
        data = android.net.Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(targeted)
    } catch (_: Exception) {
        launchSettingsIntent(context, Settings.ACTION_USAGE_ACCESS_SETTINGS)
    }
}

fun openDeveloperOptionsSettings(context: Context) {
    launchSettingsIntent(context, Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
}

fun openDeviceInfoSettings(context: Context) {
    launchSettingsIntent(context, Settings.ACTION_DEVICE_INFO_SETTINGS)
}

private fun launchSettingsIntent(context: Context, action: String) {
    val intent = Intent(action).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        // Fallback to main settings if a specific settings activity is unavailable
        try {
            context.startActivity(
                Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            // Ignore
        }
    }
}
