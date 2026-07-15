package com.billingps.licensegen

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class LicenseGenViewModel(application: Application) : AndroidViewModel(application) {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    var isLoggedIn = false
        private set
    var currentUser = ""
        private set
    var isBusy = false
        private set

    fun login(email: String, password: String, onResult: (Boolean, String) -> Unit) {
        isBusy = true
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user != null) {
                    // Check if user is admin via Firestore
                    firestore.collection("admins").document(user.uid).get()
                        .addOnSuccessListener { snap ->
                            if (snap.exists() && snap.getBoolean("isAdmin") == true) {
                                isLoggedIn = true
                                currentUser = snap.getString("username") ?: user.email ?: ""
                                isBusy = false
                                onResult(true, "")
                            } else {
                                auth.signOut()
                                isBusy = false
                                onResult(false, "Akun tidak memiliki akses admin")
                            }
                        }
                        .addOnFailureListener {
                            isBusy = false
                            onResult(false, "Gagal memeriksa akses admin")
                        }
                } else {
                    isBusy = false
                    onResult(false, "Login gagal")
                }
            }
            .addOnFailureListener { ex ->
                isBusy = false
                val msg = when (ex) {
                    is FirebaseAuthInvalidCredentialsException -> "Email/password salah"
                    else -> ex.message ?: "Login gagal"
                }
                onResult(false, msg)
            }
    }

    fun signOut() {
        auth.signOut()
        isLoggedIn = false
        currentUser = ""
    }

    fun generateLicense(paket: String, username: String, onDone: (String) -> Unit) {
        isBusy = true
        val shortCode = (10000000..99999999).random().toString() + (65..90).random().toChar()
        val nonce = java.util.UUID.randomUUID().toString().take(8)
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_YEAR, when (paket) {
            "BULANAN" -> 30; "3BULAN" -> 90; "TAHUNAN" -> 360; "LIFETIME" -> 99999; else -> 30
        })
        val expiry = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(cal.time)
        val payload = """{"p":"$paket","u":"$username","e":"$expiry","n":"$nonce"}"""
        val signature = ECDSAUtils.sign(payload)
        val kode = shortCode

        // Firestore save
        viewModelScope.launch {
            try {
                val doc = HashMap<String, Any>()
                doc["kode"] = kode
                doc["payload"] = payload
                doc["signature"] = signature
                doc["paket"] = paket
                doc["username"] = username
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
                        id = doc.id,
                        kode = d["kode"] as? String ?: "",
                        payload = d["payload"] as? String ?: "",
                        signature = d["signature"] as? String ?: "",
                        paket = d["paket"] as? String ?: "",
                        username = d["username"] as? String ?: "",
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
}

private suspend fun com.google.android.gms.tasks.Task<*>?.await() {
    if (this == null) return
    kotlinx.coroutines.suspendCancellableCoroutine<Unit> { cont ->
        this.addOnSuccessListener { if (!cont.isCompleted) cont.resume(Unit) }
            .addOnFailureListener { if (!cont.isCompleted) cont.resume(Unit) }
    }
}
