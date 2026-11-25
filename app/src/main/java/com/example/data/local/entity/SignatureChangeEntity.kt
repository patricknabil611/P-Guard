package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "signature_changes")
data class SignatureChangeEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val previousFingerprint: String,
    val currentFingerprint: String,
    val detectedAtMillis: Long,
    val detectedInScanId: Long,
    val isAcknowledged: Boolean = false,
    val isStillInstalled: Boolean = true
)
