package com.billingps.owner.data

data class OwnerTv(
    val id: String = "",
    val nama: String = "",
    val ip: String = "",
    val port: Int = 5555,
    val jenisPs: String = "PS3",
    val paketAktif: String = "",
    val sisaDetik: Long = 0L,
    val timerStart: Long = 0L,
    val timerDurasi: Long = 0L,
    val timerActive: Boolean = false,
    val bebas: Boolean = false,
    val paketHarga: Int = 0,
    val totalPesanan: Int = 0,
    val trxId: String = "",
    val pesanan: Map<String, Int> = emptyMap(),
    val pesananHarga: Map<String, Int> = emptyMap(),
    val bebasMulai: Long = 0L,
    val bebasHargaPerJam: Int = 0,
    val bebasPesananTotal: Int = 0,
    val cancelBatas: Long = 0L,
    val sudahBayar: Boolean = false,
    val namaRental: String = "",
    val hdmiPort: Int = 0,
) {
    val aktif: Boolean
        get() = timerActive || bebas || paketAktif.isNotBlank() || sisaDetik > 0L

    val statusSesi: String
        get() = when {
            bebas -> "Bebas"
            timerActive || sisaDetik > 0L -> paketAktif.ifBlank { "Aktif" }
            else -> "Idle"
        }
}

data class ClientStatus(
    val online: Boolean = false,
    val version: String = "",
    val lastSeen: Long = 0L,
)

data class OwnerTransaction(
    val id: String = "",
    val username: String = "",
    val email: String = "",
    val waktu: String = "",
    val kasir: String = "",
    val kota: String = "",
    val paket: String = "",
    val total: Int = 0,
    val paketHarga: Int = 0,
    val pesanan: Map<String, Int> = emptyMap(),
    val pesananHarga: Map<String, Int> = emptyMap(),
    val tvJenisPs: String = "",
    val sudahBayar: Boolean = false,
    val metode: String = "",
) {
    val sumber: String
        get() = if (tvJenisPs.equals("PC", ignoreCase = true)) "PC" else "TV"
}

data class RentalInfo(
    val username: String = "",
    val namaRental: String = "",
    val email: String = "",
    val whatsapp: String = "",
    val alamat: String = "",
)

data class OwnerCashDrawer(
    val status: String = "TUTUP",
    val shiftId: String = "",
    val shiftUsername: String = "",
    val jenisShift: String = "",
    val startedAt: Long = 0L,
    val openingCash: Int = 0,
    val cashExpected: Int = 0,
    val cashSales: Int = 0,
    val cashIn: Int = 0,
    val cashOut: Int = 0,
    val updatedAt: Long = 0L,
) {
    val aktif: Boolean get() = status.equals("AKTIF", ignoreCase = true)
}

data class CashDrawerRequest(
    val id: String = "",
    val nominal: Int = 0,
    val note: String = "",
    val status: String = "pending",
    val createdAt: Long = 0L,
    val processedAt: Long = 0L,
    val appliedBy: String = "",
    val reason: String = "",
) {
    val pending: Boolean get() = status.equals("pending", ignoreCase = true)
    val applied: Boolean get() = status.equals("applied", ignoreCase = true)
}

data class RentalSnapshot(
    val exists: Boolean = false,
    val tvList: List<OwnerTv> = emptyList(),
    val transaksiList: List<OwnerTransaction> = emptyList(),
    val lastSync: Long = 0L,
    val appVersion: String = "",
    val currentUser: String = "",
    val menuMakanan: Map<String, Int> = emptyMap(),
    val menuMinuman: Map<String, Int> = emptyMap(),
    val stokMakanan: Map<String, Int> = emptyMap(),
    val stokMinuman: Map<String, Int> = emptyMap(),
    val jenisPsList: List<String> = emptyList(),
    val paketMain: Map<String, Map<String, Int>> = emptyMap(),
    val paketDurasi: Map<String, Int> = emptyMap(),
    val cashDrawer: OwnerCashDrawer = OwnerCashDrawer(),
    val error: String? = null,
)

data class StockOpnameItem(
    val name: String,
    val category: String,
    val expected: Int?,
    val quantity: Int,
)

data class FnbItemInput(
    val category: String,
    val name: String,
    val price: Int,
    val initialStock: Int,
    val action: String = "add",
)

data class PackageInput(
    val action: String,
    val groupName: String,
    val name: String,
    val price: Int,
    val durationMinutes: Int,
)

data class GroupInput(
    val name: String,
)

data class OwnerAuthResult(
    val success: Boolean,
    val message: String = "",
)
