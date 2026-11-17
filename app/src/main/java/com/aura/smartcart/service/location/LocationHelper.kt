package com.aura.smartcart.service.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

data class Place(val lat: Double, val lng: Double, val address: String)

/** Where the user is when saving a bill, to remember which shop/market it was. */
class LocationHelper(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun current(): Place? {
        if (!hasPermission()) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        val location = withTimeoutOrNull(8_000) {
            try {
                client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, CancellationTokenSource().token).await()
                    ?: client.lastLocation.await()
            } catch (_: Exception) {
                null
            }
        } ?: return null
        return Place(location.latitude, location.longitude, addressOf(location.latitude, location.longitude))
    }

    @Suppress("DEPRECATION")
    private suspend fun addressOf(lat: Double, lng: Double): String = withContext(Dispatchers.IO) {
        try {
            val a = Geocoder(context, Locale.getDefault()).getFromLocation(lat, lng, 1)?.firstOrNull()
                ?: return@withContext ""
            listOfNotNull(a.subLocality, a.locality ?: a.subAdminArea, a.countryName)
                .distinct()
                .joinToString(", ")
        } catch (_: Exception) {
            ""
        }
    }
}
