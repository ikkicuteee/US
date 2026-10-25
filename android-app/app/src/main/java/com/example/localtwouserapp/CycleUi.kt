package com.example.localtwouserapp

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable fun CycleCard(store: SharedStore, entries: List<Entry>, selected: LocalDate, notify: (String) -> Unit) {
    val today = LocalDate.now()
    val context = LocalContext.current
    val periods = CycleTracker.periods(entries)
    val estimate = CycleTracker.estimate(entries, today)
    val daily = CycleTracker.day(entries, selected)
    val revision by store.revision.collectAsStateWithLifecycle()
    val pending = remember(entries, revision) { entries.any { it.type == "cycle" && !store.uploaded(it.id) } }
    var editingPeriod by remember { mutableStateOf(false) }
    var periodEntry by remember { mutableStateOf<Entry?>(null) }
    var editingDay by remember { mutableStateOf(false) }
    var editingSettings by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Entry?>(null) }
    var allHistory by rememberSaveable { mutableStateOf(false) }
    fun save(entry: Entry, original: Entry?): String? = runCatching {
        val current = store.entries.value.firstOrNull { it.id == entry.id && !it.deleted }
        check(current?.version == original?.version) { "This record changed on another phone. Reopen it before saving." }
        if (entry.fields["kind"] == "period") {
            val start = CycleTracker.date(entry.date) ?: error("Choose a valid start date")
            val end = entry.fields["end"].orEmpty().takeIf { it.isNotEmpty() }?.let { CycleTracker.date(it) ?: error("Choose a valid end date") }
            CycleTracker.periodError(store.entries.value, entry.id, start, end, LocalDate.now())?.let { error(it) }
        }
        if (entry.fields["kind"] == "day") check(CycleTracker.date(entry.date)!! <= LocalDate.now()) { "Daily logs cannot be in the future." }
        store.put(entry)
        notify("Cycle record saved · syncs with ${store.partnerName} when connected")
    }.exceptionOrNull()?.localizedMessage
    SoftCard {
        Text("Our cycle tracker", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Wine)
        Text("Shared with ${store.partnerName}. Both of you can view and edit dates, symptoms and notes.", fontSize = 12.sp)
        if (pending) Text("Cycle changes waiting to sync", fontSize = 12.sp, color = Rose)
        if (periods.isEmpty()) Text("Start by logging the first day of a period. Add the end date when it finishes.")
        else {
            val latest = periods.lastOrNull { it.start <= today }
            latest?.let {
                Text("Day ${ChronoUnit.DAYS.between(it.start, today) + 1} since the last logged start", fontWeight = FontWeight.Bold)
                Text(if (it.end == null) "Period marked as ongoing since ${it.start}" else "Last period: ${it.start} to ${it.end}", fontSize = 13.sp)
            }
        }
        if (estimate.next != null) Text("Estimated next start: ${estimate.next}", color = Rose, fontWeight = FontWeight.Bold)
        Text(estimate.explanation, fontSize = 12.sp)
        if (estimate.intervals.isNotEmpty()) Text("Recent recorded cycles: ${estimate.intervals.joinToString(", ")} days", fontSize = 12.sp)
        Text("Dates are estimates, not confirmed ovulation or contraception advice. Tracking alone cannot identify safe days.", fontSize = 12.sp, color = Wine)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { periodEntry = null; editingPeriod = true }, modifier = Modifier.weight(1f)) { Text("Log period") }
            OutlinedButton(enabled = selected <= today, onClick = { editingDay = true }, modifier = Modifier.weight(1f)) { Text("Daily log") }
        }
        Text("Selected day: $selected", fontWeight = FontWeight.Bold)
        if (selected > today) Text("Future dates show estimates only. Daily logs become available on the day.", fontSize = 12.sp)
        else if (daily == null) Text("No daily log yet. Flow or spotting entries do not automatically start a period.", fontSize = 12.sp)
        else {
            Text(listOf("flow", "mood", "energy", "sleep").joinToString(" · ") { "${it.replaceFirstChar(Char::uppercase)}: ${daily.fields[it]}" }, fontSize = 13.sp)
            Text("Pain: ${daily.fields["pain"].orEmpty().ifEmpty { "not logged" }}" + if (!daily.fields["pain"].isNullOrEmpty()) "/10" else "", fontSize = 13.sp)
            daily.fields["symptoms"]?.takeIf { it.isNotEmpty() }?.let { Text(it.replace("|", " · "), fontSize = 13.sp) }
            if (daily.text.isNotBlank()) Text(daily.text)
            TextButton(onClick = { deleting = daily }) { Text("Delete daily log") }
        }
        Text("Period history", fontWeight = FontWeight.Bold)
        (if (allHistory) periods.asReversed() else periods.asReversed().take(6)).forEach { log ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${log.start} → ${log.end ?: "ongoing"}", fontSize = 13.sp)
                    log.end?.let { Text("${ChronoUnit.DAYS.between(log.start, it) + 1} days recorded", fontSize = 12.sp, color = Rose) }
                }
                TextButton(onClick = { periodEntry = log.entry; editingPeriod = true }) { Text("Edit") }
                TextButton(onClick = { deleting = log.entry }) { Text("Delete") }
            }
        }
        if (periods.size > 6) TextButton(onClick = { allHistory = !allHistory }) { Text(if (allHistory) "Show recent periods" else "Show all periods") }
        TextButton(onClick = { editingSettings = true }) { Text("Cycle estimate settings") }
        Text("If pain or bleeding interrupts daily activities, discuss the log with a clinician.", fontSize = 12.sp)
        TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(CycleTracker.GUIDE))) }) { Text("Read the UPMC tracking guide") }
    }
    if (editingPeriod) PeriodEditor(periodEntry, minOf(selected, today), { editingPeriod = false }) { entry ->
        save(entry, periodEntry).also { if (it == null) editingPeriod = false }
    }
    if (editingDay) key(selected) { CycleDayEditor(daily, selected, { editingDay = false }) { entry, original ->
        save(entry, original).also { if (it == null) editingDay = false }
    } }
    if (editingSettings) CycleSettingsEditor(CycleTracker.settings(entries), { editingSettings = false }) { entry, original ->
        save(entry, original).also { if (it == null) editingSettings = false }
    }
    deleting?.let { entry ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete this shared cycle record?") },
            text = { Text("It will be removed from both calendars after syncing. Other cycle records stay in place.") },
            confirmButton = { TextButton(onClick = {
                val current = store.entries.value.firstOrNull { it.id == entry.id && !it.deleted }
                if (current?.version != entry.version) notify("The record changed. Review it before deleting.")
                else { store.put(current!!.copy(deleted = true, text = "")); notify("Cycle record deleted") }
                deleting = null
            }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } })
    }
}

@Composable private fun CycleDateButton(label: String, value: LocalDate, choose: (LocalDate) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(onClick = {
        DatePickerDialog(context, { _, year, month, day -> choose(LocalDate.of(year, month + 1, day)) },
            value.year, value.monthValue - 1, value.dayOfMonth).apply { datePicker.maxDate = System.currentTimeMillis() }.show()
    }) { Text("$label: $value") }
}

@Composable private fun PeriodEditor(original: Entry?, selected: LocalDate, dismiss: () -> Unit, save: (Entry) -> String?) {
    val initial = remember { original ?: Entry(id = "cycle-period-${java.util.UUID.randomUUID()}", type = "cycle", date = selected.toString(), fields = mapOf("kind" to "period", "end" to "")) }
    var start by rememberSaveable { mutableStateOf(initial.date) }
    var end by rememberSaveable { mutableStateOf(initial.fields["end"].orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(if (original == null) "Log period" else "Edit period") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Choose actual bleeding dates. The start is day 1 of the cycle.")
            CycleDateButton("Start", LocalDate.parse(start)) { start = it.toString() }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(end.isEmpty(), { ongoing -> end = if (ongoing) "" else maxOf(LocalDate.parse(start), selected).toString() })
                Text("Still ongoing")
            }
            if (end.isNotEmpty()) CycleDateButton("End", LocalDate.parse(end)) { end = it.toString() }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = { error = save(initial.copy(date = start, fields = mapOf("kind" to "period", "end" to end))) }) { Text("Save period") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

@Composable private fun CycleChoices(label: String, values: List<String>, selected: String, choose: (String) -> Unit) {
    Text(label, fontWeight = FontWeight.Bold)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        values.forEach { value -> FilterChip(selected == value, { choose(value) }, label = { Text(value) }) }
    }
}

@Composable private fun CycleDayEditor(original: Entry?, date: LocalDate, dismiss: () -> Unit, save: (Entry, Entry?) -> String?) {
    val initial = remember { original }
    var flow by rememberSaveable { mutableStateOf(initial?.fields?.get("flow") ?: "Not logged") }
    var mood by rememberSaveable { mutableStateOf(initial?.fields?.get("mood") ?: "Not logged") }
    var energy by rememberSaveable { mutableStateOf(initial?.fields?.get("energy") ?: "Not logged") }
    var sleep by rememberSaveable { mutableStateOf(initial?.fields?.get("sleep") ?: "Not logged") }
    var pain by rememberSaveable { mutableStateOf(initial?.fields?.get("pain").orEmpty()) }
    var symptoms by rememberSaveable { mutableStateOf(initial?.fields?.get("symptoms").orEmpty()) }
    var notes by rememberSaveable { mutableStateOf(initial?.text.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Daily log · $date") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Shared with your partner. Swipe choices sideways to see more.", fontSize = 12.sp)
            CycleChoices("Flow", CycleTracker.flows, flow) { flow = it }
            CycleChoices("Mood", CycleTracker.moods, mood) { mood = it }
            CycleChoices("Energy", CycleTracker.energies, energy) { energy = it }
            CycleChoices("Sleep", CycleTracker.sleeps, sleep) { sleep = it }
            OutlinedTextField(pain, { if (it.isEmpty() || (it.length <= 2 && it.all(Char::isDigit))) pain = it }, label = { Text("Pain 0–10 (optional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Text("Symptoms", fontWeight = FontWeight.Bold)
            CycleTracker.symptoms.forEach { symptom ->
                val active = symptom in symptoms.split("|")
                val toggle = { symptoms = (if (active) symptoms.split("|").filter { it != symptom } else symptoms.split("|") + symptom).filter { it.isNotEmpty() }.joinToString("|") }
                Row(Modifier.fillMaxWidth().clickable { toggle() }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(active, { toggle() }); Text(symptom)
                }
            }
            OutlinedTextField(notes, { notes = it.take(1000) }, label = { Text("Notes (shared)") }, maxLines = 4)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = {
        error = if (pain.isNotEmpty() && pain.toIntOrNull() !in 0..10) "Pain must be between 0 and 10, or left blank."
        else save(CycleTracker.dailyEntry(date, flow, pain.toIntOrNull()?.toString().orEmpty(), mood, energy, sleep, symptoms.split("|").filter { it.isNotEmpty() }.toSet(), notes.trim()), initial)
    }) { Text("Save daily log") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

@Composable private fun CycleSettingsEditor(original: Entry?, dismiss: () -> Unit, save: (Entry, Entry?) -> String?) {
    val initial = remember { original }
    var enabled by rememberSaveable { mutableStateOf(initial?.fields?.get("predict") != "false") }
    var length by rememberSaveable { mutableStateOf(initial?.fields?.get("length").orEmpty().takeUnless { it == "0" }.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Cycle estimates") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Switch(enabled, { enabled = it }); Text("Show next-period estimate") }
            OutlinedTextField(length, { if (it.length <= 3 && it.all(Char::isDigit)) length = it }, label = { Text("Usual cycle length in days (optional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Text("Leave blank to use recent recorded cycles after three start dates. An entered length overrides history. The accepted range of 1–365 is a data-entry limit, not a normal-cycle range.", fontSize = 12.sp)
            Text("Turn estimates off if they are not useful, including with irregular cycles or circumstances that change bleeding patterns. Dates do not establish ovulation or pregnancy.", fontSize = 12.sp)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = {
        error = if (length.isNotEmpty() && length.toIntOrNull() !in 1..365) "Enter 1–365 days or leave blank."
        else save(Entry(id = "cycle-settings", type = "cycle", fields = mapOf("kind" to "settings", "predict" to enabled.toString(), "length" to (length.toIntOrNull() ?: 0).toString())), initial)
    }) { Text("Save settings") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}
