package com.example.localtwouserapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import java.util.UUID

val Blush = Color(0xFFD9A0B3)
val Rose = Color(0xFFB56576)
val Wine = Color(0xFF7B3B4A)
val Mist = Color(0xFFE9E3E6)
val Ink = Color(0xFF3B3235)
val UsColors = lightColorScheme(primary = Wine, onPrimary = Color.White, secondary = Rose,
    secondaryContainer = Blush.copy(alpha = .35f), onSecondaryContainer = Wine, background = Mist,
    onBackground = Ink, surface = Color(0xFFFFFAFC), onSurface = Ink, surfaceVariant = Mist, onSurfaceVariant = Wine)

class MainActivity : ComponentActivity() {
    val requestedPage = mutableStateOf("home")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        Notices.channels(this); FirebaseSync.get(this); handleIntent(intent)
        TodayWidget.refresh(this)
        PlanReminders.schedule(this)
        setContent { MaterialTheme(colorScheme = UsColors) { UsApp(this) } }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleIntent(intent) }
    private fun handleIntent(intent: Intent?) {
        requestedPage.value = intent?.getStringExtra("page") ?: "home"
    }
}
@Composable fun UsApp(activity: MainActivity) {
    val store = remember { SharedStore.get(activity) }
    val entries by store.entries.collectAsStateWithLifecycle()
    val sync = remember { FirebaseSync.get(activity) }
    val user by sync.user.collectAsStateWithLifecycle()
    val room by sync.room.collectAsStateWithLifecycle()
    val status by sync.status.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf(if (store.configured) "home" else "settings") }
    var fullMap by rememberSaveable { mutableStateOf(false) }
    val mapFullscreen = fullMap && page == "map" && user != null && room.isNotEmpty()
    val notice = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val notify: (String) -> Unit = { scope.launch { notice.showSnackbar(it) } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (!it) notify("Enable notifications in Settings to receive partner alerts.") }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        if (BackgroundLocation.canRestore(activity)) runCatching { BackgroundLocation.startFromScreen(activity) }
    }
    LaunchedEffect(activity.requestedPage.value) { if (store.configured) page = activity.requestedPage.value }
    val diskError by store.error.collectAsStateWithLifecycle()
    LaunchedEffect(diskError) { diskError?.let(notify) }
    BackHandler(page != "home") { if (mapFullscreen) fullMap = false else page = "home" }
    Scaffold(containerColor = Mist, snackbarHost = { SnackbarHost(notice) }, bottomBar = {
        if (!mapFullscreen) NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
            listOf(Triple("home", "Together", Icons.Default.FavoriteBorder), Triple("chat", "Chat", Icons.Default.ChatBubbleOutline),
                Triple("calendar", "Plans", Icons.Default.CalendarMonth), Triple("map", "Map", Icons.Default.LocationOn), Triple("profile", "Us", Icons.Default.PersonOutline))
                .forEach { (id, label, icon) -> NavigationBarItem(selected = page == id, onClick = { page = id }, icon = { Icon(icon, label) }, label = { Text(label) }) }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!mapFullscreen) Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("US", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = Wine)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { page = "savings" }) { Icon(Icons.Default.Savings, "Shared savings", tint = Wine) }
                IconButton(onClick = { page = "settings" }) { Icon(Icons.Default.Settings, "Connection settings", tint = Wine) }
            }
            if (user == null || room.isEmpty()) SettingsPage(store, status, notify) { page = "home" } else key(user?.uid, room) { when (page) {
                "home" -> HomePage(store, entries, status, { page = it }, notify)
                "chat" -> ChatPage(store, entries, notify)
                "calendar" -> CalendarPage(store, entries, notify)
                "map" -> MapPage(notify, mapFullscreen) { fullMap = it }
                "profile" -> ProfilePage(store, entries, notify)
                "savings" -> SavingsPage(store, entries, notify)
                else -> SettingsPage(store, status, notify) { page = "home" }
            } }
        }
    }
}
@Composable fun PageColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
}
@Composable fun Heading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Wine)
    }
}
@Composable fun SoftCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
@Composable fun HomePage(store: SharedStore, entries: List<Entry>, status: String, navigate: (String) -> Unit, notify: (String) -> Unit) {
    val partnerName = PartnerNames.from(entries, store.deviceId)
    val today = PlanSchedule.on(entries, java.time.LocalDate.now())
    val balance = entries.filter { it.type == "saving" && !it.deleted }.sumOf { it.amount }
    PageColumn {
        Heading("A little space for us.", "Hello, ${store.name}. Make today a shared memory.")
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(Wine, Rose))).padding(24.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("JUST THE TWO OF YOU", color = Mist, fontSize = 11.sp, letterSpacing = 2.sp)
                Text("Close, wherever\nyou are.", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold, lineHeight = 38.sp)
                Text(status, color = Mist, fontSize = 12.sp)
                FilledTonalButton(onClick = { navigate("chat") }) { Text("Say something lovely  →") }
            }
        }
        SoftCard {
            Text("Today's little plans", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            if (today.isEmpty()) Text("Nothing planned yet. Leave room for a little adventure.")
            listOf(true to "To-dos", false to "Reminders & events").forEach { (todo, heading) ->
                val section = today.filter { PlanSchedule.isTodo(it) == todo }
                if (section.isNotEmpty()) {
                    Text(heading, fontWeight = FontWeight.Bold, color = Rose)
                    section.take(3).forEach { EventRow(it, { store.togglePlan(it, java.time.LocalDate.now()) }) }
                }
            }
            TextButton(onClick = { navigate("calendar") }) { Text("Open our calendar →") }
        }
        SoftCard(Modifier.clickable { navigate("savings") }) {
            Text("OUR NEXT CHAPTER", fontSize = 11.sp, color = Rose, letterSpacing = 1.sp)
            Text(money(balance), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Wine)
            Text("Saved together · view our goal →")
        }
        Text("Send a little feeling", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        listOf("Miss you ♥", "Need a hug", "Thinking of you").forEach { mood ->
            SuggestionChip(onClick = { store.put(Entry(type = "message", text = mood)); notify("Sent to your conversation") }, label = { Text(mood) })
        }
        var confirm by remember { mutableStateOf<String?>(null) }
        OutlinedButton(onClick = { confirm = "I need you. Please check in with me. ♥" }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.NotificationsActive, null); Spacer(Modifier.width(8.dp)); Text("I need you · priority alert") }
        OutlinedButton(onClick = { confirm = "Kinda horny" }, modifier = Modifier.fillMaxWidth()) { Text("Kinda horny · send an alert") }
        if (confirm != null) AlertDialog(onDismissRequest = { confirm = null }, title = { Text("Let $partnerName know?") }, text = { Text("Send “$confirm” as a priority notification. They can see it when their app reconnects. Background notifications may be delayed.") },
            confirmButton = { TextButton(onClick = { store.put(Entry(type = "alert", text = confirm.orEmpty())); confirm = null; notify("Alert added to your conversation") }) { Text("Send alert") } }, dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } })
    }
}
