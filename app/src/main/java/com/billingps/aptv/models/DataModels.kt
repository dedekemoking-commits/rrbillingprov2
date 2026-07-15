package com.billingps.aptv.models

data class TvData(
    val id: String = "",
    val nama: String = "",
    val ip: String = "",
    val port: Int = 5555,
    val jenisPs: String = "PS3",
    val paketAktif: String = "",
    val sisaDetik: Long = 0,
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
