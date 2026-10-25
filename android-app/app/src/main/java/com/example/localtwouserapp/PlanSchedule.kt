package com.example.localtwouserapp

import java.time.LocalDate
import java.time.YearMonth
import java.time.LocalDateTime
import java.time.LocalTime

object PlanSchedule {
    fun isTodo(entry: Entry) = entry.type == "event" && entry.fields["todo"] == "true" && !RelationshipDates.isMilestone(entry)
    fun repeat(entry: Entry) = entry.fields["repeat"] ?: if (entry.fields["yearly"] == "true") "annual" else "once"
    fun validTime(value: String) = Regex("([01][0-9]|2[0-3]):[0-5][0-9]").matches(value)
    fun occurs(entry: Entry, day: LocalDate): Boolean {
        if (entry.type != "event" || entry.deleted) return false
        val start = runCatching { LocalDate.parse(entry.date) }.getOrNull() ?: return false
        if (day < start) return false
        return when (repeat(entry)) {
            "monthly" -> day.dayOfMonth == minOf(start.dayOfMonth, day.lengthOfMonth())
            "annual" -> day.month == start.month && day.dayOfMonth == minOf(start.dayOfMonth, day.lengthOfMonth())
            else -> day == start
        }
    }
    fun done(entry: Entry, day: LocalDate) = if (!isTodo(entry)) false else if (repeat(entry) == "once") entry.done
        else entry.fields["done-$day"]?.toBooleanStrictOrNull() ?: (entry.done && entry.date == day.toString())
    fun toggle(entry: Entry, day: LocalDate): Entry = if (!isTodo(entry)) entry else if (repeat(entry) == "once") entry.copy(done = !entry.done)
        else entry.copy(fields = entry.fields + ("done-$day" to (!done(entry, day)).toString()))
    fun on(entries: List<Entry>, day: LocalDate) = RelationshipDates.on(day) + entries.filter { occurs(it, day) }
        .map { it.copy(done = done(it, day)) }.sortedWith(compareBy<Entry> { it.done }.thenBy { it.fields["at"] ?: "24:00" }.thenBy { it.text })
    fun next(entry: Entry, after: LocalDateTime): LocalDateTime? {
        val at = entry.fields["at"]?.takeIf(::validTime)?.let(LocalTime::parse) ?: return null
        val start = runCatching { LocalDate.parse(entry.date) }.getOrNull() ?: return null
        val base = maxOf(after.toLocalDate(), start)
        val dates = when (repeat(entry)) {
            "monthly" -> (0L..13L).map { YearMonth.from(base).plusMonths(it).let { month -> month.atDay(minOf(start.dayOfMonth, month.lengthOfMonth())) } }
            "annual" -> (base.year..base.year + 4).map { year -> YearMonth.of(year, start.month).let { month -> month.atDay(minOf(start.dayOfMonth, month.lengthOfMonth())) } }
            else -> listOf(start)
        }
        return dates.firstOrNull { occurs(entry, it) && !done(entry, it) && it.atTime(at) > after }?.atTime(at)
    }
    fun due(entries: List<Entry>, time: LocalDateTime) = entries.filter {
        occurs(it, time.toLocalDate()) && it.fields["at"] == time.toLocalTime().toString() && !done(it, time.toLocalDate())
    }
    fun nextAlarm(entries: List<Entry>, now: LocalDateTime, pending: LocalDateTime?): LocalDateTime? {
        // Keep a delayed alarm due today when unrelated chat/profile updates arrive.
        val delayed = pending?.takeIf { it <= now && it.toLocalDate() == now.toLocalDate() && due(entries, it).isNotEmpty() }
        return delayed ?: entries.mapNotNull { next(it, now) }.minOrNull()
    }
}

object MapDistance {
    fun meters(a: LivePoint, b: LivePoint): Double {
        val lat = Math.toRadians(b.latitude - a.latitude)
        val lon = Math.toRadians(b.longitude - a.longitude)
        val h = kotlin.math.sin(lat / 2).let { it * it } + kotlin.math.cos(Math.toRadians(a.latitude)) *
            kotlin.math.cos(Math.toRadians(b.latitude)) * kotlin.math.sin(lon / 2).let { it * it }
        return 6_371_000.0 * 2 * kotlin.math.asin(kotlin.math.sqrt(h.coerceIn(0.0, 1.0)))
    }
    // A delayed/retried upload must not make an older GPS measurement look new.
    fun measuredAt(point: LivePoint) = if (point.updatedAt > 0) minOf(point.time, point.updatedAt) else point.time
    fun fresh(point: LivePoint, now: Long) = measuredAt(point) > 0 && now - measuredAt(point) in 0 until LocationTiming.STALE_MS
}
