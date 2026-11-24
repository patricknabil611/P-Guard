package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trusted_apps")
data class TrustedAppEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val trustedAtMillis: Long
)
