package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.ScanDao
import com.example.data.local.entity.DetectedNewAppEntity
import com.example.data.local.entity.PackageSnapshotEntity
import com.example.data.local.entity.PermissionChangeEntity
import com.example.data.local.entity.ScanHistoryEntity
import com.example.data.local.entity.ScanRecordEntity
import com.example.data.local.entity.SignatureChangeEntity
import com.example.data.local.entity.TrustedAppEntity

@Database(
    entities = [
        ScanRecordEntity::class,
        PackageSnapshotEntity::class,
        DetectedNewAppEntity::class,
        TrustedAppEntity::class,
        SignatureChangeEntity::class,
        PermissionChangeEntity::class,
        ScanHistoryEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class PGuardDatabase : RoomDatabase() {
    abstract fun scanDao(): ScanDao

    companion object {
        private const val DATABASE_NAME = "p_guard_security.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `trusted_apps` (
                        `packageName` TEXT NOT NULL,
                        `appName` TEXT NOT NULL,
                        `trustedAtMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`packageName`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "ALTER TABLE `package_snapshots` ADD COLUMN `hasLauncherIcon` INTEGER NOT NULL DEFAULT 1"
                )
                db.execSQL(
                    "ALTER TABLE `package_snapshots` ADD COLUMN `riskyPermissionsCsv` TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE `package_snapshots` ADD COLUMN `hasAccessibilityAccess` INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE `package_snapshots` ADD COLUMN `isDeviceAdmin` INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE `package_snapshots` ADD COLUMN `hasNotificationListener` INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE `package_snapshots` ADD COLUMN `hasOverlayPermission` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `package_snapshots` ADD COLUMN `signingCertSha256` TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE `package_snapshots` ADD COLUMN `grantedPermissionsCsv` TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `signature_changes` (
                        `packageName` TEXT NOT NULL,
                        `appName` TEXT NOT NULL,
                        `previousFingerprint` TEXT NOT NULL,
                        `currentFingerprint` TEXT NOT NULL,
                        `detectedAtMillis` INTEGER NOT NULL,
                        `detectedInScanId` INTEGER NOT NULL,
                        `isAcknowledged` INTEGER NOT NULL DEFAULT 0,
                        `isStillInstalled` INTEGER NOT NULL DEFAULT 1,
                        PRIMARY KEY(`packageName`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `permission_changes` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `packageName` TEXT NOT NULL,
                        `appName` TEXT NOT NULL,
                        `addedPermissionsCsv` TEXT NOT NULL,
                        `detectedAtMillis` INTEGER NOT NULL,
                        `detectedInScanId` INTEGER NOT NULL,
                        `isReviewed` INTEGER NOT NULL DEFAULT 0,
                        `isStillInstalled` INTEGER NOT NULL DEFAULT 1
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `scan_history` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `scannedAtMillis` INTEGER NOT NULL,
                        `isBackground` INTEGER NOT NULL DEFAULT 0,
                        `securityScore` INTEGER NOT NULL DEFAULT 100,
                        `totalAppsCount` INTEGER NOT NULL DEFAULT 0,
                        `newAppsCount` INTEGER NOT NULL DEFAULT 0,
                        `signatureChangesCount` INTEGER NOT NULL DEFAULT 0,
                        `newSuspiciousCount` INTEGER NOT NULL DEFAULT 0,
                        `permissionChangesCount` INTEGER NOT NULL DEFAULT 0,
                        `devModeAlert` INTEGER NOT NULL DEFAULT 0,
                        `summaryText` TEXT NOT NULL DEFAULT '',
                        `detailsJson` TEXT NOT NULL DEFAULT ''
                    )
                    """.trimIndent()
                )
            }
        }

        fun create(context: Context): PGuardDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                PGuardDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
        }
    }
}
