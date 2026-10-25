package com.example.localtwouserapp

import org.junit.Assert.*
import org.junit.Test

class LocationQualityTest {
    private fun sample(accuracy: Float, time: Long) = LocationSample(LivePoint(10.0, 122.0, accuracy, 1L), time)

    @Test fun recentGpsWinsOverRoughNetworkFix() {
        val gps = sample(6f, 9_000)
        assertEquals(gps, LocationQuality.best(listOf(gps, sample(900f, 10_000)), 10_000))
    }
    @Test fun oldAccurateFixDoesNotPinMovingPerson() {
        val moving = sample(40f, 10_000)
        assertEquals(moving, LocationQuality.best(listOf(sample(3f, 4_000), moving), 10_000))
    }
    @Test fun staleFutureAndUnknownAccuracyFixesAreRejected() {
        assertNull(LocationQuality.best(listOf(sample(3f, 1), sample(5f, 20_001),
            sample(0f, 20_000), sample(Float.NaN, 20_000), sample(100_001f, 20_000)), 20_000))
        assertFalse(LocationTiming.usable(Double.NaN, 122.0, 10f, 0))
    }
    @Test fun manualRefreshRequiresSampleAfterTap() {
        assertNull(LocationQuality.best(listOf(sample(3f, 9_999), sample(3f, 10_000)), 10_100, 10_000))
        assertNotNull(LocationQuality.best(listOf(sample(10f, 10_001)), 10_100, 10_000))
    }
    @Test fun majorImprovementCanBypassUploadInterval() {
        assertTrue(LocationQuality.improved(sample(900f, 1), sample(8f, 2)))
        assertFalse(LocationQuality.improved(sample(8f, 1), sample(7f, 2)))
        assertFalse(LocationQuality.improved(sample(900f, 2), sample(8f, 1)))
    }
    @Test fun sameSampleIsNotRepublishedAsFresh() {
        assertNull(LocationQuality.best(listOf(sample(10f, 10_000)), 12_000, 10_000))
        assertEquals(15_000L, LocationTiming.INTERVAL_MS)
    }
}
