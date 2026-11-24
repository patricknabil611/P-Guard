package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "package_snapshots",
    foreignKeys = [
        ForeignKey(
            entity = ScanRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["scanId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["scanId"]),
        Index(value = ["scanId", "packageName"], unique = true)
    ]
)
data class PackageSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val scanId: Long,
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val firstInstallTimeMillis: Long,
    val lastUpdateTimeMillis: Long,
    val installerPackageName: String?,
    val isSystemApp: Boolean,
    val hasLauncherIcon: Boolean = true,
    val riskyPermissionsCsv: String = "",
    val hasAccessibilityAccess: Boolean = false,
    val isDeviceAdmin: Boolean = false,
    val hasNotificationListener: Boolean = false,
    val hasOverlayPermission: Boolean = false
)
