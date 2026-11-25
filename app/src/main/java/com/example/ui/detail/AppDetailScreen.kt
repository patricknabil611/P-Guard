package com.example.ui.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.RemoveModerator
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.scanner.AppBackgroundDataUsage
import com.example.data.scanner.AppSecurityAssessment
import com.example.data.scanner.NetworkUsageScanner
import com.example.security.BiometricAuthHelper
import com.example.ui.common.AppPackageIcon
import com.example.ui.common.RiskLevelBadge
import com.example.ui.common.SuspiciousReasonChips
import com.example.ui.common.formatBytes
import com.example.ui.common.formatInstallerSource
import com.example.ui.common.formatScanDateTime
import com.example.ui.common.formatShortCertFingerprint
import com.example.ui.common.openAppInfoSettings
import com.example.ui.common.riskyPermissionLabel
import com.example.ui.theme.PGuardTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppDetailScreen(
    viewModel: AppDetailViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val trustTitle = stringResource(R.string.biometric_trust_app_title)
    val biometricUnavailableMsg = stringResource(R.string.biometric_unavailable_message)
    val biometricFailedMsg = stringResource(R.string.biometric_auth_failed)

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshUsageInfo()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_detail_screen_title),
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
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                uiState.assessment == null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.detail_app_not_found),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                else -> {
                    val assessment = uiState.assessment!!
                    AppDetailContent(
                        assessment = assessment,
                        hasUsageAccess = uiState.hasUsageAccess,
                        lastUsedMillis = uiState.lastUsedMillis,
                        backgroundData24h = uiState.backgroundData24h,
                        onToggleTrusted = {
                            val isCurrentlyTrusted = assessment.isTrusted && !assessment.hasSignatureChanged
                            if (isCurrentlyTrusted) {
                                viewModel.onToggleTrusted()
                            } else {
                                val activity = BiometricAuthHelper.findFragmentActivity(context)
                                if (activity == null || !BiometricAuthHelper.canAuthenticate(context)) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(biometricUnavailableMsg)
                                    }
                                } else {
                                    BiometricAuthHelper.authenticate(
                                        activity = activity,
                                        title = trustTitle,
                                        subtitle = context.getString(
                                            R.string.biometric_trust_app_subtitle,
                                            assessment.snapshot.appName
                                        ),
                                        onSuccess = viewModel::onToggleTrusted,
                                        onFailure = {
                                            scope.launch {
                                                snackbarHostState.showSnackbar(biometricFailedMsg)
                                            }
                                        }
                                    )
                                }
                            }
                        },
                        onOpenAppInfo = {
                            openAppInfoSettings(context, assessment.snapshot.packageName)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppDetailContent(
    assessment: AppSecurityAssessment,
    hasUsageAccess: Boolean,
    lastUsedMillis: Long?,
    backgroundData24h: AppBackgroundDataUsage?,
    onToggleTrusted: () -> Unit,
    onOpenAppInfo: () -> Unit
) {
    val pkg = assessment.snapshot
    val semantic = PGuardTheme.semanticColors
    val context = LocalContext.current
    var copiedFingerprint by remember(pkg.signingCertSha256) { mutableStateOf(false) }

    LaunchedEffect(copiedFingerprint) {
        if (copiedFingerprint) {
            delay(2_000L)
            copiedFingerprint = false
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 640.dp)
            .testTag("app_detail_list"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header card
        item(key = "header") {
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
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        AppPackageIcon(
                            packageName = pkg.packageName,
                            appName = pkg.appName,
                            size = 56.dp
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = pkg.appName,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = pkg.packageName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = if (pkg.isSystemApp) {
                                            stringResource(R.string.badge_system_app)
                                        } else {
                                            stringResource(R.string.badge_user_app)
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                                if (assessment.isTrusted) {
                                    RiskLevelBadge(riskLevel = assessment.riskLevel, isTrusted = true)
                                } else if (assessment.isSuspicious) {
                                    RiskLevelBadge(riskLevel = assessment.riskLevel)
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenAppInfo,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("detail_app_info_button")
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

                        FilledTonalButton(
                            onClick = onToggleTrusted,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("detail_trust_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (assessment.isTrusted) {
                                    Icons.Outlined.RemoveModerator
                                } else {
                                    Icons.Outlined.VerifiedUser
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (assessment.isTrusted) {
                                    stringResource(R.string.action_remove_trusted)
                                } else {
                                    stringResource(R.string.action_mark_trusted)
                                },
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }

        if (assessment.hasSignatureChanged) {
            item(key = "signature_changed_banner") {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("detail_signature_changed_banner"),
                    shape = MaterialTheme.shapes.medium,
                    color = semantic.statusDangerContainer,
                    border = BorderStroke(1.dp, semantic.statusDangerBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            tint = semantic.statusDanger,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.detail_signature_changed_banner),
                            style = MaterialTheme.typography.bodyMedium,
                            color = semantic.onStatusDangerContainer
                        )
                    }
                }
            }
        } else if (assessment.isTrusted) {
            item(key = "trusted_banner") {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("detail_trusted_banner"),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.VerifiedUser,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.detail_trusted_banner),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        // Flagged reasons
        if (assessment.reasons.isNotEmpty()) {
            item(key = "reasons") {
                DetailSectionCard(title = stringResource(R.string.detail_section_risk_reasons)) {
                    SuspiciousReasonChips(reasons = assessment.reasons)
                }
            }
        }

        // Special access section (Accessibility, Device Admin, Notification Listener, Overlay)
        item(key = "special_access") {
            DetailSectionCard(
                title = stringResource(R.string.detail_section_special_access),
                modifier = Modifier.testTag("detail_special_access_section")
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SpecialAccessRow(
                        icon = Icons.Outlined.AccessibilityNew,
                        label = stringResource(R.string.detail_special_accessibility),
                        isActive = pkg.hasAccessibilityAccess,
                        activeText = stringResource(R.string.detail_status_enabled)
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SpecialAccessRow(
                        icon = Icons.Outlined.AdminPanelSettings,
                        label = stringResource(R.string.detail_special_device_admin),
                        isActive = pkg.isDeviceAdmin,
                        activeText = stringResource(R.string.detail_status_enabled)
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SpecialAccessRow(
                        icon = Icons.Outlined.NotificationsActive,
                        label = stringResource(R.string.detail_special_notification_listener),
                        isActive = pkg.hasNotificationListener,
                        activeText = stringResource(R.string.detail_status_enabled)
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SpecialAccessRow(
                        icon = Icons.Outlined.Layers,
                        label = stringResource(R.string.detail_special_overlay),
                        isActive = pkg.hasOverlayPermission,
                        activeText = stringResource(R.string.detail_status_requested)
                    )
                }
            }
        }

        // Risky permissions section
        item(key = "risky_permissions") {
            DetailSectionCard(
                title = stringResource(R.string.detail_section_risky_permissions),
                modifier = Modifier.testTag("detail_risky_permissions_section")
            ) {
                if (assessment.riskyPermissions.isEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = semantic.statusClear,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.detail_no_risky_permissions),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        assessment.riskyPermissions.forEach { perm ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = semantic.statusAttentionContainer.copy(alpha = 0.7f),
                                border = BorderStroke(1.dp, semantic.statusAttentionBorder)
                            ) {
                                Text(
                                    text = riskyPermissionLabel(perm),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = semantic.onStatusAttentionContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Package info section
        item(key = "package_info") {
            val source = formatInstallerSource(pkg.installerPackageName, pkg.isSystemApp)
            DetailSectionCard(title = stringResource(R.string.detail_section_package_info)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    InfoRow(
                        label = stringResource(R.string.detail_label_installer),
                        value = source
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    InfoRow(
                        label = stringResource(R.string.detail_label_installed),
                        value = formatScanDateTime(pkg.firstInstallTimeMillis)
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    InfoRow(
                        label = stringResource(R.string.detail_label_updated),
                        value = formatScanDateTime(pkg.lastUpdateTimeMillis)
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    InfoRow(
                        label = stringResource(R.string.detail_label_version),
                        value = "${pkg.versionName} (${pkg.versionCode})"
                    )
                    if (hasUsageAccess) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        InfoRow(
                            label = stringResource(R.string.detail_label_last_used),
                            value = if (lastUsedMillis != null && lastUsedMillis > 0L) {
                                formatScanDateTime(lastUsedMillis)
                            } else {
                                stringResource(R.string.detail_last_used_unknown)
                            }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        val bgBytes = backgroundData24h?.totalBackgroundBytes ?: 0L
                        val isHighBg = !assessment.isTrusted &&
                            bgBytes > NetworkUsageScanner.HIGH_BACKGROUND_DATA_24H_BYTES
                        val bgValue = if (backgroundData24h != null && bgBytes > 0L) {
                            stringResource(
                                R.string.detail_bg_data_split,
                                formatBytes(bgBytes),
                                formatBytes(backgroundData24h.mobileBackgroundBytes),
                                formatBytes(backgroundData24h.wifiBackgroundBytes)
                            )
                        } else {
                            stringResource(R.string.detail_bg_data_none)
                        }
                        InfoRow(
                            label = stringResource(R.string.detail_label_bg_data_24h),
                            value = bgValue,
                            valueColor = if (isHighBg) semantic.statusAttention else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    InfoRow(
                        label = stringResource(R.string.detail_label_launcher),
                        value = if (pkg.hasLauncherIcon) {
                            stringResource(R.string.detail_launcher_visible)
                        } else {
                            stringResource(R.string.detail_launcher_hidden)
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SigningCertificateRow(
                        fullFingerprint = pkg.signingCertSha256,
                        copied = copiedFingerprint,
                        onCopy = {
                            if (pkg.signingCertSha256.isNotBlank()) {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                cm?.setPrimaryClip(
                                    ClipData.newPlainText(
                                        "SHA-256 Certificate",
                                        pkg.signingCertSha256
                                    )
                                )
                                copiedFingerprint = true
                            }
                        }
                    )
                }
            }
        }

        item(key = "bottom_spacer") {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun DetailSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
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
private fun SpecialAccessRow(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    activeText: String
) {
    val semantic = PGuardTheme.semanticColors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isActive) semantic.statusAttention else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Surface(
            shape = RoundedCornerShape(999.dp),
            color = if (isActive) {
                semantic.statusDangerContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ) {
            Text(
                text = if (isActive) activeText else stringResource(R.string.detail_status_not_enabled),
                style = MaterialTheme.typography.labelMedium,
                color = if (isActive) {
                    semantic.statusDanger
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 12.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SigningCertificateRow(
    fullFingerprint: String,
    copied: Boolean,
    onCopy: () -> Unit
) {
    val hasCert = fullFingerprint.isNotBlank()
    val shortFingerprint = if (hasCert) {
        formatShortCertFingerprint(fullFingerprint)
    } else {
        stringResource(R.string.detail_signing_cert_unavailable)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (hasCert) {
                    Modifier.clickable(onClick = onCopy)
                } else {
                    Modifier
                }
            )
            .padding(vertical = 4.dp)
            .testTag("detail_signing_cert_row"),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.detail_label_signing_cert),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (hasCert) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                        contentDescription = stringResource(R.string.cd_copy_fingerprint),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = stringResource(
                            if (copied) R.string.detail_copied else R.string.detail_tap_to_copy
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        Text(
            text = shortFingerprint,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
