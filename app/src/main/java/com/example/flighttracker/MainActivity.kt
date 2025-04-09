package com.example.flighttracker

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.flighttracker.databinding.ActivityMainBinding
import com.example.flighttracker.db.FlightStatsEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import androidx.work.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val apiClient = AviationStackApi.create()
    private val apiKeys = listOf(
        "439f32d72667d96dc71e0533e7ca122d", // Replace with your additional API keys if needed
        "f9bc28d79ae8197e390ad292024308ba",
        "2876a088a6f920f2aac06fd11900fdca",
        "3d28600beb658bb095eb99c8fd8e3c0d",
        "51bb2bbeb46827ef8e718f983238c93c",
        "7dd8aa39712ef1683256797638f9eb4a",
        "4612de52ed33e2a6d678ad88d1ae1be2",
        "39eaad1ca5d0a008e09f3ee801265335",
        "f617496f9aad38f5ee8181b5b5494278",
        "77c311182be949ca37f66f4ec8f7cb8f",
        "e1717c38a511f74d4b633cef4b637d0c",
        "4863e8c7e213654ad908c7a999b2dcbf",
        "699ae730fec5c6b77c5c114614f1ae09",
        "635288c2b1d2207ef7fab743b3532190",
        "435fab4c7dc93ffe17b78ab0af30523e",
        "ffa45e5f83873df2a695f42cb1dfb201",
        "6c80eb4f9c0cbf0446c5d707e1ece7d9",
        "3e045e2a3df6e5b8057f1fc5ecf7b445",
        "8a789705113d3852063a34449dc14e23",
        "d0eac1281011f89a5263262c3a317486",
        "ca8c9e8b8a9b4112e7b6af74c08f9c00",
    )
    private var currentApiKeyIndex = 0
    private val db by lazy { FlightTrackerApp.database.flightStatsDao() }
    private var flightRefreshHandler: Handler? = null
    private var flightRefreshRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.trackButton.setOnClickListener { trackFlight() }
        binding.statsButton.setOnClickListener { fetchRouteStats() }
        binding.historyButton.setOnClickListener { showHistory() }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopFlightRefresh() // Clean up Handler to prevent memory leaks
    }

    private fun trackFlight() {
        val flightNumber = binding.flightNumberInput.text.toString().trim()
        if (flightNumber.isNotEmpty()) {
            binding.progressBar.visibility = View.VISIBLE
            binding.errorText.visibility = View.GONE
            binding.flightDetailsContainer.visibility = View.VISIBLE

            lifecycleScope.launch {
                var success = false
                var attempt = 0
                while (!success && attempt < apiKeys.size) {
                    try {
                        val response = apiClient.getFlightData(apiKey = getCurrentApiKey(), flightNumber = flightNumber, limit = 1)
                        if (response.isSuccessful) {
                            val flightResponse = response.body()
                            if (flightResponse?.data != null && flightResponse.data.isNotEmpty()) {
                                updateUI(flightResponse.data[0])
                                startFlightRefresh(flightNumber)
                                success = true
                            } else {
                                showError("🔍 No flight data found")
                                success = true
                            }
                        } else {
                            switchToNextApiKey()
                            attempt++
                            if (attempt == apiKeys.size) {
                                showError("⚠️ Failed to track flight $flightNumber. All API keys exhausted.")
                            }
                        }
                    } catch (e: Exception) {
                        if (attempt == apiKeys.size - 1) {
                            showError("⚠️ Network error: ${e.message}. All API keys exhausted.")
                        }
                        switchToNextApiKey()
                        attempt++
                    }
                }
                if (!success && attempt == apiKeys.size) {
                    showError("⚠️ Failed to track flight $flightNumber. All API keys exhausted.")
                }
                binding.progressBar.visibility = View.GONE
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
            binding.flightDetailsContainer.visibility = View.VISIBLE

            lifecycleScope.launch {
                var success = false
                var attempt = 0
                while (!success && attempt < apiKeys.size) {
                    try {
                        val initialResponse = apiClient.getFlightData(apiKey = getCurrentApiKey(), flightNumber = flightNumber, limit = 1)
                        if (initialResponse.isSuccessful) {
                            val flightResponse = initialResponse.body()
                            val flightData = flightResponse?.data?.firstOrNull()
                            val depIata = flightData?.departure?.iata
                            val arrIata = flightData?.arrival?.iata
                            val depAirport = flightData?.departure?.airport
                            val arrAirport = flightData?.arrival?.airport
                            if (depIata != null && arrIata != null) {
                                val statsResponse = apiClient.getFlightData(
                                    apiKey = getCurrentApiKey(),
                                    departureAirport = depIata,
                                    arrivalAirport = arrIata,
                                    limit = 5
                                )
                                if (statsResponse.isSuccessful) {
                                    val flightResponse = statsResponse.body()
                                    if (flightResponse?.data != null && flightResponse.data.isNotEmpty()) {
                                        val flightDataList = flightResponse.data.take(5)
                                        saveFlightStats(flightDataList)
                                        val (averageTime, averageDelay) = calculateRouteStats(flightDataList)
                                        updateStatsUI(averageTime, averageDelay, depIata, arrIata, depAirport, arrAirport, flightDataList)
                                        scheduleRouteStatsJob(depIata, arrIata)
                                        success = true
                                    } else {
                                        showError("🔍 No flight data found for stats")
                                        success = true
                                    }
                                } else {
                                    switchToNextApiKey()
                                    attempt++
                                    if (attempt == apiKeys.size) {
                                        showError("⚠️ Failed to fetch route stats for $flightNumber. All API keys exhausted.")
                                    }
                                }
                            } else {
                                showError("🔍 Departure or arrival IATA not found")
                                success = true
                            }
                        } else {
                            switchToNextApiKey()
                            attempt++
                            if (attempt == apiKeys.size) {
                                showError("⚠️ Failed to fetch route stats for $flightNumber. All API keys exhausted.")
                            }
                        }
                    } catch (e: Exception) {
                        if (attempt == apiKeys.size - 1) {
                            showError("⚠️ Network error: ${e.message}. All API keys exhausted.")
                        }
                        switchToNextApiKey()
                        attempt++
                    }
                }
                if (!success && attempt == apiKeys.size) {
                    showError("⚠️ Failed to fetch route stats for $flightNumber. All API keys exhausted.")
                }
                binding.progressBar.visibility = View.GONE
            }
        } else {
            showError("✈️ Please enter a flight number")
        }
    }

    private fun getCurrentApiKey(): String {
        return apiKeys[currentApiKeyIndex]
    }

    private fun switchToNextApiKey() {
        currentApiKeyIndex = (currentApiKeyIndex + 1) % apiKeys.size
        Log.d("API", "Switched to API key index $currentApiKeyIndex")
    }

    private fun startFlightRefresh(flightNumber: String) {
        stopFlightRefresh()
        flightRefreshHandler = Handler(Looper.getMainLooper())
        flightRefreshRunnable = Runnable {
            lifecycleScope.launch {
                refreshFlightData(flightNumber)
                flightRefreshHandler?.postDelayed(flightRefreshRunnable!!, 60000)
            }
        }
        flightRefreshHandler?.postDelayed(flightRefreshRunnable!!, 60000)
    }

    private suspend fun refreshFlightData(flightNumber: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.errorText.visibility = View.GONE

        var success = false
        var attempt = 0
        while (!success && attempt < apiKeys.size) {
            try {
                val response = withContext(Dispatchers.IO) {
                    apiClient.getFlightData(apiKey = getCurrentApiKey(), flightNumber = flightNumber, limit = 1)
                }
                if (response.isSuccessful) {
                    val flightResponse = response.body()
                    if (flightResponse?.data != null && flightResponse.data.isNotEmpty()) {
                        updateUI(flightResponse.data[0])
                        success = true
                    } else {
                        showError("🔍 No flight data found during refresh")
                        success = true
                    }
                } else {
                    switchToNextApiKey()
                    attempt++
                    if (attempt == apiKeys.size) {
                        showError("⚠️ Failed to refresh flight $flightNumber. All API keys exhausted.")
                    }
                }
            } catch (e: Exception) {
                if (attempt == apiKeys.size - 1) {
                    showError("⚠️ Network error during refresh: ${e.message}. All API keys exhausted.")
                }
                switchToNextApiKey()
                attempt++
            }
        }
        binding.progressBar.visibility = View.GONE
    }

    private fun stopFlightRefresh() {
        flightRefreshHandler?.removeCallbacks(flightRefreshRunnable!!)
        flightRefreshHandler = null
        flightRefreshRunnable = null
    }

    private fun scheduleRouteStatsJob(depIata: String, arrIata: String) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<RouteStatsWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .setInitialDelay(1, TimeUnit.DAYS)
            .setInputData(workDataOf("depIata" to depIata, "arrIata" to arrIata, "apiKey" to getCurrentApiKey()))
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "routeStats_$depIata$arrIata",
            ExistingPeriodicWorkPolicy.REPLACE,
            workRequest
        )
    }

    private fun showHistory() {
        binding.progressBar.visibility = View.VISIBLE
        binding.errorText.visibility = View.GONE
        binding.flightDetailsContainer.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val routeHistory = db.getRouteHistory()
                if (routeHistory.isNotEmpty()) {
                    val historyText = routeHistory.joinToString("\n") { history ->
                        val (depIata, arrIata) = history.route.split("-")
                        "Route: $depIata to $arrIata - Avg Duration: ${history.avgDuration?.toInt() ?: 0} minutes"
                    }
                    binding.flightNumberText.text = "📜 History"
                    binding.mainContentText.text = historyText
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

    private fun saveFlightStats(flightDataList: List<FlightData>) {
        lifecycleScope.launch {
            flightDataList.forEach { flight ->
                val depIata = flight.departure?.iata ?: ""
                val arrIata = flight.arrival?.iata ?: ""
                val duration = calculateFlightDuration(flight)
                val delay = flight.departure?.delay ?: flight.arrival?.delay ?: 0
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
            ((arrTime.time - depTime.time) / (1000 * 60)).toInt() + (flight.departure?.delay ?: 0) + (flight.arrival?.delay ?: 0)
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
            binding.mainContentText.text = buildString {
                // Airline Information
                appendLine("🏛️ Airline: ${airline?.name ?: "N/A"} (${airline?.iata ?: ""})\n")

                // Departure Details
                appendLine("🛫 Departure:")
                appendLine("  🏢 Airport: ${departure?.airport ?: "N/A"} (${departure?.iata ?: ""})")
                appendLine("  🚪 Terminal: ${departure?.terminal ?: "N/A"}, Gate: ${departure?.gate ?: "N/A"}")
                appendLine("  🕒 Scheduled: ${adjustTimeForIST(departure?.scheduled)}")
                appendLine("  ⏳ Estimated: ${adjustTimeForIST(departure?.estimated)}")
                appendLine("  ✅ Actual: ${adjustTimeForIST(departure?.actual)}")
                appendLine("  ⏰ Delay: ${departure?.delay ?: 0} min\n")

                // Arrival Details
                appendLine("🛬 Arrival:")
                appendLine("  🏢 Airport: ${arrival?.airport ?: "N/A"} (${arrival?.iata ?: ""})")
                appendLine("  🚪 Terminal: ${arrival?.terminal ?: "N/A"}, Gate: ${arrival?.gate ?: "N/A"}")
                appendLine("  🕒 Scheduled: ${adjustTimeForIST(arrival?.scheduled)}")
                appendLine("  ⏳ Estimated: ${adjustTimeForIST(arrival?.estimated)}")
                appendLine("  ✅ Actual: ${adjustTimeForIST(arrival?.actual)}")
                appendLine("  ⏰ Delay: ${arrival?.delay ?: 0} min\n")

                // Flight Status
                appendLine("📊 Status: ${flight_status?.replace("_", " ")?.capitalize() ?: "N/A"}\n")

                // Live Tracking (if available)
                if (live != null) {
                    appendLine("🛰️ Live Tracking:")
                    appendLine("  🔄 Updated: ${formatTime(live.updated)} IST")
                    appendLine("  📍 Position: ${"%.4f".format(live.latitude)}, ${"%.4f".format(live.longitude)}")
                    appendLine("  ⬆️ Altitude: ${live.altitude?.toInt()?.convertMetersToFeet() ?: 0} ft")
                    appendLine("  🚀 Speed: ${live.speed_horizontal?.toInt()?.convertKphToKnots() ?: 0} kts")
                    appendLine("  🧭 Direction: ${live.direction?.toInt() ?: 0}°\n")
                } else {
                    appendLine("📡 Live tracking data not available\n")
                }
            }.trim()
            binding.lastUpdatedText.text = "⏱️ Last updated: ${getCurrentFormattedTime()}"
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateStatsUI(
        averageTime: Int,
        averageDelay: Int,
        depIata: String,
        arrIata: String,
        depAirport: String?,
        arrAirport: String?,
        flightDataList: List<FlightData>
    ) {
        binding.flightDetailsContainer.visibility = View.VISIBLE
        binding.flightNumberText.text = "📊 Route Stats"
        binding.mainContentText.text = buildString {
            appendLine("✈️ Route: $depAirport ($depIata) to $arrAirport ($arrIata)")
            appendLine("⏱️ Average Flight Duration: $averageTime minutes")
            appendLine("⏰ Average Delay: $averageDelay minutes")
            appendLine("🔍 Top 5 Flights:\n")
            flightDataList.forEachIndexed { index, flight ->
                val flightNumber = flight.flight?.iata ?: "N/A"
                val airline = flight.airline?.name ?: "N/A"
                val depAirport = flight.departure?.airport ?: "N/A"
                val arrAirport = flight.arrival?.airport ?: "N/A"
                val depScheduled = adjustTimeForIST(flight.departure?.scheduled) ?: "N/A"
                val arrScheduled = adjustTimeForIST(flight.arrival?.scheduled) ?: "N/A"
                val delay = flight.departure?.delay ?: flight.arrival?.delay ?: 0
                appendLine("${index + 1}. $flightNumber ($airline)")
                appendLine("Scheduled: $depScheduled - $arrScheduled")
                appendLine("Delay: $delay min\n")
            }
        }.trim()
        binding.lastUpdatedText.text = "⏱️ Last updated: ${getCurrentFormattedTime()}"
    }

    private fun adjustTimeForIST(apiTime: String?): String {
        if (apiTime.isNullOrEmpty()) return "N/A"
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
            val date = inputFormat.parse(apiTime)
            val calendar = Calendar.getInstance().apply {
                time = date
                add(Calendar.HOUR_OF_DAY, -5)
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