package com.billingps.aptv.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.billingps.aptv.models.*
import org.json.JSONArray
import org.json.JSONObject

object StorageUtil {
    private const val PREFS_NAME = "billingps_data"
    private const val PREFS_SENSITIVE = "billingps_sensitive"
    private lateinit var prefs: SharedPreferences
    private lateinit var securePrefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            securePrefs = EncryptedSharedPreferences.create(
                context,
                PREFS_SENSITIVE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (_: Exception) {
            securePrefs = context.getSharedPreferences(PREFS_SENSITIVE, Context.MODE_PRIVATE)
        }
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
                val pesananObj = JSONObject()
                t.pesanan.forEach { (k, v) -> pesananObj.put(k, v) }
                put("pesanan", pesananObj)
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
            list.add(Transaksi(
                id = o.optString("id"),
                waktu = o.optString("waktu"),
                kasir = o.optString("kasir"),
                kota = o.optString("kota"),
                paket = o.optString("paket"),
                pesanan = pesanan,
                total = o.optInt("total"),
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
