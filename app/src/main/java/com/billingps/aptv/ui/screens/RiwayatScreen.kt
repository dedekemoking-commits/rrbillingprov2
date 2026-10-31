package com.billingps.aptv.ui.screens

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.util.Log
import com.billingps.aptv.models.MainViewModel
import com.billingps.aptv.models.Transaksi
import com.billingps.aptv.models.fnbTotal
import com.billingps.aptv.models.paketTotal
import com.billingps.aptv.ui.charts.*
import com.billingps.aptv.ui.theme.*
import com.billingps.aptv.utils.TimeUtils

@Composable
fun RiwayatScreen(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsState()
    val ctx = LocalContext.current
    var detailTransaksi by remember { mutableStateOf<Transaksi?>(null) }
    var cetakMsg by remember { mutableStateOf("") }
    // Filter mode: "DAY", "WEEK", "MONTH", "ALL", "DATE"
    var filterMode by remember { mutableStateOf("DAY") }
    var filterDate by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var filterBayar by remember { mutableStateOf("ALL") } // ALL, PAID, UNPAID
    val allList = state.transaksiList.sortedByDescending {
        TimeUtils.transactionInstant(it.id, it.waktu) ?: java.time.Instant.EPOCH
    }
    val nowWib = java.time.ZonedDateTime.now(TimeUtils.WIB)
    fun inDay(tx: Transaksi): Boolean =
        TimeUtils.transactionZoned(tx.id, tx.waktu)?.toLocalDate() == nowWib.toLocalDate()
    fun inWeek(tx: Transaksi): Boolean {
        val d = TimeUtils.transactionZoned(tx.id, tx.waktu) ?: return false
        val start = nowWib.toLocalDate().minusDays((nowWib.dayOfWeek.value - 1).toLong())
        return !d.toLocalDate().isBefore(start)
    }
    fun inMonth(tx: Transaksi): Boolean {
        val d = TimeUtils.transactionZoned(tx.id, tx.waktu) ?: return false
        return d.year == nowWib.year && d.monthValue == nowWib.monthValue
    }
    fun inDate(tx: Transaksi, dateYmd: String): Boolean {
        val d = TimeUtils.transactionZoned(tx.id, tx.waktu) ?: return false
        return d.toLocalDate().toString() == dateYmd
    }
    val txList = remember(allList, filterMode, searchQuery, filterDate, filterBayar) {
        val timeFiltered = when (filterMode) {
            "DAY" -> allList.filter { inDay(it) }
            "WEEK" -> allList.filter { inWeek(it) }
            "MONTH" -> allList.filter { inMonth(it) }
            "DATE" -> allList.filter { filterDate.isNotEmpty() && inDate(it, filterDate) }
            else -> allList
        }
        val bayarFiltered = when (filterBayar) {
            "PAID" -> timeFiltered.filter { it.sudahBayar }
            "UNPAID" -> timeFiltered.filter { !it.sudahBayar }
            else -> timeFiltered
        }
        if (searchQuery.isBlank()) bayarFiltered
        else bayarFiltered.filter { t ->
            t.kota.contains(searchQuery, ignoreCase = true) ||
            t.kasir.contains(searchQuery, ignoreCase = true) ||
            t.paket.contains(searchQuery, ignoreCase = true)
        }
    }
    val totalPendapatan = txList.sumOf { it.total }
    val totalUtang = allList.filter { !it.sudahBayar }.sumOf { it.total }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        if (uri != null) {
            try {
                val header = "ID,Waktu,TanggalOperasional,Shift,Kasir,TV,Paket,PaketHarga,FnbHarga,Total,Status,Metode\n"
                val rows = txList.joinToString("\n") { t ->
                    listOf(
                        t.id,
                        t.waktu,
                        t.tanggalOperasional,
                        t.jenisShift,
                        t.kasir,
                        t.kota,
                        t.paket,
                        t.paketTotal().toString(),
                        t.fnbTotal().toString(),
                        t.total.toString(),
                        if (t.sudahBayar) "LUNAS" else "BELUM",
                        t.metode,
                    ).joinToString(",")
                }
                ctx.contentResolver.openOutputStream(uri)?.use { it.write((header + rows).toByteArray(Charsets.UTF_8)) }
                Toast.makeText(ctx, "Tersimpan", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(ctx, "Gagal export: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
        Log.i("RiwayatScreen", "rendering RiwayatScreen, total transactions=${state.transaksiList.size}")
        Row(
            modifier = Modifier.fillMaxWidth().background(DarkSurface).padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("RIWAYAT TRANSAKSI", style = MaterialTheme.typography.titleLarge, color = NeonGreen)

            // Cloud status icon (per-user) di kanan atas
            val cloudColor = when (state.cloudStatus) {
                "connected" -> NeonGreen
                "syncing" -> NeonYellow
                "error" -> NeonRed
                else -> TextSecondary
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // show last sync time as small text when available
                if (state.cloudLastSync > 0L) {
                    Text(
                        java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(state.cloudLastSync)),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                IconButton(onClick = { viewModel.syncToCloud() }, enabled = state.cloudStatus != "syncing") {
                    Icon(Icons.Filled.Cloud, contentDescription = "Sync Cloud", tint = cloudColor)
                }
            }
        }

        // DEBUG HELP: show simple text labels for filters so uiautomator can detect them even if buttons fail to render
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("FILTERS:", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Text("HARI", style = MaterialTheme.typography.bodySmall, color = NeonGreen)
            Text("MINGGU", style = MaterialTheme.typography.bodySmall, color = NeonGreen)
            Text("BULAN", style = MaterialTheme.typography.bodySmall, color = NeonGreen)
        }

        // Analytics: summary cards + chart toggle
        var showAnalytics by remember { mutableStateOf(true) }
        Row(
            modifier = Modifier.fillMaxWidth().background(DarkSurface).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = DarkSurfaceV2), border = BorderStroke(1.dp, DarkSurfaceV3)) {
                Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Transaksi", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Text("${txList.size}", style = MaterialTheme.typography.titleLarge, color = NeonYellow)
                }
            }
            Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = DarkSurfaceV2), border = BorderStroke(1.dp, DarkSurfaceV3)) {
                Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Pendapatan", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Text(fmtRp(totalPendapatan), style = MaterialTheme.typography.titleLarge, color = NeonGreen)
                }
            }
            if (totalUtang > 0) {
                Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = DarkSurfaceV2), border = BorderStroke(1.dp, NeonRed)) {
                    Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Utang", style = MaterialTheme.typography.bodySmall, color = NeonRed)
                        Text(fmtRp(totalUtang), style = MaterialTheme.typography.titleLarge, color = NeonRed)
                    }
                }
            }
            Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = DarkSurfaceV2), border = BorderStroke(1.dp, DarkSurfaceV3), onClick = { showAnalytics = !showAnalytics }) {
                Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Analitik", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Icon(if (showAnalytics) Icons.Filled.Leaderboard else Icons.Filled.Leaderboard, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
                }
            }
        }

        // Charts section (collapsible)
        if (showAnalytics && txList.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().background(DarkSurface).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Daily revenue bar chart (last 7 days from filtered data)
                val dailyRevenue = remember(txList) {
                    val dayTotal = mutableMapOf<String, Int>()
                    txList.forEach { tx ->
                        val day = TimeUtils.wibDayKey(tx.id, tx.waktu) ?: return@forEach
                        dayTotal[day] = (dayTotal[day] ?: 0) + tx.total
                    }
                    dayTotal.entries.sortedBy { it.key }.takeLast(7).map { BarData(it.key.takeLast(5), it.value.toFloat()) }
                }
                if (dailyRevenue.isNotEmpty()) {
                    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = DarkSurfaceV2), border = BorderStroke(1.dp, DarkSurfaceV3)) {
                        Column(Modifier.padding(12.dp)) {
                            RevenueBarChart(data = dailyRevenue, title = "PENDAPATAN HARIAN")
                        }
                    }
                }

                // PS Type Revenue
                var showPsDetail by remember { mutableStateOf(false) }
                val psRevenue = remember(txList, state.tvList) {
                    val psTotal = mutableMapOf<String, Int>()
                    txList.forEach { tx ->
                        val ps = state.tvList.find { it.nama == tx.kota }?.jenisPs ?: "Unknown"
                        psTotal[ps] = (psTotal[ps] ?: 0) + tx.total
                    }
                    psTotal.entries.sortedByDescending { it.value }
                }
                if (psRevenue.isNotEmpty()) {
                    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = DarkSurfaceV2), border = BorderStroke(1.dp, DarkSurfaceV3)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("PENDAPATAN PER PS", style = MaterialTheme.typography.labelSmall, color = NeonCyan, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            psRevenue.forEach { (ps, total) ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                    Text(ps, style = MaterialTheme.typography.bodySmall, color = TextPrimary, modifier = Modifier.weight(1f))
                                    Text(fmtRp(total), style = MaterialTheme.typography.bodySmall, color = NeonGreen, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // F&B Revenue
                val fbRevenue = remember(txList) {
                    var fbTotal = 0
                    val fbItems = mutableMapOf<String, Int>()
                    txList.forEach { tx ->
                        tx.pesanan.forEach { (name, qty) ->
                            val price = tx.pesananHarga[name] ?: 0
                            val itemTotal = price * qty
                            fbTotal += itemTotal
                            fbItems[name] = (fbItems[name] ?: 0) + qty
                        }
                    }
                    fbTotal to fbItems.entries.sortedByDescending { it.value }
                }
                if (fbRevenue.first > 0) {
                    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = DarkSurfaceV2), border = BorderStroke(1.dp, DarkSurfaceV3)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("PENDAPATAN F&B", style = MaterialTheme.typography.labelSmall, color = NeonCyan, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text(fmtRp(fbRevenue.first), style = MaterialTheme.typography.titleMedium, color = NeonYellow, fontWeight = FontWeight.Bold)
                            fbRevenue.second.take(5).forEach { (name, qty) ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                                    Text("• ${name} ×${qty}", style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                // Package distribution pie chart (clickable)
                val packageData = remember(txList) {
                    val pkgTotal = mutableMapOf<String, Int>()
                    txList.forEach { tx ->
                        val pkg = tx.paket.split("+").firstOrNull()?.take(15) ?: tx.paket
                        pkgTotal[pkg] = (pkgTotal[pkg] ?: 0) + tx.total
                    }
                    pkgTotal.entries.sortedByDescending { it.value }.take(6).mapIndexed { i, (k, v) ->
                        PieData(k, v.toFloat(), if (i < chartColorsList.size) chartColorsList[i] else chartColorsList[i % chartColorsList.size])
                    }
                }
                if (packageData.isNotEmpty()) {
                    Card(
                        shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = DarkSurfaceV2), border = BorderStroke(1.dp, DarkSurfaceV3),
                        onClick = { showPsDetail = true }
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            RevenuePieChart(data = packageData, title = "DISTRIBUSI PAKET (klik detail)")
                        }
                    }
                }
                // Dialog detail distribusi paket
                if (showPsDetail && packageData.isNotEmpty()) {
                    AlertDialog(
                        onDismissRequest = { showPsDetail = false },
                        containerColor = DarkSurface,
                        title = { Text("DETAIL DISTRIBUSI PAKET", fontWeight = FontWeight.Bold, color = NeonCyan) },
                        text = {
                            Column {
                                packageData.forEach { pd ->
                                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                        Box(Modifier.size(10.dp).background(pd.color, RoundedCornerShape(2.dp)))
                                        Spacer(Modifier.width(8.dp))
                                        Text(pd.label, style = MaterialTheme.typography.bodySmall, color = TextPrimary, modifier = Modifier.weight(1f))
                                        Text(fmtRp(pd.value.toInt()), style = MaterialTheme.typography.bodySmall, color = NeonGreen, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showPsDetail = false }) { Text("Tutup", color = NeonGreen) }
                        },
                    )
                }

                // Top packages by revenue
                val topPackages = remember(txList) {
                    val pkgTotal = mutableMapOf<String, Int>()
                    txList.forEach { tx ->
                        val pkg = tx.paket.split("+").firstOrNull()?.take(20) ?: tx.paket
                        pkgTotal[pkg] = (pkgTotal[pkg] ?: 0) + tx.total
                    }
                    pkgTotal.entries.sortedByDescending { it.value }.map { it.key to it.value }
                }
                if (topPackages.isNotEmpty()) {
                    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = DarkSurfaceV2), border = BorderStroke(1.dp, DarkSurfaceV3)) {
                        Column(Modifier.padding(12.dp)) {
                            TopPackagesList(data = topPackages, title = "PAKET TERLARIS")
                        }
                    }
                }
            }
        }

        // Filter buttons
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("DAY" to "HARI", "WEEK" to "MINGGU", "MONTH" to "BULAN", "ALL" to "SEMUA", "DATE" to "TGL").forEach { (mode, label) ->
                if (filterMode == mode) {
                    Button(
                        onClick = {
                            filterMode = mode
                            if (mode == "DATE") {
                                val cal = java.util.Calendar.getInstance()
                                DatePickerDialog(ctx, { _, y, m, d ->
                                    filterDate = "$y-${(m+1).toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}"
                                }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH)).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = DarkBackground)
                    ) { Text(if (mode == "DATE" && filterDate.isNotEmpty()) filterDate.takeLast(5) else label) }
                } else {
                    OutlinedButton(
                        onClick = {
                            filterMode = mode
                            if (mode == "DATE") {
                                val cal = java.util.Calendar.getInstance()
                                DatePickerDialog(ctx, { _, y, m, d ->
                                    filterDate = "$y-${(m+1).toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}"
                                }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH)).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        border = BorderStroke(1.dp, DarkSurfaceV3)
                    ) { Text(label) }
                }
            }
        }

        // Payment status filter
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("ALL" to "Semua", "PAID" to "Lunas", "UNPAID" to "Utang").forEach { (mode, label) ->
                if (filterBayar == mode) {
                    Button(
                        onClick = { filterBayar = mode },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (mode == "UNPAID") NeonRed else NeonGreen, contentColor = DarkBackground),
                    ) { Text(label) }
                } else {
                    OutlinedButton(
                        onClick = { filterBayar = mode },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        border = BorderStroke(1.dp, DarkSurfaceV3),
                    ) { Text(label) }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Cari TV, kasir, atau paket...", color = TextDim) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextDim) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Hapus", tint = TextDim)
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NeonCyan, unfocusedBorderColor = DarkSurfaceV3,
                cursorColor = NeonCyan,
                unfocusedContainerColor = DarkSurfaceV2, focusedContainerColor = DarkSurfaceV2,
            ),
        )

        Spacer(Modifier.height(6.dp))

        if (state.statusMessage.isNotEmpty()) {
            Text(state.statusMessage, style = MaterialTheme.typography.bodySmall, color = NeonCyan, modifier = Modifier.padding(horizontal = 12.dp))
            Spacer(Modifier.height(4.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth().background(DarkSurface).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = { viewModel.importFromCloud() },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                border = BorderStroke(1.dp, NeonCyan),
            ) { Icon(Icons.Filled.CloudDownload, contentDescription = "Import") }
            var showCetakDialog by remember { mutableStateOf(false) }
            var cetakDate by remember { mutableStateOf("") }
            OutlinedButton(
                onClick = {
                    val cal = java.util.Calendar.getInstance()
                    DatePickerDialog(ctx, { _, y, m, d ->
                        cetakDate = "$y-${(m+1).toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}"
                        showCetakDialog = true
                    }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH)).show()
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonYellow),
                border = BorderStroke(1.dp, NeonYellow),
            ) { Icon(Icons.Filled.Print, contentDescription = "Cetak") }
            if (showCetakDialog && cetakDate.isNotEmpty()) {
                val cetakList = allList.filter { inDate(it, cetakDate) }
                AlertDialog(
                    onDismissRequest = { showCetakDialog = false },
                    containerColor = DarkSurface,
                    title = { Text("CETAK LAPORAN", fontWeight = FontWeight.Bold, color = NeonYellow) },
                    text = { Text("Cetak laporan ${cetakList.size} transaksi tanggal $cetakDate? (total ${fmtRp(cetakList.sumOf { it.total })})", color = TextSecondary) },
                    confirmButton = {
                        Button(
                            onClick = {
                                showCetakDialog = false
                                Toast.makeText(ctx, "Laporan dikirim ke printer", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonYellow, contentColor = DarkBackground),
                            shape = RoundedCornerShape(10.dp),
                        ) { Text("Cetak") }
                    },
                    dismissButton = {
                        OutlinedButton(
                            onClick = { showCetakDialog = false },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                            border = BorderStroke(1.dp, TextSecondary.copy(alpha = 0.5f)),
                        ) { Text("Batal") }
                    },
                )
            }
            OutlinedButton(
                onClick = {
                    if (txList.isEmpty()) {
                        Toast.makeText(ctx, "Belum ada transaksi", Toast.LENGTH_SHORT).show()
                    } else {
                        val tgl = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
                        exportLauncher.launch("laporan_rrbillingpro_$tgl.csv")
                    }
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonGreen),
                border = BorderStroke(1.dp, NeonGreen),
            ) { Icon(Icons.Filled.FileDownload, contentDescription = "Export CSV") }
        }

        Row(
            modifier = Modifier.fillMaxWidth().background(DarkSurfaceV2).padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text("WAKTU / TV", style = MaterialTheme.typography.labelSmall, color = NeonGreen, modifier = Modifier.weight(2f))
            Text("TOTAL", style = MaterialTheme.typography.labelSmall, color = NeonGreen, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }

        if (txList.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Receipt, contentDescription = null, modifier = Modifier.size(54.dp), tint = TextDim)
                    Spacer(Modifier.height(12.dp))
                    Text("Belum ada transaksi", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                itemsIndexed(txList) { idx, tx ->
                    Row(
                        modifier = Modifier.fillMaxWidth().background(if (idx % 2 == 0) DarkBackground else DarkSurfaceV2).clickable { detailTransaksi = tx }.padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Column(modifier = Modifier.weight(2f)) {
                            Text(formatWaktu(tx.id, tx.waktu), style = MaterialTheme.typography.bodySmall, color = TextDim)
                            if (tx.tanggalOperasional.isNotBlank() || tx.jenisShift.isNotBlank()) {
                                Text(
                                    listOf(tx.tanggalOperasional, tx.jenisShift)
                                        .filter { it.isNotBlank() }
                                        .joinToString(" • "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = when (tx.jenisShift) {
                                        "Pagi" -> NeonYellow
                                        "Malam" -> NeonCyan
                                        else -> TextDim
                                    },
                                )
                            }
                            Text(tx.kota, style = MaterialTheme.typography.bodyMedium, color = NeonGreen, fontWeight = FontWeight.Bold)
                            Text(tx.paket, style = MaterialTheme.typography.bodySmall, color = NeonYellow)
                            tx.pesanan.forEach { (nm, q) ->
                                Text("• $nm ×$q", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
                            Text(fmtRp(tx.total), style = MaterialTheme.typography.titleSmall, color = NeonGreen)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                                if (tx.sudahBayar) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = "Sudah Bayar", modifier = Modifier.size(12.dp), tint = NeonGreen)
                                    Spacer(Modifier.width(2.dp))
                                    Text("Bayar", style = MaterialTheme.typography.bodySmall, color = NeonGreen)
                                } else {
                                    Icon(Icons.Filled.Cancel, contentDescription = "Belum Bayar", modifier = Modifier.size(12.dp), tint = NeonRed)
                                    Spacer(Modifier.width(2.dp))
                                    Text("Utang", style = MaterialTheme.typography.bodySmall, color = NeonRed)
                                }
                            }
                            Text(tx.kasir, style = MaterialTheme.typography.bodySmall, color = TextDim)
                        }
                    }
                }
            }
        }

        detailTransaksi?.let { tx ->
            val tglStr = TimeUtils.formatWibFull(tx.id, tx.waktu)
            val tvData = state.tvList.find { it.nama == tx.kota }
            AlertDialog(
                onDismissRequest = { detailTransaksi = null; cetakMsg = "" },
                containerColor = DarkSurface,
                titleContentColor = NeonCyan,
                textContentColor = TextPrimary,
                title = { Text("DETAIL TRANSAKSI") },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text("No    : ${tx.id.takeLast(12)}", color = TextPrimary, style = MaterialTheme.typography.bodySmall)
                        Text("Tgl   : $tglStr", color = TextPrimary, style = MaterialTheme.typography.bodySmall)
                        Text("Tgl Ops: ${tx.tanggalOperasional.ifBlank { "-" }}", color = TextPrimary, style = MaterialTheme.typography.bodySmall)
                        Text(
                            "Shift : ${tx.jenisShift.ifBlank { "-" }}",
                            color = when (tx.jenisShift) {
                                "Pagi" -> NeonYellow
                                "Malam" -> NeonCyan
                                else -> TextDim
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text("TV    : ${tx.kota}", color = TextPrimary, style = MaterialTheme.typography.bodySmall)
                        if (tvData != null) Text("PS    : ${tvData.jenisPs}", color = TextPrimary, style = MaterialTheme.typography.bodySmall)
                        Text("Kasir : ${tx.kasir}", color = TextPrimary, style = MaterialTheme.typography.bodySmall)
                        Text(
                            if (tx.sudahBayar) "Status : Lunas" else "Status : Utang",
                            color = if (tx.sudahBayar) NeonGreen else NeonRed,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(4.dp))
                        HorizontalDivider(color = DarkSurfaceV3)
                        Spacer(Modifier.height(4.dp))

                        if (tx.paketHarga > 0) {
                            Text(tx.paket, color = NeonYellow, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            Text("  Rp ${String.format("%,d", tx.paketHarga).replace(",", ".")}", color = NeonGreen, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(4.dp))
                        } else if (tx.paket.isNotBlank() && tx.paket != "Pesanan Tambahan") {
                            Text("  ${tx.paket}", color = NeonYellow, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(4.dp))
                        }

                        if (tx.pesanan.isNotEmpty()) {
                            HorizontalDivider(color = DarkSurfaceV3)
                            Spacer(Modifier.height(4.dp))
                            tx.pesanan.forEach { (name, qty) ->
                                val price = tx.pesananHarga[name] ?: 0
                                val line = "$qty $name"
                                val priceStr = if (price > 0) "Rp ${String.format("%,d", price).replace(",", ".")}" else ""
                                Row(Modifier.fillMaxWidth()) {
                                    Text(line, color = TextPrimary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                    if (priceStr.isNotEmpty()) Text(priceStr, color = NeonGreen, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = DarkSurfaceV3)
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.fillMaxWidth()) {
                            Text("TOTAL", color = TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text("Rp ${String.format("%,d", tx.total).replace(",", ".")}", color = NeonGreen, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }

                        if (cetakMsg.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(cetakMsg, color = NeonCyan, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (tvData != null) {
                                cetakMsg = "Mencetak..."
                                viewModel.printReceipt(tx, tvData)
                                cetakMsg = "Struk dicetak"
                            } else {
                                cetakMsg = "Data TV tidak tersedia untuk cetak"
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DarkBackground),
                    ) { Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Cetak") }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { detailTransaksi = null; cetakMsg = "" },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonRed),
                    ) { Text("Tutup") }
                },
            )
        }
    }
}

private fun formatWaktu(id: String, iso: String): String = TimeUtils.formatWib(id, iso)

private val chartColorsList = listOf(
    Color(0xFF39FF14), Color(0xFF00E5FF), Color(0xFFBB00FF),
    Color(0xFFFF6B35), Color(0xFFFFD700), Color(0xFFFF4081),
)


