package com.billingps.aptv.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.billingps.aptv.models.*
import com.billingps.aptv.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun KontrolHargaScreen(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var editPaket by remember { mutableStateOf<Map<String, Map<String, String>>>(emptyMap()) }
    var editDurasi by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var editMakanan by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var editMinuman by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    LaunchedEffect(state.paketMain, state.paketDurasi, state.menuMakanan, state.menuMinuman) {
        editPaket = state.paketMain.mapValues { (_, inner) -> inner.mapValues { it.value.toString() } }
        editDurasi = state.paketDurasi.mapValues { it.value.toString() }
        editMakanan = state.menuMakanan.mapValues { it.value.toString() }
        editMinuman = state.menuMinuman.mapValues { it.value.toString() }
    }

    fun toIntMap(map: Map<String, String>): Map<String, Int> =
        map.filterKeys { it.isNotBlank() }.mapValues { it.value.toIntOrNull() ?: 0 }

    Column(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(DarkSurface).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("HARGA", style = MaterialTheme.typography.titleLarge, color = NeonGreen)
            FilledTonalButton(
                onClick = {
                    val paketSaved = mutableMapOf<String, Map<String, Int>>()
                    JENIS_PS.forEach { t -> paketSaved[t] = toIntMap(editPaket[t] ?: emptyMap()) }
                    viewModel.saveHarga(paketSaved, toIntMap(editDurasi), toIntMap(editMakanan), toIntMap(editMinuman))
                    Toast.makeText(ctx, "Data tersimpan!", Toast.LENGTH_SHORT).show()
                },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = NeonGreen.copy(alpha = 0.2f), contentColor = NeonGreen),
            ) { Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Simpan") }
        }

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Paket Waktu Main
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, DarkSurfaceV3),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("PAKET WAKTU MAIN", style = MaterialTheme.typography.labelLarge, color = NeonCyan)
                        FilledTonalButton(onClick = {
                            val key = "Item Baru ${(editPaket["PS3"]?.size ?: 0) + 1}"
                            editPaket = editPaket.mapValues { (_, v) -> v + (key to "0") }
                            editDurasi = editDurasi + (key to "60")
                        }, shape = RoundedCornerShape(6.dp), colors = ButtonDefaults.filledTonalButtonColors(containerColor = NeonGreen.copy(0.2f), contentColor = NeonGreen)) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Tambah", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Paket", style = MaterialTheme.typography.labelSmall, color = TextPrimary, modifier = Modifier.weight(1.5f), fontWeight = FontWeight.Bold)
                        JENIS_PS.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = TextPrimary, modifier = Modifier.weight(1.5f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.Bold) }
                        Text("Mnt", style = MaterialTheme.typography.labelSmall, color = TextPrimary, modifier = Modifier.width(50.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(28.dp))
                    }

                    val unikNames = JENIS_PS.flatMap { t -> (editPaket[t]?.keys ?: emptySet()) }.distinct()
                    unikNames.forEach { nama ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                            OutlinedTextField(
                                value = nama, onValueChange = { baru ->
                                    val cp = editPaket.toMutableMap()
                                    JENIS_PS.forEach { t ->
                                        val inner = cp[t]?.toMutableMap() ?: return@forEach
                                        inner[baru] = inner.remove(nama) ?: "0"
                                        cp[t] = inner
                                    }
                                    editPaket = cp
                                    val cd = editDurasi.toMutableMap()
                                    cd[baru] = cd.remove(nama) ?: "60"
                                    editDurasi = cd
                                },
                                singleLine = true, modifier = Modifier.weight(1.5f), textStyle = MaterialTheme.typography.bodySmall,
                                shape = RoundedCornerShape(6.dp), colors = fieldColors(),
                            )
                            JENIS_PS.forEach { t ->
                                OutlinedTextField(
                                    value = editPaket[t]?.get(nama) ?: "0",
                                    onValueChange = { v -> editPaket = editPaket + (t to ((editPaket[t] ?: emptyMap()) + (nama to v))) },
                                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1.5f), textStyle = MaterialTheme.typography.bodySmall,
                                    shape = RoundedCornerShape(6.dp), colors = fieldColors(),
                                )
                            }
                            OutlinedTextField(
                                value = editDurasi[nama] ?: "60", onValueChange = { v -> editDurasi = editDurasi + (nama to v) },
                                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.width(50.dp), textStyle = MaterialTheme.typography.bodySmall,
                                shape = RoundedCornerShape(6.dp), colors = fieldColors(),
                            )
                            IconButton(onClick = {
                                val cp = editPaket.toMutableMap()
                                JENIS_PS.forEach { t -> cp[t] = cp[t]?.filterKeys { it != nama } ?: emptyMap() }
                                editPaket = cp
                                editDurasi = editDurasi.filterKeys { it != nama }
                            }, modifier = Modifier.size(28.dp)) { Icon(Icons.Filled.Delete, contentDescription = null, tint = NeonRed, modifier = Modifier.size(16.dp)) }
                        }
                    }
                }
            }

            // Menu Makanan
            EditFlatSection("MAKANAN", editMakanan, { editMakanan = it })
            // Menu Minuman
            EditFlatSection("MINUMAN", editMinuman, { editMinuman = it })

            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun EditFlatSection(title: String, editMap: Map<String, String>, onEditMap: (Map<String, String>) -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkSurfaceV3),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("MENU $title", style = MaterialTheme.typography.labelLarge, color = NeonCyan)
                FilledTonalButton(onClick = {
                    val key = "Item Baru ${editMap.size + 1}"
                    onEditMap(editMap + (key to "0"))
                }, shape = RoundedCornerShape(6.dp), colors = ButtonDefaults.filledTonalButtonColors(containerColor = NeonGreen.copy(0.2f), contentColor = NeonGreen)) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Tambah", style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(8.dp))

            editMap.entries.forEach { (nama, harga) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    OutlinedTextField(
                        value = nama, onValueChange = { v ->
                            val cp = editMap.toMutableMap()
                            cp[v] = cp.remove(nama) ?: "0"
                            onEditMap(cp)
                        },
                        singleLine = true, modifier = Modifier.weight(2f), textStyle = MaterialTheme.typography.bodySmall,
                        shape = RoundedCornerShape(6.dp), colors = fieldColors(),
                    )
                    OutlinedTextField(
                        value = harga, onValueChange = { v -> onEditMap(editMap + (nama to v)) },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f), textStyle = MaterialTheme.typography.bodySmall,
                        shape = RoundedCornerShape(6.dp), colors = fieldColors(),
                    )
                    Text("Rp", style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.padding(horizontal = 4.dp))
                    IconButton(onClick = { onEditMap(editMap.filterKeys { it != nama }) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Delete, contentDescription = null, tint = NeonRed, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

private fun <K, V> Map<K, V>.plus(pair: Pair<K, V>): Map<K, V> = this + pair

@Composable private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = NeonGreen, unfocusedBorderColor = DarkSurfaceV3,
    cursorColor = NeonGreen, focusedLabelColor = NeonGreen,
    unfocusedContainerColor = DarkSurfaceV2, focusedContainerColor = DarkSurfaceV2,
)
