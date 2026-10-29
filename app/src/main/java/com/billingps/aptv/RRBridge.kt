package com.billingps.aptv

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.billingps.aptv.models.AppUiState
import com.billingps.aptv.models.Invoice
import com.billingps.aptv.models.LicenseCatalog
import com.billingps.aptv.models.LicenseRecord
import com.billingps.aptv.models.MainViewModel
import com.billingps.aptv.models.PrayerConfig
import com.billingps.aptv.models.Transaksi
import com.billingps.aptv.models.TvData
import com.billingps.aptv.models.UserData
import com.billingps.aptv.models.fnbTotal
import com.billingps.aptv.models.isPaketTx
import com.billingps.aptv.models.paketTotal
import com.billingps.aptv.tv.TvConnection
import com.billingps.aptv.tv.TvHubManager
import com.billingps.aptv.ui.theme.BackgroundOption
import com.billingps.aptv.ui.theme.ThemeOption
import com.billingps.aptv.utils.PrayerTimes
import com.billingps.aptv.utils.ThermalPrinter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RRBridge(
    private val activity: MainActivity,
    private val mainViewModel: MainViewModel,
    private val tvViewModel: TvViewModel,
    private val onInstallClientApk: (TvData) -> Unit = {},
) {
    private var webView: WebView? = null
    private var observing = false
    private var sequence = 0
    private var lastState = ""
    private var pendingPairTvId: String? = null
    private var pendingPairIp: String = ""
    private var pendingPairNewName: String = ""
    private var pendingPairNewJenis: String = "PS3"

    fun attach(view: WebView) {
        webView = view
        startObserving()
    }

    fun onPageFinished() {
        emitState(force = true)
    }

    fun destroy() {
        webView = null
    }

    @SuppressLint("JavascriptInterface")
    @JavascriptInterface
    fun postMessage(raw: String) {
        activity.runOnUiThread {
            try {
                val message = JSONObject(raw)
                when (message.optString("type")) {
                    "ready" -> emitState(force = true)
                    "command" -> {
                        val id = message.optString("id")
                        val name = message.optString("name")
                        val payload = message.optJSONObject("payload") ?: JSONObject()
                        dispatch(id, name, payload)
                    }
                }
            } catch (e: Exception) {
                Log.e("RRBridge", "postMessage gagal: ${e.message}", e)
                sendResult("", false, error = e.message ?: "Pesan tidak valid")
            }
        }
    }

    private fun startObserving() {
        if (observing) return
        observing = true

        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(mainViewModel.state, mainViewModel.tvConnections) { state, connections ->
                    state to connections
                }.collect {
                    emitState()
                }
            }
        }

        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                tvViewModel.uiState.collect { pairing ->
                    if (!pairing.isPaired) return@collect
                    val tvId = pendingPairTvId
                    val newName = pendingPairNewName
                    if (tvId == null && newName.isBlank()) return@collect

                    val prefs = activity.getSharedPreferences("tv_certs", Context.MODE_PRIVATE)
                    val cert = prefs.getString("cert_pem", "") ?: ""
                    val key = prefs.getString("key_pem", "") ?: ""
                    if (tvId != null) {
                        mainViewModel.updateTV(
                            tvId,
                            mapOf(
                                "paired" to true,
                                "ip" to pendingPairIp,
                                "port" to 6466,
                                "certPem" to cert,
                                "keyPem" to key,
                            ),
                        )
                        sendEvent(
                            "tv.pair.result",
                            JSONObject().put("ok", true).put("tvId", tvId),
                        )
                    } else {
                        val newId = "tv_${System.currentTimeMillis()}"
                        val added = mainViewModel.tambahTV(
                            TvData(
                                id = newId,
                                nama = newName,
                                ip = pendingPairIp,
                                port = 6466,
                                jenisPs = pendingPairNewJenis.ifBlank { "PS3" },
                                paired = true,
                                certPem = cert,
                                keyPem = key,
                            ),
                        )
                        val event = JSONObject()
                            .put("ok", added)
                            .put("tvId", if (added) newId else "")
                        if (!added) {
                            event.put(
                                "error",
                                "TV berhasil dipairing, tetapi gagal ditambahkan. Periksa lisensi atau batas TV.",
                            )
                        }
                        sendEvent("tv.pair.result", event)
                    }
                    pendingPairTvId = null
                    pendingPairIp = ""
                    pendingPairNewName = ""
                    pendingPairNewJenis = "PS3"
                    emitState(force = true)
                }
            }
        }
    }

    private fun emitState(force: Boolean = false) {
        val payload = buildState()
        val json = payload.toString()
        if (!force && json == lastState) return
        lastState = json
        post(
            JSONObject()
                .put("v", 1)
                .put("type", "state")
                .put("seq", ++sequence)
                .put("payload", payload),
        )
    }

    private fun post(message: JSONObject) {
        val view = webView ?: return
        val script = "window.RRBridge&&window.RRBridge.onMessage(${JSONObject.quote(message.toString())})"
        activity.runOnUiThread {
            try {
                view.evaluateJavascript(script, null)
            } catch (e: Exception) {
                Log.w("RRBridge", "evaluateJavascript gagal: ${e.message}")
            }
        }
    }

    private fun sendResult(id: String, ok: Boolean, data: Any? = null, error: String? = null) {
        val result = JSONObject()
            .put("v", 1)
            .put("type", "result")
            .put("id", id)
            .put("ok", ok)
        if (data != null) result.put("data", data)
        if (!error.isNullOrBlank()) result.put("error", error)
        post(result)
    }

    private fun sendEvent(name: String, payload: JSONObject) {
        post(
            JSONObject()
                .put("v", 1)
                .put("type", "event")
                .put("name", name)
                .put("payload", payload),
        )
    }

    private fun dispatch(id: String, name: String, payload: JSONObject) {
        try {
            when (name) {
                "app.state" -> {
                    emitState(force = true)
                    sendResult(id, true)
                }

                "auth.logout" -> {
                    mainViewModel.logout(payload.optBoolean("clearGoogleSignIn", false))
                    sendResult(id, true)
                }

                "tv.add" -> {
                    val tvName = payload.optString("nama").trim()
                    if (tvName.isBlank()) {
                        sendResult(id, false, error = "Nama TV wajib diisi")
                        return
                    }
                    val jenis = payload.optString("jenisPs", "PS3").ifBlank { "PS3" }
                    val tv = TvData(
                        id = "tv_${System.currentTimeMillis()}",
                        nama = tvName,
                        jenisPs = jenis,
                    )
                    if (mainViewModel.tambahTV(tv)) {
                        emitState(force = true)
                        sendResult(id, true, JSONObject().put("id", tv.id))
                    } else {
                        sendResult(id, false, error = "TV tidak bisa ditambahkan. Periksa lisensi atau batas TV.")
                    }
                }

                "tv.update" -> {
                    val tvId = payload.optString("id")
                    val fields = payload.optJSONObject("fields") ?: JSONObject()
                    mainViewModel.updateTV(tvId, jsonToAnyMap(fields))
                    sendResult(id, true)
                }

                "tv.delete" -> {
                    val tvId = payload.optString("id")
                    val password = payload.optString("password")
                    val state = mainViewModel.state.value
                    if (state.tvPasswordHash.isBlank()) {
                        if (password.length < 4) {
                            sendResult(id, false, error = "Buat password TV minimal 4 karakter")
                            return
                        }
                        mainViewModel.setTvPassword(password)
                    } else if (!mainViewModel.verifyTvPassword(password)) {
                        sendResult(id, false, error = "Password TV salah")
                        return
                    }
                    mainViewModel.hapusTV(tvId)
                    sendResult(id, true)
                }

                "tv.password.set" -> {
                    mainViewModel.setTvPassword(payload.optString("password"))
                    sendResult(id, true)
                }

                "tv.pair.start" -> {
                    val tvId = payload.optString("id").trim()
                    val ip = payload.optString("ip").trim()
                    val newName = payload.optString("nama").trim()
                    val newJenis = payload.optString("jenisPs", "PS3").ifBlank { "PS3" }
                    if (ip.isBlank()) {
                        sendResult(id, false, error = "IP TV wajib diisi")
                        return
                    }
                    if (tvId.isBlank()) {
                        if (newName.isBlank()) {
                            sendResult(id, false, error = "Nama TV wajib diisi")
                            return
                        }
                        if (!mainViewModel.canAddTV(showPopup = true)) {
                            sendResult(
                                id,
                                false,
                                error = "TV tidak bisa ditambahkan. Periksa lisensi atau batas TV.",
                            )
                            return
                        }
                    }
                    pendingPairTvId = tvId.ifBlank { null }
                    pendingPairNewName = if (tvId.isBlank()) newName else ""
                    pendingPairNewJenis = if (tvId.isBlank()) newJenis else "PS3"
                    pendingPairIp = ip
                    tvViewModel.updateIp(ip)
                    tvViewModel.startPairing()
                    sendResult(id, true)
                }

                "tv.pair.complete" -> {
                    val pin = payload.optString("pin").trim()
                    if (pin.isBlank()) {
                        sendResult(id, false, error = "PIN wajib diisi")
                        return
                    }
                    tvViewModel.updatePin(pin)
                    tvViewModel.completePairing()
                    sendResult(id, true)
                }

                "tv.start" -> sendResult(id, mainViewModel.tvStart(payload.optString("id")))
                "tv.pauseAll" -> {
                    val changed = mainViewModel.pauseAllSessions()
                    sendResult(id, true, JSONObject().put("changed", changed))
                }
                "tv.resumeAll" -> {
                    val changed = mainViewModel.resumeAllSessions()
                    sendResult(id, true, JSONObject().put("changed", changed))
                }
                "tv.resume" -> {
                    val resumed = mainViewModel.resumeSession(payload.optString("id"))
                    sendResult(
                        id,
                        resumed,
                        error = if (resumed) null else "Sesi tidak bisa dilanjutkan",
                    )
                }
                "tv.stop" -> sendResult(
                    id,
                    mainViewModel.tvStop(
                        payload.optString("id"),
                        payload.optBoolean("sleep", false),
                    ),
                )
                "tv.cancel" -> {
                    val result = mainViewModel.cancelSession(payload.optString("id"))
                    sendResult(id, result.success, error = if (result.success) null else result.message)
                }
                "tv.sync" -> sendResult(id, mainViewModel.tvSync(payload.optString("id")))
                "tv.lock" -> sendResult(id, mainViewModel.tvLock(payload.optString("id")))
                "tv.unlock" -> sendResult(id, mainViewModel.tvUnlock(payload.optString("id")))
                "tv.message" -> sendResult(
                    id,
                    mainViewModel.tvMessage(
                        payload.optString("id"),
                        payload.optString("pesan"),
                        payload.optInt("durasi", 5),
                    ),
                )
                "client.apk.install" -> {
                    if (!mainViewModel.isClientFeatureEnabled()) {
                        sendResult(id, false, error = "Fitur Client APK tidak aktif pada paket ini.")
                        return
                    }
                    val tv = mainViewModel.state.value.tvList.firstOrNull {
                        it.id == payload.optString("id")
                    }
                    if (tv == null) {
                        sendResult(id, false, error = "TV tidak ditemukan.")
                        return
                    }
                    if (tv.ip.isBlank()) {
                        sendResult(id, false, error = "IP TV belum diatur.")
                        return
                    }
                    onInstallClientApk(tv)
                    sendResult(id, true)
                }
                "prayer.get" -> sendResult(id, true, prayerJson())

                "prayer.save" -> {
                    val current = mainViewModel.state.value.prayerConfig
                    val config = PrayerConfig(
                        enabled = payload.optBoolean("enabled", current.enabled),
                        message = payload.optString("message", current.message),
                        durationSec = payload.optInt("durationSec", current.durationSec),
                        latitude = payload.optDouble("latitude", current.latitude),
                        longitude = payload.optDouble("longitude", current.longitude),
                        locationSet = payload.optBoolean("locationSet", current.locationSet),
                        locationLabel = payload.optString("locationLabel", current.locationLabel),
                    )
                    mainViewModel.savePrayerConfig(config)
                    sendResult(id, true, prayerJson())
                }

                "prayer.detect" -> activity.requestLocationPermissions { granted ->
                    if (!granted) {
                        sendResult(id, false, error = "Izin lokasi ditolak")
                        return@requestLocationPermissions
                    }
                    mainViewModel.detectPrayerLocation { ok, message ->
                        if (ok) {
                            sendResult(
                                id,
                                true,
                                prayerJson().put("message", message),
                            )
                        } else {
                            sendResult(id, false, error = message)
                        }
                    }
                }

                "prayer.test" -> {
                    val count = mainViewModel.testPrayerPopup(
                        payload.optString("message"),
                        payload.optInt(
                            "durationSec",
                            mainViewModel.state.value.prayerConfig.durationSec,
                        ),
                    )
                    sendResult(
                        id,
                        count > 0,
                        data = JSONObject().put("count", count),
                        error = if (count > 0) null else "Tidak ada TV yang terhubung",
                    )
                }

                "tv.sleep" -> {
                    val tv = findTv(payload.optString("id"))
                    if (tv == null) {
                        sendResult(id, false, error = "TV tidak ditemukan")
                        return
                    }
                    if (tv.certPem.isBlank() || tv.keyPem.isBlank()) {
                        sendResult(id, false, error = "TV belum dipairing")
                        return
                    }
                    tvViewModel.sendPower(tv.ip, tv.certPem, tv.keyPem, false) { result ->
                        sendResult(
                            id,
                            result.ok,
                            data = JSONObject()
                                .put("confirmed", result.confirmed)
                                .put("message", result.message),
                            error = if (result.ok) null else result.message,
                        )
                    }
                }

                "tv.config.save" -> {
                    val tvId = payload.optString("id")
                    val config = payload.optJSONObject("config") ?: JSONObject()
                    mainViewModel.updateTV(tvId, jsonToAnyMap(config))
                    sendResult(id, mainViewModel.tvApplyConfig(tvId))
                }

                "tv.remote" -> {
                    val tv = findTv(payload.optString("id"))
                    if (tv == null) {
                        sendResult(id, false, error = "TV tidak ditemukan")
                        return
                    }
                    if (tv.certPem.isBlank() || tv.keyPem.isBlank()) {
                        sendResult(id, false, error = "TV belum dipairing")
                        return
                    }
                    tvViewModel.sendKeyCommand(
                        tv.ip,
                        tv.certPem,
                        tv.keyPem,
                        payload.optString("key"),
                        payload.optString("label", payload.optString("key")),
                    )
                    sendResult(id, true)
                }

                "tv.volume" -> {
                    val tv = findTv(payload.optString("id"))
                    if (tv == null || tv.certPem.isBlank() || tv.keyPem.isBlank()) {
                        sendResult(id, false, error = "TV belum dipairing")
                        return
                    }
                    if (payload.optBoolean("up", true)) {
                        tvViewModel.sendVolumeUp(tv.ip, tv.certPem, tv.keyPem)
                    } else {
                        tvViewModel.sendVolumeDown(tv.ip, tv.certPem, tv.keyPem)
                    }
                    sendResult(id, true)
                }

                "tv.power" -> {
                    val tv = findTv(payload.optString("id"))
                    if (tv == null) {
                        sendResult(id, false, error = "TV tidak ditemukan")
                        return
                    }
                    val requested = when {
                        payload.has("on") -> payload.optBoolean("on")
                        payload.optString("action").equals("on", ignoreCase = true) -> true
                        payload.optString("action").equals("off", ignoreCase = true) -> false
                        else -> null
                    }
                    // Jangan tentukan target dari screenOn hub karena bisa basi.
                    // Tanpa "on" eksplisit, Python membaca state asli TV lalu
                    // mengirim SLEEP/WAKEUP yang idempoten.
                    val targetOn = requested

                    if (tv.certPem.isBlank() || tv.keyPem.isBlank()) {
                        sendResult(id, false, error = "TV belum dipairing")
                        return
                    }
                    tvViewModel.sendPower(tv.ip, tv.certPem, tv.keyPem, targetOn) { result ->
                        val nowOn = result.isOn
                            ?: TvHubManager.connectionFor(tv)?.screenOn
                            ?: targetOn
                        if (result.ok) mainViewModel.cancelAutoSleep(tv.id)
                        if (result.ok && nowOn == true) {
                            mainViewModel.playCachedMediaOnTv(mainViewModel.state.value.currentUser, tv.id)
                        }
                        val reportedOn: Any =
                            if (result.ok && nowOn != null) nowOn else JSONObject.NULL
                        sendResult(
                            id,
                            result.ok,
                            data = JSONObject()
                                .put("confirmed", result.confirmed)
                                .put("on", reportedOn)
                                .put("message", result.message),
                            error = if (result.ok) null else result.message,
                        )
                    }
                }

                "tv.media.upload" -> {
                    val tvId = payload.optString("id")
                    val kind = payload.optString("kind", "promo")
                    activity.pickDocument(arrayOf("video/*", "image/*")) { uri ->
                        if (uri == null) {
                            sendResult(id, false, error = "Pemilihan file dibatalkan")
                            return@pickDocument
                        }
                        mainViewModel.uploadTvMedia(tvId, kind, uri) { ok, message ->
                            sendResult(id, ok, error = if (ok) null else message)
                            emitState(force = true)
                        }
                    }
                }

                "tv.media.playCached" -> {
                    mainViewModel.playCachedMediaOnTv(
                        mainViewModel.state.value.currentUser,
                        payload.optString("id"),
                    )
                    sendResult(id, true)
                }

                "tv.move" -> {
                    mainViewModel.pindahTV(payload.optString("sourceId"), payload.optString("targetId"))
                    sendResult(id, true)
                }

                "tv.dismissExpired" -> {
                    mainViewModel.dismissWaktuHabis(payload.optString("id"))
                    sendResult(id, true)
                }

                "trx.add" -> {
                    val base = Transaksi(
                        id = payload.optString("id"),
                        waktu = payload.optString("waktu"),
                        kasir = payload.optString("kasir"),
                        kota = payload.optString("kota"),
                        paket = payload.optString("paket"),
                        pesanan = jsonToIntMap(payload.optJSONObject("pesanan")),
                        total = payload.optInt("total"),
                        paketHarga = payload.optInt("paketHarga"),
                        pesananHarga = jsonToIntMap(payload.optJSONObject("pesananHarga")),
                        tvJenisPs = payload.optString("tvJenisPs"),
                        sudahBayar = payload.optBoolean("sudahBayar", false),
                        metode = payload.optString("metode"),
                        cash = payload.optInt("cash"),
                        kembalian = payload.optInt("kembalian"),
                    ).withSession(payload)
                    val transaction = if (base.kategori.isBlank()) {
                        base.copy(
                            kategori = when {
                                base.paketHarga > 0 && base.pesanan.isEmpty() -> "PAKET"
                                base.paketHarga <= 0 && base.pesanan.isNotEmpty() -> "FNB"
                                else -> ""
                            },
                        )
                    } else base
                    mainViewModel.tambahTransaksi(transaction)
                    sendResult(id, true)
                }

                "trx.upsert" -> {
                    val existing = mainViewModel.state.value.transaksiList.firstOrNull {
                        it.id == payload.optString("id")
                    }
                    val base = Transaksi(
                        id = payload.optString("id"),
                        waktu = payload.optString("waktu"),
                        kasir = payload.optString("kasir"),
                        kota = payload.optString("kota"),
                        paket = payload.optString("paket"),
                        pesanan = jsonToIntMap(payload.optJSONObject("pesanan")),
                        total = payload.optInt("total"),
                        paketHarga = payload.optInt("paketHarga"),
                        pesananHarga = jsonToIntMap(payload.optJSONObject("pesananHarga")),
                        tvJenisPs = payload.optString("tvJenisPs"),
                        sudahBayar = payload.optBoolean("sudahBayar", false),
                        metode = payload.optString("metode"),
                        cash = payload.optInt("cash"),
                        kembalian = payload.optInt("kembalian"),
                    ).withSession(payload, existing)
                    val tv = tvForPayload(payload)
                    val sessionTxs = mainViewModel.state.value.transaksiList.filter {
                        it.id == existing?.id ||
                            (base.sesiMulai > 0L && it.sesiMulai == base.sesiMulai &&
                                (base.tvId.isBlank() || it.tvId == base.tvId || it.kota == base.kota))
                    }
                    val sessionPaketTotal = sessionTxs.filter { it.isPaketTx() }.sumOf { it.paketHarga }
                    val addAmount = (base.paketHarga - sessionPaketTotal).coerceAtLeast(0)
                    val canSplit = existing != null && tv != null && tv.timerActive && !tv.bebas &&
                        existing.paketHarga > 0 && !existing.paket.contains("Bebas", ignoreCase = true) &&
                        base.paketHarga > 0 && base.paket != "Pesanan" && base.paket != "Main Bebas" &&
                        base.sesiMulai > 0L && addAmount > 0
                    if (canSplit) {
                        val anchor = existing!!
                        val original = anchor.copy(
                            total = (base.total - addAmount).coerceAtLeast(0),
                            paketHarga = anchor.paketHarga,
                            pesanan = base.pesanan,
                            pesananHarga = base.pesananHarga,
                            sudahBayar = if (base.sudahBayar) true else anchor.sudahBayar,
                            metode = if (base.sudahBayar) base.metode else anchor.metode,
                            kasir = base.kasir.ifBlank { anchor.kasir },
                            tvId = anchor.tvId.ifBlank { base.tvId },
                            sesiMulai = anchor.sesiMulai.takeIf { it > 0L } ?: base.sesiMulai,
                            sesiSelesai = anchor.sesiSelesai,
                            waktu = anchor.waktu.ifBlank { base.waktu },
                        )
                        val addTx = base.copy(
                            id = "${anchor.id}-ADD-${System.currentTimeMillis()}",
                            paket = "Tambah Waktu: ${base.paket}",
                            total = addAmount,
                            paketHarga = addAmount,
                            pesanan = emptyMap(),
                            pesananHarga = emptyMap(),
                            kategori = "PAKET",
                            jumlah = 1,
                            sesiMulai = original.sesiMulai,
                            sesiSelesai = 0L,
                            cash = 0,
                            kembalian = 0,
                        )
                        mainViewModel.upsertTransaksi(original)
                        mainViewModel.tambahTransaksi(addTx)
                    } else {
                        mainViewModel.upsertTransaksi(base)
                    }
                    sendResult(id, true)
                }

                "trx.setPaid" -> {
                    mainViewModel.setTransaksiSudahBayar(
                        payload.optString("kota"),
                        payload.optBoolean("sudahBayar", true),
                        payload.optString("metode"),
                        payload.optString("id"),
                    )
                    sendResult(id, true)
                }

                "trx.edit" -> {
                    val state = mainViewModel.state.value
                    val existing = state.transaksiList.firstOrNull { it.id == payload.optString("id") }
                    if (existing == null) {
                        sendResult(id, false, error = "Transaksi tidak ditemukan")
                    } else if (state.currentRole == "kasir" && existing.kasir != state.currentUser) {
                        sendResult(id, false, error = "Hanya laporan Anda yang dapat diedit")
                    } else {
                        val pesananBaru = if (payload.has("pesanan")) jsonToIntMap(payload.optJSONObject("pesanan")) else existing.pesanan
                        val pesananHargaBaru = if (payload.has("pesananHarga")) jsonToIntMap(payload.optJSONObject("pesananHarga")) else existing.pesananHarga
                        val notEnough = mutableListOf<String>()
                        pesananBaru.forEach { (name, qty) ->
                            val diff = qty - (existing.pesanan[name] ?: 0)
                            if (diff > 0) {
                                val available = when {
                                    state.stokMakanan.containsKey(name) -> state.stokMakanan[name] ?: 0
                                    state.stokMinuman.containsKey(name) -> state.stokMinuman[name] ?: 0
                                    else -> Int.MAX_VALUE
                                }
                                if (diff > available) notEnough.add(name)
                            }
                        }
                        if (notEnough.isNotEmpty()) {
                            sendResult(id, false, error = "Stok tidak cukup untuk: ${notEnough.joinToString(", ")}")
                        } else {
                            val totalBaru = payload.optInt("total", existing.total)
                            val updated = existing.copy(
                                paket = payload.optString("paket", existing.paket),
                                paketHarga = payload.optInt("paketHarga", existing.paketHarga),
                                pesanan = pesananBaru,
                                pesananHarga = pesananHargaBaru,
                                total = totalBaru,
                                metode = if (payload.has("metode")) payload.optString("metode") else existing.metode,
                                sudahBayar = if (payload.has("sudahBayar")) payload.optBoolean("sudahBayar", existing.sudahBayar) else existing.sudahBayar,
                                cash = payload.optInt("cash", existing.cash),
                                kembalian = payload.optInt("kembalian", existing.kembalian),
                            )
                            mainViewModel.upsertTransaksi(updated)
                            sendResult(id, true)
                        }
                    }
                }

                "trx.settle" -> {
                    mainViewModel.settleTransaksi(
                        kota = payload.optString("kota"),
                        total = payload.optInt("total"),
                        paketHarga = payload.optInt("paketHarga"),
                        metode = payload.optString("metode", "Tunai"),
                        sudahBayar = payload.optBoolean("sudahBayar", true),
                        id = payload.optString("id"),
                        tvId = payload.optString("tvId"),
                        pesanan = payload.optJSONObject("pesanan")?.let { jsonToIntMap(it) },
                        pesananHarga = payload.optJSONObject("pesananHarga")?.let { jsonToIntMap(it) },
                        cash = payload.optInt("cash"),
                        kembalian = payload.optInt("kembalian"),
                    )
                    sendResult(id, true)
                }

                "trx.clear" -> {
                    mainViewModel.bersihkanTransaksi()
                    sendResult(id, true)
                }

                "trx.print" -> {
                    val transaction = mainViewModel.state.value.transaksiList.firstOrNull {
                        it.id == payload.optString("id")
                    }
                    val tv = mainViewModel.state.value.tvList.firstOrNull {
                        it.nama == transaction?.kota || it.id == payload.optString("tvId")
                    }
                    if (transaction == null) {
                        sendResult(id, false, error = "Transaksi tidak ditemukan")
                        return
                    }
                    if (!com.billingps.aptv.utils.ThermalPrinter.isConnected()) {
                        sendResult(id, false, error = "Printer belum terhubung")
                        return
                    }
                    mainViewModel.printReceipt(transaction, tv ?: TvData())
                    sendResult(id, true)
                }

                "price.save" -> {
                    mainViewModel.saveHarga(
                        jsonToNestedIntMap(payload.optJSONObject("paketMain")),
                        jsonToIntMap(payload.optJSONObject("paketDurasi")),
                        jsonToIntMap(payload.optJSONObject("menuMakanan")),
                        jsonToIntMap(payload.optJSONObject("menuMinuman")),
                    )
                    sendResult(id, true)
                }

                "stok.save" -> {
                    mainViewModel.saveStok(
                        jsonToIntMap(payload.optJSONObject("makanan")),
                        jsonToIntMap(payload.optJSONObject("minuman")),
                    )
                    sendResult(id, true)
                }

                "fnb.sale" -> {
                    val result = mainViewModel.jualFnb(
                        jsonToIntMap(payload.optJSONObject("itemsMakanan")),
                        jsonToIntMap(payload.optJSONObject("itemsMinuman")),
                        payload.optString("metode", "Tunai"),
                        payload.optInt("cash", 0),
                        payload.optInt("kembalian", 0),
                    )
                    val data = if (result.success) {
                        val tx = mainViewModel.state.value.transaksiList.firstOrNull {
                            it.id == result.transactionId
                        }
                        JSONObject()
                            .put("id", result.transactionId)
                            .put("tvNama", "KASIR F&B")
                            .put("tvJenisPs", "")
                            .put("paket", tx?.paket ?: "F&B")
                            .put("total", tx?.total ?: 0)
                            .put("paketHarga", tx?.paketHarga ?: 0)
                            .put("pesanan", intMapToJson(tx?.pesanan ?: emptyMap()))
                            .put("pesananHarga", intMapToJson(tx?.pesananHarga ?: emptyMap()))
                            .put("metode", tx?.metode ?: "Tunai")
                            .put("cash", tx?.cash ?: result.cash)
                            .put("kembalian", tx?.kembalian ?: result.kembalian)
                            .put("kasir", tx?.kasir ?: "")
                            .put("waktu", tx?.waktu ?: "")
                    } else {
                        null
                    }
                    sendResult(
                        id,
                        result.success,
                        data = data,
                        error = if (result.success) null else result.message,
                    )
                }

                "group.add" -> {
                    mainViewModel.tambahGrup(payload.optString("nama"))
                    sendResult(id, true)
                }

                "group.rename" -> {
                    mainViewModel.renameGrup(payload.optString("lama"), payload.optString("baru"))
                    sendResult(id, true)
                }

                "group.delete" -> {
                    mainViewModel.hapusGrup(payload.optString("nama"))
                    sendResult(id, true)
                }

                "invoice.create" -> {
                    val paket = payload.optString("paket")
                    val plan = LicenseCatalog.find(paket)
                    if (plan == null) {
                        sendResult(id, false, error = "Paket lisensi tidak dikenal")
                        return
                    }
                    val jumlahTv = payload.optInt("jumlahTv", 1).coerceIn(1, 99)
                    val mode = LicenseCatalog.normalizeMode(payload.optString("mode"))
                    val current = mainViewModel.state.value
                    if (mode != LicenseCatalog.MODE_NEW && current.licenseStatus.status != "active") {
                        sendResult(id, false, error = "Lisensi aktif diperlukan untuk perpanjangan atau tambah TV")
                        return
                    }
                    val invoice = mainViewModel.buatInvoice(
                        paket,
                        plan.unitPrice * jumlahTv,
                        jumlahTv = jumlahTv,
                        mode = mode,
                    )
                    sendResult(id, true, invoiceJson(invoice))
                }

                "invoice.uploadProof" -> {
                    val invoiceId = payload.optString("invoiceId")
                    activity.pickDocument(arrayOf("image/*")) { uri ->
                        if (uri == null) {
                            sendResult(id, false, error = "Pemilihan gambar dibatalkan")
                            return@pickDocument
                        }
                        val base64 = compressImageToBase64(uri)
                        if (base64 == null) {
                            sendResult(id, false, error = "Gambar tidak bisa dibaca")
                            return@pickDocument
                        }
                        mainViewModel.uploadBukti(invoiceId, base64)
                        sendResult(id, true)
                    }
                }

                "license.refresh" -> {
                    mainViewModel.getActiveLicenses { records ->
                        sendResult(id, true, licenseRecordsJson(records))
                    }
                }

                "license.revoke" -> {
                    mainViewModel.revokeLicense(
                        payload.optString("docId"),
                        payload.optString("licenseUsername"),
                    ) { ok ->
                        sendResult(id, ok, error = if (ok) null else "Gagal mencabut lisensi")
                    }
                }

                "license.activate" -> {
                    mainViewModel.aktivasiLisensi(payload.optString("kode")) { ok, message ->
                        sendResult(id, ok, error = if (ok) null else message)
                    }
                }

                "license.confirmPayment" -> {
                    sendResult(
                        id,
                        false,
                        error = "Konfirmasi pembayaran hanya dapat dilakukan melalui RR License Generator.",
                    )
                }

                "history.sync" -> {
                    mainViewModel.syncToCloud()
                    sendResult(id, true)
                }

                "history.import" -> {
                    mainViewModel.importFromCloud()
                    sendResult(id, true)
                }

                "history.export" -> {
                    val rows = buildCsv(
                        payload.optString("kasir"),
                        payload.optString("from"),
                        payload.optString("to"),
                    )
                    activity.createDocument(
                        "riwayat_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())}.csv",
                    ) { uri ->
                        if (uri == null) {
                            sendResult(id, false, error = "Penyimpanan dibatalkan")
                            return@createDocument
                        }
                        activity.lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                activity.contentResolver.openOutputStream(uri)?.use { output ->
                                    output.write(rows.toByteArray(Charsets.UTF_8))
                                }
                                sendResult(id, true)
                            } catch (e: Exception) {
                                sendResult(id, false, error = e.message ?: "Gagal menyimpan CSV")
                            }
                        }
                    }
                }

                "history.printReport" -> sendResult(id, false, error = "Cetak laporan belum tersedia")

                "profile.password.change" -> {
                    sendResult(
                        id,
                        mainViewModel.gantiPassword(
                            mainViewModel.state.value.currentUser,
                            payload.optString("newPassword"),
                        ),
                    )
                }

                "profile.user.add" -> {
                    val username = payload.optString("username")
                    if (mainViewModel.cekUsername(username)) {
                        sendResult(id, false, error = "Username sudah dipakai")
                        return
                    }
                    activity.lifecycleScope.launch {
                        val result = mainViewModel.daftarUser(
                            username = username,
                            password = payload.optString("password"),
                            email = payload.optString("email"),
                            role = "kasir",
                            noHp = payload.optString("noHp"),
                        )
                        sendResult(id, result.success, error = if (result.success) null else result.message)
                    }
                }

                "profile.user.reset" -> {
                    sendResult(
                        id,
                        mainViewModel.gantiPassword(
                            payload.optString("username"),
                            payload.optString("newPassword"),
                        ),
                    )
                }

                "profile.user.delete" -> {
                    sendResult(id, mainViewModel.hapusUser(payload.optString("username")))
                }

                "profile.qris.upload" -> {
                    activity.pickDocument(arrayOf("image/*")) { uri ->
                        if (uri == null) {
                            sendResult(id, false, error = "Pemilihan gambar QRIS dibatalkan")
                            return@pickDocument
                        }
                        val base64 = compressImageToBase64(uri)
                        if (base64 == null) {
                            sendResult(id, false, error = "Gambar QRIS tidak bisa dibaca")
                            return@pickDocument
                        }
                        sendResult(id, true, JSONObject().put("base64", base64))
                    }
                }

                "profile.theme" -> {
                    mainViewModel.setTheme(ThemeOption.valueOf(payload.optString("option")))
                    sendResult(id, true)
                }

                "profile.background" -> {
                    mainViewModel.setBackgroundOption(BackgroundOption.valueOf(payload.optString("option")))
                    sendResult(id, true)
                }

                "profile.notif.read" -> {
                    mainViewModel.markNotifRead()
                    sendResult(id, true)
                }

                "profile.rental.save" -> {
                    if (mainViewModel.state.value.currentRole == "kasir") {
                        sendResult(id, false, error = "Hanya admin yang dapat mengubah data rental")
                    } else {
                        mainViewModel.updateDataRental(
                            payload.optString("nama"),
                            payload.optString("alamat"),
                            payload.optString("wa"),
                        )
                        sendResult(id, true)
                    }
                }

                "profile.printer.scan" -> {
                    activity.requestBluetoothPermissions { granted ->
                        if (!granted) {
                            sendResult(id, false, error = "Izin Bluetooth diperlukan untuk memindai printer")
                        } else {
                            try {
                                val devices = ThermalPrinter.getAllBondedDevices().map { device ->
                                    JSONObject()
                                        .put("name", device.name ?: "Printer")
                                        .put("address", device.address)
                                        .put("bonded", device.bondState == android.bluetooth.BluetoothDevice.BOND_BONDED)
                                }
                                sendResult(id, true, JSONArray(devices))
                            } catch (e: Exception) {
                                sendResult(id, false, error = e.message ?: "Gagal memindai Bluetooth")
                            }
                        }
                    }
                }

                "profile.printer.save" -> {
                    mainViewModel.savePrinter(payload.optString("address"), payload.optString("name"))
                    sendResult(id, true)
                }

                "profile.printer.connect" -> {
                    activity.requestBluetoothPermissions { granted ->
                        if (!granted) {
                            sendResult(id, false, error = "Izin Bluetooth diperlukan untuk menghubungkan printer")
                        } else {
                            mainViewModel.connectPrinter(payload.optString("address"))
                            sendResult(id, true)
                        }
                    }
                }

                "profile.printer.settings" -> {
                    activity.startActivity(
                        android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS),
                    )
                    sendResult(id, true)
                }

                "profile.printer.disconnect" -> {
                    mainViewModel.disconnectPrinter()
                    sendResult(id, true)
                }

                "profile.printer.test" -> {
                    mainViewModel.printTestPage()
                    sendResult(id, true)
                }

                "profile.update.check" -> {
                    mainViewModel.checkForUpdate()
                    sendResult(id, true)
                }

                "profile.update.download" -> {
                    mainViewModel.downloadAndInstall(activity)
                    sendResult(id, true)
                }

                "promo.dismiss" -> {
                    mainViewModel.dismissPromoPopup()
                    sendResult(id, true)
                }

                "notification.dismiss" -> {
                    mainViewModel.dismissNotifPopup()
                    sendResult(id, true)
                }

                else -> sendResult(id, false, error = "Perintah tidak dikenal: $name")
            }
        } catch (e: Exception) {
            Log.e("RRBridge", "command $name gagal: ${e.message}", e)
            sendResult(id, false, error = e.message ?: "Perintah gagal")
        }
    }

    private fun prayerJson(): JSONObject {
        val state = mainViewModel.state.value
        val config = state.prayerConfig
        val schedule = PrayerTimes.scheduleForToday(config)
        val scheduleArray = JSONArray()
        schedule?.all()?.forEach { prayer ->
            scheduleArray.put(
                JSONObject()
                    .put("name", prayer.name)
                    .put("label", prayer.label)
                    .put("atMillis", prayer.atMillis),
            )
        }
        val next = PrayerTimes.nextPrayer(config)
        return JSONObject()
            .put(
                "config",
                JSONObject()
                    .put("enabled", config.enabled)
                    .put("message", config.message)
                    .put("durationSec", config.durationSec)
                    .put("latitude", config.latitude)
                    .put("longitude", config.longitude)
                    .put("locationSet", config.locationSet)
                    .put("locationLabel", config.locationLabel),
            )
            .put("schedule", scheduleArray)
            .put(
                "next",
                next?.let {
                    JSONObject()
                        .put("name", it.name)
                        .put("label", it.label)
                        .put("atMillis", it.atMillis)
                } ?: JSONObject.NULL,
            )
            .put("locationStatus", state.prayerLocationStatus)
    }

    private fun findTv(id: String): TvData? =
        mainViewModel.state.value.tvList.firstOrNull { it.id == id }

    private fun buildState(): JSONObject {
        val state = mainViewModel.state.value
        val connections = mainViewModel.tvConnections.value
        val user = state.users[state.currentUser]
        val hasTrial = state.trialBatas > System.currentTimeMillis()
        val isLocked = state.licenseStatus.status != "active" && !hasTrial

        val baseTabs = if (state.currentRole == "kasir") {
            listOf("TV", "KASIR", "RIWAYAT", "LISENSI", "PROFILE")
        } else {
            listOf("TV", "KASIR", "STOK", "RIWAYAT", "HARGA", "CLIENT", "LISENSI", "PROFILE")
        }
        val tabs = if (mainViewModel.isClientFeatureEnabled()) baseTabs
        else baseTabs.filter { it != "CLIENT" }

        val payload = JSONObject()
            .put(
                "session",
                JSONObject()
                    .put("isLoggedIn", state.isLoggedIn)
                    .put("currentUser", state.currentUser)
                    .put("currentRole", state.currentRole)
                    .put("loginMethod", state.loginMethod)
                    .put("isBusy", state.isBusy)
                    .put("statusMessage", state.statusMessage)
                    .put("appVersionName", state.appVersionName),
            )
            .put("tabs", JSONArray(tabs))
            .put(
                "license",
                JSONObject()
                    .put("status", state.licenseStatus.status)
                    .put("pesan", state.licenseStatus.pesan)
                    .put("expiresAt", state.licenseStatus.expiresAt)
                    .put("maxTv", state.maxTv)
                    .put("promoAddTv", state.licenseStatus.promoAddTv)
                    .put("paket", state.licenseStatus.paket)
                    .put("clientEnabled", mainViewModel.isClientFeatureEnabled())
                    .put("trialBatas", state.trialBatas)
                    .put("isLocked", isLocked),
            )
            .put(
                "pricing",
                JSONObject()
                    .put("jenisPsList", JSONArray(state.jenisPsList))
                    .put("paketMain", nestedIntMapToJson(state.paketMain))
                    .put("paketDurasi", intMapToJson(state.paketDurasi))
                    .put("menuMakanan", intMapToJson(state.menuMakanan))
                    .put("menuMinuman", intMapToJson(state.menuMinuman))
                    .put("stokMakanan", intMapToJson(state.stokMakanan))
                    .put("stokMinuman", intMapToJson(state.stokMinuman)),
            )
            .put("transactions", transactionsJson(state.transaksiList))
            .put(
                "invoices",
                JSONObject()
                    .put("my", invoicesJson(mainViewModel.getMyInvoices()))
                    .put(
                        "pending",
                        invoicesJson(state.invoices.filter { it.status == "WAITING_CONFIRMATION" }),
                    )
                    .put("pendingCount", state.pendingInvoiceCount),
            )
            .put(
                "profile",
                JSONObject()
                    .put("current", userJson(user))
                    .put("users", usersJson(state.users.values.toList()))
                    .put("themeOption", state.themeOption.name)
                    .put("backgroundOption", state.backgroundOption.name)
                    .put("tvPasswordSet", state.tvPasswordHash.isNotBlank())
                    .put(
                        "printer",
                        JSONObject()
                            .put("address", state.printerAddress)
                            .put("name", state.printerName)
                            .put("connected", state.printerConnected),
                    ),
            )
            .put(
                "notifications",
                JSONObject()
                    .put("unreadCount", state.unreadNotifCount)
                    .put("items", notificationsJson(state)),
            )
            .put(
                "promo",
                JSONObject()
                    .put("active", state.promo.promoAktif)
                    .put("newUserActive", mainViewModel.isNewUserPromoActive())
                    .put("newUserRemainingHours", mainViewModel.newUserPromoRemainingHours())
                    .put("regularRemainingHours", mainViewModel.regularPromoRemainingHours())
                    .put("newUserDiscountPercent", state.promo.newUserDiscountPercent)
                    .put("diskonPerPaket", intMapToJson(state.promo.diskonPerPaket))
                    .put("newUserDiskonPerPaket", intMapToJson(state.promo.newUserDiskonPerPaket)),
            )
            .put(
                "update",
                JSONObject()
                    .put("checked", state.updateChecked)
                    .put("downloadProgress", state.downloadProgress)
                    .put("versionName", state.updateInfo?.versionName ?: "")
                    .put("changelog", state.updateInfo?.changelog ?: "")
                    .put("apkUrl", state.updateInfo?.apkUrl ?: ""),
            )
            .put("clientStatus", clientStatusJson(state))
            .put(
                "pairing",
                JSONObject()
                    .put("status", tvViewModel.uiState.value.status)
                    .put("isPairing", tvViewModel.uiState.value.isPairing)
                    .put("isPaired", tvViewModel.uiState.value.isPaired)
                    .put("isBusy", tvViewModel.uiState.value.isBusy)
                    .put("pendingTvId", pendingPairTvId ?: JSONObject.NULL),
            )
            .put("tv", tvListJson(state, connections))
            .put("packages", packagesJson())
            .put("qrisUrl", "/qris_admin.png")
            .put("habisDismissedIds", JSONArray(state.habisDismissedIds.toList()))

        return payload
    }

    private fun tvListJson(
        state: AppUiState,
        connections: Map<String, TvConnection>,
    ): JSONArray {
        val array = JSONArray()
        state.tvList.forEach { tv ->
            val connection = TvHubManager.connectionFor(tv)
            val client = state.clientStatus[tv.id]
            array.put(
                JSONObject()
                    .put("id", tv.id)
                    .put("nama", tv.nama)
                    .put("ip", tv.ip)
                    .put("port", tv.port)
                    .put("jenisPs", tv.jenisPs)
                    .put("paketAktif", tv.paketAktif)
                    .put("sisaDetik", tv.sisaDetik)
                    .put("timerStart", tv.timerStart)
                    .put("timerDurasi", tv.timerDurasi)
                    .put("timerActive", tv.timerActive)
                    .put("sesiMulai", tv.sesiMulai)
                    .put("sesiSelesai", tv.sesiSelesai)
                    .put("sesiPaused", tv.sesiPaused)
                    .put("bebasTertundaMs", tv.bebasTertundaMs)
                    .put("bebas", tv.bebas)
                    .put("paketHarga", tv.paketHarga)
                    .put("totalPesanan", tv.totalPesanan)
                    .put("trxId", tv.trxId)
                    .put("pesanan", intMapToJson(tv.pesanan))
                    .put("pesananHarga", intMapToJson(tv.pesananHarga))
                    .put("bebasMulai", tv.bebasMulai)
                    .put("bebasHargaPerJam", tv.bebasHargaPerJam)
                    .put("bebasPesananTotal", tv.bebasPesananTotal)
                    .put("paired", tv.paired)
                    .put("cancelBatas", tv.cancelBatas)
                    .put("sudahBayar", tv.sudahBayar)
                    .put("paketSudahBayar", tv.paketSudahBayar)
                    .put("fnbSudahBayar", tv.fnbSudahBayar)
                    .put("cashDiterima", tv.cashDiterima)
                    .put("kembalian", tv.kembalian)
                    .put("namaRental", tv.namaRental)
                    .put("popupMode", tv.popupMode)
                    .put("popupLastMinutes", tv.popupLastMinutes)
                    .put("bebasInterval", tv.bebasInterval)
                    .put("bebasDurasi", tv.bebasDurasi)
                    .put("hdmiPort", tv.hdmiPort)
                    .put("hdmiInputHint", tv.hdmiInputHint)
                    .put("hdmiCommand", tv.hdmiCommand)
                    .put("hdmiRemoteCommand", tv.hdmiRemoteCommand)
                    .put("hasPromoVideo", tv.promoVideoPath.isNotBlank())
                    .put("hasLockscreenVideo", tv.lockscreenVideoPath.isNotBlank())
                    .put("online", connection?.online == true)
                    .put("screenOn", connection?.screenOn ?: JSONObject.NULL)
                    .put("inputLabel", connection?.inputLabel ?: "")
                    .put("uiMode", connection?.uiMode ?: "")
                    .put("locked", connection?.locked == true)
                    .put("lastSeen", connection?.lastSeen ?: 0L)
                    .put("clientVersion", client?.version ?: "")
                    .put("clientLastSeen", client?.lastSeen ?: 0L),
            )
        }
        return array
    }

    private fun clientStatusJson(state: AppUiState): JSONObject {
        val obj = JSONObject()
        state.clientStatus.forEach { (id, status) ->
            obj.put(
                id,
                JSONObject()
                    .put("online", status.online)
                    .put("version", status.version)
                    .put("lastSeen", status.lastSeen),
            )
        }
        return obj
    }

    private fun tvForPayload(payload: JSONObject): TvData? {
        val tvId = payload.optString("tvId")
        if (tvId.isNotBlank()) {
            mainViewModel.state.value.tvList.firstOrNull { it.id == tvId }?.let { return it }
        }
        val kota = payload.optString("kota")
        return mainViewModel.state.value.tvList.firstOrNull { it.nama == kota }
    }

    private fun Transaksi.withSession(
        payload: JSONObject,
        existing: Transaksi? = null,
    ): Transaksi {
        val tv = tvForPayload(payload)
        val tvStart = when {
            tv == null -> 0L
            tv.sesiMulai > 0L -> tv.sesiMulai
            tv.timerActive && tv.timerStart > 0L -> tv.timerStart
            tv.bebas && tv.bebasMulai > 0L -> tv.bebasMulai
            else -> 0L
        }
        val sesiMulai = payload.optLong("sesiMulai", 0L).takeIf { it > 0L } ?: tvStart
        val sesiSelesai = payload.optLong("sesiSelesai", 0L).takeIf { it > 0L }
            ?: tv?.sesiSelesai?.takeIf { it > 0L }
            ?: 0L
        return copy(
            tvId = tvId.ifBlank { tv?.id.orEmpty() },
            kategori = payload.optString("kategori").ifBlank { existing?.kategori.orEmpty() },
            jumlah = payload.optInt("jumlah", existing?.jumlah ?: 1).coerceAtLeast(1),
            sesiMulai = sesiMulai,
            sesiSelesai = sesiSelesai,
        )
    }

    private fun transactionsJson(items: List<Transaksi>): JSONArray {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("waktu", item.waktu)
                    .put("kasir", item.kasir)
                    .put("kota", item.kota)
                    .put("paket", item.paket)
                    .put("pesanan", intMapToJson(item.pesanan))
                    .put("total", item.total)
                    .put("paketHarga", item.paketHarga)
                    .put("pesananHarga", intMapToJson(item.pesananHarga))
                    .put("tvJenisPs", item.tvJenisPs)
                    .put("sudahBayar", item.sudahBayar)
                    .put("metode", item.metode)
                    .put("tvId", item.tvId)
                    .put("kategori", item.kategori)
                    .put("jumlah", item.jumlah)
                    .put("sesiMulai", item.sesiMulai)
                    .put("sesiSelesai", item.sesiSelesai)
                    .put("cash", item.cash)
                    .put("kembalian", item.kembalian),
            )
        }
        return array
    }

    private fun invoicesJson(items: List<Invoice>): JSONArray {
        val array = JSONArray()
        items.forEach { item -> array.put(invoiceJson(item)) }
        return array
    }

    private fun invoiceJson(invoice: Invoice): JSONObject =
        JSONObject()
            .put("id", invoice.id)
            .put("username", invoice.username)
            .put("email", invoice.email)
            .put("paket", invoice.paket)
            .put("harga", invoice.harga)
            .put("status", invoice.status)
            .put("dibuat", invoice.dibuat)
            .put("dibayar", invoice.dibayar)
            .put("confirmedBy", invoice.confirmedBy)
            .put("kodeLisensi", invoice.kodeLisensi)
            .put("jumlahTv", invoice.jumlahTv)
            .put("mode", invoice.mode)
            .put("baseMaxTv", invoice.baseMaxTv)
            .put("hasProof", invoice.buktiBase64.isNotBlank())

    private fun licenseRecordsJson(items: List<LicenseRecord>): JSONArray {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("kode", item.kode)
                    .put("paket", item.paket)
                    .put("username", item.username)
                    .put("email", item.email)
                    .put("expiry", item.expiry)
                    .put("activatedAt", item.activatedAt)
                    .put("revoked", item.revoked)
                    .put("maxActivations", item.maxActivations)
                    .put("maxTv", item.maxTv)
                    .put("clientEnabled", item.clientEnabled)
                    .put(
                        "devices",
                        JSONArray(
                            item.activatedDevices.map { device ->
                                JSONObject()
                                    .put("deviceType", device.deviceType)
                                    .put("deviceId", device.deviceId)
                                    .put("activatedAt", device.activatedAt)
                            },
                        ),
                    ),
            )
        }
        return array
    }

    private fun userJson(user: UserData?): Any {
        if (user == null) return JSONObject.NULL
        return JSONObject()
            .put("username", user.username)
            .put("role", user.role)
            .put("email", user.email)
            .put("noHp", user.noHp)
            .put("namaRental", user.namaRental)
            .put("alamatRental", user.alamatRental)
            .put("whatsappRental", user.whatsappRental)
            .put("emailVerified", user.emailVerified)
    }

    private fun usersJson(users: List<UserData>): JSONArray {
        val array = JSONArray()
        users.forEach { user -> array.put(userJson(user)) }
        return array
    }

    private fun notificationsJson(state: AppUiState): JSONArray {
        val array = JSONArray()
        state.notifications.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("title", item.title)
                    .put("body", item.body)
                    .put("type", item.type)
                    .put("sentAt", item.sentAt)
                    .put("read", item.read),
            )
        }
        return array
    }

    private fun packagesJson(): JSONArray {
        val array = JSONArray()
        LicenseCatalog.plans.forEach { plan ->
            array.put(
                JSONObject()
                    .put("code", plan.code)
                    .put("nama", plan.label)
                    .put("harga", plan.unitPrice)
                    .put("hargaDiskon", plan.unitPrice)
                    .put("clientEnabled", plan.clientEnabled)
                    .put("durationDays", plan.durationDays)
                    .put("desc", plan.description),
            )
        }
        return array
    }

    private fun buildCsv(kasirFilter: String = "", fromDate: String = "", toDate: String = ""): String {
        val header = "ID,Waktu,Kasir,TV,Paket,PaketHarga,FnbHarga,Total,Status,Metode"
        val state = mainViewModel.state.value
        val scoped = if (state.currentRole == "kasir") {
            state.transaksiList.filter { it.kasir.equals(state.currentUser, ignoreCase = true) }
        } else {
            state.transaksiList
        }
        val selectedKasir = kasirFilter.trim()
        val byKasir = if (selectedKasir.isNotBlank() && !selectedKasir.equals("all", ignoreCase = true)) {
            scoped.filter { it.kasir.equals(selectedKasir, ignoreCase = true) }
        } else {
            scoped
        }
        val from = fromDate.trim().takeIf { it.length >= 10 }?.take(10)
        val to = toDate.trim().takeIf { it.length >= 10 }?.take(10)
        val items = if (from != null || to != null) {
            byKasir.filter { item ->
                val day = item.waktu.take(10)
                (from == null || day >= from) && (to == null || day <= to)
            }
        } else {
            byKasir
        }
        val rows = items
            .sortedWith(compareByDescending<Transaksi> { it.waktu }.thenByDescending { it.id })
            .joinToString("\n") { item ->
            listOf(
                item.id,
                item.waktu,
                item.kasir,
                item.kota,
                item.paket,
                item.paketTotal().toString(),
                item.fnbTotal().toString(),
                item.total.toString(),
                if (item.sudahBayar) "LUNAS" else "BELUM",
                item.metode,
            ).joinToString(",") { escapeCsv(it) }
        }
        return "$header\n$rows\n"
    }

    private fun escapeCsv(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    private fun compressImageToBase64(uri: Uri): String? {
        return try {
            val input = activity.contentResolver.openInputStream(uri) ?: return null
            val bitmap = BitmapFactory.decodeStream(input)
            input.close()
            if (bitmap == null) return null

            val maxDim = 1024f
            val scale = minOf(maxDim / bitmap.width, maxDim / bitmap.height, 1f)
            val resized = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt(),
                    (bitmap.height * scale).toInt(),
                    true,
                )
            } else {
                bitmap
            }
            val output = ByteArrayOutputStream()
            resized.compress(Bitmap.CompressFormat.JPEG, 80, output)
            if (resized !== bitmap) bitmap.recycle()
            resized.recycle()
            val bytes = output.toByteArray()
            if (bytes.size > 800 * 1024) return null
            Base64.encodeToString(bytes, Base64.DEFAULT)
        } catch (e: Exception) {
            Log.e("RRBridge", "compressImageToBase64 gagal: ${e.message}", e)
            null
        }
    }

    private fun intMapToJson(map: Map<String, Int>): JSONObject {
        val obj = JSONObject()
        map.forEach { (key, value) -> obj.put(key, value) }
        return obj
    }

    private fun nestedIntMapToJson(map: Map<String, Map<String, Int>>): JSONObject {
        val obj = JSONObject()
        map.forEach { (key, value) -> obj.put(key, intMapToJson(value)) }
        return obj
    }

    private fun jsonToIntMap(obj: JSONObject?): Map<String, Int> {
        if (obj == null) return emptyMap()
        val result = mutableMapOf<String, Int>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            result[key] = obj.optInt(key)
        }
        return result
    }

    private fun jsonToNestedIntMap(obj: JSONObject?): Map<String, Map<String, Int>> {
        if (obj == null) return emptyMap()
        val result = mutableMapOf<String, Map<String, Int>>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            result[key] = jsonToIntMap(obj.optJSONObject(key))
        }
        return result
    }

    private fun jsonToAnyMap(obj: JSONObject): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = obj.opt(key)
            when (value) {
                is Int, is Long, is Double, is Boolean, is String -> result[key] = value
                is Number -> result[key] = value
                is JSONObject -> result[key] = jsonToIntMap(value)
            }
        }
        return result
    }
}
