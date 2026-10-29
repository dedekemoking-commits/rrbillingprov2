package com.billingps.owner.data

object MenuCatalog {
    private val makanan = mapOf(
        "Indomie Goreng" to 8000,
        "Indomie Kuah" to 8000,
        "Kentang Goreng" to 12000,
        "Burger" to 20000,
        "Roti Bakar" to 10000,
        "Nasi Goreng" to 15000,
        "Sosis Bakar" to 7000,
    )

    private val minuman = mapOf(
        "Air Mineral" to 3000,
        "Es Teh Manis" to 5000,
        "Es Jeruk" to 6000,
        "Kopi Hitam" to 6000,
        "Susu Coklat" to 8000,
        "Jus Mangga" to 10000,
        "Soda Gembira" to 10000,
        "Pocari Sweat" to 8000,
    )

    fun price(name: String): Int = makanan[name] ?: minuman[name] ?: 0

    fun isMakanan(name: String): Boolean = makanan.containsKey(name)

    fun isMinuman(name: String): Boolean = minuman.containsKey(name)
}
