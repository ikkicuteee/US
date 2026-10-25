package com.example.localtwouserapp

import android.Manifest
import android.app.Application
import android.location.Location
import android.os.Looper
import android.os.SystemClock
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LocationSharingTest {
    private fun fix() = Location("gps").apply {
        latitude = 10.0; longitude = 122.0; accuracy = 200f
        time = System.currentTimeMillis(); elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
    }
    @Test fun refreshWithPoorAccuracyCompletesAndStopCancelsCallbacks() {
        val app: Application = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        val published = mutableListOf<LivePoint>()
        val sharing = LocationSharing(app) { published += it }
        try {
            sharing.start()
            sharing.onLocationChanged(fix())
            assertEquals(1, published.size)
            assertTrue(sharing.refresh())
            assertFalse(sharing.refresh())
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
            sharing.onLocationChanged(fix())
            assertEquals(2, published.size)
            assertFalse(LocationService.refreshingLocation.value)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
            sharing.onLocationChanged(fix())
            assertEquals(3, published.size)
            sharing.stop()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30))
            sharing.onLocationChanged(fix())
            assertFalse(sharing.refresh())
            assertEquals(3, published.size)
        } finally { sharing.stop() }
    }
    @Test fun timeoutLeavesRegularSharingRunning() {
        val app: Application = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        val published = mutableListOf<LivePoint>()
        val sharing = LocationSharing(app) { published += it }
        try {
            sharing.start()
            sharing.onLocationChanged(fix())
            assertTrue(sharing.refresh())
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(15))
            assertFalse(LocationService.refreshingLocation.value)
            assertEquals(1, published.size)
            sharing.onLocationChanged(fix())
            assertEquals(2, published.size)
        } finally { sharing.stop() }
    }
}
