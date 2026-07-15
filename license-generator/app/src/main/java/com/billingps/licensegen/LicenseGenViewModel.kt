package com.billingps.licensegen

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.security.MessageDigest

private const val SUPER_ADMIN_USER = "rrbilling"
private const val SUPER_ADMIN_PASS = "@rrcctv5555"

class LicenseGenViewModel(application: Application) : AndroidViewModel(application) {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private var usersListener: ListenerRegistration? = null

    var isLoggedIn by mutableStateOf(false); private set
    var currentUser by mutableStateOf(""); private set
    var isBusy by mutableStateOf(false); private set
    var userList by mutableStateOf(listOf<FirestoreUser>()); private set
    var firestoreReady by mutableStateOf(false); private set

    init { ensureAnonymous() }

    private fun ensureAnonymous() {
        viewModelScope.launch {
            if (auth.currentUser == null) {
                suspendCancellableCoroutine<Boolean> { cont ->
                    auth.signInAnonymously()
                        .addOnSuccessListener { if (!cont.isCompleted) cont.resume(true) }
                        .addOnFailureListener { if (!cont.isCompleted) cont.resume(false) }
                }
            }
            firestoreReady = true
            if (isLoggedIn) startListening()
        }
    }

    fun login(password: String, onResult: (Boolean, String) -> Unit) {
        val hash = MessageDigest.getInstance("SHA-256").digest(password.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val expected = MessageDigest.getInstance("SHA-256").digest(SUPER_ADMIN_PASS.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        if (hash != expected) {
            onResult(false, "Password salah")
            return
        }
        isLoggedIn = true
        currentUser = SUPER_ADMIN_USER
        if (firestoreReady) startListening()
        onResult(true, "")
    }

    fun signOut() {
        isLoggedIn = false
        currentUser = ""
        usersListener?.remove()
        usersListener = null
        userList = emptyList()
    }

    private fun startListening() {
        usersListener?.remove()
        usersListener = firestore.collection("billingps_users")
            .addSnapshotListener { snap, error ->
                if (error != null) { Log.e("LicenseGen", "listen error: ${error.message}"); return@addSnapshotListener }
                if (snap == null) return@addSnapshotListener
                val allUsers = mutableMapOf<String, FirestoreUser>()
                for (doc in snap.documents) {
                    val data = doc.data ?: continue
                    val usersRaw = data["users"] as? Map<*, *> ?: continue
                    for ((key, value) in usersRaw) {
                        if (key !is String || value !is Map<*, *>) continue
                        val existing = allUsers[key]
                        val u = FirestoreUser(
                            username = key,
                            role = value["role"] as? String ?: "",
                            email = value["email"] as? String ?: "",
                            dibuat = value["dibuat"] as? String ?: "",
                        )
                        if (existing == null || u.email.isNotBlank() || existing.email.isBlank()) {
                            allUsers[key] = u
                        }
                    }
                }
                userList = allUsers.values.sortedBy { it.username }
            }
    }

    fun generateLicense(paket: String, username: String, email: String, onDone: (String) -> Unit) {
        isBusy = true
        val shortCode = (10000000..99999999).random().toString() + (65..90).random().toChar()
        val nonce = java.util.UUID.randomUUID().toString().take(8)
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_YEAR, when (paket) {
            "BULANAN" -> 30; "3BULAN" -> 90; "TAHUNAN" -> 360; "LIFETIME" -> 99999; else -> 30
        })
        val expiry = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(cal.time)
        val payload = """{"p":"$paket","u":"$username","e":"$expiry","n":"$nonce","m":"$email"}"""
        val signature = ECDSAUtils.sign(payload)
        val kode = shortCode

        viewModelScope.launch {
            try {
                val doc = HashMap<String, Any>()
                doc["kode"] = kode
                doc["payload"] = payload
                doc["signature"] = signature
                doc["paket"] = paket
                doc["username"] = username
                doc["email"] = email
                doc["expiry"] = expiry
                doc["generatedBy"] = currentUser
                doc["generatedAt"] = System.currentTimeMillis()
                doc["activatedAt"] = 0L
                doc["activatedDeviceId"] = ""
                doc["revoked"] = false
                firestore.collection("licenses").add(doc).await()
            } catch (e: Exception) {
                Log.e("LicenseGen", "Firestore save failed: ${e.message}")
            }
            isBusy = false
            onDone(kode)
        }
    }

    suspend fun getHistory(): List<LicenseRecord> = suspendCancellableCoroutine { cont ->
        firestore.collection("licenses")
            .whereEqualTo("generatedBy", currentUser)
            .orderBy("generatedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(50)
            .get()
            .addOnSuccessListener { snap ->
                val list = snap.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    LicenseRecord(
                        id = doc.id, kode = d["kode"] as? String ?: "",
                        payload = d["payload"] as? String ?: "",
                        signature = d["signature"] as? String ?: "",
                        paket = d["paket"] as? String ?: "",
                        username = d["username"] as? String ?: "",
                        email = d["email"] as? String ?: "",
                        expiry = d["expiry"] as? String ?: "",
                        generatedBy = d["generatedBy"] as? String ?: "",
                        generatedAt = d["generatedAt"] as? Long ?: 0,
                        activatedAt = d["activatedAt"] as? Long ?: 0,
                    )
                }
                cont.resume(list)
            }
            .addOnFailureListener { cont.resume(emptyList()) }
    }

    override fun onCleared() {
        usersListener?.remove()
        super.onCleared()
    }
}

private suspend fun com.google.android.gms.tasks.Task<*>?.await() {
    if (this == null) return
    kotlinx.coroutines.suspendCancellableCoroutine<Unit> { cont ->
        this.addOnSuccessListener { if (!cont.isCompleted) cont.resume(Unit) }
            .addOnFailureListener { if (!cont.isCompleted) cont.resume(Unit) }
    }
}
