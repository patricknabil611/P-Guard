package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.DetectedNewAppEntity
import com.example.data.local.entity.PackageSnapshotEntity
import com.example.data.local.entity.PermissionChangeEntity
import com.example.data.local.entity.ScanRecordEntity
import com.example.data.local.entity.SignatureChangeEntity
import com.example.data.local.entity.TrustedAppEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanDao {

    @Query("SELECT * FROM scan_records ORDER BY scannedAtMillis DESC, id DESC LIMIT 1")
    fun observeLatestScan(): Flow<ScanRecordEntity?>

    @Query("SELECT * FROM scan_records ORDER BY scannedAtMillis DESC, id DESC LIMIT 1")
    suspend fun getLatestScanOnce(): ScanRecordEntity?

    @Query("SELECT COUNT(*) FROM scan_records")
    fun observeScanCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScanRecord(record: ScanRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPackageSnapshots(snapshots: List<PackageSnapshotEntity>)

    @Query("SELECT * FROM package_snapshots WHERE scanId = :scanId")
    suspend fun getSnapshotsForScan(scanId: Long): List<PackageSnapshotEntity>

    @Query(
        """
        SELECT * FROM package_snapshots
        WHERE scanId = (SELECT id FROM scan_records ORDER BY scannedAtMillis DESC, id DESC LIMIT 1)
        ORDER BY isSystemApp ASC, appName COLLATE NOCASE ASC
        """
    )
    fun observeLatestSnapshotPackages(): Flow<List<PackageSnapshotEntity>>

    @Query(
        """
        SELECT * FROM package_snapshots
        WHERE scanId = (SELECT id FROM scan_records ORDER BY scannedAtMillis DESC, id DESC LIMIT 1)
          AND packageName = :packageName
        LIMIT 1
        """
    )
    fun observeLatestPackageByName(packageName: String): Flow<PackageSnapshotEntity?>

    @Query(
        """
        DELETE FROM package_snapshots
        WHERE scanId NOT IN (
            SELECT id FROM scan_records ORDER BY scannedAtMillis DESC, id DESC LIMIT :keepRecentScans
        )
        """
    )
    suspend fun pruneOlderSnapshots(keepRecentScans: Int = 5)

    // Detected new apps
    @Query(
        """
        SELECT * FROM detected_new_apps
        WHERE isReviewed = 0 AND isStillInstalled = 1
        ORDER BY detectedAtMillis DESC, appName COLLATE NOCASE ASC
        """
    )
    fun observeUnreviewedNewApps(): Flow<List<DetectedNewAppEntity>>

    @Query(
        """
        SELECT * FROM detected_new_apps
        WHERE isReviewed = 1 OR isStillInstalled = 0
        ORDER BY detectedAtMillis DESC, appName COLLATE NOCASE ASC
        """
    )
    fun observeReviewedOrRemovedApps(): Flow<List<DetectedNewAppEntity>>

    @Query("SELECT * FROM detected_new_apps")
    suspend fun getAllDetectedNewAppsOnce(): List<DetectedNewAppEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDetectedNewApps(apps: List<DetectedNewAppEntity>)

    @Query("UPDATE detected_new_apps SET isReviewed = 1 WHERE packageName = :packageName")
    suspend fun markAppReviewed(packageName: String)

    @Query("UPDATE detected_new_apps SET isReviewed = 1 WHERE isReviewed = 0")
    suspend fun markAllAppsReviewed()

    @Query("DELETE FROM detected_new_apps WHERE isReviewed = 1 OR isStillInstalled = 0")
    suspend fun clearReviewedHistory()

    // Trusted apps
    @Query("SELECT * FROM trusted_apps ORDER BY trustedAtMillis DESC, appName COLLATE NOCASE ASC")
    fun observeTrustedApps(): Flow<List<TrustedAppEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrustedApp(app: TrustedAppEntity)

    @Query("DELETE FROM trusted_apps WHERE packageName = :packageName")
    suspend fun removeTrustedApp(packageName: String)

    // Signature change alerts
    @Query(
        """
        SELECT * FROM signature_changes
        WHERE isAcknowledged = 0 AND isStillInstalled = 1
        ORDER BY detectedAtMillis DESC, appName COLLATE NOCASE ASC
        """
    )
    fun observeActiveSignatureChanges(): Flow<List<SignatureChangeEntity>>

    @Query("SELECT * FROM signature_changes")
    suspend fun getAllSignatureChangesOnce(): List<SignatureChangeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSignatureChanges(changes: List<SignatureChangeEntity>)

    @Query("UPDATE signature_changes SET isAcknowledged = 1 WHERE packageName = :packageName")
    suspend fun acknowledgeSignatureChange(packageName: String)

    // Permission change alerts
    @Query(
        """
        SELECT * FROM permission_changes
        WHERE isReviewed = 0 AND isStillInstalled = 1
        ORDER BY detectedAtMillis DESC, appName COLLATE NOCASE ASC
        """
    )
    fun observeUnreviewedPermissionChanges(): Flow<List<PermissionChangeEntity>>

    @Query(
        """
        SELECT * FROM permission_changes
        WHERE isReviewed = 1 OR isStillInstalled = 0
        ORDER BY detectedAtMillis DESC, appName COLLATE NOCASE ASC
        """
    )
    fun observeReviewedPermissionChanges(): Flow<List<PermissionChangeEntity>>

    @Query("SELECT * FROM permission_changes")
    suspend fun getAllPermissionChangesOnce(): List<PermissionChangeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPermissionChanges(changes: List<PermissionChangeEntity>)

    @Query("UPDATE permission_changes SET isReviewed = 1 WHERE id = :id")
    suspend fun markPermissionChangeReviewed(id: Long)

    @Query("UPDATE permission_changes SET isReviewed = 1 WHERE isReviewed = 0")
    suspend fun markAllPermissionChangesReviewed()

    @Query("DELETE FROM permission_changes WHERE isReviewed = 1 OR isStillInstalled = 0")
    suspend fun clearReviewedPermissionChanges()

    // Scan history (kept for 90 days)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScanHistory(entry: com.example.data.local.entity.ScanHistoryEntity): Long

    @Query("SELECT * FROM scan_history ORDER BY scannedAtMillis DESC, id DESC")
    fun observeScanHistory(): Flow<List<com.example.data.local.entity.ScanHistoryEntity>>

    @Query("SELECT * FROM scan_history WHERE scannedAtMillis >= :sinceMillis ORDER BY scannedAtMillis ASC, id ASC")
    fun observeScanHistorySince(sinceMillis: Long): Flow<List<com.example.data.local.entity.ScanHistoryEntity>>

    @Query("SELECT COUNT(*) FROM scan_history")
    fun observeScanHistoryCount(): Flow<Int>

    @Query("SELECT * FROM scan_history WHERE id = :id LIMIT 1")
    fun observeScanHistoryById(id: Long): Flow<com.example.data.local.entity.ScanHistoryEntity?>

    @Query("SELECT * FROM scan_history WHERE id = :id LIMIT 1")
    suspend fun getScanHistoryByIdOnce(id: Long): com.example.data.local.entity.ScanHistoryEntity?

    @Query("SELECT * FROM scan_history ORDER BY scannedAtMillis DESC, id DESC LIMIT 1")
    suspend fun getLatestScanHistoryOnce(): com.example.data.local.entity.ScanHistoryEntity?

    @Query("DELETE FROM scan_history WHERE scannedAtMillis < :cutoffMillis")
    suspend fun deleteScanHistoryOlderThan(cutoffMillis: Long)

    @Query("DELETE FROM scan_history")
    suspend fun clearAllScanHistory()
}
