package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.DetectedNewAppEntity
import com.example.data.local.entity.PackageSnapshotEntity
import com.example.data.local.entity.ScanRecordEntity
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

    @Query("SELECT packageName FROM package_snapshots WHERE scanId = :scanId")
    suspend fun getPackageNamesForScan(scanId: Long): List<String>

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
}
