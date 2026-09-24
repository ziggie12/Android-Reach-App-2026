package com.example.munandydrive.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.munandydrive.data.local.entity.PendingStopUpdateEntity

@Dao
interface PendingStopUpdateDao {
    @Query("SELECT * FROM pending_stop_updates ORDER BY createdAt ASC")
    suspend fun getAll(): List<PendingStopUpdateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(update: PendingStopUpdateEntity)

    @Delete
    suspend fun delete(update: PendingStopUpdateEntity)

    @Query("DELETE FROM pending_stop_updates WHERE routeId = :routeId AND stopId = :stopId")
    suspend fun deleteForStop(routeId: Int, stopId: Int)

    @Query("SELECT COUNT(*) FROM pending_stop_updates")
    suspend fun count(): Int
}
