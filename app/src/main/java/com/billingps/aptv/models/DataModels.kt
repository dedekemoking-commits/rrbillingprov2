package com.billingps.aptv.models

import java.io.Serializable
import java.util.Locale

data class TvData(
    val id: String = "",
    val nama: String = "",
    val ip: String = "",
    val port: Int = 5555,
    val jenisPs: String = "PS3",
    val paketAktif: String = "",
    val sisaDetik: Long = 0,
    val timerStart: Long = 0L,
    val timerDurasi: Long = 0L,
    val timerActive: Boolean = false,
    val bebas: Boolean = false,
    val paketHarga: Int = 0,
    val totalPesanan: Int = 0,
    val trxId: String = "",
    val pesanan: Map<String, Int> = emptyMap(),
    val pesananHarga: Map<String, Int> = emptyMap(),
    val bebasMulai: Long = 0,
    val bebasHargaPerJam: Int = 0,
    val bebasPesananTotal: Int = 0,
    val paired: Boolean = false,
    val certPem: String = "",
    val keyPem: String = "",
    val cancelBatas: Long = 0,
    val sudahBayar: Boolean = false,
    val namaRental: String = "",
    val promoVideoPath: String = "",
    val lockscreenVideoPath: String = "",
    val popupMode: String = "always",
    val popupLastMinutes: Int = 5,
    val bebasInterval: Int = 60,
    val bebasDurasi: Int = 30,
    val hdmiPort: Int = 0,
    val hdmiInputHint: String = "",
    val hdmiCommand: String = "",
    val hdmiRemoteCommand: String = "",
    val sesiMulai: Long = 0L,
    val sesiSelesai: Long = 0L,
    val sesiPaused: Boolean = false,
    val bebasTertundaMs: Long = 0L,
    val paketSudahBayar: Boolean = false,
    val fnbSudahBayar: Boolean = false,
    val cashDiterima: Int = 0,
    val kembalian: Int = 0,
) : Serializable

data class Transaksi(
    val id: String = "",
    val waktu: String = "",
    val kasir: String = "",
    val kota: String = "",
    val paket: String = "",
    val pesanan: Map<String, Int> = emptyMap(),
    val total: Int = 0,
    val paketHarga: Int = 0,
    val pesananHarga: Map<String, Int> = emptyMap(),
    val tvJenisPs: String = "",
    val sudahBayar: Boolean = false,
    val metode: String = "",
    val tvId: String = "",
    val kategori: String = "",
    val jumlah: Int = 1,
    val sesiMulai: Long = 0L,
    val sesiSelesai: Long = 0L,
    val cash: Int = 0,
    val kembalian: Int = 0,
    val tanggalOperasional: String = "",
    val jenisShift: String = "",
)

fun Transaksi.fnbTotal(): Int =
    pesanan.entries.sumOf { (name, qty) -> (pesananHarga[name] ?: 0) * qty }

fun Transaksi.paketTotal(): Int =
    (total - fnbTotal()).coerceAtLeast(0)

fun Transaksi.isPaketTx(): Boolean =
    kategori == "PAKET" || (kategori.isBlank() && paketHarga != 0 && paket != "BATAL")

fun Transaksi.isFnbTx(): Boolean =
    kategori == "FNB" || (kategori.isBlank() && paketHarga == 0 && pesanan.isNotEmpty())

val TvData.totalFnb: Int get() = totalPesanan + bebasPesananTotal
val TvData.totalBelanja: Int get() = paketHarga + totalFnb
val TvData.semuaLunas: Boolean get() = paketSudahBayar && fnbSudahBayar
val TvData.adaTagihan: Boolean get() = totalBelanja > 0 && !semuaLunas

fun Transaksi.namaPaketBersih(): String =
    paket.removePrefix("Tambah Waktu: ").trim()

data class UserData(
    val username: String = "",
    val passwordHash: String = "",
    val salt: String = "",
    val role: String = "kasir",
    val adminUtama: String = "",
    val email: String = "",
    val noHp: String = "",
    val dibuat: String = "",
    val emailVerified: Boolean = false,
    val verificationCode: String = "",
    val verificationCodeExpiry: Long = 0,
    val resetCode: String = "",
    val resetCodeExpiry: Long = 0,
    val namaRental: String = "",
    val alamatRental: String = "",
    val whatsappRental: String = "",
    val registeredAt: Long = 0L,
)

data class ShiftConfig(
    val pagiMulai: Int = 11 * 60,
    val pagiSelesai: Int = 19 * 60,
    val malamMulai: Int = 21 * 60,
    val malamSelesai: Int = 5 * 60,
    val batasHari: Int = 6 * 60,
) : Serializable {
    fun validasi(): String? {
        if (pagiMulai !in 0 until MINUTES_PER_DAY ||
            pagiSelesai !in 0 until MINUTES_PER_DAY ||
            malamMulai !in 0 until MINUTES_PER_DAY ||
            malamSelesai !in 0 until MINUTES_PER_DAY ||
            batasHari !in 0 until MINUTES_PER_DAY
        ) {
            return "Jam shift tidak valid"
        }
        if (pagiMulai >= pagiSelesai) return "Jam selesai shift pagi harus setelah jam mulai"
        if (malamMulai == malamSelesai) return "Jam mulai dan selesai shift malam tidak boleh sama"
        if (batasHari > pagiMulai) return "Batas hari tidak boleh melewati jam mulai shift pagi"
        if (malamMulai > malamSelesai && batasHari < malamSelesai) {
            return "Batas hari minimal jam selesai shift malam"
        }
        val malamRanges = if (malamMulai > malamSelesai) {
            listOf(malamMulai to MINUTES_PER_DAY, 0 to malamSelesai)
        } else {
            listOf(malamMulai to malamSelesai)
        }
        if (malamRanges.any { (start, end) -> overlaps(pagiMulai, pagiSelesai, start, end) }) {
            return "Shift pagi dan shift malam tidak boleh overlap"
        }
        return null
    }

    fun isPagi(menit: Int): Boolean = menit >= pagiMulai && menit < pagiSelesai

    fun isMalam(menit: Int): Boolean = if (malamMulai > malamSelesai) {
        menit >= malamMulai || menit < malamSelesai
    } else {
        menit >= malamMulai && menit < malamSelesai
    }

    fun isDalamShift(menit: Int): Boolean = isPagi(menit) || isMalam(menit)

    fun labelShift(menit: Int): String = when {
        isPagi(menit) -> "Pagi"
        isMalam(menit) -> "Malam"
        else -> "Luar Shift"
    }

    fun pagiLabel(): String = "${formatTime(pagiMulai)} - ${formatTime(pagiSelesai)}"

    fun malamLabel(): String = "${formatTime(malamMulai)} - ${formatTime(malamSelesai)}"

    fun tanggalOperasional(waktuWib: java.time.ZonedDateTime): java.time.LocalDate {
        val menit = waktuWib.hour * 60 + waktuWib.minute
        return if (menit < batasHari) waktuWib.toLocalDate().minusDays(1) else waktuWib.toLocalDate()
    }

    fun jenisShift(waktuWib: java.time.ZonedDateTime): String =
        labelShift(waktuWib.hour * 60 + waktuWib.minute)

    private fun overlaps(aStart: Int, aEnd: Int, bStart: Int, bEnd: Int): Boolean =
        aStart < bEnd && bStart < aEnd

    companion object {
        const val MINUTES_PER_DAY = 24 * 60

        fun formatTime(menit: Int): String =
            String.format(Locale.US, "%02d:%02d", menit / 60, menit % 60)

        fun parseTime(raw: String?): Int? {
            if (raw.isNullOrBlank()) return null
            val parts = raw.trim().take(5).split(":")
            if (parts.size < 2) return null
            val jam = parts[0].toIntOrNull() ?: return null
            val menit = parts[1].toIntOrNull() ?: return null
            if (jam !in 0..23 || menit !in 0..59) return null
            return jam * 60 + menit
        }
    }
}

data class PrayerConfig(
    val enabled: Boolean = false,
    val message: String = "Waktu sholat {sholat} telah masuk. Mohon jeda sejenak.",
    val durationSec: Int = 15,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val locationSet: Boolean = false,
    val locationLabel: String = "",
)

data class AuthResult(
    val success: Boolean,
    val message: String,
)

data class FnbSaleResult(
    val success: Boolean,
    val message: String = "",
    val transactionId: String = "",
    val total: Int = 0,
    val cash: Int = 0,
    val kembalian: Int = 0,
    val metode: String = "Tunai",
)

data class LicenseStatus(
    val status: String = "", // active, trial, inactive
    val pesan: String = "",
    val expiresAt: String = "",
    val maxTv: Int = 0, // 0 = unlimited (legacy only)
    val promoAddTv: Int = 0, // legacy promo TV count
    val paket: String = "",
    val clientEnabled: Boolean = true, // old licenses keep client access
)

data class KodeGenerasi(
    val waktu: String = "",
    val username: String = "",
    val paket: String = "",
    val kode: String = "",
)

data class ActivatedDevice(
    val deviceType: String = "", // "android" atau "desktop"
    val deviceId: String = "",
    val activatedAt: Long = 0,
)

data class LicenseRecord(
    val id: String = "",
    val kode: String = "",
    val payload: String = "",
    val signature: String = "",
    val paket: String = "",
    val username: String = "",
    val email: String = "",
    val expiry: String = "",
    val generatedBy: String = "",
    val generatedAt: Long = 0,
    val activatedAt: Long = 0,
    val activatedDeviceId: String = "",
    val revoked: Boolean = false,
    val maxActivations: Int = 1,
    val activatedDevices: List<ActivatedDevice> = emptyList(),
    val promoMaxTv: Int = 0,
    val maxTv: Int = 0,
    val clientEnabled: Boolean = true,
)

data class SmtpConfig(
    val host: String = "",
    val port: Int = 587,
    val user: String = "",
    val pass: String = "",
)

data class AppConfig(
    val users: Map<String, UserData> = emptyMap(),
    val currentUser: String = "",
    val currentRole: String = "",
    val paketMain: Map<String, Map<String, Int>> = emptyMap(),
    val paketDurasi: Map<String, Int> = emptyMap(),
    val menuMakanan: Map<String, Int> = emptyMap(),
    val menuMinuman: Map<String, Int> = emptyMap(),
    val tvList: List<TvData> = emptyList(),
    val transaksiList: List<Transaksi> = emptyList(),
    val licenseStatus: LicenseStatus = LicenseStatus(),
    val smtp: SmtpConfig = SmtpConfig(),
    val kodeGenerasiList: List<KodeGenerasi> = emptyList(),
)


data class UpdateInfo(
    val versionName: String,
    val apkUrl: String,
    val changelog: String,
)

data class PromoSettings(
    val promoAktif: Boolean = false,
    val diskonPerPaket: Map<String, Int> = emptyMap(),
    val addTvOverride: Map<String, Int> = emptyMap(),
    val updatedBy: String = "",
    val updatedAt: Long = 0L,
    val newUserPromoActive: Boolean = false,
    val newUserDiscountPercent: Int = 30,
    val newUserPromoDurationHours: Int = 96,
    val newUserDiskonPerPaket: Map<String, Int> = emptyMap(),
    val promoStartedAt: Long = 0L,
    val promoDurationHours: Int = 0,
)

data class AppNotification(
    val id: String = "",
    val title: String = "",
    val body: String = "",
    val type: String = "", // "promo", "license_confirmed", "info"
    val sentAt: Long = 0L,
    val read: Boolean = false,
)

data class Invoice(
    val id: String = "",
    val username: String = "",
    val email: String = "",
    val paket: String = "",
    val harga: Int = 0,
    val status: String = "PENDING", // PENDING, WAITING_CONFIRMATION, CONFIRMED, EXPIRED
    val dibuat: Long = 0L,
    val dibayar: Long = 0L,
    val confirmedBy: String = "",
    val kodeLisensi: String = "",
    val buktiBase64: String = "",
    val jumlahTv: Int = 0, // 0 = legacy invoice
    val mode: String = "", // new, add, renew; empty = legacy
    val baseMaxTv: Int = 0,
)

data class ClientStatus(
    val online: Boolean = false,
    val version: String = "",
    val lastSeen: Long = 0L,
    val hdmiTestId: String = "",
    val hdmiTestResult: String = "",
    val monitorCommand: String = "",
    val monitorCommandId: String = "",
    val monitorStatus: String = "",
    val monitorResult: String = "",
    val monitorIssuedAt: Long = 0L,
)

data class DeviceConflict(
    val activeDeviceId: String = "",
    val activeDeviceName: String = "perangkat lain",
)

data class DeviceClaimResult(
    val success: Boolean = false,
    val sessionId: String = "",
    val conflict: DeviceConflict? = null,
    val error: String = "",
)
