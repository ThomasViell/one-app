package com.uip.oneapp.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class NominatimService {

    suspend fun reverseGeocode(lat: Double, lon: Double): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = URL(
                "https://nominatim.openstreetmap.org/reverse?format=json&lat=$lat&lon=$lon&zoom=18&accept-language=de"
            )
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "ONE.APP/1.0")

            val responseCode = conn.responseCode
            if (responseCode != 200) {
                return@withContext Result.failure(Exception("HTTP $responseCode"))
            }

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val json = JSONObject(body)

            // Try to build a clean address from address components
            val address = json.optJSONObject("address")
            val formatted = if (address != null) {
                val road = address.optString("road").ifEmpty { address.optString("pedestrian") }
                val houseNumber = address.optString("house_number")
                val postcode = address.optString("postcode")
                val city = address.optString("city").ifEmpty {
                    address.optString("town").ifEmpty {
                        address.optString("village").ifEmpty {
                            address.optString("municipality")
                        }
                    }
                }

                buildString {
                    if (road.isNotEmpty()) {
                        append(road)
                        if (houseNumber.isNotEmpty()) append(" $houseNumber")
                    }
                    if (city.isNotEmpty()) {
                        if (isNotEmpty()) append(", ")
                        if (postcode.isNotEmpty()) append("$postcode ")
                        append(city)
                    }
                }
            } else ""

            // Fall back to display_name if we couldn't build address
            val result = formatted.ifEmpty {
                json.optString("display_name").ifEmpty { "$lat, $lon" }
            }

            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
