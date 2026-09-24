package com.example.munandydrive.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ReportsSummary(
    @SerialName("draft_routes")    val draftRoutes: Int = 0,
    @SerialName("active_routes")   val activeRoutes: Int = 0,
    @SerialName("complete_routes") val completeRoutes: Int = 0,
    @SerialName("inactive_routes") val inactiveRoutes: Int = 0,
    @SerialName("pending_stops")   val pendingStops: Int = 0,
    @SerialName("arrived_stops")   val arrivedStops: Int = 0,
    @SerialName("completed_stops") val completedStops: Int = 0,
    @SerialName("skipped_stops")   val skippedStops: Int = 0,
    @SerialName("avg_stop_duration_minutes") val avgStopDurationMinutes: Int? = null,
    val salesmen: List<SalesmanReport> = emptyList()
)

@Serializable
data class SalesmanReport(
    val name: String,
    @SerialName("routes_assigned")  val routesAssigned: Int = 0,
    @SerialName("stops_completed")  val stopsCompleted: Int = 0,
    @SerialName("stops_skipped")    val stopsSkipped: Int = 0,
    @SerialName("stops_total")      val stopsTotal: Int = 0
)
