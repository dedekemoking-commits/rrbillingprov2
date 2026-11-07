package com.billingps.owner.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.billingps.owner.data.CashDrawerRequest
import com.billingps.owner.data.OwnerCashDrawer
import com.billingps.owner.util.OwnerTime
import java.util.Locale

@Composable
fun CashScreen(
    state: OwnerUiState,
    onSubmit: (Int, String) -> Unit,
    onClearNotice: () -> Unit,
) {
    var nominalText by remember(state.username) { mutableStateOf("") }
    var note by remember(state.username) { mutableStateOf("") }
    var showConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(state.cashRequestNotice) {
        if (state.cashRequestNotice != null) {
            nominalText = ""
            note = ""
        }
    }

    val drawer = state.cashDrawer
    val nominal = nominalText.toIntOrNull() ?: 0
    val exceedBalance = nominal > 0 && drawer.aktif && nominal > drawer.cashExpected
    val canSubmit = drawer.aktif && nominal > 0 && !exceedBalance &&
        !state.isSubmittingCashRequest

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = OwnerPanel),
                border = BorderStroke(1.dp, OwnerBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = OwnerGreen,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Tarik Uang Laci",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = OwnerText,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Ajukan penarikan dari saldo laci shift aktif. " +
                            "Aplikasi billing akan memproses dan mencatat penarikan ini.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OwnerMuted,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Sinkron: ${OwnerTime.formatDateTime(state.lastSync)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = OwnerMuted,
                    )
                }
            }
        }

        state.cashRequestNotice?.let { notice ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF063B2B)),
                    border = BorderStroke(1.dp, OwnerGreen.copy(alpha = 0.45f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = OwnerGreen,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            notice,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = OwnerGreen,
                        )
                        TextButton(onClick = onClearNotice) {
                            Text("Tutup", color = OwnerMuted, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        item { CashDrawerCard(drawer) }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = OwnerPanel),
                border = BorderStroke(1.dp, OwnerBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "Nominal Penarikan",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = OwnerMuted,
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = nominalText,
                        onValueChange = { raw ->
                            nominalText = raw.filter { it.isDigit() }.take(9)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Jumlah (Rp)") },
                        placeholder = { Text("Contoh: 50000", color = OwnerMuted) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        isError = exceedBalance,
                        supportingText = if (exceedBalance || drawer.aktif) {
                            {
                                Text(
                                    if (exceedBalance) {
                                        "Melebihi saldo laci ${formatRupiah(drawer.cashExpected)}"
                                    } else {
                                        "Maksimal ${formatRupiah(drawer.cashExpected)}"
                                    },
                                )
                            }
                        } else {
                            null
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it.take(200) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Catatan (opsional)") },
                        minLines = 2,
                        maxLines = 3,
                        shape = RoundedCornerShape(12.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { showConfirm = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        enabled = canSubmit,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OwnerGreen,
                            contentColor = Color(0xFF06281D),
                        ),
                    ) {
                        if (state.isSubmittingCashRequest) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF06281D),
                            )
                        } else {
                            Icon(
                                Icons.Default.CloudUpload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("AJUKAN PENARIKAN", fontWeight = FontWeight.Black)
                        }
                    }
                    if (!drawer.aktif) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Belum ada shift kasir aktif. Buka shift di aplikasi billing terlebih dahulu.",
                            style = MaterialTheme.typography.bodySmall,
                            color = OwnerAmber,
                        )
                    }
                }
            }
        }

        item {
            SectionHeader(
                title = "Riwayat Permintaan",
                subtitle = "${state.cashDrawerRequests.size} permintaan terakhir",
            )
        }

        if (state.cashDrawerRequests.isEmpty()) {
            item {
                EmptyState("Belum ada permintaan penarikan uang laci.")
            }
        } else {
            items(state.cashDrawerRequests) { request ->
                CashRequestRow(request)
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            containerColor = OwnerPanel,
            title = {
                Text(
                    "Ajukan penarikan?",
                    color = OwnerText,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    "${formatRupiah(nominal)} akan ditarik dari uang laci. " +
                        "Aplikasi billing akan memproses permintaan ini.",
                    color = OwnerMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirm = false
                        onSubmit(nominal, note)
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OwnerGreen,
                        contentColor = Color(0xFF06281D),
                    ),
                ) {
                    Text("Kirim", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text("Batal", color = OwnerMuted)
                }
            },
        )
    }
}

@Composable
private fun CashDrawerCard(drawer: OwnerCashDrawer) {
    val accent = if (drawer.aktif) OwnerGreen else OwnerMuted
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = OwnerPanel),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Payments,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Uang Laci",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = OwnerText,
                )
                Text(
                    if (drawer.aktif) "SHIFT AKTIF" else "SHIFT TUTUP",
                    modifier = Modifier
                        .background(accent.copy(alpha = 0.14f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Saldo laci (seharusnya)",
                style = MaterialTheme.typography.bodySmall,
                color = OwnerMuted,
            )
            Text(
                formatRupiah(drawer.cashExpected),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = if (drawer.cashExpected < 0) OwnerRed else OwnerGreen,
            )
            Spacer(Modifier.height(10.dp))
            if (drawer.aktif) {
                InfoLine("Kasir", drawer.shiftUsername.ifBlank { "-" })
                InfoLine(
                    "Shift",
                    listOf(drawer.jenisShift, "mulai ${OwnerTime.formatDateTime(drawer.startedAt)}")
                        .filter { it.isNotBlank() }
                        .joinToString(" • ")
                        .ifBlank { "-" },
                )
            }
            InfoLine("Kas awal", formatRupiah(drawer.openingCash))
            InfoLine("Penjualan tunai", formatRupiah(drawer.cashSales))
            InfoLine("Kas masuk", formatRupiah(drawer.cashIn))
            InfoLine("Kas keluar", formatRupiah(drawer.cashOut))
        }
    }
}

@Composable
private fun CashRequestRow(request: CashDrawerRequest) {
    val accent = when {
        request.pending -> OwnerAmber
        request.applied -> OwnerGreen
        else -> OwnerRed
    }
    val statusText = when {
        request.pending -> "Menunggu"
        request.applied -> "Diterapkan"
        else -> "Ditolak"
    }
    val icon = when {
        request.pending -> Icons.Default.HourglassTop
        request.applied -> Icons.Default.CheckCircle
        else -> Icons.Default.Cancel
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = OwnerPanel),
        border = BorderStroke(1.dp, OwnerBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    formatRupiah(request.nominal),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Black,
                    color = OwnerText,
                )
                Text(
                    statusText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                OwnerTime.formatDateTime(request.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = OwnerMuted,
            )
            if (request.note.isNotBlank()) {
                Text(
                    request.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = OwnerMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!request.pending && request.reason.isNotBlank()) {
                Text(
                    request.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (request.applied) OwnerMuted else OwnerRed,
                )
            }
        }
    }
}

private fun formatRupiah(value: Int): String =
    "Rp " + String.format(Locale("id", "ID"), "%,d", value)
