package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_history")
data class ScanHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val scannedAtMillis: Long,
    val isBackground: Boolean = false,
    val securityScore: Int = 100,
    val totalAppsCount: Int = 0,
    val newAppsCount: Int = 0,
    val signatureChangesCount: Int = 0,
    val newSuspiciousCount: Int = 0,
    val permissionChangesCount: Int = 0,
    val devModeAlert: Boolean = false,
    val summaryText: String = "",
    val detailsJson: String = ""
)
