package com.example.flighttracker.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "flight_stats")
data class FlightStatsEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val route: String,
    val durationMinutes: Int,
    val delayMinutes: Int,
    val timestamp: Long
)