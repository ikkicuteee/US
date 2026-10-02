package com.example.localtwouserapp

import org.junit.Assert.*
import org.junit.Test

class MergeRulesTest {
    @Test fun offlineContributionsSurviveEitherSyncDirection() {
        val a = listOf(Entry(id = "a", type = "saving", amount = 10001, editor = "phone-a"))
        val b = listOf(Entry(id = "b", type = "saving", amount = -2500, editor = "phone-b"))
        assertEquals(MergeRules.merge(a, b), MergeRules.merge(b, a))
        assertEquals(7501L, MergeRules.merge(a, b).sumOf { it.amount })
    }
    @Test fun replayDoesNotDoubleCountSavings() {
        val items = listOf(Entry(id = "deposit", type = "saving", amount = 2000))
        assertEquals(items, MergeRules.merge(items, items + items))
    }
    @Test fun deletedPlansDoNotReturnAfterStaleSnapshot() {
        val old = Entry(id = "plan", type = "event", version = 1, editor = "a")
        val deleted = old.copy(version = 2, deleted = true)
        assertTrue(MergeRules.merge(listOf(deleted), listOf(old)).single().deleted)
        assertTrue(MergeRules.merge(listOf(old), listOf(deleted)).single().deleted)
    }
    @Test fun concurrentGoalEditsConverge() {
        val a = Entry(id = "shared-goal", version = 4, editor = "phone-a", amount = 10000)
        val b = a.copy(editor = "phone-b", amount = 20000)
        assertEquals(b, MergeRules.winner(a, b))
        assertEquals(b, MergeRules.winner(b, a))
    }
    @Test fun moneyUsesExactCents() {
        assertEquals(101L, MergeRules.cents("1.01"))
        assertEquals(100L, MergeRules.cents("1"))
        assertNull(MergeRules.cents("1.001"))
        assertNull(MergeRules.cents("-1"))
        assertNull(MergeRules.cents("0"))
        assertNull(MergeRules.cents("NaN"))
        assertNull(MergeRules.cents("9999999999999999999999"))
    }
    @Test fun invitationRequiresFullRandomToken() {
        assertTrue(MergeRules.validInvite("ab12".repeat(16)))
        listOf("123456", "a".repeat(63), "a".repeat(65), "/".repeat(64), "g".repeat(64)).forEach {
            assertFalse(it, MergeRules.validInvite(it))
        }
    }
    @Test fun staleAndInvalidLocationFixesAreRejected() {
        assertTrue(LocationTiming.usable(14.6, 121.0, 10f, 5_000))
        assertFalse(LocationTiming.usable(14.6, 121.0, 10f, 60_000))
        assertFalse(LocationTiming.usable(14.6, 121.0, 10f, -1))
        assertFalse(LocationTiming.usable(Double.NaN, 121.0, 10f, 0))
        assertFalse(LocationTiming.usable(91.0, 121.0, 10f, 0))
        assertFalse(LocationTiming.usable(14.6, 121.0, Float.NaN, 0))
    }
    @Test fun earlierUploadCannotAcknowledgeANewerLocalEdit() {
        val sent = Entry(id = "plan", version = 2, editor = "alice")
        val current = sent.copy(version = 3, done = true)
        assertFalse(MergeRules.acknowledged(current, sent, sent))
    }
    @Test fun winningRemoteEditResolvesLosingOutboxEntry() {
        val sent = Entry(id = "plan", version = 2, editor = "alice")
        val remote = sent.copy(editor = "bob", done = true)
        assertTrue(MergeRules.acknowledged(sent, sent, remote))
        assertTrue(MergeRules.acknowledged(remote, sent, remote))
        assertEquals(remote, MergeRules.winner(sent, remote))
    }
}
