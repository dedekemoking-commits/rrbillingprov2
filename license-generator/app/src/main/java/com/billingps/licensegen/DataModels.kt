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
    val promoStartedAt: Long = 0L,
    val promoDurationHours: Int = 0,
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
    val revoked: Boolean = false,
)

data class FirestoreUser(
    val username: String = "",
    val role: String = "",
    val email: String = "",
    val dibuat: String = "",
    val appVersion: String = "",
    val whatsapp: String = "",
    val lastActive: Long = 0L,
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
    val jumlahTv: Int = 0,
    val maxTv: Int = 0,
    val mode: String = "",
    val baseMaxTv: Int = 0,
    val clientEnabled: Boolean = true,
    // Ringkasan yang dikirim client saat membuat invoice, supaya tab Invoice
    // bisa menampilkan kalimat utuh: "rrbillingpro meminta lisensi LIFETIME
    // untuk 5 TV". Tanpa ini admin harus menyimpulkan sendiri dari beberapa
    // angka terpisah (paket + jumlah_tv) yang sering tidak sinkron.
    val permintaan: String = "",
    val hargaPerTv: Int = 0,
) {

    /** Ringkasan yang selalu punya isi, walau client versi lama tidak mengirim field `permintaan`. */
    fun ringkas(): String {
        if (permintaan.isNotBlank()) return permintaan
        val tv = if (jumlahTv > 0) jumlahTv else maxTv
        val tvTxt = if (tv > 0) "$tv TV" else "jumlah TV tidak disebutkan"
        val perTv = if (hargaPerTv > 0) " (Rp ${hargaPerTv}/TV)" else ""
        return "$username meminta lisensi $paket untuk $tvTxt$perTv"
    }

    /** Harga per TV, dihitung dari harga total bila client tidak mengirim field dedicated. */
    fun hargaPerTvEfektif(): Int {
        if (hargaPerTv > 0) return hargaPerTv
        val tv = if (jumlahTv > 0) jumlahTv else maxTv
        return if (tv > 0) harga / tv else 0
    }
}
