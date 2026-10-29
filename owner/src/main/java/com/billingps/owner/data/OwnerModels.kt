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

data class RentalSnapshot(
    val exists: Boolean = false,
    val tvList: List<OwnerTv> = emptyList(),
    val transaksiList: List<OwnerTransaction> = emptyList(),
    val lastSync: Long = 0L,
    val appVersion: String = "",
    val currentUser: String = "",
    val error: String? = null,
)
