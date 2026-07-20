package com.billingps.aptv.models

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
    val bebasMulai: Long = 0,
    val bebasHargaPerJam: Int = 0,
    val bebasPesananTotal: Int = 0,
    val paired: Boolean = false,
    val certPem: String = "",
    val keyPem: String = "",
    val cancelBatas: Long = 0,
    val sudahBayar: Boolean = false,
)

data class Transaksi(
    val id: String = "",
    val waktu: String = "",
    val kasir: String = "",
    val kota: String = "",
    val paket: String = "",
    val pesanan: Map<String, Int> = emptyMap(),
    val total: Int = 0,
)

data class UserData(
    val username: String = "",
    val passwordHash: String = "",
    val role: String = "kasir",
    val email: String = "",
    val dibuat: String = "",
    val emailVerified: Boolean = false,
    val verificationCode: String = "",
    val resetCode: String = "",
    val resetCodeExpiry: Long = 0,
)

data class AuthResult(
    val success: Boolean,
    val message: String,
)

data class LicenseStatus(
    val status: String = "", // active, trial, inactive
    val pesan: String = "",
    val expiresAt: String = "",
    val maxTv: Int = 0, // 0 = unlimited
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
    val maxActivations: Int = 2,
    val activatedDevices: List<ActivatedDevice> = emptyList(),
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

val JENIS_PS = listOf("PS3", "PS4", "PS5")

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
)
