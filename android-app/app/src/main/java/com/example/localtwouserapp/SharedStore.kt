package com.example.localtwouserapp

import android.content.Context
import android.util.AtomicFile
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.time.LocalDate
import java.util.UUID

data class Entry(
    val id: String = UUID.randomUUID().toString(),
    val type: String = "message",
    val author: String = "",
    val version: Long = 1,
    val editor: String = "",
    val time: Long = System.currentTimeMillis(),
    val text: String = "",
    val date: String = "",
    val done: Boolean = false,
    val deleted: Boolean = false,
    val amount: Long = 0,
    val fields: Map<String, String> = emptyMap(),
) {
    fun wire(): Map<String, Any> = mapOf("id" to id, "type" to type, "author" to author,
        "version" to version, "editor" to editor, "time" to time, "text" to text,
        "date" to date, "done" to done, "deleted" to deleted, "amount" to amount, "fields" to fields)
}

object MergeRules {
    fun winner(a: Entry, b: Entry): Entry = when {
        SavingsGoals.closed(a) && !SavingsGoals.closed(b) -> a
        SavingsGoals.closed(b) && !SavingsGoals.closed(a) -> b
        a.version != b.version -> if (a.version > b.version) a else b
        a.editor != b.editor -> if (a.editor > b.editor) a else b
        else -> a
    }
    fun merge(local: List<Entry>, incoming: List<Entry>): List<Entry> {
        val result = local.associateBy { it.id }.toMutableMap()
        incoming.forEach { next -> result[next.id] = result[next.id]?.let { winner(it, next) } ?: next }
        return result.values.sortedWith(compareBy<Entry> { it.time }.thenBy { it.id })
    }
    fun cents(input: String): Long? = runCatching {
        require(input.length <= 20 && Regex("\\d+(\\.\\d{1,2})?").matches(input.trim()))
        input.trim().toBigDecimal().setScale(2, java.math.RoundingMode.UNNECESSARY)
            .movePointRight(2).longValueExact().takeIf { it > 0 && it <= 100_000_000_000L }
    }.getOrNull()
    fun validInvite(code: String) = Regex("[a-f0-9]{64}").matches(code)
    fun acknowledged(current: Entry, sent: Entry, remote: Entry) = current == sent || winner(current, remote) == remote
}

data class DiskState(val entries: List<Entry> = emptyList(), val pending: Set<String> = emptySet())

/** Account/space isolation and a durable outbox. Original Tailscale data is left untouched. */
@OptIn(ExperimentalCoroutinesApi::class)
class SharedStore private constructor(val context: Context) {
    val prefs = context.getSharedPreferences("us_pair", Context.MODE_PRIVATE)
    val gson = Gson()
    private var scopeKey = prefs.getString("firebase_scope", "signed-out").orEmpty()
    var deviceId: String = prefs.getString("firebase_uid", "").orEmpty(); private set
    var roomId: String = prefs.getString("firebase_room", "").orEmpty(); private set
    private fun disk(key: String) = AtomicFile(File(context.filesDir, "firebase-$key.json"))
    private fun read(key: String) = runCatching {
        disk(key).openRead().use { gson.fromJson(it.reader(), DiskState::class.java) }
    }.getOrNull() ?: DiskState()
    private var data = read(scopeKey)
    val entries = MutableStateFlow(data.entries)
    val revision = MutableStateFlow(0L)
    val error = MutableStateFlow<String?>(null)
    private val writer = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    val name get() = entries.value.firstOrNull { it.id == "profile-$deviceId" }?.text
        ?: prefs.getString("name_$deviceId", "You").orEmpty()
    val configured get() = deviceId.isNotEmpty() && roomId.isNotEmpty()
    val partnerName get() = PartnerNames.from(entries.value, deviceId)

    @Synchronized fun selectSpace(uid: String, room: String) {
        val key = if (uid.isEmpty() || room.isEmpty()) "signed-out" else "$uid-$room"
        deviceId = uid; roomId = room
        prefs.edit().putString("firebase_uid", uid).putString("firebase_room", room).putString("firebase_scope", key).apply()
        if (key == scopeKey) return
        scopeKey = key; data = read(key); entries.value = data.entries; revision.value++; error.value = null
        TodayWidget.refresh(context)
        PlanReminders.schedule(context)
    }
    @Synchronized fun put(entry: Entry): Entry {
        check(configured) { "Sign in and connect with your partner first." }
        val old = data.entries.firstOrNull { it.id == entry.id }
        check(old == null || !SavingsGoals.closed(old)) { "This goal has already moved to history." }
        val next = entry.copy(author = old?.author ?: deviceId, editor = deviceId, version = (old?.version ?: 0) + 1)
        data = data.copy(entries = MergeRules.merge(data.entries, listOf(next)), pending = data.pending + next.id)
        publish()
        FirebaseSync.instance?.sendPending()
        return next
    }
    @Synchronized fun merge(incoming: List<Entry>): List<Entry> {
        val unseen = incoming.filter { next -> data.entries.none { it.id == next.id } }
        val closed = incoming.filter { SavingsGoals.closed(it) }.associateBy { it.id }
        val merged = MergeRules.merge(data.entries, incoming).map { closed[it.id] ?: it }
        if (merged != data.entries || data.pending.any { it in closed }) {
            data = data.copy(entries = merged, pending = data.pending - closed.keys); publish()
        }
        return unseen
    }
    @Synchronized fun pending() = data.entries.filter { it.id in data.pending }
    @Synchronized fun uploaded(sent: Entry, remote: Entry?) {
        val current = data.entries.firstOrNull { it.id == sent.id } ?: return
        val resolved = remote ?: sent
        val clear = SavingsGoals.closed(resolved) || MergeRules.acknowledged(current, sent, resolved)
        val merged = MergeRules.merge(data.entries, listOf(resolved)).map { if (it.id == resolved.id && SavingsGoals.closed(resolved)) resolved else it }
        data = data.copy(entries = merged,
            pending = if (clear) data.pending - sent.id else data.pending)
        publish()
    }
    @Synchronized fun uploaded(id: String) = id !in data.pending
    suspend fun flush() { writer.async { check(error.value == null) { "Local changes could not be saved" } }.await() }
    private fun publish() {
        entries.value = data.entries; revision.value++
        val snapshot = data; val target = disk(scopeKey); val key = scopeKey
        writer.launch {
            var stream: java.io.FileOutputStream? = null
            try {
                stream = target.startWrite(); stream.write(gson.toJson(snapshot).toByteArray(Charsets.UTF_8))
                target.finishWrite(stream)
                if (key == scopeKey) { error.value = null; TodayWidget.refresh(context); PlanReminders.schedule(context) }
            } catch (e: Exception) {
                target.failWrite(stream)
                if (key == scopeKey) error.value = "Could not save changes: ${e.localizedMessage}"
            }
        }
    }
    fun today(date: LocalDate = LocalDate.now()) = PlanSchedule.on(entries.value, date)
    @Synchronized fun closeGoal(id: String, expectedVersion: Long): Entry {
        val goal = data.entries.firstOrNull { it.id == id } ?: error("This goal is no longer available.")
        check(goal.version == expectedVersion) { "This goal changed. Review it before closing." }
        val balance = data.entries.filter { it.type == "saving" && !it.deleted }.sumOf { it.amount }
        return put(SavingsGoals.close(goal, balance, System.currentTimeMillis()))
    }
    @Synchronized fun togglePlan(entry: Entry, date: LocalDate) {
        if (!PlanSchedule.isTodo(entry)) return
        val current = data.entries.firstOrNull { it.id == entry.id && !it.deleted } ?: return
        put(PlanSchedule.toggle(current, date))
    }
    companion object {
        @Volatile private var singleton: SharedStore? = null
        fun get(context: Context): SharedStore = singleton ?: synchronized(this) {
            singleton ?: SharedStore(context.applicationContext).also { singleton = it }
        }
    }
}

