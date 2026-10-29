package com.billingps.aptv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.billingps.aptv.models.FnbSaleResult
import com.billingps.aptv.models.MainViewModel
import com.billingps.aptv.ui.theme.DarkBackground
import com.billingps.aptv.ui.theme.DarkSurface
import com.billingps.aptv.ui.theme.DarkSurfaceV2
import com.billingps.aptv.ui.theme.DarkSurfaceV3
import com.billingps.aptv.ui.theme.NeonCyan
import com.billingps.aptv.ui.theme.NeonGreen
import com.billingps.aptv.ui.theme.NeonRed
import com.billingps.aptv.ui.theme.NeonYellow
import com.billingps.aptv.ui.theme.TextDim
import com.billingps.aptv.ui.theme.TextPrimary
import com.billingps.aptv.ui.theme.TextSecondary

private data class PosMenuItem(
    val name: String,
    val price: Int,
    val isMakanan: Boolean,
    val stock: Int?,
)

@Composable
fun PosScreen(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsState()
    var cartMakanan by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var cartMinuman by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var cashText by remember { mutableStateOf("") }
    var metode by remember { mutableStateOf("Tunai") }
    var saleResult by remember { mutableStateOf<FnbSaleResult?>(null) }

    val menu = remember(state.menuMakanan, state.menuMinuman, state.stokMakanan, state.stokMinuman) {
        buildList {
            state.menuMakanan.forEach { (name, price) ->
                add(PosMenuItem(name, price, true, state.stokMakanan[name]))
            }
            state.menuMinuman.forEach { (name, price) ->
                add(PosMenuItem(name, price, false, state.stokMinuman[name]))
            }
        }
    }
    val makananItems = menu.filter { it.isMakanan }
    val minumanItems = menu.filterNot { it.isMakanan }
    val foodTotal = cartMakanan.entries.sumOf { (name, qty) -> (state.menuMakanan[name] ?: 0) * qty }
    val drinkTotal = cartMinuman.entries.sumOf { (name, qty) -> (state.menuMinuman[name] ?: 0) * qty }
    val total = foodTotal + drinkTotal
    val itemCount = cartMakanan.values.sum() + cartMinuman.values.sum()
    val cash = if (metode == "Tunai") (cashText.toIntOrNull() ?: 0) else total
    val change = (cash - total).coerceAtLeast(0)
    val canPay = total > 0 && (metode != "Tunai" || cash >= total)

    fun qtyOf(item: PosMenuItem): Int =
        if (item.isMakanan) cartMakanan[item.name] ?: 0 else cartMinuman[item.name] ?: 0

    fun changeQty(item: PosMenuItem, delta: Int) {
        val current = qtyOf(item)
        val next = (current + delta).coerceAtLeast(0)
        if (item.stock != null && next > item.stock) return
        val map = if (item.isMakanan) cartMakanan else cartMinuman
        val updated = if (next == 0) map - item.name else map + (item.name to next)
        if (item.isMakanan) cartMakanan = updated else cartMinuman = updated
    }

    Column(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.PointOfSale, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text("KASIR / POS", style = MaterialTheme.typography.titleMedium, color = NeonGreen, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(
                if (itemCount > 0) "$itemCount item" else "Keranjang kosong",
                style = MaterialTheme.typography.bodySmall,
                color = if (itemCount > 0) NeonCyan else TextDim,
            )
        }

        if (menu.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.PointOfSale, contentDescription = null, tint = TextDim, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Menu belum tersedia", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
                    Text("Tambahkan menu di tab Harga", style = MaterialTheme.typography.bodySmall, color = TextDim)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (makananItems.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        PosSectionTitle("MAKANAN")
                    }
                    items(makananItems, key = { "M-${it.name}" }) { item ->
                        PosMenuCard(item, qtyOf(item), onMinus = { changeQty(item, -1) }, onPlus = { changeQty(item, 1) })
                    }
                }
                if (minumanItems.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        PosSectionTitle("MINUMAN")
                    }
                    items(minumanItems, key = { "D-${it.name}" }) { item ->
                        PosMenuCard(item, qtyOf(item), onMinus = { changeQty(item, -1) }, onPlus = { changeQty(item, 1) })
                    }
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().background(DarkSurface).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("TOTAL", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
                Text(fmtRp(total), style = MaterialTheme.typography.titleLarge, color = NeonGreen, fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Tunai", "QRIS", "Transfer").forEach { m ->
                    FilterChip(
                        selected = metode == m,
                        onClick = { metode = m },
                        label = { Text(m, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonGreen.copy(alpha = 0.2f),
                            selectedLabelColor = NeonGreen,
                            labelColor = TextSecondary,
                        ),
                    )
                }
            }
            if (metode == "Tunai") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = cashText,
                        onValueChange = { value -> cashText = value.filter { it.isDigit() }.take(9) },
                        label = { Text("Uang diterima", style = MaterialTheme.typography.labelSmall) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonGreen,
                            unfocusedBorderColor = DarkSurfaceV3,
                            cursorColor = NeonGreen,
                            focusedLabelColor = NeonGreen,
                            unfocusedContainerColor = DarkSurfaceV2,
                            focusedContainerColor = DarkSurfaceV2,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                        ),
                    )
                    OutlinedButton(
                        onClick = { cashText = total.toString() },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                    ) { Text("Uang Pas", style = MaterialTheme.typography.labelSmall) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Kembalian", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    Text(
                        fmtRp(change),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (change > 0) NeonYellow else TextDim,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Button(
                onClick = {
                    val result = viewModel.jualFnb(
                        itemsMakanan = cartMakanan,
                        itemsMinuman = cartMinuman,
                        metode = metode,
                        cash = cash,
                        kembalian = change,
                    )
                    saleResult = result
                    if (result.success) {
                        cartMakanan = emptyMap()
                        cartMinuman = emptyMap()
                        cashText = ""
                    }
                },
                enabled = canPay,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonGreen,
                    contentColor = DarkBackground,
                    disabledContainerColor = DarkSurfaceV3,
                    disabledContentColor = TextDim,
                ),
            ) {
                Icon(Icons.Filled.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("BAYAR ${fmtRp(total)}", fontWeight = FontWeight.Bold)
            }
        }
    }

    saleResult?.let { result ->
        AlertDialog(
            onDismissRequest = { saleResult = null },
            containerColor = DarkSurface,
            title = {
                Text(
                    if (result.success) "PEMBAYARAN BERHASIL" else "PEMBAYARAN GAGAL",
                    color = if (result.success) NeonGreen else NeonRed,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (result.success) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total", color = TextSecondary)
                            Text(fmtRp(result.total), color = TextPrimary, fontWeight = FontWeight.Bold)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Metode", color = TextSecondary)
                            Text(result.metode, color = TextPrimary, fontWeight = FontWeight.Bold)
                        }
                        if (result.metode.equals("Tunai", ignoreCase = true)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Tunai", color = TextSecondary)
                                Text(fmtRp(result.cash), color = TextPrimary)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Kembalian", color = TextSecondary)
                                Text(fmtRp(result.kembalian), color = NeonYellow, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    } else {
                        Text(result.message, color = TextSecondary)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { saleResult = null }) {
                    Text("Tutup", color = NeonGreen)
                }
            },
        )
    }
}

@Composable
private fun PosSectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = NeonCyan,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun PosMenuCard(
    item: PosMenuItem,
    qty: Int,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    val outOfStock = item.stock != null && item.stock <= 0
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 104.dp)
            .clickable(enabled = !outOfStock) { onPlus() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (qty > 0) NeonGreen.copy(alpha = 0.12f) else DarkSurface,
            disabledContainerColor = DarkSurfaceV2,
        ),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(
                item.name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (outOfStock) TextDim else TextPrimary,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Text(fmtRp(item.price), style = MaterialTheme.typography.bodySmall, color = NeonYellow, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(
                if (item.stock == null) "Stok bebas" else "Stok ${item.stock}",
                style = MaterialTheme.typography.labelSmall,
                color = if (outOfStock) NeonRed else TextDim,
            )
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (qty > 0) {
                    IconButton(onClick = onMinus, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Filled.Remove, contentDescription = "Kurangi ${item.name}", tint = NeonRed, modifier = Modifier.size(16.dp))
                    }
                    Text("$qty", style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                } else {
                    Spacer(Modifier.width(30.dp))
                }
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = onPlus,
                    enabled = !outOfStock,
                    modifier = Modifier.size(30.dp),
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "Tambah ${item.name}",
                        tint = if (outOfStock) TextDim else NeonGreen,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
