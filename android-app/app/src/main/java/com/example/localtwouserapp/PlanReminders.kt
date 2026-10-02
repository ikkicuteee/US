package com.example.localtwouserapp

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalDateTime
import java.time.ZoneId

/** One inexact local alarm for the next due time; no periodic background work. */
class PlanReminders : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        synchronized(PlanReminders) { receive(context, intent) }
    }
    private fun receive(context: Context, intent: Intent) {
        val store = SharedStore.get(context)
        val prefs = context.getSharedPreferences("plan-reminders", Context.MODE_PRIVATE)
        if (intent.action == DUE && store.configured && intent.getStringExtra("space") == "${store.deviceId}/${store.roomId}") {
            val due = runCatching { LocalDateTime.parse(intent.getStringExtra("due")) }.getOrNull()
            val deliveryKey = "${store.deviceId}/${store.roomId}/$due"
            if (due != null && due <= LocalDateTime.now() && prefs.getString("delivered", "") != deliveryKey) {
                val entries = PlanSchedule.due(store.entries.value, due)
                if (entries.isNotEmpty()) {
                    Notices.channels(context)
                    Notices.show(context, "Time for our plans", entries.joinToString("\n") { "${it.fields["at"]} · ${it.text}" }, page = "calendar")
                }
                prefs.edit().putString("delivered", deliveryKey).remove("due").apply()
            }
        }
        schedule(context)
    }
    companion object {
        private const val DUE = "us.plan_due"
        @Synchronized fun schedule(context: Context) {
            val store = SharedStore.get(context)
            val prefs = context.getSharedPreferences("plan-reminders", Context.MODE_PRIVATE)
            val space = "${store.deviceId}/${store.roomId}"
            val now = LocalDateTime.now()
            val previous = if (prefs.getString("space", "") == space) runCatching {
                LocalDateTime.parse(prefs.getString("due", ""))
            }.getOrNull() else null
            val next = if (store.configured) PlanSchedule.nextAlarm(store.entries.value, now, previous) else null
            val pending = PendingIntent.getBroadcast(context, 83,
                Intent(context, PlanReminders::class.java).setAction(DUE)
                    .putExtra("due", next?.toString()).putExtra("space", space),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val manager = context.getSystemService(AlarmManager::class.java)
            manager.cancel(pending)
            prefs.edit().putString("space", space).putString("due", next?.toString()).apply()
            if (next != null) manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), pending)
        }
    }
}
