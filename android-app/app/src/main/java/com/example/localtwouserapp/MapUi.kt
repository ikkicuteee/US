package com.example.localtwouserapp

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

private data class CachedRoute(val mode: RouteMode, val route: RoadRoute, val attempt: RouteAttempt, val calculatedAt: Long)

@Composable fun MapPage(notify: (String) -> Unit, fullScreen: Boolean = false, setFullScreen: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    val sync = remember { FirebaseSync.get(context) }
    val store = remember { SharedStore.get(context) }
    val own by LocationService.ownLocation.collectAsStateWithLifecycle()
    val partner by sync.partnerLocation.collectAsStateWithLifecycle()
    val entries by store.entries.collectAsStateWithLifecycle()
    val partnerName = PartnerNames.from(entries, store.deviceId)
    val stopped = entries.firstOrNull { it.type == "locationstatus" && it.author != store.deviceId && it.fields["sharing"] == "false" }
    val visiblePartner = partner.takeUnless { stopped != null }
    var now by remember { mutableLongStateOf(sync.serverTime()) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) { owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        while (true) { now = sync.serverTime(); delay(1_000) }
    } }
    var modeName by rememberSaveable { mutableStateOf("off") }
    val mode = RouteMode.entries.firstOrNull { it.name == modeName }
    val ready = own?.let { MapDistance.fresh(it, now) } == true && visiblePartner?.let { MapDistance.fresh(it, now) } == true
    val latestOwn by rememberUpdatedState(own)
    val latestPartner by rememberUpdatedState(visiblePartner)
    val cache = remember { mutableMapOf<RouteMode, CachedRoute>() }
    val attempts = remember { mutableMapOf<RouteMode, RouteAttempt>() }
    var route by remember { mutableStateOf<CachedRoute?>(null) }
    var loading by remember { mutableStateOf(false) }
    var routeError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(mode, ready, owner) {
        route = if (mode != null && ready) cache[mode] else null
        routeError = null
        if (own == null || visiblePartner == null) { cache.clear(); attempts.clear() }
        if (mode == null || !ready) return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val from = latestOwn; val to = latestPartner
                val elapsed = SystemClock.elapsedRealtime()
                if (from != null && to != null && MapDistance.fresh(from, sync.serverTime()) && MapDistance.fresh(to, sync.serverTime()) && RouteRefresh.needed(attempts[mode], from, to, elapsed)) {
                    val attempt = RouteAttempt(from, to, elapsed, false)
                    attempts[mode] = attempt
                    loading = true; routeError = null
                    try {
                        val result = RoadRoutes.fetch(mode, from, to)
                        CachedRoute(mode, result, attempt, sync.serverTime()).also { cache[mode] = it; route = it }
                    } catch (_: TimeoutCancellationException) {
                        attempts[mode] = attempt.copy(failed = true); routeError = "Route request timed out. Retrying in two minutes."
                    } catch (e: CancellationException) {
                        // A cancelled request must be retried when the map is visible again.
                        attempts.remove(mode); throw e
                    } catch (_: Exception) {
                        attempts[mode] = attempt.copy(failed = true)
                        routeError = "Route unavailable here or offline. Retrying in two minutes."
                    } finally { loading = false }
                }
                delay(5_000)
            }
        }
    }
    // Never draw a route after either person stops sharing, or label stale coordinates as live.
    val shownRoute = route.takeIf { mode != null && ready && it?.mode == mode }
    val map = remember {
        Configuration.getInstance().apply {
            userAgentValue = "USCoupleApp/3.5 (Android; private two-person map)"
            osmdroidBasePath = java.io.File(context.cacheDir, "maps")
            osmdroidTileCache = java.io.File(context.cacheDir, "maps/tiles")
        }
        MapView(context).apply { setTileSource(TileSourceFactory.MAPNIK); setMultiTouchControls(true); controller.setZoom(2.0); controller.setCenter(GeoPoint(15.0, 0.0)) }
    }
    DisposableEffect(map, owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) map.onResume()
            if (event == Lifecycle.Event.ON_PAUSE) map.onPause()
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) map.onResume()
        onDispose { owner.lifecycle.removeObserver(observer); map.onPause(); map.onDetach() }
    }
    var fittedCount by remember { mutableIntStateOf(0) }
    fun fitBoth() {
        val points = listOfNotNull(own, visiblePartner).map { GeoPoint(it.latitude, it.longitude) } +
            shownRoute?.route?.points.orEmpty().map { GeoPoint(it.latitude, it.longitude) }
        if (points.isEmpty()) return
        if (points.size == 1 || points.all { it.distanceToAsDouble(points[0]) < 50 }) {
            map.controller.setZoom(17.0); map.controller.setCenter(points[0])
        } else map.zoomToBoundingBox(org.osmdroid.util.BoundingBox.fromGeoPoints(points), true, if (fullScreen) 170 else 60, 17.0, null)
    }
    val locations = listOfNotNull(own, visiblePartner)
    LaunchedEffect(own, visiblePartner, partnerName, shownRoute) {
        map.overlays.clear()
        listOf("You" to own, partnerName to visiblePartner).forEachIndexed { index, (label, point) ->
            if (point != null) map.overlays.add(Marker(map).apply {
                position = GeoPoint(point.latitude, point.longitude); title = label
                subDescription = "Accuracy ±${point.accuracy.toInt()} m"
                icon = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(if (index == 0) android.graphics.Color.rgb(123, 59, 74) else android.graphics.Color.rgb(217, 160, 179))
                    setStroke(3, android.graphics.Color.WHITE); setSize(40, 40)
                }
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            })
        }
        if (locations.size == 2) map.overlays.add(0, Polyline(map).apply {
            setPoints(shownRoute?.route?.points?.map { GeoPoint(it.latitude, it.longitude) } ?: locations.map { GeoPoint(it.latitude, it.longitude) })
            outlinePaint.color = android.graphics.Color.rgb(181, 101, 118)
            outlinePaint.strokeWidth = if (shownRoute == null) 3f else 7f
            if (shownRoute == null) outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 10f), 0f)
        })
        if (locations.size != fittedCount) { fittedCount = locations.size; map.post { fitBoth() } }
        map.invalidate()
    }
    val straight = if (own != null && visiblePartner != null) "${RoadRoutes.distance(MapDistance.meters(own!!, visiblePartner))} apart · ${if (ready) "straight line" else "last known positions"}"
        else "Both locations are needed to calculate distance."
    val routeText = when {
        mode == null -> straight
        !ready -> "Waiting for two fresh locations."
        loading -> "Updating ${mode.label.lowercase()} route…"
        routeError != null -> routeError!! + if (shownRoute != null) " Showing the previous estimate." else ""
        shownRoute != null -> "${RoadRoutes.distance(shownRoute.route.meters)} by ${mode.label.lowercase()} · about ${RoadRoutes.duration(shownRoute.route.seconds)}"
        else -> "Preparing route…"
    }
    @Composable fun RouteControls() {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(routeText, color = Wine, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(mode == null, { modeName = "off" }, label = { Text("Direct") })
                RouteMode.entries.forEach { value -> FilterChip(mode == value, { modeName = value.name }, label = { Text(value.label) }) }
            }
            if (mode == null) Text("Road / Walk sends both locations to FOSSGIS for routing.", fontSize = 11.sp, color = Wine)
            else {
                Text("You → $partnerName · estimates exclude live traffic and transit schedules.", fontSize = 11.sp, color = Wine)
                if (shownRoute != null) Text("Calculated ${(now - shownRoute.calculatedAt).coerceAtLeast(0) / 1000}s ago · refreshes after movement, at most once a minute", fontSize = 11.sp, color = Wine)
            }
        }
    }
    val partnerStatus = if (stopped != null) "$partnerName stopped sharing" + stopped.text.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
        else visiblePartner?.let { pointLabel(partnerName, it, now) } ?: "$partnerName hasn't shared a location yet."
    Column(Modifier.fillMaxSize().then(if (fullScreen) Modifier else Modifier.padding(horizontal = 22.dp)), verticalArrangement = Arrangement.spacedBy(if (fullScreen) 0.dp else 8.dp)) {
        if (!fullScreen) {
            Heading("A little closer.", "Locations update about every 20 seconds while sharing.")
            LocationControls(notify)
            RouteControls()
        }
        Box(Modifier.weight(1f).fillMaxWidth().then(if (fullScreen) Modifier else Modifier.clip(RoundedCornerShape(24.dp)))) {
            AndroidView(factory = { map }, modifier = Modifier.fillMaxSize())
            if (fullScreen) Surface(Modifier.align(Alignment.TopStart).padding(12.dp).fillMaxWidth(.80f), shape = RoundedCornerShape(18.dp), color = Mist.copy(alpha = .96f)) {
                Box(Modifier.padding(12.dp)) { RouteControls() }
            }
            Column(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                FilledIconButton(onClick = { setFullScreen(!fullScreen) }) { Icon(if (fullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, if (fullScreen) "Exit full-screen map" else "Full-screen map") }
                FilledIconButton(onClick = { fitBoth() }) { Icon(Icons.Default.MyLocation, "Show both locations and route") }
            }
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color.White.copy(alpha = .92f)).padding(5.dp)) {
                if (fullScreen) Text(partnerStatus, fontSize = 11.sp, color = Wine)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("© OpenStreetMap contributors", fontSize = 10.sp, modifier = Modifier.clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/copyright"))) })
                    Text("Routes: FOSSGIS / OSRM", fontSize = 10.sp, modifier = Modifier.clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://routing.openstreetmap.de/about.html"))) })
                    Text("Fix the map", fontSize = 10.sp, modifier = Modifier.clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/fixthemap"))) })
                }
            }
        }
        if (!fullScreen) Column(Modifier.padding(bottom = 10.dp)) {
            if (own != null) Text(pointLabel("You", own!!, now), fontSize = 11.sp, color = Wine)
            Text(partnerStatus, fontSize = 11.sp, color = Wine)
        }
    }
}

private fun pointLabel(name: String, point: LivePoint, now: Long): String {
    val age = (now - (point.updatedAt.takeIf { it > 0 } ?: point.time)).coerceAtLeast(0) / 1000
    return "$name · ${if (MapDistance.fresh(point, now)) "Live" else "Last known"} · ${age}s ago · ±${point.accuracy.toInt()} m"
}
