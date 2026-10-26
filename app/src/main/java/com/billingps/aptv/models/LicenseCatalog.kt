package com.billingps.aptv.models

/**
 * Single source of truth for the two current license products.
 *
 * The quota is intentionally not part of a plan: one purchase buys N TV cards.
 * Existing licenses are additive, so 6 cards + 2 cards = 8 cards.
 */
object LicenseCatalog {
    const val LIFETIME = "LIFETIME"
    const val MONTHLY = "BULANAN"

    const val MODE_NEW = "new"
    const val MODE_ADD = "add"
    const val MODE_RENEW = "renew"

    data class Plan(
        val code: String,
        val label: String,
        val unitPrice: Int,
        val durationDays: Int,
        val clientEnabled: Boolean,
        val description: String,
    )

    val plans: List<Plan> = listOf(
        Plan(
            code = LIFETIME,
            label = "Lifetime",
            unitPrice = 75_000,
            durationDays = 99_999,
            clientEnabled = true,
            description = "Sekali bayar · client APK tersedia",
        ),
        Plan(
            code = MONTHLY,
            label = "Bulanan",
            unitPrice = 10_000,
            durationDays = 30,
            clientEnabled = false,
            description = "30 hari · tanpa client APK",
        ),
    )

    fun find(paket: String?): Plan? {
        val key = paket?.trim()?.uppercase() ?: return null
        return plans.firstOrNull { it.code == key }
    }

    fun label(paket: String?): String = find(paket)?.label ?: (paket ?: "")

    fun unitPrice(paket: String?): Int = find(paket)?.unitPrice ?: 0

    fun durationDays(paket: String?): Int =
        find(paket)?.durationDays ?: legacyDurationDays(paket)

    fun clientEnabled(paket: String?): Boolean =
        find(paket)?.clientEnabled ?: legacyClientEnabled(paket)

    fun normalizeMode(mode: String?): String = when (mode?.trim()?.lowercase()) {
        MODE_ADD -> MODE_ADD
        MODE_RENEW -> MODE_RENEW
        else -> MODE_NEW
    }

    fun isMonthly(paket: String?): Boolean = find(paket)?.code == MONTHLY

    /**
     * Legacy package fallback. Existing invoices/codes must keep working even
     * though the purchase UI only exposes the two new products.
     */
    fun legacyMaxTv(paket: String?): Int = when (paket?.trim()?.uppercase()) {
        "BULANAN", "1 BULAN" -> 5
        "3BULAN", "3 BULAN" -> 8
        "TAHUNAN", "1 TAHUN" -> 15
        LIFETIME -> 0
        else -> 2
    }

    fun legacyDurationDays(paket: String?): Int = when (paket?.trim()?.uppercase()) {
        "BULANAN", "1 BULAN" -> 30
        "3BULAN", "3 BULAN" -> 90
        "TAHUNAN", "1 TAHUN" -> 360
        LIFETIME -> 99_999
        else -> 30
    }

    /** Old licenses already sold with client access must not lose it. */
    fun legacyClientEnabled(@Suppress("UNUSED_PARAMETER") paket: String?): Boolean = true
}
