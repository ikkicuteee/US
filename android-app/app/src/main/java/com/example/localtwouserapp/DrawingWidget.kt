package com.example.localtwouserapp

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.widget.RemoteViews

class DrawingWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        refresh(context, true)
        // Catch up when the launcher starts a new process; ongoing updates use existing listeners.
        if (SharedStore.get(context).configured) FirebaseSync.get(context)
    }
    companion object {
        private var previous: List<String>? = null
        @Synchronized fun refresh(context: Context, force: Boolean = false) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, DrawingWidget::class.java))
            if (ids.isEmpty()) return
            val store = SharedStore.get(context)
            // Consistent author order gives both widgets the same composition.
            val layers = if (store.configured) store.entries.value.filter { it.type == "drawing" && !it.deleted }.sortedBy { it.author }.take(2) else emptyList()
            val signature = listOf(store.deviceId, store.roomId, ids.joinToString()) + layers.map { it.fields["image"].orEmpty() }
            if (!force && signature == previous) return
            val bitmap = Bitmap.createBitmap(384, 384, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap); canvas.drawColor(Color.rgb(233, 227, 230))
            layers.forEach { entry -> DrawingImages.decode(entry.fields["image"].orEmpty())?.let {
                canvas.drawBitmap(it, null, Rect(0, 0, 384, 384), null); it.recycle()
            } }
            val views = RemoteViews(context.packageName, R.layout.widget_drawing).apply {
                setImageViewBitmap(R.id.drawing_image, bitmap)
                setContentDescription(R.id.drawing_root, if (!store.configured) "Sign in to US" else "Our drawing · tap to draw")
                setOnClickPendingIntent(R.id.drawing_root, Notices.open(context, "drawing", 92))
            }
            ids.forEach { manager.updateAppWidget(it, views) }
            previous = signature
        }
    }
}
