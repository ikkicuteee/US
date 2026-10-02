package com.example.localtwouserapp

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
                Text("Share my live location", fontWeight = FontWeight.Bold)
                Text(if (sharing) "On · keeps sharing when US is closed" else "Off · your location stays private", fontSize = 12.sp, color = Rose)
            }
            Switch(sharing, { enabled ->
                if (!enabled) { BackgroundLocation.stop(context); stopReason = ""; explainStop = true }
                else if (!SharedStore.get(context).configured) notify("Sign in and pair with your partner in Settings first.")
                else if (BackgroundLocation.hasBackgroundAccess(context)) enable()
                else if (BackgroundLocation.hasLocation(context)) explainBackground = true
                else foregroundRequest.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            })
        }
        Text(if (sharing && !connected) "Offline · live updates resume when this phone reconnects."
            else if (sharing) status + " · Keep internet and Location on. Background sharing uses extra battery." else status,
            fontSize = 12.sp, color = Wine)
        if (sharing) TextButton(onClick = {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
        }) { Text("Battery settings · allow background activity") }
    }
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
