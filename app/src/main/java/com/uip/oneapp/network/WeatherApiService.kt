package com.uip.oneapp.network

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class WeatherResult(
    val temperature: Double,
    val weatherCode: Int,
    val weatherDescription: String,
    val windSpeed: Double
) {
    fun toFormattedString(): String {
        return "$weatherDescription, ${"%.1f".format(temperature)}°C, Wind ${"%.0f".format(windSpeed)} km/h"
    }
}

private data class OpenMeteoResponse(
    @SerializedName("current_weather") val currentWeather: CurrentWeather?
)

private data class CurrentWeather(
    val temperature: Double,
    @SerializedName("weathercode") val weatherCode: Int,
    @SerializedName("windspeed") val windSpeed: Double,
    @SerializedName("is_day") val isDay: Int
)

class WeatherApiService {

    companion object {
        private val WMO_CODE_MAP = mapOf(
            0 to "Sonnig",
            1 to "Überwiegend sonnig",
            2 to "Leicht bewölkt",
            3 to "Bewölkt",
            45 to "Nebel",
            48 to "Nebel",
            51 to "Leichter Nieselregen",
            53 to "Nieselregen",
            55 to "Starker Nieselregen",
            56 to "Gefrierender Nieselregen",
            57 to "Gefrierender Nieselregen",
            61 to "Leichter Regen",
            63 to "Regen",
            65 to "Starker Regen",
            66 to "Gefrierender Regen",
            67 to "Gefrierender Regen",
            71 to "Leichter Schneefall",
            73 to "Schneefall",
            75 to "Starker Schneefall",
            77 to "Schneegriesel",
            80 to "Leichte Regenschauer",
            81 to "Regenschauer",
            82 to "Starke Regenschauer",
            85 to "Leichte Schneeschauer",
            86 to "Starke Schneeschauer",
            95 to "Gewitter",
            96 to "Gewitter mit Hagel",
            99 to "Gewitter mit starkem Hagel"
        )
    }

    private val gson = Gson()

    suspend fun fetchWeather(latitude: Double, longitude: Double): Result<WeatherResult> =
        withContext(Dispatchers.IO) {
            try {
                val url = URL(
                    "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current_weather=true"
                )
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.requestMethod = "GET"

                val responseCode = conn.responseCode
                if (responseCode != 200) {
                    return@withContext Result.failure(Exception("HTTP $responseCode"))
                }

                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()

                val response = gson.fromJson(body, OpenMeteoResponse::class.java)
                val current = response.currentWeather
                    ?: return@withContext Result.failure(Exception("No weather data"))

                val description = WMO_CODE_MAP[current.weatherCode] ?: "Unbekannt (${current.weatherCode})"

                Result.success(
                    WeatherResult(
                        temperature = current.temperature,
                        weatherCode = current.weatherCode,
                        weatherDescription = description,
                        windSpeed = current.windSpeed
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
