@file:OptIn(ExperimentalMaterial3Api::class)
package com.billingps.aptv.ui.screens

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.animation.core.*
import com.billingps.aptv.models.*
import com.billingps.aptv.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.ui.platform.LocalContext
import java.text.NumberFormat
import java.util.Locale

fun fmtRp(amount: Int): String {
    val nf = NumberFormat.getNumberInstance(Locale("id", "ID"))
    return "Rp${nf.format(amount)}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    tvViewModel: com.billingps.aptv.TvViewModel,
) {
    val state by viewModel.state.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var showTambah by remember { mutableStateOf(false) }
    var showPaket by remember { mutableStateOf(false) }
    var selectedTV by remember { mutableStateOf<TvData?>(null) }
    var showPairingDialog by remember { mutableStateOf(false) }
    var pairingTvId by remember { mutableStateOf("") }
    var showSelesaiSummary by remember { mutableStateOf(false) }
    var selesaiSummaryTV by remember { mutableStateOf<TvData?>(null) }
    var showTambahWaktu by remember { mutableStateOf(false) }
    var tambahWaktuTV by remember { mutableStateOf<TvData?>(null) }
    var showBatalDialog by remember { mutableStateOf(false) }
    var batalTV by remember { mutableStateOf<TvData?>(null) }
    var showKonfirmasiBayar by remember { mutableStateOf(false) }
    var tvBayar by remember { mutableStateOf<TvData?>(null) }
    var bayarUpdates by remember { mutableStateOf<Map<String, Any>?>(null) }
    var showHabisDialog by remember { mutableStateOf(false) }
    var tvHabis by remember { mutableStateOf<TvData?>(null) }
    val habisDismissedIds = state.habisDismissedIds
    var showHapusPasswordDialog by remember { mutableStateOf(false) }
    var tvHapusTarget by remember { mutableStateOf<TvData?>(null) }
    var hapusPasswordInput by remember { mutableStateOf("") }
    var hapusPasswordError by remember { mutableStateOf("") }
    var showBuatPasswordDialog by remember { mutableStateOf(false) }
    var buatPasswordInput by remember { mutableStateOf("") }
    var buatPasswordConfirm by remember { mutableStateOf("") }
    var buatPasswordError by remember { mutableStateOf("") }
    val ctx = LocalContext.current

    // Check for update on first render
    LaunchedEffect(Unit) {
        viewModel.checkForUpdate()
    }

    val WhiteNeon = Color(0xFFF0FDF4)
    val GreenTosca = Color(0xFF00C9A7)
    val headerGradient = Brush.horizontalGradient(colors = listOf(WhiteNeon, GreenTosca))

    Column(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
        // Header
        Box(
            modifier = Modifier.fillMaxWidth().background(headerGradient).padding(horizontal = 12.dp, vertical = 2.dp),
        ) {
            Image(
                painter = androidx.compose.ui.res.painterResource(com.billingps.aptv.R.drawable.logo_transparant),
                contentDescription = "Logo",
                modifier = Modifier.align(Alignment.CenterStart).size(80.dp),
            )
            Text("TV: ${state.tvList.size}", style = MaterialTheme.typography.titleLarge, color = DarkBackground,
                modifier = Modifier.align(Alignment.Center))
            FilledTonalButton(
                onClick = { showTambah = true },
                modifier = Modifier.align(Alignment.CenterEnd),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = DarkBackground, contentColor = WhiteNeon),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Tambah TV", style = MaterialTheme.typography.labelSmall)
            }
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Cari TV...", color = TextDim) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Filled.Clear, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NeonGreen, unfocusedBorderColor = DarkSurfaceV3,
                cursorColor = NeonGreen, focusedLabelColor = NeonGreen,
                unfocusedContainerColor = DarkSurfaceV2, focusedContainerColor = DarkSurfaceV2,
            ),
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
        )

        val filteredList = if (searchQuery.isBlank()) state.tvList
            else state.tvList.filter { it.nama.contains(searchQuery, ignoreCase = true) || it.ip.contains(searchQuery, ignoreCase = true) || it.jenisPs.contains(searchQuery, ignoreCase = true) }

        if (state.tvList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Tv, contentDescription = null, modifier = Modifier.size(64.dp), tint = TextDim)
                    Spacer(Modifier.height(12.dp))
                    Text("Belum ada TV", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
                    Text("Tap \"Tambah TV\" untuk memulai", style = MaterialTheme.typography.bodySmall, color = TextDim)
                }
            }
        } else if (searchQuery.isNotBlank() && filteredList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.SearchOff, contentDescription = null, modifier = Modifier.size(64.dp), tint = TextDim)
                    Spacer(Modifier.height(12.dp))
                    Text("TV \"$searchQuery\" tidak ditemukan", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
                    Text("Coba kata kunci lain", style = MaterialTheme.typography.bodySmall, color = TextDim)
                }
            }
        } else {
            val listState = rememberLazyListState()
            val tvList = filteredList
            val scope = rememberCoroutineScope()
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                    state = listState,
                ) {
                    itemsIndexed(tvList, key = { _, tv -> tv.id }) { idx, tv ->
                    val forceDisabled = state.licenseStatus.status != "active" && idx >= 2
                    TVCard(
                        tv = tv,
                        showPindah = tvList.size > 1,
                        forceDisabled = forceDisabled,
                        onPilihPaket = { selectedTV = tv; showPaket = true },
                        onHapus = {
                            if (state.tvPasswordHash.isEmpty()) {
                                tvHapusTarget = tv
                                buatPasswordInput = ""
                                buatPasswordConfirm = ""
                                buatPasswordError = ""
                                showBuatPasswordDialog = true
                            } else {
                                tvHapusTarget = tv
                                hapusPasswordInput = ""
                                hapusPasswordError = ""
                                showHapusPasswordDialog = true
                            }
                        },
                        onPair = { pairingTvId = tv.id; showPairingDialog = true },
                        onUpdate = { updates -> viewModel.updateTV(tv.id, updates) },
                        onPower = {
                            if (tv.certPem.isNotEmpty() && tv.keyPem.isNotEmpty()) {
                                tvViewModel.sendPower(tv.ip, tv.certPem, tv.keyPem)
                            }
                        },
                        onVolUp = {
                            if (tv.certPem.isNotEmpty() && tv.keyPem.isNotEmpty()) {
                                tvViewModel.sendVolumeUp(tv.ip, tv.certPem, tv.keyPem)
                            }
                        },
                        onVolDown = {
                            if (tv.certPem.isNotEmpty() && tv.keyPem.isNotEmpty()) {
                                tvViewModel.sendVolumeDown(tv.ip, tv.certPem, tv.keyPem)
                            }
                        },
                        onSelesai = {
                            selesaiSummaryTV = tv
                            showSelesaiSummary = true
                        },
                        onHdmi = {
                            if (tv.certPem.isNotEmpty() && tv.keyPem.isNotEmpty()) {
                                tvViewModel.sendKeyCommand(tv.ip, tv.certPem, tv.keyPem, "TV_INPUT", "HDMI")
                            }
                        },
                        onOk = {
                            if (tv.certPem.isNotEmpty() && tv.keyPem.isNotEmpty()) {
                                tvViewModel.sendKeyCommand(tv.ip, tv.certPem, tv.keyPem, "DPAD_CENTER", "CENTER")
                            }
                        },
                        onTambahWaktu = {
                            tambahWaktuTV = tv
                            showTambahWaktu = true
                        },
                        onPindahTV = {
                            val next = (idx + 1) % tvList.size
                            scope.launch { listState.animateScrollToItem(next) }
                        },
                        onBatal = {
                            selectedTV = tv
                            showBatalDialog = true
                        },
                    )
                }
            }
        }

        // Start background timer service when any timer is active
        LaunchedEffect(state.tvList) {
            val hasActiveTimer = state.tvList.any { it.timerActive && !it.bebas && it.sisaDetik > 0 } || state.tvList.any { it.bebas }
            if (hasActiveTimer) {
                viewModel.startTimerService()
            }
        }

        // Monitor cancelBatas expiry (10-minute cancellation window)
        LaunchedEffect(Unit) {
            while (isActive) {
                kotlinx.coroutines.delay(1000)
                val now = System.currentTimeMillis()
                state.tvList.forEach { tv ->
                    if (tv.cancelBatas > 0 && now > tv.cancelBatas) {
                        viewModel.updateTV(tv.id, mapOf("cancelBatas" to 0L))
                    }
                }
            }
        }

        // Monitor expired timers — show dialog once per session, never again after dismissed
        LaunchedEffect(state.tvList) {
            val expiredTv = state.tvList.firstOrNull {
                it.paketAktif == "WAKTU HABIS" && it.sisaDetik == 0L && it.id !in habisDismissedIds
            }
            if (expiredTv != null && tvHabis == null) {
                tvHabis = expiredTv
                showHabisDialog = true
                if (expiredTv.certPem.isNotEmpty() && expiredTv.keyPem.isNotEmpty()) {
                    tvViewModel.sendPower(expiredTv.ip, expiredTv.certPem, expiredTv.keyPem)
                }
            }
        }
    }

    if (showTambah) {
        ModalTambahTV(
            jenisPsList = state.jenisPsList,
            onDismiss = { showTambah = false },
            onConfirm = { tvData ->
                val ok = viewModel.tambahTV(tvData)
                if (ok) {
                    showTambah = false
                    pairingTvId = tvData.id
                    showPairingDialog = true
                }
            },
            nomorTV = state.tvList.size + 1,
            maxTv = state.maxTv,
            currentTvCount = state.tvList.size,
            hasAccess = state.licenseStatus.status == "active" || state.trialBatas > System.currentTimeMillis(),
        )
    }

    if (showPairingDialog && pairingTvId.isNotEmpty()) {
        PairingDialog(
            tvId = pairingTvId,
            tvViewModel = tvViewModel,
            mainViewModel = viewModel,
            onDismiss = { showPairingDialog = false; pairingTvId = "" },
        )
    }

    val selesaiSummaryTv = selesaiSummaryTV
    if (showSelesaiSummary && selesaiSummaryTv != null) {
        SelesaiSummaryDialog(
            tv = selesaiSummaryTv,
            onBatal = { showSelesaiSummary = false; selesaiSummaryTV = null },
            onSelesai = {
                viewModel.updateTV(selesaiSummaryTv.id, mapOf(
                    "timerActive" to false, "sisaDetik" to 0L,
                    "bebas" to false, "paketAktif" to "SELESAI",
                ))
                showSelesaiSummary = false
                selesaiSummaryTV = null
                if (selesaiSummaryTv.certPem.isNotEmpty() && selesaiSummaryTv.keyPem.isNotEmpty()) {
                    tvViewModel.sendPower(selesaiSummaryTv.ip, selesaiSummaryTv.certPem, selesaiSummaryTv.keyPem)
                }
            },
            onPrint = {
                val lastTx = state.transaksiList.filter { it.kota == selesaiSummaryTv.nama }.maxByOrNull { it.waktu }
                if (lastTx != null) {
                    viewModel.printReceipt(lastTx, selesaiSummaryTv)
                }
            },
        )
    }

    val tvBayarValue = tvBayar
    if (showKonfirmasiBayar && tvBayarValue != null) {
        KonfirmasiBayarDialog(
            tvNama = tvBayarValue.nama,
            onSudahBayar = {
                viewModel.updateTV(tvBayarValue.id, mapOf("sudahBayar" to true))
                showKonfirmasiBayar = false; tvBayar = null
            },
            onBelumBayar = {
                viewModel.updateTV(tvBayarValue.id, mapOf("sudahBayar" to false))
                showKonfirmasiBayar = false; tvBayar = null
            },
        )
    }

    val tvHabisValue = tvHabis
    if (showHabisDialog && tvHabisValue != null) {
        HabisDialog(
            tv = tvHabisValue,
            onDismiss = {
                viewModel.dismissWaktuHabis(tvHabisValue.id)
                showHabisDialog = false; tvHabis = null
            },
            onSudahBayar = { viewModel.updateTV(tvHabisValue.id, mapOf("sudahBayar" to true)) },
        )
    }

    val tambahWaktuTvValue = tambahWaktuTV
    if (showTambahWaktu && tambahWaktuTvValue != null) {
        val tvData = tambahWaktuTvValue
        val hargaPaketList = state.paketMain[tvData.jenisPs] ?: state.paketMain["PS3"] ?: emptyMap()
        val durasiMap = state.paketDurasi
        var selectedPaket by remember { mutableStateOf(hargaPaketList.keys.firstOrNull() ?: "30 Menit") }
        val tambahDetik = (durasiMap[selectedPaket] ?: 30) * 60
        val tambahHarga = hargaPaketList[selectedPaket] ?: 0

        AlertDialog(
            onDismissRequest = { showTambahWaktu = false; tambahWaktuTV = null },
            containerColor = DarkSurface,
            title = { Text("Tambah Waktu", color = NeonGreen) },
            text = {
                Column {
                    Text("TV: ${tvData.nama} (${tvData.jenisPs})", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                    Spacer(Modifier.height(12.dp))
                    Text("Pilih paket tambahan:", style = MaterialTheme.typography.labelLarge, color = NeonCyan)
                    Spacer(Modifier.height(6.dp))
                    hargaPaketList.entries.forEach { (nama, harga) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selectedPaket == nama,
                                onClick = { selectedPaket = nama },
                                colors = RadioButtonDefaults.colors(selectedColor = NeonGreen),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(nama, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                                Text(fmtRp(harga), style = MaterialTheme.typography.bodySmall, color = NeonGreen)
                            }
                            val m = durasiMap[nama] ?: 0
                            if (nama != "Main Bebas") {
                                Text("${m / 60}j ${m % 60}m", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text("Tambahan:", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text(fmtRp(tambahHarga), style = MaterialTheme.typography.bodyMedium, color = NeonGreen)
                    }
                    if (selectedPaket == "Main Bebas") {
                        Spacer(Modifier.height(4.dp))
                        Text("Main Bebas akan ditambahkan ke total belanja", style = MaterialTheme.typography.bodySmall, color = NeonYellow)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val newSisa = if (selectedPaket == "Main Bebas") 0L else tvData.sisaDetik + tambahDetik
                    val updates = mutableMapOf<String, Any>(
                        "paketAktif" to "${tvData.paketAktif}+$selectedPaket",
                        "totalPesanan" to (tvData.totalPesanan + tambahHarga),
                        "sisaDetik" to newSisa,
                        "timerActive" to (newSisa > 0),
                        "timerStart" to System.currentTimeMillis(),
                        "timerDurasi" to newSisa,
                    )
                    viewModel.updateTV(tvData.id, updates)
                    viewModel.tambahTransaksi(Transaksi(
                        waktu = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(java.util.Date()),
                        kasir = state.currentUser,
                        kota = tvData.nama,
                        paket = "Tambah Waktu: $selectedPaket",
                        pesanan = emptyMap(),
                        total = tambahHarga,
                        paketHarga = tambahHarga,
                        tvJenisPs = tvData.jenisPs,
                    ))
                    showTambahWaktu = false; tambahWaktuTV = null
                }, colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = DarkBackground)) { Text("Tambah Rp$tambahHarga") }
            },
            dismissButton = { TextButton(onClick = { showTambahWaktu = false; tambahWaktuTV = null }) { Text("Batal", color = TextSecondary) } },
        )
    }

    val selectedTvBatal = selectedTV
    if (showBatalDialog && selectedTvBatal != null) {
        val tvData = selectedTvBatal
        AlertDialog(
            onDismissRequest = { showBatalDialog = false; selectedTV = null },
            containerColor = DarkSurface,
            title = { Text("BATALKAN", color = NeonOrange) },
            text = {
                Column {
                    Text("TV: ${tvData.nama}", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    Text("Batalkan transaksi terakhir?", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Spacer(Modifier.height(4.dp))
                    val lastTx = state.transaksiList.filter { it.kota == tvData.nama }.maxByOrNull { it.waktu }
                    if (lastTx != null) {
                        Text("• ${lastTx.paket}", style = MaterialTheme.typography.bodySmall, color = NeonYellow)
                        Text("• ${fmtRp(lastTx.total)}", style = MaterialTheme.typography.bodyMedium, color = NeonGreen)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Hanya tersedia 10 menit setelah transaksi", style = MaterialTheme.typography.bodySmall, color = TextDim)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val lastTx = state.transaksiList.filter { it.kota == tvData.nama }.maxByOrNull { it.waktu }
                    if (lastTx != null) {
                        viewModel.tambahTransaksi(Transaksi(
                            waktu = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(java.util.Date()),
                            kasir = state.currentUser,
                            kota = tvData.nama,
                            paket = "BATAL: ${lastTx.paket}",
                            pesanan = emptyMap(),
                            total = -lastTx.total,
                            paketHarga = -lastTx.paketHarga,
                            tvJenisPs = tvData.jenisPs,
                        ))
                        val isPaket = lastTx.paket != "Pesanan Tambahan"
                        if (isPaket) {
                            viewModel.updateTV(tvData.id, mapOf(
                                "timerActive" to false, "sisaDetik" to 0L, "paketAktif" to "",
                                "bebas" to false, "paketHarga" to 0, "totalPesanan" to 0,
                                "bebasMulai" to 0L, "bebasHargaPerJam" to 0, "bebasPesananTotal" to 0,
                                "cancelBatas" to 0L,
                            ))
                        } else {
                            viewModel.updateTV(tvData.id, mapOf(
                                "totalPesanan" to if (tvData.bebas) tvData.bebasPesananTotal - lastTx.total else tvData.totalPesanan - lastTx.total,
                                "bebasPesananTotal" to (tvData.bebasPesananTotal - lastTx.total),
                                "cancelBatas" to 0L,
                            ))
                        }
                    }
                    showBatalDialog = false; selectedTV = null
                }, colors = ButtonDefaults.buttonColors(containerColor = NeonOrange, contentColor = DarkBackground)) { Text("Ya, Batalkan") }
            },
            dismissButton = { TextButton(onClick = { showBatalDialog = false; selectedTV = null }) { Text("Tidak", color = TextSecondary) } },
        )
    }

    val selectedTvPaket = selectedTV
    if (showPaket && selectedTvPaket != null) {
        ModalPaket(
            tv = selectedTvPaket,
            paketData = state.paketMain[selectedTvPaket.jenisPs] ?: state.paketMain["PS3"] ?: emptyMap(),
            paketDurasi = state.paketDurasi,
            makananData = state.menuMakanan,
            minumanData = state.menuMinuman,
            currentUser = state.currentUser,
            onDismiss = { showPaket = false; selectedTV = null },
            onConfirm = { paketNm, paketHarga, pesanan, total, durasi, updates ->
                if (paketNm == null && updates != null) {
                    viewModel.updateTV(selectedTvPaket.id, updates)
                } else if (paketNm != null) {
                    viewModel.updateTV(selectedTvPaket.id, updates ?: emptyMap())
                }
                val pesananHarga = pesanan.entries.associate { (name, qty) ->
                    val unitPrice = state.menuMakanan[name] ?: state.menuMinuman[name] ?: 0
                    name to (unitPrice * qty)
                }
                viewModel.tambahTransaksi(Transaksi(
                    waktu = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(java.util.Date()),
                    kasir = state.currentUser,
                    kota = selectedTvPaket.nama,
                    paket = paketNm ?: "Pesanan Tambahan",
                    pesanan = pesanan,
                    total = total,
                    paketHarga = if (paketNm != null) paketHarga else 0,
                    pesananHarga = pesananHarga,
                    tvJenisPs = selectedTvPaket.jenisPs,
                ))
                if (paketNm != null) {
                    tvBayar = selectedTV
                    bayarUpdates = updates
                    showKonfirmasiBayar = true
                }
                showPaket = false; selectedTV = null
            },
        )
    }

    // ── Update Dialog ──────────────────────────────────────
    val updateInfo = state.updateInfo
    val dlProgress = state.downloadProgress
    if (updateInfo != null) {
        AlertDialog(
            onDismissRequest = { },
            containerColor = DarkSurface,
            title = { Text("UPDATE TERSEDIA", color = NeonGreen) },
            text = {
                Column {
                    Text("Versi ${updateInfo.versionName} tersedia!", style = MaterialTheme.typography.titleLarge, color = NeonGreen)
                    Spacer(Modifier.height(8.dp))
                    if (updateInfo.changelog.isNotBlank()) {
                        Text(updateInfo.changelog, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Ukuran: ${updateInfo.apkUrl.length} (link)", style = MaterialTheme.typography.bodySmall, color = TextDim)
                    if (dlProgress >= 0) {
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { dlProgress / 100f },
                            modifier = Modifier.fillMaxWidth().height(12.dp),
                            color = NeonGreen,
                            trackColor = DarkSurfaceV3,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("Download: $dlProgress%", style = MaterialTheme.typography.bodySmall, color = NeonGreen)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.downloadAndInstall(ctx) },
                    enabled = dlProgress < 0,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = DarkBackground),
                ) { Text(if (dlProgress >= 0) "Mengunduh..." else "Update Now") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissUpdate() }) { Text("Update Nanti", color = TextSecondary) }
            },
        )
    }

    // ── Buat Password TV Dialog ──────────────────────────
    val tvHapusTargetBuat = tvHapusTarget
    if (showBuatPasswordDialog && tvHapusTargetBuat != null) {
        AlertDialog(
            onDismissRequest = { showBuatPasswordDialog = false; tvHapusTarget = null },
            containerColor = DarkSurface,
            title = { Text("BUAT PASSWORD TV", color = NeonCyan, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Buat password untuk melindungi penghapusan TV.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = buatPasswordInput,
                        onValueChange = { buatPasswordInput = it; buatPasswordError = "" },
                        label = { Text("Password TV") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = fieldColors(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = buatPasswordConfirm,
                        onValueChange = { buatPasswordConfirm = it; buatPasswordError = "" },
                        label = { Text("Konfirmasi Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = fieldColors(),
                    )
                    if (buatPasswordError.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(buatPasswordError, style = MaterialTheme.typography.bodySmall, color = NeonRed)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (buatPasswordInput.length < 4) { buatPasswordError = "Minimal 4 karakter"; return@Button }
                        if (buatPasswordInput != buatPasswordConfirm) { buatPasswordError = "Konfirmasi tidak cocok"; return@Button }
                        viewModel.setTvPassword(buatPasswordInput)
                        viewModel.hapusTV(tvHapusTargetBuat.id)
                        showBuatPasswordDialog = false
                        tvHapusTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonRed, contentColor = DarkBackground),
                    shape = RoundedCornerShape(8.dp),
                ) { Text("Buat & Hapus TV", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showBuatPasswordDialog = false; tvHapusTarget = null }) { Text("Batal", color = TextSecondary) }
            },
        )
    }

    // ── Masukkan Password TV Dialog ──────────────────────────
    val tvHapusTargetHapus = tvHapusTarget
    if (showHapusPasswordDialog && tvHapusTargetHapus != null) {
        AlertDialog(
            onDismissRequest = { showHapusPasswordDialog = false; tvHapusTarget = null },
            containerColor = DarkSurface,
            title = { Text("HAPUS TV", color = NeonRed, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Masukkan password TV untuk menghapus ${tvHapusTargetHapus.nama}.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = hapusPasswordInput,
                        onValueChange = { hapusPasswordInput = it; hapusPasswordError = "" },
                        label = { Text("Password TV") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = fieldColors(),
                    )
                    if (hapusPasswordError.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(hapusPasswordError, style = MaterialTheme.typography.bodySmall, color = NeonRed)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (!viewModel.verifyTvPassword(hapusPasswordInput)) {
                            hapusPasswordError = "Password salah"
                            return@Button
                        }
                        viewModel.hapusTV(tvHapusTargetHapus.id)
                        showHapusPasswordDialog = false
                        tvHapusTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonRed, contentColor = DarkBackground),
                    shape = RoundedCornerShape(8.dp),
                ) { Text("Hapus TV", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showHapusPasswordDialog = false; tvHapusTarget = null }) { Text("Batal", color = TextSecondary) }
            },
        )
    }
}

@Composable
fun TVCard(
    tv: TvData,
    onPilihPaket: () -> Unit,
    onHapus: () -> Unit,
    onPair: () -> Unit,
    onUpdate: (Map<String, Any>) -> Unit,
    onPower: () -> Unit = {},
    onVolUp: () -> Unit = {},
    onVolDown: () -> Unit = {},
    onSelesai: () -> Unit = {},
    onHdmi: () -> Unit = {},
    onOk: () -> Unit = {},
    onTambahWaktu: () -> Unit = {},
    onPindahTV: () -> Unit = {},
    onBatal: () -> Unit = {},
    showPindah: Boolean = false,
    forceDisabled: Boolean = false,
) {
    val alpha = if (forceDisabled) 0.38f else 1f
    var bebasTick by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(tv.bebas, tv.id) {
        while (tv.bebas && isActive) {
            bebasTick = System.currentTimeMillis()
            delay(1000)
        }
    }

    var showEditHarga by remember { mutableStateOf(false) }
    var editHargaValue by remember { mutableStateOf("") }

    val sisaWaktu = if (tv.timerActive && !tv.bebas) {
        val m = tv.sisaDetik / 60
        val d = tv.sisaDetik % 60
        "${m}m ${d}d"
    } else if (tv.bebas) {
        val elapsedDetik = ((bebasTick - tv.bebasMulai) / 1000).toInt()
        val m = elapsedDetik / 60
        val d = elapsedDetik % 60
        "${m}m ${d}d"
    } else ""
    val isHabis = tv.paketAktif == "WAKTU HABIS" || tv.paketAktif == "SELESAI"
    val isAlmostHabis = tv.timerActive && !tv.bebas && tv.sisaDetik in 1..60

    val infiniteTransition = rememberInfiniteTransition(label = "blink")
    val blinkAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "blinkAlpha",
    )

    val cardBg = when {
        isAlmostHabis -> Color(0xFFD32F2F).copy(alpha = blinkAlpha)
        tv.timerActive || tv.bebas -> Color(0xFF1565C0)
        else -> DarkSurface
    }
    val gradientShape = RoundedCornerShape(20.dp)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha)
            .then(
                if (tv.timerActive || tv.bebas || isHabis)
                    Modifier.border(
                        BorderStroke(1.5.dp, Brush.linearGradient(
                            colors = listOf(Color(0xFF00F2FE), Color(0xFF0D1B2A)),
                            start = Offset(0f, 0f),
                            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                        )),
                        gradientShape,
                    )
                else Modifier.border(
                    BorderStroke(1.dp, Color(0xFF00F2FE).copy(alpha = 0.15f)),
                    gradientShape,
                )
            ),
        shape = gradientShape,
        colors = CardDefaults.cardColors(containerColor = cardBg),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val psLogoRes = when (tv.jenisPs) {
                    "PS4" -> com.billingps.aptv.R.drawable.ps4
                    "PS5" -> com.billingps.aptv.R.drawable.ps5
                    else -> com.billingps.aptv.R.drawable.ps3
                }
                Box(
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(psLogoRes),
                        contentDescription = tv.jenisPs,
                        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(6.dp)),
                    )
                }
            }

            if (tv.paketAktif.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                if (!isHabis && sisaWaktu.isNotEmpty()) {
                    Text(sisaWaktu, style = MaterialTheme.typography.titleLarge,
                        color = if (tv.paketAktif == "WAKTU HABIS") NeonRed else NeonYellow,
                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
                Text(tv.paketAktif, style = MaterialTheme.typography.bodySmall,
                    color = if (isHabis) NeonRed else NeonGreen, fontWeight = FontWeight.Bold)
                if (!isHabis && tv.bebas) {
                    val elapsedMs = bebasTick - tv.bebasMulai
                    val runningTotal = ((elapsedMs * tv.bebasHargaPerJam) / 3600000).toInt() + tv.bebasPesananTotal
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Biaya: ${fmtRp(runningTotal)} (${fmtRp(tv.bebasHargaPerJam)}/jam)",
                            style = MaterialTheme.typography.bodySmall, color = NeonGreen,
                            modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = { showEditHarga = true; editHargaValue = tv.bebasHargaPerJam.toString() },
                            modifier = Modifier.size(18.dp),
                        ) { Icon(Icons.Filled.Edit, contentDescription = "Ubah harga", tint = NeonYellow, modifier = Modifier.size(14.dp)) }
                    }
                }
                if (!isHabis && !tv.bebas) {
                    val totalBelanja = tv.paketHarga + tv.totalPesanan
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(fmtRp(totalBelanja), style = MaterialTheme.typography.bodyMedium, color = NeonGreen, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!tv.paired) {
                    OutlinedButton(
                        onClick = { if (!forceDisabled) onPair() },
                        enabled = !forceDisabled,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                        border = BorderStroke(1.dp, NeonCyan),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    ) { Icon(Icons.Filled.Cast, contentDescription = null, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(2.dp)); Text("Pair", style = MaterialTheme.typography.labelSmall) }
                }
                Button(
                    onClick = { if (!forceDisabled) onPilihPaket() },
                    enabled = !forceDisabled,
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = DarkBackground),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                ) { Text(if (tv.timerActive || tv.bebas) "Pesanan" else "Paket", style = MaterialTheme.typography.labelSmall) }
                if (tv.paired) {
                    OutlinedButton(
                        onClick = { if (!forceDisabled) onVolDown() },
                        enabled = !forceDisabled,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonGreen),
                        border = BorderStroke(1.dp, NeonGreen),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                    ) { Icon(Icons.Filled.VolumeDown, contentDescription = null, modifier = Modifier.size(12.dp)) }
                    OutlinedButton(
                        onClick = { if (!forceDisabled) onVolUp() },
                        enabled = !forceDisabled,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonGreen),
                        border = BorderStroke(1.dp, NeonGreen),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                    ) { Icon(Icons.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(12.dp)) }
                }
                if (tv.timerActive || tv.bebas) {
                    OutlinedButton(
                        onClick = { if (!forceDisabled) onTambahWaktu() },
                        enabled = !forceDisabled,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonYellow),
                        border = BorderStroke(1.dp, NeonYellow),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    ) { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(2.dp)); Text("Waktu", style = MaterialTheme.typography.labelSmall) }
                }
                if (tv.cancelBatas > 0 && System.currentTimeMillis() < tv.cancelBatas) {
                    OutlinedButton(
                        onClick = { if (!forceDisabled) onBatal() },
                        enabled = !forceDisabled,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonOrange),
                        border = BorderStroke(1.dp, NeonOrange),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                    ) { Icon(Icons.Filled.Cancel, contentDescription = null, modifier = Modifier.size(12.dp)) }
                }
                Spacer(Modifier.weight(1f))
                OutlinedButton(
                    onClick = { if (!forceDisabled) onHapus() },
                    enabled = !forceDisabled,
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonRed),
                    border = BorderStroke(1.dp, NeonRed),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                ) { Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(14.dp)) }
            }

            if (tv.paired) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { if (!forceDisabled) onPower() },
                        enabled = !forceDisabled,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonOrange),
                        border = BorderStroke(1.dp, NeonOrange),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    ) { Icon(Icons.Filled.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(2.dp)); Text("Power", style = MaterialTheme.typography.labelSmall) }
                    OutlinedButton(
                        onClick = { if (!forceDisabled) onHdmi() },
                        enabled = !forceDisabled,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonGreen),
                        border = BorderStroke(1.dp, NeonGreen),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    ) { Icon(Icons.Filled.Input, contentDescription = null, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(2.dp)); Text("HDMI", style = MaterialTheme.typography.labelSmall) }
                    OutlinedButton(
                        onClick = { if (!forceDisabled) onOk() },
                        enabled = !forceDisabled,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonGreen),
                        border = BorderStroke(1.dp, NeonGreen),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    ) { Text("OK", style = MaterialTheme.typography.labelSmall, color = NeonGreen) }
                    if (tv.timerActive || tv.bebas) {
                        OutlinedButton(
                            onClick = { if (!forceDisabled) onSelesai() },
                            enabled = !forceDisabled,
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonRed),
                            border = BorderStroke(1.dp, NeonRed),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        ) { Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(2.dp)); Text("Selesai", style = MaterialTheme.typography.labelSmall) }
                    }
                    if (showPindah) {
                        TextButton(onClick = { if (!forceDisabled) onPindahTV() }, enabled = !forceDisabled, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                            Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(12.dp), tint = if (forceDisabled) TextDim else NeonCyan)
                            Spacer(Modifier.width(2.dp))
                            Text("Pindah", style = MaterialTheme.typography.labelSmall, color = if (forceDisabled) TextDim else NeonCyan)
                        }
                    }
                }
            }
        }
    }

    if (showEditHarga) {
        AlertDialog(
            onDismissRequest = { showEditHarga = false },
            containerColor = DarkSurface,
            title = { Text("UBAH HARGA PER JAM", color = NeonYellow) },
            text = {
                OutlinedTextField(
                    value = editHargaValue,
                    onValueChange = { editHargaValue = it },
                    label = { Text("Harga per jam (Rp)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = fieldColors(),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newRate = editHargaValue.toIntOrNull()
                        if (newRate != null && newRate > 0) {
                            onUpdate(mapOf("bebasHargaPerJam" to newRate))
                        }
                        showEditHarga = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = DarkBackground),
                ) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { showEditHarga = false }) { Text("Batal", color = TextSecondary) } },
        )
    }
}

@Composable
fun ModalTambahTV(
    jenisPsList: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (TvData) -> Unit,
    nomorTV: Int,
    maxTv: Int = 0,
    currentTvCount: Int = 0,
    hasAccess: Boolean = true,
) {
    var nama by remember { mutableStateOf("TV $nomorTV") }
    var jenis by remember { mutableStateOf(jenisPsList.firstOrNull() ?: "PS3") }

    val terlampaui = maxTv > 0 && currentTvCount >= maxTv
    val blocked = !hasAccess

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = { Text("Tambah TV", color = NeonGreen) },
        text = {
            Column {
                if (blocked) {
                    Text("Lisensi tidak aktif. Aktifkan lisensi terlebih dahulu.", style = MaterialTheme.typography.bodySmall, color = NeonRed)
                    Spacer(Modifier.height(8.dp))
                } else if (terlampaui) {
                    Text("Batas maksimal TV ($maxTv) telah tercapai!", style = MaterialTheme.typography.bodySmall, color = NeonRed)
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(value = nama, onValueChange = { nama = it }, label = { Text("Nama") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = fieldColors())
                Spacer(Modifier.height(8.dp))
                Text("Jenis PS", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    jenisPsList.forEach { j ->
                        val logoRes = when (j) {
                            "PS4" -> com.billingps.aptv.R.drawable.ps4
                            "PS5" -> com.billingps.aptv.R.drawable.ps5
                            else -> com.billingps.aptv.R.drawable.ps3
                        }
                        FilterChip(
                            selected = jenis == j,
                            onClick = { jenis = j },
                            label = { Text(j) },
                            leadingIcon = {
                                Image(
                                    painter = painterResource(logoRes),
                                    contentDescription = j,
                                    modifier = Modifier.size(20.dp).clip(RoundedCornerShape(4.dp)),
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NeonGreen, selectedLabelColor = DarkBackground),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("TV akan dipairing via Android Remote TV", style = MaterialTheme.typography.bodySmall, color = TextDim)
            }
        },
        confirmButton = {
            Button(onClick = {
                val id = "tv_${System.currentTimeMillis()}"
                onConfirm(TvData(id = id, nama = nama, jenisPs = jenis))
            }, enabled = nama.isNotBlank() && !terlampaui && !blocked, colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = DarkBackground)) { Text("Tambah & Pairing") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal", color = TextSecondary) } },
    )
}

@Composable
fun ModalPaket(
    tv: TvData,
    paketData: Map<String, Int>,
    paketDurasi: Map<String, Int>,
    makananData: Map<String, Int>,
    minumanData: Map<String, Int>,
    currentUser: String,
    onDismiss: () -> Unit,
    onConfirm: (paketNm: String?, paketHarga: Int, pesanan: Map<String, Int>, total: Int, durasi: Pair<Int, Int>?, updates: Map<String, Any>?) -> Unit,
) {
    var selectedPaket by remember { mutableStateOf("") }
    var pesananBaru by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var total by remember { mutableIntStateOf(0) }

    val hidePaket = tv.timerActive || tv.bebas

    fun hitungTotal() {
        val hargaPaket = if (!hidePaket && selectedPaket.isNotEmpty()) (paketData[selectedPaket] ?: 0) else 0
        val makananTotal = pesananBaru.entries.filter { it.key in makananData }.sumOf { (k, q) -> (makananData[k] ?: 0) * q }
        val minumanTotal = pesananBaru.entries.filter { it.key in minumanData }.sumOf { (k, q) -> (minumanData[k] ?: 0) * q }
        total = hargaPaket + makananTotal + minumanTotal
    }

    LaunchedEffect(selectedPaket, pesananBaru) { hitungTotal() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = { Text(tv.nama, color = NeonGreen) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (!hidePaket) {
                    Text("PILIH PAKET", style = MaterialTheme.typography.labelLarge, color = NeonCyan)
                    Spacer(Modifier.height(8.dp))
                    paketData.entries.forEach { (nama, harga) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selectedPaket == nama,
                                onClick = { selectedPaket = nama },
                                colors = RadioButtonDefaults.colors(selectedColor = NeonGreen),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(nama, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                                Text(fmtRp(harga), style = MaterialTheme.typography.bodySmall, color = NeonGreen)
                            }
                        }
                    }
                    val hargaPerJam = paketData["1 Jam"] ?: 0
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selectedPaket == "Main Bebas",
                            onClick = { selectedPaket = "Main Bebas" },
                            colors = RadioButtonDefaults.colors(selectedColor = NeonGreen),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Main Bebas", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                            Text("${fmtRp(hargaPerJam)}/jam", style = MaterialTheme.typography.bodySmall, color = NeonGreen)
                        }
                    }
                }

                if (makananData.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("MAKANAN", style = MaterialTheme.typography.labelLarge, color = NeonCyan)
                    makananData.entries.forEach { (nama, harga) ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(nama, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                            Text(fmtRp(harga), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            Spacer(Modifier.width(8.dp))
                            IconButton(onClick = {
                                val q = (pesananBaru[nama] ?: 0) - 1
                                pesananBaru = if (q <= 0) pesananBaru - nama else pesananBaru + (nama to q)
                            }, modifier = Modifier.size(28.dp)) { Icon(Icons.Filled.Remove, contentDescription = null, tint = NeonRed, modifier = Modifier.size(16.dp)) }
                            Text("${pesananBaru[nama] ?: 0}", style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.width(20.dp), textAlign = TextAlign.Center)
                            IconButton(onClick = {
                                pesananBaru = pesananBaru + (nama to ((pesananBaru[nama] ?: 0) + 1))
                            }, modifier = Modifier.size(28.dp)) { Icon(Icons.Filled.Add, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(16.dp)) }
                        }
                    }
                }

                if (minumanData.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("MINUMAN", style = MaterialTheme.typography.labelLarge, color = NeonCyan)
                    minumanData.entries.forEach { (nama, harga) ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(nama, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                            Text(fmtRp(harga), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            Spacer(Modifier.width(8.dp))
                            IconButton(onClick = {
                                val q = (pesananBaru[nama] ?: 0) - 1
                                pesananBaru = if (q <= 0) pesananBaru - nama else pesananBaru + (nama to q)
                            }, modifier = Modifier.size(28.dp)) { Icon(Icons.Filled.Remove, contentDescription = null, tint = NeonRed, modifier = Modifier.size(16.dp)) }
                            Text("${pesananBaru[nama] ?: 0}", style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.width(20.dp), textAlign = TextAlign.Center)
                            IconButton(onClick = {
                                pesananBaru = pesananBaru + (nama to ((pesananBaru[nama] ?: 0) + 1))
                            }, modifier = Modifier.size(28.dp)) { Icon(Icons.Filled.Add, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(16.dp)) }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text("Total: ${fmtRp(total)}", style = MaterialTheme.typography.titleMedium, color = NeonGreen)

                if (!hidePaket && selectedPaket.isNotEmpty()) {
                    val durasi = paketDurasi[selectedPaket] ?: 60
                    val menit = if (selectedPaket == "Main Bebas") null else (durasi / 60) to (durasi % 60)
                    Spacer(Modifier.height(4.dp))
                    Text("Durasi: ${menit?.let { "${it.first}j ${it.second}m" } ?: "Bebas"}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val hargaPaket = if (!hidePaket && selectedPaket.isNotEmpty()) (paketData[selectedPaket] ?: 0) else 0
                    val durasiMnt = if (!hidePaket && selectedPaket.isNotEmpty()) paketDurasi[selectedPaket] ?: 60 else 0
                    val menitPair = if (durasiMnt > 0 && selectedPaket != "Main Bebas") (durasiMnt / 60) to (durasiMnt % 60) else null

                    if (hidePaket) {
                        // total hanya makanan+minuman (hargaPaket=0)
                        onConfirm(null, 0, pesananBaru, total, null,
                            mapOf(
                                "totalPesanan" to (tv.totalPesanan + total),
                                "bebasPesananTotal" to (tv.bebasPesananTotal + total),
                                "cancelBatas" to (System.currentTimeMillis() + 600_000),
                            ))
                    } else {
                        val pesananTotal = maxOf(0, total - hargaPaket)
                        val isBebas = selectedPaket == "Main Bebas"
                        val durasiDetik = durasiMnt * 60L
                        val updates = mutableMapOf<String, Any>(
                            "paketAktif" to if (isBebas) "Main Bebas" else "$selectedPaket (${durasiMnt}m)",
                            "sisaDetik" to (if (isBebas) 0 else durasiDetik),
                            "timerStart" to System.currentTimeMillis(),
                            "timerDurasi" to durasiDetik,
                            "timerActive" to (durasiMnt > 0 && !isBebas),
                            "bebas" to isBebas,
                            "paketHarga" to hargaPaket,
                            "totalPesanan" to (if (isBebas) 0 else pesananTotal),
                            "cancelBatas" to (System.currentTimeMillis() + 600_000),
                        )
                        if (isBebas) {
                            val hourlyRate = paketData["1 Jam"] ?: 0
                            updates["bebasMulai"] = System.currentTimeMillis()
                            updates["bebasHargaPerJam"] = hourlyRate
                            updates["bebasPesananTotal"] = pesananTotal
                        }
                        onConfirm(selectedPaket, hargaPaket, pesananBaru, total, menitPair, updates)
                    }
                },
                enabled = hidePaket || selectedPaket.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = DarkBackground),
            ) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal", color = TextSecondary) } },
    )
}

@Composable
fun PairingDialog(
    tvId: String,
    tvViewModel: com.billingps.aptv.TvViewModel,
    mainViewModel: MainViewModel,
    onDismiss: () -> Unit,
) {
    val tvState by tvViewModel.uiState.collectAsState()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var ip by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var step by remember { mutableIntStateOf(0) }

    LaunchedEffect(tvState.isPaired) {
        if (tvState.isPaired && step == 1) {
            val prefs = ctx.getSharedPreferences("tv_certs", Context.MODE_PRIVATE)
            val cert = prefs.getString("cert_pem", "") ?: ""
            val key = prefs.getString("key_pem", "") ?: ""
            mainViewModel.updateTV(tvId, mapOf("paired" to true, "ip" to ip.trim(), "port" to 6466, "certPem" to cert, "keyPem" to key))
            step = 2
        }
    }
    LaunchedEffect(tvState.status) {
        if (tvState.status == "PIN shown on TV. Enter the code below." && step == 0) {
            step = 1
        }
        if (tvState.status.startsWith("Error:") && step == 1) {
            step = 1
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = { Text("Pairing TV", color = NeonGreen) },
        text = {
            Column {
                when (step) {
                    0 -> {
                        Text("Masukkan IP TV", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = ip, onValueChange = { ip = it; tvViewModel.updateIp(it) }, label = { Text("IP Address") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = fieldColors())
                        if (tvState.status.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(tvState.status, style = MaterialTheme.typography.bodySmall, color = if (tvState.isPairing) NeonYellow else NeonRed)
                        }
                    }
                    1 -> {
                        Text("Masukkan PIN yang muncul di TV", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = pin, onValueChange = {
                            pin = it; tvViewModel.updatePin(it)
                        }, label = { Text("PIN") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = fieldColors())
                        if (tvState.status.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(tvState.status, style = MaterialTheme.typography.bodySmall, color = if (tvState.isPaired) NeonGreen else if (tvState.isBusy) NeonYellow else NeonRed)
                        }
                    }
                    2 -> {
                        Text("TV berhasil dipairing!", style = MaterialTheme.typography.bodyMedium, color = NeonGreen)
                        Text("Sertifikat tersimpan.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
            }
        },
        confirmButton = {
            when (step) {
                0 -> Button(
                    onClick = { tvViewModel.startPairing() },
                    enabled = ip.isNotBlank() && !tvState.isBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = DarkBackground),
                ) { if (tvState.isBusy) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = DarkBackground, strokeWidth = 2.dp) else Text("Mulai Pairing") }
                1 -> Button(
                    onClick = { tvViewModel.completePairing() },
                    enabled = pin.isNotBlank() && !tvState.isBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = DarkBackground),
                ) { if (tvState.isBusy) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = DarkBackground, strokeWidth = 2.dp) else Text("Selesai Pairing") }
                2 -> Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = DarkBackground),
                ) { Text("Selesai") }
            }
        },
        dismissButton = {
            if (step < 2) {
                TextButton(onClick = onDismiss) { Text("Batal", color = TextSecondary) }
            }
        },
    )
}

@Composable
fun SelesaiSummaryDialog(
    tv: TvData,
    onBatal: () -> Unit,
    onSelesai: () -> Unit,
    onPrint: (() -> Unit)? = null,
) {
    val totalBiaya = if (tv.bebas) {
        val elapsedMs = System.currentTimeMillis() - tv.bebasMulai
        ((elapsedMs * tv.bebasHargaPerJam) / 3600000).toInt() + tv.bebasPesananTotal
    } else {
        tv.paketHarga + tv.totalPesanan
    }
    val durasiText = if (tv.bebas) {
        val menit = ((System.currentTimeMillis() - tv.bebasMulai) / 60000).toInt()
        "${menit / 60}j ${menit % 60}m"
    } else {
        "${tv.sisaDetik / 60}m"
    }

    AlertDialog(
        onDismissRequest = onBatal,
        containerColor = DarkSurface,
        title = { Text("SELESAI", color = NeonRed) },
        text = {
            Column {
                Text(tv.nama, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("Paket", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Text(tv.paketAktif, style = MaterialTheme.typography.bodySmall, color = NeonGreen)
                }
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("Durasi", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Text(durasiText, style = MaterialTheme.typography.bodySmall, color = NeonYellow)
                }
                if (tv.totalPesanan > 0 || tv.bebasPesananTotal > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text("Pesanan:", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Text(fmtRp(tv.totalPesanan + tv.bebasPesananTotal), style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("TOTAL", style = MaterialTheme.typography.titleMedium, color = NeonGreen)
                    Text(fmtRp(totalBiaya), style = MaterialTheme.typography.titleMedium, color = NeonGreen)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (tv.sudahBayar) "✓ Sudah Bayar" else "✗ Belum Bayar",
                    color = if (tv.sudahBayar) NeonGreen else NeonRed,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onPrint != null) {
                    OutlinedButton(
                        onClick = onPrint,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                        border = BorderStroke(1.dp, NeonCyan),
                    ) { Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Cetak") }
                }
                Button(
                    onClick = onSelesai,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = DarkBackground),
                ) { Text("OK, Selesai") }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onBatal,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonRed),
                border = BorderStroke(1.dp, NeonRed),
            ) { Text("Batal") }
        },
    )
}

@Composable
fun KonfirmasiBayarDialog(
    tvNama: String,
    onSudahBayar: () -> Unit,
    onBelumBayar: () -> Unit,
) {
    Dialog(onDismissRequest = onBelumBayar) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
        ) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("KONFIRMASI PEMBAYARAN", style = MaterialTheme.typography.titleLarge, color = NeonCyan)
                Spacer(Modifier.height(16.dp))
                Text("Apakah $tvNama sudah bayar?", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onBelumBayar,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonRed),
                        shape = RoundedCornerShape(10.dp)) { Text("Belum Bayar", fontWeight = FontWeight.Bold) }
                    Button(onClick = onSudahBayar,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = DarkBackground),
                        shape = RoundedCornerShape(10.dp)) { Text("Sudah Bayar", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
fun HabisDialog(
    tv: TvData,
    onDismiss: () -> Unit,
    onSudahBayar: (() -> Unit)? = null,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
        ) {
            Column(Modifier.padding(24.dp)) {
                Text("WAKTU HABIS", style = MaterialTheme.typography.titleLarge, color = NeonRed)
                Spacer(Modifier.height(8.dp))
                Text(tv.nama, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text("Sesi selesai", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Spacer(Modifier.height(12.dp))
                Text(
                    if (tv.sudahBayar) "✓ Sudah Bayar" else "✗ Belum Bayar",
                    color = if (tv.sudahBayar) NeonGreen else NeonRed,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(16.dp))
                if (tv.sudahBayar) {
                    Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = DarkBackground)) { Text("OK") }
                } else {
                    Button(
                        onClick = {
                            onSudahBayar?.invoke()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = DarkBackground),
                    ) { Text("Sudah Bayar") }
                }
            }
        }
    }
}

private fun Map<String, Int>.plus(pair: Pair<String, Int>): Map<String, Int> = this + pair
private fun Map<String, Int>.minus(key: String): Map<String, Int> = this.filterKeys { it != key }

@Composable private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = NeonGreen, unfocusedBorderColor = DarkSurfaceV3,
    cursorColor = NeonGreen, focusedLabelColor = NeonGreen,
    unfocusedContainerColor = DarkSurfaceV2, focusedContainerColor = DarkSurfaceV2,
)
