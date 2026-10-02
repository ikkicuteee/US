package com.example.localtwouserapp

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.security.SecureRandom

data class LivePoint(val latitude: Double = 0.0, val longitude: Double = 0.0,
    val accuracy: Float = 0f, val time: Long = 0L, val updatedAt: Long = 0L)

/** Spark-only client: no Cloud Functions, Storage, FCM sender, or permanent socket service. */
class FirebaseSync private constructor(private val context: Context) {
    private val store = SharedStore.get(context)
    val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()
    private val root = database.reference
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val user = MutableStateFlow(auth.currentUser)
    val room = MutableStateFlow("")
    val partnerUid = MutableStateFlow("")
    val connected = MutableStateFlow(false)
    val partnerOnline = MutableStateFlow(false)
    val status = MutableStateFlow("Sign in to connect your worlds")
    val problem = MutableStateFlow<String?>(null)
    val partnerLocation = MutableStateFlow<LivePoint?>(null)
    val invitation = MutableStateFlow("")
    val inviteExpires = MutableStateFlow(0L)
    val loadingHistory = MutableStateFlow(false)
    private var generation = 0
    private var activeUid = ""
    private var offset = 0L
    private val roomListeners = mutableListOf<Pair<Query, ValueEventListener>>()
    private var accountRef: DatabaseReference? = null
    private var accountListener: ValueEventListener? = null
    private val inFlight = mutableSetOf<String>()
    private var presence: DatabaseReference? = null
    fun serverTime() = System.currentTimeMillis() + offset

    init {
        // Durable documents/outbox are stored per account by SharedStore. GPS writes are never
        // persisted by the SDK, so a process restart cannot replay a route of stale fixes.
        root.child(".info/serverTimeOffset").addValueEventListener(listener { offset = (it.value as? Number)?.toLong() ?: 0L })
        root.child(".info/connected").addValueEventListener(listener {
            connected.value = it.getValue(Boolean::class.java) == true
            refreshStatus()
            if (connected.value) { setPresence(); sendPending() }
        })
        auth.addAuthStateListener { next ->
            user.value = next.currentUser
            val uid = next.currentUser?.uid.orEmpty()
            if (uid != activeUid || accountRef == null) bindAccount(uid)
        }
    }
    private fun listener(block: (DataSnapshot) -> Unit) = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) = block(snapshot)
        override fun onCancelled(error: DatabaseError) { fail(error.toException()) }
    }
    private fun fail(error: Exception) {
        problem.value = if ((error as? DatabaseException)?.message?.contains("Permission") == true ||
            error.message?.contains("Permission denied", true) == true)
            "Database access denied. Publish the supplied Firebase database rules, then reopen US."
        else error.localizedMessage ?: "Could not sync. Check your connection."
        status.value = problem.value!!
    }
    private fun refreshStatus() {
        status.value = when {
            user.value == null -> "Sign in to connect your worlds"
            room.value.isEmpty() -> "Create a private space or join your partner"
            !connected.value -> "Offline · changes saved on this phone"
            partnerOnline.value -> "Connected · ${store.partnerName} is online"
            else -> "Connected · ${store.partnerName} can catch up later"
        }
    }
    private fun bindAccount(uid: String) {
        if (store.deviceId != uid) BackgroundLocation.stop(context)
        accountListener?.let { accountRef?.removeEventListener(it) }
        clearRoom()
        activeUid = uid
        store.selectSpace(uid, "")
        if (uid.isEmpty()) { accountRef = null; refreshStatus(); return }
        accountRef = root.child("users").child(uid).child("room")
        val cached = store.prefs.getString("room_$uid", "").orEmpty()
        if (MergeRules.validInvite(cached)) attachRoom(cached)
        accountListener = listener { snapshot ->
            if (activeUid != uid) return@listener
            val code = snapshot.getValue(String::class.java).orEmpty()
            if (code.isNotEmpty() && !MergeRules.validInvite(code)) { problem.value = "Invalid shared space."; return@listener }
            if (room.value != code) {
                BackgroundLocation.stop(context)
                clearRoom(); store.selectSpace(uid, "")
                if (code.isNotEmpty()) attachRoom(code)
            }
            store.prefs.edit().putString("room_$uid", code).apply()
            refreshStatus()
        }.also { accountRef!!.addValueEventListener(it) }
    }
    private fun clearRoom() {
        generation++
        presence?.removeValue(); presence?.onDisconnect()?.cancel(); presence = null
        roomListeners.forEach { (query, callback) -> query.removeEventListener(callback) }; roomListeners.clear()
        room.value = ""; partnerUid.value = ""; partnerOnline.value = false
        partnerLocation.value = null; invitation.value = ""; inviteExpires.value = 0
        inFlight.clear(); loadingHistory.value = false
    }
    private fun watch(query: Query, block: (DataSnapshot) -> Unit) {
        val expected = generation
        val callback = listener { if (expected == generation) block(it) }
        roomListeners += query to callback
        query.addValueEventListener(callback)
    }
    private fun attachRoom(code: String) {
        room.value = code
        val uid = activeUid
        store.selectSpace(uid, code)
        val base = root.child("rooms").child(code)
        watch(root.child("pairings").child(code)) { snapshot ->
            val owner = snapshot.child("owner").getValue(String::class.java).orEmpty()
            val guest = snapshot.child("guest").getValue(String::class.java).orEmpty()
            if (uid != owner && uid != guest) {
                problem.value = "This account is not a member of this space."
                BackgroundLocation.stop(context); clearRoom(); store.selectSpace(uid, "")
                return@watch
            }
            partnerUid.value = if (uid == owner) guest else owner
            invitation.value = if (uid == owner && guest.isEmpty()) code else ""
            inviteExpires.value = snapshot.child("expires").getValue(Long::class.java) ?: 0
        }
        watch(base.child("presence")) { snapshot ->
            partnerOnline.value = snapshot.children.any { it.key != uid && it.getValue(Boolean::class.java) == true }
            refreshStatus()
        }
        watch(base.child("locations")) { snapshot ->
            partnerLocation.value = snapshot.children.firstOrNull { it.key != uid }?.let { decodePoint(it) }
        }
        listOf("message", "alert", "event", "saving", "goal", "profile", "locationstatus").forEach { type ->
            val ref = base.child("items").child(type)
            val query = if (type in listOf("message", "alert")) ref.orderByChild("time").limitToLast(100) else ref
            var initialized = false
            watch(query) { snapshot ->
                val incoming = snapshot.children.mapNotNull { decode(it) }
                val fresh = store.merge(incoming)
                if (type == "profile") refreshStatus()
                if (initialized) fresh.filter { it.author != uid && !it.deleted && it.type in listOf("message", "alert", "event", "saving") }
                    .takeLast(1).forEach {
                        Notices.show(context, if (it.type == "alert") "A priority alert from ${store.partnerName}" else "A little update from ${store.partnerName}",
                            it.text.ifBlank { "Your shared plans have changed" }, it.type == "alert")
                    }
                initialized = true
            }
        }
        presence = base.child("presence").child(uid)
        if (connected.value) { setPresence(); sendPending() }
        refreshStatus()
    }
    private fun setPresence() {
        val ref = presence ?: return
        val expected = generation
        ref.onDisconnect().removeValue().addOnSuccessListener {
            if (expected == generation && connected.value) ref.setValue(true)
        }
    }
    private fun decode(snapshot: DataSnapshot): Entry? = runCatching {
        snapshot.getValue(Entry::class.java)
    }.getOrNull()
    private fun decodePoint(snapshot: DataSnapshot): LivePoint? = runCatching {
        snapshot.getValue(LivePoint::class.java)?.takeIf {
            it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 && it.time > 0
        }
    }.getOrNull()
    fun signIn(email: String, password: String, create: Boolean, result: (String?) -> Unit) {
        val task = if (create) auth.createUserWithEmailAndPassword(email.trim(), password)
            else auth.signInWithEmailAndPassword(email.trim(), password)
        task.addOnCompleteListener { result(it.exception?.localizedMessage) }
    }
    fun resetPassword(email: String, result: (String?) -> Unit) {
        auth.sendPasswordResetEmail(email.trim()).addOnCompleteListener { result(it.exception?.localizedMessage) }
    }
    fun signOut() {
        BackgroundLocation.stop(context)
        clearRoom()
        auth.signOut()
        store.selectSpace("", "")
    }
    fun createSpace(result: (String?) -> Unit) {
        val uid = auth.currentUser?.uid ?: return result("Sign in first.")
        if (!connected.value) return result("Connect to the internet first.")
        if (room.value.isNotEmpty()) return result("You already have a shared space.")
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val code = bytes.joinToString("") { "%02x".format(it) }
        root.child("pairings").child(code).setValue(mapOf("owner" to uid, "guest" to "",
            "expires" to (System.currentTimeMillis() + offset + 86_400_000)))
            .addOnSuccessListener { linkSpace(code, uid, result) }
            .addOnFailureListener { result(it.localizedMessage) }
    }
    private fun linkSpace(code: String, uid: String, result: (String?) -> Unit) {
        if (auth.currentUser?.uid != uid) return result("Account changed. Sign in again.")
        root.child("users").child(uid).child("room").setValue(code).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                store.prefs.edit().putString("room_$uid", code).apply()
                if (room.value != code) { clearRoom(); attachRoom(code) }
            }
            result(task.exception?.localizedMessage)
        }
    }
    fun joinSpace(input: String, result: (String?) -> Unit) {
        val uid = auth.currentUser?.uid ?: return result("Sign in first.")
        val code = input.trim().lowercase()
        if (!MergeRules.validInvite(code)) return result("Paste the full invitation code from your partner.")
        if (!connected.value) return result("Connect to the internet first.")
        if (room.value.isNotEmpty()) return result("This account already has a shared space.")
        val ref = root.child("pairings").child(code)
        // Load first: Firebase transactions can initially receive null from an empty cache.
        ref.get().addOnSuccessListener { initial ->
            if (!initial.exists()) { result("Invitation unavailable."); return@addOnSuccessListener }
            if (initial.child("owner").getValue(String::class.java) == uid || initial.child("guest").getValue(String::class.java) == uid) {
                linkSpace(code, uid, result); return@addOnSuccessListener
            }
            ref.runTransaction(object : Transaction.Handler {
                override fun doTransaction(data: MutableData): Transaction.Result {
                    if (data.value == null) data.value = initial.value
                    val owner = data.child("owner").getValue(String::class.java) ?: return Transaction.abort()
                    val guest = data.child("guest").getValue(String::class.java).orEmpty()
                    if (uid == owner || uid == guest) return Transaction.abort()
                    val expires = data.child("expires").getValue(Long::class.java) ?: 0
                    if (guest.isNotEmpty() || expires <= System.currentTimeMillis() + offset) return Transaction.abort()
                    data.child("guest").value = uid
                    return Transaction.success(data)
                }
                override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                    if (error != null) result(error.message)
                    else if (!committed && snapshot?.child("owner")?.getValue(String::class.java) != uid &&
                        snapshot?.child("guest")?.getValue(String::class.java) != uid) result("Invitation expired, unavailable, or already used.")
                    else linkSpace(code, uid, result)
                }
            }, false)
        }.addOnFailureListener { result(it.localizedMessage) }
    }
    fun renewInvite(result: (String?) -> Unit) {
        val code = invitation.value
        if (code.isEmpty() || !connected.value) return result("Connect to the internet first.")
        root.child("pairings").child(code).child("expires")
            .setValue(System.currentTimeMillis() + offset + 86_400_000)
            .addOnCompleteListener { result(it.exception?.localizedMessage) }
    }
    fun sendPending() {
        if (!connected.value || !store.configured) return
        val expected = generation
        store.pending().forEach { entry ->
            if (!inFlight.add(entry.id)) return@forEach
            val ref = root.child("rooms").child(room.value).child("items").child(entry.type).child(entry.id)
            scope.launch {
                try { store.flush() } catch (e: Exception) { inFlight.remove(entry.id); fail(e); return@launch }
                if (expected != generation) return@launch
                ref.runTransaction(object : Transaction.Handler {
                    override fun doTransaction(data: MutableData): Transaction.Result {
                        val remote = runCatching { data.getValue(Entry::class.java) }.getOrNull()
                        val winner = if (remote != null && SavingsGoals.closed(remote)) remote else remote?.let { MergeRules.winner(it, entry) } ?: entry
                        if (remote != null && winner == remote) return Transaction.abort()
                        data.value = winner.wire()
                        return Transaction.success(data)
                    }
                    override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                        if (expected != generation) return
                        inFlight.remove(entry.id)
                        if (error != null) { fail(error.toException()); return }
                        val remote = snapshot?.let { decode(it) }
                        if (committed || remote != null) {
                            store.uploaded(entry, remote)
                            sendPending()
                        }
                    }
                }, false)
            }
        }
    }
    fun publishLocation(point: LivePoint) {
        if (!connected.value || !store.configured) return
        root.child("rooms").child(room.value).child("locations").child(activeUid)
            .setValue(mapOf("latitude" to point.latitude, "longitude" to point.longitude,
                "accuracy" to point.accuracy, "time" to point.time, "updatedAt" to ServerValue.TIMESTAMP))
            .addOnFailureListener { fail(it) }
    }
    fun clearLocation() {
        if (room.value.isNotEmpty() && activeUid.isNotEmpty())
            root.child("rooms").child(room.value).child("locations").child(activeUid).removeValue()
    }
    fun loadOlder(result: (String?) -> Unit) {
        if (loadingHistory.value || !store.configured) return
        val oldest = store.entries.value.filter { it.type == "message" }.minWithOrNull(compareBy<Entry> { it.time }.thenBy { it.id })
            ?: return result("No older messages.")
        val expected = generation
        loadingHistory.value = true
        root.child("rooms").child(room.value).child("items/message").orderByChild("time")
            .endBefore(oldest.time.toDouble(), oldest.id).limitToLast(100).get()
            .addOnCompleteListener { task ->
                if (expected != generation) return@addOnCompleteListener
                loadingHistory.value = false
                if (!task.isSuccessful) result(task.exception?.localizedMessage ?: "Could not load history.")
                else {
                    val items = task.result.children.mapNotNull { decode(it) }
                    store.merge(items); result(if (items.isEmpty()) "You're at the start of your conversation." else null)
                }
            }
    }
    companion object {
        @Volatile var instance: FirebaseSync? = null; private set
        fun get(context: Context): FirebaseSync = instance ?: synchronized(this) {
            instance ?: FirebaseSync(context.applicationContext).also { instance = it }
        }
    }
}

