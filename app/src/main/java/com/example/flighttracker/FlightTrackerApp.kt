package com.example.flighttracker

import android.app.Application
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.flighttracker.db.FlightStatsDatabase

class FlightTrackerApp : Application() {
    companion object {
        lateinit var database: FlightStatsDatabase
    }

    override fun onCreate() {
        super.onCreate()
        database = Room.databaseBuilder(
            applicationContext,
            FlightStatsDatabase::class.java,
            "flight_stats_db"
        ).addMigrations(MIGRATION_1_2).build()
    }

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE flight_stats ADD COLUMN delayMinutes INTEGER NOT NULL DEFAULT 0")
        }
    }
}