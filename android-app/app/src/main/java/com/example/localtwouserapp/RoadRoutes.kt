package com.example.localtwouserapp

import com.google.gson.JsonParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

enum class RouteMode(val label: String, val server: String) { ROAD("Road", "car"), WALK("Walk", "foot") }
data class RoutePosition(val latitude: Double, val longitude: Double)
data class RoadRoute(val meters: Double, val seconds: Double, val points: List<RoutePosition>)
data class RouteAttempt(val from: LivePoint, val to: LivePoint, val elapsed: Long, val failed: Boolean)

object RouteRefresh {
    const val MIN_INTERVAL = 60_000L
    const val RETRY_INTERVAL = 120_000L
    fun moved(a: LivePoint, b: LivePoint) = MapDistance.meters(a, b) >= 30.0
    fun needed(previous: RouteAttempt?, from: LivePoint, to: LivePoint, elapsed: Long): Boolean {
        if (previous == null) return true
        if (elapsed - previous.elapsed < if (previous.failed) RETRY_INTERVAL else MIN_INTERVAL) return false
        return previous.failed || moved(previous.from, from) || moved(previous.to, to) || elapsed - previous.elapsed >= 600_000
    }
}

/** Public FOSSGIS OSRM endpoints. No coordinates or routes are stored on disk. */
object RoadRoutes {
    private val gate = Mutex()
    private var lastRequestNanos = 0L
    private val worker = Executors.newSingleThreadExecutor { task -> Thread(task, "us-route-request").apply { isDaemon = true } }
    fun url(mode: RouteMode, from: LivePoint, to: LivePoint): String {
        require(listOf(from, to).all { it.latitude.isFinite() && it.longitude.isFinite() && it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 })
        val coordinates = String.format(Locale.ROOT, "%.6f,%.6f;%.6f,%.6f", from.longitude, from.latitude, to.longitude, to.latitude)
        return "https://routing.openstreetmap.de/routed-${mode.server}/route/v1/driving/$coordinates?overview=simplified&geometries=geojson&steps=false&alternatives=false&radiuses=200;200&generate_hints=false"
    }
    fun parse(json: String): RoadRoute {
        val root = JsonParser.parseString(json).asJsonObject
        val code = root.get("code")?.asString
        check(code == "Ok") { if (code in listOf("NoRoute", "NoSegment")) "No mapped route connects these locations." else "The routing service could not calculate this route." }
        val route = root.getAsJsonArray("routes")?.firstOrNull()?.asJsonObject ?: error("No mapped route connects these locations.")
        val meters = route.get("distance").asDouble
        val seconds = route.get("duration").asDouble
        require(meters.isFinite() && meters >= 0 && seconds.isFinite() && seconds >= 0) { "Invalid route estimate." }
        val coordinates = route.getAsJsonObject("geometry").getAsJsonArray("coordinates")
        require(coordinates.size() in 2..20_000) { "Unsupported route size." }
        val points = coordinates.map { value ->
            val pair = value.asJsonArray
            val lon = pair[0].asDouble; val lat = pair[1].asDouble
            require(lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0) { "Invalid route geometry." }
            RoutePosition(lat, lon)
        }
        return RoadRoute(meters, seconds, points)
    }
    suspend fun fetch(mode: RouteMode, from: LivePoint, to: LivePoint): RoadRoute = gate.withLock {
        // Mode switches and re-entering the screen also respect the provider's rate limit.
        if (lastRequestNanos != 0L) delay((2_000 - (System.nanoTime() - lastRequestNanos) / 1_000_000).coerceAtLeast(0))
        lastRequestNanos = System.nanoTime()
        withTimeout(15_000) {
            suspendCancellableCoroutine { continuation ->
                val connection = URL(url(mode, from, to)).openConnection() as HttpURLConnection
                connection.connectTimeout = 8_000; connection.readTimeout = 8_000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("User-Agent", "USCoupleApp/3.5 (Android; private two-person app)")
                connection.setRequestProperty("Accept", "application/json")
                continuation.invokeOnCancellation { connection.disconnect() }
                worker.execute {
                    if (!continuation.isActive) { connection.disconnect(); return@execute }
                    try {
                        check(connection.responseCode == 200) { "Routing is temporarily unavailable. Retrying in two minutes." }
                        val bytes = connection.inputStream.use { input ->
                            val output = java.io.ByteArrayOutputStream()
                            val chunk = ByteArray(8192)
                            while (continuation.isActive) {
                                val count = input.read(chunk)
                                if (count < 0) break
                                check(output.size() + count <= 1_000_000) { "Route response is too large." }
                                output.write(chunk, 0, count)
                            }
                            output.toByteArray()
                        }
                        if (continuation.isActive) continuation.resumeWith(Result.success(parse(bytes.toString(Charsets.UTF_8))))
                    } catch (e: Exception) {
                        if (continuation.isActive) continuation.resumeWith(Result.failure(e))
                    } finally { connection.disconnect() }
                }
            }
        }
    }
    fun distance(meters: Double) = if (meters < 1000) "${meters.toInt()} m" else String.format(Locale.ROOT, "%.2f km", meters / 1000)
    fun duration(seconds: Double): String {
        val minutes = kotlin.math.ceil(seconds / 60).toLong().coerceAtLeast(1)
        return if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"
    }
}
