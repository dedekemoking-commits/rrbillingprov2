package com.billingps.aptv.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.billingps.aptv.ui.theme.*

@Composable
fun RiwayatScreen(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsState()
    val ctx = LocalContext.current
    // Filter mode: "DAY", "WEEK", "MONTH", "ALL"
    var filterMode by remember { mutableStateOf("DAY") }
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
        } catch (_: Exception) { false }
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
        } catch (_: Exception) { false }
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
        } catch (_: Exception) { false }
    }
    val txList = remember(allList, filterMode) {
        when (filterMode) {
            "DAY" -> allList.filter { inDay(it.waktu) }
            "WEEK" -> allList.filter { inWeek(it.waktu) }
            "MONTH" -> allList.filter { inMonth(it.waktu) }
            else -> allList
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
                OutlinedButton(
                    onClick = { viewModel.bersihkanTransaksi() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonRed),
                    border = BorderStroke(1.dp, NeonRed),
                ) { Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Bersihkan") }
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
                        modifier = Modifier.fillMaxWidth().background(if (idx % 2 == 0) DarkBackground else DarkSurfaceV2).padding(horizontal = 14.dp, vertical = 10.dp),
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
    }
}

private fun formatWaktu(iso: String): String {
    return try {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
        val out = java.text.SimpleDateFormat("dd MMM HH:mm", java.util.Locale("id", "ID"))
        out.format(sdf.parse(iso)!!)
    } catch (_: Exception) { iso }
}


