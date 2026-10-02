package com.example.localtwouserapp

object SavingsGoals {
    fun closed(goal: Entry) = goal.type == "goal" && goal.fields["outcome"] in listOf("success", "failure")
    fun active(entries: List<Entry>) = entries.filter { it.type == "goal" && !it.deleted && !closed(it) }.sortedByDescending { it.time }
    fun history(entries: List<Entry>) = entries.filter { !it.deleted && closed(it) }.sortedByDescending { it.fields["closedAt"]?.toLongOrNull() ?: it.time }
    fun close(goal: Entry, balance: Long, now: Long): Entry {
        require(goal.type == "goal" && !goal.deleted && !closed(goal) && goal.amount > 0)
        require(!goal.fields["reason"].isNullOrBlank() && runCatching { java.time.LocalDate.parse(goal.date) }.isSuccess) {
            "Add a target date and reason in Edit our goal before moving it to history."
        }
        return goal.copy(fields = goal.fields + mapOf("outcome" to if (balance >= goal.amount) "success" else "failure",
            "finalBalance" to balance.toString(), "closedAt" to now.toString()))
    }
}

object PartnerNames {
    fun from(entries: List<Entry>, ownUid: String): String = entries.lastOrNull {
        it.type == "profile" && !it.deleted && it.author != ownUid && it.text.isNotBlank()
    }?.text?.trim()?.take(40) ?: "Your partner"
}
