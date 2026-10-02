package com.example.localtwouserapp

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object Notices {
    private const val UPDATES = "us_updates_v2"
    private const val ALERTS = "us_alerts_v2"
    private const val LOCATION = "us_location_v3"
    fun channels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.deleteNotificationChannel("us_calls_v2")
        manager.deleteNotificationChannel("us_connection_v2")
        listOf(UPDATES to "Messages and shared updates", ALERTS to "Priority partner alerts").forEach { (id, title) ->
            manager.createNotificationChannel(NotificationChannel(id, title, NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true); enableLights(true); lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            })
        }
        manager.createNotificationChannel(NotificationChannel(LOCATION, "Live location sharing", NotificationManager.IMPORTANCE_LOW))
    }
    fun open(context: Context, page: String = "home", request: Int = 0): PendingIntent = PendingIntent.getActivity(context, request,
        Intent(context, MainActivity::class.java).putExtra("page", page).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun ongoing(context: Context): Notification = NotificationCompat.Builder(context, LOCATION)
        .setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentTitle("US · live location")
        .setContentText("Sharing with ${SharedStore.get(context).partnerName} · tap to view or stop")
        .setContentIntent(open(context, "map")).setOngoing(true).setOnlyAlertOnce(true)
        .addAction(0, "Stop sharing", PendingIntent.getService(context, 5,
            Intent(context, LocationService::class.java).setAction("stop_location"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)).build()
    fun show(context: Context, title: String, body: String, urgent: Boolean = false, page: String = "chat") {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), NotificationCompat.Builder(context, if (urgent) ALERTS else UPDATES)
            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body)).setPriority(if (urgent) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (urgent) NotificationCompat.CATEGORY_EVENT else NotificationCompat.CATEGORY_MESSAGE)
            .setDefaults(NotificationCompat.DEFAULT_ALL).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(open(context, page, if (page == "calendar") 83 else 0)).setAutoCancel(true).build())
    }
}

