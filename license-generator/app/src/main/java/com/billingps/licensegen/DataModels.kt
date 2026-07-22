package com.billingps.licensegen

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
)

data class FirestoreUser(
    val username: String = "",
    val role: String = "",
    val email: String = "",
    val dibuat: String = "",
)

data class NotifikasiHistory(
    val id: String = "",
    val judul: String = "",
    val pesan: String = "",
    val target: String = "", // "all" atau username
    val jumlahTerima: Int = 0,
    val dikirimPada: Long = 0L,
)

data class Invoice(
    val id: String = "",
    val username: String = "",
    val email: String = "",
    val paket: String = "",
    val harga: Int = 0,
    val status: String = "PENDING",
    val dibuat: Long = 0L,
    val dibayar: Long = 0L,
    val confirmedBy: String = "",
    val kodeLisensi: String = "",
    val buktiBase64: String = "",
)
