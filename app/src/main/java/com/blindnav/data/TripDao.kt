package com.blindnav.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Insert
    suspend fun insert(trip: TripEntity): Long

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun get(id: Long): TripEntity?

    @Query("SELECT * FROM trips")
    suspend fun all(): List<TripEntity>

    @Query("SELECT * FROM trips ORDER BY startedAtMs DESC")
    fun observeAll(): Flow<List<TripEntity>>

    @Query("UPDATE trips SET optimalPolyline = NULL WHERE startedAtMs < :cutoffMs AND optimalPolyline IS NOT NULL")
    suspend fun clearOptimalRoutesBefore(cutoffMs: Long)
}
