package com.uip.oneapp.network

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.util.Log
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class LocationService(private val context: Context) {

    companion object {
        private const val TAG = "LocationService"
    }

    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Result<Pair<Double, Double>> = withContext(Dispatchers.IO) {
        try {
            // Try last location first (fast, often cached)
            val lastLocation: Location? = try {
                fusedClient.lastLocation.await()
            } catch (e: Exception) {
                Log.d(TAG, "lastLocation failed: ${e.message}")
                null
            }

            if (lastLocation != null) {
                Log.d(TAG, "Using lastLocation: ${lastLocation.latitude}, ${lastLocation.longitude}")
                return@withContext Result.success(lastLocation.latitude to lastLocation.longitude)
            }

            // Request fresh location with balanced priority
            Log.d(TAG, "No lastLocation, requesting fresh location...")
            val cancellationToken = CancellationTokenSource()

            val freshLocation: Location? = withTimeoutOrNull(15_000L) {
                try {
                    fusedClient.getCurrentLocation(
                        Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                        cancellationToken.token
                    ).await()
                } catch (e: Exception) {
                    Log.e(TAG, "getCurrentLocation failed: ${e.message}")
                    null
                }
            }

            if (freshLocation != null) {
                Log.d(TAG, "Fresh location: ${freshLocation.latitude}, ${freshLocation.longitude}")
                Result.success(freshLocation.latitude to freshLocation.longitude)
            } else {
                cancellationToken.cancel()
                Log.e(TAG, "No location available")
                Result.failure(Exception("Could not determine location"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Location error: ${e.message}", e)
            Result.failure(e)
        }
    }
}
