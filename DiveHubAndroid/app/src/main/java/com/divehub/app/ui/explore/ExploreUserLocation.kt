package com.divehub.app.ui.explore

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

fun hasExploreLocationPermission(context: Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    return fine || coarse
}

fun getLastKnownLocationForExplore(context: Context): Pair<Double, Double>? {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    val providers = runCatching { lm.getProviders(true) }.getOrDefault(emptyList())
    val best = providers.mapNotNull { provider -> runCatching { lm.getLastKnownLocation(provider) }.getOrNull() }
        .maxByOrNull { it.accuracy }
    return best?.let { it.latitude to it.longitude }
}

/**
 * Непрерывные обновления положения (как CLLocationManager на iOS) для списка/карты «Исследовать».
 */
@Composable
fun rememberUserLatLngForMap(): Pair<Double, Double>? {
    val context = LocalContext.current
    val granted = hasExploreLocationPermission(context)
    var state by remember(granted) { mutableStateOf(if (granted) getLastKnownLocationForExplore(context) else null) }
    DisposableEffect(context, granted) {
        if (!granted) {
            state = null
            return@DisposableEffect onDispose { }
        }
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (lm == null) {
            return@DisposableEffect onDispose { }
        }
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                state = location.latitude to location.longitude
            }
        }
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (runCatching { lm.isProviderEnabled(LocationManager.FUSED_PROVIDER) }.getOrDefault(false)) {
                    add(LocationManager.FUSED_PROVIDER)
                }
            }
            if (runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)) {
                add(LocationManager.GPS_PROVIDER)
            }
            if (runCatching { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)) {
                add(LocationManager.NETWORK_PROVIDER)
            }
        }
        for (p in providers) {
            runCatching {
                lm.requestLocationUpdates(p, 2_000L, 15f, listener, Looper.getMainLooper())
            }
        }
        onDispose {
            runCatching { lm.removeUpdates(listener) }
        }
    }
    return state
}
