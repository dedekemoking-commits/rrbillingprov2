package com.billingps.owner.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.billingps.owner.data.ClientStatus
import com.billingps.owner.data.OwnerTv
import com.billingps.owner.data.ReportPeriod
import com.billingps.owner.data.filteredTransactions
import com.billingps.owner.data.summarize
import com.billingps.owner.util.OwnerTime

@Composable
fun TvStatusScreen(
    state: OwnerUiState,
    onRefresh: () -> Unit,
) {
    val today = filteredTransactions(state.transactions, ReportPeriod.TODAY, "")
    val todaySummary = summarize(today)
    val onlineCount = state.tvs.count { OwnerTime.isOnline(state.clientStatus[it.id]) }
    val activeCount = state.tvs.count { it.aktif }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard(
                    label = "Total TV",
                    value = state.tvs.size.toString(),
                    accent = OwnerCyan,
                    icon = Icons.Default.Tv,
                    sub = "unit terdaftar",
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    label = "Online",
                    value = "$onlineCount/${state.tvs.size}",
                    accent = OwnerGreen,
                    icon = Icons.Default.Wifi,
                    sub = "heartbeat client",
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard(
                    label = "Sesi Aktif",
                    value = activeCount.toString(),
                    accent = OwnerAmber,
                    icon = Icons.Default.AccessTime,
                    sub = "sedang berjalan",
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    label = "Omzet Hari Ini",
                    value = rupiah(todaySummary.omzet),
                    accent = OwnerPink,
                    icon = Icons.Default.Payments,
                    sub = "${todaySummary.jumlah} transaksi",
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            SectionHeader(
                title = "Kartu TV",
                subtitle = "Status kartu, sesi, dan client",
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (state.tvs.isEmpty()) {
            item {
                EmptyState("Belum ada kartu TV yang tersinkron.")
            }
        } else {
            items(state.tvs, key = { it.id.ifBlank { it.nama } }) { tv ->
                TvCard(
                    tv = tv,
                    client = state.clientStatus[tv.id],
                )
            }
        }
        item {
            Text(
                buildString {
                    append("Sinkron Firestore: ")
                    append(OwnerTime.formatDateTime(state.lastSync))
                    if (state.appVersion.isNotBlank()) {
                        append("  •  app kasir v${state.appVersion}")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = OwnerMuted,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun TvCard(tv: OwnerTv, client: ClientStatus?) {
    val online = OwnerTime.isOnline(client)
    var expanded by remember(tv.id) { mutableStateOf(false) }
    val accent = when {
        online && tv.aktif -> OwnerGreen
        online -> OwnerCyan
        tv.aktif -> OwnerAmber
        else -> OwnerBorder
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = OwnerPanel),
        border = BorderStroke(1.dp, accent.copy(alpha = if (accent == OwnerBorder) 1f else 0.45f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(accent.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Tv, contentDescription = null, tint = accent)
                }
                Spacer(Modifier.size(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        tv.nama.ifBlank { "TV ${tv.id.take(6)}" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = OwnerText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        buildString {
                            append(tv.jenisPs.ifBlank { "PS" })
                            if (tv.ip.isNotBlank()) append("  •  ${tv.ip}:${tv.port}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = OwnerMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                ClientBadge(online = online, client = client)
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SessionBadge(tv.statusSesi, accent)
                if (tv.sudahBayar) {
                    PaymentBadge(paid = true, metode = "Lunas")
                } else if (tv.aktif) {
                    PaymentBadge(paid = false, metode = "")
                }
            }
            Spacer(Modifier.height(10.dp))

            if (tv.aktif) {
                if (tv.timerDurasi > 0L) {
                    val progress = (tv.sisaDetik.toFloat() / tv.timerDurasi.toFloat())
                        .coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = accent,
                        trackColor = OwnerBorder,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniStat(
                        label = "Sisa",
                        value = OwnerTime.formatDuration(tv.sisaDetik),
                        color = accent,
                        modifier = Modifier.weight(1f),
                    )
                    MiniStat(
                        label = "Paket",
                        value = rupiah(tv.paketHarga),
                        color = OwnerPurpleLight,
                        modifier = Modifier.weight(1f),
                    )
                    MiniStat(
                        label = "Pesanan",
                        value = rupiah(tv.totalPesanan),
                        color = OwnerAmber,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (tv.bebas) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Bebas • ${rupiah(tv.bebasHargaPerJam)}/jam" +
                            if (tv.bebasMulai > 0L) " • mulai ${OwnerTime.formatDateTime(tv.bebasMulai)}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = OwnerMuted,
                    )
                }
                if (tv.pesanan.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = OwnerBorder)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Fastfood,
                            contentDescription = null,
                            tint = OwnerAmber,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            "Pesanan",
                            style = MaterialTheme.typography.labelMedium,
                            color = OwnerMuted,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    tv.pesanan.entries.take(if (expanded) Int.MAX_VALUE else 3).forEach { (name, qty) ->
                        OrderRow(
                            name = name,
                            qty = qty,
                            price = tv.pesananHarga[name] ?: 0,
                        )
                    }
                    if (tv.pesanan.size > 3) {
                        TextButton(
                            onClick = { expanded = !expanded },
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                        ) {
                            Icon(
                                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = OwnerCyan,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.size(4.dp))
                            Text(
                                if (expanded) "Sembunyikan" else "Lihat ${tv.pesanan.size - 3} lagi",
                                color = OwnerCyan,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
                if (expanded) {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = OwnerBorder)
                    Spacer(Modifier.height(8.dp))
                    InfoLine("ID Transaksi", tv.trxId.ifBlank { "-" })
                    InfoLine("Timer", if (tv.timerActive) "Aktif" else "Tidak aktif")
                    InfoLine("Total Pesanan", rupiah(tv.totalPesanan))
                    if (tv.hdmiPort > 0) InfoLine("HDMI", "Port ${tv.hdmiPort}")
                    if (tv.cancelBatas > 0L) {
                        InfoLine("Batas Cancel", OwnerTime.formatDateTime(tv.cancelBatas))
                    }
                }
            } else {
                Text(
                    "Tidak ada sesi aktif. TV siap dipakai.",
                    style = MaterialTheme.typography.bodySmall,
                    color = OwnerMuted,
                )
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = OwnerBorder)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (online) Icons.Default.Wifi else Icons.Default.WifiOff,
                    contentDescription = null,
                    tint = if (online) OwnerGreen else OwnerMuted,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    buildString {
                        append("Client ")
                        append(client?.version?.takeIf { it.isNotBlank() }?.let { "v$it" } ?: "belum terhubung")
                        append("  •  ")
                        append(OwnerTime.lastSeenText(client))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = OwnerMuted,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!expanded && tv.aktif) {
                    TextButton(
                        onClick = { expanded = true },
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                    ) {
                        Text("Detail", color = OwnerCyan, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun ClientBadge(online: Boolean, client: ClientStatus?) {
    val color = when {
        online -> OwnerGreen
        client == null -> OwnerMuted
        else -> OwnerRed
    }
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(online)
        Spacer(Modifier.size(5.dp))
        Text(
            when {
                online -> "ONLINE"
                client == null -> "MENUNGGU"
                else -> "OFFLINE"
            },
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SessionBadge(label: String, color: Color) {
    Text(
        label.uppercase(),
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun MiniStat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(OwnerPanel2, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = OwnerMuted)
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun OrderRow(name: String, qty: Int, price: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$name x$qty",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = OwnerText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            if (price > 0) rupiah(price * qty) else "-",
            style = MaterialTheme.typography.bodySmall,
            color = OwnerMuted,
        )
    }
}
