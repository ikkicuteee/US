package com.example.localtwouserapp

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun LocationControls(notify: (String) -> Unit) {
    val context = LocalContext.current
    val store = remember { SharedStore.get(context) }
    val profiles by store.entries.collectAsStateWithLifecycle()
    val partnerName = PartnerNames.from(profiles, store.deviceId)
    val sharing by LocationService.sharingLocation.collectAsStateWithLifecycle()
    val status by LocationService.locationStatus.collectAsStateWithLifecycle()
    val connected by FirebaseSync.get(context).connected.collectAsStateWithLifecycle()
    var explainBackground by rememberSaveable { mutableStateOf(false) }
    var explainStop by rememberSaveable { mutableStateOf(false) }
    var stopReason by rememberSaveable { mutableStateOf("") }
    val power = remember { context.getSystemService(PowerManager::class.java) }
    var unrestricted by remember { mutableStateOf(power.isIgnoringBatteryOptimizations(context.packageName)) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var explainBattery by rememberSaveable { mutableStateOf(false) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) unrestricted = power.isIgnoringBatteryOptimizations(context.packageName)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val enable = {
        runCatching { BackgroundLocation.startFromScreen(context) }
            .onFailure { notify(it.localizedMessage ?: "Could not start location sharing") }
        Unit
    }
    val backgroundRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) enable() else notify("Sharing is off. Allow background location to share when US is closed.")
    }
    val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (BackgroundLocation.hasBackgroundAccess(context)) enable()
        else notify("Sharing is off. In Permissions → Location, choose Allow all the time.")
    }
    val foregroundRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (!BackgroundLocation.hasLocation(context)) notify("Allow location access to share your position.")
        else if (BackgroundLocation.hasBackgroundAccess(context)) enable()
        else explainBackground = true
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Location sharing", fontWeight = FontWeight.Bold)
                Text(if (!sharing) "Off" else if (!connected) "Firebase disconnected" else "On", fontSize = 12.sp, color = Rose)
            }
            IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Tune, "Location settings") }
            Switch(sharing, { enabled ->
                if (!enabled) { BackgroundLocation.stop(context); stopReason = ""; explainStop = true }
                else if (!SharedStore.get(context).configured) notify("Sign in and pair with your partner in Settings first.")
                else if (BackgroundLocation.hasBackgroundAccess(context)) enable()
                else if (BackgroundLocation.hasLocation(context)) explainBackground = true
                else foregroundRequest.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            })
        }
    }
    if (showSettings) AlertDialog(onDismissRequest = { showSettings = false },
        title = { Text("Location settings") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(status, fontSize = 13.sp)
            TextButton(onClick = {
                runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }
                    .onFailure { notify("Open your phone's app settings for US.") }
            }) { Text("Location permissions") }
            TextButton(enabled = !unrestricted, onClick = { showSettings = false; explainBattery = true }) {
                Text(if (unrestricted) "Background battery access allowed" else "Allow screen-off sharing")
            }
            if (Build.MANUFACTURER.equals("xiaomi", true) || Build.BRAND.equals("redmi", true))
                Text("For background sharing on Redmi, enable Background autostart and No restrictions in US app settings.", fontSize = 13.sp)
        } },
        confirmButton = { TextButton(onClick = { showSettings = false }) { Text("Done") } })
    if (explainBattery) AlertDialog(onDismissRequest = { explainBattery = false },
        title = { Text("Share with the screen off") },
        text = { Text("Allow US to work without battery optimization so location uploads can continue while your screen is off. Live sharing keeps the processor awake and uses extra battery. Stop sharing to release it. Android or your phone's app management can still stop US; force-stopping the app stops updates until you reopen it.") },
        confirmButton = { TextButton(onClick = {
            explainBattery = false
            runCatching { context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))) }
                .onFailure {
                    runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                        .onFailure { notify("Open Settings → Apps → US → Battery and allow unrestricted use.") }
                }
        }) { Text("Open battery permission") } },
        dismissButton = { TextButton(onClick = { explainBattery = false }) { Text("Later") } })
    if (explainStop) AlertDialog(onDismissRequest = { explainStop = false },
        title = { Text("Location sharing is off") },
        text = { Column {
            Text("Let $partnerName know why. Your location stays off even if you skip this.")
            OutlinedTextField(stopReason, { stopReason = it.take(200) }, label = { Text("Reason (optional)") }, modifier = Modifier.fillMaxWidth())
        } },
        confirmButton = { TextButton(enabled = stopReason.isNotBlank(), onClick = {
            BackgroundLocation.recordStatus(context, false, stopReason)
            explainStop = false; notify("Reason saved for $partnerName")
        }) { Text("Share reason") } },
        dismissButton = { TextButton(onClick = { explainStop = false }) { Text("Skip") } })
    if (explainBackground) AlertDialog(
        onDismissRequest = { explainBackground = false },
        title = { Text("Keep sharing when US is closed?") },
        text = { Text("US will use your location in the background and share it with your paired partner, even when you use other apps or lock your screen. A notification stays visible, and you can stop sharing at any time.\n\nIn the next screen, open Permissions → Location and choose Allow all the time. Your choice also lets US resume sharing after a restart.") },
        confirmButton = { TextButton(onClick = {
            explainBackground = false
            if (Build.VERSION.SDK_INT >= 30) settings.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
            else if (Build.VERSION.SDK_INT == 29) backgroundRequest.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            else enable()
        }) { Text("Continue") } },
        dismissButton = { TextButton(onClick = { explainBackground = false }) { Text("Not now") } },
    )
}
