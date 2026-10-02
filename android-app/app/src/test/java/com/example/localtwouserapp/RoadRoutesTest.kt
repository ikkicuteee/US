package com.example.localtwouserapp

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class RoadRoutesTest {
    private val from = LivePoint(latitude = 52.517037, longitude = 13.388860)
    private val to = LivePoint(latitude = 52.529407, longitude = 13.397634)
    private val sample = """{"code":"Ok","routes":[{"distance":1888.0,"duration":260.4,"geometry":{"type":"LineString","coordinates":[[13.388860,52.517037],[13.397634,52.529407]]}}]}"""
    @Test fun urlsSelectTransportProfileAndUseLongitudeFirstInAnyLocale() {
        val old = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertTrue(RoadRoutes.url(RouteMode.ROAD, from, to).contains("/routed-car/route/v1/driving/13.388860,52.517037;13.397634,52.529407?"))
            assertTrue(RoadRoutes.url(RouteMode.WALK, from, to).contains("/routed-foot/"))
        } finally { Locale.setDefault(old) }
    }
    @Test fun parseKeepsRoadDistanceDurationAndGeometry() {
        val result = RoadRoutes.parse(sample)
        assertEquals(1888.0, result.meters, .001)
        assertEquals(260.4, result.seconds, .001)
        assertEquals(RoutePosition(from.latitude, from.longitude), result.points.first())
        assertEquals("1.89 km", RoadRoutes.distance(result.meters))
        assertEquals("5 min", RoadRoutes.duration(result.seconds))
    }
    @Test(expected = IllegalStateException::class) fun missingRouteDoesNotBecomeStraightLineDistance() {
        RoadRoutes.parse("""{"code":"NoRoute","routes":[]}""")
    }
    @Test(expected = IllegalArgumentException::class) fun invalidGeometryIsRejected() {
        RoadRoutes.parse(sample.replace("52.517037", "92.0"))
    }
    @Test(expected = IllegalArgumentException::class) fun negativeRouteDistanceIsRejected() {
        RoadRoutes.parse(sample.replace("1888.0", "-1"))
    }
    @Test fun movementRefreshesEitherEndpointButNeverBeforeOneMinute() {
        val previous = RouteAttempt(from, to, 1000, false)
        val moved = from.copy(latitude = from.latitude + .001)
        assertFalse(RouteRefresh.needed(previous, moved, to, 60_999))
        assertTrue(RouteRefresh.needed(previous, moved, to, 61_000))
        assertTrue(RouteRefresh.needed(previous, from, moved, 61_000))
        assertFalse(RouteRefresh.needed(previous, from.copy(latitude = from.latitude + .00001), to, 61_000))
    }
    @Test fun failedRequestsBackOffAndStationaryPhonesReuseCachedRoute() {
        val failed = RouteAttempt(from, to, 1000, true)
        assertFalse(RouteRefresh.needed(failed, from, to, 120_999))
        assertTrue(RouteRefresh.needed(failed, from, to, 121_000))
        val previous = failed.copy(failed = false)
        assertFalse(RouteRefresh.needed(previous, from, to, 599_999))
        assertTrue(RouteRefresh.needed(previous, from, to, 601_000))
        assertTrue(RouteRefresh.needed(null, from, to, 0))
    }
}
