package com.billingps.licensegen

import android.app.Application
import android.util.Base64
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec

private const val SUPER_ADMIN_USER = "rrbilling"
private const val SUPER_ADMIN_PASS = "@rrcctv5555"
private var cachedAccessToken: String? = null
private var tokenExpiresAt: Long = 0L

class LicenseGenViewModel(application: Application) : AndroidViewModel(application) {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private var usersListener: ListenerRegistration? = null
    private var invoicesListener: ListenerRegistration? = null
    private var promoListener: ListenerRegistration? = null

    var isLoggedIn by mutableStateOf(false); private set
    var currentUser by mutableStateOf(""); private set
    var isBusy by mutableStateOf(false); private set
    var userList by mutableStateOf(listOf<FirestoreUser>()); private set
    var invoiceList by mutableStateOf(listOf<Invoice>()); private set
    var firestoreReady by mutableStateOf(false); private set
    var promo by mutableStateOf(PromoSettings()); private set

    init {
        ECDSAUtils.init(application)
        ensureAnonymous()
    }

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

    fun setPromo(promoAktif: Boolean, diskonPerPaket: Map<String, Int>, addTvOverride: Map<String, Int>, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val data = HashMap<String, Any>()
                data["promoAktif"] = promoAktif
                data["diskonPerPaket"] = diskonPerPaket.mapValues { it.value.coerceIn(0, 100) }
                data["addTvOverride"] = addTvOverride
                data["updatedBy"] = currentUser
                data["updatedAt"] = System.currentTimeMillis()
                firestore.document("settings/global").set(data, SetOptions.merge()).await()
                promo = PromoSettings(
                    promoAktif = promoAktif,
                    diskonPerPaket = diskonPerPaket.mapValues { it.value.coerceIn(0, 100) },
                    addTvOverride = addTvOverride,
                    updatedBy = currentUser,
                    updatedAt = System.currentTimeMillis(),
                )
                onResult(true, "Promo tersimpan!")
            } catch (e: Exception) {
                onResult(false, "Gagal: ${e.message}")
            }
        }
    }

    fun setNewUserPromo(active: Boolean, discountPercent: Int, durationHours: Int, diskonPerPaket: Map<String, Int>, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val data = HashMap<String, Any>()
                data["newUserPromoActive"] = active
                data["newUserDiscountPercent"] = discountPercent.coerceIn(0, 100)
                data["newUserPromoDurationHours"] = durationHours.coerceAtLeast(1)
                data["newUserDiskonPerPaket"] = diskonPerPaket.mapValues { it.value.coerceIn(0, 100) }
                data["updatedBy"] = currentUser
                data["updatedAt"] = System.currentTimeMillis()
                firestore.document("settings/global").set(data, SetOptions.merge()).await()
                promo = promo.copy(
                    newUserPromoActive = active,
                    newUserDiscountPercent = discountPercent.coerceIn(0, 100),
                    newUserPromoDurationHours = durationHours.coerceAtLeast(1),
                    newUserDiskonPerPaket = diskonPerPaket.mapValues { it.value.coerceIn(0, 100) },
                    updatedBy = currentUser,
                    updatedAt = System.currentTimeMillis(),
                )
                onResult(true, "Promo user baru tersimpan!")
            } catch (e: Exception) {
                onResult(false, "Gagal: ${e.message}")
            }
        }
    }

    // ── Private Key Management ───────────────────────────────
    fun hasPrivateKey(): Boolean = ECDSAUtils.hasPrivateKey()

    fun getPrivateKey(): String = ECDSAUtils.getPrivateKey(getApplication()) ?: ""

    fun setPrivateKey(b64: String) {
        ECDSAUtils.setPrivateKey(getApplication(), b64)
    }

    fun generateNewKeyPair(onDone: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val kpg = java.security.KeyPairGenerator.getInstance("EC")
                kpg.initialize(256)
                val kp = kpg.generateKeyPair()
                val b64 = android.util.Base64.encodeToString(kp.private.encoded, android.util.Base64.DEFAULT)
                setPrivateKey(b64)
                withContext(Dispatchers.Main) { onDone(b64) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onDone("ERROR: ${e.message}") }
            }
        }
    }

    fun signOut() {
        isLoggedIn = false
        currentUser = ""
        usersListener?.remove()
        usersListener = null
        invoicesListener?.remove()
        invoicesListener = null
        promoListener?.remove()
        promoListener = null
        userList = emptyList()
        invoiceList = emptyList()
        promo = PromoSettings()
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

        // Listen promo settings
        promoListener?.remove()
        promoListener = firestore.document("settings/global")
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
                rawNewDiskon?.forEach { (k, v) ->
                    if (k is String && v is Number) newUserDiskonPerPaket[k] = v.toInt()
                }
                promo = PromoSettings(
                    promoAktif = promoAktif,
                    diskonPerPaket = diskonPerPaket,
                    addTvOverride = addTvOverride,
                    updatedBy = snap.getString("updatedBy") ?: "",
                    updatedAt = snap.getLong("updatedAt") ?: 0L,
                    newUserPromoActive = newUserPromoActive,
                    newUserDiscountPercent = newUserDiscountPercent,
                    newUserPromoDurationHours = newUserPromoDurationHours,
                    newUserDiskonPerPaket = newUserDiskonPerPaket,
                )
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
        if (!ECDSAUtils.hasPrivateKey()) {
            onDone(false, "Private key belum dikonfigurasi! Buka tab Settings.")
            return
        }
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
                val promoKey = when (invoice.paket) {
                    "1 Bulan", "BULANAN" -> "1 Bulan"; "3 Bulan", "3BULAN" -> "3 Bulan"
                    "1 Tahun", "TAHUNAN" -> "1 Tahun"; "LIFETIME" -> "LIFETIME"; else -> null
                }
                val tvOverride = if (promo.promoAktif && promoKey != null) promo.addTvOverride[promoKey] else null
                val maxTv = tvOverride ?: when (invoice.paket) {
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

                // Auto-send push notification
                val notifTitle = "🎉 Lisensi Aktif!"
                val notifBody = "${invoice.paket} aktif hingga $expiry"
                sendFcmToUser(invoice.username, notifTitle, notifBody)

                // Save notification history
                try {
                    val notifData = HashMap<String, Any>()
                    notifData["username"] = invoice.username
                    notifData["title"] = notifTitle
                    notifData["body"] = notifBody
                    notifData["type"] = "license_confirmed"
                    notifData["sentAt"] = System.currentTimeMillis()
                    firestore.collection("notifications").add(notifData)
                } catch (_: Exception) { }

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
        if (!ECDSAUtils.hasPrivateKey()) {
            onDone("ERROR: Private key belum dikonfigurasi! Buka tab Settings.")
            return
        }
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
        val signature = try { ECDSAUtils.sign(payload) } catch (e: Exception) { onDone("ERROR: Signing gagal: ${e.message}"); return }

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

    fun sendPopupToUser(username: String, title: String, body: String) {
        viewModelScope.launch {
            try {
                val token = suspendCancellableCoroutine<String?> { cont ->
                    firestore.collection("billingps_users").document(username).get()
                        .addOnSuccessListener { cont.resume(it.getString("fcmToken")) }
                        .addOnFailureListener { cont.resume(null) }
                }
                if (!token.isNullOrBlank()) {
                    sendFcmV1(token, title, body, "admin")
                }
                // Save to Firestore with type=admin for inbox + popup trigger
                saveNotifToFirestore(username, title, body)
            } catch (e: Exception) {
                Log.e("FCM", "sendPopupToUser exception: ${e.message}")
            }
        }
    }

    fun sendPopupToAllUsers(title: String, body: String, onDone: ((Int) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val allUsers = suspendCancellableCoroutine<MutableList<Pair<String, String>>> { cont ->
                    firestore.collection("billingps_users").get()
                        .addOnSuccessListener { snap ->
                            val list = mutableListOf<Pair<String, String>>()
                            for (doc in snap.documents) {
                                val token = doc.getString("fcmToken")
                                val username = doc.id
                                if (!token.isNullOrBlank()) list.add(username to token)
                            }
                            cont.resume(list)
                        }
                        .addOnFailureListener { cont.resume(mutableListOf()) }
                }
                var sent = 0
                for ((_, token) in allUsers) {
                    sendFcmV1(token, title, body, "admin")
                    sent++
                }
                // Broadcast notification to all users
                val notifData = HashMap<String, Any>()
                notifData["username"] = "__all__"
                notifData["title"] = title
                notifData["body"] = body
                notifData["type"] = "admin"
                notifData["sentAt"] = System.currentTimeMillis()
                try { firestore.collection("notifications").add(notifData) } catch (_: Exception) {}
                withContext(Dispatchers.Main) { onDone?.invoke(sent) }
            } catch (_: Exception) { withContext(Dispatchers.Main) { onDone?.invoke(0) } }
        }
    }

    fun sendFcmToUser(username: String, title: String, body: String) {
        viewModelScope.launch {
            try {
                val token = suspendCancellableCoroutine<String?> { cont ->
                    firestore.collection("billingps_users").document(username).get()
                        .addOnSuccessListener { cont.resume(it.getString("fcmToken")) }
                        .addOnFailureListener { cont.resume(null) }
                }
                if (!token.isNullOrBlank()) {
                    sendFcmV1(token, title, body)
                }
                // Save to Firestore notifications collection
                saveNotifToFirestore(username, title, body)
            } catch (e: Exception) {
                Log.e("FCM", "sendFcmToUser exception: ${e.message}")
            }
        }
    }

    fun sendFcmToAllUsers(title: String, body: String, onDone: ((Int) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val allUsers = suspendCancellableCoroutine<MutableList<Pair<String, String>>> { cont ->
                    firestore.collection("billingps_users").get()
                        .addOnSuccessListener { snap ->
                            val list = mutableListOf<Pair<String, String>>()
                            for (doc in snap.documents) {
                                val token = doc.getString("fcmToken")
                                val username = doc.id
                                if (!token.isNullOrBlank()) list.add(username to token)
                            }
                            cont.resume(list)
                        }
                        .addOnFailureListener { cont.resume(mutableListOf()) }
                }
                var sent = 0
                for ((username, token) in allUsers) {
                    sendFcmV1(token, title, body)
                    sent++
                }
                // Save to Firestore — one doc per user or a single broadcast doc
                val notifData = HashMap<String, Any>()
                notifData["username"] = "__all__"
                notifData["title"] = title
                notifData["body"] = body
                notifData["type"] = "admin"
                notifData["sentAt"] = System.currentTimeMillis()
                try {
                    firestore.collection("notifications").add(notifData)
                } catch (_: Exception) {}
                withContext(Dispatchers.Main) { onDone?.invoke(sent) }
            } catch (_: Exception) { withContext(Dispatchers.Main) { onDone?.invoke(0) } }
        }
    }

    private fun saveNotifToFirestore(username: String, title: String, body: String) {
        try {
            val notifData = HashMap<String, Any>()
            notifData["username"] = username
            notifData["title"] = title
            notifData["body"] = body
            notifData["type"] = "admin"
            notifData["sentAt"] = System.currentTimeMillis()
            firestore.collection("notifications").add(notifData)
        } catch (_: Exception) {}
    }

    private fun sendFcmV1(deviceToken: String, title: String, body: String, type: String = "info") {
        try {
            val accessToken = getAccessToken() ?: return
            val url = URL("https://fcm.googleapis.com/v1/projects/rrbillingpro/messages:send")
            val conn = url.openConnection() as HttpURLConnection
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $accessToken")
            val esc = { s: String -> s.replace("\"", "\\\"").replace("\n", "\\n") }
            val escapedTitle = esc(title)
            val escapedBody = esc(body)
            val escapedType = esc(type)
            val json = """{"message":{"token":"$deviceToken","notification":{"title":"$escapedTitle","body":"$escapedBody"},"data":{"title":"$escapedTitle","body":"$escapedBody","type":"$escapedType"}}}"""
            conn.outputStream.write(json.toByteArray(Charsets.UTF_8))
            val code = conn.responseCode
            if (code != 200) {
                val err = conn.errorStream?.bufferedReader()?.readText() ?: ""
                Log.e("FCM", "sendFcmV1 failed: HTTP $code — $err")
            } else {
                Log.i("FCM", "sendFcmV1 success for token=${deviceToken.take(20)}...")
            }
            conn.disconnect()
        } catch (e: Exception) {
            Log.e("FCM", "sendFcmV1 exception: ${e.message}")
        }
    }

    private fun getAccessToken(): String? {
        val now = System.currentTimeMillis() / 1000
        if (cachedAccessToken != null && now < tokenExpiresAt - 60) return cachedAccessToken
        try {
            val ctx = getApplication<Application>()
            val inputStream = ctx.resources.openRawResource(com.billingps.licensegen.R.raw.fcm_key)
            val jsonStr = inputStream.bufferedReader().use { it.readText() }
            val json = org.json.JSONObject(jsonStr)
            val clientEmail = json.getString("client_email")
            val privateKeyPem = json.getString("private_key")

            // Parse PEM private key
            val pemStr = privateKeyPem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("\n", "").replace("\r", "")
            val keyBytes = Base64.decode(pemStr, Base64.DEFAULT)
            val keySpec = PKCS8EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance("RSA")
            val privateKey = keyFactory.generatePrivate(keySpec)

            // Build JWT
            val header = org.json.JSONObject().apply {
                put("alg", "RS256")
                put("typ", "JWT")
            }
            val claim = org.json.JSONObject().apply {
                put("iss", clientEmail)
                put("scope", "https://www.googleapis.com/auth/firebase.messaging")
                put("aud", "https://oauth2.googleapis.com/token")
                put("exp", now + 3600)
                put("iat", now)
            }

            val b64Header = Base64.encodeToString(header.toString().toByteArray(), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            val b64Claim = Base64.encodeToString(claim.toString().toByteArray(), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            val toSign = "$b64Header.$b64Claim"

            val sig = Signature.getInstance("SHA256withRSA")
            sig.initSign(privateKey)
            sig.update(toSign.toByteArray())
            val signature = sig.sign()
            val b64Sig = Base64.encodeToString(signature, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

            val jwt = "$toSign.$b64Sig"

            // Exchange JWT for access token
            val tokenUrl = URL("https://oauth2.googleapis.com/token")
            val tokenConn = tokenUrl.openConnection() as HttpURLConnection
            tokenConn.doOutput = true
            tokenConn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            val body = "grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=$jwt"
            tokenConn.outputStream.write(body.toByteArray(Charsets.UTF_8))
            val resp = tokenConn.inputStream.bufferedReader().use { it.readText() }
            tokenConn.disconnect()

            val respJson = org.json.JSONObject(resp)
            val token = respJson.getString("access_token")
            val expiresIn = respJson.optInt("expires_in", 3600)
            cachedAccessToken = token
            tokenExpiresAt = now + expiresIn
            return token
        } catch (e: Exception) {
            Log.e("FCM", "getAccessToken failed: ${e.message}")
            return null
        }
    }

    override fun onCleared() {
        usersListener?.remove()
        invoicesListener?.remove()
        promoListener?.remove()
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
