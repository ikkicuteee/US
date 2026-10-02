package com.example.localtwouserapp

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class PlanScheduleTest {
    private fun day(s: String) = LocalDate.parse(s)
    private fun plan(date: String, repeat: String = "once") = Entry(type = "event", date = date, fields = mapOf("todo" to "true", "repeat" to repeat, "at" to "23:05"))
    @Test fun monthlyClampsShortMonthsWithoutDriftingOrGoingBeforeStart() {
        val entry = plan("2026-01-31", "monthly")
        assertFalse(PlanSchedule.occurs(entry, day("2025-12-31")))
        assertTrue(PlanSchedule.occurs(entry, day("2026-02-28")))
        assertTrue(PlanSchedule.occurs(entry, day("2026-03-31")))
        assertFalse(PlanSchedule.occurs(entry, day("2026-03-28")))
    }
    @Test fun leapAnniversaryClampsAndLegacyAnnualPlansStillWork() {
        val entry = plan("2024-02-29").copy(fields = mapOf("yearly" to "true"))
        assertTrue(PlanSchedule.occurs(entry, day("2025-02-28")))
        assertTrue(PlanSchedule.occurs(entry, day("2028-02-29")))
        assertFalse(PlanSchedule.occurs(entry, day("2028-02-28")))
    }
    @Test fun recurringCompletionsAreIndependentAndCanBeUnchecked() {
        val entry = plan("2026-01-01", "monthly")
        val jan = day("2026-01-01"); val feb = day("2026-02-01")
        val first = PlanSchedule.toggle(entry, jan)
        val both = PlanSchedule.toggle(first, feb)
        assertTrue(PlanSchedule.done(both, jan)); assertTrue(PlanSchedule.done(both, feb))
        val undone = PlanSchedule.toggle(both, jan)
        assertFalse(PlanSchedule.done(undone, jan)); assertTrue(PlanSchedule.done(undone, feb))
    }
    @Test fun agendaUsesUpdatedEntriesAndSortsByTime() {
        val entry = plan("2026-10-02"); val early = entry.copy(id = "early", fields = mapOf("at" to "09:00"))
        assertEquals("early", PlanSchedule.on(listOf(entry, early), day(entry.date)).first().id)
        assertTrue(PlanSchedule.on(listOf(entry.copy(done = true)), day(entry.date)).single().done)
        assertTrue(PlanSchedule.on(listOf(entry.copy(deleted = true)), day(entry.date)).isEmpty())
    }
    @Test fun timesUse24HoursAndNextReminderSkipsCompletedDate() {
        listOf("00:00", "09:05", "23:59").forEach { assertTrue(PlanSchedule.validTime(it)) }
        listOf("24:00", "9:05", "12:60", "12:30 PM", "").forEach { assertFalse(PlanSchedule.validTime(it)) }
        val entry = plan("2026-01-31", "monthly")
        assertEquals(LocalDateTime.parse("2026-02-28T23:05"), PlanSchedule.next(entry, LocalDateTime.parse("2026-02-01T00:00")))
        assertEquals(LocalDateTime.parse("2026-03-31T23:05"), PlanSchedule.next(PlanSchedule.toggle(entry, day("2026-02-28")), LocalDateTime.parse("2026-02-01T00:00")))
        assertNull(PlanSchedule.next(plan("2026-01-01"), LocalDateTime.parse("2026-01-02T00:00")))
    }
    @Test fun distanceHandlesSamePlaceEquatorAndDateLine() {
        assertEquals(0.0, MapDistance.meters(LivePoint(), LivePoint()), .001)
        assertEquals(111195.0, MapDistance.meters(LivePoint(), LivePoint(longitude = 1.0)), 1.0)
        assertEquals(22239.0, MapDistance.meters(LivePoint(longitude = 179.9), LivePoint(longitude = -179.9)), 1.0)
        assertFalse(MapDistance.fresh(LivePoint(time = 1), 61_001))
    }
    @Test fun delayedReminderSurvivesUnrelatedSyncButNotCompletionOrDeletion() {
        val entry = plan("2026-10-02")
        val pending = LocalDateTime.parse("2026-10-02T23:05")
        val now = pending.plusMinutes(5)
        assertEquals(pending, PlanSchedule.nextAlarm(listOf(entry, Entry(text = "New chat message")), now, pending))
        assertNull(PlanSchedule.nextAlarm(listOf(entry.copy(done = true)), now, pending))
        assertNull(PlanSchedule.nextAlarm(listOf(entry.copy(deleted = true)), now, pending))
        assertNull(PlanSchedule.nextAlarm(listOf(entry), now.plusDays(1), pending))
    }
    @Test fun cropPositionAndZoomStayWithinSource() {
        assertEquals(CropBox(0, 0, 100), PhotoCrop.bounds(300, 100, 0f, .5f, 1f))
        assertEquals(CropBox(200, 0, 100), PhotoCrop.bounds(300, 100, 1f, .5f, 1f))
        val zoomed = PhotoCrop.bounds(100, 300, 1f, 1f, 2f)
        assertEquals(CropBox(50, 250, 50), zoomed)
    }
}
