package com.example.localtwouserapp

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.flow.MutableStateFlow

/** Runs only while the user has explicitly enabled location sharing. */
class LocationService : Service() {
    private var location: LocationSharing? = null
    private lateinit var sync: FirebaseSync
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        instance = this
        Notices.channels(this)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "stop_location") { shareLocation(false); return START_NOT_STICKY }
        if (!BackgroundLocation.hasBackgroundAccess(this) || !SharedStore.get(this).configured) {
            stopSelf(); return START_NOT_STICKY
        }
        sync = FirebaseSync.get(this)
        try {
            ServiceCompat.startForeground(this, 17, Notices.ongoing(this),
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0)
            if (location == null) {
                location = LocationSharing(this) { point ->
                    ownLocation.value = point
                    sync.publishLocation(point)
                }.also { it.start() }
            }
            sharingLocation.value = true
            BackgroundLocation.remember(this, true)
            BackgroundLocation.recordStatus(this, true)
            locationStatus.value = "Sharing live · aiming for an update every 20 seconds"
        } catch (e: Exception) {
            locationStatus.value = "Location paused: ${e.localizedMessage}"
            BackgroundLocation.remember(this, false)
            stopSelf()
        }
        return if (BackgroundLocation.requested(this)) START_STICKY else START_NOT_STICKY
    }
    fun shareLocation(enabled: Boolean) {
        if (enabled) return
        BackgroundLocation.remember(this, false)
        location?.stop(); location = null
        sharingLocation.value = false; ownLocation.value = null
        FirebaseSync.instance?.clearLocation()
        BackgroundLocation.recordStatus(this, false)
        locationStatus.value = "Off · your location stays private"
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }
    override fun onDestroy() {
        location?.stop(); location = null
        sharingLocation.value = false; ownLocation.value = null
        instance = null
        super.onDestroy()
    }
    companion object {
        @Volatile var instance: LocationService? = null
        val sharingLocation = MutableStateFlow(false)
        val ownLocation = MutableStateFlow<LivePoint?>(null)
        val locationStatus = MutableStateFlow("Off · your location stays private")
    }
}

