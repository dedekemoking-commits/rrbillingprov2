package com.billingps.aptv.models

import com.billingps.aptv.utils.TimeUtils
import java.time.ZonedDateTime

data class ShiftActionResult<T>(
    val data: T? = null,
    val error: String? = null,
) {
    val ok: Boolean get() = error == null
}

data class ShiftStartResult(
    val shift: ShiftKasir,
    val movement: PergerakanKas,
)

data class ShiftCloseResult(
    val shift: ShiftKasir,
    val movement: PergerakanKas,
)

object CashDrawerEngine {
    const val STATUS_AKTIF = "AKTIF"
    const val STATUS_SELESAI = "SELESAI"
    const val KATEGORI_MANUAL = "MANUAL"
    const val KATEGORI_ADMIN_WITHDRAW = "ADMIN_WITHDRAW"
    const val KATEGORI_OPENING = "OPENING"
    const val KATEGORI_CARRY_OVER = "CARRY_OVER"
    const val KATEGORI_CLOSING = "CLOSING"

    fun startShift(
        shifts: List<ShiftKasir>,
        config: ShiftConfig,
        rentalUsername: String,
        username: String,
        role: String,
        now: ZonedDateTime,
        openingCash: Int? = null,
        catatan: String = "",
    ): ShiftActionResult<ShiftStartResult> {
        val cashier = username.trim()
        if (cashier.isBlank()) return ShiftActionResult(error = "Sesi login tidak valid")
        val rental = rentalUsername.trim().ifBlank { cashier }
        val active = shifts.firstOrNull { it.aktif && sameRental(it, rental) }
        if (active != null) {
            return ShiftActionResult(error = "Masih ada shift aktif: ${active.username} (${active.id})")
        }
        val nowMillis = now.toInstant().toEpochMilli()
        val previous = shifts
            .filter { !it.aktif && it.selesai > 0L && sameRental(it, rental) }
            .maxByOrNull { it.selesai }
        val opening = openingCash
            ?: return ShiftActionResult(error = "Masukkan jumlah uang laci untuk membuka shift")
        if (opening < 0) return ShiftActionResult(error = "Kas awal tidak boleh negatif")
        var id = "SHIFT-$nowMillis"
        if (shifts.any { it.id == id }) id = "$id-${shifts.size + 1}"
        val shift = ShiftKasir(
            id = id,
            username = cashier,
            mulai = nowMillis,
            kasAwal = opening,
            status = STATUS_AKTIF,
            catatan = catatan.trim(),
            tanggalOperasional = config.tanggalOperasional(now).toString(),
            jenisShift = config.jenisShift(now),
            rentalUsername = rental,
            previousShiftId = previous?.id.orEmpty(),
        )
        val movement = PergerakanKas(
            id = if (previous == null) "KAS-$id-OPEN" else "KAS-$id-CARRY",
            shiftId = id,
            username = cashier,
            waktu = nowMillis,
            tipe = "MASUK",
            nominal = opening,
            keterangan = if (previous == null) {
                "Kas awal shift"
            } else {
                "Saldo laci dari shift ${previous.id}"
            },
            kategori = if (previous == null) KATEGORI_OPENING else KATEGORI_CARRY_OVER,
            referenceId = previous?.id.orEmpty(),
            saldoSetelah = opening,
        )
        return ShiftActionResult(data = ShiftStartResult(shift, movement))
    }

    fun endShift(
        shifts: List<ShiftKasir>,
        transactions: List<Transaksi>,
        movements: List<PergerakanKas>,
        shiftId: String,
        uangFisik: Int,
        closedBy: String,
        role: String,
        nowMillis: Long,
        catatan: String = "",
    ): ShiftActionResult<ShiftCloseResult> {
        val target = shifts.firstOrNull { it.id == shiftId }
            ?: return ShiftActionResult(error = "Shift aktif tidak ditemukan")
        if (!target.aktif) return ShiftActionResult(error = "Shift sudah ditutup")
        val isAdmin = role.equals("admin", ignoreCase = true)
        val isOwner = target.username.equals(closedBy, ignoreCase = true)
        if (!isAdmin && !isOwner) {
            return ShiftActionResult(error = "Hanya admin yang bisa menutup shift kasir lain")
        }
        if (uangFisik < 0) return ShiftActionResult(error = "Uang fisik tidak boleh negatif")
        val metrics = metrics(target, transactions, movements, uangFisik)
        val closed = target.copy(
            selesai = nowMillis,
            kasAkhir = uangFisik,
            kasSeharusnya = metrics.kasSeharusnya,
            selisih = metrics.selisih,
            totalPenjualanTunai = metrics.penjualanTunai,
            totalTransaksi = metrics.totalTransaksi,
            totalPaket = metrics.totalPaket,
            totalFnb = metrics.totalFnb,
            totalQris = metrics.totalQris,
            kasMasuk = metrics.kasMasuk,
            kasKeluar = metrics.kasKeluar,
            totalAdminWithdraw = metrics.adminWithdrawals,
            status = STATUS_SELESAI,
            catatan = catatan.trim().ifBlank { target.catatan },
            ditutupOleh = closedBy,
        )
        val movement = PergerakanKas(
            id = "KAS-${target.id}-CLOSE",
            shiftId = target.id,
            username = closedBy,
            waktu = nowMillis,
            tipe = "MASUK",
            nominal = uangFisik,
            keterangan = "Tutup shift, uang fisik ${formatRupiah(uangFisik)}",
            kategori = KATEGORI_CLOSING,
            referenceId = target.id,
            saldoSetelah = uangFisik,
        )
        return ShiftActionResult(data = ShiftCloseResult(closed, movement))
    }

    fun addTransaction(
        tx: Transaksi,
        shifts: List<ShiftKasir>,
        config: ShiftConfig,
        rentalUsername: String,
        username: String,
        now: ZonedDateTime,
    ): ShiftActionResult<Transaksi> {
        if (!tx.sudahBayar) return ShiftActionResult(data = tx.copy(shiftId = ""))
        val rental = rentalUsername.trim().ifBlank { username.trim() }
        val active = shifts.firstOrNull { it.aktif && sameRental(it, rental) }
            ?: return ShiftActionResult(error = "Shift belum dibuka")
        val waktuBayar = tx.waktuBayar.ifBlank { TimeUtils.toUtcIso(now.toInstant()) }
        val zoned = TimeUtils.parseInstant(waktuBayar)?.atZone(TimeUtils.WIB) ?: now
        return ShiftActionResult(
            data = tx.copy(
                shiftId = active.id,
                kasir = tx.kasir.ifBlank { username.trim() },
                waktuBayar = waktuBayar,
                tanggalOperasional = tx.tanggalOperasional.ifBlank {
                    config.tanggalOperasional(zoned).toString()
                },
                jenisShift = tx.jenisShift.ifBlank { config.jenisShift(zoned) },
            ),
        )
    }

    fun adminWithdraw(
        shifts: List<ShiftKasir>,
        transactions: List<Transaksi>,
        movements: List<PergerakanKas>,
        shiftId: String?,
        rentalUsername: String,
        adminUsername: String,
        role: String,
        nominal: Int,
        nowMillis: Long,
        keterangan: String = "",
    ): ShiftActionResult<PergerakanKas> {
        if (!role.equals("admin", ignoreCase = true)) {
            return ShiftActionResult(error = "Hanya admin yang dapat menarik uang laci")
        }
        if (nominal <= 0) return ShiftActionResult(error = "Nominal harus lebih dari 0")
        val rental = rentalUsername.trim().ifBlank { adminUsername.trim() }
        val target = if (!shiftId.isNullOrBlank()) {
            shifts.firstOrNull { it.id == shiftId }
        } else {
            shifts.firstOrNull { it.aktif && sameRental(it, rental) }
        } ?: return ShiftActionResult(error = "Shift aktif tidak ditemukan")
        if (!target.aktif) return ShiftActionResult(error = "Shift sudah ditutup")
        if (!sameRental(target, rental)) {
            return ShiftActionResult(error = "Shift bukan milik rental ini")
        }
        val expected = metrics(target, transactions, movements, 0).kasSeharusnya
        if (nominal > expected) {
            return ShiftActionResult(error = "Nominal melebihi saldo laci ${formatRupiah(expected)}")
        }
        val sequence = movements.count {
            it.shiftId == target.id && it.kategori == KATEGORI_ADMIN_WITHDRAW
        } + 1
        val movement = PergerakanKas(
            id = "KAS-${target.id}-WD-$sequence",
            shiftId = target.id,
            username = adminUsername.trim(),
            waktu = nowMillis,
            tipe = "KELUAR",
            nominal = nominal,
            keterangan = keterangan.trim(),
            kategori = KATEGORI_ADMIN_WITHDRAW,
            saldoSetelah = expected - nominal,
        )
        return ShiftActionResult(data = movement)
    }

    fun summary(
        shifts: List<ShiftKasir>,
        transactions: List<Transaksi>,
        movements: List<PergerakanKas>,
        rentalUsername: String,
        nowMillis: Long,
    ): CashDrawerSummary {
        val rental = rentalUsername.trim()
        val shift = shifts.firstOrNull { it.aktif && sameRental(it, rental) }
            ?: return CashDrawerSummary(
                status = "TUTUP",
                updatedAt = nowMillis,
            )
        val metrics = metrics(shift, transactions, movements, 0)
        return CashDrawerSummary(
            status = STATUS_AKTIF,
            shiftId = shift.id,
            shiftUsername = shift.username,
            jenisShift = shift.jenisShift,
            startedAt = shift.mulai,
            openingCash = shift.kasAwal,
            cashExpected = metrics.kasSeharusnya,
            cashSales = metrics.penjualanTunai,
            cashIn = metrics.kasMasuk,
            cashOut = metrics.kasKeluar,
            updatedAt = nowMillis,
        )
    }

    fun metrics(
        shift: ShiftKasir,
        transactions: List<Transaksi>,
        movements: List<PergerakanKas>,
        uangFisik: Int,
    ): ShiftMetrics {
        val scoped = transactions.filter { tx ->
            val matchesShift = if (shift.rentalUsername.isNotBlank()) {
                tx.shiftId == shift.id
            } else {
                val end = if (shift.selesai > 0L) shift.selesai else Long.MAX_VALUE
                tx.shiftId.isBlank() &&
                    tx.kasir.equals(shift.username, ignoreCase = true) &&
                    (waktuBayarMs(tx)?.let { it in shift.mulai..end } == true)
            }
            matchesShift && tx.sudahBayar && !isBatal(tx)
        }
        val tunai = scoped.filter { isTunai(it) }
        val qris = scoped.filter { !isTunai(it) }
        val movement = movements.filter { it.shiftId == shift.id }
        val manual = movement.filter {
            it.kategori.isBlank() || it.kategori == KATEGORI_MANUAL
        }
        val kasMasuk = manual.filter { it.tipe.equals("MASUK", ignoreCase = true) }.sumOf { it.nominal }
        val manualKeluar = manual.filter { it.tipe.equals("KELUAR", ignoreCase = true) }.sumOf { it.nominal }
        val adminWithdrawals = movement
            .filter { it.kategori == KATEGORI_ADMIN_WITHDRAW }
            .sumOf { it.nominal }
        val kasKeluar = manualKeluar + adminWithdrawals
        val penjualanTunai = tunai.sumOf { it.total }
        val kasSeharusnya = shift.kasAwal + penjualanTunai + kasMasuk - kasKeluar
        return ShiftMetrics(
            shiftId = shift.id,
            kasAwal = shift.kasAwal,
            penjualanTunai = penjualanTunai,
            kasMasuk = kasMasuk,
            kasKeluar = kasKeluar,
            kasSeharusnya = kasSeharusnya,
            totalTransaksi = scoped.size,
            totalPaket = scoped.sumOf { it.paketTotal() },
            totalFnb = scoped.sumOf { it.fnbTotal() },
            totalQris = qris.sumOf { it.total },
            uangFisik = uangFisik,
            selisih = uangFisik - kasSeharusnya,
            adminWithdrawals = adminWithdrawals,
        )
    }

    fun isTunai(t: Transaksi): Boolean =
        t.metode.equals("Tunai", ignoreCase = true) ||
            t.metode.equals("Cash", ignoreCase = true) ||
            (t.metode.isBlank() && t.cash > 0)

    fun isBatal(t: Transaksi): Boolean =
        t.kategori.equals("BATAL", ignoreCase = true) ||
            t.metode.equals("BATAL", ignoreCase = true) ||
            t.paket.startsWith("BATAL", ignoreCase = true)

    fun sameRental(shift: ShiftKasir, rentalUsername: String): Boolean =
        shift.rentalUsername.isBlank() ||
            shift.rentalUsername.equals(rentalUsername.trim(), ignoreCase = true)

    fun formatRupiah(value: Int): String =
        "Rp " + String.format(java.util.Locale("id", "ID"), "%,d", value)

    private fun waktuBayarMs(t: Transaksi): Long? =
        TimeUtils.parseInstant(t.waktuBayar)?.toEpochMilli()
            ?: TimeUtils.transactionInstant(t.id, t.waktu)?.toEpochMilli()
}
