package com.example.flighttracker

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.flighttracker.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val apiClient = AviationStackApi.create()
    private val apiKey = "439f32d72667d96dc71e0533e7ca122d"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.trackButton.setOnClickListener {
            val flightNumber = binding.flightNumberInput.text.toString().trim()
            if (flightNumber.isNotEmpty()) {
                trackFlight(flightNumber)
            } else {
                showError("✈️ Please enter a flight number")
            }
        }
    }

    private fun trackFlight(flightNumber: String) {
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

    @SuppressLint("SetTextI18n")
    private fun updateUI(flightData: FlightData) {
        binding.flightDetailsContainer.visibility = View.VISIBLE

        with(flightData) {
            // Basic flight info
            binding.flightNumberText.text = "✈️ Flight: ${flight?.iata ?: "N/A"}"
            binding.airlineText.text = "🏛️ Airline: ${airline?.name ?: "N/A"} (${airline?.iata ?: ""})"

            // Departure info (with IST adjustment)
            binding.departureText.text = """
                🛫 Departure:
                🏢 Airport: ${departure?.airport ?: "N/A"} (${departure?.iata ?: ""})
                🚪 Terminal: ${departure?.terminal ?: "N/A"}, Gate: ${departure?.gate ?: "N/A"}
                🕒 Scheduled: ${adjustTimeForIST(departure?.scheduled)}
                ⏳ Estimated: ${adjustTimeForIST(departure?.estimated)}
                ✅ Actual: ${adjustTimeForIST(departure?.actual)}
                ⏰ Delay: ${departure?.delay ?: 0} min
            """.trimIndent()

            // Arrival info (with IST adjustment)
            binding.arrivalText.text = """
                🛬 Arrival:
                🏢 Airport: ${arrival?.airport ?: "N/A"} (${arrival?.iata ?: ""})
                🚪 Terminal: ${arrival?.terminal ?: "N/A"}, Gate: ${arrival?.gate ?: "N/A"}
                🕒 Scheduled: ${adjustTimeForIST(arrival?.scheduled)}
                ⏳ Estimated: ${adjustTimeForIST(arrival?.estimated)}
                ✅ Actual: ${adjustTimeForIST(arrival?.actual)}
                ⏰ Delay: ${arrival?.delay ?: 0} min
            """.trimIndent()

            // Flight status
            binding.statusText.text = "📊 Status: ${flight_status?.replace("_", " ")?.capitalize() ?: "N/A"}"

            // Live data (without time adjustment)
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

    private fun adjustTimeForIST(apiTime: String?): String {
        if (apiTime.isNullOrEmpty()) return "N/A"
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
            val date = inputFormat.parse(apiTime)

            // Subtract 5 hours 30 minutes for IST
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