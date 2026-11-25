package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "permission_changes")
data class PermissionChangeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val packageName: String,
    val appName: String,
    val addedPermissionsCsv: String,
    val detectedAtMillis: Long,
    val detectedInScanId: Long,
    val isReviewed: Boolean = false,
    val isStillInstalled: Boolean = true
)
