package com.example.localtwouserapp

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Persist consent, never coordinates. Only the phone owner can enable sharing. */
object BackgroundLocation {
    private const val ENABLED = "background_location_enabled"
    const val START_SHARING = "us.start_location_sharing"
    fun requested(context: Context) = context.getSharedPreferences("us_pair", Context.MODE_PRIVATE).getBoolean(ENABLED, false)
    fun remember(context: Context, enabled: Boolean) {
        context.getSharedPreferences("us_pair", Context.MODE_PRIVATE).edit().putBoolean(ENABLED, enabled).apply()
    }
    fun hasLocation(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    fun hasBackgroundAccess(context: Context): Boolean = hasLocation(context) &&
        (Build.VERSION.SDK_INT < 29 || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED)
    fun canRestore(context: Context) = requested(context) && hasBackgroundAccess(context) && SharedStore.get(context).configured
    fun startFromScreen(context: Context) {
        check(SharedStore.get(context).configured) { "Sign in and pair with your partner in Settings first." }
        check(hasBackgroundAccess(context)) { "Choose Allow all the time for location access." }
        ContextCompat.startForegroundService(context, Intent(context, LocationService::class.java).setAction(START_SHARING))
    }
    fun recordStatus(context: Context, enabled: Boolean, reason: String = "") {
        val store = SharedStore.get(context)
        if (!store.configured) return
        val id = "locationstatus-${store.deviceId}"
        val current = store.entries.value.firstOrNull { it.id == id }
        if (current?.fields?.get("sharing") == enabled.toString() && current.text == reason.trim()) return
        store.put(Entry(id = id, type = "locationstatus", text = reason.trim().take(200), fields = mapOf("sharing" to enabled.toString())))
    }
    fun stop(context: Context) {
        remember(context, false)
        val service = LocationService.instance
        if (service != null) service.shareLocation(false)
        else {
            // Explicit stop/sign-out must remove the last cloud position even if Android
            // has already stopped the location service.
            FirebaseSync.instance?.clearLocation()
            LocationService.ownLocation.value = null
            LocationService.sharingLocation.value = false
            LocationService.locationStatus.value = "Off · your location stays private"
            recordStatus(context, false)
        }
    }
}

/** Boot/package replacement are Android exemptions for restoring this opted-in service. */
class LocationRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) return
        if (!BackgroundLocation.canRestore(context)) return
        try {
            ContextCompat.startForegroundService(context, Intent(context, LocationService::class.java))
        } catch (_: IllegalStateException) {
            Notices.channels(context)
            Notices.show(context, "Resume location sharing", "Open US to resume sharing your location with ${SharedStore.get(context).partnerName}.")
        } catch (_: SecurityException) {
            Notices.channels(context)
            Notices.show(context, "Location sharing paused", "Open US and check your location permission.")
        }
    }
}
