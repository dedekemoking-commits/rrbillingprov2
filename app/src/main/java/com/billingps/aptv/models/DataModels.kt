package com.billingps.aptv.models

import java.io.Serializable

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
)

fun Transaksi.fnbTotal(): Int =
    pesanan.entries.sumOf { (name, qty) -> (pesananHarga[name] ?: 0) * qty }

fun Transaksi.paketTotal(): Int =
    (total - fnbTotal()).coerceAtLeast(0)

data class UserData(
    val username: String = "",
    val passwordHash: String = "",
    val salt: String = "",
    val role: String = "kasir",
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

data class AuthResult(
    val success: Boolean,
    val message: String,
)

data class FnbSaleResult(
    val success: Boolean,
    val message: String = "",
    val transactionId: String = "",
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
