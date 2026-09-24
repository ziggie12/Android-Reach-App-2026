package com.example.munandydrive.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StatsResponse(
    @SerialName("active_routes")   val activeRoutes: Int = 0,
    @SerialName("active_drivers")  val activeDrivers: Int = 0,
    @SerialName("stops_completed") val stopsCompleted: Int = 0,
    @SerialName("stops_pending")   val stopsPending: Int = 0
)

@Serializable
data class MutationResponse(
    val id: Int? = null,
    val success: Boolean? = null
)
