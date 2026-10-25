package com.example.localtwouserapp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable fun SettingsPage(store: SharedStore, status: String, notify: (String) -> Unit, done: () -> Unit) {
    val context = LocalContext.current
    val profiles by store.entries.collectAsStateWithLifecycle()
    val partnerName = PartnerNames.from(profiles, store.deviceId)
    val sync = remember { FirebaseSync.get(context) }
    val user by sync.user.collectAsStateWithLifecycle()
    val room by sync.room.collectAsStateWithLifecycle()
    val invite by sync.invitation.collectAsStateWithLifecycle()
    val partner by sync.partnerUid.collectAsStateWithLifecycle()
    val expires by sync.inviteExpires.collectAsStateWithLifecycle()
    val problem by sync.problem.collectAsStateWithLifecycle()
    val connected by sync.connected.collectAsStateWithLifecycle()
    val retrying by sync.retrying.collectAsStateWithLifecycle()
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var createAccount by rememberSaveable { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    val result: (String?) -> Unit = { error -> busy = false; if (error != null) notify(error) else { password = ""; code = "" } }
    PageColumn {
        Heading("Connect your worlds.", "One private space for the two of you.")
        SoftCard {
            Text(status, color = Wine)
            if (!connected) TextButton(enabled = !retrying, onClick = { sync.retryConnection() }) {
                Text(if (retrying) "Reconnecting…" else "Retry connection")
            }
            problem?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
            if (user == null) {
                Text(if (createAccount) "Make your account" else "Welcome back", fontWeight = FontWeight.Bold)
                OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth())
                Button(enabled = !busy && email.isNotBlank() && password.length >= 6, onClick = {
                    busy = true; sync.signIn(email, password, createAccount, result)
                }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Connecting…" else if (createAccount) "Create account" else "Sign in") }
                TextButton(enabled = !busy, onClick = { createAccount = !createAccount }) {
                    Text(if (createAccount) "Already have an account? Sign in" else "New here? Create an account")
                }
                TextButton(enabled = !busy && email.isNotBlank(), onClick = {
                    busy = true; sync.resetPassword(email) { result(it); if (it == null) notify("Check your email for a password reset link.") }
                }) { Text("Forgot password") }
            } else {
                Text(user?.email.orEmpty(), fontWeight = FontWeight.Bold)
                if (room.isEmpty()) {
                    Button(enabled = !busy, onClick = { busy = true; sync.createSpace(result) }, modifier = Modifier.fillMaxWidth()) { Text("Invite my partner") }
                    Text("Or paste the invitation your partner sent you.", fontSize = 13.sp)
                    OutlinedTextField(code, { code = it.trim().take(64) }, label = { Text("Invitation code") }, modifier = Modifier.fillMaxWidth())
                    OutlinedButton(enabled = !busy && code.isNotBlank(), onClick = { busy = true; sync.joinSpace(code, result) },
                        modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Connecting…" else "Join my partner") }
                } else if (invite.isNotEmpty()) {
                    Text("Your private invitation is ready.", fontWeight = FontWeight.Bold)
                    Text("Share this code privately with your partner. It allows one account to join and expires after 24 hours.", fontSize = 13.sp)
                    OutlinedTextField(invite, {}, readOnly = true, label = { Text("Invitation code") }, modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = {
                        val clip = ClipData.newPlainText("US invitation", invite)
                        if (Build.VERSION.SDK_INT >= 33) clip.description.extras = android.os.PersistableBundle().apply {
                            putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true)
                        }
                        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
                        notify("Invitation copied. Share it only with your partner.")
                    }) { Text("Copy invitation") }
                    Text("Expires: " + java.text.DateFormat.getDateTimeInstance().format(java.util.Date(expires)), fontSize = 12.sp)
                    TextButton(enabled = !busy, onClick = { busy = true; sync.renewInvite(result) }) { Text("Extend invitation for 24 hours") }
                    TextButton(onClick = done) { Text("Explore our space") }
                } else {
                    Text(if (partner.isEmpty()) "Loading your shared space…" else "Paired with $partnerName ♥")
                    Button(onClick = done, modifier = Modifier.fillMaxWidth()) { Text("Open our space") }
                }
                TextButton(enabled = !busy, onClick = { confirmSignOut = true }) { Text("Sign out") }
            }
        }
        SoftCard {
            Text("Notifications", fontWeight = FontWeight.Bold)
            Text("Updates can notify you while US is running and connected. Android may delay them when it sleeps or stops the app. Open US to catch up.", fontSize = 13.sp)
            TextButton(onClick = {
                val intent = if (Build.VERSION.SDK_INT >= 26) Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                context.startActivity(intent)
            }) { Text("Notification settings") }
        }
        SoftCard {
            TextButton(onClick = { context.startActivity(Intent(context, MainActivity::class.java).putExtra("page", "drawing")) }) { Text("Our drawing & widget") }
            Text("Photo frames", fontWeight = FontWeight.Bold)
            Text("Keep a favorite picture on your home screen. Each frame has its own photo, saved on this phone.", fontSize = 13.sp)
            TextButton(onClick = { context.startActivity(Intent(context, PhotoFrameActivity::class.java)) }) {
                Text("Add a photo widget")
            }
        }
        SoftCard {
            Text("About US", fontWeight = FontWeight.Bold)
            val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }
            Text("Version $version")
            Text("Developed by jwcaburog")
        }
        SoftCard {
            Text("Location is your choice", fontWeight = FontWeight.Bold)
            Text("Turn on sharing in Map to keep sending your latest position while using other apps. Sharing shows a persistent notification and uses extra battery. You can stop it at any time.", fontSize = 13.sp)
        }
    }
    if (confirmSignOut) AlertDialog(onDismissRequest = { confirmSignOut = false },
        title = { Text("Sign out of US?") },
        text = { Text("Location sharing will stop. Any unsynced changes stay on this phone for this account until you sign in again.") },
        confirmButton = { TextButton(onClick = { sync.signOut(); confirmSignOut = false }) { Text("Sign out") } },
        dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Stay") } })
}

