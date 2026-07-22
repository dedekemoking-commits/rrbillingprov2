package com.billingps.aptv.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.billingps.aptv.models.*
import org.json.JSONArray
import org.json.JSONObject
import android.util.Log

object StorageUtil {
    private const val PREFS_NAME = "billingps_data"
    private const val PREFS_SENSITIVE = "billingps_sensitive"
    private var _ctx: Context? = null
    private val prefs: SharedPreferences get() = _ctx!!.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val securePrefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(_ctx!!)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                _ctx!!,
                PREFS_SENSITIVE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            Log.e("StorageUtil", "securePrefs init failed: ${e.message}")
            _ctx!!.getSharedPreferences(PREFS_SENSITIVE, Context.MODE_PRIVATE)
        }
    }

    fun init(context: Context) {
        _ctx = context.applicationContext
        // trigger securePrefs init to validate early
        try { securePrefs.edit().apply() } catch (e: Exception) { Log.e("StorageUtil", "init securePrefs edit: ${e.message}") }
    }

    // ── Users ──────────────────────────────────────────────
fun saveUsers(users: Map<String, UserData>) {
    val obj = JSONObject()
    users.forEach { (k, v) ->
        obj.put(k, JSONObject().apply {
            put("passwordHash", v.passwordHash)
            put("role", v.role)
            put("email", v.email)
            put("dibuat", v.dibuat)
            put("emailVerified", v.emailVerified)
            put("verificationCode", v.verificationCode)
            put("resetCode", v.resetCode)
            put("resetCodeExpiry", v.resetCodeExpiry)
            put("namaRental", v.namaRental)
            put("alamatRental", v.alamatRental)
            put("whatsappRental", v.whatsappRental)
            put("registeredAt", v.registeredAt)
        })
    }
    prefs.edit().putString("users", obj.toString()).apply()
}

fun loadUsers(): Map<String, UserData> {
    val raw = prefs.getString("users", "{}") ?: "{}"
    val obj = JSONObject(raw)
    val map = mutableMapOf<String, UserData>()
    obj.keys().forEach { key ->
        val u = obj.getJSONObject(key)
        map[key] = UserData(
            username = key,
            passwordHash = u.optString("passwordHash", ""),
            role = u.optString("role", "kasir"),
            email = u.optString("email", ""),
            dibuat = u.optString("dibuat", ""),
            emailVerified = u.optBoolean("emailVerified", false),
            verificationCode = u.optString("verificationCode", ""),
            resetCode = u.optString("resetCode", ""),
            resetCodeExpiry = u.optLong("resetCodeExpiry", 0),
            namaRental = u.optString("namaRental", ""),
            alamatRental = u.optString("alamatRental", ""),
            whatsappRental = u.optString("whatsappRental", ""),
            registeredAt = u.optLong("registeredAt", 0),
        )
    }
    return map
}

    fun saveCurrentSession(username: String, role: String) {
        prefs.edit()
            .putString("currentUser", username)
            .putString("currentRole", role)
            .apply()
    }

    fun loadCurrentUser(): String = prefs.getString("currentUser", "") ?: ""
    fun loadCurrentRole(): String = prefs.getString("currentRole", "") ?: ""

    fun clearSession() {
        prefs.edit()
            .remove("currentUser")
            .remove("currentRole")
            .apply()
    }

    // ── Harga ──────────────────────────────────────────────
    fun saveHarga(paketMain: Map<String, Map<String, Int>>, paketDurasi: Map<String, Int>,
                  menuMakanan: Map<String, Int>, menuMinuman: Map<String, Int>) {
        prefs.edit()
            .putString("paketMain", map2dToJson(paketMain))
            .putString("paketDurasi", mapToJson(paketDurasi))
            .putString("menuMakanan", mapToJson(menuMakanan))
            .putString("menuMinuman", mapToJson(menuMinuman))
            .apply()
    }

    fun loadPaketMain(): Map<String, Map<String, Int>> = jsonToMap2d(prefs.getString("paketMain", null))
    fun loadPaketDurasi(): Map<String, Int> = jsonToMap(prefs.getString("paketDurasi", null))
    fun loadMenuMakanan(): Map<String, Int> = jsonToMap(prefs.getString("menuMakanan", null))
    fun loadMenuMinuman(): Map<String, Int> = jsonToMap(prefs.getString("menuMinuman", null))

    fun saveJenisPsList(list: List<String>) {
        val arr = org.json.JSONArray(list)
        prefs.edit().putString("jenisPsList", arr.toString()).apply()
    }

    fun loadJenisPsList(): List<String> {
        val raw = prefs.getString("jenisPsList", null) ?: return emptyList()
        val arr = org.json.JSONArray(raw)
        return (0 until arr.length()).map { arr.optString(it, "") }.filter { it.isNotBlank() }
    }

    // ── TV List ────────────────────────────────────────────
    fun saveTvList(list: List<TvData>) {
        val arr = JSONArray()
        list.forEach { tv ->
            arr.put(JSONObject().apply {
                put("id", tv.id)
                put("nama", tv.nama)
                put("ip", tv.ip)
                put("port", tv.port)
                put("jenisPs", tv.jenisPs)
                put("paketAktif", tv.paketAktif)
                put("sisaDetik", tv.sisaDetik)
                put("timerStart", tv.timerStart)
                put("timerDurasi", tv.timerDurasi)
                put("timerActive", tv.timerActive)
                put("bebas", tv.bebas)
                put("paketHarga", tv.paketHarga)
                put("totalPesanan", tv.totalPesanan)
                put("bebasMulai", tv.bebasMulai)
                put("bebasHargaPerJam", tv.bebasHargaPerJam)
                put("bebasPesananTotal", tv.bebasPesananTotal)
                put("paired", tv.paired)
                put("certPem", tv.certPem)
                put("keyPem", tv.keyPem)
                put("cancelBatas", tv.cancelBatas)
            })
        }
        prefs.edit().putString("tvList", arr.toString()).apply()
    }

    fun loadTvList(): List<TvData> {
        val raw = prefs.getString("tvList", "[]") ?: "[]"
        val arr = JSONArray(raw)
        val list = mutableListOf<TvData>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(TvData(
                id = o.optString("id"),
                nama = o.optString("nama"),
                ip = o.optString("ip"),
                port = o.optInt("port", 5555),
                jenisPs = o.optString("jenisPs", "PS3"),
                paketAktif = o.optString("paketAktif"),
                sisaDetik = o.optLong("sisaDetik"),
                timerStart = o.optLong("timerStart"),
                timerDurasi = o.optLong("timerDurasi"),
                timerActive = o.optBoolean("timerActive"),
                bebas = o.optBoolean("bebas"),
                paketHarga = o.optInt("paketHarga"),
                totalPesanan = o.optInt("totalPesanan"),
                bebasMulai = o.optLong("bebasMulai"),
                bebasHargaPerJam = o.optInt("bebasHargaPerJam"),
                bebasPesananTotal = o.optInt("bebasPesananTotal"),
                paired = o.optBoolean("paired"),
                certPem = o.optString("certPem"),
                keyPem = o.optString("keyPem"),
                cancelBatas = o.optLong("cancelBatas"),
            ))
        }
        return list
    }

    // ── Transaksi ──────────────────────────────────────────
    fun saveTransaksi(list: List<Transaksi>) {
        val arr = JSONArray()
        list.forEach { t ->
            arr.put(JSONObject().apply {
                put("id", t.id)
                put("waktu", t.waktu)
                put("kasir", t.kasir)
                put("kota", t.kota)
                put("paket", t.paket)
                put("total", t.total)
                put("paketHarga", t.paketHarga)
                put("tvJenisPs", t.tvJenisPs)
                val pesananObj = JSONObject()
                t.pesanan.forEach { (k, v) -> pesananObj.put(k, v) }
                put("pesanan", pesananObj)
                val pesananHargaObj = JSONObject()
                t.pesananHarga.forEach { (k, v) -> pesananHargaObj.put(k, v) }
                put("pesananHarga", pesananHargaObj)
            })
        }
        prefs.edit().putString("transaksiList", arr.toString()).apply()
    }

    fun loadTransaksi(): List<Transaksi> {
        val raw = prefs.getString("transaksiList", "[]") ?: "[]"
        val arr = JSONArray(raw)
        val list = mutableListOf<Transaksi>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val pesananRaw = o.optJSONObject("pesanan") ?: JSONObject()
            val pesanan = mutableMapOf<String, Int>()
            pesananRaw.keys().forEach { k -> pesanan[k] = pesananRaw.optInt(k) }
            val pesananHargaRaw = o.optJSONObject("pesananHarga") ?: JSONObject()
            val pesananHarga = mutableMapOf<String, Int>()
            pesananHargaRaw.keys().forEach { k -> pesananHarga[k] = pesananHargaRaw.optInt(k) }
            list.add(Transaksi(
                id = o.optString("id"),
                waktu = o.optString("waktu"),
                kasir = o.optString("kasir"),
                kota = o.optString("kota"),
                paket = o.optString("paket"),
                pesanan = pesanan,
                total = o.optInt("total"),
                paketHarga = o.optInt("paketHarga"),
                pesananHarga = pesananHarga,
                tvJenisPs = o.optString("tvJenisPs"),
            ))
        }
        return list
    }

    // ── License ────────────────────────────────────────────
    fun saveLicense(status: LicenseStatus) {
        prefs.edit()
            .putString("licenseStatus", status.status)
            .putString("licensePesan", status.pesan)
            .putString("licenseExpires", status.expiresAt)
            .putInt("licenseMaxTv", status.maxTv)
            .apply()
    }

    fun loadLicense(): LicenseStatus = LicenseStatus(
        status = prefs.getString("licenseStatus", "") ?: "",
        pesan = prefs.getString("licensePesan", "") ?: "",
        expiresAt = prefs.getString("licenseExpires", "") ?: "",
        maxTv = prefs.getInt("licenseMaxTv", 0),
    )

    fun saveTrial(trialBatas: Long) {
        prefs.edit().putLong("trialBatas", trialBatas).apply()
    }

    fun loadTrialBatas(): Long = prefs.getLong("trialBatas", 0L)

    // ── Invoices ────────────────────────────────────────────
    fun saveInvoices(list: List<Invoice>) {
        val arr = JSONArray()
        list.forEach { inv ->
            arr.put(JSONObject().apply {
                put("id", inv.id)
                put("username", inv.username)
                put("email", inv.email)
                put("paket", inv.paket)
                put("harga", inv.harga)
                put("status", inv.status)
                put("dibuat", inv.dibuat)
                put("dibayar", inv.dibayar)
                put("confirmedBy", inv.confirmedBy)
                put("kodeLisensi", inv.kodeLisensi)
                put("buktiBase64", inv.buktiBase64)
            })
        }
        prefs.edit().putString("invoices", arr.toString()).apply()
    }

    fun loadInvoices(): List<Invoice> {
        val raw = prefs.getString("invoices", "[]") ?: "[]"
        val arr = JSONArray(raw)
        val list = mutableListOf<Invoice>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(Invoice(
                id = o.optString("id"),
                username = o.optString("username"),
                email = o.optString("email"),
                paket = o.optString("paket"),
                harga = o.optInt("harga"),
                status = o.optString("status", "PENDING"),
                dibuat = o.optLong("dibuat"),
                dibayar = o.optLong("dibayar"),
                confirmedBy = o.optString("confirmedBy"),
                kodeLisensi = o.optString("kodeLisensi"),
                buktiBase64 = o.optString("buktiBase64"),
            ))
        }
        return list
    }

    // ── SMTP Skip Permanently ──────────────────────────────
    fun saveSmtpSkipPermanently(skip: Boolean) {
        prefs.edit().putBoolean("smtpSkipPermanently", skip).apply()
    }

    fun loadSmtpSkipPermanently(): Boolean = prefs.getBoolean("smtpSkipPermanently", false)

    // ── SMTP ───────────────────────────────────────────────
    fun saveSmtp(cfg: SmtpConfig) {
        securePrefs.edit()
            .putString("smtpHost", cfg.host)
            .putInt("smtpPort", cfg.port)
            .putString("smtpUser", cfg.user)
            .putString("smtpPass", cfg.pass)
            .apply()
    }

    fun loadSmtp(): SmtpConfig = SmtpConfig(
        host = securePrefs.getString("smtpHost", "") ?: "",
        port = securePrefs.getInt("smtpPort", 587),
        user = securePrefs.getString("smtpUser", "") ?: "",
        pass = securePrefs.getString("smtpPass", "") ?: "",
    )

    // ── Promo ───────────────────────────────────────────────
    fun savePromo(promo: PromoSettings) {
        prefs.edit()
            .putBoolean("promoAktif", promo.promoAktif)
            .putString("diskonPerPaket", mapToJson(promo.diskonPerPaket.mapValues { it.value }))
            .putString("addTvOverride", mapToJson(promo.addTvOverride.mapValues { it.value }))
            .putString("promoUpdatedBy", promo.updatedBy)
            .putLong("promoUpdatedAt", promo.updatedAt)
            .putBoolean("newUserPromoActive", promo.newUserPromoActive)
            .putInt("newUserDiscountPercent", promo.newUserDiscountPercent)
            .putInt("newUserPromoDurationHours", promo.newUserPromoDurationHours)
            .putString("newUserDiskonPerPaket", mapToJson(promo.newUserDiskonPerPaket.mapValues { it.value }))
            .apply()
    }

    fun loadPromo(): PromoSettings {
        val rawDiskon = prefs.getString("diskonPerPaket", null)
        val rawTv = prefs.getString("addTvOverride", null)
        val rawNewDiskon = prefs.getString("newUserDiskonPerPaket", null)
        val diskonMap = rawDiskon?.let { jsonToMap(it) } ?: emptyMap()
        return PromoSettings(
            promoAktif = prefs.getBoolean("promoAktif", false),
            diskonPerPaket = diskonMap,
            addTvOverride = rawTv?.let { jsonToMap(it) } ?: emptyMap(),
            updatedBy = prefs.getString("promoUpdatedBy", "") ?: "",
            updatedAt = prefs.getLong("promoUpdatedAt", 0L),
            newUserPromoActive = prefs.getBoolean("newUserPromoActive", false),
            newUserDiscountPercent = prefs.getInt("newUserDiscountPercent", 30),
            newUserPromoDurationHours = prefs.getInt("newUserPromoDurationHours", 96),
            newUserDiskonPerPaket = rawNewDiskon?.let { jsonToMap(it) } ?: emptyMap(),
        )
    }

    // ── Notifications ───────────────────────────────────────
    fun saveNotifications(list: List<AppNotification>) {
        val arr = JSONArray()
        list.forEach { n ->
            arr.put(JSONObject().apply {
                put("id", n.id)
                put("title", n.title)
                put("body", n.body)
                put("type", n.type)
                put("sentAt", n.sentAt)
                put("read", n.read)
            })
        }
        prefs.edit().putString("appNotifications", arr.toString()).apply()
    }

    fun loadNotifications(): List<AppNotification> {
        val raw = prefs.getString("appNotifications", "[]") ?: "[]"
        val arr = JSONArray(raw)
        val list = mutableListOf<AppNotification>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(AppNotification(
                id = o.optString("id"),
                title = o.optString("title"),
                body = o.optString("body"),
                type = o.optString("type"),
                sentAt = o.optLong("sentAt"),
                read = o.optBoolean("read"),
            ))
        }
        return list
    }

    // ── Theme ───────────────────────────────────────────────
    fun saveThemeOption(name: String) {
        prefs.edit().putString("themeOption", name).apply()
    }

    fun loadThemeOption(): String = prefs.getString("themeOption", "GAMING_DARK") ?: "GAMING_DARK"

    // ── TV Password ─────────────────────────────────────────
    fun saveTvPassword(hash: String) {
        securePrefs.edit().putString("tvPasswordHash", hash).apply()
    }

    fun loadTvPassword(): String = securePrefs.getString("tvPasswordHash", "") ?: ""

    // ── Kode Generasi ───────────────────────────────────────
    fun saveKodeGenerasiList(list: List<KodeGenerasi>) {
        val arr = JSONArray()
        list.forEach { g ->
            arr.put(JSONObject().apply {
                put("waktu", g.waktu)
                put("username", g.username)
                put("paket", g.paket)
                put("kode", g.kode)
            })
        }
        prefs.edit().putString("kodeGenerasi", arr.toString()).apply()
    }

    fun loadKodeGenerasiList(): List<KodeGenerasi> {
        val raw = prefs.getString("kodeGenerasi", "[]") ?: "[]"
        val arr = JSONArray(raw)
        val list = mutableListOf<KodeGenerasi>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(KodeGenerasi(
                waktu = o.optString("waktu"),
                username = o.optString("username"),
                paket = o.optString("paket"),
                kode = o.optString("kode"),
            ))
        }
        return list
    }

    // ── JSON Helpers ───────────────────────────────────────
    private fun mapToJson(map: Map<String, Int>?): String {
        val obj = JSONObject()
        map?.forEach { (k, v) -> obj.put(k, v) }
        return obj.toString()
    }

    private fun jsonToMap(raw: String?): Map<String, Int> {
        if (raw.isNullOrBlank()) return emptyMap()
        val obj = JSONObject(raw)
        val map = mutableMapOf<String, Int>()
        obj.keys().forEach { k -> map[k] = obj.optInt(k) }
        return map
    }

    // ── Login Method ────────────────────────────────────────
    fun saveLoginMethod(method: String) {
        prefs.edit().putString("loginMethod", method).apply()
    }

    fun loadLoginMethod(): String = prefs.getString("loginMethod", "password") ?: "password"

    // ── FCM Token ───────────────────────────────────────────
    fun saveFcmToken(token: String) {
        prefs.edit().putString("fcmToken", token).apply()
    }

    fun loadFcmToken(): String = prefs.getString("fcmToken", "") ?: ""

    // ── Notification Dialog Flag ────────────────────────────
    fun saveNotifDialogShown(shown: Boolean) {
        prefs.edit().putBoolean("notifDialogShown", shown).apply()
    }

    fun loadNotifDialogShown(): Boolean = prefs.getBoolean("notifDialogShown", false)

    // ── Secure Preferences (for ECDSA, etc.) ──────────────
    fun getSecurePreference(key: String): String? = securePrefs.getString(key, null)

    fun putSecurePreference(key: String, value: String) {
        securePrefs.edit().putString(key, value).apply()
    }

    // ── Clear all app data (for Google account switch) ────
    fun clearAllAppData() {
        val editor = prefs.edit()
        // Keep: themeOption, loginMethod, fcmToken, notifDialogShown, smtpSkipPermanently
        // Keep: printerAddress, printerName (device-level)
        // Keep: ECDSA keys, TV password (secure prefs, cleared separately if needed)
        val keepTheme = prefs.getString("themeOption", "GAMING_DARK")
        val keepLoginMethod = prefs.getString("loginMethod", "password")
        val keepFcm = prefs.getString("fcmToken", "")
        val keepNotifDialog = prefs.getBoolean("notifDialogShown", false)
        val keepSmtpSkip = prefs.getBoolean("smtpSkipPermanently", false)
        editor.clear()
        // Restore kept values
        keepTheme?.let { editor.putString("themeOption", it) }
        editor.putString("loginMethod", keepLoginMethod)
        editor.putString("fcmToken", keepFcm)
        editor.putBoolean("notifDialogShown", keepNotifDialog)
        editor.putBoolean("smtpSkipPermanently", keepSmtpSkip)
        editor.apply()
    }

    // ── Activity Log ──────────────────────────────────────
    fun saveActivityLog(list: List<String>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        prefs.edit().putString("activityLog", arr.toString()).apply()
    }

    fun loadActivityLog(): List<String> {
        val raw = prefs.getString("activityLog", "[]") ?: "[]"
        val arr = JSONArray(raw)
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) list.add(arr.optString(i))
        return list
    }

    private fun map2dToJson(map: Map<String, Map<String, Int>>?): String {
        val outer = JSONObject()
        map?.forEach { (k, inner) ->
            outer.put(k, JSONObject().apply { inner.forEach { (ik, iv) -> put(ik, iv) } })
        }
        return outer.toString()
    }

    private fun jsonToMap2d(raw: String?): Map<String, Map<String, Int>> {
        if (raw.isNullOrBlank()) return emptyMap()
        val outer = JSONObject(raw)
        val result = mutableMapOf<String, Map<String, Int>>()
        outer.keys().forEach { k ->
            val inner = outer.getJSONObject(k)
            val m = mutableMapOf<String, Int>()
            inner.keys().forEach { ik -> m[ik] = inner.optInt(ik) }
            result[k] = m
        }
        return result
    }
}
