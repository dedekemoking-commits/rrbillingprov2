package com.billingps.aptv.cloud

import android.app.Application
import android.provider.Settings
import android.util.Log
import com.billingps.aptv.models.*
import com.billingps.aptv.utils.ECDSAUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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
    suspend fun syncAll(state: AppUiState, username: String? = null): Boolean {
            Log.i("CloudRepo", "syncAll start for user=${username ?: "(device)"}")
            if (!ensureSignedIn()) { Log.i("CloudRepo", "ensureSignedIn failed"); return false }

            val usersMap = state.users.mapValues { (_, u) ->
                mapOf(
                    "role" to u.role,
                    "email" to u.email,
                    "dibuat" to u.dibuat,
                )
            }

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

            val payload = mapOf(
                "lastSync" to System.currentTimeMillis(),
                "deviceId" to deviceId,
                "currentUser" to state.currentUser,
                "users" to usersMap,
                "tvList" to tvs,
                "transaksiList" to txs,
                "licenseStatus" to mapOf(
                    "status" to state.licenseStatus.status,
                    "pesan" to state.licenseStatus.pesan,
                    "expiresAt" to state.licenseStatus.expiresAt,
                    "maxTv" to state.licenseStatus.maxTv,
                ),
                "appVersion" to "1.0.8",
            )

            return suspendCancellableCoroutine { cont ->
                val docRef = if (!username.isNullOrBlank()) {
                    // Save under per-user document
                    firestore.collection("billingps_users").document(username)
                } else {
                    firestore.collection("billingps_devices").document(deviceId)
                }
                docRef.set(payload)
                    .addOnSuccessListener { r -> Log.i("CloudRepo", "syncAll success for ${docRef.path}"); if (!cont.isCompleted) cont.resume(true) }
                    .addOnFailureListener { ex -> Log.i("CloudRepo", "syncAll failed: ${ex.message}"); if (!cont.isCompleted) cont.resume(false) }
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

    suspend fun findLicenseByCode(kode: String): LicenseRecord? {
        if (!ensureSignedIn()) return null
        return suspendCancellableCoroutine { cont ->
            firestore.collection("licenses").whereEqualTo("kode", kode).get()
                .addOnSuccessListener { snap ->
                    if (snap.isEmpty) { cont.resume(null); return@addOnSuccessListener }
                    val doc = snap.documents.first()
                    val data = doc.data ?: run { cont.resume(null); return@addOnSuccessListener }
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
                    ))
                }
                .addOnFailureListener { cont.resume(null) }
        }
    }

    suspend fun activateLicense(docId: String): Boolean {
        if (!ensureSignedIn()) return false
        return suspendCancellableCoroutine { cont ->
            firestore.collection("licenses").document(docId)
                .update("activatedAt", System.currentTimeMillis(), "activatedDeviceId", deviceId)
                .addOnSuccessListener { cont.resume(true) }
                .addOnFailureListener { cont.resume(false) }
        }
    }
}
