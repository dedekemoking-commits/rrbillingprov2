package com.billingps.aptv.tv

import com.billingps.aptv.models.TvData
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class HubConfig(
    val tvs: List<TvData> = emptyList(),
    val rentalName: String = "RR Billing Pro",
    val clientEnabled: Boolean = true,
)

data class TvConnection(
    val mejaId: String = "",
    val nama: String = "",
    val ip: String = "",
    val device: String = "",
    val online: Boolean = false,
    val screenOn: Boolean? = null,
    val inputLabel: String = "",
    val uiMode: String = "",
    val locked: Boolean = false,
    val lastSeen: Long = 0L,
)

object TvHubProtocol {
    const val ACTION_START_TIMER = "START_TIMER"
    const val ACTION_PAUSE_TIMER = "PAUSE_TIMER"
    const val ACTION_RESUME_TIMER = "RESUME_TIMER"
    const val ACTION_STOP_TIMER = "STOP_TIMER"
    const val ACTION_SLEEP_TV = "SLEEP_TV"
    const val ACTION_UPDATE_TOTAL = "UPDATE_TOTAL"
    const val ACTION_SYNC_TIMER = "SYNC_TIMER"
    const val ACTION_LOCK_SCREEN = "LOCK_SCREEN"
    const val ACTION_UNLOCK_SCREEN = "UNLOCK_SCREEN"
    const val ACTION_UPDATE_RENTAL = "UPDATE_RENTAL"
    const val ACTION_SHOW_MEDIA = "SHOW_MEDIA"
    const val ACTION_HIDE_MEDIA = "HIDE_MEDIA"
    const val ACTION_UPDATE_LOCK_BG = "UPDATE_LOCK_BG"
    const val ACTION_OVERLAY_SETTINGS = "OVERLAY_SETTINGS"
    const val ACTION_SHOW_MESSAGE = "SHOW_MESSAGE"
    const val ACTION_PING = "PING"
    const val ACTION_PAIRING_MODE = "PAIRING_MODE"

    const val TYPE_REGISTER = "REGISTER"
    const val TYPE_REGISTERED = "REGISTERED"
    const val TYPE_PONG = "PONG"
    const val TYPE_SCREEN_STATE = "SCREEN_STATE"
    const val TYPE_CLIENT_EVENT = "CLIENT_EVENT"
    const val TYPE_GET_TVS = "GET_TVS"
    const val TYPE_TVS_RESPONSE = "TVS_RESPONSE"
    const val TYPE_ERROR = "ERROR"

    fun normalizeId(value: String?): String =
        (value ?: "").trim().lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "")

    fun findTv(tvs: List<TvData>, mejaId: String): TvData? {
        val wanted = normalizeId(mejaId)
        if (wanted.isEmpty()) return null
        return tvs.firstOrNull { normalizeId(it.nama) == wanted }
            ?: tvs.firstOrNull { normalizeId(it.id) == wanted }
    }

    fun startTimer(tv: TvData, mejaId: String, rentalName: String): JSONObject {
        val payload = JSONObject()
            .put("action", ACTION_START_TIMER)
            .put("meja_id", mejaId)
            .put("sisa_detik", if (tv.bebas) -1 else tv.sisaDetik.coerceAtLeast(0))
            .put("is_bebas", tv.bebas)
            .put("nama_rental", rentalName)
            .put("total_tagihan", fmtRp(totalTagihan(tv)))
            .put("lunas_total", fmtRp(if (tv.sudahBayar) totalTagihan(tv) else 0))
            .put("tagihan_total", fmtRp(if (tv.sudahBayar) 0 else totalTagihan(tv)))
            .put("sewa_harga", fmtRp(sewaHarga(tv)))
            .put("fnb", fmtRp(fnbTotal(tv)))
            .put("overlay_mode", overlayMode(tv))
            .put("overlay_last_minutes", tv.popupLastMinutes.coerceIn(1, 120))
            .put("bebas_interval", tv.bebasInterval.coerceIn(1, 600))
            .put("bebas_durasi", tv.bebasDurasi.coerceIn(1, 120))
            .put("elapsed_detik", if (tv.bebas) elapsedBebas(tv) else -1)
        addHdmi(payload, tv)
        return payload
    }

    fun syncTimer(tv: TvData, mejaId: String): JSONObject {
        val payload = JSONObject()
            .put("action", ACTION_SYNC_TIMER)
            .put("meja_id", mejaId)
            .put("sisa_detik", if (tv.bebas) -1 else tv.sisaDetik.coerceAtLeast(0))
            .put("is_bebas", tv.bebas)
            .put("total_tagihan", fmtRp(totalTagihan(tv)))
            .put("lunas_total", fmtRp(if (tv.sudahBayar) totalTagihan(tv) else 0))
            .put("tagihan_total", fmtRp(if (tv.sudahBayar) 0 else totalTagihan(tv)))
            .put("sewa_harga", fmtRp(sewaHarga(tv)))
            .put("fnb", fmtRp(fnbTotal(tv)))
            .put("overlay_mode", overlayMode(tv))
            .put("overlay_last_minutes", tv.popupLastMinutes.coerceIn(1, 120))
            .put("bebas_interval", tv.bebasInterval.coerceIn(1, 600))
            .put("bebas_durasi", tv.bebasDurasi.coerceIn(1, 120))
            .put("elapsed_detik", if (tv.bebas) elapsedBebas(tv) else -1)
        addHdmi(payload, tv)
        return payload
    }

    fun stopTimer(mejaId: String): JSONObject =
        JSONObject().put("action", ACTION_STOP_TIMER).put("meja_id", mejaId)

    fun sleepTv(mejaId: String): JSONObject = JSONObject()
        .put("action", ACTION_SLEEP_TV)
        .put("meja_id", mejaId)
        .put("countdown", 0)

    fun lockScreen(tv: TvData, mejaId: String): JSONObject {
        val total = totalTagihan(tv)
        val detail = JSONObject()
            .put("meja", tv.nama)
            .put("sewa", tv.paketAktif.ifBlank { "-" })
            .put("fnb", fmtRp(fnbTotal(tv)))
            .put("total", fmtRp(total))
            .put("sewa_harga", fmtRp(sewaHarga(tv)))
            .put("sewa_lunas", tv.sudahBayar)
            .put("lunas_total", fmtRp(if (tv.sudahBayar) total else 0))
            .put("tagihan_total", fmtRp(if (tv.sudahBayar) 0 else total))
            .put("makanan", JSONArray())
            .put("minuman", JSONArray())
        return JSONObject()
            .put("action", ACTION_LOCK_SCREEN)
            .put("meja_id", mejaId)
            .put("pesan", "WAKTU SEWA HABIS")
            .put("detail_transaksi", detail)
    }

    fun overlaySettings(tv: TvData, mejaId: String): JSONObject = JSONObject()
        .put("action", ACTION_OVERLAY_SETTINGS)
        .put("meja_id", mejaId)
        .put("overlay_mode", if (tv.timerActive || tv.bebas) overlayMode(tv) else "hidden")
        .put("overlay_last_minutes", tv.popupLastMinutes.coerceIn(1, 120))
        .put("bebas_start_durasi", 5)
        .put("bebas_interval", tv.bebasInterval.coerceIn(1, 600))
        .put("bebas_durasi", tv.bebasDurasi.coerceIn(1, 120))

    fun updateRental(mejaId: String, rentalName: String): JSONObject = JSONObject()
        .put("action", ACTION_UPDATE_RENTAL)
        .put("meja_id", mejaId)
        .put("nama_rental", rentalName)

    fun showMedia(mejaId: String, mediaType: String, local: String = "", url: String = ""): JSONObject =
        JSONObject()
            .put("action", ACTION_SHOW_MEDIA)
            .put("meja_id", mejaId)
            .put("type", mediaType.ifBlank { "video" })
            .put("url", url)
            .put("local", local)

    fun hideMedia(mejaId: String): JSONObject =
        JSONObject().put("action", ACTION_HIDE_MEDIA).put("meja_id", mejaId)

    fun updateLockBg(mejaId: String, local: String = "", url: String = ""): JSONObject =
        JSONObject()
            .put("action", ACTION_UPDATE_LOCK_BG)
            .put("meja_id", mejaId)
            .put("local", local)
            .put("bg_url", url)

    fun showMessage(mejaId: String, pesan: String, durasi: Int = 5): JSONObject = JSONObject()
        .put("action", ACTION_SHOW_MESSAGE)
        .put("meja_id", mejaId)
        .put("pesan", pesan.take(300))
        .put("durasi", durasi.coerceIn(3, 300))

    fun fnbTotal(tv: TvData): Int =
        if (tv.bebas) tv.bebasPesananTotal.coerceAtLeast(0)
        else tv.totalPesanan.coerceAtLeast(0)

    fun sewaHarga(tv: TvData): Int =
        if (tv.bebas) (totalTagihan(tv) - fnbTotal(tv)).coerceAtLeast(0)
        else tv.paketHarga.coerceAtLeast(0)

    fun totalTagihan(tv: TvData): Int {
        if (tv.bebas) {
            val menit = elapsedBebas(tv) / 60.0
            val tarif = (tv.bebasHargaPerJam / 60.0) * menit
            return (roundBebasCharge(tarif) + tv.bebasPesananTotal.coerceAtLeast(0)).coerceAtLeast(0)
        }
        return (tv.paketHarga + tv.totalPesanan).coerceAtLeast(0)
    }

    fun roundBebasCharge(value: Double): Int {
        val amount = value.coerceAtLeast(0.0)
        if (amount <= 500.0) return 0
        val ribuan = kotlin.math.floor(amount / 1000.0).toInt() * 1000
        return ribuan + if (amount - ribuan > 500.0) 1000 else 0
    }

    fun elapsedBebas(tv: TvData): Int {
        if (!tv.bebas || tv.bebasMulai <= 0L) return 0
        return ((System.currentTimeMillis() - tv.bebasMulai) / 1000L).coerceAtLeast(0).toInt()
    }

    fun overlayMode(tv: TvData): String {
        val mode = tv.popupMode.trim().lowercase(Locale.US)
        return if (mode in setOf("always", "last_minutes", "hidden")) mode else "always"
    }

    fun fmtRp(value: Int): String {
        val sign = if (value < 0) "-" else ""
        val digits = kotlin.math.abs(value).toString()
        val grouped = digits.reversed().chunked(3).joinToString(".").reversed()
        return "Rp $sign$grouped"
    }

    private fun addHdmi(payload: JSONObject, tv: TvData) {
        payload.put("hdmi_port", tv.hdmiPort)
        if (tv.hdmiInputHint.isNotBlank()) {
            payload.put("hdmi_input_hint", tv.hdmiInputHint)
        }
    }
}
