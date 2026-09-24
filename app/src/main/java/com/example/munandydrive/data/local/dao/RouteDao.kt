package com.example.munandydrive.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.munandydrive.data.local.entity.RouteEntity
import com.example.munandydrive.data.local.entity.RouteStopEntity
import com.example.munandydrive.data.model.StopStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface RouteDao {
    @Query("SELECT * FROM routes")
    fun getAllRoutes(): Flow<List<RouteEntity>>

    @Query("SELECT * FROM routes WHERE assignedTo = :userId")
    fun getRoutesForUser(userId: Int): Flow<List<RouteEntity>>

    @Query("SELECT * FROM routes WHERE id = :routeId")
    suspend fun getRouteById(routeId: Int): RouteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoute(route: RouteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStops(stops: List<RouteStopEntity>)

    @Query("SELECT * FROM route_stops WHERE routeId = :routeId ORDER BY stopOrder ASC")
    fun getStopsForRoute(routeId: Int): Flow<List<RouteStopEntity>>

    @Query("UPDATE route_stops SET status = :status, arrivedAt = :arrivedAt, notes = :notes WHERE id = :stopId")
    suspend fun updateStopStatus(stopId: Int, status: StopStatus, arrivedAt: String?, notes: String?)

    @Query("DELETE FROM routes WHERE id NOT IN (:ids)")
    suspend fun deleteRoutesNotIn(ids: List<Int>)

    @Query("DELETE FROM routes")
    suspend fun deleteAllRoutes()

    @Query("DELETE FROM routes WHERE id = :routeId")
    suspend fun deleteRouteById(routeId: Int)

    @Query("DELETE FROM route_stops WHERE routeId = :routeId")
    suspend fun deleteStopsForRoute(routeId: Int)

    @Query("DELETE FROM route_stops WHERE routeId NOT IN (:routeIds)")
    suspend fun deleteStopsNotInRoutes(routeIds: List<Int>)

    @Query("DELETE FROM route_stops")
    suspend fun deleteAllStops()
}
