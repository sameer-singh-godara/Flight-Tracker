package com.example.flighttracker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.flighttracker.db.FlightStatsDatabase
import com.example.flighttracker.db.FlightStatsEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class RouteStatsWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val depIata = inputData.getString("depIata") ?: return Result.failure()
        val arrIata = inputData.getString("arrIata") ?: return Result.failure()
        val apiKey = inputData.getString("apiKey") ?: return Result.failure()

        return try {
            val apiClient = AviationStackApi.create()
            // Fetch 3 flights for the day (limit to 3)
            val response = apiClient.getFlightData(
                apiKey = apiKey,
                departureAirport = depIata,
                arrivalAirport = arrIata,
                limit = 3
            )
            if (response.isSuccessful) {
                val flightResponse = response.body()
                if (flightResponse?.data != null && flightResponse.data.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        val db = FlightTrackerApp.database.flightStatsDao() // Access via FlightTrackerApp
                        flightResponse.data.forEach { flight ->
                            val depIataValue = flight.departure?.iata ?: "UNK"
                            val arrIataValue = flight.arrival?.iata ?: "UNK"
                            val duration = calculateFlightDuration(flight)
                            val delay = flight.departure?.delay ?: flight.arrival?.delay ?: 0
                            val stats = FlightStatsEntity(
                                route = "$depIataValue-$arrIataValue",
                                durationMinutes = duration,
                                delayMinutes = delay,
                                timestamp = System.currentTimeMillis()
                            )
                            db.insert(stats)
                        }
                    }
                    Log.d("RouteStatsWorker", "Updated stats for $depIata to $arrIata")
                    Result.success()
                } else {
                    Result.retry()
                }
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e("RouteStatsWorker", "Error: ${e.message}")
            Result.retry()
        }
    }

    private fun calculateFlightDuration(flight: FlightData): Int {
        val depTime = flight.departure?.scheduled?.let { parseTime(it) }
        val arrTime = flight.arrival?.scheduled?.let { parseTime(it) }
        return if (depTime != null && arrTime != null) {
            ((arrTime.time - depTime.time) / (1000 * 60)).toInt() + (flight.departure?.delay ?: 0) + (flight.arrival?.delay ?: 0)
        } else 0
    }

    private fun parseTime(timeStr: String): Date? {
        return try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault()).parse(timeStr)
        } catch (e: Exception) {
            null
        }
    }
}