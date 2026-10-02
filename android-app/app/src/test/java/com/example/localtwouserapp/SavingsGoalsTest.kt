package com.example.localtwouserapp

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class SavingsGoalsTest {
    private val goal = Entry(id = "shared-goal", type = "goal", text = "Trip", amount = 10000, date = "2027-01-01", fields = mapOf("reason" to "Flights"))
    @Test fun closingReachedGoalPreservesSnapshotAndClearsActiveGoal() {
        val closed = SavingsGoals.close(goal, 10500, 1234)
        assertEquals("success", closed.fields["outcome"])
        assertEquals("10500", closed.fields["finalBalance"])
        assertEquals("1234", closed.fields["closedAt"])
        assertEquals(goal.id, closed.id); assertEquals(goal.amount, closed.amount); assertEquals(goal.date, closed.date)
        assertTrue(SavingsGoals.active(listOf(closed)).isEmpty())
        assertEquals(listOf(closed), SavingsGoals.history(listOf(closed)))
    }
    @Test fun belowTargetFailsAndExactTargetSucceeds() {
        assertEquals("failure", SavingsGoals.close(goal, 9999, 1).fields["outcome"])
        assertEquals("success", SavingsGoals.close(goal, 10000, 1).fields["outcome"])
        assertEquals("failure", SavingsGoals.close(goal, -100, 1).fields["outcome"])
    }
    @Test fun newGoalsAndLedgerCannotOverwriteHistory() {
        val closed = SavingsGoals.close(goal, 10000, 1)
        val next = goal.copy(id = "new-goal")
        val saving = Entry(type = "saving", amount = 10000)
        val entries = listOf(closed, next, saving)
        assertEquals(listOf(next), SavingsGoals.active(entries))
        assertEquals(listOf(closed), SavingsGoals.history(entries))
        assertEquals(10000L, entries.filter { it.type == "saving" }.sumOf { it.amount })
        assertEquals(closed, MergeRules.winner(closed, goal.copy(version = 99)))
    }
    @Test(expected = IllegalArgumentException::class) fun historyCannotBeClosedAgain() {
        SavingsGoals.close(SavingsGoals.close(goal, 10000, 1), 1, 2)
    }
    @Test(expected = IllegalArgumentException::class) fun legacyGoalNeedsRequiredDetailsBeforeArchiving() {
        SavingsGoals.close(goal.copy(date = "", fields = emptyMap()), 10000, 1)
    }
    @Test fun partnerNameUsesOnlyCurrentPartnerProfileAndHasFallback() {
        val own = Entry(type = "profile", author = "me", text = "Me")
        val partner = Entry(type = "profile", author = "partner", text = "  Alex  ")
        assertEquals("Alex", PartnerNames.from(listOf(own, partner), "me"))
        assertEquals("Sam", PartnerNames.from(listOf(partner.copy(text = "Sam")), "me"))
        assertEquals("Your partner", PartnerNames.from(listOf(own, partner.copy(deleted = true)), "me"))
        assertEquals("Your partner", PartnerNames.from(listOf(partner.copy(text = "  ")), "me"))
    }
    @Test fun tasksHaveCheckmarksButEventsDoNot() {
        val date = LocalDate.of(2026, 10, 2)
        val reminder = Entry(type = "event", date = date.toString(), done = true, fields = mapOf("todo" to "false", "at" to "09:00"))
        val task = reminder.copy(done = false, fields = reminder.fields + ("todo" to "true"))
        assertFalse(PlanSchedule.isTodo(reminder)); assertFalse(PlanSchedule.done(reminder, date))
        assertEquals(reminder, PlanSchedule.toggle(reminder, date))
        assertTrue(PlanSchedule.isTodo(task)); assertTrue(PlanSchedule.done(PlanSchedule.toggle(task, date), date))
    }
}
