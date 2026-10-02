package com.example.localtwouserapp

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable fun ProfileCropDialog(source: Bitmap, dismiss: () -> Unit, save: (String) -> Unit, notify: (String) -> Unit) {
    var turns by remember { mutableIntStateOf(0) }
    val rotated = remember(source, turns) {
        if (turns == 0) source else Bitmap.createBitmap(source, 0, 0, source.width, source.height,
            Matrix().apply { postRotate(turns * 90f) }, true)
    }
    DisposableEffect(rotated) { onDispose { if (rotated !== source) rotated.recycle() } }
    var x by remember { mutableFloatStateOf(.5f) }
    var y by remember { mutableFloatStateOf(.5f) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = { if (!busy) dismiss() }, title = { Text("Position your picture") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val bitmap = remember(rotated) { rotated.asImageBitmap() }
            Canvas(Modifier.fillMaxWidth().aspectRatio(1f).clip(CircleShape)) {
                val crop = PhotoCrop.bounds(rotated.width, rotated.height, x, y, zoom)
                drawImage(bitmap, srcOffset = IntOffset(crop.left, crop.top), srcSize = IntSize(crop.size, crop.size), dstSize = IntSize(size.width.toInt(), size.height.toInt()))
            }
            Text("Zoom")
            Slider(zoom, { zoom = it }, valueRange = 1f..3f, enabled = !busy)
            Text("Left / right")
            Slider(x, { x = it }, enabled = !busy)
            Text("Up / down")
            Slider(y, { y = it }, enabled = !busy)
            TextButton(enabled = !busy, onClick = { turns = (turns + 1) % 4; x = .5f; y = .5f }) { Text("Rotate 90°") }
        }
    }, confirmButton = { TextButton(enabled = !busy, onClick = {
        busy = true
        scope.launch {
            try {
                val encoded = withContext(Dispatchers.Default) { ProfilePhotos.encode(rotated, x, y, zoom) }
                save(encoded)
            } catch (_: Exception) { notify("Could not save this crop. Try another photo.") }
            finally { busy = false }
        }
    }) { Text(if (busy) "Saving…" else "Use picture") } }, dismissButton = { TextButton(enabled = !busy, onClick = dismiss) { Text("Cancel") } })
}
