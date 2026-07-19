package com.billingps.aptv.ui.screens

import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.billingps.aptv.models.Invoice
import com.billingps.aptv.models.MainViewModel
import com.billingps.aptv.ui.theme.*

@Composable
fun VerifikasiScreen(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsState()
    val ctx = LocalContext.current

    var invDialogPaket by remember { mutableStateOf("") }
    var invDialogHarga by remember { mutableStateOf("") }
    var showInvoiceDialog by remember { mutableStateOf(false) }
    var lastInvoice by remember { mutableStateOf<Invoice?>(null) }
    var showUploadDialog by remember { mutableStateOf(false) }
    var showInvoiceHistoryDialog by remember { mutableStateOf(false) }

    val myInvoices = remember(state.invoices) { viewModel.getMyInvoices() }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "BERLANGGANAN",
            style = MaterialTheme.typography.titleLarge,
            color = NeonCyan,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Pilih paket dan lakukan pembayaran",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )

        // Subscription Packages
        SubscriptionPackagesVerif { paket, harga ->
            invDialogPaket = paket
            invDialogHarga = harga
            lastInvoice = viewModel.buatInvoice(paket, harga.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0)
            showInvoiceDialog = true
        }

        // Invoice Dialog
        if (showInvoiceDialog && lastInvoice != null) {
            InvoiceDialogVerif(
                invoice = lastInvoice!!,
                onUpload = {
                    showInvoiceDialog = false
                    showUploadDialog = true
                },
                onNanti = { showInvoiceDialog = false },
                onDismiss = { showInvoiceDialog = false },
            )
        }

        // Upload Bukti Dialog
        if (showUploadDialog && lastInvoice != null) {
            UploadBuktiDialogVerif(
                invoice = lastInvoice!!,
                onConfirm = { base64 ->
                    viewModel.uploadBukti(lastInvoice!!.id, base64)
                    showUploadDialog = false
                    lastInvoice = null
                    Toast.makeText(ctx, "Bukti terkirim! Menunggu konfirmasi admin.", Toast.LENGTH_LONG).show()
                },
                onDismiss = { showUploadDialog = false },
            )
        }

        // Invoice History
        if (myInvoices.isNotEmpty()) {
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = DarkSurface), border = BorderStroke(1.dp, DarkSurfaceV3)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("INVOICE SAYA", style = MaterialTheme.typography.labelLarge, color = NeonCyan, modifier = Modifier.weight(1f))
                        Text("${myInvoices.size}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    Spacer(Modifier.height(8.dp))
                    myInvoices.take(5).forEach { inv ->
                        val sdf = java.text.SimpleDateFormat("dd/MM/yy HH:mm", java.util.Locale.getDefault())
                        val statusColor = when (inv.status) {
                            "CONFIRMED" -> NeonGreen
                            "WAITING_CONFIRMATION" -> NeonYellow
                            else -> TextDim
                        }
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(inv.id, style = MaterialTheme.typography.bodySmall, color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Text("${inv.paket} • ${sdf.format(java.util.Date(inv.dibuat))}", style = MaterialTheme.typography.bodySmall, color = TextDim, fontSize = 10.sp)
                            }
                            Text(inv.status.replace("_", " "), style = MaterialTheme.typography.labelSmall, color = statusColor)
                        }
                    }
                    if (myInvoices.size > 5) {
                        TextButton(onClick = { showInvoiceHistoryDialog = true }) { Text("Lihat semua (${myInvoices.size})", color = NeonCyan, fontSize = 12.sp) }
                    }
                }
            }
        }

        if (showInvoiceHistoryDialog) {
            AlertDialog(
                onDismissRequest = { showInvoiceHistoryDialog = false },
                containerColor = DarkSurface,
                title = { Text("Riwayat Invoice", color = NeonCyan, fontWeight = FontWeight.Bold) },
                text = {
                    LazyColumn {
                        items(myInvoices) { inv ->
                            val sdf = java.text.SimpleDateFormat("dd/MM/yy HH:mm", java.util.Locale.getDefault())
                            val statusColor = when (inv.status) {
                                "CONFIRMED" -> NeonGreen; "WAITING_CONFIRMATION" -> NeonYellow; else -> TextDim
                            }
                            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = DarkSurfaceV2), shape = RoundedCornerShape(8.dp)) {
                                Column(Modifier.padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(inv.id, style = MaterialTheme.typography.bodySmall, color = NeonCyan, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                        Text(inv.status.replace("_", " "), style = MaterialTheme.typography.labelSmall, color = statusColor)
                                    }
                                    Text("${inv.paket} • Rp${String.format("%,d", inv.harga)}", style = MaterialTheme.typography.bodySmall, color = TextPrimary, fontSize = 11.sp)
                                    Text(sdf.format(java.util.Date(inv.dibuat)), style = MaterialTheme.typography.bodySmall, color = TextDim, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showInvoiceHistoryDialog = false }) { Text("Tutup", color = NeonCyan) } },
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}

private val paketHargaMap = mapOf(
    "1 Bulan" to 99000,
    "3 Bulan" to 299000,
    "1 Tahun" to 999000,
    "LIFETIME" to 2000000,
)

@Composable
private fun SubscriptionPackagesVerif(onBeliInvoice: (String, String) -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkSurfaceV3),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("PAKET BERLANGGANAN", style = MaterialTheme.typography.labelLarge, color = NeonCyan)
            Spacer(Modifier.height(8.dp))
            listOf(
                SubData("1 Bulan", "Rp99.000", "ADD TV 5", NeonGreen),
                SubData("3 Bulan", "Rp299.000", "ADD TV 10", NeonCyan),
                SubData("1 Tahun", "Rp999.000", "ADD TV 15", NeonYellow),
                SubData("LIFETIME", "Rp2.000.000", "ADD TV unlimited", NeonOrange),
            ).forEach { p ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceV2),
                    border = BorderStroke(1.dp, p.color),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(p.nama, style = MaterialTheme.typography.bodyMedium, color = p.color, fontWeight = FontWeight.Bold)
                            Text(p.harga, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                            Text(p.desc, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        Button(
                            onClick = { onBeliInvoice(p.nama, p.harga) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = p.color, contentColor = DarkBackground),
                        ) { Text("Bayar") }
                    }
                }
            }
        }
    }
}

private data class SubData(val nama: String, val harga: String, val desc: String, val color: Color)

@Composable
private fun InvoiceDialogVerif(invoice: Invoice, onUpload: () -> Unit, onNanti: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = { Text("INVOICE PEMBAYARAN", color = NeonCyan, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(invoice.id, style = MaterialTheme.typography.titleMedium, color = NeonGreen, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.height(8.dp))
                Text("User: ${invoice.username}", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                Text("Paket: ${invoice.paket}", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                Text("Total: Rp${String.format("%,d", invoice.harga)}", style = MaterialTheme.typography.bodyMedium, color = NeonYellow, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Card(
                    modifier = Modifier.size(200.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(2.dp, NeonCyan),
                ) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(com.billingps.aptv.R.drawable.qris_admin),
                        contentDescription = "QRIS Admin",
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Card(colors = CardDefaults.cardColors(containerColor = DarkSurfaceV2), shape = RoundedCornerShape(8.dp)) {
                    Column(Modifier.padding(10.dp)) {
                        Text("BCA: 1234567890 a.n. RR BILLING", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text("Berita: ${invoice.id}", style = MaterialTheme.typography.bodySmall, color = NeonCyan, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Transfer sesuai total & gunakan no invoice sebagai berita.", style = MaterialTheme.typography.bodySmall, color = TextDim, textAlign = TextAlign.Center)
            }
        },
        confirmButton = {
            Button(
                onClick = onUpload,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = DarkBackground),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            ) { Icon(Icons.Filled.Upload, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Upload Bukti Bayar") }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onNanti,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                border = BorderStroke(1.dp, TextDim),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextDim),
            ) { Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Bayar Nanti") }
        },
    )
}

@Composable
private fun UploadBuktiDialogVerif(invoice: Invoice, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var selectedBase64 by remember { mutableStateOf("") }
    var previewBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val ctx = androidx.compose.ui.platform.LocalContext.current

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val inputStream: java.io.InputStream = ctx.contentResolver.openInputStream(uri) ?: return@rememberLauncherForActivityResult
                val bytes = inputStream.readBytes()
                inputStream.close()
                selectedBase64 = Base64.encodeToString(bytes, Base64.DEFAULT)
                previewBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (_: Exception) { }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = { Text("UPLOAD BUKTI TRANSFER", color = NeonCyan, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(invoice.id, style = MaterialTheme.typography.bodySmall, color = NeonGreen)
                Spacer(Modifier.height(8.dp))
                if (previewBitmap != null) {
                    Image(
                        bitmap = previewBitmap!!.asImageBitmap(),
                        contentDescription = "Preview",
                        modifier = Modifier.fillMaxWidth().heightIn(max = 250.dp).clip(RoundedCornerShape(12.dp)),
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Button(
                    onClick = { launcher.launch("image/*") },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DarkBackground),
                ) {
                    Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (previewBitmap != null) "Ganti Gambar" else "Pilih Gambar")
                }
                if (selectedBase64.isNotEmpty() && previewBitmap != null) {
                    Spacer(Modifier.height(8.dp))
                    Text("Ukuran: ${selectedBase64.length / 1024} KB", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedBase64.isNotEmpty()) onConfirm(selectedBase64)
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = DarkBackground),
                enabled = selectedBase64.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            ) { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Kirim Bukti") }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonRed),
                border = BorderStroke(1.dp, NeonRed),
            ) { Text("Batal") }
        },
    )
}
