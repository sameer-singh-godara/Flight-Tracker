package com.example.flighttracker

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.flighttracker.databinding.ActivityMainBinding
import com.example.flighttracker.db.FlightStatsDao
import com.example.flighttracker.db.FlightStatsEntity
import com.example.flighttracker.db.RouteHistory
import kotlinx.coroutines.launch
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val apiClient = AviationStackApi.create()
    private val apiKey = "439f32d72667d96dc71e0533e7ca122d"
    private val db by lazy { FlightTrackerApp.database.flightStatsDao() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.trackButton.setOnClickListener { trackFlight() }
        binding.statsButton.setOnClickListener { fetchRouteStats() }
        binding.historyButton.setOnClickListener { showHistory() }
    }

    private fun trackFlight() {
        val flightNumber = binding.flightNumberInput.text.toString().trim()
        if (flightNumber.isNotEmpty()) {
            binding.progressBar.visibility = View.VISIBLE
            binding.errorText.visibility = View.GONE
            binding.flightDetailsContainer.visibility = View.GONE

            lifecycleScope.launch {
                try {
                    val response = apiClient.getFlightData(
                        apiKey = apiKey,
                        flightNumber = flightNumber,
                        limit = 1
                    )
                    handleApiResponse(response)
                } catch (e: Exception) {
                    showError("⚠️ Network error: ${e.message}")
                } finally {
                    binding.progressBar.visibility = View.GONE
                }
            }
        } else {
            showError("✈️ Please enter a flight number")
        }
    }

    private fun fetchRouteStats() {
        val flightNumber = binding.flightNumberInput.text.toString().trim()
        if (flightNumber.isNotEmpty()) {
            binding.progressBar.visibility = View.VISIBLE
            binding.errorText.visibility = View.GONE
            binding.flightDetailsContainer.visibility = View.GONE

            lifecycleScope.launch {
                try {
                    val initialResponse = apiClient.getFlightData(
                        apiKey = apiKey,
                        flightNumber = flightNumber,
                        limit = 1
                    )
                    if (initialResponse.isSuccessful) {
                        val flightResponse = initialResponse.body()
                        val flightData = flightResponse?.data?.firstOrNull()
                        val depIata = flightData?.departure?.iata
                        val arrIata = flightData?.arrival?.iata
                        if (depIata != null && arrIata != null) {
                            val statsResponse = apiClient.getFlightData(
                                apiKey = apiKey,
                                departureAirport = depIata,
                                arrivalAirport = arrIata,
                                limit = 5
                            )
                            handleStatsResponse(statsResponse, depIata, arrIata)
                        } else {
                            showError("🔍 Departure or arrival IATA not found")
                        }
                    } else {
                        showError("⚠️ Initial API error: ${initialResponse.code()}")
                    }
                } catch (e: Exception) {
                    showError("⚠️ Network error: ${e.message}")
                } finally {
                    binding.progressBar.visibility = View.GONE
                }
            }
        } else {
            showError("✈️ Please enter a flight number")
        }
    }

    private fun showHistory() {
        binding.progressBar.visibility = View.VISIBLE
        binding.errorText.visibility = View.GONE
        binding.flightDetailsContainer.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val routeHistory = db.getRouteHistory()
                if (routeHistory.isNotEmpty()) {
                    val historyText = routeHistory.joinToString("\n") { history ->
                        val (depIata, arrIata) = history.route.split("-")
                        "Route: $depIata to $arrIata - Avg Duration: ${history.avgDuration?.toInt() ?: 0} minutes"
                    }
                    binding.flightDetailsContainer.visibility = View.VISIBLE
                    binding.flightNumberText.text = "📜 History"
                    binding.airlineText.text = ""
                    binding.departureText.text = ""
                    binding.arrivalText.text = ""
                    binding.statusText.text = ""
                    binding.locationText.text = historyText
                    binding.lastUpdatedText.text = "⏱️ Last updated: ${getCurrentFormattedTime()}"
                } else {
                    showError("🔍 No history available")
                }
            } catch (e: Exception) {
                showError("⚠️ Error loading history: ${e.message}")
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun handleApiResponse(response: Response<FlightResponse>) {
        if (response.isSuccessful) {
            val flightResponse = response.body()
            if (flightResponse?.data != null && flightResponse.data.isNotEmpty()) {
                updateUI(flightResponse.data[0])
            } else {
                showError("🔍 No flight data found")
            }
        } else {
            showError("⚠️ API error: ${response.code()} - ${response.message()}")
        }
    }

    private fun handleStatsResponse(response: Response<FlightResponse>, depIata: String, arrIata: String) {
        if (response.isSuccessful) {
            val flightResponse = response.body()
            if (flightResponse?.data != null && flightResponse.data.isNotEmpty()) {
                val flightDataList = flightResponse.data.take(5)
                saveFlightStats(flightDataList)
                val (averageTime, averageDelay) = calculateRouteStats(flightDataList)
                updateStatsUI(averageTime, averageDelay, depIata, arrIata, flightDataList)
            } else {
                showError("🔍 No flight data found for stats")
            }
        } else {
            showError("⚠️ API error: ${response.code()} - ${response.message()}")
        }
    }

    private fun saveFlightStats(flightDataList: List<FlightData>) {
        lifecycleScope.launch {
            flightDataList.forEach { flight ->
                val depIata = flight.departure?.iata ?: ""
                val arrIata = flight.arrival?.iata ?: ""
                val duration = calculateFlightDuration(flight)
                val delay = flight.departure?.delay ?: 0
                val stats = FlightStatsEntity(
                    route = "$depIata-$arrIata",
                    durationMinutes = duration,
                    delayMinutes = delay,
                    timestamp = System.currentTimeMillis()
                )
                db.insert(stats)
            }
        }
    }

    private fun calculateFlightDuration(flight: FlightData): Int {
        val depTime = flight.departure?.scheduled?.let { parseTime(it) }
        val arrTime = flight.arrival?.scheduled?.let { parseTime(it) }
        return if (depTime != null && arrTime != null) {
            ((arrTime.time - depTime.time) / (1000 * 60)).toInt()
        } else 0
    }

    private fun calculateRouteStats(flightDataList: List<FlightData>): Pair<Int, Int> {
        val durations = flightDataList.mapNotNull { calculateFlightDuration(it) }
        val delays = flightDataList.mapNotNull { it.departure?.delay ?: it.arrival?.delay ?: 0 }
        val averageTime = if (durations.isNotEmpty()) durations.average().toInt() else 0
        val averageDelay = if (delays.isNotEmpty()) delays.average().toInt() else 0
        return Pair(averageTime, averageDelay)
    }

    private fun parseTime(timeStr: String): Date? {
        return try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault()).parse(timeStr)
        } catch (e: Exception) {
            null
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateUI(flightData: FlightData) {
        binding.flightDetailsContainer.visibility = View.VISIBLE
        with(flightData) {
            binding.flightNumberText.text = "✈️ Flight: ${flight?.iata ?: "N/A"}"
            binding.airlineText.text = "🏛️ Airline: ${airline?.name ?: "N/A"} (${airline?.iata ?: ""})"
            binding.departureText.text = """
                🛫 Departure:
                🏢 Airport: ${departure?.airport ?: "N/A"} (${departure?.iata ?: ""})
                🚪 Terminal: ${departure?.terminal ?: "N/A"}, Gate: ${departure?.gate ?: "N/A"}
                🕒 Scheduled: ${adjustTimeForIST(departure?.scheduled)}
                ⏳ Estimated: ${adjustTimeForIST(departure?.estimated)}
                ✅ Actual: ${adjustTimeForIST(departure?.actual)}
                ⏰ Delay: ${departure?.delay ?: 0} min
            """.trimIndent()
            binding.arrivalText.text = """
                🛬 Arrival:
                🏢 Airport: ${arrival?.airport ?: "N/A"} (${arrival?.iata ?: ""})
                🚪 Terminal: ${arrival?.terminal ?: "N/A"}, Gate: ${arrival?.gate ?: "N/A"}
                🕒 Scheduled: ${adjustTimeForIST(arrival?.scheduled)}
                ⏳ Estimated: ${adjustTimeForIST(arrival?.estimated)}
                ✅ Actual: ${adjustTimeForIST(arrival?.actual)}
                ⏰ Delay: ${arrival?.delay ?: 0} min
            """.trimIndent()
            binding.statusText.text = "📊 Status: ${flight_status?.replace("_", " ")?.capitalize() ?: "N/A"}"
            binding.locationText.text = if (live != null) {
                """
                🛰️ Live Tracking:
                🔄 Updated: ${formatTime(live.updated)} IST
                📍 Position: ${"%.4f".format(live.latitude)}, ${"%.4f".format(live.longitude)}
                ⬆️ Altitude: ${live.altitude?.toInt()?.convertMetersToFeet() ?: 0} ft
                🚀 Speed: ${live.speed_horizontal?.toInt()?.convertKphToKnots() ?: 0} kts
                🧭 Direction: ${live.direction?.toInt() ?: 0}°
                """.trimIndent()
            } else {
                "📡 Live tracking data not available"
            }
        }
        binding.lastUpdatedText.text = "⏱️ Last updated: ${getCurrentFormattedTime()}"
    }

    @SuppressLint("SetTextI18n")
    private fun updateStatsUI(averageTime: Int, averageDelay: Int, depIata: String, arrIata: String, flightDataList: List<FlightData>) {
        binding.flightDetailsContainer.visibility = View.VISIBLE
        binding.flightNumberText.text = "📊 Route Stats"
        binding.airlineText.text = ""
        binding.departureText.text = ""
        binding.arrivalText.text = ""
        binding.statusText.text = ""
        binding.locationText.text = buildString {
            appendLine("✈️ Route: $depIata to $arrIata")
            appendLine("⏱️ Average Flight Duration: $averageTime minutes")
            appendLine("⏰ Average Delay: $averageDelay minutes")
            appendLine("🔍 Top 5 Flights:")
            flightDataList.forEachIndexed { index, flight ->
                val flightNumber = flight.flight?.iata ?: "N/A"
                val airline = flight.airline?.name ?: "N/A"
                val depAirport = flight.departure?.airport ?: "N/A"
                val arrAirport = flight.arrival?.airport ?: "N/A"
                val depScheduled = adjustTimeForIST(flight.departure?.scheduled) ?: "N/A"
                val arrScheduled = adjustTimeForIST(flight.arrival?.scheduled) ?: "N/A"
                val delay = flight.departure?.delay ?: flight.arrival?.delay ?: 0
                appendLine("${index + 1}. $flightNumber ($airline) - $depAirport to $arrAirport")
                appendLine("   Scheduled: $depScheduled - $arrScheduled, Delay: $delay min")
            }
        }
        binding.lastUpdatedText.text = "⏱️ Last updated: ${getCurrentFormattedTime()}"
    }

    private fun adjustTimeForIST(apiTime: String?): String {
        if (apiTime.isNullOrEmpty()) return "N/A"
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
            val date = inputFormat.parse(apiTime)
            val calendar = Calendar.getInstance().apply {
                time = date
                add(Calendar.HOUR_OF_DAY, -5) // Adjust to IST (UTC+5:30)
                add(Calendar.MINUTE, -30)
            }
            val outputFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
            "${outputFormat.format(calendar.time)} IST"
        } catch (e: Exception) {
            "N/A"
        }
    }

    private fun formatTime(apiTime: String?): String {
        if (apiTime.isNullOrEmpty()) return "N/A"
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
            val date = inputFormat.parse(apiTime)
            val outputFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
            outputFormat.format(date)
        } catch (e: Exception) {
            "N/A"
        }
    }

    private fun showError(message: String) {
        binding.errorText.text = message
        binding.errorText.visibility = View.VISIBLE
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun getCurrentFormattedTime(): String {
        val outputFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
        return outputFormat.format(Date())
    }

    private fun Int.convertMetersToFeet() = (this * 3.28084).toInt()
    private fun Int.convertKphToKnots() = (this * 0.539957).toInt()
}