package com.example.localtwouserapp

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PhotoFrameActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id != AppWidgetManager.INVALID_APPWIDGET_ID && !PhotoFrameWidget.owns(this, id)) { finish(); return }
        setContent {
            MaterialTheme(colorScheme = UsColors) {
                if (id == AppWidgetManager.INVALID_APPWIDGET_ID) AddFrames()
                else ConfigureFrame(id)
            }
        }
    }
    @Composable private fun AddFrames() {
        Scaffold(containerColor = Mist) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Heading("A photo to keep close.", "Choose a frame for your home screen.")
                SoftCard {
                    Text("Each frame has its own photo, saved only on this phone.")
                    Text("After adding it, tap the frame to choose a picture. Long-press it to resize. Exact grid sizes depend on your home-screen launcher.")
                }
                val labels = listOf("2 × 2 · Square", "3 × 2 · Wide", "2 × 3 · Tall", "3 × 3 · Large square")
                PhotoFrameWidget.providers.forEachIndexed { index, provider ->
                    OutlinedButton(onClick = {
                        val manager = AppWidgetManager.getInstance(this@PhotoFrameActivity)
                        if (Build.VERSION.SDK_INT >= 26 && manager.isRequestPinAppWidgetSupported) {
                            // Some launchers do not configure pinned widgets; the callback renders
                            // a tappable placeholder once the launcher allocates the actual widget ID.
                            val callback = PendingIntent.getBroadcast(this@PhotoFrameActivity, index,
                                Intent(this@PhotoFrameActivity, provider).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE),
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                            val accepted = manager.requestPinAppWidget(ComponentName(this@PhotoFrameActivity, provider), null, callback)
                            Toast.makeText(this@PhotoFrameActivity,
                                if (accepted) "Confirm on your home screen, then tap the frame to choose its photo."
                                else "Long-press your home screen and open Widgets → US.", Toast.LENGTH_LONG).show()
                        } else Toast.makeText(this@PhotoFrameActivity,
                            "Long-press your home screen, choose Widgets, then US photo frame.", Toast.LENGTH_LONG).show()
                    }, modifier = Modifier.fillMaxWidth()) { Text(labels[index]) }
                }
                TextButton(onClick = { finish() }) { Text("Back") }
            }
        }
    }
    @Composable private fun ConfigureFrame(id: Int) {
        var original by remember { mutableStateOf<FramePhoto?>(null) }
        var loaded by remember { mutableStateOf(false) }
        var chosen by rememberSaveable { mutableStateOf<String?>(null) }
        var fit by rememberSaveable { mutableStateOf(false) }
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()
        LaunchedEffect(id) {
            original = withContext(Dispatchers.IO) { PhotoFrameStore(this@PhotoFrameActivity).use { it.get(id) } }
            if (chosen == null) fit = original?.fit ?: false
            loaded = true
        }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) { chosen = uri.toString(); error = null }
        }
        val preview = chosen ?: original?.let { PhotoFrameStore(this@PhotoFrameActivity).use { store -> store.file(it).path } }
        val options = AppWidgetManager.getInstance(this).getAppWidgetOptions(id)
        val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180).coerceAtLeast(1)
        val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 180).coerceAtLeast(1)
        Scaffold(containerColor = Mist) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Heading("Your photo frame.", "Tap this widget any time to change its picture.")
                Box(Modifier.fillMaxWidth().heightIn(max = 320.dp).aspectRatio((width.toFloat() / height).coerceIn(.5f, 2f))
                    .clip(RoundedCornerShape(24.dp)).background(Rose).padding(6.dp), contentAlignment = Alignment.Center) {
                    if (preview == null) Text("Choose a photo", color = Color.White)
                    else AsyncImage(preview, "Frame preview", contentScale = if (fit) ContentScale.Fit else ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)).background(Mist))
                }
                Button(enabled = !busy && loaded, onClick = { picker.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (preview == null) "Choose photo" else "Choose another photo")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilterChip(selected = !fit, enabled = !busy, onClick = { fit = false }, label = { Text("Fill frame") })
                    FilterChip(selected = fit, enabled = !busy, onClick = { fit = true }, label = { Text("Whole photo") })
                }
                Text("A copy is saved on this phone. Each widget can show a different photo.", color = Wine)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(enabled = loaded && preview != null && !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                    busy = true; error = null
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                check(PhotoFrameWidget.owns(this@PhotoFrameActivity, id)) { "This frame was removed. Add a new widget." }
                                PhotoFrameStore(this@PhotoFrameActivity).use { store ->
                                    store.save(id, chosen?.let(Uri::parse), fit)
                                    if (!PhotoFrameWidget.owns(this@PhotoFrameActivity, id)) { store.delete(id); error("This frame was removed.") }
                                }
                                PhotoFrameWidget.update(this@PhotoFrameActivity, id)
                            }
                            setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                            finish()
                        } catch (e: Exception) {
                            error = "Could not save this picture. Choose a photo again. ${e.localizedMessage.orEmpty()}"
                        } finally { busy = false }
                    }
                }) { Text(if (busy) "Saving…" else "Save frame") }
                TextButton(enabled = !busy, onClick = { finish() }) { Text("Cancel") }
            }
        }
    }
}

