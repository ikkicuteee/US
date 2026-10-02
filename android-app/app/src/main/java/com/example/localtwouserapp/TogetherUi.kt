package com.example.localtwouserapp

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker


@Composable fun ProfilePage(store: SharedStore, entries: List<Entry>, notify: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val partnerName = PartnerNames.from(entries, store.deviceId)
    val me = entries.firstOrNull { it.id == "profile-${store.deviceId}" }
    val partner = entries.lastOrNull { it.type == "profile" && it.author != store.deviceId }
    var name by rememberSaveable { mutableStateOf(me?.text ?: store.name) }
    var hobbies by rememberSaveable { mutableStateOf(me?.fields?.get("hobbies").orEmpty()) }
    var movies by rememberSaveable { mutableStateOf(me?.fields?.get("movies").orEmpty()) }
    var anime by rememberSaveable { mutableStateOf(me?.fields?.get("anime").orEmpty()) }
    var games by rememberSaveable { mutableStateOf(me?.fields?.get("games").orEmpty()) }
    var photo by rememberSaveable { mutableStateOf(me?.fields?.get("avatar").orEmpty()) }
    var crop by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var photoBusy by remember { mutableStateOf(false) }
    DisposableEffect(crop) { val owned = crop; onDispose { owned?.recycle() } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) scope.launch {
        photoBusy = true
        runCatching { withContext(Dispatchers.IO) { PhotoDecoder.decode(context, uri) } }.onSuccess { crop = it }.onFailure { notify("Could not use that picture") }
        photoBusy = false
    } }
    crop?.let { source -> ProfileCropDialog(source, { crop = null }, { photo = it; crop = null }, notify) }
    PageColumn {
        Heading("All the little things.", "The favorites, quirks, and stories that make you, you.")
        SoftCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val uri = remember(photo) { ProfilePhotos.decode(photo) }
                if (uri != null) AsyncImage(uri, "Your profile picture", modifier = Modifier.size(80.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                else Box(Modifier.size(80.dp).background(Blush, CircleShape), contentAlignment = Alignment.Center) { Text("♥", color = Wine, fontSize = 32.sp) }
                TextButton(enabled = !photoBusy, onClick = { picker.launch(arrayOf("image/*")) }) { Text(if (photoBusy) "Opening…" else "Choose your picture") }
            }
            if (photo.isNotBlank()) TextButton(enabled = !photoBusy, onClick = {
                ProfilePhotos.decode(photo)?.let { bytes -> crop = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
            }) { Text("Adjust / rotate current picture") }
            OutlinedTextField(name, { name = it.take(40) }, label = { Text("Your name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(hobbies, { hobbies = it.take(200) }, label = { Text("Things I love doing") }, placeholder = { Text("Cooking, late-night walks, drawing…") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(movies, { movies = it.take(100) }, label = { Text("Favorite movie genres") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(anime, { anime = it.take(100) }, label = { Text("Favorite anime genres") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(games, { games = it.take(100) }, label = { Text("Favorite game genres") }, modifier = Modifier.fillMaxWidth())
            Button(enabled = name.isNotBlank(), onClick = { store.prefs.edit().putString("name_${store.deviceId}", name).apply(); store.put(Entry(id = "profile-${store.deviceId}", type = "profile", text = name, fields = mapOf("avatar" to photo, "hobbies" to hobbies, "movies" to movies, "anime" to anime, "games" to games))); notify("Profile saved for $partnerName ♥") }, modifier = Modifier.fillMaxWidth()) { Text("Save my little world") }
        }
        SoftCard {
            Text(partner?.text ?: "${partnerName}'s little world", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (partner == null) Text("Their profile will appear here after they save it and you connect.")
            else {
                if (!partner.fields["avatar"].isNullOrBlank()) AsyncImage(remember(partner.fields["avatar"]) { ProfilePhotos.decode(partner.fields["avatar"].orEmpty()) }, "Partner profile picture", modifier = Modifier.size(80.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                listOf("hobbies" to "Happiest when", "movies" to "Movie night", "anime" to "Anime mood", "games" to "Let's play").forEach { (key, label) -> Text(label, color = Rose, fontSize = 11.sp); Text(partner.fields[key].orEmpty().ifBlank { "A little mystery, for now" }) }
            }
        }
    }
}
