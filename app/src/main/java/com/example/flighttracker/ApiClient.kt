package com.example.flighttracker

import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface AviationStackApi {
    @GET("flights")
    suspend fun getFlightData(
        @Query("access_key") apiKey: String,
        @Query("flight_iata") flightNumber: String? = null,
        @Query("flight_status") status: String? = null,
        @Query("dep_iata") departureAirport: String? = null,
        @Query("arr_iata") arrivalAirport: String? = null,
        @Query("airline_iata") airlineCode: String? = null,
        @Query("limit") limit: Int = 1
    ): Response<FlightResponse>

    companion object {
        private const val BASE_URL = "https://api.aviationstack.com/v1/"

        fun create(): AviationStackApi {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(AviationStackApi::class.java)
        }
    }
}