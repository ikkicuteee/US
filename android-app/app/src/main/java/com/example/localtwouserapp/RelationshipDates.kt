package com.example.localtwouserapp

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Recurring personal dates are calculated locally, so both phones and the widget agree. */
object RelationshipDates {
    val start: LocalDate = LocalDate.of(2021, 9, 1)
    private const val AUTHOR = "relationship-calendar"
    fun isMilestone(entry: Entry) = entry.author == AUTHOR && entry.id.startsWith("relationship-")

    fun on(date: LocalDate): List<Entry> {
        if (date.isBefore(start) || date.dayOfMonth != 1) return emptyList()
        val months = ChronoUnit.MONTHS.between(start, date)
        fun event(kind: String, title: String, detail: String) = Entry(
            id = "relationship-$kind-$date", type = "event", author = AUTHOR,
            editor = AUTHOR, time = 0, date = date.toString(), text = title,
            fields = mapOf("milestone" to kind, "detail" to detail),
        )
        if (months == 0L) return listOf(event("start", "Our story began", "September 1, 2021"))
        val result = mutableListOf<Entry>()
        if (date.monthValue == start.monthValue) {
            val years = months / 12
            result += event("anniversary", "Our ${ordinal(years)} anniversary",
                "$years ${if (years == 1L) "year" else "years"} together · Since September 1, 2021")
        }
        result += event("monthsary", "Our ${ordinal(months)} monthsary",
            "$months ${if (months == 1L) "month" else "months"} together · Every first of the month")
        return result
    }

    private fun ordinal(number: Long): String {
        val suffix = if (number % 100 in 11L..13L) "th" else when (number % 10) {
            1L -> "st"
            2L -> "nd"
            3L -> "rd"
            else -> "th"
        }
        return "$number$suffix"
    }
}
