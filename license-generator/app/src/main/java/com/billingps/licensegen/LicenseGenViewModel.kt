package com.billingps.licensegen

import android.app.Application
import android.util.Base64
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import org.json.JSONArray
import org.json.JSONObject

private const val SUPER_ADMIN_USER = "rrbilling"
private const val SUPER_ADMIN_USER_2 = "rrlisensi"
private const val SUPER_ADMIN_PASS = "@rrcctv5555"
private const val DEFAULT_RRTV_WORKER_URL = "https://rr-billing-worker.dedekemoking.workers.dev"
private val ADMIN_USERS = mapOf(
    SUPER_ADMIN_USER to SUPER_ADMIN_PASS,
    SUPER_ADMIN_USER_2 to SUPER_ADMIN_PASS,
)
private var cachedAccessToken: String? = null
private var tokenExpiresAt: Long = 0L

class LicenseGenViewModel(application: Application) : AndroidViewModel(application) {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private var usersListener: ListenerRegistration? = null
    private var invoicesListener: ListenerRegistration? = null
    private var promoListener: ListenerRegistration? = null
    private var licenseRequestListener: ListenerRegistration? = null
    private var historyListener: ListenerRegistration? = null

    private var supabaseInvoices: List<Invoice> = emptyList()
    private var firestoreInvoices: List<Invoice> = emptyList()
    private var supabaseRequests: List<LicenseRequest> = emptyList()
    private var firestoreRequests: List<LicenseRequest> = emptyList()

    var isLoggedIn by mutableStateOf(false); private set
    var currentUser by mutableStateOf(""); private set
    var isBusy by mutableStateOf(false); private set
    var userList by mutableStateOf(listOf<FirestoreUser>()); private set
    var invoiceList by mutableStateOf(listOf<Invoice>()); private set
    var firestoreReady by mutableStateOf(false); private set
    var promo by mutableStateOf(PromoSettings()); private set
    var historyList by mutableStateOf(listOf<LicenseRecord>()); private set

    data class LicenseRequest(
        val id: String = "",
        val user: String = "",
        val paket: String = "",
        val harga: Int = 0,
        val invoiceId: String = "",
        val title: String = "",
        val body: String = "",
        val sentAt: Long = 0L,
        val read: Boolean = false,
    )
    var licenseRequests by mutableStateOf(listOf<LicenseRequest>()); private set
    var pendingRequestCount by mutableStateOf(0); private set

    // Status lisensi ONLINE per username (tabel license_status).
    // Ini sumber kebenaran yang dipoll desktop tiap 60 detik, jadi revoke
    // hanya benar-benar mengunci aplikasi kalau baris di tabel ini berubah.
    // Penanda `revoked` pada baris licenses tidak cukup.
    var onlineLicenseStatus by mutableStateOf(mapOf<String, String>()); private set

    // ── Supabase REST helpers ──────────────────────────────────────────────
    private fun supabaseFetch(path: String): String? {    
        return try {
            val url = URL("$SUPABASE_URL/rest/v1/$path")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 15000; conn.readTimeout = 15000
            conn.setRequestProperty("apikey", SUPABASE_KEY)
            conn.setRequestProperty("Authorization", "Bearer $SUPABASE_KEY")
            val code = conn.responseCode
            if (code in 200..299) conn.inputStream.bufferedReader().readText() else null
        } catch (ex: Exception) { Log.w("LicenseGen", "supabaseFetch $path: ${ex.message}"); null }
    }

    private var lastSupabaseError = ""
    // Kolom yang diketahui tidak ada di database, DISIMPAN PER TABEL.
    // PostgREST menolak SELURUH request dengan PGRST204 kalau satu saja kolom
    // tak dikenal, jadi tanpa penanganan ini satu kolom baru bisa mematikan
    // seluruh generate lisensi (terjadi nyata: licenses.maxTv belum ada ->
    // konfirmasi invoice gagal). Cache ini tidak boleh global: `maxTv` tidak
    // ada di tabel `licenses`, tetapi ADA di `license_status`; cache global
    // pernah membuang maxTv dari upsert license_status sehingga kuota user
    // tertulis 0 walau sudah dibayar.
    private val kolomTidakAda = mutableSetOf<String>()

    private fun cacheKey(table: String, kolom: String) = "$table.$kolom"

    /**
     * Kirim request, buang kolom yang tidak dikenal, lalu coba lagi.
     * Maksimal 6 percobaan karena tiap percobaan hanya menghapus satu kolom.
     */
    private fun reqToleranKolom(
        table: String, path: String, method: String, body: JSONObject?,
        prefer: String? = null
    ): Boolean {
        for (percobaan in 1..6) {
            val kirim = if (body == null) null else {
                JSONObject(body.toString()).also { c ->
                    kolomTidakAda
                        .filter { it.startsWith("$table.") }
                        .forEach { c.remove(it.substringAfter('.')) }
                }
            }
            if (supabaseRequest(path, method, kirim?.toString(), prefer)) return true
            val kolom = _kolomAsing(lastSupabaseError)
            if (kolom == null) return false
            Log.w("LicenseGen", "Kolom $table.$kolom belum ada di database -- dilewati. " +
                "Jalankan supabase_harga_setup.sql agar field ini tersimpan.")
            kolomTidakAda.add(cacheKey(table, kolom))
        }
        return false
    }

    private fun _kolomAsing(err: String): String? {
        if (!err.contains("PGRST204")) return null
        val m = Regex("Could not find the '([^']+)'").find(err) ?: return null
        return m.groupValues[1]
    }

    /**
     * Perbarui satu baris berdasarkan primary key.
     *
     * PENTING: jangan pakai PATCH. Android HttpURLConnection tidak mendukung
     * PATCH, dan|workaround X-HTTP-Method-Override TIDAK dihormati PostgREST --
     * request-nya diperlakukan sebagai INSERT sehingga gagal dengan
     * "null value in column id". Konfirmasi invoice karena itu tidak pernah
     * mengubah status invoice sama sekali.
     *
     * Diganti POST + resolution=merge-duplicates dengan id di dalam body:
     * PostgREST melakukan upsert pada primary key, jadi baris yang ada di-update
     * dan tidak ada baris ganda.
     */
    private fun updateById(table: String, pk: String, values: JSONObject,
                           prefer: String? = null): Boolean {
        val body = JSONObject(values.toString())
        body.put("id", pk)
        return reqToleranKolom(table, table, "POST", body,
            prefer = prefer ?: "resolution=merge-duplicates,return=minimal")
    }

    private fun supabaseRequest(path: String, method: String, body: String?, prefer: String? = null): Boolean {
        lastSupabaseError = ""
        return try {
            val url = URL("$SUPABASE_URL/rest/v1/$path")
            val conn = url.openConnection() as HttpURLConnection
            // PATCH tidak bisa dipakai: HttpURLConnection Android menolaknya dan
            // X-HTTP-Method-Override tidak dihormati PostgREST. Semua
            // pembaruan baris harus lewat POST upsert (lihat updateById).
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000; conn.readTimeout = 15000
            conn.setRequestProperty("apikey", SUPABASE_KEY)
            conn.setRequestProperty("Authorization", "Bearer $SUPABASE_KEY")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Prefer", prefer ?: "return=minimal")
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.write(body.toByteArray(Charsets.UTF_8))
            }
            val code = conn.responseCode
            if (code in 200..299) {
                true
            } else {
                lastSupabaseError = try { conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $code" }
                    catch (_: Exception) { "HTTP $code" }
                Log.w("LicenseGen", "supabaseRequest $path HTTP $code: $lastSupabaseError")
                false
            }
        } catch (ex: Exception) {
            lastSupabaseError = ex.message ?: "error"
            Log.w("LicenseGen", "supabaseRequest $path: ${ex.message}")
            false
        }
    }

    private val SUPABASE_URL = "https://nqaucjpbnewckedqcezb.supabase.co"
    private val SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im5xYXVjanBibmV3Y2tlZHFjZXpiIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODg1MjUyNzUsImV4cCI6MjEwNDEwMTI3NX0.xi-h-3E2Yww95HzXsNmmiOmdvMKi_TaFNw8Op7xryig"

    private var supabasePollerJob: Job? = null

    private fun startSupabasePolling() {
        supabasePollerJob?.cancel()
        supabasePollerJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                try { pollSupabaseOnce() } catch (ex: Exception) { Log.w("LicenseGen", "pollSupabase: ${ex.message}") }
                delay(5000)
            }
        }
    }

    private suspend fun pollSupabaseOnce() {
        // Invoices
        try {
            val json = supabaseFetch("invoices?select=*&order=dibuat.desc&limit=50")
            if (json != null) {
                val arr = JSONArray(json)
                val items = mutableListOf<Invoice>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    items.add(Invoice(
                        id = o.optString("id", ""), username = o.optString("username", ""),
                        email = o.optString("email", ""), paket = o.optString("paket", ""),
                        harga = o.optInt("harga", 0), status = o.optString("status", "PENDING"),
                        dibuat = o.optLong("dibuat", 0L), dibayar = o.optLong("dibayar", 0L),
                        confirmedBy = o.optString("confirmedBy", ""),
                        kodeLisensi = o.optString("kodeLisensi", ""),
                        buktiBase64 = o.optString("buktiBase64", ""),
                        jumlahTv = o.optInt("jumlah_tv", o.optInt("jumlahTv", 0)),
                        maxTv = o.optInt("maxTv", 0),
                        mode = o.optString("mode", ""),
                        baseMaxTv = o.optInt("baseMaxTv", 0),
                        clientEnabled = if (o.has("clientEnabled")) o.optBoolean("clientEnabled", true)
                            else o.optString("paket", "").uppercase() != "BULANAN",
                        // Field ringkasan (dikirim main.py). Default kosong
                        // supaya client lama yang belum mengirimnya tetap aman.
                        permintaan = o.optString("permintaan", ""),
                        hargaPerTv = o.optInt("hargaPerTv", 0),
                    ))
                }
                withContext(Dispatchers.Main) {
                    supabaseInvoices = items
                    publishInvoices()
                }
            }
        } catch (ex: Exception) { Log.e("LicenseGen", "parse invoices: ${ex.message}") }
        // Status lisensi online (license_status) — sumber kebenaran revoke.
        try {
            val stJson = supabaseFetch("license_status?select=id,status&limit=500")
            if (stJson != null) {
                val arr = JSONArray(stJson)
                val m = mutableMapOf<String, String>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    m[o.optString("id", "")] = o.optString("status", "")
                }
                withContext(Dispatchers.Main) { onlineLicenseStatus = m }
            }
        } catch (ex: Exception) { Log.e("LicenseGen", "parse license_status: ${ex.message}") }
        // License history.
        // Dulu difilter generatedBy=eq.<admin> — itu Means "dibuat oleh admin
        // siapa", bukan "lisensi milik user siapa". Akibatnya kode yang dibuat
        // admin lain tidak pernah muncul, dan revoke tidak bisa dilakukan dari
        // sini. Sekarang ambil seluruh 100 baris terbaru supaya daftar kode
        // milik semua user terlihat.
        try {
            val json = supabaseFetch("licenses?select=*&order=generatedAt.desc&limit=100")
            if (json != null) {
                val arr = JSONArray(json)
                val items = mutableListOf<LicenseRecord>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    items.add(LicenseRecord(
                        id = o.optString("id", ""), kode = o.optString("kode", ""),
                        payload = o.optString("payload", ""), signature = o.optString("signature", ""),
                        paket = o.optString("paket", ""), username = o.optString("username", ""),
                        email = o.optString("email", ""), expiry = o.optString("expiry", ""),
                        generatedBy = o.optString("generatedBy", ""),
                        generatedAt = o.optLong("generatedAt", 0L),
                        activatedAt = o.optLong("activatedAt", 0L),
                        revoked = o.optBoolean("revoked", false),
                    ))
                }
                withContext(Dispatchers.Main) { historyList = items.sortedByDescending { it.generatedAt } }
            }
        } catch (ex: Exception) { Log.e("LicenseGen", "parse history: ${ex.message}") }
        // Promo
        try {
            val json = supabaseFetch("promo?select=*&id=eq.global&limit=1")
            if (json != null) {
                val arr = JSONArray(json)
                if (arr.length() > 0) {
                    val o = arr.getJSONObject(0)
                    withContext(Dispatchers.Main) {
                        promo = PromoSettings(
                            promoAktif = o.optBoolean("promoAktif", false),
                            diskonPerPaket = parseStringIntMap(o.optJSONObject("diskonPerPaket")),
                            addTvOverride = parseStringIntMap(o.optJSONObject("addTvOverride")),
                            updatedBy = o.optString("updatedBy", ""),
                            updatedAt = o.optLong("updatedAt", 0L),
                            newUserPromoActive = o.optBoolean("newUserPromoActive", false),
                            newUserDiscountPercent = o.optInt("newUserDiscountPercent", 30),
                            newUserPromoDurationHours = o.optInt("newUserPromoDurationHours", 96),
                            newUserDiskonPerPaket = parseStringIntMap(o.optJSONObject("newUserDiskonPerPaket")),
                            promoStartedAt = o.optLong("promoStartedAt", 0L),
                            promoDurationHours = o.optInt("promoDurationHours", 0),
                        )
                    }
                }
            }
        } catch (ex: Exception) { Log.e("LicenseGen", "parse promo: ${ex.message}") }
        // Notifications
        try {
            val json = supabaseFetch("notifications?select=*&type=eq.license_request&order=sentAt.desc&limit=50")
            if (json != null) {
                val arr = JSONArray(json)
                val items = mutableListOf<LicenseRequest>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    items.add(LicenseRequest(
                        id = o.optString("id", ""), user = o.optString("user", ""),
                        paket = o.optString("paket", ""), harga = o.optInt("harga", 0),
                        invoiceId = o.optString("invoiceId", ""),
                        title = o.optString("title", ""), body = o.optString("body", ""),
                        sentAt = o.optLong("sentAt", 0L), read = o.optBoolean("read", false),
                    ))
                }
                withContext(Dispatchers.Main) {
                    val previousPending = pendingRequestCount
                    supabaseRequests = items
                    publishRequests()
                    if (pendingRequestCount > previousPending && previousPending >= 0) {
                        val latest = licenseRequests.firstOrNull { !it.read }
                        if (latest != null) NotificationHelper.show("Request Lisensi Baru", "${latest.user} — ${latest.paket} (Rp${String.format("%,d", latest.harga)})")
                    }
                }
            }
        } catch (ex: Exception) { Log.e("LicenseGen", "parse notif: ${ex.message}") }
    }

    private fun publishInvoices() {
        val merged = LinkedHashMap<String, Invoice>()
        for (inv in supabaseInvoices) merged[invoiceKey(inv)] = inv
        for (inv in firestoreInvoices) {
            val key = invoiceKey(inv)
            val existing = merged[key]
            merged[key] = if (existing == null) inv else mergeInvoice(existing, inv)
        }
        invoiceList = merged.values.sortedByDescending { it.dibuat }
    }

    private fun mergeInvoice(a: Invoice, b: Invoice): Invoice {
        val aConfirmed = a.status == "CONFIRMED" && a.kodeLisensi.isNotBlank()
        val bConfirmed = b.status == "CONFIRMED" && b.kodeLisensi.isNotBlank()
        if (aConfirmed != bConfirmed) return if (aConfirmed) a else b
        if (a.buktiBase64.isNotBlank() != b.buktiBase64.isNotBlank()) {
            return if (a.buktiBase64.isNotBlank()) a else b
        }
        return b
    }

    private fun invoiceKey(inv: Invoice): String =
        inv.id.ifBlank { "${inv.username}|${inv.dibuat}" }

    private fun publishRequests() {
        val merged = LinkedHashMap<String, LicenseRequest>()
        for (req in supabaseRequests) merged[requestKey(req)] = req
        for (req in firestoreRequests) {
            val key = requestKey(req)
            val existing = merged[key]
            merged[key] = if (existing == null) req else req.copy(read = existing.read && req.read)
        }
        val sorted = merged.values.sortedByDescending { it.sentAt }
        licenseRequests = sorted
        pendingRequestCount = sorted.count { !it.read }
    }

    private fun requestKey(req: LicenseRequest): String =
        req.invoiceId.ifBlank { req.id }

    private fun longValue(value: Any?): Long = when (value) {
        is Number -> value.toLong()
        is Timestamp -> value.toDate().time
        is String -> value.toLongOrNull() ?: 0L
        else -> 0L
    }

    private fun docToInvoice(doc: DocumentSnapshot): Invoice? {
        val d = doc.data ?: return null
        return Invoice(
            id = (d["id"] as? String)?.takeIf { it.isNotBlank() } ?: doc.id,
            username = d["username"] as? String ?: "",
            email = d["email"] as? String ?: "",
            paket = d["paket"] as? String ?: "",
            harga = (d["harga"] as? Number)?.toInt() ?: 0,
            status = d["status"] as? String ?: "PENDING",
            dibuat = longValue(d["dibuat"]),
            dibayar = longValue(d["dibayar"]),
            confirmedBy = d["confirmedBy"] as? String ?: "",
            kodeLisensi = d["kodeLisensi"] as? String ?: "",
            buktiBase64 = d["buktiBase64"] as? String ?: "",
            jumlahTv = (d["jumlahTv"] as? Number)?.toInt()
                ?: (d["jumlah_tv"] as? Number)?.toInt() ?: 0,
            maxTv = (d["maxTv"] as? Number)?.toInt() ?: 0,
            mode = d["mode"] as? String ?: "",
            baseMaxTv = (d["baseMaxTv"] as? Number)?.toInt() ?: 0,
            clientEnabled = d["clientEnabled"] as? Boolean ?: true,
            permintaan = d["permintaan"] as? String ?: "",
            hargaPerTv = (d["hargaPerTv"] as? Number)?.toInt() ?: 0,
        )
    }

    private fun startFirestoreInvoiceListener() {
        invoicesListener?.remove()
        invoicesListener = firestore.collection("invoices")
            .orderBy("dibuat", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    Log.e("LicenseGen", "listen invoices: ${error.message}")
                    return@addSnapshotListener
                }
                firestoreInvoices = snap?.documents?.mapNotNull { docToInvoice(it) } ?: emptyList()
                publishInvoices()
            }
    }

    private fun startFirestoreRequestListener() {
        licenseRequestListener?.remove()
        licenseRequestListener = firestore.collection("notifications")
            .whereEqualTo("type", "license_request")
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    Log.e("LicenseGen", "listen license requests: ${error.message}")
                    return@addSnapshotListener
                }
                val previousPending = pendingRequestCount
                firestoreRequests = snap?.documents?.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    LicenseRequest(
                        id = doc.id,
                        user = d["user"] as? String ?: "",
                        paket = d["paket"] as? String ?: "",
                        harga = (d["harga"] as? Number)?.toInt() ?: 0,
                        invoiceId = d["invoiceId"] as? String ?: "",
                        title = d["title"] as? String ?: "",
                        body = d["body"] as? String ?: "",
                        sentAt = longValue(d["sentAt"]),
                        read = d["read"] as? Boolean ?: false,
                    )
                } ?: emptyList()
                publishRequests()
                if (pendingRequestCount > previousPending && previousPending >= 0) {
                    val latest = licenseRequests.firstOrNull { !it.read }
                    if (latest != null) NotificationHelper.show("Request Lisensi Baru", "${latest.user} — ${latest.paket} (Rp${String.format("%,d", latest.harga)})")
                }
            }
    }

    private fun parseStringIntMap(o: JSONObject?): Map<String, Int> {
        val out = mutableMapOf<String, Int>()
        if (o == null) return out
        val it = o.keys()
        while (it.hasNext()) {
            val k = it.next()
            val v = o.opt(k)
            if (v is Number) out[k] = v.toInt()
        }
        return out
    }

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
            startSupabasePolling()
            if (isLoggedIn) startListening()
        }
    }

    fun login(username: String, password: String, onResult: (Boolean, String) -> Unit) {
        val expectedPass = ADMIN_USERS[username] ?: run { onResult(false, "Username tidak dikenal"); return }
        val hash = MessageDigest.getInstance("SHA-256").digest(password.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val expected = MessageDigest.getInstance("SHA-256").digest(expectedPass.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        if (hash != expected) {
            onResult(false, "Password salah")
            return
        }
        isLoggedIn = true
        currentUser = username
        Log.i("LicenseGen", "login success as $username, firestoreReady=$firestoreReady, calling startListening")
        startListening()
        onResult(true, "")
    }

    fun setPromo(promoAktif: Boolean, diskonPerPaket: Map<String, Int>, addTvOverride: Map<String, Int>, promoDurationHours: Int = 0, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                val data = HashMap<String, Any>()
                data["promoAktif"] = promoAktif
                data["diskonPerPaket"] = diskonPerPaket.mapValues { it.value.coerceIn(0, 100) }
                data["addTvOverride"] = addTvOverride
                data["updatedBy"] = currentUser
                data["updatedAt"] = now
                data["promoDurationHours"] = promoDurationHours.coerceAtLeast(0)
                val promoStartedAt = if (promoAktif && !promo.promoAktif) now else promo.promoStartedAt
                val row = JSONObject()
                row.put("id", "global")
                row.put("promoAktif", promoAktif)
                row.put("diskonPerPaket", org.json.JSONObject(diskonPerPaket.mapValues { it.value.coerceIn(0, 100) }))
                row.put("addTvOverride", org.json.JSONObject(addTvOverride))
                row.put("updatedBy", currentUser)
                row.put("updatedAt", now)
                row.put("promoDurationHours", promoDurationHours.coerceAtLeast(0))
                row.put("promoStartedAt", promoStartedAt)
                val ok = supabaseRequest("promo", "POST", row.toString(), prefer = "resolution=merge-duplicates,return=minimal")
                if (!ok) throw RuntimeException("Supabase promo upsert gagal")
                withContext(Dispatchers.Main) {
                    promo = PromoSettings(
                        promoAktif = promoAktif,
                        diskonPerPaket = diskonPerPaket.mapValues { it.value.coerceIn(0, 100) },
                        addTvOverride = addTvOverride,
                        updatedBy = currentUser,
                        updatedAt = now,
                        promoDurationHours = promoDurationHours.coerceAtLeast(0),
                        promoStartedAt = promoStartedAt,
                    )
                }
                withContext(Dispatchers.Main) { onResult(true, "Promo tersimpan!") }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(false, "Gagal: ${e.message}") }
            }
        }
    }

    fun setNewUserPromo(active: Boolean, discountPercent: Int, durationHours: Int, diskonPerPaket: Map<String, Int>, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                val row = JSONObject()
                row.put("id", "global")
                row.put("newUserPromoActive", active)
                row.put("newUserDiscountPercent", discountPercent.coerceIn(0, 100))
                row.put("newUserPromoDurationHours", durationHours.coerceAtLeast(1))
                row.put("newUserDiskonPerPaket", org.json.JSONObject(diskonPerPaket.mapValues { it.value.coerceIn(0, 100) }))
                row.put("updatedBy", currentUser)
                row.put("updatedAt", now)
                val ok = supabaseRequest("promo", "POST", row.toString(), prefer = "resolution=merge-duplicates,return=minimal")
                if (!ok) throw RuntimeException("Supabase promo upsert gagal")
                withContext(Dispatchers.Main) {
                    promo = promo.copy(
                        newUserPromoActive = active,
                        newUserDiscountPercent = discountPercent.coerceIn(0, 100),
                        newUserPromoDurationHours = durationHours.coerceAtLeast(1),
                        newUserDiskonPerPaket = diskonPerPaket.mapValues { it.value.coerceIn(0, 100) },
                        updatedBy = currentUser,
                        updatedAt = now,
                    )
                    onResult(true, "Promo user baru tersimpan!")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(false, "Gagal: ${e.message}") }
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

    // ── RR Online (kode RRTV via Cloudflare Worker) ─────────────
    // Kode RRTV dipakai lisensi per-kartu TV di aplikasi RR Billing Online.
    // Generate lewat worker: POST /admin/keys/gen (header X-RR-TOKEN =
    // RR_ADMIN_TOKEN, secret terpisah dari token kasir).

    data class RrtvKey(val key: String, val plan: String, val batch: String)

    private val rrtvPrefs = application.getSharedPreferences(
        "licensegen_prefs", android.content.Context.MODE_PRIVATE)

    var rrtvWorkerUrl by mutableStateOf(
        rrtvPrefs.getString("rrtv_worker_url", DEFAULT_RRTV_WORKER_URL) ?: DEFAULT_RRTV_WORKER_URL
    ); private set
    var rrtvAdminToken by mutableStateOf(
        rrtvPrefs.getString("rrtv_admin_token", "") ?: ""
    ); private set
    var rrtvBusy by mutableStateOf(false); private set

    fun saveRrtvConfig(url: String, token: String) {
        rrtvWorkerUrl = url.trim().trimEnd('/').ifBlank { DEFAULT_RRTV_WORKER_URL }
        rrtvAdminToken = token.trim()
        rrtvPrefs.edit()
            .putString("rrtv_worker_url", rrtvWorkerUrl)
            .putString("rrtv_admin_token", rrtvAdminToken)
            .apply()
    }

    /**
     * Generate kode RRTV via worker. onDone(ok, pesan, daftar kode).
     * Batch kosong -> worker memakai tanggal hari ini.
     */
    fun genRrtvKeys(plan: String, count: Int, batch: String,
                    onDone: (Boolean, String, List<RrtvKey>) -> Unit) {
        if (rrtvAdminToken.isBlank()) {
            onDone(false, "Admin token belum diisi — buka Settings → RR ONLINE.", emptyList())
            return
        }
        rrtvBusy = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = URL("${rrtvWorkerUrl}/admin/keys/gen")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 20000; conn.readTimeout = 30000
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("X-RR-TOKEN", rrtvAdminToken)
                conn.doOutput = true
                val payload = JSONObject()
                    .put("plan", plan)
                    .put("count", count)
                    .put("batch", batch)
                conn.outputStream.write(payload.toString().toByteArray(Charsets.UTF_8))
                val code = conn.responseCode
                val text = if (code in 200..299) {
                    conn.inputStream.bufferedReader().readText()
                } else {
                    conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $code"
                }
                if (code !in 200..299) {
                    val msg = try {
                        JSONObject(text).optString("error", "HTTP $code")
                    } catch (_: Exception) { "HTTP $code: ${text.take(160)}" }
                    withContext(Dispatchers.Main) {
                        rrtvBusy = false
                        onDone(false, msg, emptyList())
                    }
                    return@launch
                }
                val j = JSONObject(text)
                val arr = j.optJSONArray("keys") ?: JSONArray()
                val keys = mutableListOf<RrtvKey>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    keys.add(RrtvKey(o.optString("key"), o.optString("plan"), o.optString("batch")))
                }
                withContext(Dispatchers.Main) {
                    rrtvBusy = false
                    onDone(true, "${keys.size} kode ${plan.uppercase()} dibuat (batch ${j.optString("batch")}).", keys)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    rrtvBusy = false
                    onDone(false, "Gagal: ${e.message}", emptyList())
                }
            }
        }
    }

    fun markLicenseRequestsRead() {
        pendingRequestCount = 0
        licenseRequests = licenseRequests.map { it.copy(read = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val notifIds = supabaseFetch(
                    "notifications?select=id&type=eq.license_request&read=eq.false&limit=200"
                )
                val arr = notifIds?.let { JSONArray(it) }
                if (arr != null && arr.length() > 0) {
                    val row = JSONObject()
                    row.put("read", true)
                    for (i in 0 until arr.length()) {
                        val id = arr.getJSONObject(i).optString("id", "")
                        if (id.isNotEmpty()) updateById("notifications", id, row)
                    }
                }
            } catch (e: Exception) {
                Log.e("LicenseGen", "markLicenseRequestsRead supabase: ${e.message}")
            }
            try {
                val snap = firestore.collection("notifications")
                    .whereEqualTo("type", "license_request")
                    .get()
                    .awaitResult()
                for (doc in snap.documents) {
                    if (doc.getBoolean("read") != true) {
                        doc.reference.update("read", true).awaitResult()
                    }
                }
            } catch (e: Exception) {
                Log.e("LicenseGen", "markLicenseRequestsRead firestore: ${e.message}")
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
        licenseRequestListener?.remove()
        licenseRequestListener = null
        historyListener?.remove()
        historyListener = null
        supabasePollerJob?.cancel()
        supabasePollerJob = null
        userList = emptyList()
        invoiceList = emptyList()
        supabaseInvoices = emptyList()
        firestoreInvoices = emptyList()
        supabaseRequests = emptyList()
        firestoreRequests = emptyList()
        licenseRequests = emptyList()
        pendingRequestCount = 0
        promo = PromoSettings()
        historyList = emptyList()
    }

    private fun startNotificationListener() {
        startFirestoreRequestListener()
        startSupabasePolling()
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
                        val existingWa = existing?.whatsapp ?: data["whatsappRental"] as? String ?: ""
                        val u = FirestoreUser(
                            username = username,
                            role = data["role"] as? String ?: "",
                            email = data["email"] as? String ?: "",
                            dibuat = data["dibuat"] as? String ?: "",
                            whatsapp = existingWa.ifEmpty { data["whatsappRental"] as? String ?: "" },
                        )
                        if (existing == null || u.email.isNotBlank() || existing.email.isBlank()) {
                            allUsers[username] = u
                        }
                    } else {
                        // Main user doc (sync payload) or backward compat device sync doc
                        val username = doc.id
                        if (username.startsWith("_")) {
                            // Skip docs starting with underscore that aren't _user_
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
                        } else {
                            // Main user doc with appVersion
                            val existing = allUsers[username]
                            val currentUser = data["currentUser"] as? String ?: username
                            val wa = existing?.whatsapp ?: data["whatsappRental"] as? String ?: ""
                            val u = FirestoreUser(
                                username = currentUser,
                                role = existing?.role ?: "",
                                email = existing?.email ?: (data["email"] as? String ?: ""),
                                dibuat = existing?.dibuat ?: "",
                                appVersion = data["appVersion"] as? String ?: "",
                                whatsapp = wa,
                                lastActive = (data["lastActive"] as? Number)?.toLong() ?: 0L,
                            )
                            if (existing == null || u.email.isNotBlank() || existing.email.isBlank()) {
                                allUsers[currentUser] = u
                            }
                        }
                    }
                }
                userList = allUsers.values.sortedBy { it.username }
                Log.i("LicenseGen", "snapshot received: ${snap.documents.size} docs, ${allUsers.size} users parsed, userList.size=${userList.size}")
            }

        startFirestoreInvoiceListener()
        startFirestoreRequestListener()
        historyListener?.remove(); historyListener = null
        promoListener?.remove(); promoListener = null
        startSupabasePolling()
    }

    private fun quotaFromPesan(pesan: String?): Pair<Int, Boolean> {
        if (pesan.isNullOrBlank()) return 0 to false
        val total = Regex("total\\s+(\\d+)\\s*TV", RegexOption.IGNORE_CASE)
            .find(pesan)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        val unlimited = pesan.contains("unlimited", ignoreCase = true) ||
            (pesan.contains("LIFETIME", ignoreCase = true) && total <= 0)
        return total to unlimited
    }

    private fun legacyQuotaFor(paket: String?): Int = when (paket?.trim()?.uppercase()) {
        "1 BULAN", "BULANAN" -> 5
        "3 BULAN", "3BULAN" -> 8
        "1 TAHUN", "TAHUNAN" -> 15
        "LIFETIME" -> 999999
        else -> 0
    }

    private fun expiryIsFuture(value: String?): Boolean {
        if (value.isNullOrBlank()) return true
        return try {
            val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(value)
            date != null && !date.before(java.util.Date())
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Cari kuota TV yang SUDAH dimiliki user dari semua sumber yang masuk akal.
     * Jangan pernah mengandalkan satu kolom saja: data lama menyimpan kuota di
     * maxPc, pesan, invoice.baseMaxTv, atau dokumen Firestore, sementara
     * license_status.maxTv bisa 0 karena bug/cache kolom.
     */
    private suspend fun resolveExistingMaxTv(invoice: Invoice, statusRow: JSONObject?): Int {
        var best = 0
        var activeWithoutQuota = false

        fun consider(value: Int?) {
            if (value != null && value > best) best = value
        }
        fun considerPesan(pesan: String?) {
            val (total, unlimited) = quotaFromPesan(pesan)
            if (total > best) best = total
            if (unlimited) activeWithoutQuota = true
        }

        val status = statusRow?.optString("status", "") ?: ""
        if (status.equals("revoked", true)) return 0
        if (status.equals("active", true) &&
            expiryIsFuture(statusRow?.optString("expiresAt", ""))) {
            consider(statusRow?.optInt("maxTv", 0))
            consider(statusRow?.optInt("maxPc", 0))
            consider(statusRow?.optInt("promoAddTv", 0))
            considerPesan(statusRow?.optString("pesan", ""))
            if (best <= 0) {
                consider(legacyQuotaFor(statusRow?.optString("paket", "")))
            }
        }
        consider(invoice.baseMaxTv)

        // Fallback: dokumen user di Firestore (dulu sumber kuota utama app).
        try {
            val userDoc = firestore.collection("billingps_users")
                .document(invoice.username).get().awaitResult()
            val ls = userDoc.get("licenseStatus") as? Map<*, *>
            if (ls != null) {
                val fsStatus = ls["status"] as? String ?: ""
                if (fsStatus.equals("revoked", true)) return 0
                if (fsStatus.equals("active", true) &&
                    expiryIsFuture(ls["expiresAt"] as? String)) {
                    consider((ls["maxTv"] as? Number)?.toInt())
                    consider((ls["maxPc"] as? Number)?.toInt())
                    consider((ls["promoAddTv"] as? Number)?.toInt())
                    considerPesan(ls["pesan"] as? String)
                    if (best <= 0) {
                        consider(legacyQuotaFor(ls["paket"] as? String))
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("LicenseGen", "resolve quota user doc: ${e.message}")
        }

        // Fallback: riwayat lisensi bertanda tangan yang masih berlaku.
        try {
            val snap = firestore.collection("licenses")
                .whereEqualTo("username", invoice.username)
                .limit(50)
                .get().awaitResult()
            for (doc in snap.documents) {
                if (doc.getBoolean("revoked") == true) continue
                if (!expiryIsFuture(doc.getString("expiry"))) continue
                consider((doc.get("maxTv") as? Number)?.toInt())
                val payload = doc.getString("payload")
                if (!payload.isNullOrBlank()) {
                    try {
                        consider(JSONObject(payload).optInt("q", 0))
                    } catch (_: Exception) { }
                }
            }
        } catch (e: Exception) {
            Log.w("LicenseGen", "resolve quota licenses: ${e.message}")
        }

        if (best <= 0 && activeWithoutQuota) best = 999999
        return best
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

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Hitung expiry DAN maxTv: keduanya MENJUMAH dengan yang
                // sudah dimiliki, bukan menimpa.
                //
                // Contoh: user sudah punya LIFETIME 5 TV, lalu menambah 1
                // unit TV dan membayar invoice 1 TV. Hasilnya harus 6 TV,
                // bukan turun menjadi 1 TV.
                val cal = java.util.Calendar.getInstance()
                var existingRow: JSONObject? = null
                try {
                    val encUser = URLEncoder.encode(invoice.username, "UTF-8")
                    val existingJson = supabaseFetch(
                        "license_status?select=*&id=eq.$encUser&limit=1")
                    existingRow = try {
                        existingJson?.let { org.json.JSONArray(it) }?.optJSONObject(0)
                    } catch (_: Exception) { null }
                    val existingExpires = existingRow?.optString("expiresAt", "")
                    if (!existingExpires.isNullOrEmpty()) {
                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                        val existingDate = sdf.parse(existingExpires)
                        if (existingDate != null && existingDate.after(java.util.Date())) {
                            cal.time = existingDate
                        }
                    }
                } catch (_: Exception) { }
                cal.add(java.util.Calendar.DAY_OF_YEAR, paketDays)
                val expiry = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(cal.time)

                val qty = (invoice.maxTv.takeIf { it > 0 }
                    ?: invoice.jumlahTv.takeIf { it > 0 }
                    ?: when (invoice.paket) {
                        "1 Bulan", "BULANAN" -> 5; "3 Bulan", "3BULAN" -> 8
                        "1 Tahun", "TAHUNAN" -> 15; "LIFETIME" -> 0; else -> 2
                    }).coerceAtLeast(0)
                // Kuota lama bisa tersimpan di maxTv, maxPc, promoAddTv,
                // teks pesan, invoice.baseMaxTv, atau dokumen Firestore.
                // Jangan hanya percaya license_status.maxTv: data lama
                // (dan bug cache kolom sebelumnya) membuat maxTv = 0
                // walaupun user sebenarnya punya 10 TV.
                val existingMaxTv = resolveExistingMaxTv(invoice, existingRow)
                val requestedMode = invoice.mode.trim().uppercase()
                val mode = when (requestedMode) {
                    "ADD", "RENEW" -> requestedMode
                    "NEW" -> if (existingMaxTv > 0) "ADD" else "NEW"
                    else -> if (existingMaxTv > 0) "ADD" else "NEW"
                }
                val targetMaxTv = when {
                    existingMaxTv >= 999999 -> 999999
                    mode == "RENEW" -> if (existingMaxTv > 0) existingMaxTv else qty.coerceAtLeast(1)
                    mode == "ADD" -> if (existingMaxTv > 0) {
                        (existingMaxTv + qty).coerceAtMost(999999)
                    } else qty
                    invoice.paket == "LIFETIME" && qty <= 0 -> 999999
                    else -> qty
                }.let { target ->
                    // Jangan pernah menurunkan kuota yang sudah dibayar.
                    if (existingMaxTv in 1..999998 && target in 1..999998 && target < existingMaxTv) {
                        existingMaxTv
                    } else target
                }
                val clientEnabled = when (invoice.paket.uppercase()) {
                    "BULANAN" -> false
                    "LIFETIME" -> true
                    else -> invoice.clientEnabled
                }
                val paketTvLabel = when (invoice.paket) {
                    "1 Bulan", "BULANAN" -> "Bulanan"
                    "3 Bulan", "3BULAN" -> "3 Bulan"
                    "1 Tahun", "TAHUNAN" -> "1 Tahun"
                    else -> invoice.paket
                }
                val payload = """{"p":"${invoice.paket}","u":"${invoice.username}","e":"$expiry","n":"$nonce","m":"${invoice.email}","q":$targetMaxTv,"c":$clientEnabled,"o":"$mode"}"""
                val signature = ECDSAUtils.sign(payload)

                val nowMs = System.currentTimeMillis()
                val licenseDoc = JSONObject()
                licenseDoc.put("id", kode)
                licenseDoc.put("kode", kode)
                licenseDoc.put("payload", payload)
                licenseDoc.put("signature", signature)
                licenseDoc.put("paket", invoice.paket)
                licenseDoc.put("username", invoice.username)
                licenseDoc.put("email", invoice.email)
                licenseDoc.put("expiry", expiry)
                licenseDoc.put("maxTv", targetMaxTv)
                licenseDoc.put("jumlahTv", qty)
                licenseDoc.put("maxActivations", 1)
                licenseDoc.put("clientEnabled", clientEnabled)
                licenseDoc.put("mode", mode)
                licenseDoc.put("permintaan", buildString {
                    append(
                        when (mode) {
                            "ADD" -> "${invoice.username} menambah $qty TV"
                            "RENEW" -> "${invoice.username} memperpanjang lisensi"
                            else -> "${invoice.username} membeli $qty TV"
                        }
                    )
                    append(" ($paketTvLabel)")
                    if (targetMaxTv >= 999999) {
                        append(" → unlimited TV")
                    } else {
                        append(" → total $targetMaxTv TV")
                    }
                })
                licenseDoc.put("generatedBy", currentUser)
                licenseDoc.put("generatedAt", nowMs)
                licenseDoc.put("activatedAt", 0L)
                licenseDoc.put("activatedDeviceId", "")
                licenseDoc.put("revoked", false)
                if (!reqToleranKolom("licenses", "licenses", "POST", licenseDoc)) {
                    throw RuntimeException("Gagal simpan lisensi: $lastSupabaseError")
                }

                val invUpd = JSONObject()
                invUpd.put("status", "CONFIRMED")
                invUpd.put("kodeLisensi", kode)
                invUpd.put("confirmedBy", currentUser)
                invUpd.put("dibayar", nowMs)
                if (!updateById("invoices", invoice.id, invUpd)) {
                    throw RuntimeException("Gagal update invoice: $lastSupabaseError")
                }

                // Write licenseStatus ke Supabase (desktop membaca dari sini)
                val tvBertambah = if (targetMaxTv >= 999999) 0
                                  else targetMaxTv - existingMaxTv
                Log.i("LicenseGen", "konfirmasi ${invoice.id}: mode=$mode qty=$qty existing=$existingMaxTv -> $targetMaxTv")

                val lsRow = JSONObject()
                lsRow.put("id", invoice.username)
                lsRow.put("status", "active")
                // Pesan menyebut total akhirnya, bukan cuma paket — kasir dan
                // user harus sama-sama paham TV-nya bertambah, bukan diganti.
                lsRow.put("pesan", buildString {
                    append("✅ Lisensi ${invoice.paket} aktif s/d $expiry")
                    if (targetMaxTv >= 999999) {
                        append(" • unlimited TV")
                    } else {
                        append(" • total $targetMaxTv TV")
                        if (tvBertambah > 0) append(" (+$tvBertambah)")
                    }
                })
                lsRow.put("expiresAt", expiry)
                lsRow.put("maxTv", targetMaxTv)
                lsRow.put("maxPc", targetMaxTv)
                lsRow.put("promoAddTv", 0)
                lsRow.put("paket", invoice.paket)
                lsRow.put("clientEnabled", clientEnabled)
                lsRow.put("mode", mode)
                lsRow.put("cloud_restored", true)
                lsRow.put("updatedAt", isoNow())
                val okLs = reqToleranKolom("license_status", "license_status", "POST", lsRow,
                    prefer = "resolution=merge-duplicates,return=minimal")
                if (!okLs) throw RuntimeException("Gagal tulis license_status")

                val notifTitle = "🎉 Lisensi Aktif!"
                val notifBody = if (targetMaxTv >= 999999)
                    "${invoice.paket} aktif hingga $expiry • unlimited TV"
                else "${invoice.paket} aktif s/d $expiry • total $targetMaxTv TV"

                val fsInvoice = hashMapOf<String, Any>(
                    "id" to invoice.id,
                    "username" to invoice.username,
                    "email" to invoice.email,
                    "paket" to invoice.paket,
                    "harga" to invoice.harga,
                    "status" to "CONFIRMED",
                    "dibuat" to invoice.dibuat,
                    "dibayar" to nowMs,
                    "confirmedBy" to currentUser,
                    "kodeLisensi" to kode,
                    "buktiBase64" to invoice.buktiBase64,
                    "jumlahTv" to qty,
                    "maxTv" to targetMaxTv,
                    "mode" to mode,
                    "baseMaxTv" to existingMaxTv,
                    "clientEnabled" to clientEnabled,
                    "permintaan" to licenseDoc.optString("permintaan"),
                )
                firestore.collection("invoices").document(invoice.id)
                    .set(fsInvoice, SetOptions.merge()).awaitResult()

                val fsLicense = hashMapOf<String, Any>(
                    "id" to kode,
                    "kode" to kode,
                    "payload" to payload,
                    "signature" to signature,
                    "paket" to invoice.paket,
                    "username" to invoice.username,
                    "email" to invoice.email,
                    "expiry" to expiry,
                    "maxTv" to targetMaxTv,
                    "jumlahTv" to qty,
                    "maxActivations" to 1,
                    "clientEnabled" to clientEnabled,
                    "mode" to mode,
                    "permintaan" to licenseDoc.optString("permintaan"),
                    "generatedBy" to currentUser,
                    "generatedAt" to nowMs,
                    "activatedAt" to 0L,
                    "activatedDeviceId" to "",
                    "revoked" to false,
                )
                firestore.collection("licenses").document(kode)
                    .set(fsLicense).awaitResult()

                val fsStatus = hashMapOf<String, Any>(
                    "status" to "active",
                    "pesan" to lsRow.optString("pesan"),
                    "expiresAt" to expiry,
                    "maxTv" to targetMaxTv,
                    "promoAddTv" to 0,
                    "paket" to invoice.paket,
                    "clientEnabled" to clientEnabled,
                    "mode" to mode,
                )
                firestore.collection("billingps_users").document(invoice.username)
                    .set(hashMapOf("licenseStatus" to fsStatus), SetOptions.merge()).awaitResult()

                try {
                    val reqSnap = firestore.collection("notifications")
                        .whereEqualTo("type", "license_request")
                        .get()
                        .awaitResult()
                    for (doc in reqSnap.documents) {
                        if (doc.getString("invoiceId") == invoice.id && doc.getBoolean("read") != true) {
                            doc.reference.update("read", true).awaitResult()
                        }
                    }
                } catch (e: Exception) {
                    Log.w("LicenseGen", "mark request read: ${e.message}")
                }

                try {
                    firestore.collection("notifications").add(hashMapOf(
                        "username" to invoice.username,
                        "title" to notifTitle,
                        "body" to notifBody,
                        "type" to "license_confirmed",
                        "sentAt" to nowMs,
                        "read" to false,
                    )).awaitResult()
                } catch (e: Exception) {
                    Log.w("LicenseGen", "firestore notification: ${e.message}")
                }

                sendFcmToUser(invoice.username, notifTitle, notifBody)

                // Save notification history (Supabase)
                try {
                    val notifData = JSONObject()
                    notifData.put("username", invoice.username)
                    notifData.put("title", notifTitle)
                    notifData.put("body", notifBody)
                    notifData.put("type", "license_confirmed")
                    notifData.put("sentAt", nowMs)
                    supabaseRequest("notifications", "POST", notifData.toString())
                } catch (_: Exception) { }

                withContext(Dispatchers.Main) { isBusy = false; onDone(true, kode) }
            } catch (e: Exception) {
                Log.e("LicenseGen", "konfirmasiInvoice failed: ${e.message}")
                withContext(Dispatchers.Main) { isBusy = false; onDone(false, "Gagal: ${e.message}") }
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

    fun generateLicense(paket: String, username: String, email: String,
                        jumlahTv: Int = 1, onDone: (String) -> Unit) {
        if (!ECDSAUtils.hasPrivateKey()) {
            onDone("ERROR: Private key belum dikonfigurasi! Buka tab Settings.")
            return
        }
        // Batas 1..254. Nilai 255 berarti "unlimited" dan hanya sah untuk
        // paket LIFETIME lewat license_status, jadi di sini dibatasi.
        val tv = jumlahTv.coerceIn(1, 254)
        isBusy = true
        val kode = generateKode(paket)
        val nonce = java.util.UUID.randomUUID().toString().take(8)
        val paketDays = when (paket) {
            "1 Bulan", "BULANAN" -> 30; "3 Bulan", "3BULAN" -> 90
            "1 Tahun", "TAHUNAN" -> 360; "LIFETIME" -> 99999; else -> 30
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Jangan memendekkan masa aktif lisensi yang sudah berjalan.
                // Kode q=11 untuk user yang status-nya sudah 2574-05-25 harus
                // ditandatangani dengan expiry yang tidak lebih awal, kalau
                // tidak aplikasi akan menolaknya sebagai tidak valid.
                val cal = java.util.Calendar.getInstance()
                try {
                    val encUser = URLEncoder.encode(username, "UTF-8")
                    val existingJson = supabaseFetch(
                        "license_status?select=expiresAt&id=eq.$encUser&limit=1")
                    val row = try {
                        existingJson?.let { JSONArray(it) }?.optJSONObject(0)
                    } catch (_: Exception) { null }
                    val existingExpires = row?.optString("expiresAt", "")
                    if (!existingExpires.isNullOrEmpty()) {
                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                        val existingDate = sdf.parse(existingExpires)
                        if (existingDate != null && existingDate.after(java.util.Date())) {
                            cal.time = existingDate
                        }
                    }
                } catch (_: Exception) { }
                cal.add(java.util.Calendar.DAY_OF_YEAR, paketDays)
                val expiry = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(cal.time)
                val clientEnabled = paket.uppercase() != "BULANAN"
                val payload = """{"p":"$paket","u":"$username","e":"$expiry","n":"$nonce","m":"$email","q":$tv,"c":$clientEnabled,"o":"NEW"}"""
                val signature = try {
                    ECDSAUtils.sign(payload)
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isBusy = false
                        onDone("ERROR: Signing gagal: ${e.message}")
                    }
                    return@launch
                }

                val doc = JSONObject()
                doc.put("id", kode)
                doc.put("kode", kode)
                doc.put("payload", payload)
                doc.put("signature", signature)
                doc.put("paket", paket)
                doc.put("username", username)
                doc.put("email", email)
                doc.put("expiry", expiry)
                // Jumlah TV yang dibeli. Tanpa field ini, kode baru kembali
                // dengan maxTv kosong dan desktop jatuh ke limit default
                // edition (LIFETIME = tanpa batas) walau admin sudah
                // menjual 7 TV ke user.
                doc.put("maxTv", tv)
                doc.put("jumlahTv", tv)
                doc.put("maxActivations", 1)
                doc.put("clientEnabled", clientEnabled)
                doc.put("mode", "NEW")
                doc.put("permintaan", "$username meminta lisensi $paket untuk $tv TV")
                doc.put("generatedBy", currentUser)
                doc.put("generatedAt", System.currentTimeMillis())
                doc.put("activatedAt", 0L)
                doc.put("activatedDeviceId", "")
                doc.put("revoked", false)
                if (!reqToleranKolom("licenses", "licenses", "POST", doc)) {
                    throw RuntimeException("Gagal simpan lisensi: $lastSupabaseError")
                }

                // Tulis license_status juga. Ini yang dipoll desktop tiap 60
                // detik dan menjadi sumber kebenaran batas TV, jadi tanpa
                // penulisan ini kode yang baru dibuat tidak berlaku di PC
                // pelanggan sampai app restore sendiri.
                val lsRow = JSONObject()
                lsRow.put("id", username)
                lsRow.put("status", "active")
                lsRow.put("pesan", "Lisensi $paket aktif hingga $expiry • $tv TV")
                lsRow.put("expiresAt", expiry)
                lsRow.put("maxTv", tv)
                lsRow.put("maxPc", tv)
                lsRow.put("promoAddTv", 0)
                lsRow.put("paket", paket)
                lsRow.put("clientEnabled", clientEnabled)
                lsRow.put("mode", "NEW")
                lsRow.put("cloud_restored", true)
                lsRow.put("updatedAt", isoNow())
                if (!reqToleranKolom("license_status", "license_status", "POST", lsRow,
                        prefer = "resolution=merge-duplicates,return=minimal")) {
                    // Kode sudah tersimpan; gagal menulis status tidak
                    // membatalkan kode, tapi kasir harus diberi tahu.
                    Log.w("LicenseGen", "Gagal tulis license_status untuk $username: $lastSupabaseError")
                    withContext(Dispatchers.Main) {
                        isBusy = false
                        onDone(kode)
                    }
                    return@launch
                }
            } catch (e: Exception) {
                Log.e("LicenseGen", "Supabase save failed: ${e.message}")
                withContext(Dispatchers.Main) { isBusy = false; onDone("ERROR: ${e.message}") }
                return@launch
            }
            withContext(Dispatchers.Main) { isBusy = false; onDone(kode) }
        }
    }

    suspend fun getHistory(): List<LicenseRecord> = withContext(Dispatchers.IO) {
        try {
            val u = URLEncoder.encode(currentUser, "UTF-8")
            val json = supabaseFetch("licenses?select=*&generatedBy=eq.$u&order=generatedAt.desc&limit=50")
            val list = mutableListOf<LicenseRecord>()
            if (json != null) {
                try {
                    val arr = JSONArray(json)
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        list.add(LicenseRecord(
                            id = o.optString("id", ""), kode = o.optString("kode", ""),
                            payload = o.optString("payload", ""), signature = o.optString("signature", ""),
                            paket = o.optString("paket", ""), username = o.optString("username", ""),
                            email = o.optString("email", ""), expiry = o.optString("expiry", ""),
                            generatedBy = o.optString("generatedBy", ""),
                            generatedAt = o.optLong("generatedAt", 0L),
                            activatedAt = o.optLong("activatedAt", 0L),
                            revoked = o.optBoolean("revoked", false),
                        ))
                    }
                } catch (ex: Exception) { Log.e("LicenseGen", "getHistory parse: ${ex.message}") }
            }
            list.sortedByDescending { it.generatedAt }
        } catch (ex: Exception) {
            Log.e("LicenseGen", "getHistory failed: ${ex.message}")
            emptyList()
        }
    }

    fun deleteUser(username: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                firestore.collection("billingps_users").document(username).delete().await()
                try {
                    firestore.collection("billingps_users").document("_user_$username").delete().await()
                } catch (_: Exception) {}
                withContext(Dispatchers.Main) { onResult(true, "User $username berhasil dihapus") }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(false, "Gagal hapus user: ${e.message}") }
            }
        }
    }

    fun revokeLicense(licenseId: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val licRev = JSONObject()
                licRev.put("revoked", true)
                licRev.put("revokedAt", System.currentTimeMillis())
                licRev.put("revokedBy", currentUser)
                val ok = updateById("licenses", licenseId, licRev)
                withContext(Dispatchers.Main) { onResult(ok, if (ok) "Lisensi dinonaktifkan" else "Gagal: $lastSupabaseError") }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(false, "Gagal: ${e.message}") }
            }
        }
    }

    // ── Pencabutan lisensi yang benar-benar mengunci aplikasi ──────────────
    // revokeLicense() di atas hanya menandai baris `licenses`, yang dibaca
    // desktop saat restore cloud (main.py:19071)._itidak menyentuh
    // license_status, yaitu tabel yang dipoll tiap 60 detik dan satu-satunya
    // sumber yang membuat desktop mengunci tab.
    //
    // Fungsi ini menulis keduanya sekaligus supaya tombol "NONAKTIFKAN"
    // benar-benar mengunci, bukan sekadar mencatat.
    fun revokeUserLicense(username: String, licenseId: String, alasan: String,
                          onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pesan = alasan.trim().ifEmpty { "Lisensi dicabut oleh admin." }
                val encodedUser = URLEncoder.encode(username, "UTF-8")

                // 1. license_status: ini yang membuat desktop terkunci.
                val row = JSONObject()
                row.put("id", username)
                row.put("status", "revoked")
                row.put("pesan", pesan)
                row.put("expiresAt", "")
                row.put("updatedAt", isoNow())
                val okLs = reqToleranKolom("license_status", "license_status", "POST", row,
                    prefer = "resolution=merge-duplicates,return=minimal")
                if (!okLs) {
                    withContext(Dispatchers.Main) {
                        onResult(false, "Gagal menulis license_status: $lastSupabaseError")
                    }
                    return@launch
                }

                // 2. licenses: dicatat supaya tidak ter-RESTORE dari sisi mana pun.
                if (licenseId.isNotEmpty()) {
                    val licRow = JSONObject()
                    licRow.put("revoked", true)
                    licRow.put("revokedAt", System.currentTimeMillis())
                    licRow.put("revokedBy", currentUser)
                    updateById("licenses", licenseId, licRow)
                }

                // 3. Kabari user lewat push + simpan riwayat notifikasi.
                sendFcmToUser(username, "🛑 Lisensi Dinonaktifkan", pesan)
                try {
                    val notifData = JSONObject()
                    notifData.put("username", username)
                    notifData.put("title", "🛑 Lisensi Dinonaktifkan")
                    notifData.put("body", pesan)
                    notifData.put("type", "license_revoked")
                    notifData.put("sentAt", System.currentTimeMillis())
                    supabaseRequest("notifications", "POST", notifData.toString())
                } catch (_: Exception) { }

                pollSupabaseOnce()
                withContext(Dispatchers.Main) {
                    onResult(true, "Lisensi $username dicabut. Aplikasi akan terkunci dalam 60 detik.")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(false, "Gagal: ${e.message}") }
            }
        }
    }

    // Mengembalikan status 'active' untuk sebuah username.
    // Dipakai untuk membatalkan pencabutan tanpa membuat kode baru, selama
    // kode lama masih berlaku (belum lewat tanggalnya).
    fun restoreUserLicense(username: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val encodedUser = URLEncoder.encode(username, "UTF-8")
                val existing = supabaseFetch("license_status?select=*&id=eq.$encodedUser&limit=1")
                val obj = existing?.let { org.json.JSONArray(it) }?.optJSONObject(0)
                if (obj == null) {
                    withContext(Dispatchers.Main) {
                        onResult(false, "Belum ada baris license_status untuk $username")
                    }
                    return@launch
                }
                val existingQuota = maxOf(
                    obj.optInt("maxTv", 0),
                    obj.optInt("maxPc", 0),
                    obj.optInt("promoAddTv", 0),
                    quotaFromPesan(obj.optString("pesan", "")).first,
                )
                val row = JSONObject()
                row.put("id", username)
                row.put("status", "active")
                row.put("pesan", "Lisensi dipulihkan oleh admin.")
                row.put("expiresAt", obj.optString("expiresAt", ""))
                row.put("maxTv", existingQuota)
                if (existingQuota > 0) row.put("maxPc", existingQuota)
                row.put("promoAddTv", obj.optInt("promoAddTv", 0))
                row.put("cloud_restored", true)
                row.put("updatedAt", isoNow())
                val ok = updateById("license_status", username, row)
                if (!ok) {
                    withContext(Dispatchers.Main) {
                        onResult(false, "Gagal memulihkan: $lastSupabaseError")
                    }
                    return@launch
                }
                // Bersihkan juga penanda pada baris licenses.
                try {
                    val licJson = supabaseFetch("licenses?select=id,kode,username&username=eq.$encodedUser")
                    val arr = licJson?.let { org.json.JSONArray(it) }
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val lid = arr.getJSONObject(i).optString("id", "")
                            if (lid.isNotEmpty()) {
                                val licOff = JSONObject()
                                licOff.put("revoked", false)
                                licOff.put("revokedAt", 0)
                                updateById("licenses", lid, licOff)
                            }
                        }
                    }
                } catch (_: Exception) { }

                sendFcmToUser(username, "✅ Lisensi Dipulihkan", "Lisensi Anda sudah dipulihkan.")
                pollSupabaseOnce()
                withContext(Dispatchers.Main) {
                    onResult(true, "Lisensi $username dipulihkan.")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(false, "Gagal: ${e.message}") }
            }
        }
    }

    private fun isoNow(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date())

    fun sendPopupToUser(username: String, title: String, body: String) {
        viewModelScope.launch(Dispatchers.IO) {
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
        viewModelScope.launch(Dispatchers.IO) {
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
        licenseRequestListener?.remove()
        historyListener?.remove()
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

private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitResult(): T =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { if (!cont.isCompleted) cont.resume(it) }
        addOnFailureListener { if (!cont.isCompleted) cont.resumeWithException(it) }
    }
