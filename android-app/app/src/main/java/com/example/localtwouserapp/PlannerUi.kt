package com.example.localtwouserapp

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable fun CalendarPage(store: SharedStore, entries: List<Entry>, notify: (String) -> Unit) {
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var selectedText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val month = YearMonth.parse(monthText)
    val selected = LocalDate.parse(selectedText)
    var adding by remember { mutableStateOf<Boolean?>(null) }
    val context = LocalContext.current
    var showCycle by rememberSaveable { mutableStateOf(false) }
    val cyclePeriods = remember(entries) { CycleTracker.periods(entries) }
    val cycleEstimate = remember(entries, LocalDate.now()) { CycleTracker.estimate(entries, LocalDate.now()) }
    PageColumn {
        Heading("Days to look forward to.", "Together since September 1, 2021.")
        SoftCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { monthText = month.minusMonths(1).toString() }) { Icon(Icons.Default.ChevronLeft, "Previous month") }
                Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                IconButton(onClick = { monthText = month.plusMonths(1).toString() }) { Icon(Icons.Default.ChevronRight, "Next month") }
            }
            Row { listOf("M", "T", "W", "T", "F", "S", "S").forEach { Text(it, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = Rose, fontSize = 12.sp) } }
            val offset = month.atDay(1).dayOfWeek.value - 1
            val cells = ((offset + month.lengthOfMonth() + 6) / 7) * 7
            (0 until cells step 7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    (week until week + 7).forEach { index ->
                        val day = index - offset + 1
                        if (day !in 1..month.lengthOfMonth()) Spacer(Modifier.weight(1f).height(48.dp))
                        else {
                            val date = month.atDay(day)
                            val milestone = RelationshipDates.on(date).isNotEmpty()
                            val hasEvents = PlanSchedule.on(entries, date).isNotEmpty()
                            Column(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(15.dp)).background(if (date == selected) Wine else if (date == LocalDate.now()) Blush.copy(alpha = .35f) else Color.Transparent).clickable { selectedText = date.toString() }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text("$day", color = if (date == selected) Color.White else Ink, fontWeight = if (date == LocalDate.now()) FontWeight.Bold else FontWeight.Normal)
                                Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (milestone) Icon(Icons.Default.Favorite, "Our special day", tint = if (date == selected) Blush else Rose, modifier = Modifier.size(12.dp))
                                    else if (hasEvents) Box(Modifier.size(4.dp).background(if (date == selected) Blush else Rose, CircleShape))
                                    val recorded = CycleTracker.recorded(cyclePeriods, date, LocalDate.now())
                                    val predicted = date >= LocalDate.now() && cycleEstimate.next == date
                                    if (recorded || predicted) Box(Modifier.size(8.dp).border(1.dp, if (date == selected) Blush else Rose, CircleShape)
                                        .background(if (recorded) Blush else Color.Transparent, CircleShape)
                                        .semantics { contentDescription = if (recorded) "Logged period" else "Estimated period start" })
                                    if (CycleTracker.day(entries, date) != null) Box(Modifier.size(3.dp).background(if (date == selected) Color.White else Wine, CircleShape).semantics { contentDescription = "Cycle daily log" })
                                }
                            }
                        }
                    }
                }
            }
            TextButton(onClick = { monthText = YearMonth.now().toString(); selectedText = LocalDate.now().toString() }) { Text("Back to today") }
            if (cyclePeriods.isNotEmpty()) Text("Filled pink circle: logged period · outline: estimated start", fontSize = 11.sp, color = Wine)
        }
        OutlinedButton(onClick = { showCycle = !showCycle }, modifier = Modifier.fillMaxWidth()) { Text(if (showCycle) "Hide cycle tracker" else "Open cycle tracker") }
        if (showCycle) CycleCard(store, entries, selected, notify)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(selected.format(DateTimeFormatter.ofPattern("EEEE, MMM d")), fontWeight = FontWeight.Bold, fontSize = 20.sp); Text("Make time for each other", fontSize = 12.sp, color = Rose) }

        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { adding = true }, modifier = Modifier.weight(1f)) { Text("Add to-do") }
            OutlinedButton(onClick = { adding = false }, modifier = Modifier.weight(1f)) { Text("Add reminder") }
        }
        val plans = PlanSchedule.on(entries, selected)
        listOf(true to "To-dos", false to "Reminders & events").forEach { (todo, heading) ->
            Text(heading, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            val section = plans.filter { PlanSchedule.isTodo(it) == todo }
            if (section.isEmpty()) Text(if (todo) "No tasks for this day." else "No events for this day.", color = Rose)
            section.forEach { plan -> key(plan.id) { SoftCard {
                EventRow(plan, { store.togglePlan(plan, selected) })
                if (!RelationshipDates.isMilestone(plan)) TextButton(onClick = { store.put(plan.copy(deleted = true)) }) { Text("Remove") }
            } } }
        }
        OutlinedButton(onClick = {
            val manager = AppWidgetManager.getInstance(context)
            if (Build.VERSION.SDK_INT >= 26 && manager.isRequestPinAppWidgetSupported) manager.requestPinAppWidget(ComponentName(context, TodayWidget::class.java), null, null)
            else notify("Long-press your home screen, choose Widgets, then US.")
        }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Widgets, null); Spacer(Modifier.width(8.dp)); Text("Add today's plans to home screen") }
    }
    if (adding != null) PlanEditor(selected, adding == true, { adding = null }) { title, todo, repeat, at ->
        store.put(Entry(type = "event", text = title, date = selectedText,
            fields = mapOf("todo" to todo.toString(), "repeat" to repeat) + if (at.isEmpty()) emptyMap() else mapOf("at" to at)))
        adding = null
    }
}
@Composable private fun PlanEditor(date: LocalDate, todo: Boolean, dismiss: () -> Unit, save: (String, Boolean, String, String) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var repeat by rememberSaveable { mutableStateOf("once") }
    var at by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    AlertDialog(onDismissRequest = dismiss, title = { Text(if (todo) "Add to-do" else "Add reminder") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy")))
            OutlinedTextField(title, { title = it.take(200) }, label = { Text(if (todo) "Task" else "Event") })
            Text("Repeat", fontWeight = FontWeight.Bold)
            listOf("once" to "Does not repeat", "monthly" to "Monthly", "annual" to "Annual").forEach { (value, label) ->
                Row(Modifier.fillMaxWidth().clickable { repeat = value }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(repeat == value, { repeat = value }); Text(label)
                }
            }
            OutlinedButton(onClick = {
                val time = runCatching { java.time.LocalTime.parse(at) }.getOrDefault(java.time.LocalTime.now())
                android.app.TimePickerDialog(context, { _, hour, minute -> at = "%02d:%02d".format(java.util.Locale.ROOT, hour, minute) }, time.hour, time.minute, true).show()
            }) { Text(if (at.isEmpty()) "Set time (24-hour)" else "Time: $at") }
            if (at.isNotEmpty()) TextButton(onClick = { at = "" }) { Text("Remove time") }
            if (at.isNotEmpty()) Text("A reminder is scheduled on each phone after syncing. Android may delay it during sleep.", fontSize = 12.sp)
            if (repeat != "once") Text("Each date has its own checkmark. Shorter months use their last day.", fontSize = 12.sp)
        }
    }, confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = { save(title.trim(), todo, repeat, at) }) { Text(if (todo) "Save to-do" else "Save reminder") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

@Composable fun EventRow(entry: Entry, toggle: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (RelationshipDates.isMilestone(entry)) {
            Icon(Icons.Default.Favorite, null, tint = Rose, modifier = Modifier.padding(end = 12.dp).size(24.dp))
            Column {
                Text(entry.text, fontWeight = FontWeight.Bold, color = Wine)
                Text(entry.fields["detail"].orEmpty(), color = Rose, fontSize = 12.sp)
            }
        } else {
            if (PlanSchedule.isTodo(entry)) Checkbox(entry.done, { toggle() })
            else Icon(Icons.Default.Event, null, tint = Rose, modifier = Modifier.padding(end = 12.dp).size(24.dp))
            Column { Text(entry.text, fontWeight = FontWeight.Medium, textDecoration = if (PlanSchedule.isTodo(entry) && entry.done) TextDecoration.LineThrough else null)
                Text(listOfNotNull(entry.fields["at"], if (PlanSchedule.isTodo(entry) && entry.done) "Complete" else if (entry.fields["todo"] == "true") "To-do" else "Reminder",
                    PlanSchedule.repeat(entry).takeIf { it != "once" }?.replaceFirstChar { it.uppercase() }).joinToString(" · "), color = Rose, fontSize = 12.sp) }
        }
    }
}
@Composable fun SavingsPage(store: SharedStore, entries: List<Entry>, notify: (String) -> Unit) {
    val ledger = entries.filter { it.type == "saving" && !it.deleted }
    val balance = ledger.sumOf { it.amount }
    val active = SavingsGoals.active(entries)
    val history = SavingsGoals.history(entries)
    var selectedGoal by rememberSaveable { mutableStateOf<String?>(null) }
    val goal = active.firstOrNull { it.id == selectedGoal } ?: active.firstOrNull()
    var closing by remember { mutableStateOf<Entry?>(null) }
    val partnerName = PartnerNames.from(entries, store.deviceId)
    var amount by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var goalEditor by remember { mutableStateOf(false) }
    var transaction by remember { mutableStateOf<String?>(null) }
    var showHistory by remember { mutableStateOf(false) }
    PageColumn {
        Text("Savings", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        SoftCard {
            Text(goal?.text ?: "Shared balance", color = Rose, fontWeight = FontWeight.Bold)
            Text(money(balance), color = Wine, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            val target = goal?.amount ?: 0L
            if (target > 0) {
                LinearProgressIndicator(progress = { (balance.toFloat() / target).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = Rose, trackColor = Mist)
                Text("of ${money(target)} · ${((balance.toDouble() / target) * 100).toInt()}%", fontSize = 13.sp)
            }
            if (!goal?.date.isNullOrBlank()) Text("Target date: ${goal?.date}", color = Wine)
            TextButton(onClick = { goalEditor = true }) { Text(if (goal == null) "Add goal" else "Edit goal") }
            if (goal != null) OutlinedButton(onClick = { closing = goal }, modifier = Modifier.fillMaxWidth()) {
                Text(if (balance >= goal.amount) "Complete goal" else "End goal")
            }
            if (active.size > 1) {
                Text("Other open goals", fontWeight = FontWeight.Bold)
                active.filter { it.id != goal?.id }.forEach { other -> TextButton(onClick = { selectedGoal = other.id }) { Text(other.text) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { amount = ""; note = ""; transaction = "add" }, modifier = Modifier.weight(1f)) { Text("Add money") }
            OutlinedButton(onClick = { amount = ""; note = ""; transaction = "deduct" }, modifier = Modifier.weight(1f)) { Text("Deduct") }
        }
        TextButton(onClick = { showHistory = !showHistory }) { Text(if (showHistory) "Hide goal history" else "Goal history (${history.size})") }
        if (showHistory && history.isEmpty()) Text("No completed goals yet.", color = Rose)
        if (showHistory) history.forEach { archived -> key(archived.id) { SoftCard {
            Text(archived.text, fontWeight = FontWeight.Bold)
            Text(if (archived.fields["outcome"] == "success") "Success · target reached" else "Failure · ended below target", color = Wine)
            Text("${money(archived.fields["finalBalance"]?.toLongOrNull() ?: 0)} saved / ${money(archived.amount)} target")
            if (archived.date.isNotBlank()) Text("Target date: ${archived.date}", fontSize = 12.sp)
            archived.fields["closedAt"]?.toLongOrNull()?.let { stamp ->
                Text("Closed: " + java.text.DateFormat.getDateTimeInstance().format(java.util.Date(stamp)), fontSize = 12.sp)
            }
        } } }
        Text("Transactions", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        ledger.asReversed().take(100).forEach { item -> SoftCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(item.text.ifBlank { if (item.amount >= 0) "Deposit" else "Withdrawal" }, modifier = Modifier.weight(1f)); Text((if (item.amount > 0) "+" else "") + money(item.amount), fontWeight = FontWeight.Bold, color = Wine) }
            Text(if (item.author == store.deviceId) "You" else partnerName, color = Rose, fontSize = 11.sp)
        } }
    }
    transaction?.let { mode ->
        val deduct = mode == "deduct"
        AlertDialog(onDismissRequest = { transaction = null }, title = { Text(if (deduct) "Deduct money" else "Add money") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(amount, { amount = it.take(20) }, label = { Text("Amount") }, prefix = { Text("PHP ") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                if (deduct) OutlinedTextField(note, { note = it.take(100) }, label = { Text("Reason") })
            } },
            confirmButton = { TextButton(enabled = MergeRules.cents(amount) != null && (!deduct || note.isNotBlank()), onClick = {
                val cents = MergeRules.cents(amount) ?: return@TextButton
                runCatching { store.put(Entry(type = "saving", text = if (deduct) note.trim() else "", amount = if (deduct) -cents else cents)) }
                    .onSuccess { transaction = null }
                    .onFailure { notify(it.localizedMessage ?: "Could not save transaction") }
            }) { Text(if (deduct) "Deduct" else "Add") } },
            dismissButton = { TextButton(onClick = { transaction = null }) { Text("Cancel") } })
    }
    closing?.let { chosen ->
        AlertDialog(onDismissRequest = { closing = null }, title = { Text("Move this goal to history?") }, text = {
            Text("${chosen.text} will be recorded as ${if (balance >= chosen.amount) "a success" else "a failure"} using your saved balance when you confirm. Your savings balance and transactions stay available for your next goal.")
        }, confirmButton = { TextButton(onClick = {
            runCatching { store.closeGoal(chosen.id, chosen.version) }
                .onSuccess { notify("Goal saved in history") }
                .onFailure { notify(it.localizedMessage ?: "Could not close this goal") }
            closing = null
        }) { Text("Move to history") } }, dismissButton = { TextButton(onClick = { closing = null }) { Text("Cancel") } })
    }
    if (goalEditor) {
        val editing = remember { goal }
        var title by remember { mutableStateOf(editing?.text.orEmpty()) }
        var target by remember { mutableStateOf(editing?.amount?.let { java.math.BigDecimal.valueOf(it, 2).toPlainString() }.orEmpty()) }
        var date by remember { mutableStateOf(editing?.date.orEmpty()) }
        val context = LocalContext.current
        AlertDialog(onDismissRequest = { goalEditor = false }, title = { Text("Our savings goal") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it.take(100) }, label = { Text("Goal name") })
                OutlinedTextField(target, { target = it }, label = { Text("Goal amount (₱)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedButton(onClick = {
                    val initial = runCatching { LocalDate.parse(date) }.getOrDefault(LocalDate.now())
                    android.app.DatePickerDialog(context, { _, y, m, d -> date = LocalDate.of(y, m + 1, d).toString() }, initial.year, initial.monthValue - 1, initial.dayOfMonth).apply {
                        datePicker.minDate = LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                    }.show()
                }) { Text(if (date.isBlank()) "Choose target date (required)" else "Target date: $date") }
            }
        }, confirmButton = { TextButton(enabled = title.isNotBlank() && date.isNotBlank(), onClick = {
            val cents = MergeRules.cents(target)
            if (cents == null) notify("Enter a positive target with up to two decimal places.")
            else if (LocalDate.parse(date) < LocalDate.now()) notify("Choose today or a future target date.")
            else {
                runCatching { store.put(Entry(id = editing?.id ?: java.util.UUID.randomUUID().toString(), type = "goal", time = editing?.time ?: System.currentTimeMillis(), text = title.trim(), amount = cents, date = date, fields = editing?.fields.orEmpty() - "reason")) }
                    .onSuccess { goalEditor = false }.onFailure { notify(it.localizedMessage ?: "Could not save goal") }
            }
        }) { Text("Save our goal") } }, dismissButton = { TextButton(onClick = { goalEditor = false }) { Text("Cancel") } })
    }
}
fun money(cents: Long) = "₱" + java.text.DecimalFormat("#,##0.00").format(java.math.BigDecimal.valueOf(cents, 2))
