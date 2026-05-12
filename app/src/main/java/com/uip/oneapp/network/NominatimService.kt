package com.uip.oneapp.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class NominatimService {

    /** Forward geocode: text query (street, place name) → first matching (lat, lon, displayName). */
    suspend fun searchAddress(query: String): Result<Triple<Double, Double, String>> =
        withContext(Dispatchers.IO) {
            try {
                val q = URLEncoder.encode(query.trim(), "UTF-8")
                val url = URL(
                    "https://nominatim.openstreetmap.org/search?format=json&q=$q&limit=1&accept-language=de"
                )
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "DrainQ ONE/1.0")

                val code = conn.responseCode
                if (code != 200) return@withContext Result.failure(Exception("HTTP $code"))

                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()

                val arr = JSONArray(body)
                if (arr.length() == 0) {
                    return@withContext Result.failure(Exception("NO_MATCH"))
                }
                val first = arr.getJSONObject(0)
                val lat = first.getString("lat").toDouble()
                val lon = first.getString("lon").toDouble()
                val display = first.optString("display_name")
                Result.success(Triple(lat, lon, display))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun reverseGeocode(lat: Double, lon: Double): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = URL(
                "https://nominatim.openstreetmap.org/reverse?format=json&lat=$lat&lon=$lon&zoom=18&accept-language=de"
            )
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "DrainQ ONE/1.0")

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
