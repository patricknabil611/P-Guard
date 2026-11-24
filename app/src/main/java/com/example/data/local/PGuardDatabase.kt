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
import com.example.data.local.entity.ScanRecordEntity
import com.example.data.local.entity.TrustedAppEntity

@Database(
    entities = [
        ScanRecordEntity::class,
        PackageSnapshotEntity::class,
        DetectedNewAppEntity::class,
        TrustedAppEntity::class
    ],
    version = 2,
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

        fun create(context: Context): PGuardDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                PGuardDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(MIGRATION_1_2)
                .build()
        }
    }
}
