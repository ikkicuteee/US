package com.example.localtwouserapp

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

data class PeriodLog(val entry: Entry, val start: LocalDate, val end: LocalDate?)
data class CycleEstimate(val next: LocalDate?, val length: Int?, val intervals: List<Int>, val explanation: String)

object CycleTracker {
    const val GUIDE = "https://share.upmc.com/2026/01/tracking-menstrual-cycle-beginners-guide/"
    val flows = listOf("Not logged", "None", "Spotting", "Light", "Moderate", "Heavy")
    val moods = listOf("Not logged", "Okay", "Happy", "Sad", "Anxious", "Irritable")
    val energies = listOf("Not logged", "Low", "Usual", "High")
    val sleeps = listOf("Not logged", "Poor", "Okay", "Good")
    val symptoms = listOf("Cramps", "Bloating", "Headache", "Back pain", "Breast tenderness", "Acne", "Cravings")
    fun date(value: String): LocalDate? = runCatching { LocalDate.parse(value) }.getOrNull()
    fun periods(entries: List<Entry>): List<PeriodLog> = entries.filter {
        it.type == "cycle" && !it.deleted && it.fields["kind"] == "period"
    }.mapNotNull { entry ->
        val start = date(entry.date) ?: return@mapNotNull null
        val rawEnd = entry.fields["end"].orEmpty()
        val end = if (rawEnd.isEmpty()) null else date(rawEnd) ?: return@mapNotNull null
        if (end != null && end < start) null else PeriodLog(entry, start, end)
    }.sortedBy { it.start }
    fun day(entries: List<Entry>, date: LocalDate) = entries.firstOrNull {
        it.id == "cycle-day-$date" && it.type == "cycle" && !it.deleted
    }
    fun settings(entries: List<Entry>) = entries.firstOrNull { it.id == "cycle-settings" && it.type == "cycle" && !it.deleted }
    fun recorded(periods: List<PeriodLog>, date: LocalDate, today: LocalDate) = periods.any {
        date >= it.start && date <= (it.end ?: today) && date <= today
    }
    fun overlap(periods: List<PeriodLog>, today: LocalDate): Boolean = periods.zipWithNext().any { (a, b) ->
        (a.end ?: today) >= b.start
    }
    fun periodError(entries: List<Entry>, id: String?, start: LocalDate, end: LocalDate?, today: LocalDate): String? {
        if (start > today || (end != null && end > today)) return "Log actual dates up to today. Estimates are shown separately."
        if (end != null && end < start) return "End date must be on or after the start date."
        val other = periods(entries).filter { it.entry.id != id }
        if (other.any { start <= (it.end ?: today) && (end ?: today) >= it.start })
            return "These dates overlap another period. Edit or end that record first."
        return null
    }
    fun estimate(entries: List<Entry>, today: LocalDate): CycleEstimate {
        val logs = periods(entries).filter { it.start <= today }
        val intervals = logs.zipWithNext { a, b -> ChronoUnit.DAYS.between(a.start, b.start).toInt() }.takeLast(6)
        val config = settings(entries)
        fun none(reason: String) = CycleEstimate(null, null, intervals, reason)
        if (config?.fields?.get("predict") == "false") return none("Estimates are turned off.")
        if (logs.isEmpty()) return none("Log a period start to begin.")
        if (overlap(logs, today)) return none("Overlapping records need review before estimating dates.")
        val manual = config?.fields?.get("length")?.toIntOrNull()?.takeIf { it in 1..365 }
        if (manual == null && intervals.size < 2) return none("Log three period starts, or enter a usual cycle length in settings.")
        if (manual == null && (intervals.any { it !in 1..365 } || intervals.max() - intervals.min() > 7))
            return none("Recorded intervals vary widely or may include missing logs. Automatic estimates are paused; review the history.")
        val length = manual ?: intervals.sorted().let { sorted ->
            if (sorted.size % 2 == 1) sorted[sorted.size / 2]
            else ((sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0).roundToInt()
        }
        val next = logs.last().start.plusDays(length.toLong())
        val reason = if (manual != null) "Based on your entered $length-day cycle."
            else "Based on the middle value of ${intervals.size} recent recorded cycles ($length days)."
        if (logs.last().end == null) return CycleEstimate(null, length, intervals, "End the ongoing period to see the next estimate. $reason")
        if (next <= logs.last().end) return none("The estimated cycle ends within the logged period. Review the dates or entered length.")
        // Never roll a missed estimate forward as though an unlogged period happened.
        if (next < today) return CycleEstimate(next, length, intervals, "$reason Estimated date has passed; update the log when bleeding starts.")
        return CycleEstimate(next, length, intervals, reason)
    }
    fun dailyEntry(date: LocalDate, flow: String, pain: String, mood: String, energy: String,
        sleep: String, selectedSymptoms: Set<String>, notes: String): Entry {
        require(flow in flows && mood in moods && energy in energies && sleep in sleeps)
        require(pain.isEmpty() || pain.toIntOrNull() in 0..10)
        require(selectedSymptoms.all { it in symptoms } && notes.length <= 1000)
        return Entry(id = "cycle-day-$date", type = "cycle", date = date.toString(), text = notes,
            fields = mapOf("kind" to "day", "flow" to flow, "pain" to pain, "mood" to mood,
                "energy" to energy, "sleep" to sleep, "symptoms" to symptoms.filter { it in selectedSymptoms }.joinToString("|")))
    }
}
