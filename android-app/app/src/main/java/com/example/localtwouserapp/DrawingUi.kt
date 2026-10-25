package com.example.localtwouserapp

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable fun DrawingPage(store: SharedStore, notify: (String) -> Unit) {
    val context = LocalContext.current
    val entries by store.entries.collectAsStateWithLifecycle()
    val revision by store.revision.collectAsStateWithLifecycle()
    val own = DrawingImages.own(entries, store.deviceId)
    val partner = DrawingImages.partner(entries, store.deviceId)
    val draftKey = "drawing-${store.roomId}-${store.deviceId}"
    val prefs = remember { context.getSharedPreferences("drawing_drafts", 0) }
    var dirty by remember(draftKey) { mutableStateOf(prefs.contains(draftKey)) }
    val board = remember(draftKey) { DrawingBoardView(context).apply {
        loadOwn(prefs.getString(draftKey, null) ?: own?.fields?.get("image").orEmpty())
    } }
    var erasing by remember { mutableStateOf(false) }
    var color by remember { mutableIntStateOf(android.graphics.Color.rgb(123, 59, 74)) }
    var clearConfirm by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(board, lifecycle) {
        fun saveDraft() { if (dirty) prefs.edit().putString(draftKey, board.snapshot()).apply() }
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) saveDraft() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); saveDraft() }
    }
    LaunchedEffect(own?.version) { if (!dirty) board.loadOwn(own?.fields?.get("image").orEmpty()) }
    PageColumn {
        Text("Our drawing", style = MaterialTheme.typography.headlineMedium)
        Text("Draw together. Eraser and Clear affect only your drawing.", style = MaterialTheme.typography.bodySmall)
        AndroidView(factory = { board }, update = {
            it.inkColor = color; it.erasing = erasing; it.changed = { dirty = true }
            it.ownOnTop = store.deviceId > partner?.author.orEmpty()
            it.setPartner(partner?.fields?.get("image").orEmpty())
        }, modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp)))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Wine" to 0xFF7B3B4A, "Rose" to 0xFFB56576, "Ink" to 0xFF3B3235, "Teal" to 0xFF288C85).forEach { (name, value) ->
                FilterChip(!erasing && color == value.toInt(), { color = value.toInt(); erasing = false },
                    modifier = Modifier.semantics { contentDescription = "$name pen" }, label = { Text("●", color = Color(value)) })
            }
            FilterChip(erasing, { erasing = !erasing }, label = { Text("Eraser") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { clearConfirm = true }, modifier = Modifier.weight(1f)) { Text("Clear mine") }
            Button(enabled = dirty, onClick = {
                val image = board.snapshot()
                if (image.length > DrawingImages.MAX_ENCODED) notify("This drawing is too detailed. Erase a little and try again.")
                else runCatching { store.put(Entry(id = "drawing-${store.deviceId}", type = "drawing", fields = mapOf("image" to image))) }
                    .onSuccess { dirty = false; prefs.edit().remove(draftKey).apply(); notify("Drawing saved") }
                    .onFailure { notify(it.localizedMessage ?: "Could not save drawing") }
            }, modifier = Modifier.weight(1f)) { Text("Share") }
        }
        val pending = remember(revision, own?.id) { own?.let { !store.uploaded(it.id) } ?: false }
        if (pending) Text("Waiting to sync", color = Wine)
        TextButton(onClick = {
            val manager = AppWidgetManager.getInstance(context)
            if (android.os.Build.VERSION.SDK_INT >= 26 && manager.isRequestPinAppWidgetSupported)
                manager.requestPinAppWidget(ComponentName(context, DrawingWidget::class.java), null, null)
            else notify("Long-press your home screen, open Widgets, and choose US drawing.")
        }) { Text("Add drawing widget") }
    }
    if (clearConfirm) AlertDialog(onDismissRequest = { clearConfirm = false }, title = { Text("Clear your drawing?") },
        text = { Text("Your partner's drawing stays. Tap Share to sync your cleared layer.") },
        confirmButton = { TextButton(onClick = { board.clearOwn(); clearConfirm = false }) { Text("Clear mine") } },
        dismissButton = { TextButton(onClick = { clearConfirm = false }) { Text("Cancel") } })
}
