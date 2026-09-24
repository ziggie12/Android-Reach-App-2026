package com.example.munandydrive.data.repository

import com.example.munandydrive.data.local.dao.RouteDao
import com.example.munandydrive.data.local.entity.RouteEntity
import com.example.munandydrive.data.local.entity.RouteStopEntity
import com.example.munandydrive.data.model.AddStopItem
import com.example.munandydrive.data.model.AddStopsRequest
import com.example.munandydrive.data.model.Route
import com.example.munandydrive.data.model.RouteStop
import com.example.munandydrive.data.remote.MunandyApiService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RouteRepository(
    private val routeDao: RouteDao,
    private val apiService: MunandyApiService
) {
    val allRoutes: Flow<List<Route>> = routeDao.getAllRoutes().map { entities ->
        entities.map { it.toDomain() }
    }

    suspend fun refreshRoutes() {
        val remoteRoutes = apiService.getRoutes()
        if (remoteRoutes.isEmpty()) {
            routeDao.deleteAllStops()
            routeDao.deleteAllRoutes()
        } else {
            remoteRoutes.forEach { route -> routeDao.insertRoute(route.toEntity()) }
            val ids = remoteRoutes.map { it.id }
            routeDao.deleteRoutesNotIn(ids)
            routeDao.deleteStopsNotInRoutes(ids)
        }
    }

    /** Creates a new route and returns its ID. */
    suspend fun createRoute(name: String, date: String, assignedTo: Int?): Int {
        val body = mutableMapOf("name" to name, "date" to date)
        assignedTo?.let { body["assigned_to"] = it.toString() }
        val response = apiService.createRoute(body)
        refreshRoutes()
        return response.id ?: throw Exception("Server did not return a route ID")
    }

    /** Adds stops to an existing route (replaces all). */
    suspend fun addStops(routeId: Int, customerIds: List<Int>) {
        val stops = customerIds.mapIndexed { idx, cid -> AddStopItem(cid, idx + 1) }
        apiService.addStops(routeId, AddStopsRequest(stops))
    }

    /** Updates a route's status. */
    suspend fun updateRouteStatus(routeId: Int, status: String) {
        apiService.updateRouteStatus(routeId, mapOf("status" to status))
        refreshRoutes()
    }

    /** Assigns (or unassigns) a route to a user. */
    suspend fun assignRoute(routeId: Int, userId: Int?) {
        apiService.assignRoute(routeId, mapOf<String, Any?>("assigned_to" to userId))
        refreshRoutes()
    }

    /** Deletes a route and its stops. */
    suspend fun deleteRoute(routeId: Int) {
        apiService.deleteRoute(routeId)
        routeDao.deleteStopsForRoute(routeId)
        routeDao.deleteRouteById(routeId)
    }

    fun getStopsForRoute(routeId: Int): Flow<List<RouteStop>> =
        routeDao.getStopsForRoute(routeId).map { entities -> entities.map { it.toDomain() } }

    private fun RouteEntity.toDomain() = Route(
        id = id, name = name, date = date,
        createdBy = createdBy, assignedTo = assignedTo,
        assignedToName = assignedToName, status = status
    )

    private fun Route.toEntity() = RouteEntity(
        id = id, name = name, date = date,
        createdBy = createdBy, assignedTo = assignedTo,
        assignedToName = assignedToName, status = status
    )

    private fun RouteStopEntity.toDomain() = RouteStop(
        id = id, routeId = routeId, customerId = customerId, stopOrder = stopOrder,
        status = status, arrivedAt = arrivedAt, completedAt = completedAt,
        notes = notes, skippedReason = skippedReason
    )
}
