package com.example.munandydrive.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class RouteStatus {
    @SerialName("Draft") DRAFT,
    @SerialName("Active") ACTIVE,
    @SerialName("Complete") COMPLETE,
    @SerialName("Inactive") INACTIVE
}

@Serializable
data class Route(
    val id: Int,
    val name: String,
    val date: String,
    @SerialName("created_by") val createdBy: Int? = null,
    @SerialName("assigned_to") val assignedTo: Int? = null,
    @SerialName("assigned_to_name") val assignedToName: String? = null,
    val status: RouteStatus
)

@Serializable
enum class StopStatus {
    @SerialName("Pending") PENDING,
    @SerialName("Arrived") ARRIVED,
    @SerialName("Completed") COMPLETED,
    @SerialName("Skipped") SKIPPED
}

@Serializable
data class AddStopItem(
    @SerialName("customer_id") val customerId: Int,
    @SerialName("stop_order") val stopOrder: Int
)

@Serializable
data class AddStopsRequest(val stops: List<AddStopItem>)

@Serializable
data class RouteStop(
    val id: Int,
    @SerialName("route_id") val routeId: Int,
    @SerialName("customer_id") val customerId: Int,
    @SerialName("stop_order") val stopOrder: Int,
    val status: StopStatus,
    @SerialName("arrived_at") val arrivedAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    val notes: String? = null,
    @SerialName("skipped_reason") val skippedReason: String? = null,
    val customerName: String? = null,
    val customerAddress: String? = null,
    val customerLat: Double? = null,
    val customerLng: Double? = null
)
