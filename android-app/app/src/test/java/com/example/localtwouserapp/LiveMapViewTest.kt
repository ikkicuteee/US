package com.example.localtwouserapp

import android.view.View
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LiveMapViewTest {
    private fun map() = LiveMapView(RuntimeEnvironment.getApplication()).apply { setUseDataConnection(false) }
    private val own = LivePoint(10.0, 122.0, 6f, 1)
    private val partner = LivePoint(10.1, 122.1, 200f, 1)
    private fun size(map: LiveMapView, height: Int) {
        map.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        map.layout(0, 0, 360, height)
    }
    @Test fun refreshAfterTemporaryWindowDetachKeepsMapAlive() {
        val map = map()
        try {
            size(map, 330)
            map.updateLocations(own, partner, "Partner", null)
            ReflectionHelpers.callInstanceMethod<Unit>(map, "onDetachedFromWindow")
            map.updateLocations(own.copy(latitude = 10.02), partner, "Partner", null)
            map.fitLocations(false)
            assertNotNull(map.tileProvider)
            assertTrue(map.zoomLevelDouble.isFinite())
        } finally { map.release() }
    }
    @Test fun repeatedRefreshKeepsOnlyThreeOverlayInstances() {
        val map = map()
        try {
            size(map, 330)
            val original = map.overlays.toList()
            repeat(1_000) { index ->
                map.updateLocations(own.copy(latitude = 10.0 + index / 100_000.0), partner, "Partner", null)
                assertEquals(3, map.overlays.size)
                original.forEachIndexed { i, overlay -> assertSame(overlay, map.overlays[i]) }
            }
            map.updateLocations(null, null, "Partner", null)
            assertTrue(map.overlays.none { it.isEnabled })
        } finally { map.release() }
    }
    @Test fun fitBeforeLayoutAndInTinyViewportUsesFiniteZoom() {
        val map = map()
        try {
            map.updateLocations(own, partner, "Partner", null)
            map.fitLocations(true)
            size(map, 40)
            map.fitLocations(true)
            assertTrue(map.zoomLevelDouble.isFinite())
            assertTrue(map.zoomLevelDouble >= 0)
        } finally { map.release() }
    }
    @Test fun lateUpdatesAfterReleaseAreIgnoredAndReleaseIsIdempotent() {
        val map = map()
        map.release()
        map.release()
        map.updateLocations(own, partner, "Partner", null)
        map.fitLocations(false)
    }
}
