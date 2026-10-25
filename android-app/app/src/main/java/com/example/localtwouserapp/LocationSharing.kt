package com.example.localtwouserapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat

class LocationSharing(private val context: Context, private val publish: (LivePoint) -> Unit) : LocationListener {
    private val manager = context.getSystemService(LocationManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val samples = mutableMapOf<String, LocationSample>()
    private var delivery = LocationDelivery()
    private var running = false
    private val tick = Runnable { deliver() }
    private val refreshTimeout = Runnable {
        if (!running || !delivery.refreshing) return@Runnable
        finishRefresh()
        LocationService.locationStatus.value = "No fresh fix yet · check Precise location and try outdoors"
    }
    fun start() {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        require(fine || coarse) { "Allow location access to share your position" }
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { it in manager.allProviders && (fine || it == LocationManager.NETWORK_PROVIDER) }
        require(providers.isNotEmpty()) { "No location provider is available on this phone" }
        stop()
        // Register independently: a broken network provider must not disable working GPS.
        var registered = 0
        var lastError: Exception? = null
        providers.forEach { provider ->
            try {
                manager.requestLocationUpdates(provider, LocationTiming.SAMPLE_MS, 0f, this, Looper.getMainLooper())
                registered++
            } catch (e: SecurityException) { lastError = e
            } catch (e: IllegalArgumentException) { lastError = e }
        }
        if (registered == 0) throw lastError ?: IllegalStateException("No location provider is available")
        running = true
        handler.postDelayed(tick, LocationTiming.INTERVAL_MS)
        LocationService.locationStatus.value = if (fine) "Acquiring GPS · sharing about every 15 seconds"
            else "Approximate location only · enable Precise location in app permissions"
    }
    fun refresh(): Boolean {
        if (!running || !delivery.requestRefresh(SystemClock.elapsedRealtime())) return false
        LocationService.refreshingLocation.value = true
        LocationService.locationStatus.value = "Getting a fresh position…"
        handler.postDelayed(refreshTimeout, LocationTiming.REFRESH_TIMEOUT_MS)
        return true
    }
    fun onConnectionRestored() {
        if (!running) return
        // Only the most recent usable measurement can be retried; preserve its sensor time.
        delivery.reconnect()
        deliver()
    }
    override fun onLocationChanged(location: Location) {
        if (!running) return
        val elapsed = SystemClock.elapsedRealtime()
        val sampleTime = location.elapsedRealtimeNanos / 1_000_000
        if (!location.hasAccuracy() || !LocationTiming.usable(location.latitude, location.longitude, location.accuracy, elapsed - sampleTime)) return
        val provider = location.provider ?: "unknown"
        if (sampleTime <= (samples[provider]?.elapsedMs ?: -1)) return
        samples[provider] = LocationSample(LivePoint(location.latitude, location.longitude, location.accuracy, location.time), sampleTime)
        deliver()
    }
    private fun deliver() {
        if (!running) return
        if (!BackgroundLocation.hasBackgroundAccess(context)) {
            LocationService.locationStatus.value = "Location permission changed · open US to resume sharing"
            stop()
            LocationService.instance?.stopSelf()
            return
        }
        val now = SystemClock.elapsedRealtime()
        val sample = delivery.choose(samples.values, now)
        if (sample != null) {
            publish(sample.point)
            delivery.delivered(sample, now)
            if (!delivery.refreshing) finishRefresh()
            LocationService.locationStatus.value = "Sharing about every 15 seconds · accuracy ±${kotlin.math.ceil(sample.point.accuracy.toDouble()).toInt()} m" +
                if (sample.point.accuracy > 100f) " · weak accuracy; enable Precise location or try outdoors" else ""
        }
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, delivery.nextCheckIn(now))
    }
    private fun finishRefresh() {
        handler.removeCallbacks(refreshTimeout)
        delivery.finishRefresh()
        LocationService.refreshingLocation.value = false
    }
    override fun onProviderEnabled(provider: String) { if (running) LocationService.locationStatus.value = "Location enabled · waiting for a fresh position" }
    override fun onProviderDisabled(provider: String) {
        samples.remove(provider)
        if (running && samples.isEmpty()) LocationService.locationStatus.value = "Waiting for location · check that your phone's Location is on"
    }
    @Deprecated("Legacy platform callback")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    fun stop() {
        running = false
        handler.removeCallbacks(tick)
        // Permission can be revoked while sharing; cleanup must still finish.
        try { manager.removeUpdates(this) } catch (_: SecurityException) { }
        finishRefresh(); samples.clear(); delivery = LocationDelivery()
    }
}
