package com.example.flighttracker.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface FlightStatsDao {
    @Insert
    suspend fun insert(flightStats: FlightStatsEntity)

    @Query("SELECT route, AVG(durationMinutes) as avgDuration FROM flight_stats GROUP BY route")
    suspend fun getRouteHistory(): List<RouteHistory>

    @Query("SELECT AVG(durationMinutes) as avgDuration, AVG(delayMinutes) as avgDelay FROM flight_stats WHERE route = :route")
    suspend fun getRouteStats(route: String): RouteStats?
}

data class RouteStats(
    val avgDuration: Double?,
    val avgDelay: Double?
)

data class RouteHistory(
    val route: String,
    val avgDuration: Double?
)