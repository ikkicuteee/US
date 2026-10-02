package com.example.localtwouserapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat

object LocationTiming {
    const val INTERVAL_MS = 20_000L
    const val STALE_MS = 60_000L
    fun usable(latitude: Double, longitude: Double, accuracy: Float, ageMs: Long) =
        latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
            accuracy.isFinite() && accuracy >= 0f && ageMs in 0..15_000
}

class LocationSharing(private val context: Context, private val publish: (LivePoint) -> Unit) : LocationListener {
    private val manager = context.getSystemService(LocationManager::class.java)
    private var lastSent = -LocationTiming.INTERVAL_MS
    fun start() {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        require(fine || coarse) { "Allow location access to share your position" }
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { it in manager.allProviders && (fine || it == LocationManager.NETWORK_PROVIDER) }
        require(providers.isNotEmpty()) { "No location provider is available on this phone" }
        manager.removeUpdates(this)
        // Zero distance threshold keeps the timestamp current even while standing still.
        // Android/provider delivery can be slower than the requested interval.
        providers.forEach { manager.requestLocationUpdates(it, LocationTiming.INTERVAL_MS, 0f, this, Looper.getMainLooper()) }
        providers.mapNotNull { manager.getLastKnownLocation(it) }
            .filter { SystemClock.elapsedRealtime() - it.elapsedRealtimeNanos / 1_000_000 in 0..15_000 }
            .minByOrNull { it.accuracy }?.let { onLocationChanged(it) }
    }
    override fun onLocationChanged(location: Location) {
        val elapsed = SystemClock.elapsedRealtime()
        val age = elapsed - location.elapsedRealtimeNanos / 1_000_000
        if (!LocationTiming.usable(location.latitude, location.longitude, location.accuracy, age)) return
        if (elapsed - lastSent < LocationTiming.INTERVAL_MS) return
        lastSent = elapsed
        publish(LivePoint(location.latitude, location.longitude, location.accuracy, location.time))
    }
    override fun onProviderEnabled(provider: String) { LocationService.locationStatus.value = "Location enabled · waiting for a fresh position" }
    override fun onProviderDisabled(provider: String) {
        if (manager.getProviders(true).none { it in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER) })
            LocationService.locationStatus.value = "Turn on your phone's Location to resume updates"
    }
    @Deprecated("Legacy platform callback")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    fun stop() { manager.removeUpdates(this); lastSent = -LocationTiming.INTERVAL_MS }
}

