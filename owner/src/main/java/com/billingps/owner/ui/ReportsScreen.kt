package com.billingps.owner.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.billingps.owner.data.DayRevenue
import com.billingps.owner.data.OwnerTransaction
import com.billingps.owner.data.ReportPeriod
import com.billingps.owner.data.dailyRevenue
import com.billingps.owner.data.filteredTransactions
import com.billingps.owner.data.fnbTotal
import com.billingps.owner.data.makananTotal
import com.billingps.owner.data.minumanTotal
import com.billingps.owner.data.paketTotal
import com.billingps.owner.data.paymentBreakdown
import com.billingps.owner.data.summarize
import com.billingps.owner.data.topPackages
import com.billingps.owner.util.OwnerTime
import java.util.Locale

@Composable
fun ReportsScreen(
    state: OwnerUiState,
    onPeriodChange: (ReportPeriod) -> Unit,
    onSearchChange: (String) -> Unit,
) {
    val context = LocalContext.current
    val filtered = filteredTransactions(state.transactions, state.period, state.search)
    val summary = summarize(filtered)
    val daily = dailyRevenue(state.transactions, 7)
    val packages = topPackages(filtered)
    val payments = paymentBreakdown(filtered)
    var detailTx by remember { mutableStateOf<OwnerTransaction?>(null) }
    val csvText = remember(filtered) { buildCsv(filtered) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(csvText.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "CSV laporan tersimpan", Toast.LENGTH_SHORT).show()
            } catch (t: Throwable) {
                Toast.makeText(context, "Gagal menyimpan CSV: ${t.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ReportPeriod.values()) { period ->
                    FilterChip(
                        selected = state.period == period,
                        onClick = { onPeriodChange(period) },
                        label = { Text(period.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = OwnerPanel2,
                            labelColor = OwnerMuted,
                            selectedContainerColor = OwnerPurple,
                            selectedLabelColor = Color.White,
                        ),
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard(
                    label = "Omzet",
                    value = rupiah(summary.omzet),
                    accent = OwnerGreen,
                    icon = Icons.Default.Payments,
                    sub = "${summary.jumlah} transaksi",
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    label = "Transaksi",
                    value = summary.jumlah.toString(),
                    accent = OwnerCyan,
                    icon = Icons.Default.ReceiptLong,
                    sub = "periode terpilih",
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard(
                    label = "Rata-rata",
                    value = rupiah(summary.rataRata),
                    accent = OwnerPurpleLight,
                    icon = Icons.Default.TrendingUp,
                    sub = "per transaksi",
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    label = "Belum Lunas",
                    value = rupiah(summary.belumLunas),
                    accent = OwnerRed,
                    icon = Icons.Default.Warning,
                    sub = "piutang periode",
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard(
                    label = "Makanan",
                    value = rupiah(summary.makanan),
                    accent = OwnerAmber,
                    icon = Icons.Default.Fastfood,
                    sub = "F&B makanan",
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    label = "Minuman",
                    value = rupiah(summary.minuman),
                    accent = OwnerPink,
                    icon = Icons.Default.LocalDrink,
                    sub = "F&B minuman",
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard(
                    label = "Omzet TV",
                    value = rupiah(summary.tv),
                    accent = OwnerPurpleLight,
                    icon = Icons.Default.Tv,
                    sub = "sumber TV",
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    label = "Omzet PC",
                    value = rupiah(summary.pc),
                    accent = OwnerCyan,
                    icon = Icons.Default.Computer,
                    sub = "sumber PC",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            SectionHeader(
                title = "Tren Omzet 7 Hari",
                subtitle = "Berdasarkan waktu WIB",
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(8.dp))
            DailyBarChart(daily)
        }

        item {
            SectionHeader(
                title = "Omzet per Paket",
                subtitle = "Top ${packages.size} paket",
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(8.dp))
            if (packages.isEmpty()) {
                EmptyState("Belum ada data paket pada periode ini.")
            } else {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = OwnerPanel),
                    border = BorderStroke(1.dp, OwnerBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        val max = packages.maxOf { it.second }.coerceAtLeast(1)
                        packages.forEach { (name, total) ->
                            BarRow(
                                label = name,
                                value = total,
                                max = max,
                                color = OwnerPurpleLight,
                            )
                        }
                    }
                }
            }
        }

        item {
            SectionHeader(
                title = "Metode Pembayaran",
                subtitle = "Distribusi omzet",
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(8.dp))
            if (payments.isEmpty()) {
                EmptyState("Belum ada data pembayaran.")
            } else {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = OwnerPanel),
                    border = BorderStroke(1.dp, OwnerBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        val max = payments.maxOf { it.second }.coerceAtLeast(1)
                        val colors = listOf(OwnerGreen, OwnerCyan, OwnerAmber, OwnerPink, OwnerPurpleLight)
                        payments.forEachIndexed { index, (name, total) ->
                            BarRow(
                                label = name,
                                value = total,
                                max = max,
                                color = colors[index % colors.size],
                            )
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionHeader(
                    title = "Transaksi",
                    subtitle = "${filtered.size} transaksi • ${state.period.label}",
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        val safeUser = state.username.ifBlank { "rental" }
                            .replace(Regex("[^A-Za-z0-9_-]"), "_")
                        exportLauncher.launch(
                            "laporan_${safeUser}_${state.period.name.lowercase(Locale.ROOT)}.csv",
                        )
                    },
                    enabled = filtered.isNotEmpty(),
                ) {
                    Icon(
                        Icons.Default.FileDownload,
                        contentDescription = null,
                        tint = if (filtered.isEmpty()) OwnerMuted else OwnerCyan,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        "CSV",
                        color = if (filtered.isEmpty()) OwnerMuted else OwnerCyan,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.search,
                onValueChange = onSearchChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Cari waktu, TV, paket, kasir...") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = OwnerMuted)
                },
                trailingIcon = {
                    if (state.search.isNotBlank()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = null, tint = OwnerMuted)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )
        }

        if (filtered.isEmpty()) {
            item {
                EmptyState("Belum ada transaksi pada periode/filter ini.")
            }
        } else {
            items(filtered.take(300), key = { it.id.ifBlank { it.waktu + it.total } }) { tx ->
                TransactionRow(tx = tx, onClick = { detailTx = tx })
            }
            if (filtered.size > 300) {
                item {
                    Text(
                        "Menampilkan 300 dari ${filtered.size} transaksi. Persempit filter atau ekspor CSV untuk data lengkap.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OwnerMuted,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
            }
        }

        item {
            HorizontalDivider(color = OwnerBorder)
            Spacer(Modifier.height(8.dp))
            Text(
                buildString {
                    append("Sumber: ")
                    append(state.dataSource.ifBlank { "memuat..." })
                    append("  •  ${state.transactions.size} transaksi tersimpan")
                    append("  •  auto-refresh 60 detik")
                },
                style = MaterialTheme.typography.bodySmall,
                color = OwnerMuted,
            )
        }
    }

    detailTx?.let { tx ->
        TransactionDetailDialog(tx = tx, onDismiss = { detailTx = null })
    }
}

@Composable
private fun DailyBarChart(daily: List<DayRevenue>) {
    val max = daily.maxOfOrNull { it.total }?.coerceAtLeast(1) ?: 1
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = OwnerPanel),
        border = BorderStroke(1.dp, OwnerBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Total 7 hari",
                    style = MaterialTheme.typography.labelMedium,
                    color = OwnerMuted,
                )
                Text(
                    rupiah(daily.sumOf { it.total }),
                    style = MaterialTheme.typography.bodyMedium,
                    color = OwnerGreen,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                daily.forEach { day ->
                    val fraction = (day.total.toFloat() / max.toFloat()).coerceIn(0f, 1f)
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((110f * fraction).dp.coerceAtLeast(2.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(OwnerPurpleLight, OwnerPurple),
                                    ),
                                    RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp),
                                ),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            day.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = OwnerMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            fontSize = 8.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BarRow(label: String, value: Int, max: Int, color: Color) {
    Column(modifier = Modifier.padding(vertical = 5.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = OwnerText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                rupiah(value),
                style = MaterialTheme.typography.bodySmall,
                color = color,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = (value.toFloat() / max.toFloat()).coerceIn(0f, 1f),
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = color,
            trackColor = OwnerBorder,
        )
    }
}

@Composable
private fun TransactionRow(tx: OwnerTransaction, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = OwnerPanel),
        border = BorderStroke(1.dp, OwnerBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        OwnerTime.formatWib(tx.id, tx.waktu),
                        style = MaterialTheme.typography.bodyMedium,
                        color = OwnerText,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.size(7.dp))
                    SourceBadge(tx.sumber)
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    buildString {
                        append(tx.paket.ifBlank { "Tanpa Paket" })
                        if (tx.kasir.isNotBlank()) append("  •  ${tx.kasir}")
                        if (tx.kota.isNotBlank()) append("  •  ${tx.kota}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = OwnerMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(5.dp))
                PaymentBadge(paid = tx.sudahBayar, metode = tx.metode)
            }
            Spacer(Modifier.size(10.dp))
            Text(
                rupiah(tx.total),
                style = MaterialTheme.typography.titleMedium,
                color = OwnerGreen,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun TransactionDetailDialog(tx: OwnerTransaction, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OwnerPanel,
        title = {
            Text(
                "Detail Transaksi",
                color = OwnerText,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                InfoLine("ID", tx.id.ifBlank { "-" })
                InfoLine("Waktu", OwnerTime.formatWibFull(tx.id, tx.waktu))
                InfoLine("Sumber", tx.sumber)
                InfoLine("Kasir", tx.kasir.ifBlank { "-" })
                InfoLine("Kota", tx.kota.ifBlank { "-" })
                InfoLine("Paket", tx.paket.ifBlank { "Tanpa Paket" })
                InfoLine("Harga Paket", rupiah(tx.paketHarga))
                InfoLine("Total F&B", rupiah(tx.fnbTotal()))
                InfoLine("Makanan", rupiah(tx.makananTotal()), OwnerAmber)
                InfoLine("Minuman", rupiah(tx.minumanTotal()), OwnerPink)
                InfoLine("Total", rupiah(tx.total), OwnerGreen)
                InfoLine("Metode", tx.metode.ifBlank { "-" })
                InfoLine(
                    "Status",
                    if (tx.sudahBayar) "Lunas" else "Belum Lunas",
                    if (tx.sudahBayar) OwnerGreen else OwnerRed,
                )
                if (tx.pesanan.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = OwnerBorder)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Rincian Pesanan",
                        style = MaterialTheme.typography.labelMedium,
                        color = OwnerPurpleLight,
                        fontWeight = FontWeight.Bold,
                    )
                    tx.pesanan.forEach { (name, qty) ->
                        val price = tx.pesananHarga[name] ?: 0
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                        ) {
                            Text(
                                "$name x$qty",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = OwnerText,
                            )
                            Text(
                                if (price > 0) rupiah(price * qty) else "-",
                                style = MaterialTheme.typography.bodySmall,
                                color = OwnerMuted,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup", color = OwnerGreen)
            }
        },
    )
}

private fun buildCsv(transactions: List<OwnerTransaction>): String {
    val header = listOf(
        "ID", "Waktu WIB", "Kasir", "Kota", "Sumber", "Paket", "Harga Paket",
        "Makanan", "Minuman", "Total", "Metode", "Lunas",
    )
    val lines = mutableListOf(header.joinToString(",") { csvEscape(it) })
    transactions.forEach { tx ->
        lines.add(
            listOf(
                tx.id,
                OwnerTime.formatWibFull(tx.id, tx.waktu),
                tx.kasir,
                tx.kota,
                tx.sumber,
                tx.paket,
                tx.paketHarga.toString(),
                tx.makananTotal().toString(),
                tx.minumanTotal().toString(),
                tx.total.toString(),
                tx.metode,
                if (tx.sudahBayar) "Lunas" else "Belum Lunas",
            ).joinToString(",") { csvEscape(it) },
        )
    }
    return lines.joinToString("\r\n")
}

private fun csvEscape(value: String): String =
    "\"" + value.replace("\"", "\"\"") + "\""
