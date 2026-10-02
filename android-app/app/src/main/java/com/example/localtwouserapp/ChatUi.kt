package com.example.localtwouserapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable fun ChatPage(store: SharedStore, entries: List<Entry>, notify: (String) -> Unit) {
    val sync = remember { FirebaseSync.get(store.context) }
    val online by sync.partnerOnline.collectAsStateWithLifecycle()
    val connected by sync.connected.collectAsStateWithLifecycle()
    val revision by store.revision.collectAsStateWithLifecycle()
    val loading by sync.loadingHistory.collectAsStateWithLifecycle()
    val partnerName = PartnerNames.from(entries, store.deviceId)
    val messages = entries.filter { it.type in listOf("message", "alert") && !it.deleted }
    var text by rememberSaveable { mutableStateOf("") }
    val list = rememberLazyListState()
    val latest = messages.lastOrNull()?.id
    LaunchedEffect(latest) { if (messages.isNotEmpty()) list.animateScrollToItem(messages.size) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp)) {
            Text("$partnerName", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(when { !connected -> "Offline · your notes will wait"; online -> "Here with you"; else -> "They can catch up when they reconnect" },
                color = Rose, fontSize = 12.sp)
        }
        LazyColumn(state = list, modifier = Modifier.weight(1f), contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                if (messages.isEmpty()) SoftCard { Text("Your conversation starts here ♥"); Text("Share a thought or a little piece of your day.", fontSize = 13.sp) }
                else TextButton(enabled = connected && !loading, onClick = { sync.loadOlder { it?.let(notify) } }) {
                    Text(if (loading) "Loading…" else "Earlier messages")
                }
            }
            items(messages, key = { it.id }) { entry ->
                val mine = entry.author == store.deviceId
                Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                    Card(Modifier.widthIn(max = 300.dp), shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = if (mine) Wine else MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(entry.text, color = if (mine) Color.White else Ink)
                            Text(if (mine) { if (revision >= 0 && store.uploaded(entry.id)) "Saved to our space ♥" else "Waiting to sync" } else "$partnerName",
                                fontSize = 10.sp, color = if (mine) Mist else Rose)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().imePadding().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(text, { text = it.take(10_000) }, placeholder = { Text("A little note…") },
                modifier = Modifier.weight(1f), maxLines = 4, shape = RoundedCornerShape(24.dp))
            IconButton(enabled = text.isNotBlank(), onClick = { store.put(Entry(text = text.trim())); text = "" }) {
                Icon(Icons.Default.Send, "Send message", tint = Wine)
            }
        }
    }
}

