package com.billingps.owner.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.coroutines.resume

class OwnerRepository {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    suspend fun ensureSignedIn(): Boolean {
        if (auth.currentUser != null) return true
        return suspendCancellableCoroutine { cont ->
            auth.signInAnonymously()
                .addOnSuccessListener {
                    Log.i(TAG, "Anonymous auth success: ${it.user?.uid}")
                    if (!cont.isCompleted) cont.resume(true)
                }
                .addOnFailureListener { error ->
                    Log.e(TAG, "Anonymous auth failed: ${error.message}")
                    if (!cont.isCompleted) cont.resume(false)
                }
        }
    }

    fun listenRental(username: String, onUpdate: (RentalSnapshot) -> Unit): ListenerRegistration? {
        if (username.isBlank()) return null
        return db.collection("billingps_users").document(username)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    Log.e(TAG, "listenRental error: ${error.message}")
                    onUpdate(RentalSnapshot(error = error.message ?: "Gagal membaca data rental"))
                    return@addSnapshotListener
                }
                if (snap == null || !snap.exists()) {
                    onUpdate(RentalSnapshot(exists = false))
                    return@addSnapshotListener
                }
                try {
                    onUpdate(
                        RentalSnapshot(
                            exists = true,
                            tvList = parseTvList(snap.get("tvList")),
                            transaksiList = parseFirestoreTransactions(snap.get("transaksiList")),
                            lastSync = (snap.get("lastSync") as? Number)?.toLong() ?: 0L,
                            appVersion = snap.getString("appVersion") ?: "",
                            currentUser = snap.getString("currentUser") ?: "",
                        ),
                    )
                } catch (t: Throwable) {
                    Log.e(TAG, "listenRental parse failed: ${t.message}", t)
                    onUpdate(RentalSnapshot(error = "Gagal memproses data rental"))
                }
            }
    }

    fun listenClientStatus(
        username: String,
        tvId: String,
        onUpdate: (ClientStatus) -> Unit,
    ): ListenerRegistration? {
        if (username.isBlank() || tvId.isBlank()) return null
        return db.collection("billingps_users").document(username)
            .collection("tvCommands").document(tvId)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    Log.e(TAG, "listenClientStatus $tvId error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snap == null) return@addSnapshotListener
                onUpdate(
                    ClientStatus(
                        online = snap.getBoolean("clientOnline") ?: false,
                        version = snap.getString("clientVersion") ?: "",
                        lastSeen = snap.getTimestamp("lastSeen")?.toDate()?.time ?: 0L,
                    ),
                )
            }
    }

    suspend fun fetchRentalInfo(username: String): RentalInfo {
        if (username.isBlank()) return RentalInfo()
        return suspendCancellableCoroutine { cont ->
            db.collection("billingps_users").document("_user_$username").get()
                .addOnSuccessListener { snap ->
                    if (!snap.exists()) {
                        if (!cont.isCompleted) cont.resume(RentalInfo(username = username))
                        return@addOnSuccessListener
                    }
                    if (!cont.isCompleted) {
                        cont.resume(
                            RentalInfo(
                                username = snap.getString("username") ?: username,
                                namaRental = snap.getString("namaRental") ?: "",
                                email = snap.getString("email") ?: "",
                                whatsapp = snap.getString("whatsappRental") ?: "",
                                alamat = snap.getString("alamatRental") ?: "",
                            ),
                        )
                    }
                }
                .addOnFailureListener { error ->
                    Log.e(TAG, "fetchRentalInfo failed: ${error.message}")
                    if (!cont.isCompleted) cont.resume(RentalInfo(username = username))
                }
        }
    }

    suspend fun fetchTransactions(username: String): List<OwnerTransaction>? {
        if (username.isBlank()) return emptyList()
        return withContext(Dispatchers.IO) {
            val encodedUser = URLEncoder.encode(username, "UTF-8")
            val all = mutableListOf<OwnerTransaction>()
            var offset = 0
            var page = 0
            while (page < MAX_PAGES) {
                val path = "transaksi?select=*&username=eq.$encodedUser" +
                    "&order=waktu.desc&limit=$PAGE_SIZE&offset=$offset"
                val json = httpGet(path) ?: return@withContext null
                try {
                    val array = JSONArray(json)
                    for (i in 0 until array.length()) {
                        all.add(parseTransaction(array.getJSONObject(i)))
                    }
                    if (array.length() < PAGE_SIZE) break
                    offset += PAGE_SIZE
                    page++
                } catch (t: Throwable) {
                    Log.e(TAG, "fetchTransactions parse failed: ${t.message}", t)
                    return@withContext null
                }
            }
            all.sortedByDescending {
                com.billingps.owner.util.OwnerTime.transactionInstant(it.id, it.waktu)
                    ?: java.time.Instant.EPOCH
            }
        }
    }

    private fun httpGet(path: String): String? {
        val connection = (URL("$BASE_URL/rest/v1/$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("apikey", API_KEY)
            setRequestProperty("Authorization", "Bearer $API_KEY")
        }
        return try {
            val code = connection.responseCode
            if (code in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                Log.e(TAG, "GET transaksi HTTP $code: $detail")
                null
            }
        } catch (t: Throwable) {
            Log.e(TAG, "GET transaksi failed: ${t.message}", t)
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun parseTransaction(o: JSONObject): OwnerTransaction =
        OwnerTransaction(
            id = o.optString("id", ""),
            username = o.optString("username", ""),
            email = o.optString("email", ""),
            waktu = o.optString("waktu", ""),
            kasir = o.optString("kasir", ""),
            kota = o.optString("kota", ""),
            paket = o.optString("paket", ""),
            total = o.optInt("total", 0),
            paketHarga = o.optInt("paketHarga", 0),
            pesanan = jsonIntMap(o.optJSONObject("pesanan")),
            pesananHarga = jsonIntMap(o.optJSONObject("pesananHarga")),
            tvJenisPs = o.optString("tvJenisPs", ""),
            sudahBayar = o.optBoolean("sudahBayar", false),
            metode = o.optString("metode", ""),
        )

    private fun jsonIntMap(o: JSONObject?): Map<String, Int> {
        if (o == null) return emptyMap()
        val out = mutableMapOf<String, Int>()
        val keys = o.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            out[key] = o.optInt(key, 0)
        }
        return out
    }

    private fun parseTvList(raw: Any?): List<OwnerTv> {
        val list = raw as? List<*> ?: return emptyList()
        return list.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            OwnerTv(
                id = map.str("id"),
                nama = map.str("nama"),
                ip = map.str("ip"),
                port = map.int("port", 5555),
                jenisPs = map.str("jenisPs", "PS3"),
                paketAktif = map.str("paketAktif"),
                sisaDetik = map.long("sisaDetik"),
                timerStart = map.long("timerStart"),
                timerDurasi = map.long("timerDurasi"),
                timerActive = map.bool("timerActive"),
                bebas = map.bool("bebas"),
                paketHarga = map.int("paketHarga"),
                totalPesanan = map.int("totalPesanan"),
                trxId = map.str("trxId"),
                pesanan = map.intMap("pesanan"),
                pesananHarga = map.intMap("pesananHarga"),
                bebasMulai = map.long("bebasMulai"),
                bebasHargaPerJam = map.int("bebasHargaPerJam"),
                bebasPesananTotal = map.int("bebasPesananTotal"),
                cancelBatas = map.long("cancelBatas"),
                sudahBayar = map.bool("sudahBayar"),
                namaRental = map.str("namaRental"),
                hdmiPort = map.int("hdmiPort"),
            )
        }
    }

    private fun parseFirestoreTransactions(raw: Any?): List<OwnerTransaction> {
        val list = raw as? List<*> ?: return emptyList()
        return list.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            OwnerTransaction(
                id = map.str("id"),
                username = map.str("username"),
                email = map.str("email"),
                waktu = map.str("waktu"),
                kasir = map.str("kasir"),
                kota = map.str("kota"),
                paket = map.str("paket"),
                total = map.int("total"),
                paketHarga = map.int("paketHarga"),
                pesanan = map.intMap("pesanan"),
                pesananHarga = map.intMap("pesananHarga"),
                tvJenisPs = map.str("tvJenisPs"),
                sudahBayar = map.bool("sudahBayar"),
                metode = map.str("metode"),
            )
        }
    }

    private fun Map<*, *>.str(key: String, fallback: String = ""): String =
        this[key] as? String ?: fallback

    private fun Map<*, *>.int(key: String, fallback: Int = 0): Int =
        (this[key] as? Number)?.toInt() ?: fallback

    private fun Map<*, *>.long(key: String): Long =
        (this[key] as? Number)?.toLong() ?: 0L

    private fun Map<*, *>.bool(key: String): Boolean =
        this[key] as? Boolean ?: false

    private fun Map<*, *>.intMap(key: String): Map<String, Int> {
        val raw = this[key] as? Map<*, *> ?: return emptyMap()
        val out = mutableMapOf<String, Int>()
        raw.forEach { (k, v) ->
            if (k is String && v is Number) out[k] = v.toInt()
        }
        return out
    }

    companion object {
        private const val TAG = "OwnerRepo"
        private const val PAGE_SIZE = 1000
        private const val MAX_PAGES = 20
        private const val BASE_URL = "https://nqaucjpbnewckedqcezb.supabase.co"
        private const val API_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im5xYXVjanBibmV3Y2tlZHFjZXpiIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODg1MjUyNzUsImV4cCI6MjEwNDEwMTI3NX0.xi-h-3E2Yww95HzXsNmmiOmdvMKi_TaFNw8Op7xryig"
    }
}
