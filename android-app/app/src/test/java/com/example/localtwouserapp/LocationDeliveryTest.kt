package com.example.localtwouserapp

import org.junit.Assert.*
import org.junit.Test

class LocationDeliveryTest {
    private fun sample(time: Long, accuracy: Float = 200f) = LocationSample(LivePoint(10.0, 122.0, accuracy, time), time)
    @Test fun roughFixStillUploadsEveryFifteenSeconds() {
        val delivery = LocationDelivery()
        for (time in 0L..60_000L step 5_000L) {
            val chosen = delivery.choose(listOf(sample(time)), time)
            assertEquals(time % 15_000L == 0L, chosen != null)
            if (chosen != null) delivery.delivered(chosen, time)
        }
    }
    @Test fun manualFixDoesNotMoveRegularDeadline() {
        val delivery = LocationDelivery()
        delivery.delivered(sample(0), 0)
        assertTrue(delivery.requestRefresh(9_000))
        assertFalse(delivery.requestRefresh(9_001))
        val manual = delivery.choose(listOf(sample(10_000)), 10_000)!!
        delivery.delivered(manual, 10_000)
        assertFalse(delivery.refreshing)
        assertEquals(5_000L, delivery.nextCheckIn(10_000))
        assertNotNull(delivery.choose(listOf(sample(15_000)), 15_000))
    }
    @Test fun timeoutDoesNotCancelScheduledUploads() {
        val delivery = LocationDelivery()
        delivery.delivered(sample(0), 0)
        delivery.requestRefresh(1_000)
        delivery.finishRefresh()
        assertNotNull(delivery.choose(listOf(sample(15_000)), 15_000))
    }
    @Test fun improvedGpsCanSendEarlyWithoutDelayingNextRegularUpload() {
        val delivery = LocationDelivery()
        delivery.delivered(sample(0, 900f), 0)
        val accurate = delivery.choose(listOf(sample(5_000, 6f)), 5_000)!!
        delivery.delivered(accurate, 5_000)
        assertNotNull(delivery.choose(listOf(sample(15_000, 6f)), 15_000))
    }
    @Test fun missingFixDoesNotRepublishOldCoordinatesAsLive() {
        val delivery = LocationDelivery()
        delivery.delivered(sample(0), 0)
        assertNull(delivery.choose(listOf(sample(0)), 15_000))
        assertNull(delivery.choose(listOf(sample(0)), 30_000))
        assertNotNull(delivery.choose(listOf(sample(30_000)), 30_000))
    }
    @Test fun reconnectRetriesOnlyFreshMeasurement() {
        val delivery = LocationDelivery()
        delivery.delivered(sample(1_000), 1_000)
        delivery.reconnect()
        assertEquals(1_000L, delivery.choose(listOf(sample(1_000)), 10_000)!!.point.time)
        assertNull(delivery.choose(listOf(sample(1_000)), 30_000))
    }
    @Test fun delayedUploadDoesNotResetDisplayedMeasurementAge() {
        val delayed = LivePoint(time = 1_000, updatedAt = 60_000)
        assertEquals(1_000L, MapDistance.measuredAt(delayed))
        assertFalse(MapDistance.fresh(delayed, 70_000))
        assertTrue(MapDistance.fresh(delayed.copy(time = 65_000, updatedAt = 66_000), 70_000))
    }
    @Test fun overdueRegularFixCanUploadWhileManualRefreshWaitsForNewSample() {
        val delivery = LocationDelivery()
        delivery.delivered(sample(0), 0)
        delivery.requestRefresh(15_000)
        val periodic = delivery.choose(listOf(sample(14_000)), 15_000)!!
        delivery.delivered(periodic, 15_000)
        assertTrue(delivery.refreshing)
        assertNotNull(delivery.choose(listOf(sample(16_000)), 16_000))
    }
}
