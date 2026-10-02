package com.example.localtwouserapp

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) { refresh(context) }
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action in listOf("us.midnight", Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_BOOT_COMPLETED)) refresh(context)
    }
    override fun onDisabled(context: Context) { context.getSystemService(AlarmManager::class.java).cancel(midnight(context)) }
    companion object {
        private fun midnight(context: Context) = PendingIntent.getBroadcast(context, 71,
            Intent(context, TodayWidget::class.java).setAction("us.midnight"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TodayWidget::class.java))
            if (ids.isEmpty()) return
            val events = SharedStore.get(context).today().sortedBy { it.done }
            val checkable = events.filter { PlanSchedule.isTodo(it) }
            val views = RemoteViews(context.packageName, R.layout.widget_today).apply {
                setTextViewText(R.id.widget_date, LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMM d")))
                setTextViewText(R.id.widget_items, if (events.isEmpty()) "A little room for something lovely.\nNo plans for today yet." else events.take(4).joinToString("\n") { "${if (RelationshipDates.isMilestone(it)) "♥" else if (!PlanSchedule.isTodo(it)) "◇" else if (it.done) "✓" else "○"}  ${it.fields["at"]?.let { time -> "$time · " }.orEmpty()}${it.text}" })
                setTextViewText(R.id.widget_footer, if (events.size > 4) "+${events.size - 4} more · Open calendar" else if (checkable.isEmpty()) "Open our calendar" else "${checkable.count { it.done }} of ${checkable.size} tasks complete · Open calendar")
                setOnClickPendingIntent(R.id.widget_root, Notices.open(context, "calendar", 71))
            }
            ids.forEach { manager.updateAppWidget(it, views) }
            val next = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            context.getSystemService(AlarmManager::class.java).set(AlarmManager.RTC, next, midnight(context))
        }
    }
}
