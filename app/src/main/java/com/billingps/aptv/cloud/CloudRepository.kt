package com.billingps.aptv.cloud

import android.app.Application
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.billingps.aptv.models.*
import com.billingps.aptv.utils.ECDSAUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.util.UUID

/**
 * Simple Firebase-backed repository for syncing app state to Firestore.
 * Uses anonymous sign-in as fallback for device-level sync, but
 * PREFERS the user's email/password Firebase Auth session when available.
 *
 * SECURITY NOTE: Anonymous auth should NOT be used for sensitive operations.
 * The app should require email/password Firebase Auth for license activation,
 * invoice confirmation, and admin operations.
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
        if (user != null) {
            if (user.isAnonymous) {
                Log.w("CloudRepo", "Using anonymous auth (degraded security) for ${user.uid}")
            } else {
                Log.i("CloudRepo", "Signed in as ${user.email ?: user.uid}")
            }
            cont.resume(true); return@suspendCancellableCoroutine
        }
        Log.w("CloudRepo", "No auth session — signing in anonymously (DEGRADED SECURITY)")
        auth.signInAnonymously()
            .addOnSuccessListener { r -> if (!cont.isCompleted) { Log.i("CloudRepo", "Anonymous sign-in success: ${r.user?.uid}"); cont.resume(true) } }
            .addOnFailureListener { ex -> if (!cont.isCompleted) { Log.i("CloudRepo", "Anonymous sign-in failed: ${ex.message}"); cont.resume(false) } }
    }

    /**
     * Check if the current Firebase Auth user is fully authenticated (not anonymous).
     * Critical operations should check this before proceeding.
     */
    fun isFullyAuthenticated(): Boolean {
        val user = auth.currentUser ?: return false
        return !user.isAnonymous
    }

    fun currentDeviceId(): String = deviceId

    fun currentDeviceName(): String = try {
        listOf(Build.MANUFACTURER, Build.MODEL)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "perangkat lain" }
    } catch (_: Throwable) {
        "perangkat lain"
    }

    /**
     * Atomically claim this account for the current device. Requires a live
     * Firestore connection so two devices cannot silently share a session.
     */
    suspend fun claimDevice(username: String, force: Boolean = false): DeviceClaimResult {
        if (username.isBlank()) return DeviceClaimResult(error = "Username kosong")
        if (!ensureSignedIn()) {
            return DeviceClaimResult(error = "Tidak dapat terhubung ke server. Login membutuhkan internet.")
        }
        val docRef = firestore.collection("billingps_users").document(username)
        return suspendCancellableCoroutine { cont ->
            firestore.runTransaction { transaction ->
                val snap = transaction.get(docRef)
                val activeId = snap.getString("activeDeviceId").orEmpty()
                val activeName = snap.getString("activeDeviceName").orEmpty()
                if (activeId.isNotBlank() && activeId != deviceId && !force) {
                    DeviceClaimResult(
                        success = false,
                        conflict = DeviceConflict(
                            activeDeviceId = activeId,
                            activeDeviceName = activeName.ifBlank { "perangkat lain" },
                        ),
                        error = "Akun sedang digunakan di ${activeName.ifBlank { "perangkat lain" }}",
                    )
                } else {
                    val sessionId = UUID.randomUUID().toString()
                    transaction.set(
                        docRef,
                        mapOf(
                            "activeDeviceId" to deviceId,
                            "activeDeviceName" to currentDeviceName(),
                            "activeSessionId" to sessionId,
                            "lastActive" to System.currentTimeMillis(),
                        ),
                        SetOptions.merge(),
                    )
                    DeviceClaimResult(success = true, sessionId = sessionId)
                }
            }
                .addOnSuccessListener { result ->
                    Log.i("CloudRepo", "claimDevice success=${result.success} conflict=${result.conflict != null}")
                    if (!cont.isCompleted) cont.resume(result)
                }
                .addOnFailureListener { ex ->
                    Log.i("CloudRepo", "claimDevice failed: ${ex.message}")
                    if (!cont.isCompleted) {
                        cont.resume(DeviceClaimResult(error = "Gagal memverifikasi perangkat. Periksa internet."))
                    }
                }
        }
    }

    fun listenDeviceSession(
        username: String,
        sessionId: String,
        onConflict: (DeviceConflict) -> Unit,
    ): ListenerRegistration? {
        if (username.isBlank() || sessionId.isBlank()) return null
        if (auth.currentUser == null) return null
        return firestore.collection("billingps_users").document(username)
            .addSnapshotListener { snap, error ->
                if (error != null || snap == null || !snap.exists()) return@addSnapshotListener
                val activeId = snap.getString("activeDeviceId").orEmpty()
                val activeSession = snap.getString("activeSessionId").orEmpty()
                val activeName = snap.getString("activeDeviceName").orEmpty()
                val replaced = (activeSession.isNotBlank() && activeSession != sessionId) ||
                    (activeId.isNotBlank() && activeId != deviceId)
                if (replaced) {
                    onConflict(
                        DeviceConflict(
                            activeDeviceId = activeId,
                            activeDeviceName = activeName.ifBlank { "perangkat lain" },
                        )
                    )
                }
            }
    }

    suspend fun releaseDeviceSession(username: String, sessionId: String): Boolean {
        if (username.isBlank() || sessionId.isBlank()) return false
        if (auth.currentUser == null) return false
        val docRef = firestore.collection("billingps_users").document(username)
        return suspendCancellableCoroutine { cont ->
            firestore.runTransaction { transaction ->
                val snap = transaction.get(docRef)
                val activeSession = snap.getString("activeSessionId").orEmpty()
                if (activeSession.isNotBlank() && activeSession != sessionId) {
                    false
                } else {
                    transaction.update(
                        docRef,
                        mapOf(
                            "activeDeviceId" to "",
                            "activeDeviceName" to "",
                            "activeSessionId" to "",
                            "lastActive" to System.currentTimeMillis(),
                        ),
                    )
                    true
                }
            }
                .addOnSuccessListener { ok -> if (!cont.isCompleted) cont.resume(ok) }
                .addOnFailureListener { ex ->
                    Log.i("CloudRepo", "releaseDeviceSession failed: ${ex.message}")
                    if (!cont.isCompleted) cont.resume(false)
                }
        }
    }

    /**
     * Require non-anonymous Firebase Auth for sensitive operations.
     * Returns true only if a real (email/password) Firebase Auth session exists.
     */
    suspend fun requireRealAuth(): Boolean = suspendCancellableCoroutine { cont ->
        val user = auth.currentUser
        if (user != null && !user.isAnonymous) {
            Log.i("CloudRepo", "Real auth confirmed: ${user.email ?: user.uid}")
            cont.resume(true)
        } else {
            Log.w("CloudRepo", "Real auth required but not available")
            cont.resume(false)
        }
        return@suspendCancellableCoroutine
    }

    /**
     * Sync the important parts of the app state to Firestore. Returns true on success.
     * SECURITY: prefers non-anonymous auth; warns if using anonymous
     */
    suspend fun writeLicenseStatusToUserDoc(username: String, ls: Map<String, Any>): Boolean {
        if (username.isBlank()) return false
        if (!ensureSignedIn()) return false
        if (!isFullyAuthenticated()) {
            Log.w("CloudRepo", "writeLicenseStatusToUserDoc using anonymous auth — data may be rejected by Firestore rules")
        }
        Log.i("CloudRepo", "writeLicenseStatusToUserDoc: username=$username ls=$ls")
        return suspendCancellableCoroutine { cont ->
            firestore.collection("billingps_users").document(username)
                .set(mapOf("licenseStatus" to ls), com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener { Log.i("CloudRepo", "writeLicenseStatusToUserDoc success"); cont.resume(true) }
                .addOnFailureListener { Log.i("CloudRepo", "writeLicenseStatusToUserDoc failed: ${it.message}"); cont.resume(false) }
        }
    }

    suspend fun syncAll(state: AppUiState, username: String? = null, appVersion: String = "0.0.0"): Boolean {
            Log.i("CloudRepo", "syncAll start for user=${username ?: "(device)"}, appVersion=$appVersion")
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
                    "timerStart" to tv.timerStart,
                    "timerDurasi" to tv.timerDurasi,
                    "timerActive" to tv.timerActive,
                    "bebas" to tv.bebas,
                    "paketHarga" to tv.paketHarga,
                    "totalPesanan" to tv.totalPesanan,
                    "trxId" to tv.trxId,
                    "pesanan" to tv.pesanan,
                    "pesananHarga" to tv.pesananHarga,
                    "bebasMulai" to tv.bebasMulai,
                    "bebasHargaPerJam" to tv.bebasHargaPerJam,
                    "bebasPesananTotal" to tv.bebasPesananTotal,
                    "cancelBatas" to tv.cancelBatas,
                    "sudahBayar" to tv.sudahBayar,
                    "namaRental" to tv.namaRental,
                    "popupMode" to tv.popupMode,
                    "popupLastMinutes" to tv.popupLastMinutes,
                    "bebasInterval" to tv.bebasInterval,
                    "bebasDurasi" to tv.bebasDurasi,
                    "hdmiPort" to tv.hdmiPort,
                    "hdmiInputHint" to tv.hdmiInputHint,
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
                    "paketHarga" to t.paketHarga,
                    "pesananHarga" to t.pesananHarga,
                    "tvJenisPs" to t.tvJenisPs,
                    "sudahBayar" to t.sudahBayar,
                    "metode" to t.metode,
                )
            }

            val payload = mutableMapOf<String, Any>(
                "lastSync" to System.currentTimeMillis(),
                "deviceId" to deviceId,
                "currentUser" to state.currentUser,
                "tvList" to tvs,
                "transaksiList" to txs,
                "appVersion" to appVersion,
                "lastActive" to System.currentTimeMillis(),
            )
            if (state.licenseStatus.status.isNotEmpty()) {
                payload["licenseStatus"] = mapOf(
                    "status" to state.licenseStatus.status,
                    "pesan" to state.licenseStatus.pesan,
                    "expiresAt" to state.licenseStatus.expiresAt,
                    "maxTv" to state.licenseStatus.maxTv,
                    "promoAddTv" to state.licenseStatus.promoAddTv,
                    "paket" to state.licenseStatus.paket,
                    "clientEnabled" to state.licenseStatus.clientEnabled,
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
                    "namaRental" to u.namaRental,
                    "alamatRental" to u.alamatRental,
                    "whatsappRental" to u.whatsappRental,
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

        suspend fun deleteUserDoc(key: String): Boolean {
        if (key.isBlank()) return false
        if (!ensureSignedIn()) return false
        return suspendCancellableCoroutine { cont ->
            firestore.collection("billingps_users").document("_user_$key").delete()
                .addOnSuccessListener { Log.i("CloudRepo", "deleteUserDoc success: $key"); if (!cont.isCompleted) cont.resume(true) }
                .addOnFailureListener { ex -> Log.i("CloudRepo", "deleteUserDoc failed: ${ex.message}"); if (!cont.isCompleted) cont.resume(false) }
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
                                    val pesananHarga = mutableMapOf<String, Int>()
                                    val ph = item["pesananHarga"] as? Map<*, *>
                                    ph?.forEach { (k, v) -> if (k is String && v is Number) pesananHarga[k] = v.toInt() }
                                    list.add(Transaksi(id = id, waktu = waktu, kasir = kasir, kota = kota, paket = paket, pesanan = pesananMap, total = total,
                                        paketHarga = (item["paketHarga"] as? Number)?.toInt() ?: 0,
                                        pesananHarga = pesananHarga,
                                        tvJenisPs = item["tvJenisPs"] as? String ?: "",
                                        sudahBayar = item["sudahBayar"] as? Boolean ?: false,
                                        metode = item["metode"] as? String ?: ""))
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

    suspend fun fetchTvListForUser(username: String): List<TvData> {
        if (username.isBlank()) return emptyList()
        if (!ensureSignedIn()) return emptyList()
        return suspendCancellableCoroutine { cont ->
            firestore.collection("billingps_users").document(username).get()
                .addOnSuccessListener { snap ->
                    if (!snap.exists()) { cont.resume(emptyList()); return@addOnSuccessListener }
                    try {
                        val raw = snap.get("tvList") as? List<*>
                        val list = mutableListOf<TvData>()
                        raw?.forEach { item ->
                            if (item is Map<*, *>) {
                                val tvPesanan = mutableMapOf<String, Int>()
                                (item["pesanan"] as? Map<*, *>)?.forEach { (k, v) ->
                                    if (k is String && v is Number) tvPesanan[k] = v.toInt()
                                }
                                val tvPesananHarga = mutableMapOf<String, Int>()
                                (item["pesananHarga"] as? Map<*, *>)?.forEach { (k, v) ->
                                    if (k is String && v is Number) tvPesananHarga[k] = v.toInt()
                                }
                                list.add(TvData(
                                    id = item["id"] as? String ?: "",
                                    nama = item["nama"] as? String ?: "",
                                    ip = item["ip"] as? String ?: "",
                                    port = (item["port"] as? Number)?.toInt() ?: 5555,
                                    jenisPs = item["jenisPs"] as? String ?: "PS3",
                                    paketAktif = item["paketAktif"] as? String ?: "",
                                    sisaDetik = (item["sisaDetik"] as? Number)?.toLong() ?: 0,
                                    timerStart = (item["timerStart"] as? Number)?.toLong() ?: 0L,
                                    timerDurasi = (item["timerDurasi"] as? Number)?.toLong() ?: 0L,
                                    timerActive = item["timerActive"] as? Boolean ?: false,
                                    bebas = item["bebas"] as? Boolean ?: false,
                                    paketHarga = (item["paketHarga"] as? Number)?.toInt() ?: 0,
                                    totalPesanan = (item["totalPesanan"] as? Number)?.toInt() ?: 0,
                                    trxId = item["trxId"] as? String ?: "",
                                    pesanan = tvPesanan,
                                    pesananHarga = tvPesananHarga,
                                    bebasMulai = (item["bebasMulai"] as? Number)?.toLong() ?: 0L,
                                    bebasHargaPerJam = (item["bebasHargaPerJam"] as? Number)?.toInt() ?: 0,
                                    bebasPesananTotal = (item["bebasPesananTotal"] as? Number)?.toInt() ?: 0,
                                    cancelBatas = (item["cancelBatas"] as? Number)?.toLong() ?: 0L,
                                    sudahBayar = item["sudahBayar"] as? Boolean ?: false,
                                    namaRental = item["namaRental"] as? String ?: "",
                                    popupMode = item["popupMode"] as? String ?: "always",
                                    popupLastMinutes = (item["popupLastMinutes"] as? Number)?.toInt() ?: 5,
                                    bebasInterval = (item["bebasInterval"] as? Number)?.toInt() ?: 60,
                                    bebasDurasi = (item["bebasDurasi"] as? Number)?.toInt() ?: 30,
                                    hdmiPort = (item["hdmiPort"] as? Number)?.toInt() ?: 0,
                                    hdmiInputHint = item["hdmiInputHint"] as? String ?: "",
                                ))
                            }
                        }
                        cont.resume(list)
                    } catch (t: Throwable) { cont.resume(emptyList()) }
                }
                .addOnFailureListener { cont.resume(emptyList()) }
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
                    if (activated == null) { Log.i("CloudRepo", "queryLicenseByField($field=$value): no activated doc"); cont.resume(null); return@addOnSuccessListener }
                    val data = activated.data ?: run { Log.i("CloudRepo", "queryLicenseByField($field=$value): no data"); cont.resume(null); return@addOnSuccessListener }
                    val paket = data["paket"] as? String ?: ""
                    val expiry = data["expiry"] as? String ?: ""
                    Log.i("CloudRepo", "queryLicenseByField($field=$value): found paket='$paket' expiry=$expiry")
                    val promoAddTv = (data["promoMaxTv"] as? Number)?.toInt() ?: 0
                    val storedMaxTv = (data["maxTv"] as? Number)?.toInt() ?: 0
                    val effectiveMaxTv = when {
                        storedMaxTv > 0 -> storedMaxTv
                        promoAddTv > 0 -> promoAddTv
                        else -> LicenseCatalog.legacyMaxTv(paket)
                    }
                    cont.resume(LicenseStatus(
                        status = "active",
                        pesan = "✅ Lisensi $paket aktif hingga $expiry",
                        expiresAt = expiry,
                        maxTv = effectiveMaxTv,
                        promoAddTv = promoAddTv,
                        paket = paket,
                        clientEnabled = (data["clientEnabled"] as? Boolean)
                            ?: LicenseCatalog.clientEnabled(paket),
                    ))
                }
                .addOnFailureListener { ex -> Log.i("CloudRepo", "queryLicenseByField($field=$value) failed: ${ex.message}"); cont.resume(null) }
        }
    }

    suspend fun fetchUserLicenseStatus(username: String): LicenseStatus? {
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
                    val storedMaxTv = (ls["maxTv"] as? Number)?.toInt() ?: 0
                    val pesan = ls["pesan"] as? String ?: "✅ Lisensi aktif hingga $expiry"
                    val promoAddTv = (ls["promoAddTv"] as? Number)?.toInt() ?: 0
                    val paket = ls["paket"] as? String ?: ""
                    val maxTv = when {
                        promoAddTv > 0 -> promoAddTv
                        storedMaxTv > 0 -> storedMaxTv // trust stored value (was set by syncToCloud or aktivasiLisensi)
                        pesan.contains("LIFETIME") -> 0
                        pesan.contains("TAHUNAN") || pesan.contains("1 Tahun") -> 15
                        pesan.contains("3BULAN") || pesan.contains("3 Bulan") -> 8
                        pesan.contains("BULANAN") || pesan.contains("1 Bulan") -> 5
                        else -> 2
                    }
                    Log.i("CloudRepo", "fetchUserLicenseStatus: found active license for $username, expiry=$expiry")
                    cont.resume(LicenseStatus(
                        status = "active",
                        pesan = pesan,
                        expiresAt = expiry,
                        maxTv = maxTv,
                        promoAddTv = promoAddTv,
                        paket = paket,
                        clientEnabled = (ls["clientEnabled"] as? Boolean)
                            ?: LicenseCatalog.clientEnabled(paket),
                    ))
                }
                .addOnFailureListener { ex ->
                    Log.i("CloudRepo", "fetchUserLicenseStatus failed: ${ex.message}")
                    cont.resume(null)
                }
        }
    }

    suspend fun fetchLicenseForUser(usernameOrEmail: String): LicenseStatus? {
        if (usernameOrEmail.isBlank()) return null
        Log.i("CloudRepo", "fetchLicenseForUser start: '$usernameOrEmail'")
        if (!ensureSignedIn()) { Log.i("CloudRepo", "fetchLicenseForUser: ensureSignedIn failed"); return null }
        var result = queryLicenseByField("username", usernameOrEmail)
        if (result == null) {
            Log.i("CloudRepo", "fetchLicenseForUser: also trying email field for '$usernameOrEmail'")
            result = queryLicenseByField("email", usernameOrEmail)
        }
        // Fallback: check user doc for licenseStatus written by License Generator
        if (result == null && !usernameOrEmail.contains("@")) {
            Log.i("CloudRepo", "fetchLicenseForUser: trying user doc fallback for '$usernameOrEmail'")
            result = fetchUserLicenseStatus(usernameOrEmail)
        }
        // Final fallback: check CONFIRMED invoices with kodeLisensi
        if (result == null && !usernameOrEmail.contains("@")) {
            Log.i("CloudRepo", "fetchLicenseForUser: trying confirmed invoice fallback for '$usernameOrEmail'")
            result = findConfirmedInvoiceLicense(usernameOrEmail)
        }
        if (result != null) {
            Log.i("CloudRepo", "fetchLicenseForUser: FOUND status=${result.status} maxTv=${result.maxTv} expires=${result.expiresAt} pesan='${result.pesan}'")
        } else {
            Log.i("CloudRepo", "fetchLicenseForUser: NO active license for '$usernameOrEmail'")
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
                    val promoAddTv = (data["promoMaxTv"] as? Number)?.toInt() ?: 0
                    val storedMaxTv = (data["maxTv"] as? Number)?.toInt() ?: 0
                    val maxTv = when {
                        storedMaxTv > 0 -> storedMaxTv
                        promoAddTv > 0 -> promoAddTv
                        else -> LicenseCatalog.legacyMaxTv(paket)
                    }
                    val clientEnabled = (data["clientEnabled"] as? Boolean)
                        ?: LicenseCatalog.clientEnabled(paket)
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
                        promoAddTv = promoAddTv,
                        paket = paket,
                        clientEnabled = clientEnabled,
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
                        maxActivations = (data["maxActivations"] as? Number)?.toInt() ?: 1,
                        promoMaxTv = (data["promoMaxTv"] as? Number)?.toInt() ?: 0,
                        maxTv = (data["maxTv"] as? Number)?.toInt() ?: 0,
                        clientEnabled = (data["clientEnabled"] as? Boolean)
                            ?: LicenseCatalog.clientEnabled(data["paket"] as? String),
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
                "jumlahTv" to inv.jumlahTv,
                "mode" to inv.mode,
                "baseMaxTv" to inv.baseMaxTv,
            )
            firestore.collection("invoices").document(inv.id)
                .set(data)
                .addOnSuccessListener { if (!cont.isCompleted) cont.resume(Unit) }
                .addOnFailureListener { e ->
                    Log.e("CloudRepo", "saveInvoice failed: ${e.message}")
                    if (!cont.isCompleted) cont.resumeWith(Result.failure(e))
                }
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
                            jumlahTv = (d["jumlahTv"] as? Number)?.toInt() ?: 0,
                            mode = d["mode"] as? String ?: "",
                            baseMaxTv = (d["baseMaxTv"] as? Number)?.toInt() ?: 0,
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

    suspend fun saveFcmToken(username: String, token: String) {
        if (username.isBlank() || token.isBlank()) return
        try {
            firestore.collection("billingps_users").document(username)
                .set(mapOf("fcmToken" to token), com.google.firebase.firestore.SetOptions.merge())
        } catch (e: Exception) { Log.e("CloudRepo", "saveFcmToken: ${e.message}") }
    }

    fun listenGlobalSettings(onUpdate: (PromoSettings) -> Unit): ListenerRegistration? {
        return firestore.document("settings/global")
            .addSnapshotListener { snap, error ->
                if (error != null || snap == null || !snap.exists()) return@addSnapshotListener
                val promoAktif = snap.getBoolean("promoAktif") ?: false
                val rawDiskon = snap.get("diskonPerPaket") as? Map<*, *>
                val diskonPerPaket = mutableMapOf<String, Int>()
                rawDiskon?.forEach { (k, v) ->
                    if (k is String && v is Number) diskonPerPaket[k] = v.toInt()
                }
                val rawOverride = snap.get("addTvOverride") as? Map<*, *>
                val addTvOverride = mutableMapOf<String, Int>()
                rawOverride?.forEach { (k, v) ->
                    if (k is String && v is Number) addTvOverride[k] = v.toInt()
                }
                val newUserPromoActive = snap.getBoolean("newUserPromoActive") ?: false
                val newUserDiscountPercent = (snap.get("newUserDiscountPercent") as? Number)?.toInt() ?: 30
                val newUserPromoDurationHours = (snap.get("newUserPromoDurationHours") as? Number)?.toInt() ?: 96
                val rawNewDiskon = snap.get("newUserDiskonPerPaket") as? Map<*, *>
                val newUserDiskonPerPaket = mutableMapOf<String, Int>()
                rawNewDiskon?.forEach { (k, v) -> if (k is String && v is Number) newUserDiskonPerPaket[k] = v.toInt() }
                val promoStartedAt = snap.getLong("promoStartedAt") ?: 0L
                val promoDurationHours = (snap.get("promoDurationHours") as? Number)?.toInt() ?: 0
                val updatedBy = snap.getString("updatedBy") ?: ""
                val updatedAt = snap.getLong("updatedAt") ?: 0L
                onUpdate(PromoSettings(
                    promoAktif = promoAktif,
                    diskonPerPaket = diskonPerPaket,
                    addTvOverride = addTvOverride,
                    newUserPromoActive = newUserPromoActive,
                    newUserDiscountPercent = newUserDiscountPercent,
                    newUserPromoDurationHours = newUserPromoDurationHours,
                    newUserDiskonPerPaket = newUserDiskonPerPaket,
                    promoStartedAt = promoStartedAt,
                    promoDurationHours = promoDurationHours,
                    updatedBy = updatedBy,
                    updatedAt = updatedAt,
                ))
            }
    }

    suspend fun getActiveLicensesForUser(username: String): List<LicenseRecord> {
        if (username.isBlank() || !ensureSignedIn()) return emptyList()
        return suspendCancellableCoroutine { cont ->
            firestore.collection("licenses")
                .whereEqualTo("username", username)
                .whereEqualTo("revoked", false)
                .get()
                .addOnSuccessListener { snap ->
                    val list = snap.documents.mapNotNull { doc ->
                        val d = doc.data ?: return@mapNotNull null
                        val rawDevices = d["activatedDevices"] as? List<Map<String, Any>>
                        val activatedDevices = rawDevices?.mapNotNull { dev ->
                            val dt = dev["deviceType"] as? String ?: return@mapNotNull null
                            val di = dev["deviceId"] as? String ?: ""
                            val da = (dev["activatedAt"] as? Number)?.toLong() ?: 0L
                            ActivatedDevice(deviceType = dt, deviceId = di, activatedAt = da)
                        } ?: emptyList()
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
                            activatedDeviceId = d["activatedDeviceId"] as? String ?: "",
                            revoked = d["revoked"] as? Boolean ?: false,
                            maxActivations = (d["maxActivations"] as? Number)?.toInt() ?: 2,
                            activatedDevices = activatedDevices,
                        )
                    }
                    val active = list.filter { it.activatedAt > 0 }
                    Log.i("CloudRepo", "getActiveLicensesForUser: ${active.size} active for $username")
                    cont.resume(active)
                }
                .addOnFailureListener { ex ->
                    Log.e("CloudRepo", "getActiveLicensesForUser failed: ${ex.message}")
                    cont.resume(emptyList())
                }
        }
    }

    suspend fun revokeLicense(docId: String): Boolean {
        if (!ensureSignedIn()) return false
        return suspendCancellableCoroutine { cont ->
            firestore.collection("licenses").document(docId)
                .update("revoked", true)
                .addOnSuccessListener { cont.resume(true) }
                .addOnFailureListener { cont.resume(false) }
        }
    }

    suspend fun saveTrialToCloud(username: String, trialBatas: Long) {
        if (username.isBlank()) return
        try {
            firestore.collection("billingps_users").document(username)
                .set(mapOf("trialBatas" to trialBatas), com.google.firebase.firestore.SetOptions.merge())
        } catch (e: Exception) { Log.e("CloudRepo", "saveTrialToCloud: ${e.message}") }
    }

    suspend fun loadTrialFromCloud(username: String): Long {
        if (username.isBlank()) return 0L
        return try {
            suspendCancellableCoroutine { cont ->
                firestore.collection("billingps_users").document(username).get()
                    .addOnSuccessListener { snap ->
                        val result = if (snap.exists()) snap.getLong("trialBatas") ?: 0L else 0L
                        cont.resume(result)
                    }
                    .addOnFailureListener { cont.resume(0L) }
            }
        } catch (e: Exception) { Log.e("CloudRepo", "loadTrialFromCloud: ${e.message}"); 0L }
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
                if (status == "revoked") {
                    onUpdate(LicenseStatus(status = "revoked", pesan = "❌ Lisensi dicabut oleh admin"))
                    return@addSnapshotListener
                }
                if (status != "active") { onUpdate(null); return@addSnapshotListener }
                val expiry = ls["expiresAt"] as? String ?: ""
                val storedMaxTv = (ls["maxTv"] as? Number)?.toInt() ?: 0
                val pesan = ls["pesan"] as? String ?: "✅ Lisensi aktif hingga $expiry"
                val promoAddTv = (ls["promoAddTv"] as? Number)?.toInt() ?: 0
                val paket = ls["paket"] as? String ?: ""
                val maxTv = when {
                    promoAddTv > 0 -> promoAddTv
                    storedMaxTv > 0 -> storedMaxTv
                    pesan.contains("LIFETIME") -> 0
                    pesan.contains("TAHUNAN") || pesan.contains("1 Tahun") -> 15
                    pesan.contains("3BULAN") || pesan.contains("3 Bulan") -> 8
                    pesan.contains("BULANAN") || pesan.contains("1 Bulan") -> 5
                    else -> 2
                }
                Log.i("CloudRepo", "listenUserLicense: license updated for $username, expiry=$expiry")
                onUpdate(LicenseStatus(
                    status = "active",
                    pesan = pesan,
                    expiresAt = expiry,
                    maxTv = maxTv,
                    promoAddTv = promoAddTv,
                    paket = paket,
                    clientEnabled = (ls["clientEnabled"] as? Boolean)
                        ?: LicenseCatalog.clientEnabled(paket),
                ))
            }
    }

    // ── One-time fetch promo ─────────────────────────────────
    suspend fun fetchPromoSettings(): PromoSettings? {
        if (!ensureSignedIn()) return null
        return suspendCancellableCoroutine { cont ->
            firestore.document("settings/global").get()
                .addOnSuccessListener { snap ->
                    if (!snap.exists()) { cont.resume(null); return@addOnSuccessListener }
                    val promoAktif = snap.getBoolean("promoAktif") ?: false
                    val rawDiskon = snap.get("diskonPerPaket") as? Map<*, *>
                    val diskonPerPaket = mutableMapOf<String, Int>()
                    rawDiskon?.forEach { (k, v) -> if (k is String && v is Number) diskonPerPaket[k] = v.toInt() }
                    val rawOverride = snap.get("addTvOverride") as? Map<*, *>
                    val addTvOverride = mutableMapOf<String, Int>()
                    rawOverride?.forEach { (k, v) -> if (k is String && v is Number) addTvOverride[k] = v.toInt() }
                    val newUserPromoActive = snap.getBoolean("newUserPromoActive") ?: false
                    val newUserDiscountPercent = (snap.get("newUserDiscountPercent") as? Number)?.toInt() ?: 30
                    val newUserPromoDurationHours = (snap.get("newUserPromoDurationHours") as? Number)?.toInt() ?: 96
                    val rawNewDiskon = snap.get("newUserDiskonPerPaket") as? Map<*, *>
                    val newUserDiskonPerPaket = mutableMapOf<String, Int>()
                    rawNewDiskon?.forEach { (k, v) -> if (k is String && v is Number) newUserDiskonPerPaket[k] = v.toInt() }
                    cont.resume(PromoSettings(
                        promoAktif = promoAktif,
                        diskonPerPaket = diskonPerPaket,
                        addTvOverride = addTvOverride,
                        updatedBy = snap.getString("updatedBy") ?: "",
                        updatedAt = snap.getLong("updatedAt") ?: 0L,
                        newUserPromoActive = newUserPromoActive,
                        newUserDiscountPercent = newUserDiscountPercent,
                        newUserPromoDurationHours = newUserPromoDurationHours,
                        newUserDiskonPerPaket = newUserDiskonPerPaket,
                        promoStartedAt = snap.getLong("promoStartedAt") ?: 0L,
                        promoDurationHours = (snap.get("promoDurationHours") as? Number)?.toInt() ?: 0,
                    ))
                }
                .addOnFailureListener { Log.e("CloudRepo", "fetchPromoSettings failed: ${it.message}"); cont.resume(null) }
        }
    }

    // ── Listen notifications for user ────────────────────────
    fun listenUserNotifications(username: String, onUpdate: (List<AppNotification>) -> Unit): ListenerRegistration? {
        if (username.isBlank()) return null
        if (auth.currentUser == null) return null
        return firestore.collection("notifications")
            .whereEqualTo("username", username)
            .addSnapshotListener { snap, error ->
                if (error != null) { Log.e("CloudRepo", "listenNotifications error: ${error.message}"); return@addSnapshotListener }
                if (snap == null) return@addSnapshotListener
                val list = snap.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    AppNotification(
                        id = doc.id,
                        title = d["title"] as? String ?: "",
                        body = d["body"] as? String ?: "",
                        type = d["type"] as? String ?: "info",
                        sentAt = d["sentAt"] as? Long ?: 0L,
                    )
                }
                onUpdate(list.sortedByDescending { it.sentAt }.take(50))
            }
    }

    fun listenBroadcastNotifications(onUpdate: (List<AppNotification>) -> Unit): ListenerRegistration? {
        if (auth.currentUser == null) return null
        return firestore.collection("notifications")
            .whereEqualTo("username", "__all__")
            .addSnapshotListener { snap, error ->
                if (error != null) { Log.e("CloudRepo", "listenBroadcast error: ${error.message}"); return@addSnapshotListener }
                if (snap == null) return@addSnapshotListener
                val list = snap.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    AppNotification(
                        id = doc.id,
                        title = d["title"] as? String ?: "",
                        body = d["body"] as? String ?: "",
                        type = d["type"] as? String ?: "info",
                        sentAt = d["sentAt"] as? Long ?: 0L,
                    )
                }
                onUpdate(list.sortedByDescending { it.sentAt }.take(50))
            }
    }

    fun listenClientStatus(username: String, tvId: String, onUpdate: (ClientStatus) -> Unit): ListenerRegistration? {
        if (username.isBlank() || tvId.isBlank()) return null
        return firestore
            .collection("billingps_users").document(username)
            .collection("tvCommands").document(tvId)
            .addSnapshotListener { snap, error ->
                if (error != null) { Log.e("CloudRepo", "listenClientStatus error: ${error.message}"); return@addSnapshotListener }
                if (snap == null) return@addSnapshotListener
                val exists = snap.exists()
                val online = snap.getBoolean("clientOnline") ?: false
                val version = snap.getString("clientVersion") ?: ""
                val lastSeen = snap.getTimestamp("lastSeen")?.toDate()?.time ?: 0L
                val allFields = snap.data?.keys?.joinToString(",") ?: "none"
                Log.i("CloudRepo", "listenClientStatus: tvId=$tvId exists=$exists online=$online version=$version lastSeen=$lastSeen fields=[$allFields]")
                onUpdate(ClientStatus(online, version, lastSeen))
            }
    }

    suspend fun deleteTvCommandDoc(username: String, tvId: String): Boolean {
        if (username.isBlank() || tvId.isBlank()) return false
        if (!ensureSignedIn()) return false
        return suspendCancellableCoroutine { cont ->
            firestore
                .collection("billingps_users").document(username)
                .collection("tvCommands").document(tvId)
                .delete()
                .addOnSuccessListener {
                    Log.i("CloudRepo", "deleteTvCommandDoc success: $username/$tvId")
                    if (!cont.isCompleted) cont.resume(true)
                }
                .addOnFailureListener { ex ->
                    Log.i("CloudRepo", "deleteTvCommandDoc failed: ${ex.message}")
                    if (!cont.isCompleted) cont.resume(false)
                }
        }
    }

    fun sendPlayVideo(username: String, tvId: String, videoUrl: String) {
        if (username.isBlank() || tvId.isBlank() || videoUrl.isBlank()) return
        firestore
            .collection("billingps_users").document(username)
            .collection("tvCommands").document(tvId)
            .set(
                mapOf(
                    "playVideo" to true,
                    "videoUrl" to videoUrl,
                    "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                ),
                com.google.firebase.firestore.SetOptions.merge(),
            )
    }

    fun sendCacheMedia(username: String, tvId: String, mediaUrl: String) {
        if (username.isBlank() || tvId.isBlank() || mediaUrl.isBlank()) return
        firestore
            .collection("billingps_users").document(username)
            .collection("tvCommands").document(tvId)
            .set(
                mapOf(
                    "cacheMedia" to true,
                    "mediaUrl" to mediaUrl,
                    "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                ),
                com.google.firebase.firestore.SetOptions.merge(),
            )
    }

    fun sendWarningImage(username: String, tvId: String, imageUrl: String) {
        if (username.isBlank() || tvId.isBlank() || imageUrl.isBlank()) return
        firestore
            .collection("billingps_users").document(username)
            .collection("tvCommands").document(tvId)
            .set(
                mapOf(
                    "warningImageUrl" to imageUrl,
                ),
                com.google.firebase.firestore.SetOptions.merge(),
            )
    }

    fun sendWarningVideo(username: String, tvId: String, videoUrl: String) {
        if (username.isBlank() || tvId.isBlank() || videoUrl.isBlank()) return
        firestore
            .collection("billingps_users").document(username)
            .collection("tvCommands").document(tvId)
            .set(
                mapOf(
                    "warningVideoUrl" to videoUrl,
                ),
                com.google.firebase.firestore.SetOptions.merge(),
            )
    }

    fun sendHdmiPort(username: String, tvId: String, hdmiPort: Int) {
        if (username.isBlank() || tvId.isBlank() || hdmiPort <= 0) return
        firestore
            .collection("billingps_users").document(username)
            .collection("tvCommands").document(tvId)
            .set(
                mapOf(
                    "hdmiPort" to hdmiPort.toLong(),
                ),
                com.google.firebase.firestore.SetOptions.merge(),
            )
    }

    fun sendPlayCachedMedia(username: String, tvId: String) {
        if (username.isBlank() || tvId.isBlank()) return
        firestore
            .collection("billingps_users").document(username)
            .collection("tvCommands").document(tvId)
            .set(
                mapOf(
                    "playCachedMedia" to true,
                    "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                ),
                com.google.firebase.firestore.SetOptions.merge(),
            )
    }

    fun sendWarning(username: String, tvId: String, message: String) {
        if (username.isBlank() || tvId.isBlank()) return
        firestore
            .collection("billingps_users").document(username)
            .collection("tvCommands").document(tvId)
            .set(
                mapOf(
                    "showWarning" to true,
                    "warningMessage" to message,
                    "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                ),
                com.google.firebase.firestore.SetOptions.merge(),
            )
    }

    // ── Save license request notification (for admin) ──────
    fun saveLicenseRequestNotification(username: String, paket: String, harga: Int, invoiceId: String) {
        if (!ensureSignedInSync()) return
        try {
            val data = HashMap<String, Any>()
            data["type"] = "license_request"
            data["username"] = "rrbilling"
            data["user"] = username
            data["paket"] = paket
            data["harga"] = harga
            data["invoiceId"] = invoiceId
            data["title"] = "Request Lisensi Baru"
            data["body"] = "User $username memesan paket $paket"
            data["sentAt"] = System.currentTimeMillis()
            data["read"] = false
            firestore.collection("notifications").add(data)
            Log.i("CloudRepo", "saveLicenseRequestNotification: $username - $paket")
        } catch (e: Exception) {
            Log.e("CloudRepo", "saveLicenseRequestNotification error: ${e.message}")
        }
    }

    private fun ensureSignedInSync(): Boolean {
        return auth.currentUser != null
    }
}
