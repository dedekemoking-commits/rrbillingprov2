package com.billingps.owner.data

import com.billingps.owner.util.OwnerTime
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

enum class ReportPeriod(val label: String) {
    TODAY("Hari Ini"),
    WEEK("7 Hari"),
    THIRTY("30 Hari"),
    MONTH("Bulan Ini"),
    ALL("Semua"),
}

data class DayRevenue(
    val key: String,
    val label: String,
    val total: Int,
)

data class ReportSummary(
    val omzet: Int = 0,
    val jumlah: Int = 0,
    val rataRata: Int = 0,
    val makanan: Int = 0,
    val minuman: Int = 0,
    val tv: Int = 0,
    val pc: Int = 0,
    val belumLunas: Int = 0,
)

fun OwnerTransaction.fnbTotal(): Int =
    pesanan.entries.sumOf { (name, qty) ->
        (pesananHarga[name] ?: MenuCatalog.price(name)) * qty
    }

fun OwnerTransaction.paketTotal(): Int =
    (total - fnbTotal()).coerceAtLeast(0)

fun OwnerTransaction.makananTotal(): Int =
    pesanan.entries.sumOf { (name, qty) ->
        if (MenuCatalog.isMakanan(name)) {
            (pesananHarga[name] ?: MenuCatalog.price(name)) * qty
        } else {
            0
        }
    }

fun OwnerTransaction.minumanTotal(): Int =
    pesanan.entries.sumOf { (name, qty) ->
        if (MenuCatalog.isMinuman(name)) {
            (pesananHarga[name] ?: MenuCatalog.price(name)) * qty
        } else {
            0
        }
    }

fun OwnerTransaction.matchesSearch(query: String): Boolean {
    val q = query.trim().lowercase(Locale.ROOT)
    if (q.isEmpty()) return true
    val haystack = listOf(
        id, waktu, kasir, kota, paket, tvJenisPs, metode, total.toString(),
        OwnerTime.formatWib(id, waktu),
    ).joinToString(" ").lowercase(Locale.ROOT)
    return haystack.contains(q)
}

fun inPeriod(tx: OwnerTransaction, period: ReportPeriod, now: Instant = Instant.now()): Boolean {
    val instant = OwnerTime.transactionInstant(tx.id, tx.waktu) ?: return period == ReportPeriod.ALL
    val nowWib = now.atZone(OwnerTime.WIB)
    val txWib = instant.atZone(OwnerTime.WIB)
    return when (period) {
        ReportPeriod.TODAY -> txWib.toLocalDate() == nowWib.toLocalDate()
        ReportPeriod.WEEK -> instant >= now.minus(7, ChronoUnit.DAYS)
        ReportPeriod.THIRTY -> instant >= now.minus(30, ChronoUnit.DAYS)
        ReportPeriod.MONTH ->
            txWib.year == nowWib.year && txWib.monthValue == nowWib.monthValue
        ReportPeriod.ALL -> true
    }
}

fun filteredTransactions(
    transactions: List<OwnerTransaction>,
    period: ReportPeriod,
    search: String,
): List<OwnerTransaction> =
    transactions
        .filter { inPeriod(it, period) && it.matchesSearch(search) }
        .sortedByDescending { OwnerTime.transactionInstant(it.id, it.waktu) ?: Instant.EPOCH }

fun summarize(transactions: List<OwnerTransaction>): ReportSummary {
    var omzet = 0
    var makanan = 0
    var minuman = 0
    var tv = 0
    var pc = 0
    var belumLunas = 0
    transactions.forEach { tx ->
        omzet += tx.total
        makanan += tx.makananTotal()
        minuman += tx.minumanTotal()
        if (tx.sumber == "PC") pc += tx.total else tv += tx.total
        if (!tx.sudahBayar) belumLunas += tx.total
    }
    return ReportSummary(
        omzet = omzet,
        jumlah = transactions.size,
        rataRata = if (transactions.isEmpty()) 0 else omzet / transactions.size,
        makanan = makanan,
        minuman = minuman,
        tv = tv,
        pc = pc,
        belumLunas = belumLunas,
    )
}

fun dailyRevenue(
    transactions: List<OwnerTransaction>,
    days: Int = 7,
    now: Instant = Instant.now(),
): List<DayRevenue> {
    val dayFormat = DateTimeFormatter.ofPattern("EEE dd/MM", Locale("id", "ID"))
    val dayKeyFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)
    val today = now.atZone(OwnerTime.WIB).toLocalDate()
    val keys = (days - 1 downTo 0).map { offset ->
        val date = today.minusDays(offset.toLong())
        date to date.format(dayKeyFormat)
    }
    val totals = keys.associate { (_, key) -> key to 0 }.toMutableMap()
    transactions.forEach { tx ->
        val key = OwnerTime.dayKey(tx.id, tx.waktu) ?: return@forEach
        if (totals.containsKey(key)) totals[key] = (totals[key] ?: 0) + tx.total
    }
    return keys.map { (date, key) ->
        DayRevenue(
            key = key,
            label = date.format(dayFormat),
            total = totals[key] ?: 0,
        )
    }
}

fun topPackages(
    transactions: List<OwnerTransaction>,
    limit: Int = 6,
): List<Pair<String, Int>> {
    val totals = mutableMapOf<String, Int>()
    transactions.forEach { tx ->
        val name = tx.paket.ifBlank { "Tanpa Paket" }
        totals[name] = (totals[name] ?: 0) + tx.total
    }
    return totals.entries
        .sortedByDescending { it.value }
        .take(limit)
        .map { it.key to it.value }
}

fun paymentBreakdown(
    transactions: List<OwnerTransaction>,
): List<Pair<String, Int>> {
    val totals = mutableMapOf<String, Int>()
    transactions.forEach { tx ->
        val name = tx.metode.ifBlank { if (tx.sudahBayar) "Lunas" else "Belum Lunas" }
        totals[name] = (totals[name] ?: 0) + tx.total
    }
    return totals.entries
        .sortedByDescending { it.value }
        .map { it.key to it.value }
}
