package com.divehub.app.util

import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Last known position + reverse geocoding for a short place label (user can edit in the form).
 */
object DiveLogLocationHelper {

    fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    fun getLastKnownLatLngOrNull(context: Context): Pair<Double, Double>? {
        if (!hasLocationPermission(context)) return null
        @Suppress("MissingPermission")
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val loc =
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        return loc?.let { it.latitude to it.longitude }
    }

    suspend fun reverseGeocodeShortLabel(context: Context, lat: Double, lon: Double): String? =
        withContext(Dispatchers.IO) {
            if (!Geocoder.isPresent()) return@withContext String.format(
                Locale.US,
                "%.4f, %.4f",
                lat,
                lon,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return@withContext reverseGeocode33(context, lat, lon)
            }
            @Suppress("DEPRECATION")
            val fromLegacy = runCatching {
                Geocoder(context, Locale.getDefault()).getFromLocation(lat, lon, 1)?.firstOrNull()
            }.getOrNull()?.let { formatAddressLine(it) }
            fromLegacy ?: String.format(Locale.US, "%.4f, %.4f", lat, lon)
        }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun reverseGeocode33(context: Context, lat: Double, lon: Double): String? =
        suspendCoroutine { cont ->
            val geocoder = Geocoder(context, Locale.getDefault())
            geocoder.getFromLocation(
                lat,
                lon,
                1,
                object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        val line = addresses.firstOrNull()?.let { formatAddressLine(it) }
                        cont.resume(line)
                    }

                    override fun onError(errorMessage: String?) {
                        cont.resume(null)
                    }
                },
            )
        }

    private fun formatAddressLine(a: Address): String? {
        if (a.maxAddressLineIndex >= 0) {
            val l0 = a.getAddressLine(0)
            if (!l0.isNullOrBlank()) return l0.trim()
        }
        val parts = listOfNotNull(
            a.featureName?.takeIf { it.isNotBlank() && it != a.subThoroughfare },
            a.locality,
            a.subLocality,
            a.adminArea,
        ).filter { it.isNotBlank() }
        if (parts.isNotEmpty()) return parts.first()
        a.countryName?.takeIf { it.isNotBlank() }?.let { return it }
        return null
    }
}
