package com.billingps.owner.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.billingps.owner.BuildConfig
import com.billingps.owner.util.OwnerTime

@Composable
fun SettingsScreen(
    state: OwnerUiState,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
) {
    var confirmLogout by remember { mutableStateOf(false) }
    val onlineCount = state.tvs.count { OwnerTime.isOnline(state.clientStatus[it.id]) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionHeader(
                title = "Rental",
                subtitle = "Akun owner yang sedang aktif",
            )
            Spacer(Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = OwnerPanel),
                border = BorderStroke(1.dp, OwnerBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Store, contentDescription = null, tint = OwnerGreen)
                        Spacer(Modifier.size(10.dp))
                        Column {
                            Text(
                                state.rental.namaRental.ifBlank { state.username },
                                style = MaterialTheme.typography.titleMedium,
                                color = OwnerText,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "@${state.username}",
                                style = MaterialTheme.typography.bodySmall,
                                color = OwnerMuted,
                            )
                        }
                    }
                    HorizontalDivider(color = OwnerBorder)
                    InfoLine("Nama Rental", state.rental.namaRental.ifBlank { "-" })
                    InfoLine("Username", state.username.ifBlank { "-" })
                    InfoLine("Email", state.rental.email.ifBlank { "-" })
                    InfoLine("WhatsApp", state.rental.whatsapp.ifBlank { "-" })
                    InfoLine("Alamat", state.rental.alamat.ifBlank { "-" })
                }
            }
        }

        item {
            SectionHeader(
                title = "Status Koneksi",
                subtitle = "Sumber data dan sinkronisasi",
            )
            Spacer(Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = OwnerPanel),
                border = BorderStroke(1.dp, OwnerBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    InfoLine("TV terdaftar", "${state.tvs.size} unit", OwnerCyan)
                    InfoLine("TV online", "$onlineCount unit", OwnerGreen)
                    InfoLine("Sumber laporan", state.dataSource.ifBlank { "-" })
                    InfoLine("Transaksi tersimpan", "${state.transactions.size} row")
                    InfoLine("Sinkron Firestore", OwnerTime.formatDateTime(state.lastSync))
                    InfoLine("Versi app kasir", state.appVersion.ifBlank { "-" })
                    InfoLine("Versi owner", "v${BuildConfig.VERSION_NAME}")
                }
            }
        }

        item {
            SectionHeader(
                title = "Aksi",
                subtitle = "Perbarui data atau keluar dari rental",
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onRefresh,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isRefreshing,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OwnerGreen,
                    contentColor = Color(0xFF06281D),
                ),
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Muat Ulang Laporan", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { confirmLogout = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, OwnerRed),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = OwnerRed),
            ) {
                Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Ganti Rental", fontWeight = FontWeight.Bold)
            }
        }

        item {
            SectionHeader(
                title = "Tentang",
                subtitle = "RR Billing Owner",
            )
            Spacer(Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = OwnerPanel),
                border = BorderStroke(1.dp, OwnerBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AboutRow(
                        Icons.Default.Tv,
                        "Status kartu TV",
                        "Membaca tvList dan heartbeat client dari Firestore.",
                    )
                    AboutRow(
                        Icons.Default.Cloud,
                        "Laporan lengkap",
                        "Transaksi dari Supabase, ditampilkan dalam WIB.",
                    )
                    AboutRow(
                        Icons.Default.Schedule,
                        "Waktu",
                        "Data disimpan UTC dan ditampilkan Asia/Jakarta.",
                    )
                    AboutRow(
                        Icons.Default.Info,
                        "Catatan",
                        "PIN owner bersifat lokal. Untuk keamanan penuh, atur Firestore Rules.",
                    )
                }
            }
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            containerColor = OwnerPanel,
            title = { Text("Ganti Rental?", color = OwnerText, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Username dan PIN owner akan dihapus dari perangkat ini. Data rental di server tidak berubah.",
                    color = OwnerMuted,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLogout = false
                        onLogout()
                    },
                ) {
                    Text("Ya, Ganti", color = OwnerRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) {
                    Text("Batal", color = OwnerMuted)
                }
            },
        )
    }
}

@Composable
private fun AboutRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = OwnerPurpleLight, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(10.dp))
        Column {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = OwnerText,
                fontWeight = FontWeight.Bold,
            )
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = OwnerMuted,
            )
        }
    }
}
