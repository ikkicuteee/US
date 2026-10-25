package com.example.localtwouserapp

object LocationTiming {
    const val INTERVAL_MS = 15_000L
    const val SAMPLE_MS = 5_000L
    const val STALE_MS = 60_000L
    const val REFRESH_TIMEOUT_MS = 15_000L
    fun usable(latitude: Double, longitude: Double, accuracy: Float, ageMs: Long) =
        latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
            accuracy.isFinite() && accuracy > 0f && accuracy <= 100_000f && ageMs in 0..15_000
}

/** Manual refresh never postpones the regular delivery deadline. Monotonic times only. */
class LocationDelivery {
    private var nextDue = 0L
    private var deliveredAt = -1L
    private var previous: LocationSample? = null
    private var refreshAfter: Long? = null
    val refreshing get() = refreshAfter != null
    fun requestRefresh(now: Long): Boolean {
        if (refreshing) return false
        refreshAfter = now
        return true
    }
    fun finishRefresh() { refreshAfter = null }
    fun choose(samples: Collection<LocationSample>, now: Long): LocationSample? {
        val next = LocationQuality.best(samples, now, deliveredAt) ?: return null
        val requested = refreshAfter
        return next.takeIf { now >= nextDue || LocationQuality.improved(previous, it) ||
            (requested != null && it.elapsedMs > requested) }
    }
    fun delivered(sample: LocationSample, now: Long) {
        if (now >= nextDue) nextDue = now + LocationTiming.INTERVAL_MS
        deliveredAt = sample.elapsedMs
        previous = sample
        if (refreshAfter?.let { sample.elapsedMs > it } == true) finishRefresh()
    }
    fun reconnect() { nextDue = 0L; deliveredAt = -1L; previous = null }
    fun nextCheckIn(now: Long) = (nextDue - now).takeIf { it > 0 } ?: LocationTiming.SAMPLE_MS
}

data class LocationSample(val point: LivePoint, val elapsedMs: Long)

object LocationQuality {
    // Prefer accuracy among recent fixes, but never hold an old fix over a moving user.
    fun best(samples: Collection<LocationSample>, now: Long, after: Long = -1L): LocationSample? {
        val valid = samples.filter { it.elapsedMs > after && LocationTiming.usable(
            it.point.latitude, it.point.longitude, it.point.accuracy, now - it.elapsedMs) }
        val recent = valid.filter { now - it.elapsedMs <= 5_000 }
        return (recent.ifEmpty { valid }).minWithOrNull(
            compareBy<LocationSample> { it.point.accuracy }.thenByDescending { it.elapsedMs })
    }
    fun improved(previous: LocationSample?, next: LocationSample): Boolean = previous != null &&
        next.elapsedMs > previous.elapsedMs && previous.point.accuracy > 25f &&
        next.point.accuracy <= previous.point.accuracy / 2
}
