package com.example.data.repository

import com.example.data.local.dao.ScanDao
import com.example.data.local.entity.DetectedNewAppEntity
import com.example.data.local.entity.PackageSnapshotEntity
import com.example.data.local.entity.ScanRecordEntity
import com.example.data.local.entity.TrustedAppEntity
import com.example.data.scanner.AppSecurityAssessment
import com.example.data.scanner.PackageScanner
import com.example.data.scanner.RiskyPermission
import com.example.data.scanner.SecurityEvaluator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface ScanExecutionState {
    data object Idle : ScanExecutionState
    data object Scanning : ScanExecutionState
    data class Error(val message: String) : ScanExecutionState
}

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

    val securityAssessments: Flow<List<AppSecurityAssessment>> = combine(
        latestSnapshotPackages,
        trustedApps
    ) { snapshots, trusted ->
        val trustedSet = trusted.map { it.packageName }.toSet()
        val now = System.currentTimeMillis()
        snapshots.map { snapshot ->
            SecurityEvaluator.evaluate(
                snapshot = snapshot,
                trustedPackages = trustedSet,
                ownPackageName = packageScanner.ownPackageName,
                nowMillis = now
            )
        }
    }

    fun observeAssessmentForPackage(packageName: String): Flow<AppSecurityAssessment?> {
        return combine(
            scanDao.observeLatestPackageByName(packageName),
            trustedApps
        ) { snapshot, trusted ->
            if (snapshot == null) return@combine null
            val trustedSet = trusted.map { it.packageName }.toSet()
            SecurityEvaluator.evaluate(
                snapshot = snapshot,
                trustedPackages = trustedSet,
                ownPackageName = packageScanner.ownPackageName,
                nowMillis = System.currentTimeMillis()
            )
        }
    }

    @Volatile
    private var lastCompletedScanAtMillis: Long = 0L

    /**
     * Runs a full on-device package scan off the main thread, saves a snapshot of all
     * installed packages in Room, and compares it against the previous scan's snapshot.
     */
    suspend fun runScan(force: Boolean = true, minIntervalMillis: Long = 2_000L): Boolean {
        if (!force && (System.currentTimeMillis() - lastCompletedScanAtMillis) < minIntervalMillis) {
            return false
        }

        return scanMutex.withLock {
            if (!force && (System.currentTimeMillis() - lastCompletedScanAtMillis) < minIntervalMillis) {
                return@withLock false
            }

            _executionState.value = ScanExecutionState.Scanning
            try {
                withContext(ioDispatcher) {
                    val scanTimestamp = System.currentTimeMillis()
                    val currentPackages = packageScanner.scanInstalledPackages()
                    val currentPackageSet = currentPackages.map { it.packageName }.toSet()

                    val previousScan = scanDao.getLatestScanOnce()
                    val previousPackageNames = if (previousScan != null) {
                        scanDao.getPackageNamesForScan(previousScan.id).toSet()
                    } else {
                        emptySet()
                    }

                    val isBaseline = previousScan == null
                    val newlyDetected = if (isBaseline) {
                        emptyList()
                    } else {
                        currentPackages.filter { it.packageName !in previousPackageNames }
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
                            hasOverlayPermission = pkg.hasOverlayPermission
                        )
                    }
                    scanDao.insertPackageSnapshots(snapshotEntities)

                    val existingDetected = scanDao.getAllDetectedNewAppsOnce()
                    val updatedExisting = existingDetected.mapNotNull { existing ->
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

                    val upserts = updatedExisting + newAlerts
                    if (upserts.isNotEmpty()) {
                        scanDao.upsertDetectedNewApps(upserts)
                    }

                    scanDao.pruneOlderSnapshots(keepRecentScans = 5)
                    lastCompletedScanAtMillis = System.currentTimeMillis()
                }
                _executionState.value = ScanExecutionState.Idle
                true
            } catch (e: Exception) {
                _executionState.value = ScanExecutionState.Error(
                    e.localizedMessage ?: "Scan failed"
                )
                false
            }
        }
    }

    suspend fun markAppReviewed(packageName: String) {
        withContext(ioDispatcher) {
            scanDao.markAppReviewed(packageName)
        }
    }

    suspend fun markAllAppsReviewed() {
        withContext(ioDispatcher) {
            scanDao.markAllAppsReviewed()
        }
    }

    suspend fun clearReviewedHistory() {
        withContext(ioDispatcher) {
            scanDao.clearReviewedHistory()
        }
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
        }
    }

    suspend fun removeTrustedApp(packageName: String) {
        withContext(ioDispatcher) {
            scanDao.removeTrustedApp(packageName)
        }
    }
}
