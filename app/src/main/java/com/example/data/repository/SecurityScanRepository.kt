package com.example.data.repository

import com.example.data.local.dao.ScanDao
import com.example.data.local.entity.DetectedNewAppEntity
import com.example.data.local.entity.HistoryAppEntry
import com.example.data.local.entity.HistoryChecklistEntry
import com.example.data.local.entity.HistoryPermissionChangeEntry
import com.example.data.local.entity.HistorySuspiciousEntry
import com.example.data.local.entity.PackageSnapshotEntity
import com.example.data.local.entity.PermissionChangeEntity
import com.example.data.local.entity.ScanHistoryDetails
import com.example.data.local.entity.ScanHistoryEntity
import com.example.data.local.entity.ScanRecordEntity
import com.example.data.local.entity.SignatureChangeEntity
import com.example.data.local.entity.TrustedAppEntity
import com.example.data.scanner.AppSecurityAssessment
import com.example.data.scanner.ChecklistStatus
import com.example.data.scanner.DeveloperModeStatus
import com.example.data.scanner.DevicePostureSnapshot
import com.example.data.scanner.PackageScanner
import com.example.data.scanner.RiskyPermission
import com.example.data.scanner.RootCheckTrigger
import com.example.data.scanner.SecurityChecklistId
import com.example.data.scanner.SecurityChecklistItem
import com.example.data.scanner.SecurityEvaluator
import com.example.data.scanner.SecurityScoreEvaluator
import com.example.data.scanner.SuspiciousReason
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface ScanExecutionState {
    data object Idle : ScanExecutionState
    data object Scanning : ScanExecutionState
    data class Error(val message: String) : ScanExecutionState
}

data class ScanRunResult(
    val succeeded: Boolean,
    val historyEntryId: Long = 0L,
    val securityScore: Int = 100,
    val newAppNames: List<String> = emptyList(),
    val signatureChangedAppNames: List<String> = emptyList(),
    val newlySuspiciousAppNames: List<String> = emptyList(),
    val riskyPermissionChangedAppNames: List<String> = emptyList(),
    val developerOptionsTurnedOn: Boolean = false,
    val usbDebuggingTurnedOn: Boolean = false
)

class SecurityScanRepository(
    private val scanDao: ScanDao,
    private val packageScanner: PackageScanner,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val scanMutex = Mutex()

    private val _executionState = MutableStateFlow<ScanExecutionState>(ScanExecutionState.Idle)
    val executionState: StateFlow<ScanExecutionState> = _executionState.asStateFlow()

    val ownPackageName: String
        get() = packageScanner.ownPackageName

    val latestScan: Flow<ScanRecordEntity?> = scanDao.observeLatestScan()
    val scanCount: Flow<Int> = scanDao.observeScanCount()
    val unreviewedNewApps: Flow<List<DetectedNewAppEntity>> = scanDao.observeUnreviewedNewApps()
    val reviewedOrRemovedApps: Flow<List<DetectedNewAppEntity>> = scanDao.observeReviewedOrRemovedApps()
    val latestSnapshotPackages: Flow<List<PackageSnapshotEntity>> = scanDao.observeLatestSnapshotPackages()
    val trustedApps: Flow<List<TrustedAppEntity>> = scanDao.observeTrustedApps()
    val activeSignatureChanges: Flow<List<SignatureChangeEntity>> = scanDao.observeActiveSignatureChanges()
    val unreviewedPermissionChanges: Flow<List<PermissionChangeEntity>> = scanDao.observeUnreviewedPermissionChanges()
    val reviewedPermissionChanges: Flow<List<PermissionChangeEntity>> = scanDao.observeReviewedPermissionChanges()
    val scanHistory: Flow<List<ScanHistoryEntity>> = scanDao.observeScanHistory()
    val scanHistoryCount: Flow<Int> = scanDao.observeScanHistoryCount()

    fun observeScanHistorySince(sinceMillis: Long): Flow<List<ScanHistoryEntity>> =
        scanDao.observeScanHistorySince(sinceMillis)

    fun observeScanHistoryById(id: Long): Flow<ScanHistoryEntity?> =
        scanDao.observeScanHistoryById(id)

    suspend fun getScanHistoryByIdOnce(id: Long): ScanHistoryEntity? =
        withContext(ioDispatcher) { scanDao.getScanHistoryByIdOnce(id) }

    suspend fun getLatestScanHistoryOnce(): ScanHistoryEntity? =
        withContext(ioDispatcher) { scanDao.getLatestScanHistoryOnce() }

    val securityAssessments: Flow<List<AppSecurityAssessment>> = combine(
        latestSnapshotPackages,
        trustedApps,
        activeSignatureChanges
    ) { snapshots, trusted, sigChanges ->
        val trustedSet = trusted.map { it.packageName }.toSet()
        val sigChangedSet = sigChanges.map { it.packageName }.toSet()
        val now = System.currentTimeMillis()
        snapshots.map { snapshot ->
            SecurityEvaluator.evaluate(
                snapshot = snapshot,
                trustedPackages = trustedSet,
                ownPackageName = packageScanner.ownPackageName,
                signatureChangedPackages = sigChangedSet,
                nowMillis = now
            )
        }
    }

    fun observeAssessmentForPackage(packageName: String): Flow<AppSecurityAssessment?> {
        return combine(
            scanDao.observeLatestPackageByName(packageName),
            trustedApps,
            activeSignatureChanges
        ) { snapshot, trusted, sigChanges ->
            if (snapshot == null) return@combine null
            val trustedSet = trusted.map { it.packageName }.toSet()
            val sigChangedSet = sigChanges.map { it.packageName }.toSet()
            SecurityEvaluator.evaluate(
                snapshot = snapshot,
                trustedPackages = trustedSet,
                ownPackageName = packageScanner.ownPackageName,
                signatureChangedPackages = sigChangedSet,
                nowMillis = System.currentTimeMillis()
            )
        }
    }

    @Volatile
    private var lastCompletedScanAtMillis: Long = 0L

    /**
     * Convenience wrapper for callers that only need a boolean success result.
     */
    suspend fun runScan(
        force: Boolean = true,
        minIntervalMillis: Long = 2_000L,
        isBackground: Boolean = false,
        devicePosture: DevicePostureSnapshot = DevicePostureSnapshot(),
        developerModeStatus: DeveloperModeStatus = DeveloperModeStatus(),
        previousDevOptionsOn: Boolean = developerModeStatus.isDeveloperOptionsEnabled,
        previousUsbDebuggingOn: Boolean = developerModeStatus.isUsbDebuggingEnabled,
        hasDevBaseline: Boolean = false
    ): Boolean {
        return runScanDetailed(
            force = force,
            minIntervalMillis = minIntervalMillis,
            isBackground = isBackground,
            devicePosture = devicePosture,
            developerModeStatus = developerModeStatus,
            previousDevOptionsOn = previousDevOptionsOn,
            previousUsbDebuggingOn = previousUsbDebuggingOn,
            hasDevBaseline = hasDevBaseline
        ).succeeded
    }

    /**
     * Runs a full on-device package scan off the main thread, saves a snapshot of all
     * installed packages in Room, records a ScanHistoryEntity (with 90-day auto-retention),
     * and returns newly detected changes for notifications and UI feedback.
     */
    suspend fun runScanDetailed(
        force: Boolean = true,
        minIntervalMillis: Long = 2_000L,
        isBackground: Boolean = false,
        devicePosture: DevicePostureSnapshot = DevicePostureSnapshot(),
        developerModeStatus: DeveloperModeStatus = DeveloperModeStatus(),
        previousDevOptionsOn: Boolean = developerModeStatus.isDeveloperOptionsEnabled,
        previousUsbDebuggingOn: Boolean = developerModeStatus.isUsbDebuggingEnabled,
        hasDevBaseline: Boolean = false
    ): ScanRunResult {
        if (!force && (System.currentTimeMillis() - lastCompletedScanAtMillis) < minIntervalMillis) {
            return ScanRunResult(succeeded = false)
        }

        return scanMutex.withLock {
            if (!force && (System.currentTimeMillis() - lastCompletedScanAtMillis) < minIntervalMillis) {
                return@withLock ScanRunResult(succeeded = false)
            }

            if (!isBackground) {
                _executionState.value = ScanExecutionState.Scanning
            }
            try {
                val result = withContext(ioDispatcher) {
                    val scanTimestamp = System.currentTimeMillis()
                    val currentPackages = packageScanner.scanInstalledPackages()
                    val currentPackageSet = currentPackages.map { it.packageName }.toSet()

                    val previousScan = scanDao.getLatestScanOnce()
                    val previousSnapshotsByPkg = if (previousScan != null) {
                        scanDao.getSnapshotsForScan(previousScan.id).associateBy { it.packageName }
                    } else {
                        emptyMap()
                    }

                    val trustedList = scanDao.observeTrustedApps().first()
                    val trustedSet = trustedList.map { it.packageName }.toSet()

                    val existingSigChangesBefore = scanDao.getAllSignatureChangesOnce()
                    val prevActiveSigSet = existingSigChangesBefore
                        .filter { !it.isAcknowledged && it.isStillInstalled }
                        .map { it.packageName }
                        .toSet()

                    val previousSuspiciousPackages = previousSnapshotsByPkg.values.map { prevSnap ->
                        SecurityEvaluator.evaluate(
                            snapshot = prevSnap,
                            trustedPackages = trustedSet,
                            ownPackageName = packageScanner.ownPackageName,
                            signatureChangedPackages = prevActiveSigSet,
                            nowMillis = scanTimestamp
                        )
                    }.filter { it.isActiveWarning }.map { it.snapshot.packageName }.toSet()

                    val isBaseline = previousScan == null
                    val newlyDetected = if (isBaseline) {
                        emptyList()
                    } else {
                        currentPackages.filter { it.packageName !in previousSnapshotsByPkg }
                    }

                    val newScanId = scanDao.insertScanRecord(
                        ScanRecordEntity(
                            scannedAtMillis = scanTimestamp,
                            totalAppsCount = currentPackages.size,
                            newAppsDetectedCount = newlyDetected.size,
                            isBaseline = isBaseline
                        )
                    )

                    val snapshotEntities = currentPackages.map { pkg ->
                        PackageSnapshotEntity(
                            scanId = newScanId,
                            packageName = pkg.packageName,
                            appName = pkg.appName,
                            versionName = pkg.versionName,
                            versionCode = pkg.versionCode,
                            firstInstallTimeMillis = pkg.firstInstallTimeMillis,
                            lastUpdateTimeMillis = pkg.lastUpdateTimeMillis,
                            installerPackageName = pkg.installerPackageName,
                            isSystemApp = pkg.isSystemApp,
                            hasLauncherIcon = pkg.hasLauncherIcon,
                            riskyPermissionsCsv = RiskyPermission.toCsv(pkg.riskyPermissions),
                            hasAccessibilityAccess = pkg.hasAccessibilityAccess,
                            isDeviceAdmin = pkg.isDeviceAdmin,
                            hasNotificationListener = pkg.hasNotificationListener,
                            hasOverlayPermission = pkg.hasOverlayPermission,
                            signingCertSha256 = pkg.signingCertSha256,
                            grantedPermissionsCsv = pkg.grantedPermissions.joinToString(",")
                        )
                    }
                    scanDao.insertPackageSnapshots(snapshotEntities)

                    // Reconcile & upsert newly detected apps
                    val existingDetected = scanDao.getAllDetectedNewAppsOnce()
                    val updatedExistingDetected = existingDetected.mapNotNull { existing ->
                        val stillInstalled = existing.packageName in currentPackageSet
                        if (existing.isStillInstalled != stillInstalled) {
                            existing.copy(isStillInstalled = stillInstalled)
                        } else {
                            null
                        }
                    }

                    val newAlerts = newlyDetected.map { pkg ->
                        DetectedNewAppEntity(
                            packageName = pkg.packageName,
                            appName = pkg.appName,
                            versionName = pkg.versionName,
                            versionCode = pkg.versionCode,
                            firstInstallTimeMillis = pkg.firstInstallTimeMillis,
                            lastUpdateTimeMillis = pkg.lastUpdateTimeMillis,
                            installerPackageName = pkg.installerPackageName,
                            isSystemApp = pkg.isSystemApp,
                            detectedAtMillis = scanTimestamp,
                            detectedInScanId = newScanId,
                            isReviewed = false,
                            isStillInstalled = true
                        )
                    }

                    val detectedUpserts = updatedExistingDetected + newAlerts
                    if (detectedUpserts.isNotEmpty()) {
                        scanDao.upsertDetectedNewApps(detectedUpserts)
                    }

                    // Detect signing certificate changes & newly granted permissions on existing apps
                    val sigChangeAlerts = mutableListOf<SignatureChangeEntity>()
                    val permChangeAlerts = mutableListOf<PermissionChangeEntity>()

                    if (!isBaseline) {
                        for (pkg in currentPackages) {
                            val prev = previousSnapshotsByPkg[pkg.packageName] ?: continue

                            if (
                                prev.signingCertSha256.isNotBlank() &&
                                pkg.signingCertSha256.isNotBlank() &&
                                prev.signingCertSha256 != pkg.signingCertSha256
                            ) {
                                sigChangeAlerts += SignatureChangeEntity(
                                    packageName = pkg.packageName,
                                    appName = pkg.appName,
                                    previousFingerprint = prev.signingCertSha256,
                                    currentFingerprint = pkg.signingCertSha256,
                                    detectedAtMillis = scanTimestamp,
                                    detectedInScanId = newScanId,
                                    isAcknowledged = false,
                                    isStillInstalled = true
                                )
                            }

                            val prevHadPermissionBaseline = prev.signingCertSha256.isNotBlank() ||
                                prev.grantedPermissionsCsv.isNotBlank()
                            if (prevHadPermissionBaseline) {
                                val prevGranted = prev.grantedPermissionsCsv
                                    .split(',')
                                    .map { it.trim() }
                                    .filter { it.isNotEmpty() }
                                    .toSet()
                                val newlyGranted = pkg.grantedPermissions.filter { it !in prevGranted }
                                if (newlyGranted.isNotEmpty()) {
                                    permChangeAlerts += PermissionChangeEntity(
                                        packageName = pkg.packageName,
                                        appName = pkg.appName,
                                        addedPermissionsCsv = newlyGranted.joinToString(","),
                                        detectedAtMillis = scanTimestamp,
                                        detectedInScanId = newScanId,
                                        isReviewed = false,
                                        isStillInstalled = true
                                    )
                                }
                            }
                        }

                        // Reconcile existing signature changes for uninstalled apps
                        val reconciledSig = existingSigChangesBefore.mapNotNull { existing ->
                            val stillInstalled = existing.packageName in currentPackageSet
                            if (existing.isStillInstalled != stillInstalled) {
                                existing.copy(isStillInstalled = stillInstalled)
                            } else {
                                null
                            }
                        }
                        val allSigUpserts = reconciledSig + sigChangeAlerts
                        if (allSigUpserts.isNotEmpty()) {
                            scanDao.upsertSignatureChanges(allSigUpserts)
                        }

                        // Reconcile existing permission changes for uninstalled apps
                        val existingPermChanges = scanDao.getAllPermissionChangesOnce()
                        val reconciledPerm = existingPermChanges.mapNotNull { existing ->
                            val stillInstalled = existing.packageName in currentPackageSet
                            if (existing.isStillInstalled != stillInstalled) {
                                existing.copy(isStillInstalled = stillInstalled)
                            } else {
                                null
                            }
                        }
                        val allPermUpserts = reconciledPerm + permChangeAlerts
                        if (allPermUpserts.isNotEmpty()) {
                            scanDao.insertPermissionChanges(allPermUpserts)
                        }
                    }

                    // Evaluate current posture & security score for scan history
                    val currentActiveSigChanges = scanDao.getAllSignatureChangesOnce()
                        .filter { !it.isAcknowledged && it.isStillInstalled }
                    val currentActiveSigSet = currentActiveSigChanges.map { it.packageName }.toSet()

                    val currentAssessments = snapshotEntities.map { snap ->
                        SecurityEvaluator.evaluate(
                            snapshot = snap,
                            trustedPackages = trustedSet,
                            ownPackageName = packageScanner.ownPackageName,
                            signatureChangedPackages = currentActiveSigSet,
                            nowMillis = scanTimestamp
                        )
                    }

                    val activeSuspicious = currentAssessments.filter { it.isActiveWarning }
                    val suspiciousNonSigCount = activeSuspicious.count { !it.hasSignatureChanged }
                    val newlySuspicious = if (isBaseline) {
                        emptyList()
                    } else {
                        activeSuspicious.filter { it.snapshot.packageName !in previousSuspiciousPackages }
                    }

                    val scoreReport = SecurityScoreEvaluator.evaluate(
                        posture = devicePosture,
                        developerMode = developerModeStatus,
                        untrustedSuspiciousCount = suspiciousNonSigCount,
                        signatureChangeCount = currentActiveSigChanges.size,
                        trustedPackages = trustedSet
                    )

                    val devOptionsTurnedOn = hasDevBaseline &&
                        !previousDevOptionsOn && developerModeStatus.isDeveloperOptionsEnabled
                    val usbTurnedOn = hasDevBaseline &&
                        !previousUsbDebuggingOn && developerModeStatus.isUsbDebuggingEnabled
                    val devModeTurnedOn = devOptionsTurnedOn || usbTurnedOn

                    val hiddenUserApps = snapshotEntities.filter {
                        !it.hasLauncherIcon && !it.isSystemApp && it.packageName != packageScanner.ownPackageName
                    }

                    val permHistoryEntries = permChangeAlerts.map { alert ->
                        val added = alert.addedPermissionsCsv
                            .split(',')
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                        HistoryPermissionChangeEntry(
                            appName = alert.appName,
                            packageName = alert.packageName,
                            addedPermissions = added,
                            hasRiskyPermission = added.any { RiskyPermission.isRiskyAndroidPermission(it) }
                        )
                    }

                    val historyDetails = ScanHistoryDetails(
                        developerOptionsEnabled = developerModeStatus.isDeveloperOptionsEnabled,
                        usbDebuggingEnabled = developerModeStatus.isUsbDebuggingEnabled,
                        isRooted = devicePosture.rootResult.isRooted,
                        rootTriggers = devicePosture.rootResult.triggeredChecks.map { describeRootTrigger(it) },
                        checklist = scoreReport.items.map { item ->
                            HistoryChecklistEntry(
                                id = item.id.name,
                                title = describeChecklistTitle(item.id),
                                status = item.status.name,
                                explanation = describeChecklistExplanation(item)
                            )
                        },
                        suspiciousApps = activeSuspicious.map { a ->
                            HistorySuspiciousEntry(
                                appName = a.snapshot.appName,
                                packageName = a.snapshot.packageName,
                                riskLevel = a.riskLevel.name,
                                reasons = a.reasons.map { describeSuspiciousReason(it) }
                            )
                        },
                        newlySuspiciousApps = newlySuspicious.map { a ->
                            HistoryAppEntry(
                                appName = a.snapshot.appName,
                                packageName = a.snapshot.packageName,
                                detail = a.riskLevel.name
                            )
                        },
                        signatureChangedApps = sigChangeAlerts.map { s ->
                            HistoryAppEntry(
                                appName = s.appName,
                                packageName = s.packageName,
                                detail = "Signature changed"
                            )
                        },
                        hiddenApps = hiddenUserApps.map { h ->
                            HistoryAppEntry(
                                appName = h.appName,
                                packageName = h.packageName,
                                detail = h.installerPackageName ?: "Unknown source"
                            )
                        },
                        newApps = newlyDetected.map { n ->
                            HistoryAppEntry(
                                appName = n.appName,
                                packageName = n.packageName,
                                detail = n.installerPackageName ?: "Unknown source"
                            )
                        },
                        permissionChanges = permHistoryEntries
                    )

                    val summaryText = ScanHistoryDetails.buildSummaryText(
                        newAppsCount = newlyDetected.size,
                        signatureChangesCount = sigChangeAlerts.size,
                        newSuspiciousCount = newlySuspicious.size,
                        permissionChangesCount = permChangeAlerts.size,
                        devModeTurnedOn = devModeTurnedOn
                    )

                    val historyId = scanDao.insertScanHistory(
                        ScanHistoryEntity(
                            scannedAtMillis = scanTimestamp,
                            isBackground = isBackground,
                            securityScore = scoreReport.score,
                            totalAppsCount = currentPackages.size,
                            newAppsCount = newlyDetected.size,
                            signatureChangesCount = sigChangeAlerts.size,
                            newSuspiciousCount = newlySuspicious.size,
                            permissionChangesCount = permChangeAlerts.size,
                            devModeAlert = devModeTurnedOn,
                            summaryText = summaryText,
                            detailsJson = historyDetails.toJson()
                        )
                    )

                    // Auto-prune scan history older than 90 days
                    val ninetyDaysCutoff = scanTimestamp - HISTORY_RETENTION_MILLIS
                    scanDao.deleteScanHistoryOlderThan(ninetyDaysCutoff)

                    scanDao.pruneOlderSnapshots(keepRecentScans = 5)
                    lastCompletedScanAtMillis = System.currentTimeMillis()

                    ScanRunResult(
                        succeeded = true,
                        historyEntryId = historyId,
                        securityScore = scoreReport.score,
                        newAppNames = newlyDetected.map { it.appName },
                        signatureChangedAppNames = sigChangeAlerts.map { it.appName },
                        newlySuspiciousAppNames = newlySuspicious.map { it.snapshot.appName },
                        riskyPermissionChangedAppNames = permHistoryEntries
                            .filter { it.hasRiskyPermission }
                            .map { it.appName },
                        developerOptionsTurnedOn = devOptionsTurnedOn,
                        usbDebuggingTurnedOn = usbTurnedOn
                    )
                }
                if (!isBackground) {
                    _executionState.value = ScanExecutionState.Idle
                }
                result
            } catch (e: Exception) {
                if (!isBackground) {
                    _executionState.value = ScanExecutionState.Error(
                        e.localizedMessage ?: "Scan failed"
                    )
                }
                ScanRunResult(succeeded = false)
            }
        }
    }

    suspend fun markAppReviewed(packageName: String) {
        withContext(ioDispatcher) { scanDao.markAppReviewed(packageName) }
    }

    suspend fun markAllAppsReviewed() {
        withContext(ioDispatcher) { scanDao.markAllAppsReviewed() }
    }

    suspend fun clearReviewedHistory() {
        withContext(ioDispatcher) { scanDao.clearReviewedHistory() }
    }

    suspend fun markAppTrusted(packageName: String, appName: String) {
        withContext(ioDispatcher) {
            scanDao.upsertTrustedApp(
                TrustedAppEntity(
                    packageName = packageName,
                    appName = appName,
                    trustedAtMillis = System.currentTimeMillis()
                )
            )
            scanDao.acknowledgeSignatureChange(packageName)
        }
    }

    suspend fun removeTrustedApp(packageName: String) {
        withContext(ioDispatcher) { scanDao.removeTrustedApp(packageName) }
    }

    suspend fun markPermissionChangeReviewed(id: Long) {
        withContext(ioDispatcher) { scanDao.markPermissionChangeReviewed(id) }
    }

    suspend fun markAllPermissionChangesReviewed() {
        withContext(ioDispatcher) { scanDao.markAllPermissionChangesReviewed() }
    }

    suspend fun clearReviewedPermissionChanges() {
        withContext(ioDispatcher) { scanDao.clearReviewedPermissionChanges() }
    }

    suspend fun clearAllScanHistory() {
        withContext(ioDispatcher) { scanDao.clearAllScanHistory() }
    }

    suspend fun pruneExpiredScanHistory(nowMillis: Long = System.currentTimeMillis()) {
        withContext(ioDispatcher) {
            scanDao.deleteScanHistoryOlderThan(nowMillis - HISTORY_RETENTION_MILLIS)
        }
    }

    companion object {
        const val HISTORY_RETENTION_DAYS: Long = 90L
        const val HISTORY_RETENTION_MILLIS: Long = HISTORY_RETENTION_DAYS * 24L * 60L * 60L * 1_000L

        private fun describeRootTrigger(trigger: RootCheckTrigger): String = when (trigger) {
            RootCheckTrigger.SU_BINARY_FOUND -> "su binary found"
            RootCheckTrigger.ROOT_MANAGER_APP_INSTALLED -> "Root manager app installed"
            RootCheckTrigger.TEST_KEYS_BUILD_TAGS -> "test-keys build"
            RootCheckTrigger.WRITABLE_SYSTEM_PARTITION -> "Writable /system partition"
        }

        private fun describeSuspiciousReason(reason: SuspiciousReason): String = when (reason) {
            SuspiciousReason.SignatureChanged -> "Signature changed"
            is SuspiciousReason.Sideloaded -> "Sideloaded (${reason.installerPackageName ?: "Unknown source"})"
            is SuspiciousReason.RiskyPermissions -> "${reason.permissions.size} risky permission(s)"
            SuspiciousReason.HiddenNoLauncher -> "No launcher icon"
            SuspiciousReason.AccessibilityEnabled -> "Accessibility enabled"
            SuspiciousReason.DeviceAdminActive -> "Device admin"
            SuspiciousReason.NotificationListenerEnabled -> "Notification listener"
            is SuspiciousReason.RecentWithManyPermissions -> "Installed <7d with ${reason.permissionCount} risky perms"
        }

        private fun describeChecklistTitle(id: SecurityChecklistId): String = when (id) {
            SecurityChecklistId.SCREEN_LOCK -> "Screen lock"
            SecurityChecklistId.SECURITY_PATCH -> "Android security patch"
            SecurityChecklistId.UNKNOWN_APP_INSTALLERS -> "Install unknown apps"
            SecurityChecklistId.USER_CA_CERTS -> "User-installed CA certificates"
            SecurityChecklistId.VPN_ACTIVE -> "VPN connection"
            SecurityChecklistId.DEVELOPER_MODE -> "Developer Options & USB Debugging"
            SecurityChecklistId.ROOT_STATUS -> "Root status"
            SecurityChecklistId.SUSPICIOUS_APPS -> "Suspicious apps"
            SecurityChecklistId.SIGNATURE_CHANGES -> "App signing certificates"
        }

        private fun describeChecklistExplanation(item: SecurityChecklistItem): String = when (item.id) {
            SecurityChecklistId.SCREEN_LOCK ->
                if (item.status == ChecklistStatus.PASS) "PIN, pattern, or password lock is enabled."
                else "No secure screen lock is configured."
            SecurityChecklistId.SECURITY_PATCH ->
                if (item.detailValue.isBlank()) "Security patch level could not be verified."
                else when (item.status) {
                    ChecklistStatus.PASS -> "Patch ${item.detailValue} is up to date (<3 months)."
                    ChecklistStatus.WARNING -> "Patch ${item.detailValue} is 3-6 months old."
                    else -> "Patch ${item.detailValue} is over 6 months old."
                }
            SecurityChecklistId.UNKNOWN_APP_INSTALLERS ->
                if (item.status == ChecklistStatus.PASS) "No untrusted apps allowed to install APKs."
                else "${item.count} untrusted app(s) allowed to install unknown apps."
            SecurityChecklistId.USER_CA_CERTS ->
                if (item.status == ChecklistStatus.PASS) "No user-installed root certificates present."
                else "${item.count} user CA certificate(s) installed."
            SecurityChecklistId.VPN_ACTIVE ->
                if (item.count > 0) "VPN is on (informational)." else "No VPN active (informational)."
            SecurityChecklistId.DEVELOPER_MODE -> when (item.status) {
                ChecklistStatus.PASS -> "Developer Options and USB Debugging are both off."
                ChecklistStatus.WARNING -> "Developer Options is ON (USB Debugging off)."
                else -> "USB Debugging is ON."
            }
            SecurityChecklistId.ROOT_STATUS ->
                if (item.status == ChecklistStatus.PASS) "Device is not rooted."
                else "Root detected (${item.count} check(s) triggered)."
            SecurityChecklistId.SUSPICIOUS_APPS ->
                if (item.status == ChecklistStatus.PASS) "No untrusted suspicious apps flagged."
                else "${item.count} untrusted suspicious app(s) flagged."
            SecurityChecklistId.SIGNATURE_CHANGES ->
                if (item.status == ChecklistStatus.PASS) "No signing certificate changes detected."
                else "${item.count} app(s) changed signing certificate."
        }
    }
}
