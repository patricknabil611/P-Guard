package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "detected_new_apps")
data class DetectedNewAppEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val firstInstallTimeMillis: Long,
    val lastUpdateTimeMillis: Long,
    val installerPackageName: String?,
    val isSystemApp: Boolean,
    val detectedAtMillis: Long,
    val detectedInScanId: Long,
    val isReviewed: Boolean = false,
    val isStillInstalled: Boolean = true
)
