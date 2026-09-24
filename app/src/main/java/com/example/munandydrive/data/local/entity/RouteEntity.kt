package com.example.munandydrive.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.munandydrive.data.model.RouteStatus
import com.example.munandydrive.data.model.StopStatus

@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val date: String,
    val createdBy: Int?,
    val assignedTo: Int?,
    val assignedToName: String? = null,
    val status: RouteStatus,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(tableName = "route_stops")
data class RouteStopEntity(
    @PrimaryKey val id: Int,
    val routeId: Int,
    val customerId: Int,
    val stopOrder: Int,
    val status: StopStatus,
    val arrivedAt: String?,
    val completedAt: String?,
    val notes: String?,
    val skippedReason: String?
)
