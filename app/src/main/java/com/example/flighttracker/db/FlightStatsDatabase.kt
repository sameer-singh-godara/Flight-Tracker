package com.example.flighttracker.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [FlightStatsEntity::class], version = 2, exportSchema = false)
abstract class FlightStatsDatabase : RoomDatabase() {
    abstract fun flightStatsDao(): FlightStatsDao
}