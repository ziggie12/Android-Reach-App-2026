package com.example.munandydrive.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_stop_updates")
data class PendingStopUpdateEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val routeId: Int,
    val stopId: Int,
    val status: String,
    val notes: String? = null,
    val skippedReason: String? = null,
    val arrivedAt: String? = null,
    val completedAt: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
