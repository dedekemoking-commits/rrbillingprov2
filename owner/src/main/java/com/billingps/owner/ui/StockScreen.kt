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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Inventory2
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
import com.billingps.owner.data.StockOpnameItem
import com.billingps.owner.util.OwnerTime

@Composable
fun StockScreen(
    state: OwnerUiState,
    onSubmit: (List<StockOpnameItem>, String) -> Unit,
    onClearNotice: () -> Unit,
) {
    var counts by remember(state.username) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var note by remember(state.username) { mutableStateOf("") }
    var showConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(state.opnameNotice) {
        if (state.opnameNotice != null) {
            counts = emptyMap()
            note = ""
        }
    }

    val foodItems = remember(state.menuMakanan, state.stokMakanan) {
        (state.menuMakanan.keys + state.stokMakanan.keys).sorted()
    }
    val drinkItems = remember(state.menuMinuman, state.stokMinuman) {
        (state.menuMinuman.keys + state.stokMinuman.keys).sorted()
    }

    fun key(category: String, name: String) = "$category::$name"

    fun buildItems(): List<StockOpnameItem> {
        val result = mutableListOf<StockOpnameItem>()
        foodItems.forEach { name ->
            val quantity = counts[key("makanan", name)]?.toIntOrNull() ?: return@forEach
            if (quantity <= 0) return@forEach
            result.add(
                StockOpnameItem(
                    name = name,
                    category = "makanan",
                    expected = state.stokMakanan[name],
                    quantity = quantity,
                ),
            )
        }
        drinkItems.forEach { name ->
            val quantity = counts[key("minuman", name)]?.toIntOrNull() ?: return@forEach
            if (quantity <= 0) return@forEach
            result.add(
                StockOpnameItem(
                    name = name,
                    category = "minuman",
                    expected = state.stokMinuman[name],
                    quantity = quantity,
                ),
            )
        }
        return result
    }

    val opnameItems = buildItems()

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
                            Icons.Default.Inventory2,
                            contentDescription = null,
                            tint = OwnerGreen,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Tambah Stok F&B",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = OwnerText,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Isi jumlah stok yang ditambahkan. Kosongkan item yang tidak ditambah. " +
                            "Stok sistem akan bertambah setelah aplikasi billing memproses.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OwnerMuted,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "${opnameItems.size} item diisi",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (opnameItems.isEmpty()) OwnerMuted else OwnerGreen,
                        )
                        Text(
                            "Sinkron: ${OwnerTime.formatDateTime(state.lastSync)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = OwnerMuted,
                        )
                    }
                }
            }
        }

        state.opnameNotice?.let { notice ->
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

        if (foodItems.isEmpty() && drinkItems.isEmpty()) {
            item {
                EmptyState("Belum ada menu F&B. Atur menu di aplikasi billing terlebih dahulu.")
            }
        }

        if (foodItems.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Makanan",
                    subtitle = "${foodItems.size} item",
                )
            }
            items(foodItems) { name ->
                StockRow(
                    name = name,
                    current = state.stokMakanan[name],
                    value = counts[key("makanan", name)].orEmpty(),
                    onValueChange = { value ->
                        counts = counts + (key("makanan", name) to value)
                    },
                )
            }
        }

        if (drinkItems.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Minuman",
                    subtitle = "${drinkItems.size} item",
                )
            }
            items(drinkItems) { name ->
                StockRow(
                    name = name,
                    current = state.stokMinuman[name],
                    value = counts[key("minuman", name)].orEmpty(),
                    onValueChange = { value ->
                        counts = counts + (key("minuman", name) to value)
                    },
                )
            }
        }

        if (foodItems.isNotEmpty() || drinkItems.isNotEmpty()) {
            item {
                Spacer(Modifier.height(2.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(200) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Catatan opname (opsional)") },
                    minLines = 2,
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { showConfirm = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    enabled = opnameItems.isNotEmpty() && !state.isSubmittingOpname,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OwnerGreen,
                        contentColor = Color(0xFF06281D),
                    ),
                ) {
                    if (state.isSubmittingOpname) {
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
                        Text(
                            "TAMBAH STOK (${opnameItems.size})",
                            fontWeight = FontWeight.Black,
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            containerColor = OwnerPanel,
            title = {
                Text(
                    "Tambah stok?",
                    color = OwnerText,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    "${opnameItems.size} item akan ditambahkan ke stok sistem setelah " +
                        "aplikasi billing memproses permintaan ini.",
                    color = OwnerMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirm = false
                        onSubmit(opnameItems, note)
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
private fun StockRow(
    name: String,
    current: Int?,
    value: String,
    onValueChange: (String) -> Unit,
) {
    val quantity = value.toIntOrNull()
    val endStock = quantity?.takeIf { it > 0 }?.let { (current ?: 0) + it }
    val endStockText = endStock?.let { "Stok akhir: $it" }

    Card(
        modifier = Modifier.fillMaxWidth(),
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
                Text(
                    name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = OwnerText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (current == null) "Stok sistem: —" else "Stok sistem: $current",
                    style = MaterialTheme.typography.bodySmall,
                    color = OwnerMuted,
                )
                if (endStockText != null) {
                    Text(
                        endStockText,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = OwnerGreen,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            OutlinedTextField(
                value = value,
                onValueChange = { raw ->
                    onValueChange(raw.filter { it.isDigit() }.take(6))
                },
                modifier = Modifier.width(86.dp),
                placeholder = { Text("—", color = OwnerMuted) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = OwnerText,
                    fontWeight = FontWeight.Bold,
                ),
                shape = RoundedCornerShape(10.dp),
            )
        }
    }
}
