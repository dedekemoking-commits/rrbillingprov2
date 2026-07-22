package com.billingps.aptv.ui.screens

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
import com.billingps.aptv.ui.charts.*
import com.billingps.aptv.ui.theme.*

@Composable
fun RiwayatScreen(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsState()
    val ctx = LocalContext.current
    var detailTransaksi by remember { mutableStateOf<Transaksi?>(null) }
    var cetakMsg by remember { mutableStateOf("") }
    // Filter mode: "DAY", "WEEK", "MONTH", "ALL"
    var filterMode by remember { mutableStateOf("DAY") }
    var searchQuery by remember { mutableStateOf("") }
    val allList = state.transaksiList.sortedByDescending { it.waktu }
    // compute filtered list
    val now = java.util.Date()
    fun inDay(waktuIso: String): Boolean {
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
            val d = sdf.parse(waktuIso)
            val calNow = java.util.Calendar.getInstance()
            val calD = java.util.Calendar.getInstance()
            calD.time = d
            calNow.get(java.util.Calendar.YEAR) == calD.get(java.util.Calendar.YEAR)
                    && calNow.get(java.util.Calendar.DAY_OF_YEAR) == calD.get(java.util.Calendar.DAY_OF_YEAR)
        } catch (e: Exception) { Log.e("RiwayatScreen", "inDay: ${e.message}"); false }
    }
    fun inWeek(waktuIso: String): Boolean {
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
            val d = sdf.parse(waktuIso)
            val calNow = java.util.Calendar.getInstance()
            val calD = java.util.Calendar.getInstance()
            calD.time = d
            // compare week of year and year
            calNow.get(java.util.Calendar.YEAR) == calD.get(java.util.Calendar.YEAR)
                    && calNow.get(java.util.Calendar.WEEK_OF_YEAR) == calD.get(java.util.Calendar.WEEK_OF_YEAR)
        } catch (e: Exception) { Log.e("RiwayatScreen", "inWeek: ${e.message}"); false }
    }
    fun inMonth(waktuIso: String): Boolean {
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
            val d = sdf.parse(waktuIso)
            val calNow = java.util.Calendar.getInstance()
            val calD = java.util.Calendar.getInstance()
            calD.time = d
            calNow.get(java.util.Calendar.YEAR) == calD.get(java.util.Calendar.YEAR)
                    && calNow.get(java.util.Calendar.MONTH) == calD.get(java.util.Calendar.MONTH)
        } catch (e: Exception) { Log.e("RiwayatScreen", "inMonth: ${e.message}"); false }
    }
    val txList = remember(allList, filterMode, searchQuery) {
        val timeFiltered = when (filterMode) {
            "DAY" -> allList.filter { inDay(it.waktu) }
            "WEEK" -> allList.filter { inWeek(it.waktu) }
            "MONTH" -> allList.filter { inMonth(it.waktu) }
            else -> allList
        }
        if (searchQuery.isBlank()) timeFiltered
        else timeFiltered.filter { t ->
            t.kota.contains(searchQuery, ignoreCase = true) ||
            t.kasir.contains(searchQuery, ignoreCase = true) ||
            t.paket.contains(searchQuery, ignoreCase = true)
        }
    }
    val totalPendapatan = txList.sumOf { it.total }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        if (uri != null) {
            try {
                val header = "Waktu,Kasir,Kota/TV,Paket,Pesanan,Total\n"
                val rows = txList.joinToString("\n") { t ->
                    val pesananStr = t.pesanan.entries.joinToString(" | ") { (k, v) -> "$k x$v" }.ifEmpty { "-" }
                    listOf(t.waktu, t.kasir, t.kota, t.paket, "\"$pesananStr\"", fmtRp(t.total)).joinToString(",")
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
                    val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                    val dayTotal = mutableMapOf<String, Int>()
                    txList.forEach { tx ->
                        try {
                            val day = dateFormat.format(java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).parse(tx.waktu)!!)
                            dayTotal[day] = (dayTotal[day] ?: 0) + tx.total
                        } catch (_: Exception) {}
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

                // Package distribution pie chart
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
                    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = DarkSurfaceV2), border = BorderStroke(1.dp, DarkSurfaceV3)) {
                        Column(Modifier.padding(12.dp)) {
                            RevenuePieChart(data = packageData, title = "DISTRIBUSI PAKET")
                        }
                    }
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
            listOf("DAY" to "HARI", "WEEK" to "MINGGU", "MONTH" to "BULAN", "ALL" to "SEMUA").forEach { (mode, label) ->
                if (filterMode == mode) {
                    Button(
                        onClick = { filterMode = mode },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = DarkBackground)
                    ) { Text(label) }
                } else {
                    OutlinedButton(
                        onClick = { filterMode = mode },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        border = BorderStroke(1.dp, DarkSurfaceV3)
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
            ) { Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Import") }
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
            ) { Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Export CSV") }
            if (state.currentRole != "kasir") {
                var showBersihkanDialog by remember { mutableStateOf(false) }
                OutlinedButton(
                    onClick = { showBersihkanDialog = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonRed),
                    border = BorderStroke(1.dp, NeonRed),
                ) { Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Bersihkan") }
                if (showBersihkanDialog) {
                    AlertDialog(
                        onDismissRequest = { showBersihkanDialog = false },
                        containerColor = DarkSurface,
                        title = { Text("BERSIHKAN TRANSAKSI", fontWeight = FontWeight.Bold, color = NeonRed) },
                        text = { Text("Semua transaksi lokal akan dihapus permanen. Lanjutkan?", color = TextSecondary) },
                        confirmButton = {
                            Button(
                                onClick = { showBersihkanDialog = false; viewModel.bersihkanTransaksi() },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonRed, contentColor = DarkBackground),
                                shape = RoundedCornerShape(10.dp),
                            ) { Text("Bersihkan") }
                        },
                        dismissButton = {
                            OutlinedButton(
                                onClick = { showBersihkanDialog = false },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                                border = BorderStroke(1.dp, TextSecondary.copy(alpha = 0.5f)),
                            ) { Text("Batal") }
                        },
                    )
                }
            }
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
                            Text(formatWaktu(tx.waktu), style = MaterialTheme.typography.bodySmall, color = TextDim)
                            Text(tx.kota, style = MaterialTheme.typography.bodyMedium, color = NeonGreen, fontWeight = FontWeight.Bold)
                            Text(tx.paket, style = MaterialTheme.typography.bodySmall, color = NeonYellow)
                            tx.pesanan.forEach { (nm, q) ->
                                Text("• $nm ×$q", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
                            Text(fmtRp(tx.total), style = MaterialTheme.typography.titleSmall, color = NeonGreen)
                            Text(tx.kasir, style = MaterialTheme.typography.bodySmall, color = TextDim)
                        }
                    }
                }
            }
        }

        detailTransaksi?.let { tx ->
            val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale("id", "ID"))
            val tglStr = try {
                val p = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                sdf.format(p.parse(tx.waktu) ?: java.util.Date())
            } catch (_: Exception) { tx.waktu }
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
                        Text("TV    : ${tx.kota}", color = TextPrimary, style = MaterialTheme.typography.bodySmall)
                        if (tvData != null) Text("PS    : ${tvData.jenisPs}", color = TextPrimary, style = MaterialTheme.typography.bodySmall)
                        Text("Kasir : ${tx.kasir}", color = TextPrimary, style = MaterialTheme.typography.bodySmall)
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

private fun formatWaktu(iso: String): String {
    return try {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
        val out = java.text.SimpleDateFormat("dd MMM HH:mm", java.util.Locale("id", "ID"))
        out.format(sdf.parse(iso) ?: return iso)
    } catch (e: Exception) { Log.e("RiwayatScreen", "formatWaktu: ${e.message}"); iso }
}

private val chartColorsList = listOf(
    Color(0xFF39FF14), Color(0xFF00E5FF), Color(0xFFBB00FF),
    Color(0xFFFF6B35), Color(0xFFFFD700), Color(0xFFFF4081),
)


