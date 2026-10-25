package com.example.localtwouserapp

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextOverflow
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
import kotlinx.coroutines.launch

private data class CachedRoute(val mode: RouteMode, val route: RoadRoute, val attempt: RouteAttempt, val calculatedAt: Long)

@Composable fun MapPage(notify: (String) -> Unit, fullScreen: Boolean = false, setFullScreen: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    val sync = remember { FirebaseSync.get(context) }
    val store = remember { SharedStore.get(context) }
    val own by LocationService.ownLocation.collectAsStateWithLifecycle()
    val locating by LocationService.refreshingLocation.collectAsStateWithLifecycle()
    var refreshingPartner by remember { mutableStateOf(false) }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    val refreshScope = rememberCoroutineScope()
    val partner by sync.partnerLocation.collectAsStateWithLifecycle()
    val entries by store.entries.collectAsStateWithLifecycle()
    val partnerName = PartnerNames.from(entries, store.deviceId)
    val ownProfile = entries.firstOrNull { it.id == "profile-${store.deviceId}" && !it.deleted }
    val partnerProfile = entries.firstOrNull { it.type == "profile" && it.author != store.deviceId && !it.deleted }
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
    val map = remember(context, owner) { LiveMapView(context) }
    DisposableEffect(map, owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) map.onResume()
            if (event == Lifecycle.Event.ON_PAUSE) map.onPause()
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) map.onResume()
        onDispose { owner.lifecycle.removeObserver(observer); map.release() }
    }
    LaunchedEffect(ownProfile, partnerProfile) {
        map.updateProfiles(ownProfile?.text ?: "You", ownProfile?.fields?.get("avatar").orEmpty(), partnerName, partnerProfile?.fields?.get("avatar").orEmpty())
    }
    var fittedCount by remember { mutableIntStateOf(0) }
    val locations = listOfNotNull(own, visiblePartner)
    LaunchedEffect(own, visiblePartner, partnerName, shownRoute, fullScreen) {
        map.updateLocations(own, visiblePartner, partnerName, shownRoute?.route)
        if (locations.size != fittedCount) {
            fittedCount = locations.size
            map.fitLocations(fullScreen)
        }
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(routeText, modifier = Modifier.weight(1f), color = Wine, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                IconButton(enabled = !locating && !refreshingPartner, onClick = refresh@{
                    if (refreshingPartner || LocationService.refreshingLocation.value) return@refresh
                    LocationService.instance?.refreshLocation()
                    refreshingPartner = true
                    refreshScope.launch {
                        try { notify(sync.refreshPartnerLocation()) }
                        finally { refreshingPartner = false }
                    }
                }) {
                    if (locating || refreshingPartner) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.Refresh, "Refresh locations")
                }
                IconButton(onClick = { showDetails = true }) { Icon(Icons.Default.Info, "Location and route details") }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(mode == null, { modeName = "off" }, label = { Text("Direct") })
                RouteMode.entries.forEach { value -> FilterChip(mode == value, { modeName = value.name }, label = { Text(value.label) }) }
            }
        }
    }
    val partnerStatus = if (stopped != null) "$partnerName stopped sharing" + stopped.text.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
        else visiblePartner?.let { pointLabel(partnerName, it, now) } ?: "$partnerName hasn't shared a location yet."
    Column(Modifier.fillMaxSize().then(if (fullScreen) Modifier else Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)), verticalArrangement = Arrangement.spacedBy(if (fullScreen) 0.dp else 8.dp)) {
        if (!fullScreen) {
            LocationControls(notify)
            RouteControls()
        }
        Box((if (fullScreen) Modifier.weight(1f) else Modifier.height(380.dp)).fillMaxWidth().then(if (fullScreen) Modifier else Modifier.clip(RoundedCornerShape(24.dp)))) {
            AndroidView(factory = { map }, modifier = Modifier.fillMaxSize())
            if (fullScreen) Surface(Modifier.align(Alignment.TopStart).padding(12.dp).fillMaxWidth(.80f), shape = RoundedCornerShape(18.dp), color = Mist.copy(alpha = .96f)) {
                Box(Modifier.padding(12.dp)) { RouteControls() }
            }
            Column(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                FilledIconButton(onClick = { setFullScreen(!fullScreen) }) { Icon(if (fullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, if (fullScreen) "Exit full-screen map" else "Full-screen map") }
                FilledIconButton(onClick = { map.fitLocations(fullScreen) }) { Icon(Icons.Default.MyLocation, "Show both locations and route") }
            }
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color.White.copy(alpha = .92f)).padding(5.dp)) {
                if (fullScreen) {
                    own?.let { Text(pointLabel("You", it, now), fontSize = 11.sp, color = Wine) }
                    Text(partnerStatus, fontSize = 11.sp, color = Wine, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { showDetails = true })
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("© OpenStreetMap contributors", fontSize = 10.sp, modifier = Modifier.clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/copyright"))) })
                    Text("Routes: FOSSGIS / OSRM", fontSize = 10.sp, modifier = Modifier.clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://routing.openstreetmap.de/about.html"))) })
                    Text("Fix the map", fontSize = 10.sp, modifier = Modifier.clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/fixthemap"))) })
                }
            }
        }
        if (!fullScreen) Column(Modifier.padding(bottom = 10.dp)) {
            if (own != null) Text(pointLabel("You", own!!, now), fontSize = 11.sp, color = Wine)
            Text(partnerStatus, fontSize = 11.sp, color = Wine, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { showDetails = true })
        }
    }
    if (showDetails) AlertDialog(onDismissRequest = { showDetails = false },
        title = { Text("Location details") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(own?.let { pointLabel("You", it, now) } ?: "Your location is unavailable.")
            Text(partnerStatus)
            Text(routeText)
            Text("Accuracy is each phone's estimated uncertainty. Locations update about every 15 seconds while sharing.", fontSize = 13.sp)
            Text("Road and Walk send both locations to FOSSGIS for routing. Estimates exclude live traffic and public transport schedules.", fontSize = 13.sp)
            shownRoute?.let { Text("Route calculated ${(now - it.calculatedAt).coerceAtLeast(0) / 1000}s ago. Routes refresh after movement, at most once a minute.", fontSize = 13.sp) }
        } },
        confirmButton = { TextButton(onClick = { showDetails = false }) { Text("Done") } })

}

private fun pointLabel(name: String, point: LivePoint, now: Long): String {
    val age = (now - MapDistance.measuredAt(point)).coerceAtLeast(0) / 1000
    return "$name · ${if (MapDistance.fresh(point, now)) "Live" else "Last known"} · ${age}s ago · ±${kotlin.math.ceil(point.accuracy.toDouble()).toInt()} m" +
        if (point.accuracy > 100f) " · low accuracy" else ""
}
