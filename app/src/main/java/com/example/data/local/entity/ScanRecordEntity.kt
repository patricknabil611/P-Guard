package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_records")
data class ScanRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val scannedAtMillis: Long,
    val totalAppsCount: Int,
    val newAppsDetectedCount: Int,
    val isBaseline: Boolean
)
