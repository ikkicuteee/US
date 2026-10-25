package com.example.localtwouserapp

import android.content.Context
import android.graphics.Color
import android.graphics.DashPathEffect
import android.view.MotionEvent
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/** One overlay set for the lifetime of a map. Compose owns final disposal. */
internal class LiveMapView(context: Context) : MapView(configure(context)) {
    private var released = false
    private var pendingFit: Pair<List<GeoPoint>, Boolean>? = null
    private val markers = List(2) { index -> Marker(this).apply {
        icon = PhotoPin.create(context, if (index == 0) "You" else "Partner", "", index == 0)
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        isEnabled = false
    } }
    private val line = Polyline(this).apply {
        outlinePaint.color = Color.rgb(181, 101, 118)
        infoWindow = null
        isEnabled = false
    }
    private val dashed = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    private var previousPoints: List<LivePoint?> = listOf(null, null)
    private var previousRoute: RoadRoute? = null

    init {
        // MapView normally destroys itself on temporary window detachment. Reusing that
        // view then accesses a cleared tile provider/repository. Release exactly once below.
        setDestroyMode(false)
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(true)
        controller.setZoom(2.0); controller.setCenter(GeoPoint(15.0, 0.0))
        overlays.add(line); overlays.addAll(markers)
    }

    private var profileKeys = listOf("", "")
    fun updateProfiles(ownName: String, ownAvatar: String, partnerName: String, partnerAvatar: String) {
        if (released) return
        val profiles = listOf(ownName to ownAvatar, partnerName to partnerAvatar)
        val keys = profiles.map { it.first + ":" + it.second }
        profiles.forEachIndexed { index, profile ->
            if (keys[index] != profileKeys[index]) markers[index].icon = PhotoPin.create(context, profile.first, profile.second, index == 0)
        }
        profileKeys = keys
        invalidate()
    }

    fun updateLocations(own: LivePoint?, partner: LivePoint?, partnerName: String, route: RoadRoute?) {
        if (released) return
        val points = listOf(own, partner)
        var geometryChanged = false
        points.forEachIndexed { index, point ->
            val old = previousPoints[index]
            val changed = old?.latitude != point?.latitude || old?.longitude != point?.longitude || old?.accuracy != point?.accuracy
            geometryChanged = geometryChanged || changed
            val marker = markers[index]
            marker.isEnabled = point != null
            if (point == null) marker.closeInfoWindow()
            else {
                marker.title = if (index == 0) "You" else partnerName
                marker.subDescription = "Accuracy ±${kotlin.math.ceil(point.accuracy.toDouble()).toInt()} m"
                if (changed) {
                    val position = GeoPoint(point.latitude, point.longitude)
                    marker.position = position
                }
            }
        }
        line.isEnabled = own != null && partner != null
        if (geometryChanged || route !== previousRoute) {
            line.setPoints(route?.points?.map { GeoPoint(it.latitude, it.longitude) }
                ?: points.filterNotNull().map { GeoPoint(it.latitude, it.longitude) })
            line.outlinePaint.strokeWidth = if (route == null) 3f else 7f
            line.outlinePaint.pathEffect = if (route == null) dashed else null
        }
        previousPoints = points; previousRoute = route
        invalidate()
    }

    fun fitLocations(fullScreen: Boolean) {
        if (released) return
        val points = previousPoints.filterNotNull().map { GeoPoint(it.latitude, it.longitude) } +
            previousRoute?.points.orEmpty().map { GeoPoint(it.latitude, it.longitude) }
        if (points.isEmpty()) return
        pendingFit = points to fullScreen
        fitWhenSized()
    }
    private fun fitWhenSized() {
        val (points, fullScreen) = pendingFit ?: return
        if (released || width <= 1 || height <= 1) return
        pendingFit = null
        if (points.size == 1 || points.all { it.distanceToAsDouble(points[0]) < 50 }) {
            controller.setZoom(17.0); controller.setCenter(points[0])
        } else {
            // Large fixed padding can exceed a small viewport and yield an invalid zoom.
            val padding = minOf(if (fullScreen) 170 else 60, minOf(width, height) / 4)
            zoomToBoundingBox(BoundingBox.fromGeoPoints(points), false, padding, 17.0, null)
        }
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fitWhenSized()
    }
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (released) return false
        if (event.actionMasked == MotionEvent.ACTION_DOWN) parent?.requestDisallowInterceptTouchEvent(true)
        return super.dispatchTouchEvent(event).also {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL)
                parent?.requestDisallowInterceptTouchEvent(false)
        }
    }
    fun release() {
        if (released) return
        released = true; pendingFit = null
        previousPoints = listOf(null, null); previousRoute = null
        onPause(); onDetach()
    }
    companion object {
        private fun configure(context: Context): Context {
            Configuration.getInstance().apply {
                userAgentValue = "USCoupleApp/3.10 (Android; private two-person map)"
                osmdroidBasePath = java.io.File(context.cacheDir, "maps")
                osmdroidTileCache = java.io.File(context.cacheDir, "maps/tiles")
                tileDownloadThreads = 2
                tileDownloadMaxQueueSize = 12
                cacheMapTileOvershoot = 0
            }
            return context
        }
    }
}
