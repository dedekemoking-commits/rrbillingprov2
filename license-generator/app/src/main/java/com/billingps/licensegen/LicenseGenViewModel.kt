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
import com.google.firebase.firestore.SetOptions
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
    private var invoicesListener: ListenerRegistration? = null

    var isLoggedIn by mutableStateOf(false); private set
    var currentUser by mutableStateOf(""); private set
    var isBusy by mutableStateOf(false); private set
    var userList by mutableStateOf(listOf<FirestoreUser>()); private set
    var invoiceList by mutableStateOf(listOf<Invoice>()); private set
    var firestoreReady by mutableStateOf(false); private set

    init { ensureAnonymous() }

    private fun ensureAnonymous() {
        viewModelScope.launch {
            if (auth.currentUser == null) {
                suspendCancellableCoroutine<Boolean> { cont ->
                    auth.signInAnonymously()
                        .addOnSuccessListener { r ->
                            Log.i("LicenseGen", "Anonymous auth success: ${r.user?.uid}")
                            if (!cont.isCompleted) cont.resume(true)
                        }
                        .addOnFailureListener { ex ->
                            Log.e("LicenseGen", "Anonymous auth failed: ${ex.message}")
                            if (!cont.isCompleted) cont.resume(false)
                        }
                }
            } else {
                Log.i("LicenseGen", "Already signed in as ${auth.currentUser?.uid}")
            }
            firestoreReady = true
            Log.i("LicenseGen", "firestoreReady=true, isLoggedIn=$isLoggedIn")
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
        Log.i("LicenseGen", "login success, firestoreReady=$firestoreReady, calling startListening")
        startListening()
        onResult(true, "")
    }

    fun signOut() {
        isLoggedIn = false
        currentUser = ""
        usersListener?.remove()
        usersListener = null
        invoicesListener?.remove()
        invoicesListener = null
        userList = emptyList()
        invoiceList = emptyList()
    }

    private fun startListening() {
        usersListener?.remove()
        Log.i("LicenseGen", "startListening: adding snapshot listener on billingps_users")
        usersListener = firestore.collection("billingps_users")
            .addSnapshotListener { snap, error ->
                if (error != null) { Log.e("LicenseGen", "listen error: ${error.message}"); return@addSnapshotListener }
                if (snap == null) { Log.w("LicenseGen", "snapshot is null"); return@addSnapshotListener }
                val allUsers = mutableMapOf<String, FirestoreUser>()
                for (doc in snap.documents) {
                    val data = doc.data ?: continue
                    if (doc.id.startsWith("_user_")) {
                        // Individual user doc created by main app sync
                        val username = data["username"] as? String ?: continue
                        val existing = allUsers[username]
                        val u = FirestoreUser(
                            username = username,
                            role = data["role"] as? String ?: "",
                            email = data["email"] as? String ?: "",
                            dibuat = data["dibuat"] as? String ?: "",
                        )
                        if (existing == null || u.email.isNotBlank() || existing.email.isBlank()) {
                            allUsers[username] = u
                        }
                    } else {
                        // Backward compat: read nested "users" map from device sync docs
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
                }
                userList = allUsers.values.sortedBy { it.username }
                Log.i("LicenseGen", "snapshot received: ${snap.documents.size} docs, ${allUsers.size} users parsed, userList.size=${userList.size}")
            }

        invoicesListener?.remove()
        invoicesListener = firestore.collection("invoices")
            .addSnapshotListener { snap, error ->
                if (error != null) { Log.e("LicenseGen", "invoices listen error: ${error.message}"); return@addSnapshotListener }
                if (snap == null) return@addSnapshotListener
                invoiceList = snap.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    Invoice(
                        id = d["id"] as? String ?: doc.id,
                        username = d["username"] as? String ?: "",
                        email = d["email"] as? String ?: "",
                        paket = d["paket"] as? String ?: "",
                        harga = (d["harga"] as? Long)?.toInt() ?: 0,
                        status = d["status"] as? String ?: "PENDING",
                        dibuat = d["dibuat"] as? Long ?: 0L,
                        dibayar = d["dibayar"] as? Long ?: 0L,
                        confirmedBy = d["confirmedBy"] as? String ?: "",
                        kodeLisensi = d["kodeLisensi"] as? String ?: "",
                        buktiBase64 = d["buktiBase64"] as? String ?: "",
                    )
                }.sortedByDescending { it.dibuat }
                Log.i("LicenseGen", "invoices snapshot: ${invoiceList.size} invoices")
            }
    }

    fun konfirmasiInvoice(invoice: Invoice, onDone: (Boolean, String) -> Unit) {
        isBusy = true
        val kode = generateKode(invoice.paket)
        val nonce = java.util.UUID.randomUUID().toString().take(8)
        val paketDays = when (invoice.paket) {
            "1 Bulan", "BULANAN" -> 30; "3 Bulan", "3BULAN" -> 90
            "1 Tahun", "TAHUNAN" -> 360; "LIFETIME" -> 99999; else -> 30
        }

        viewModelScope.launch {
            try {
                // Hitung expiry: extend dari existing lisensi jika masih aktif
                val cal = java.util.Calendar.getInstance()
                try {
                    val existingExpires = suspendCancellableCoroutine<String?> { cont ->
                        firestore.collection("billingps_users").document(invoice.username).get()
                            .addOnSuccessListener { snap ->
                                val ls = snap.get("licenseStatus") as? Map<*, *>
                                val ex = ls?.get("expiresAt") as? String ?: ""
                                cont.resume(if (ex.isNotEmpty()) ex else null)
                            }
                            .addOnFailureListener { cont.resume(null) }
                    }
                    if (existingExpires != null) {
                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                        val existingDate = sdf.parse(existingExpires)
                        if (existingDate != null && existingDate.after(java.util.Date())) {
                            cal.time = existingDate
                        }
                    }
                } catch (_: Exception) { }
                cal.add(java.util.Calendar.DAY_OF_YEAR, paketDays)
                val expiry = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(cal.time)
                val payload = """{"p":"${invoice.paket}","u":"${invoice.username}","e":"$expiry","n":"$nonce","m":"${invoice.email}"}"""
                val signature = ECDSAUtils.sign(payload)

                val licenseDoc = HashMap<String, Any>()
                licenseDoc["kode"] = kode
                licenseDoc["payload"] = payload
                licenseDoc["signature"] = signature
                licenseDoc["paket"] = invoice.paket
                licenseDoc["username"] = invoice.username
                licenseDoc["email"] = invoice.email
                licenseDoc["expiry"] = expiry
                licenseDoc["generatedBy"] = currentUser
                licenseDoc["generatedAt"] = System.currentTimeMillis()
                licenseDoc["activatedAt"] = 0L
                licenseDoc["activatedDeviceId"] = ""
                licenseDoc["revoked"] = false
                firestore.collection("licenses").add(licenseDoc).await()

                val update = HashMap<String, Any>()
                update["status"] = "CONFIRMED"
                update["kodeLisensi"] = kode
                update["confirmedBy"] = currentUser
                update["dibayar"] = System.currentTimeMillis()
                firestore.collection("invoices").document(invoice.id).update(update).await()

                // Also write licenseStatus to user doc so main app can detect it
                val maxTv = when (invoice.paket) {
                    "1 Bulan", "BULANAN" -> 5; "3 Bulan", "3BULAN" -> 8
                    "1 Tahun", "TAHUNAN" -> 15; "LIFETIME" -> 0; else -> 2
                }
                val userLicUpdate = HashMap<String, Any>()
                userLicUpdate["licenseStatus"] = mapOf(
                    "status" to "active",
                    "pesan" to "✅ Lisensi ${invoice.paket} aktif hingga $expiry",
                    "expiresAt" to expiry,
                    "maxTv" to maxTv,
                )
                firestore.collection("billingps_users").document(invoice.username)
                    .set(userLicUpdate, SetOptions.merge()).await()

                isBusy = false
                onDone(true, kode)
            } catch (e: Exception) {
                Log.e("LicenseGen", "konfirmasiInvoice failed: ${e.message}")
                isBusy = false
                onDone(false, "Gagal: ${e.message}")
            }
        }
    }

    private fun generateKode(paket: String): String {
        val paketChar = when {
            paket in listOf("BULANAN", "1 Bulan") -> "B"
            paket in listOf("3BULAN", "3 Bulan") -> "T"
            paket in listOf("TAHUNAN", "1 Tahun") -> "S"
            paket == "LIFETIME" -> "L"
            else -> "X"
        }
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = (1..13).map { chars.random() }.joinToString("")
        return "RR$paketChar$random"
    }

    fun generateLicense(paket: String, username: String, email: String, onDone: (String) -> Unit) {
        isBusy = true
        val kode = generateKode(paket)
        val nonce = java.util.UUID.randomUUID().toString().take(8)
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_YEAR, when (paket) {
            "1 Bulan", "BULANAN" -> 30; "3 Bulan", "3BULAN" -> 90
            "1 Tahun", "TAHUNAN" -> 360; "LIFETIME" -> 99999; else -> 30
        })
        val expiry = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(cal.time)
        val payload = """{"p":"$paket","u":"$username","e":"$expiry","n":"$nonce","m":"$email"}"""
        val signature = ECDSAUtils.sign(payload)

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
        Log.i("LicenseGen", "getHistory: querying licenses where generatedBy=$currentUser on project=${firestore.app.options.projectId}")
        firestore.collection("licenses")
            .whereEqualTo("generatedBy", currentUser)
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
                val sorted = list.sortedByDescending { it.generatedAt }
                Log.i("LicenseGen", "getHistory: found ${sorted.size} records")
                cont.resume(sorted)
            }
            .addOnFailureListener { ex ->
                Log.e("LicenseGen", "getHistory failed: ${ex.message}")
                cont.resume(emptyList())
            }
    }

    override fun onCleared() {
        usersListener?.remove()
        invoicesListener?.remove()
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
