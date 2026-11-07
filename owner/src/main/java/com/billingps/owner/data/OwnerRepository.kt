package com.billingps.owner.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
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

    fun hasRealSession(): Boolean {
        val user = auth.currentUser ?: return false
        return !user.isAnonymous
    }

    fun currentEmail(): String = auth.currentUser?.email.orEmpty()

    fun currentUid(): String = auth.currentUser?.uid.orEmpty()

    suspend fun signIn(email: String, password: String): OwnerAuthResult {
        val normalizedEmail = email.trim()
        if (normalizedEmail.isBlank() || password.isBlank()) {
            return OwnerAuthResult(false, "Email dan password wajib diisi.")
        }
        return suspendCancellableCoroutine { cont ->
            auth.signInWithEmailAndPassword(normalizedEmail, password)
                .addOnSuccessListener {
                    Log.i(TAG, "Owner sign-in success: ${it.user?.uid}")
                    if (!cont.isCompleted) cont.resume(OwnerAuthResult(true))
                }
                .addOnFailureListener { error ->
                    Log.e(TAG, "Owner sign-in failed: ${error.message}")
                    if (!cont.isCompleted) {
                        cont.resume(OwnerAuthResult(false, friendlyAuthError(error)))
                    }
                }
        }
    }

    suspend fun signInWithGoogle(idToken: String): OwnerAuthResult {
        if (idToken.isBlank()) {
            return OwnerAuthResult(false, "Token Google tidak valid. Coba lagi.")
        }
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        return suspendCancellableCoroutine { cont ->
            auth.signInWithCredential(credential)
                .addOnSuccessListener {
                    Log.i(TAG, "Owner Google sign-in success: ${it.user?.uid}")
                    if (!cont.isCompleted) cont.resume(OwnerAuthResult(true))
                }
                .addOnFailureListener { error ->
                    Log.e(TAG, "Owner Google sign-in failed: ${error.message}")
                    if (!cont.isCompleted) {
                        cont.resume(OwnerAuthResult(false, friendlyAuthError(error)))
                    }
                }
        }
    }

    fun signOut() {
        try {
            auth.signOut()
        } catch (t: Throwable) {
            Log.e(TAG, "Owner sign-out failed: ${t.message}", t)
        }
    }

    /**
     * Menentukan username rental dari akun Firebase yang sedang login.
     * Sumber utama: mapping `owner_accounts/{uid}` yang ditulis aplikasi billing.
     * Fallback: dokumen `_user_<username>` dengan email yang sama.
     */
    suspend fun resolveRentalUsername(email: String): String? {
        val user = auth.currentUser ?: return null
        if (user.isAnonymous) return null
        val normalizedEmail = email.trim()
        val mapped = fetchOwnerAccountRental(user.uid)
        if (!mapped.isNullOrBlank() && verifyRentalEmail(mapped, normalizedEmail)) {
            return mapped
        }
        if (!mapped.isNullOrBlank()) {
            Log.w(TAG, "owner_accounts mapping mismatch for uid=${user.uid}, rental=$mapped")
        }
        return queryRentalByEmail(normalizedEmail)
    }

    private suspend fun fetchOwnerAccountRental(uid: String): String? =
        suspendCancellableCoroutine { cont ->
            db.collection("owner_accounts").document(uid).get()
                .addOnSuccessListener { snap ->
                    if (!cont.isCompleted) cont.resume(snap.getString("rentalUsername"))
                }
                .addOnFailureListener { error ->
                    Log.e(TAG, "fetchOwnerAccountRental failed: ${error.message}")
                    if (!cont.isCompleted) cont.resume(null)
                }
        }

    private suspend fun verifyRentalEmail(rentalUsername: String, email: String): Boolean =
        suspendCancellableCoroutine { cont ->
            db.collection("billingps_users").document("_user_$rentalUsername").get()
                .addOnSuccessListener { snap ->
                    val docEmail = snap.getString("email").orEmpty()
                    if (!cont.isCompleted) {
                        cont.resume(snap.exists() && docEmail.equals(email, ignoreCase = true))
                    }
                }
                .addOnFailureListener { error ->
                    Log.e(TAG, "verifyRentalEmail failed: ${error.message}")
                    if (!cont.isCompleted) cont.resume(false)
                }
        }

    private suspend fun queryRentalByEmail(email: String): String? =
        suspendCancellableCoroutine { cont ->
            db.collection("billingps_users")
                .whereEqualTo("email", email)
                .limit(20)
                .get()
                .addOnSuccessListener { snap ->
                    val rental = snap.documents.firstOrNull { doc ->
                        val role = doc.getString("role").orEmpty()
                        role.equals("admin", ignoreCase = true) || role.equals("owner", ignoreCase = true)
                    }?.let { doc ->
                        doc.getString("username")?.takeIf { it.isNotBlank() }
                            ?: doc.id.removePrefix("_user_")
                    }
                    if (!cont.isCompleted) cont.resume(rental)
                }
                .addOnFailureListener { error ->
                    Log.e(TAG, "queryRentalByEmail failed: ${error.message}")
                    if (!cont.isCompleted) cont.resume(null)
                }
        }

    private fun friendlyAuthError(error: Exception): String = when (error) {
        is FirebaseAuthInvalidCredentialsException -> "Email atau password salah."
        is FirebaseAuthInvalidUserException -> "Akun owner tidak ditemukan."
        else -> error.message?.takeIf { it.isNotBlank() } ?: "Gagal login ke Firebase."
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
                            menuMakanan = parseIntMap(snap.get("menuMakanan")),
                            menuMinuman = parseIntMap(snap.get("menuMinuman")),
                            stokMakanan = parseIntMap(snap.get("stokMakanan")),
                            stokMinuman = parseIntMap(snap.get("stokMinuman")),
                            jenisPsList = parseStringList(snap.get("jenisPsList")),
                            paketMain = parseNestedIntMap(snap.get("paketMain")),
                            paketDurasi = parseIntMap(snap.get("paketDurasi")),
                            cashDrawer = parseCashDrawer(snap.get("cashDrawer")),
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
        onUpdate: (ClientStatus?) -> Unit,
    ): ListenerRegistration? {
        if (username.isBlank() || tvId.isBlank()) return null
        // TV client menulis heartbeat ke billingps_users/<username-rental>/tvCommands/<tvId>
        return db.collection("billingps_users").document(username)
            .collection("tvCommands").document(tvId)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    Log.e(TAG, "listenClientStatus $tvId error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snap == null || !snap.exists()) {
                    onUpdate(null)
                    return@addSnapshotListener
                }
                onUpdate(
                    ClientStatus(
                        online = snap.getBoolean("clientOnline") ?: false,
                        version = snap.getString("clientVersion") ?: "",
                        lastSeen = snap.getTimestamp("lastSeen")?.toDate()?.time ?: 0L,
                    ),
                )
            }
    }

    suspend fun submitStockOpname(
        rentalUsername: String,
        email: String,
        items: List<StockOpnameItem>,
        note: String,
    ): OwnerAuthResult {
        if (rentalUsername.isBlank()) {
            return OwnerAuthResult(false, "Rental belum dikenali. Login ulang.")
        }
        if (items.isEmpty()) {
            return OwnerAuthResult(false, "Belum ada tambahan stok.")
        }
        val submitterEmail = currentEmail().ifBlank { email.trim() }
        val payload = mapOf<String, Any?>(
            "status" to "pending",
            "rentalUsername" to rentalUsername,
            "submittedByEmail" to submitterEmail,
            "submittedByUid" to currentUid(),
            "note" to note.trim(),
            "createdAt" to System.currentTimeMillis(),
            "items" to items.map { item ->
                mapOf(
                    "name" to item.name,
                    "category" to item.category,
                    "expected" to item.expected,
                    "quantity" to item.quantity,
                )
            },
        )
        return suspendCancellableCoroutine { cont ->
            db.collection("billingps_users").document(rentalUsername)
                .collection("stockOpnames")
                .add(payload)
                .addOnSuccessListener { ref ->
                    Log.i(TAG, "Stock addition submitted: ${ref.id}")
                    if (!cont.isCompleted) {
                        cont.resume(OwnerAuthResult(true, "Tambah stok terkirim."))
                    }
                }
                .addOnFailureListener { error ->
                    Log.e(TAG, "Stock addition submit failed: ${error.message}")
                    if (!cont.isCompleted) {
                        cont.resume(
                            OwnerAuthResult(
                                false,
                                error.message?.takeIf { it.isNotBlank() }
                                    ?: "Gagal mengirim tambahan stok.",
                            ),
                        )
                    }
                }
        }
    }

    suspend fun submitFnbItem(
        rentalUsername: String,
        email: String,
        input: FnbItemInput,
    ): OwnerAuthResult {
        if (rentalUsername.isBlank()) {
            return OwnerAuthResult(false, "Rental belum dikenali. Login ulang.")
        }
        val name = input.name.trim()
        if (name.isBlank()) {
            return OwnerAuthResult(false, "Nama item wajib diisi.")
        }
        val category = input.category.lowercase()
        if (category != "makanan" && category != "minuman") {
            return OwnerAuthResult(false, "Kategori item tidak valid.")
        }
        val action = input.action.trim().lowercase()
        if (action != "add" && action != "update") {
            return OwnerAuthResult(false, "Aksi item F&B tidak valid.")
        }
        val initialStock = if (action == "update") 0 else input.initialStock
        if (input.price < 0 || initialStock < 0) {
            return OwnerAuthResult(false, "Harga dan stok awal tidak boleh negatif.")
        }
        val submitterEmail = currentEmail().ifBlank { email.trim() }
        val payload = mapOf<String, Any?>(
            "status" to "pending",
            "rentalUsername" to rentalUsername,
            "submittedByEmail" to submitterEmail,
            "submittedByUid" to currentUid(),
            "action" to action,
            "category" to category,
            "name" to name,
            "price" to input.price,
            "initialStock" to initialStock,
            "createdAt" to System.currentTimeMillis(),
        )
        return submitOwnerRequest(
            rentalUsername = rentalUsername,
            collection = "fnbItemRequests",
            payload = payload,
            successMessage = if (action == "update") {
                "Perubahan harga F&B terkirim. Menunggu diproses aplikasi billing."
            } else {
                "Item F&B terkirim. Menunggu diproses aplikasi billing."
            },
            failureMessage = if (action == "update") {
                "Gagal mengirim perubahan harga F&B."
            } else {
                "Gagal mengirim item F&B."
            },
            logTag = "F&B item",
        )
    }

    suspend fun submitPackage(
        rentalUsername: String,
        email: String,
        input: PackageInput,
    ): OwnerAuthResult {
        if (rentalUsername.isBlank()) {
            return OwnerAuthResult(false, "Rental belum dikenali. Login ulang.")
        }
        val groupName = input.groupName.trim()
        val name = input.name.trim()
        if (groupName.isBlank()) {
            return OwnerAuthResult(false, "Grup paket wajib dipilih.")
        }
        if (name.isBlank()) {
            return OwnerAuthResult(false, "Nama paket wajib diisi.")
        }
        if (input.action != "add" && input.action != "update") {
            return OwnerAuthResult(false, "Aksi paket tidak valid.")
        }
        if (input.price < 0 || input.durationMinutes < 0) {
            return OwnerAuthResult(false, "Harga dan durasi tidak boleh negatif.")
        }
        val submitterEmail = currentEmail().ifBlank { email.trim() }
        val payload = mapOf<String, Any?>(
            "status" to "pending",
            "rentalUsername" to rentalUsername,
            "submittedByEmail" to submitterEmail,
            "submittedByUid" to currentUid(),
            "action" to input.action,
            "groupName" to groupName,
            "name" to name,
            "price" to input.price,
            "durationMinutes" to input.durationMinutes,
            "createdAt" to System.currentTimeMillis(),
        )
        return submitOwnerRequest(
            rentalUsername = rentalUsername,
            collection = "packageRequests",
            payload = payload,
            successMessage = "Paket terkirim. Menunggu diproses aplikasi billing.",
            failureMessage = "Gagal mengirim paket.",
            logTag = "Package",
        )
    }

    suspend fun submitGroup(
        rentalUsername: String,
        email: String,
        input: GroupInput,
    ): OwnerAuthResult {
        if (rentalUsername.isBlank()) {
            return OwnerAuthResult(false, "Rental belum dikenali. Login ulang.")
        }
        val name = input.name.trim()
        if (name.isBlank()) {
            return OwnerAuthResult(false, "Nama grup wajib diisi.")
        }
        val submitterEmail = currentEmail().ifBlank { email.trim() }
        val payload = mapOf<String, Any?>(
            "status" to "pending",
            "rentalUsername" to rentalUsername,
            "submittedByEmail" to submitterEmail,
            "submittedByUid" to currentUid(),
            "name" to name,
            "createdAt" to System.currentTimeMillis(),
        )
        return submitOwnerRequest(
            rentalUsername = rentalUsername,
            collection = "groupRequests",
            payload = payload,
            successMessage = "Grup PS terkirim. Menunggu diproses aplikasi billing.",
            failureMessage = "Gagal mengirim grup PS.",
            logTag = "PS group",
        )
    }

    private suspend fun submitOwnerRequest(
        rentalUsername: String,
        collection: String,
        payload: Map<String, Any?>,
        successMessage: String,
        failureMessage: String,
        logTag: String,
    ): OwnerAuthResult = suspendCancellableCoroutine { cont ->
        db.collection("billingps_users").document(rentalUsername)
            .collection(collection)
            .add(payload)
            .addOnSuccessListener { ref ->
                Log.i(TAG, "$logTag submitted: ${ref.id}")
                if (!cont.isCompleted) cont.resume(OwnerAuthResult(true, successMessage))
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "$logTag submit failed: ${error.message}")
                if (!cont.isCompleted) {
                    cont.resume(
                        OwnerAuthResult(
                            false,
                            error.message?.takeIf { it.isNotBlank() } ?: failureMessage,
                        ),
                    )
                }
            }
    }

    fun listenCashDrawerRequests(
        rentalUsername: String,
        onUpdate: (List<CashDrawerRequest>) -> Unit,
    ): ListenerRegistration? {
        if (rentalUsername.isBlank()) return null
        return db.collection("billingps_users").document(rentalUsername)
            .collection("cashDrawerRequests")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(30)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    Log.e(TAG, "listenCashDrawerRequests error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snap == null) return@addSnapshotListener
                val requests = snap.documents.mapNotNull { doc ->
                    val nominal = (doc.get("nominal") as? Number)?.toInt() ?: return@mapNotNull null
                    CashDrawerRequest(
                        id = doc.id,
                        nominal = nominal,
                        note = doc.getString("note").orEmpty(),
                        status = doc.getString("status").orEmpty().ifBlank { "pending" },
                        createdAt = (doc.get("createdAt") as? Number)?.toLong() ?: 0L,
                        processedAt = (doc.get("processedAt") as? Number)?.toLong() ?: 0L,
                        appliedBy = doc.getString("appliedBy").orEmpty(),
                        reason = doc.getString("reason").orEmpty(),
                    )
                }.sortedWith(
                    compareByDescending<CashDrawerRequest> { it.pending }
                        .thenByDescending { it.createdAt },
                )
                onUpdate(requests)
            }
    }

    suspend fun submitCashDrawerRequest(
        rentalUsername: String,
        email: String,
        nominal: Int,
        note: String,
    ): OwnerAuthResult {
        if (rentalUsername.isBlank()) {
            return OwnerAuthResult(false, "Rental belum dikenali. Login ulang.")
        }
        if (nominal <= 0) {
            return OwnerAuthResult(false, "Nominal penarikan harus lebih dari 0.")
        }
        val submitterEmail = currentEmail().ifBlank { email.trim() }
        val payload = mapOf<String, Any?>(
            "status" to "pending",
            "rentalUsername" to rentalUsername,
            "submittedByEmail" to submitterEmail,
            "submittedByUid" to currentUid(),
            "nominal" to nominal,
            "note" to note.trim(),
            "createdAt" to System.currentTimeMillis(),
        )
        return suspendCancellableCoroutine { cont ->
            db.collection("billingps_users").document(rentalUsername)
                .collection("cashDrawerRequests")
                .add(payload)
                .addOnSuccessListener { ref ->
                    Log.i(TAG, "Cash drawer request submitted: ${ref.id}")
                    if (!cont.isCompleted) {
                        cont.resume(OwnerAuthResult(true, "Permintaan penarikan terkirim."))
                    }
                }
                .addOnFailureListener { error ->
                    Log.e(TAG, "Cash drawer request submit failed: ${error.message}")
                    if (!cont.isCompleted) {
                        cont.resume(
                            OwnerAuthResult(
                                false,
                                error.message?.takeIf { it.isNotBlank() }
                                    ?: "Gagal mengirim permintaan penarikan.",
                            ),
                        )
                    }
                }
        }
    }

    private fun parseCashDrawer(raw: Any?): OwnerCashDrawer {
        val map = raw as? Map<*, *> ?: return OwnerCashDrawer()
        return OwnerCashDrawer(
            status = map.str("status", "TUTUP").ifBlank { "TUTUP" },
            shiftId = map.str("shiftId"),
            shiftUsername = map.str("shiftUsername"),
            jenisShift = map.str("jenisShift"),
            startedAt = map.long("startedAt"),
            openingCash = map.int("openingCash"),
            cashExpected = map.int("cashExpected"),
            cashSales = map.int("cashSales"),
            cashIn = map.int("cashIn"),
            cashOut = map.int("cashOut"),
            updatedAt = map.long("updatedAt"),
        )
    }

    private fun parseIntMap(raw: Any?): Map<String, Int> {
        val map = raw as? Map<*, *> ?: return emptyMap()
        val out = mutableMapOf<String, Int>()
        map.forEach { (key, value) ->
            if (key is String && value is Number) out[key] = value.toInt()
        }
        return out
    }

    private fun parseStringList(raw: Any?): List<String> =
        (raw as? List<*>)?.mapNotNull { it as? String }?.filter { it.isNotBlank() }.orEmpty()

    private fun parseNestedIntMap(raw: Any?): Map<String, Map<String, Int>> {
        val map = raw as? Map<*, *> ?: return emptyMap()
        val out = mutableMapOf<String, Map<String, Int>>()
        map.forEach { (key, value) ->
            if (key is String) out[key] = parseIntMap(value)
        }
        return out
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
            // Map paid (legacy) -> sudahBayar (new schema)
            sudahBayar = o.optBoolean("sudahBayar", o.optBoolean("paid", false)),
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
