package com.example.localtwouserapp

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews

open class PhotoFrameWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) { ids.forEach { update(context, it) } }
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) { update(context, id) }
    override fun onDeleted(context: Context, ids: IntArray) { PhotoFrameStore(context).use { store -> ids.forEach { store.delete(it) } } }
    override fun onRestored(context: Context, oldIds: IntArray, newIds: IntArray) {
        PhotoFrameStore(context).use { store -> oldIds.zip(newIds.toList()).forEach { (old, new) -> store.restore(old, new) } }
        newIds.forEach { id ->
            AppWidgetManager.getInstance(context).updateAppWidgetOptions(id, Bundle().apply { putBoolean(AppWidgetManager.OPTION_APPWIDGET_RESTORE_COMPLETED, true) })
            update(context, id)
        }
    }
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // requestPinAppWidget callbacks supply a single ID rather than the usual ID array.
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE && intent.hasExtra(AppWidgetManager.EXTRA_APPWIDGET_ID))
            update(context, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID))
        if (intent.action in listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) {
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, javaClass)).forEach { update(context, it) }
        }
    }
    companion object {
        val providers = listOf(PhotoFrame2x2::class.java, PhotoFrame3x2::class.java, PhotoFrame2x3::class.java, PhotoFrame3x3::class.java)
        fun owns(context: Context, id: Int) = AppWidgetManager.getInstance(context).getAppWidgetInfo(id)?.provider
            ?.let { info -> providers.any { info == ComponentName(context, it) } } == true
        fun update(context: Context, id: Int) {
            if (!owns(context, id)) return
            PhotoFrameStore(context).use { store ->
                val photo = store.get(id)
                val bitmap = photo?.let { runCatching { store.thumbnail(it) }.getOrNull() }
                val views = RemoteViews(context.packageName, R.layout.widget_photo_frame).apply {
                    setImageViewResource(R.id.photo_fill, 0)
                    setImageViewResource(R.id.photo_fit, 0)
                    setViewVisibility(R.id.photo_placeholder, if (bitmap == null) View.VISIBLE else View.GONE)
                    setViewVisibility(R.id.photo_fill, if (bitmap != null && photo?.fit != true) View.VISIBLE else View.GONE)
                    setViewVisibility(R.id.photo_fit, if (bitmap != null && photo?.fit == true) View.VISIBLE else View.GONE)
                    if (bitmap != null) setImageViewBitmap(if (photo?.fit == true) R.id.photo_fit else R.id.photo_fill, bitmap)
                    val intent = Intent(context, PhotoFrameActivity::class.java)
                        .setData(Uri.parse("us://photo-frame/$id"))
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    setOnClickPendingIntent(R.id.photo_frame, PendingIntent.getActivity(context, id, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                }
                try { AppWidgetManager.getInstance(context).updateAppWidget(id, views) }
                finally { bitmap?.recycle() }
            }
        }
    }
}
class PhotoFrame2x2 : PhotoFrameWidget()
class PhotoFrame3x2 : PhotoFrameWidget()
class PhotoFrame2x3 : PhotoFrameWidget()
class PhotoFrame3x3 : PhotoFrameWidget()
