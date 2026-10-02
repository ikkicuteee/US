package com.example.localtwouserapp

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class RelationshipDatesTest {
    @Test fun noCelebrationsBeforeRelationshipOrOnOtherDays() {
        assertTrue(RelationshipDates.on(LocalDate.of(2021, 8, 1)).isEmpty())
        assertTrue(RelationshipDates.on(LocalDate.of(2026, 10, 2)).isEmpty())
    }
    @Test fun startingDayDoesNotBecomeZerothAnniversary() {
        assertEquals("Our story began", RelationshipDates.on(RelationshipDates.start).single().text)
    }
    @Test fun everyFirstCountsMonthsAcrossYears() {
        assertEquals("Our 1st monthsary", RelationshipDates.on(LocalDate.of(2021, 10, 1)).single().text)
        assertEquals("Our 4th monthsary", RelationshipDates.on(LocalDate.of(2022, 1, 1)).single().text)
        assertEquals("Our 61st monthsary", RelationshipDates.on(LocalDate.of(2026, 10, 1)).single().text)
    }
    @Test fun septemberFirstHasBothCelebrations() {
        val entries = RelationshipDates.on(LocalDate.of(2026, 9, 1))
        assertEquals(listOf("Our 5th anniversary", "Our 60th monthsary"), entries.map { it.text })
        assertTrue(entries.all { RelationshipDates.isMilestone(it) })
        assertEquals(2, entries.map { it.id }.distinct().size)
        assertEquals(entries, RelationshipDates.on(LocalDate.of(2026, 9, 1)))
    }
    @Test fun teenOrdinalsAndLeapYearsAreCorrect() {
        assertEquals("Our 11th monthsary", RelationshipDates.on(LocalDate.of(2022, 8, 1)).single().text)
        assertEquals("Our 12th monthsary", RelationshipDates.on(LocalDate.of(2022, 9, 1)).last().text)
        assertEquals("Our 13th monthsary", RelationshipDates.on(LocalDate.of(2022, 10, 1)).single().text)
        assertEquals("Our 30th monthsary", RelationshipDates.on(LocalDate.of(2024, 3, 1)).single().text)
    }
}
