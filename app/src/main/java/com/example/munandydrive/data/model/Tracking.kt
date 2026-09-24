package com.example.munandydrive.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DriverLocation(
    @SerialName("user_id") val userId: Int,
    @SerialName("user_name") val userName: String,
    val lat: Double,
    val lng: Double,
    val timestamp: String,
    @SerialName("route_id") val routeId: Int,
    @SerialName("route_name") val routeName: String
)

@Serializable
data class SyncedStop(
    val id: Int,
    @SerialName("route_id") val routeId: Int,
    @SerialName("customer_id") val customerId: Int,
    @SerialName("stop_order") val stopOrder: Int,
    val status: String,
    @SerialName("arrived_at") val arrivedAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    val notes: String? = null,
    @SerialName("skipped_reason") val skippedReason: String? = null,
    val name: String,
    val address: String,
    val lat: Double,
    val lng: Double,
    @SerialName("opening_time") val openingTime: String? = null,
    @SerialName("closing_time") val closingTime: String? = null,
    val contact: String? = null,
    @SerialName("preferred_products") val preferredProducts: String? = null
)

@Serializable
data class SyncedRoute(
    @SerialName("route_id") val routeId: Int,
    val stops: List<SyncedStop>
)

@Serializable
data class PostLocationRequest(
    val lat: Double,
    val lng: Double,
    val timestamp: String,
    @SerialName("route_id") val routeId: Int
)

@Serializable
data class UpdateStopStatusRequest(
    val status: String,
    val notes: String? = null,
    @SerialName("skipped_reason") val skippedReason: String? = null,
    @SerialName("arrived_at") val arrivedAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null
)

@Serializable
data class UploadPodRequest(
    @SerialName("photo_base64") val photoBase64: String
)

@Serializable
data class UploadPodResponse(
    val success: Boolean = false,
    @SerialName("photo_url") val photoUrl: String? = null
)
