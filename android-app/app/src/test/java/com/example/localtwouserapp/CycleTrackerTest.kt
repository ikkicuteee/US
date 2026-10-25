package com.example.localtwouserapp

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class CycleTrackerTest {
    private val today = LocalDate.parse("2026-10-17")
    private fun period(start: String, end: String = "") = Entry(id = "cycle-period-$start", type = "cycle", date = start,
        fields = mapOf("kind" to "period", "end" to end))
    private fun settings(length: String = "0", predict: String = "true") = Entry(id = "cycle-settings", type = "cycle",
        fields = mapOf("kind" to "settings", "length" to length, "predict" to predict))
    private val history = listOf(period("2026-08-01", "2026-08-05"), period("2026-08-29", "2026-09-02"), period("2026-09-26", "2026-09-30"))

    @Test fun estimatesFromStartToStartIntervals() {
        val result = CycleTracker.estimate(history, today)
        assertEquals(listOf(28, 28), result.intervals)
        assertEquals(LocalDate.parse("2026-10-24"), result.next)
    }
    @Test fun doesNotAssumeTwentyEightDaysForNewTracker() {
        assertNull(CycleTracker.estimate(emptyList(), today).next)
        assertNull(CycleTracker.estimate(history.take(2), today).next)
    }
    @Test fun enteredLengthIsExplicitOverride() {
        assertEquals(LocalDate.parse("2026-10-26"), CycleTracker.estimate(history.takeLast(1) + settings("30"), today).next)
    }
    @Test fun disablingEstimatesKeepsHistory() {
        assertNull(CycleTracker.estimate(history + settings(predict = "false"), today).next)
        assertEquals(3, CycleTracker.periods(history).size)
    }
    @Test fun irregularRecordedIntervalsPauseAutomaticEstimate() {
        val result = CycleTracker.estimate(history + period("2026-10-05", "2026-10-09"), today)
        assertNull(result.next)
        assertTrue(result.explanation.contains("vary"))
    }
    @Test fun doesNotInventUnloggedPeriodsAfterEstimatePasses() {
        assertEquals(LocalDate.parse("2026-10-24"), CycleTracker.estimate(history, today.plusMonths(3)).next)
    }
    @Test fun ongoingPeriodHasNoFutureBleedingMarkers() {
        val logs = CycleTracker.periods(listOf(period("2026-10-15")))
        assertTrue(CycleTracker.recorded(logs, today, today))
        assertFalse(CycleTracker.recorded(logs, today.plusDays(1), today))
        assertNull(CycleTracker.estimate(listOf(period("2026-10-15"), settings("28")), today).next)
    }
    @Test fun overlapAndDuplicateStartsAreRejected() {
        assertNotNull(CycleTracker.periodError(history, null, LocalDate.parse("2026-08-03"), LocalDate.parse("2026-08-06"), today))
        assertNotNull(CycleTracker.periodError(history, null, LocalDate.parse("2026-08-01"), null, today))
        assertNull(CycleTracker.periodError(history, history[0].id, LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-06"), today))
    }
    @Test fun crossPhoneOverlapsSuppressEstimates() {
        assertNull(CycleTracker.estimate(history + period("2026-09-28", "2026-10-02"), today).next)
    }
    @Test fun futureAndReversedPeriodDatesAreRejected() {
        assertNotNull(CycleTracker.periodError(emptyList(), null, today.plusDays(1), null, today))
        assertNotNull(CycleTracker.periodError(emptyList(), null, today, today.minusDays(1), today))
        assertNotNull(CycleTracker.periodError(emptyList(), null, today, today.plusDays(1), today))
    }
    @Test fun deletedAndInvalidRecordsDoNotAffectCalendar() {
        val invalid = listOf(history[0].copy(deleted = true), period("bad date"), period("2026-09-01", "2026-08-01"))
        assertTrue(CycleTracker.periods(invalid).isEmpty())
    }
    @Test fun leapYearAndMonthBoundariesUseRealDates() {
        val logs = listOf(period("2024-02-01", "2024-02-05"), period("2024-02-29", "2024-03-04"), settings("28"))
        assertEquals(LocalDate.parse("2024-03-28"), CycleTracker.estimate(logs, LocalDate.parse("2024-03-10")).next)
    }
    @Test fun spottingAndSymptomsDoNotCreatePeriodStarts() {
        val entry = CycleTracker.dailyEntry(today, "Spotting", "", "Okay", "Low", "Poor", setOf("Cramps"), "Rested")
        assertTrue(CycleTracker.periods(listOf(entry)).isEmpty())
        assertEquals(entry, CycleTracker.day(listOf(entry), today))
        assertEquals("", entry.fields["pain"])
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsOutOfRangePain() {
        CycleTracker.dailyEntry(today, "Light", "11", "Okay", "Low", "Poor", emptySet(), "")
    }
    @Test fun periodIncludesBothEndpoints() {
        val logs = CycleTracker.periods(history)
        assertTrue(CycleTracker.recorded(logs, LocalDate.parse("2026-08-01"), today))
        assertTrue(CycleTracker.recorded(logs, LocalDate.parse("2026-08-05"), today))
        assertFalse(CycleTracker.recorded(logs, LocalDate.parse("2026-08-06"), today))
    }
    @Test fun cannotPredictNextPeriodInsideRecordedBleeding() {
        assertNull(CycleTracker.estimate(history + settings("2"), today).next)
    }
}
