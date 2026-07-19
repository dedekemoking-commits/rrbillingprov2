package com.billingps.aptv.cloud

import android.app.Application
import android.provider.Settings
import android.util.Log
import com.billingps.aptv.models.*
import com.billingps.aptv.utils.ECDSAUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.util.UUID

/**
 * Simple Firebase-backed repository for syncing app state to Firestore.
 * Uses anonymous sign-in and writes a document under collection "billingps_devices" with the device ID as the doc ID.
 */
class CloudRepository(private val app: Application) {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val deviceId: String = try {
        Settings.Secure.getString(app.contentResolver, Settings.Secure.ANDROID_ID) ?: UUID.randomUUID().toString()
    } catch (t: Throwable) {
        UUID.randomUUID().toString()
    }

    suspend fun ensureSignedIn(): Boolean = suspendCancellableCoroutine { cont ->
        val user = auth.currentUser
        if (user != null) { Log.i("CloudRepo", "Already signed in as ${user.uid}"); cont.resume(true); return@suspendCancellableCoroutine }
        Log.i("CloudRepo", "Signing in anonymously...")
        auth.signInAnonymously()
            .addOnSuccessListener { r -> if (!cont.isCompleted) { Log.i("CloudRepo", "Anonymous sign-in success: ${r.user?.uid}"); cont.resume(true) } }
            .addOnFailureListener { ex -> if (!cont.isCompleted) { Log.i("CloudRepo", "Anonymous sign-in failed: ${ex.message}"); cont.resume(false) } }
    }

    /**
     * Sync the important parts of the app state to Firestore. Returns true on success.
     */
    suspend fun writeLicenseStatusToUserDoc(username: String, ls: Map<String, Any>): Boolean {
        if (username.isBlank()) return false
        if (!ensureSignedIn()) return false
        return suspendCancellableCoroutine { cont ->
            firestore.collection("billingps_users").document(username)
                .set(mapOf("licenseStatus" to ls), com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener { cont.resume(true) }
                .addOnFailureListener { cont.resume(false) }
        }
    }

    suspend fun syncAll(state: AppUiState, username: String? = null): Boolean {
            Log.i("CloudRepo", "syncAll start for user=${username ?: "(device)"}")
            if (!ensureSignedIn()) { Log.i("CloudRepo", "ensureSignedIn failed"); return false }

            val tvs = state.tvList.map { tv ->
                mapOf(
                    "id" to tv.id,
                    "nama" to tv.nama,
                    "ip" to tv.ip,
                    "port" to tv.port,
                    "jenisPs" to tv.jenisPs,
                    "paketAktif" to tv.paketAktif,
                    "sisaDetik" to tv.sisaDetik,
                )
            }

            val txs = state.transaksiList.map { t ->
                mapOf(
                    "id" to t.id,
                    "waktu" to t.waktu,
                    "kasir" to t.kasir,
                    "kota" to t.kota,
                    "paket" to t.paket,
                    "total" to t.total,
                    "pesanan" to t.pesanan,
                )
            }

            val payload = mutableMapOf<String, Any>(
                "lastSync" to System.currentTimeMillis(),
                "deviceId" to deviceId,
                "currentUser" to state.currentUser,
                "tvList" to tvs,
                "transaksiList" to txs,
                "appVersion" to "1.0.8",
            )
            if (state.licenseStatus.status.isNotEmpty()) {
                payload["licenseStatus"] = mapOf(
                    "status" to state.licenseStatus.status,
                    "pesan" to state.licenseStatus.pesan,
                    "expiresAt" to state.licenseStatus.expiresAt,
                    "maxTv" to state.licenseStatus.maxTv,
                )
            }

            // Write individual user docs FIRST (suspend context available here)
            val userDocOk = syncUsersMap(state.users)
            Log.i("CloudRepo", "syncUsersMap result=$userDocOk for ${state.users.size} users")

            return suspendCancellableCoroutine { cont ->
                val docRef = if (!username.isNullOrBlank()) {
                    firestore.collection("billingps_users").document(username)
                } else {
                    firestore.collection("billingps_devices").document(deviceId)
                }
                docRef.set(payload, com.google.firebase.firestore.SetOptions.merge())
                    .addOnSuccessListener { r ->
                        Log.i("CloudRepo", "syncAll success for ${docRef.path}")
                        if (!cont.isCompleted) cont.resume(true)
                    }
                    .addOnFailureListener { ex -> Log.i("CloudRepo", "syncAll failed: ${ex.message}"); if (!cont.isCompleted) cont.resume(false) }
            }
        }

    private suspend fun syncUsersMap(users: Map<String, UserData>): Boolean {
        if (users.isEmpty()) { Log.i("CloudRepo", "syncUsersMap: no users to sync"); return true }
        return suspendCancellableCoroutine { cont ->
            val tasks = users.map { (key, u) ->
                val doc = mapOf(
                    "username" to key,
                    "role" to u.role,
                    "email" to u.email,
                    "dibuat" to u.dibuat,
                )
                firestore.collection("billingps_users").document("_user_$key").set(doc)
            }
            com.google.android.gms.tasks.Tasks.whenAll(tasks)
                .addOnSuccessListener {
                    Log.i("CloudRepo", "syncUsersMap: ${users.size} users synced")
                    if (!cont.isCompleted) cont.resume(true)
                }
                .addOnFailureListener { ex ->
                    Log.i("CloudRepo", "syncUsersMap failed: ${ex.message}")
                    if (!cont.isCompleted) cont.resume(false)
                }
        }
    }

        suspend fun fetchTransaksiForUser(username: String): List<Transaksi> {
            if (username.isBlank()) return emptyList()
            Log.i("CloudRepo", "fetchTransaksiForUser start: $username")
            if (!ensureSignedIn()) { Log.i("CloudRepo", "ensureSignedIn failed for fetch"); return emptyList() }

            return suspendCancellableCoroutine { cont ->
                val docRef = firestore.collection("billingps_users").document(username)
                docRef.get()
                    .addOnSuccessListener { snap ->
                        if (!snap.exists()) { Log.i("CloudRepo", "no document for user $username"); if (!cont.isCompleted) cont.resume(emptyList()) ; return@addOnSuccessListener }
                        try {
                            val raw = snap.get("transaksiList") as? List<*>
                            val list = mutableListOf<Transaksi>()
                            raw?.forEach { item ->
                                if (item is Map<*, *>) {
                                    val id = item["id"] as? String ?: ""
                                    val waktu = item["waktu"] as? String ?: ""
                                    val kasir = item["kasir"] as? String ?: ""
                                    val kota = item["kota"] as? String ?: ""
                                    val paket = item["paket"] as? String ?: ""
                                    val total = (item["total"] as? Number)?.toInt() ?: 0
                                    val pesananMap = mutableMapOf<String, Int>()
                                    val pes = item["pesanan"] as? Map<*, *>
                                    pes?.forEach { (k, v) -> if (k is String && v is Number) pesananMap[k] = v.toInt() }
                                    list.add(Transaksi(id = id, waktu = waktu, kasir = kasir, kota = kota, paket = paket, pesanan = pesananMap, total = total))
                                }
                            }
                            Log.i("CloudRepo", "fetchTransaksiForUser: got ${list.size} items for $username")
                            if (!cont.isCompleted) cont.resume(list)
                        } catch (t: Throwable) {
                            Log.i("CloudRepo", "fetchTransaksiForUser parse failed: ${t.message}")
                            if (!cont.isCompleted) cont.resume(emptyList())
                        }
                    }
                    .addOnFailureListener { ex -> Log.i("CloudRepo", "fetchTransaksiForUser failed: ${ex.message}"); if (!cont.isCompleted) cont.resume(emptyList()) }
            }
        }

    private suspend fun queryLicenseByField(field: String, value: String): LicenseStatus? {
        return suspendCancellableCoroutine { cont ->
            firestore.collection("licenses")
                .whereEqualTo(field, value)
                .get()
                .addOnSuccessListener { snap ->
                    val activated = snap.documents
                        .filter { doc -> (doc.getLong("activatedAt") ?: 0) > 0 && doc.getBoolean("revoked") != true }
                        .maxByOrNull { doc -> doc.getLong("activatedAt") ?: 0 }
                    if (activated == null) { cont.resume(null); return@addOnSuccessListener }
                    val data = activated.data ?: run { cont.resume(null); return@addOnSuccessListener }
                    val paket = data["paket"] as? String ?: ""
                    val expiry = data["expiry"] as? String ?: ""
                    val maxTv = when (paket) {
                        "BULANAN" -> 5; "3BULAN" -> 8; "TAHUNAN" -> 15; "LIFETIME" -> 0; else -> 2
                    }
                    cont.resume(LicenseStatus(
                        status = "active",
                        pesan = "✅ Lisensi $paket aktif hingga $expiry",
                        expiresAt = expiry,
                        maxTv = maxTv,
                    ))
                }
                .addOnFailureListener { cont.resume(null) }
        }
    }

    private suspend fun fetchUserLicenseStatus(username: String): LicenseStatus? {
        if (username.isBlank()) return null
        if (!ensureSignedIn()) return null
        return suspendCancellableCoroutine { cont ->
            firestore.collection("billingps_users").document(username)
                .get()
                .addOnSuccessListener { snap ->
                    if (!snap.exists()) { cont.resume(null); return@addOnSuccessListener }
                    val ls = snap.get("licenseStatus") as? Map<*, *> ?: run { cont.resume(null); return@addOnSuccessListener }
                    val status = ls["status"] as? String ?: ""
                    if (status != "active") { cont.resume(null); return@addOnSuccessListener }
                    val expiry = ls["expiresAt"] as? String ?: ""
                    val maxTv = (ls["maxTv"] as? Number)?.toInt() ?: 2
                    val pesan = ls["pesan"] as? String ?: "✅ Lisensi aktif hingga $expiry"
                    Log.i("CloudRepo", "fetchUserLicenseStatus: found active license for $username, expiry=$expiry")
                    cont.resume(LicenseStatus(status = "active", pesan = pesan, expiresAt = expiry, maxTv = maxTv))
                }
                .addOnFailureListener { ex ->
                    Log.i("CloudRepo", "fetchUserLicenseStatus failed: ${ex.message}")
                    cont.resume(null)
                }
        }
    }

    suspend fun fetchLicenseForUser(usernameOrEmail: String): LicenseStatus? {
        if (usernameOrEmail.isBlank()) return null
        Log.i("CloudRepo", "fetchLicenseForUser start: $usernameOrEmail")
        if (!ensureSignedIn()) return null
        var result = queryLicenseByField("username", usernameOrEmail)
        if (result == null && usernameOrEmail.contains("@")) {
            result = queryLicenseByField("email", usernameOrEmail)
        }
        // Fallback: check user doc for licenseStatus written by License Generator
        if (result == null && !usernameOrEmail.contains("@")) {
            Log.i("CloudRepo", "fetchLicenseForUser: trying user doc fallback for $usernameOrEmail")
            result = fetchUserLicenseStatus(usernameOrEmail)
        }
        // Final fallback: check CONFIRMED invoices with kodeLisensi
        if (result == null && !usernameOrEmail.contains("@")) {
            Log.i("CloudRepo", "fetchLicenseForUser: trying confirmed invoice fallback for $usernameOrEmail")
            result = findConfirmedInvoiceLicense(usernameOrEmail)
        }
        if (result != null) {
            Log.i("CloudRepo", "fetchLicenseForUser: found ${result.status} for $usernameOrEmail")
        } else {
            Log.i("CloudRepo", "fetchLicenseForUser: no active license for $usernameOrEmail")
        }
        return result
    }

    suspend fun findAndActivateLicenseByUsername(username: String, expiry: String, deviceType: String = "android"): Boolean {
        if (username.isBlank()) return false
        if (!ensureSignedIn()) return false
        Log.i("CloudRepo", "findAndActivateLicenseByUsername for $username")
        return suspendCancellableCoroutine { cont ->
            firestore.collection("licenses")
                .whereEqualTo("username", username)
                .get()
                .addOnSuccessListener { snap ->
                    val unactivated = snap.documents
                        .filter { doc -> (doc.getLong("activatedAt") ?: 0) == 0L && doc.getBoolean("revoked") != true }
                        .maxByOrNull { doc -> doc.getLong("generatedAt") ?: 0 }
                    if (unactivated == null) {
                        Log.i("CloudRepo", "findAndActivate: no unactivated license for $username")
                        cont.resume(false); return@addOnSuccessListener
                    }
                    val nowMs = System.currentTimeMillis()
                    val updates = mutableMapOf<String, Any>(
                        "activatedAt" to nowMs,
                        "activatedDeviceId" to deviceId,
                    )
                    if (expiry.isNotEmpty()) updates["expiry"] = expiry
                    
                    // Multi-device support
                    val existingDevices = (unactivated.get("activatedDevices") as? List<Map<String, Any>>)?.toMutableList() ?: mutableListOf()
                    val alreadyExists = existingDevices.any { it["deviceType"] == deviceType }
                    if (!alreadyExists) {
                        existingDevices.add(mapOf(
                            "deviceType" to deviceType,
                            "deviceId" to deviceId,
                            "activatedAt" to nowMs,
                        ))
                    }
                    updates["activatedDevices"] = existingDevices
                    if (!unactivated.contains("maxActivations")) {
                        updates["maxActivations"] = 2
                    }
                    
                    firestore.collection("licenses").document(unactivated.id)
                        .update(updates)
                        .addOnSuccessListener {
                            Log.i("CloudRepo", "findAndActivate: activated ${unactivated.id} for $username")
                            cont.resume(true)
                        }
                        .addOnFailureListener { ex ->
                            Log.i("CloudRepo", "findAndActivate: failed ${ex.message}")
                            cont.resume(false)
                        }
                }
                .addOnFailureListener { ex ->
                    Log.i("CloudRepo", "findAndActivate: query failed ${ex.message}")
                    cont.resume(false)
                }
        }
    }

    suspend fun findUsernameByEmail(email: String): String? {
        if (email.isBlank()) return null
        Log.i("CloudRepo", "findUsernameByEmail start: $email")
        if (!ensureSignedIn()) return null
        return suspendCancellableCoroutine { cont ->
            firestore.collection("billingps_users")
                .whereEqualTo("email", email)
                .get()
                .addOnSuccessListener { snap ->
                    for (doc in snap.documents) {
                        val docId = doc.id
                        if (docId.startsWith("_user_")) {
                            val username = doc.getString("username")
                            if (!username.isNullOrBlank()) {
                                Log.i("CloudRepo", "findUsernameByEmail: found $username for $email")
                                cont.resume(username); return@addOnSuccessListener
                            }
                        }
                    }
                    for (doc in snap.documents) {
                        val username = doc.getString("username")
                        if (!username.isNullOrBlank()) {
                            Log.i("CloudRepo", "findUsernameByEmail: found $username for $email")
                            cont.resume(username); return@addOnSuccessListener
                        }
                    }
                    Log.i("CloudRepo", "findUsernameByEmail: no user for $email")
                    cont.resume(null)
                }
                .addOnFailureListener { ex -> Log.i("CloudRepo", "findUsernameByEmail failed: ${ex.message}"); cont.resume(null) }
        }
    }

    suspend fun findConfirmedInvoiceLicense(username: String, deviceType: String = "android"): LicenseStatus? {
        if (username.isBlank()) return null
        if (!ensureSignedIn()) return null
        return suspendCancellableCoroutine { cont ->
            firestore.collection("licenses")
                .whereEqualTo("username", username)
                .get()
                .addOnSuccessListener { licSnap ->
                    // Try to find an unactivated license (activatedAt == 0) for this user
                    val unactivated = licSnap.documents
                        .filter { doc -> (doc.getLong("activatedAt") ?: 0) == 0L && doc.getBoolean("revoked") != true }
                        .maxByOrNull { doc -> doc.getLong("generatedAt") ?: 0 }
                    if (unactivated == null) { cont.resume(null); return@addOnSuccessListener }
                    val data = unactivated.data ?: run { cont.resume(null); return@addOnSuccessListener }
                    val kode = data["kode"] as? String ?: ""
                    val paket = data["paket"] as? String ?: ""
                    val expiry = data["expiry"] as? String ?: ""
                    if (kode.isEmpty() || paket.isEmpty()) { cont.resume(null); return@addOnSuccessListener }
                    val maxTv = when (paket) {
                        "BULANAN" -> 5; "3BULAN" -> 8; "TAHUNAN" -> 15; "LIFETIME" -> 0; else -> 2
                    }
                    Log.i("CloudRepo", "findConfirmedInvoiceLicense: found unactivated $kode for $username, expiry=$expiry")
                    
                    // Activate with multi-device support
                    val nowMs = System.currentTimeMillis()
                    val updates = mutableMapOf<String, Any>(
                        "activatedAt" to nowMs,
                        "activatedDeviceId" to deviceId,
                    )
                    val existingDevices = (unactivated.get("activatedDevices") as? List<Map<String, Any>>)?.toMutableList() ?: mutableListOf()
                    val alreadyExists = existingDevices.any { it["deviceType"] == deviceType }
                    if (!alreadyExists) {
                        existingDevices.add(mapOf(
                            "deviceType" to deviceType,
                            "deviceId" to deviceId,
                            "activatedAt" to nowMs,
                        ))
                    }
                    updates["activatedDevices"] = existingDevices
                    if (!unactivated.contains("maxActivations")) {
                        updates["maxActivations"] = 2
                    }
                    
                    firestore.collection("licenses").document(unactivated.id)
                        .update(updates)
                        .addOnSuccessListener {
                            Log.i("CloudRepo", "findConfirmedInvoiceLicense: activated $kode for $username")
                        }
                        .addOnFailureListener { ex ->
                            Log.i("CloudRepo", "findConfirmedInvoiceLicense: activation failed: ${ex.message}")
                        }
                    cont.resume(LicenseStatus(
                        status = "active",
                        pesan = "✅ Lisensi $paket aktif hingga $expiry",
                        expiresAt = expiry,
                        maxTv = maxTv,
                    ))
                }
                .addOnFailureListener { ex ->
                    Log.i("CloudRepo", "findConfirmedInvoiceLicense failed: ${ex.message}")
                    cont.resume(null)
                }
        }
    }

    suspend fun findLicenseByCode(kode: String): LicenseRecord? {
        if (!ensureSignedIn()) return null
        return suspendCancellableCoroutine { cont ->
            firestore.collection("licenses").whereEqualTo("kode", kode).get()
                .addOnSuccessListener { snap ->
                    if (snap.isEmpty) { cont.resume(null); return@addOnSuccessListener }
                    val doc = snap.documents.first()
                    val data = doc.data ?: run { cont.resume(null); return@addOnSuccessListener }
                    
                    // Parse activatedDevices
                    val rawDevices = data["activatedDevices"] as? List<Map<String, Any>>
                    val activatedDevices = rawDevices?.mapNotNull { dev ->
                        val dt = dev["deviceType"] as? String ?: return@mapNotNull null
                        val di = dev["deviceId"] as? String ?: ""
                        val da = (dev["activatedAt"] as? Number)?.toLong() ?: 0L
                        ActivatedDevice(deviceType = dt, deviceId = di, activatedAt = da)
                    } ?: emptyList()
                    
                    cont.resume(LicenseRecord(
                        id = doc.id,
                        kode = data["kode"] as? String ?: "",
                        payload = data["payload"] as? String ?: "",
                        signature = data["signature"] as? String ?: "",
                        paket = data["paket"] as? String ?: "",
                        username = data["username"] as? String ?: "",
                        email = data["email"] as? String ?: "",
                        expiry = data["expiry"] as? String ?: "",
                        generatedBy = data["generatedBy"] as? String ?: "",
                        generatedAt = data["generatedAt"] as? Long ?: 0,
                        activatedAt = data["activatedAt"] as? Long ?: 0,
                        activatedDeviceId = data["activatedDeviceId"] as? String ?: "",
                        revoked = data["revoked"] as? Boolean ?: false,
                        maxActivations = (data["maxActivations"] as? Number)?.toInt() ?: 2,
                        activatedDevices = activatedDevices,
                    ))
                }
                .addOnFailureListener { cont.resume(null) }
        }
    }

    suspend fun saveInvoiceToCloud(inv: Invoice) {
        if (inv.id.isBlank()) return
        if (!ensureSignedIn()) return
        suspendCancellableCoroutine { cont ->
            val data = mapOf(
                "id" to inv.id,
                "username" to inv.username,
                "email" to inv.email,
                "paket" to inv.paket,
                "harga" to inv.harga,
                "status" to inv.status,
                "dibuat" to inv.dibuat,
                "dibayar" to inv.dibayar,
                "confirmedBy" to inv.confirmedBy,
                "kodeLisensi" to inv.kodeLisensi,
                "buktiBase64" to inv.buktiBase64,
            )
            firestore.collection("invoices").document(inv.id)
                .set(data)
                .addOnSuccessListener { if (!cont.isCompleted) cont.resume(Unit) }
                .addOnFailureListener { Log.i("CloudRepo", "saveInvoice failed: ${it.message}"); if (!cont.isCompleted) cont.resume(Unit) }
        }
    }

    fun listenUserInvoices(username: String, onUpdate: (Invoice) -> Unit): ListenerRegistration? {
        if (username.isBlank()) return null
        if (auth.currentUser == null) return null
        Log.i("CloudRepo", "listenUserInvoices: starting listener for $username")
        return firestore.collection("invoices")
            .whereEqualTo("username", username)
            .addSnapshotListener { snap, error ->
                if (error != null) { Log.e("CloudRepo", "listenUserInvoices error: ${error.message}"); return@addSnapshotListener }
                if (snap == null) return@addSnapshotListener
                for (doc in snap.documents) {
                    val d = doc.data ?: continue
                    val status = d["status"] as? String ?: ""
                    val kodeLisensi = d["kodeLisensi"] as? String ?: ""
                    if (status == "CONFIRMED" && kodeLisensi.isNotEmpty()) {
                        val inv = Invoice(
                            id = d["id"] as? String ?: doc.id,
                            username = d["username"] as? String ?: "",
                            email = d["email"] as? String ?: "",
                            paket = d["paket"] as? String ?: "",
                            harga = (d["harga"] as? Number)?.toInt() ?: 0,
                            status = status,
                            dibuat = d["dibuat"] as? Long ?: 0L,
                            dibayar = d["dibayar"] as? Long ?: 0L,
                            confirmedBy = d["confirmedBy"] as? String ?: "",
                            kodeLisensi = kodeLisensi,
                            buktiBase64 = d["buktiBase64"] as? String ?: "",
                        )
                        Log.i("CloudRepo", "listenUserInvoices: CONFIRMED invoice ${inv.id}")
                        onUpdate(inv)
                    }
                }
            }
    }

    suspend fun activateLicense(docId: String, expiry: String = "", deviceType: String = "android"): Boolean {
        if (!ensureSignedIn()) return false
        return suspendCancellableCoroutine { cont ->
            val nowMs = System.currentTimeMillis()
            val updates = mutableMapOf<String, Any>(
                "activatedAt" to nowMs,
                "activatedDeviceId" to deviceId,
            )
            if (expiry.isNotEmpty()) updates["expiry"] = expiry
            
            // Multi-device: read current activatedDevices, add this device
            firestore.collection("licenses").document(docId)
                .get()
                .addOnSuccessListener { snap ->
                    val existingDevices = if (snap.exists()) {
                        (snap.get("activatedDevices") as? List<Map<String, Any>>)?.toMutableList() ?: mutableListOf()
                    } else {
                        mutableListOf()
                    }
                    
                    // Only add if this device type not already registered
                    val alreadyExists = existingDevices.any { it["deviceType"] == deviceType }
                    if (!alreadyExists) {
                        existingDevices.add(mapOf(
                            "deviceType" to deviceType,
                            "deviceId" to deviceId,
                            "activatedAt" to nowMs,
                        ))
                    }
                    updates["activatedDevices"] = existingDevices
                    
                    // Set maxActivations if not set
                    if (!snap.exists() || !snap.contains("maxActivations")) {
                        updates["maxActivations"] = 2
                    }
                    
                    firestore.collection("licenses").document(docId)
                        .update(updates)
                        .addOnSuccessListener { cont.resume(true) }
                        .addOnFailureListener { cont.resume(false) }
                }
                .addOnFailureListener { cont.resume(false) }
        }
    }

    fun listenUserLicense(username: String, onUpdate: (LicenseStatus?) -> Unit): ListenerRegistration? {
        if (username.isBlank()) return null
        if (auth.currentUser == null) return null
        Log.i("CloudRepo", "listenUserLicense: starting listener for $username")
        return firestore.collection("billingps_users").document(username)
            .addSnapshotListener { snap, error ->
                if (error != null) { Log.e("CloudRepo", "listenUserLicense error: ${error.message}"); return@addSnapshotListener }
                if (snap == null || !snap.exists()) { return@addSnapshotListener }
                val ls = snap.get("licenseStatus") as? Map<*, *> ?: run { onUpdate(null); return@addSnapshotListener }
                val status = ls["status"] as? String ?: ""
                if (status != "active") { onUpdate(null); return@addSnapshotListener }
                val expiry = ls["expiresAt"] as? String ?: ""
                val maxTv = (ls["maxTv"] as? Number)?.toInt() ?: 2
                val pesan = ls["pesan"] as? String ?: "✅ Lisensi aktif hingga $expiry"
                Log.i("CloudRepo", "listenUserLicense: license updated for $username, expiry=$expiry")
                onUpdate(LicenseStatus(status = "active", pesan = pesan, expiresAt = expiry, maxTv = maxTv))
            }
    }
}
