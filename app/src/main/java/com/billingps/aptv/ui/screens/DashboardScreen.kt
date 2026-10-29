@file:OptIn(ExperimentalMaterial3Api::class)
package com.billingps.aptv.ui.screens

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.billingps.aptv.tv.HdmiCommandCatalog
import com.billingps.aptv.tv.TvHubManager
import com.billingps.aptv.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.billingps.aptv.ui.screens.ClientApkInstallerDialog
import com.billingps.aptv.utils.ApkServer
import com.billingps.aptv.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
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
    var showPembayaran by remember { mutableStateOf(false) }
    var pembayaranTV by remember { mutableStateOf<TvData?>(null) }
    var pembayaranTransaksi by remember { mutableStateOf<List<Transaksi>>(emptyList()) }
    var pembayaranMode by remember { mutableStateOf("IDLE") }
    var showDetailTV by remember { mutableStateOf(false) }
    var detailTV by remember { mutableStateOf<TvData?>(null) }
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
    var showClientApkInstaller by remember { mutableStateOf(false) }
    var installerTvIp by remember { mutableStateOf("") }
    var installerTvNama by remember { mutableStateOf("") }
    var uploadTargetTv by remember { mutableStateOf<TvData?>(null) }
    var showPilihTvUpload by remember { mutableStateOf(false) }
    var uploadPilihMode by remember { mutableStateOf("media") }
    var showPindahTvDialog by remember { mutableStateOf(false) }
    var pindahTvSource by remember { mutableStateOf<TvData?>(null) }
    var showMediaUploader by remember { mutableStateOf(false) }
    var showNotifUploader by remember { mutableStateOf(false) }
    var uploadMode by remember { mutableStateOf("media") }
    var uploadStatus by remember { mutableStateOf("") }
    val ctx = LocalContext.current

    val uploadScope = rememberCoroutineScope()
    val autoPowerSentSessions = remember { mutableStateMapOf<String, Long>() }

    fun bukaPembayaran(tv: TvData, mode: String) {
        val current = state.tvList.firstOrNull { it.id == tv.id } ?: tv
        pembayaranTV = current
        pembayaranTransaksi = viewModel.transaksiSesi(current)
        pembayaranMode = mode
        showPembayaran = true
    }

    fun tutupPembayaran() {
        showPembayaran = false
        pembayaranTV = null
        pembayaranTransaksi = emptyList()
        pembayaranMode = "IDLE"
    }

    fun startUpload(uri: Uri, tv: TvData, username: String, mode: String) {
        uploadStatus = "Mengupload..."
        uploadScope.launch {
            try {
                val inputStream = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri) }
                if (inputStream == null) { uploadStatus = "Gagal: tidak bisa baca file"; return@launch }
                val name = uri.lastPathSegment?.substringAfterLast("/") ?: "video.mp4"
                val cacheFile = File(ctx.cacheDir, "uploads/$name")
                withContext(Dispatchers.IO) {
                    cacheFile.parentFile?.mkdirs()
                    cacheFile.outputStream().use { out -> inputStream.copyTo(out) }
                    inputStream.close()
                }

                val ip = withContext(Dispatchers.IO) { ApkServer.getLocalIpAddress(ctx) }
                if (ip == null) { uploadStatus = "Gagal: tidak bisa dapat IP lokal"; return@launch }
                withContext(Dispatchers.IO) {
                    val apkFile = File(ctx.cacheDir, "tv-client.apk")
                    if (!apkFile.exists()) {
                        ctx.assets.open("tv-client.apk").use { input ->
                            apkFile.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                    ApkServer.start(ctx, apkFile)
                }
                val url = "http://$ip:${ApkServer.port}/uploads/$name"
                withContext(Dispatchers.IO) {
                    if (mode == "media") {
                        viewModel.cacheMediaOnTv(username, tv.id, url)
                    } else {
                        viewModel.sendWarningVideoToTv(username, tv.id, url)
                    }
                }
                uploadStatus = "Upload berhasil!"
            } catch (e: Exception) {
                uploadStatus = "Gagal upload: ${e.message}"
            }
        }
    }

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null && uploadTargetTv != null) {
            startUpload(uri, uploadTargetTv!!, state.currentUser, uploadMode)
        }
    }

    LaunchedEffect(showMediaUploader) {
        if (showMediaUploader) {
            uploadMode = "media"
            mediaPickerLauncher.launch(arrayOf("video/*", "image/*"))
            showMediaUploader = false
        }
    }

    LaunchedEffect(showNotifUploader) {
        if (showNotifUploader) {
            uploadMode = "notifikasi"
            mediaPickerLauncher.launch(arrayOf("video/*", "image/*"))
            showNotifUploader = false
        }
    }

    if (uploadStatus.isNotEmpty()) {
        val title = if (uploadMode == "media") "Upload Media" else "Upload Notifikasi"
        AlertDialog(
            onDismissRequest = { uploadStatus = ""; uploadTargetTv = null },
            containerColor = DarkSurface,
            title = { Text(title, color = NeonCyan, fontWeight = FontWeight.Bold) },
            text = { Text(uploadStatus, color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { uploadStatus = ""; uploadTargetTv = null }) { Text("OK", color = NeonGreen) }
            },
        )
    }

    // Check for update on first render
    LaunchedEffect(Unit) {
        viewModel.checkForUpdate()
    }

    val WhiteNeon = Color(0xFFF0FDF4)
    val GreenTosca = Color(0xFF00C9A7)
    val headerGradient = Brush.horizontalGradient(colors = listOf(WhiteNeon, GreenTosca))
    val activeSessionCount = state.tvList.count { it.timerActive || it.bebas }
    val pausedSessionCount = state.tvList.count { it.sesiPaused }

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
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { viewModel.pauseAllSessions() },
                    enabled = activeSessionCount > 0,
                    modifier = Modifier.size(36.dp).background(DarkBackground, CircleShape),
                ) {
                    Icon(
                        Icons.Filled.Pause,
                        contentDescription = "Pause semua sesi",
                        tint = if (activeSessionCount > 0) WhiteNeon else WhiteNeon.copy(alpha = 0.4f),
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(
                    onClick = { viewModel.resumeAllSessions() },
                    enabled = pausedSessionCount > 0,
                    modifier = Modifier.size(36.dp).background(DarkBackground, CircleShape),
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "Resume semua sesi",
                        tint = if (pausedSessionCount > 0) WhiteNeon else WhiteNeon.copy(alpha = 0.4f),
                        modifier = Modifier.size(18.dp),
                    )
                }
                FilledTonalButton(
                    onClick = { showTambah = true },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = DarkBackground, contentColor = WhiteNeon),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Tambah TV", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        if (state.tvList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Tv, contentDescription = null, modifier = Modifier.size(64.dp), tint = TextDim)
                    Spacer(Modifier.height(12.dp))
                    Text("Belum ada TV", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
                    Text("Tap \"Tambah TV\" untuk memulai", style = MaterialTheme.typography.bodySmall, color = TextDim)
                }
            }
        } else {
            val listState = rememberLazyListState()
            val tvList = state.tvList
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                    state = listState,
                ) {
                    itemsIndexed(tvList, key = { _, tv -> tv.id }) { idx, tv ->
                    val forceDisabled = state.licenseStatus.status != "active" && idx >= 2
                    val sesiTxs = viewModel.transaksiSesi(tv)
                    val tagihanSesi = sesiTxs
                        .filter { !it.sudahBayar && it.paket != "BATAL" }
                        .sumOf { it.total.coerceAtLeast(0) }
                    val totalSesi = sesiTxs
                        .filter { it.paket != "BATAL" }
                        .sumOf { it.total.coerceAtLeast(0) }
                    TVCard(
                        tv = tv,
                        clientStatus = state.clientStatus[tv.id],
                        forceDisabled = forceDisabled,
                        tagihanSesi = tagihanSesi,
                        totalSesi = totalSesi,
                        onPilihPaket = {
                            if (tv.adaTagihan && !tv.timerActive && !tv.bebas && !tv.sesiPaused) {
                                bukaPembayaran(tv, "IDLE")
                            } else {
                                selectedTV = tv
                                showPaket = true
                            }
                        },
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
                                tvViewModel.sendPower(tv.ip, tv.certPem, tv.keyPem) { result ->
                                    if (result.ok && TvHubManager.connectionFor(tv)?.screenOn == true) {
                                        viewModel.playCachedMediaOnTv(state.currentUser, tv.id)
                                    }
                                }
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
                                val remotePort = if (tv.hdmiPort in 1..4) tv.hdmiPort else 1
                                val keys = HdmiCommandCatalog.findRemoteKeys(tv.hdmiRemoteCommand, remotePort)
                                if (keys.isNotEmpty()) {
                                    tvViewModel.sendKeys(tv.ip, keys, "HDMI") { _, _ -> }
                                } else {
                                    tvViewModel.sendKeyCommand(tv.ip, tv.certPem, tv.keyPem, "TV_INPUT", "HDMI")
                                }
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
                            pindahTvSource = tv
                            showPindahTvDialog = true
                        },
                        onSudahBayar = { bukaPembayaran(tv, "IDLE") },
                        onTagihan = {
                            val ids = viewModel.transaksiSesi(tv)
                                .filter { !it.sudahBayar && it.paket != "BATAL" }
                                .map { it.id }
                            if (ids.isNotEmpty()) {
                                viewModel.settlePembayaran(tv.id, ids, lunas = false)
                                if (!tv.timerActive && !tv.bebas) viewModel.finishSession(tv.id)
                            }
                        },
                        onDetail = {
                            detailTV = tv
                            showDetailTV = true
                        },
                        onResume = { viewModel.resumeSession(tv.id) },
                        onBatal = {
                            selectedTV = tv
                            showBatalDialog = true
                        },
                        onInstallClient = {
                            installerTvIp = tv.ip
                            installerTvNama = tv.nama
                            showClientApkInstaller = true
                        },
                    )
                }
            }
        }

        // Start background timer service when any timer is active
        LaunchedEffect(state.tvList) {
            val hasActiveTimer = state.tvList.any { it.timerActive && !it.bebas && it.sisaDetik > 0 } || state.tvList.any { it.bebas }
            if (hasActiveTimer && !com.billingps.aptv.TimerService.isRunning()) {
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

        LaunchedEffect(state.tvList) {
            state.tvList.forEach { tv ->
                if (tv.paketAktif == "WAKTU HABIS" && tv.sisaDetik == 0L && !tv.sudahBayar) {
                    if (autoPowerSentSessions[tv.id] != tv.timerStart) {
                        if (tv.certPem.isNotEmpty() && tv.keyPem.isNotEmpty()) {
                            tvViewModel.sendPower(tv.ip, tv.certPem, tv.keyPem, false)
                            autoPowerSentSessions[tv.id] = tv.timerStart
                        }
                    }
                }
            }
            val expiredTv = state.tvList.firstOrNull {
                it.paketAktif == "WAKTU HABIS" && it.sisaDetik == 0L && it.id !in habisDismissedIds && !it.sudahBayar
            }
            if (expiredTv != null && tvHabis == null) {
                tvHabis = expiredTv; showHabisDialog = true
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

    val detailTvValue = detailTV
    if (showDetailTV && detailTvValue != null) {
        val current = state.tvList.firstOrNull { it.id == detailTvValue.id } ?: detailTvValue
        DetailSesiDialog(
            tv = current,
            transaksi = viewModel.transaksiSesi(current),
            onDismiss = { showDetailTV = false; detailTV = null },
            onBayar = {
                showDetailTV = false
                detailTV = null
                bukaPembayaran(current, "IDLE")
            },
        )
    }

    val selesaiSummaryTv = selesaiSummaryTV
    if (showSelesaiSummary && selesaiSummaryTv != null) {
        SelesaiSummaryDialog(
            tv = selesaiSummaryTv,
            onBatal = { showSelesaiSummary = false; selesaiSummaryTV = null },
            onSelesai = {
                viewModel.finishSession(selesaiSummaryTv.id)
                if (selesaiSummaryTv.certPem.isNotEmpty() && selesaiSummaryTv.keyPem.isNotEmpty()) {
                    tvViewModel.sendPower(
                        selesaiSummaryTv.ip,
                        selesaiSummaryTv.certPem,
                        selesaiSummaryTv.keyPem,
                        false,
                    )
                }
                showSelesaiSummary = false
                selesaiSummaryTV = null
            },
            onBayar = {
                showSelesaiSummary = false
                selesaiSummaryTV = null
                viewModel.finishSession(selesaiSummaryTv.id)
                val updated = viewModel.state.value.tvList.firstOrNull { it.id == selesaiSummaryTv.id }
                if (updated != null) bukaPembayaran(updated, "IDLE")
            },
            onPrint = {
                val txs = viewModel.transaksiSesi(selesaiSummaryTv)
                val tx = txs.firstOrNull()
                    ?: state.transaksiList.filter { it.kota == selesaiSummaryTv.nama }.maxByOrNull { it.waktu }
                if (tx != null) {
                    viewModel.printReceipt(tx, selesaiSummaryTv)
                }
            },
        )
    }

    val pembayaranTvValue = pembayaranTV
    if (showPembayaran && pembayaranTvValue != null) {
        val currentTv = state.tvList.firstOrNull { it.id == pembayaranTvValue.id } ?: pembayaranTvValue
        val txs = viewModel.transaksiSesi(currentTv).ifEmpty { pembayaranTransaksi }
        PembayaranDialog(
            tv = currentTv,
            transaksi = txs,
            onDismiss = { tutupPembayaran() },
            onBayar = { ids, cash, kembalian, metode ->
                viewModel.settlePembayaran(
                    tvId = currentTv.id,
                    transactionIds = ids,
                    lunas = true,
                    metode = metode,
                    cash = cash,
                    kembalian = kembalian,
                )
                if (pembayaranMode == "FINAL") viewModel.finishSession(currentTv.id)
                tutupPembayaran()
            },
            onTagihan = { ids ->
                viewModel.settlePembayaran(
                    tvId = currentTv.id,
                    transactionIds = ids,
                    lunas = false,
                )
                if (pembayaranMode == "FINAL") viewModel.finishSession(currentTv.id)
                tutupPembayaran()
            },
        )
    }

    val tvHabisValue = tvHabis
    if (showHabisDialog && tvHabisValue != null) {
        val alreadyPoweredOff = autoPowerSentSessions[tvHabisValue.id] == tvHabisValue.timerStart
        HabisDialog(
            tv = tvHabisValue,
            tvViewModel = tvViewModel,
            autoPowerSent = alreadyPoweredOff,
            onDismiss = {
                viewModel.finishSession(tvHabisValue.id)
                viewModel.dismissWaktuHabis(tvHabisValue.id)
                showHabisDialog = false; tvHabis = null
            },
            onSudahBayar = {
                viewModel.dismissWaktuHabis(tvHabisValue.id)
                viewModel.finishSession(tvHabisValue.id)
                showHabisDialog = false; tvHabis = null
                val updated = viewModel.state.value.tvList.firstOrNull { it.id == tvHabisValue.id }
                if (updated != null) bukaPembayaran(updated, "IDLE")
            },
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
                    val now = System.currentTimeMillis()
                    val updates = mutableMapOf<String, Any>(
                        "paketAktif" to "${tvData.paketAktif}+$selectedPaket",
                        "paketHarga" to (tvData.paketHarga + tambahHarga),
                        "sisaDetik" to newSisa,
                        "timerActive" to (newSisa > 0),
                        "timerStart" to now,
                        "timerDurasi" to newSisa,
                        "paketSudahBayar" to false,
                        "sudahBayar" to false,
                    )
                    viewModel.updateTV(tvData.id, updates)
                    viewModel.tambahTransaksi(Transaksi(
                        id = "TRX-$now-ADD",
                        waktu = TimeUtils.nowUtcIso(),
                        kasir = state.currentUser,
                        kota = tvData.nama,
                        paket = "Tambah Waktu: $selectedPaket",
                        pesanan = emptyMap(),
                        total = tambahHarga,
                        paketHarga = tambahHarga,
                        tvJenisPs = tvData.jenisPs,
                        sudahBayar = false,
                        tvId = tvData.id,
                        kategori = "PAKET",
                        jumlah = 1,
                        sesiMulai = tvData.sesiMulai,
                    ))
                    val updated = viewModel.state.value.tvList.firstOrNull { it.id == tvData.id }
                    showTambahWaktu = false; tambahWaktuTV = null
                    if (updated != null && tambahHarga > 0) bukaPembayaran(updated, "IDLE")
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
                        val now = System.currentTimeMillis()
                        viewModel.tambahTransaksi(Transaksi(
                            id = "TRX-$now-BATAL",
                            waktu = TimeUtils.nowUtcIso(),
                            kasir = state.currentUser,
                            kota = tvData.nama,
                            paket = "BATAL: ${lastTx.paket}",
                            pesanan = emptyMap(),
                            total = -lastTx.total,
                            paketHarga = -lastTx.paketHarga,
                            tvJenisPs = tvData.jenisPs,
                            sudahBayar = true,
                            metode = "BATAL",
                            tvId = tvData.id,
                            kategori = "BATAL",
                            sesiMulai = tvData.sesiMulai,
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
                val now = System.currentTimeMillis()
                val txStamp = now.toString()
                val wasActive = selectedTvPaket.timerActive || selectedTvPaket.bebas
                val sesiMulai = when {
                    selectedTvPaket.sesiMulai > 0L -> selectedTvPaket.sesiMulai
                    wasActive -> 0L
                    else -> now
                }
                val unitPrices = pesanan.entries.associate { (name, _) ->
                    name to (state.menuMakanan[name] ?: state.menuMinuman[name] ?: 0)
                }
                val fnbTotal = pesanan.entries.sumOf { (name, qty) -> (unitPrices[name] ?: 0) * qty }
                val isBebas = paketNm == "Main Bebas"
                val isNewSession = paketNm != null && !wasActive
                val paketTxId = "TRX-$txStamp-P"
                val fnbTxId = "TRX-$txStamp-F"
                val finalUpdates = (updates ?: emptyMap()).toMutableMap()
                finalUpdates["tvId"] = selectedTvPaket.id
                finalUpdates["sesiMulai"] = sesiMulai
                if (isNewSession) {
                    finalUpdates["sesiSelesai"] = 0L
                    finalUpdates["sesiPaused"] = false
                    finalUpdates["bebasTertundaMs"] = 0L
                    finalUpdates["cashDiterima"] = 0
                    finalUpdates["kembalian"] = 0
                }
                if (paketNm != null) {
                    finalUpdates["trxId"] = if (isBebas) "" else paketTxId
                    finalUpdates["paketSudahBayar"] = isBebas
                    finalUpdates["fnbSudahBayar"] = fnbTotal == 0
                    finalUpdates["sudahBayar"] = isBebas && fnbTotal == 0
                    if (isBebas) finalUpdates["paketHarga"] = 0
                } else {
                    if (!wasActive) {
                        finalUpdates["sesiSelesai"] = now
                        finalUpdates["trxId"] = ""
                    }
                    finalUpdates["fnbSudahBayar"] = false
                    finalUpdates["sudahBayar"] = false
                }
                viewModel.updateTV(selectedTvPaket.id, finalUpdates)

                if (paketNm != null && !isBebas) {
                    viewModel.tambahTransaksi(Transaksi(
                        id = paketTxId,
                        waktu = TimeUtils.nowUtcIso(),
                        kasir = state.currentUser,
                        kota = selectedTvPaket.nama,
                        paket = paketNm,
                        pesanan = emptyMap(),
                        total = paketHarga,
                        paketHarga = paketHarga,
                        pesananHarga = emptyMap(),
                        tvJenisPs = selectedTvPaket.jenisPs,
                        sudahBayar = isBebas,
                        metode = if (isBebas) "Bebas" else "",
                        tvId = selectedTvPaket.id,
                        kategori = "PAKET",
                        jumlah = 1,
                        sesiMulai = sesiMulai,
                    ))
                }
                if (pesanan.isNotEmpty() && fnbTotal > 0) {
                    viewModel.tambahTransaksi(Transaksi(
                        id = fnbTxId,
                        waktu = TimeUtils.nowUtcIso(),
                        kasir = state.currentUser,
                        kota = selectedTvPaket.nama,
                        paket = "F&B",
                        pesanan = pesanan,
                        total = fnbTotal,
                        paketHarga = 0,
                        pesananHarga = unitPrices,
                        tvJenisPs = selectedTvPaket.jenisPs,
                        sudahBayar = false,
                        tvId = selectedTvPaket.id,
                        kategori = "FNB",
                        jumlah = 1,
                        sesiMulai = sesiMulai,
                    ))
                }
                showPaket = false; selectedTV = null
                if (paketNm != null || fnbTotal > 0) {
                    val updated = viewModel.state.value.tvList.firstOrNull { it.id == selectedTvPaket.id }
                    if (updated != null) bukaPembayaran(updated, "IDLE")
                }
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

    // ── Install Client APK Dialog ────────────────────────
    if (showClientApkInstaller && installerTvIp.isNotEmpty()) {
        ClientApkInstallerDialog(
            tvIp = installerTvIp,
            tvNama = installerTvNama,
            onDismiss = {
                showClientApkInstaller = false
                installerTvIp = ""
                installerTvNama = ""
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
                        try {
                            if (buatPasswordInput.length < 4) { buatPasswordError = "Minimal 4 karakter"; return@Button }
                            if (buatPasswordInput != buatPasswordConfirm) { buatPasswordError = "Konfirmasi tidak cocok"; return@Button }
                            viewModel.setTvPassword(buatPasswordInput)
                            viewModel.hapusTV(tvHapusTargetBuat.id)
                            showBuatPasswordDialog = false
                            tvHapusTarget = null
                        } catch (e: Exception) {
                            buatPasswordError = "Error: ${e.message}"
                        }
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
                        try {
                            if (!viewModel.verifyTvPassword(hapusPasswordInput)) {
                                hapusPasswordError = "Password salah"
                                return@Button
                            }
                            viewModel.hapusTV(tvHapusTargetHapus.id)
                            showHapusPasswordDialog = false
                            tvHapusTarget = null
                        } catch (e: Exception) {
                            hapusPasswordError = "Error: ${e.message}"
                        }
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

    // ── Pilih TV untuk Upload ─────────────────────────────
    if (showPilihTvUpload) {
        AlertDialog(
            onDismissRequest = { showPilihTvUpload = false },
            containerColor = DarkSurface,
            title = { Text("Pilih TV", color = NeonCyan, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Pilih TV untuk ${if (uploadPilihMode == "media") "Media Bangun Tidur" else "Video Notifikasi 5 Menit"}:",
                        style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    state.tvList.forEach { tv ->
                        TextButton(
                            onClick = {
                                showPilihTvUpload = false
                                uploadTargetTv = tv
                                if (uploadPilihMode == "media") {
                                    uploadMode = "media"
                                    showMediaUploader = true
                                } else {
                                    uploadMode = "notifikasi"
                                    showNotifUploader = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(tv.nama, color = TextPrimary)
                            Spacer(Modifier.weight(1f))
                            Text(tv.jenisPs, color = TextDim, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPilihTvUpload = false }) { Text("Batal", color = TextSecondary) }
            },
        )
    }

    // ── Pindah TV Dialog ──────────────────────────────────
    if (showPindahTvDialog && pindahTvSource != null) {
        AlertDialog(
            onDismissRequest = { showPindahTvDialog = false; pindahTvSource = null },
            containerColor = DarkSurface,
            title = { Text("PINDAH DATA", color = NeonCyan, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Pilih TV tujuan untuk memindahkan data dari ${pindahTvSource!!.nama}:",
                        style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    state.tvList.filter { it.id != pindahTvSource!!.id && !it.timerActive && !it.bebas && it.paketAktif.isEmpty() }.forEach { target ->
                        TextButton(
                            onClick = {
                                showPindahTvDialog = false
                                viewModel.pindahTV(pindahTvSource!!.id, target.id)
                                if (pindahTvSource!!.certPem.isNotEmpty() && pindahTvSource!!.keyPem.isNotEmpty()) {
                                    tvViewModel.sendPower(
                                        pindahTvSource!!.ip,
                                        pindahTvSource!!.certPem,
                                        pindahTvSource!!.keyPem,
                                        false,
                                    )
                                }
                                pindahTvSource = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(target.nama, color = TextPrimary)
                            Spacer(Modifier.weight(1f))
                            Text(target.jenisPs, color = TextDim, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPindahTvDialog = false; pindahTvSource = null }) { Text("Batal", color = TextSecondary) }
            },
        )
    }
}

@Composable
fun TVCard(
    tv: TvData,
    clientStatus: ClientStatus? = null,
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
    onInstallClient: () -> Unit = {},
    onSudahBayar: () -> Unit = {},
    onTagihan: () -> Unit = {},
    onDetail: () -> Unit = {},
    onResume: () -> Unit = {},
    tagihanSesi: Int = 0,
    totalSesi: Int = 0,
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

    val sisaWaktu = if (tv.sesiPaused && tv.sisaDetik > 0) {
        val m = tv.sisaDetik / 60
        val d = tv.sisaDetik % 60
        "${m}m ${d}d (dijeda)"
    } else if (tv.timerActive && !tv.bebas) {
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
    val isPaused = tv.sesiPaused
    val adaTagihan = tv.adaTagihan

    val blinkAlpha by if (isAlmostHabis) {
        val infiniteTransition = rememberInfiniteTransition(label = "blink")
        infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(500),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "blinkAlpha",
        )
    } else remember { mutableStateOf(1f) }

    val isTersedia = !tv.timerActive && !tv.bebas && !isHabis && !isAlmostHabis && !isPaused && !adaTagihan
    val cardBg = when {
        isHabis -> Color(0xFFEF4444)
        isAlmostHabis -> Color(0xFFF59E0B).copy(alpha = blinkAlpha)
        isPaused -> Color(0xFF6B7280)
        tv.timerActive || tv.bebas -> Color(0xFF3498DB)
        adaTagihan -> Color(0xFFB45309)
        else -> Color.White
    }
    val gradientShape = RoundedCornerShape(20.dp)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha)
            .clickable(enabled = !forceDisabled) { onDetail() }
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
                Spacer(Modifier.weight(1f))
                Text(tv.nama, style = MaterialTheme.typography.titleLarge,
                    color = if (isTersedia) Color(0xFF1A1A1A) else Color.White, fontWeight = FontWeight.Bold)
                if (clientStatus != null) {
                    Spacer(Modifier.width(8.dp))
                    val (dotColor, tooltip) = if (clientStatus.online) Pair(NeonGreen, "Client Aktif") else Pair(NeonRed, "Client Offline")
                    Box(
                        modifier = Modifier.size(10.dp).clip(CircleShape).background(dotColor)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(tooltip, color = TextDim, fontSize = 10.sp)
                }
            }

            if (isPaused) {
                Spacer(Modifier.height(8.dp))
                Text("DIJEDA", style = MaterialTheme.typography.bodyMedium,
                    color = Color.White, fontWeight = FontWeight.Bold)
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
            } else if (isTersedia) {
                Spacer(Modifier.height(8.dp))
                Text("TERSEDIA", style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF1A1A1A), fontWeight = FontWeight.Bold)
            }
            if (tv.bebas) {
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
            val hasSession = tv.sesiMulai > 0L || tv.timerActive || tv.bebas || tv.sesiPaused || tv.sesiSelesai > 0L
            val tagihan = if (tagihanSesi > 0) tagihanSesi else tv.totalBelanja
            val totalSesiTampil = maxOf(totalSesi, tv.totalBelanja)
            val lunasSesi = tagihan <= 0 && totalSesiTampil > 0
            if (hasSession && totalSesiTampil > 0) {
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text(
                        if (lunasSesi) "LUNAS" else "TAGIHAN",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (lunasSesi) NeonGreen else NeonYellow,
                    )
                    Text(
                        fmtRp(if (lunasSesi) totalSesiTampil else tagihan),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (lunasSesi) NeonGreen else Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (tagihan > 0 && !tv.timerActive && !tv.bebas && !isPaused) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = { if (!forceDisabled) onSudahBayar() },
                            enabled = !forceDisabled,
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        ) {
                            Text("Lunas", style = MaterialTheme.typography.labelSmall, color = NeonGreen)
                        }
                        TextButton(
                            onClick = { if (!forceDisabled) onTagihan() },
                            enabled = !forceDisabled,
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        ) {
                            Text("Tagihan", style = MaterialTheme.typography.labelSmall, color = NeonYellow)
                        }
                    }
                }
            }
            if (tv.cashDiterima > 0 || tv.kembalian > 0) {
                Spacer(Modifier.height(2.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("Tunai ${fmtRp(tv.cashDiterima)}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Text("Kembali ${fmtRp(tv.kembalian)}", style = MaterialTheme.typography.bodySmall, color = NeonYellow, fontWeight = FontWeight.Bold)
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
                ) { Text(
                    if (adaTagihan && !tv.timerActive && !tv.bebas && !isPaused) "Bayar"
                    else if (tv.timerActive || tv.bebas) "Pesanan"
                    else "Paket",
                    style = MaterialTheme.typography.labelSmall,
                ) }
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
                if (isPaused) {
                    OutlinedButton(
                        onClick = { if (!forceDisabled) onResume() },
                        enabled = !forceDisabled,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonGreen),
                        border = BorderStroke(1.dp, NeonGreen),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    ) { Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(2.dp)); Text("Lanjut", style = MaterialTheme.typography.labelSmall) }
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
                    onClick = { if (!forceDisabled) onPower() },
                    enabled = !forceDisabled,
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonRed),
                    border = BorderStroke(1.dp, NeonRed),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                ) { Icon(Icons.Filled.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(14.dp)) }
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
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                    ) { Text("OK", style = MaterialTheme.typography.labelSmall, color = NeonGreen) }
                    if (clientStatus?.online != true) {
                        OutlinedButton(
                            onClick = { if (!forceDisabled) onInstallClient() },
                            enabled = !forceDisabled,
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                            border = BorderStroke(1.dp, NeonCyan),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        ) { Icon(Icons.Filled.Android, contentDescription = null, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(2.dp)); Text("Install Client", style = MaterialTheme.typography.labelSmall) }
                    }
                    if (tv.timerActive || tv.bebas || isPaused) {
                        OutlinedButton(
                            onClick = { if (!forceDisabled) onSelesai() },
                            enabled = !forceDisabled,
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonRed),
                            border = BorderStroke(1.dp, NeonRed),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        ) { Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(2.dp)); Text("Selesai", style = MaterialTheme.typography.labelSmall) }
                    }
                    TextButton(onClick = { if (!forceDisabled) onPindahTV() }, enabled = !forceDisabled, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                        Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(12.dp), tint = if (forceDisabled) TextDim else NeonCyan)
                        Spacer(Modifier.width(2.dp))
                        Text("Pindah", style = MaterialTheme.typography.labelSmall, color = if (forceDisabled) TextDim else NeonCyan)
                    }
                    if (tv.timerActive || tv.bebas || isPaused || adaTagihan) {
                        TextButton(onClick = { if (!forceDisabled) onSudahBayar() }, enabled = !forceDisabled, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                            Icon(Icons.Filled.Payments, contentDescription = null, modifier = Modifier.size(12.dp), tint = if (forceDisabled) TextDim else NeonGreen)
                            Spacer(Modifier.width(2.dp))
                            Text("Bayar", style = MaterialTheme.typography.labelSmall, color = if (forceDisabled) TextDim else NeonGreen)
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
                                "totalPesanan" to if (tv.bebas) tv.totalPesanan else (tv.totalPesanan + total),
                                "bebasPesananTotal" to if (tv.bebas) (tv.bebasPesananTotal + total) else tv.bebasPesananTotal,
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
fun DetailSesiDialog(
    tv: TvData,
    transaksi: List<Transaksi>,
    onDismiss: () -> Unit,
    onBayar: () -> Unit,
) {
    val txAktif = transaksi.filter { it.paket != "BATAL" }
    val paketTxs = txAktif.filter { it.isPaketTx() }
    val fnbTxs = txAktif.filter {
        it.isFnbTx() || (it.isPaketTx() && it.kategori.isBlank() && it.pesanan.isNotEmpty())
    }
    val paketGroup = paketTxs.groupBy { it.namaPaketBersih().ifBlank { it.paket } }
    val fnbItems = mutableMapOf<String, Pair<Int, Int>>()
    fnbTxs.forEach { tx ->
        tx.pesanan.forEach { (name, qty) ->
            val unit = tx.pesananHarga[name] ?: 0
            val old = fnbItems[name]
            fnbItems[name] = ((old?.first ?: 0) + qty) to ((old?.second ?: 0) + unit * qty)
        }
    }
    val paketTotal = paketTxs.sumOf { it.paketHarga }
    val fnbTotal = fnbItems.values.sumOf { it.second }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = { Text(tv.nama, color = NeonGreen) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    when {
                        tv.sesiPaused -> "DIJEDA"
                        tv.timerActive || tv.bebas -> "BERJALAN"
                        tv.adaTagihan -> "TAGIHAN"
                        else -> "SELESAI"
                    },
                    color = when {
                        tv.sesiPaused -> NeonYellow
                        tv.timerActive || tv.bebas -> NeonCyan
                        tv.adaTagihan -> NeonRed
                        else -> NeonGreen
                    },
                    fontWeight = FontWeight.Bold,
                )
                if (paketGroup.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("PAKET", color = NeonCyan, style = MaterialTheme.typography.labelLarge)
                    paketGroup.forEach { (nama, items) ->
                        val lunas = items.all { it.sudahBayar }
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text("$nama x ${items.size}", color = TextPrimary, style = MaterialTheme.typography.bodySmall)
                            Text(
                                "${fmtRp(items.sumOf { it.paketHarga })} ${if (lunas) "LUNAS" else "TAGIHAN"}",
                                color = if (lunas) NeonGreen else NeonRed,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                if (fnbItems.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("F&B", color = NeonCyan, style = MaterialTheme.typography.labelLarge)
                    fnbItems.forEach { (name, pair) ->
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text("${pair.first}x $name", color = TextPrimary, style = MaterialTheme.typography.bodySmall)
                            Text(fmtRp(pair.second), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text(
                        if (tv.fnbSudahBayar) "F&B LUNAS" else "F&B TAGIHAN",
                        color = if (tv.fnbSudahBayar) NeonGreen else NeonRed,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = DarkSurfaceV3)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("Total", color = TextPrimary, fontWeight = FontWeight.Bold)
                    Text(fmtRp(paketTotal + fnbTotal), color = NeonGreen, fontWeight = FontWeight.Bold)
                }
                if (tv.paketSudahBayar) Text("Paket: LUNAS", color = NeonGreen, style = MaterialTheme.typography.bodySmall)
                if (tv.fnbSudahBayar && (fnbTotal > 0 || tv.totalFnb > 0)) {
                    Text("F&B: LUNAS", color = NeonGreen, style = MaterialTheme.typography.bodySmall)
                }
                if (tv.cashDiterima > 0) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text("Tunai", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        Text(fmtRp(tv.cashDiterima), color = TextPrimary, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text("Kembali", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        Text(fmtRp(tv.kembalian), color = NeonYellow, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            if (tv.adaTagihan) {
                Button(
                    onClick = onBayar,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = DarkBackground),
                ) { Text("Bayar") }
            } else {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = DarkBackground),
                ) { Text("Tutup") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Tutup", color = TextSecondary) }
        },
    )
}

@Composable
fun SelesaiSummaryDialog(
    tv: TvData,
    onBatal: () -> Unit,
    onSelesai: () -> Unit,
    onBayar: () -> Unit = {},
    onPrint: (() -> Unit)? = null,
) {
    val now = System.currentTimeMillis()
    val mulai = when {
        tv.sesiMulai > 0L -> tv.sesiMulai
        tv.bebas && tv.bebasMulai > 0L -> tv.bebasMulai
        tv.timerStart > 0L -> tv.timerStart
        else -> 0L
    }
    val selesai = if (tv.sesiSelesai > 0L) tv.sesiSelesai else now
    val bebasSession = tv.bebas || (
        tv.sesiPaused && (tv.bebasHargaPerJam > 0 || tv.paketAktif.contains("Bebas", ignoreCase = true))
        )
    val elapsedBebas = when {
        tv.bebas && tv.bebasMulai > 0L -> (selesai - tv.bebasMulai).coerceAtLeast(0L) + tv.bebasTertundaMs
        else -> tv.bebasTertundaMs.coerceAtLeast(0L)
    }
    val totalBiaya = if (bebasSession) {
        ((elapsedBebas * tv.bebasHargaPerJam) / 3600000).toInt() + tv.bebasPesananTotal
    } else {
        tv.paketHarga + tv.totalFnb
    }
    val totalMenit = if (bebasSession) {
        (elapsedBebas / 60000).toInt()
    } else if (mulai > 0L) {
        ((selesai - mulai).coerceAtLeast(0L) / 60000).toInt()
    } else 0
    val durasiText = "${totalMenit / 60}j ${totalMenit % 60}m"
    val fmtWaktu = remember { SimpleDateFormat("dd/MM HH:mm", Locale("id", "ID")) }
    val perluBayar = tv.adaTagihan || (bebasSession && totalBiaya > 0)

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
                if (mulai > 0L) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text("Mulai", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text(fmtWaktu.format(Date(mulai)), style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                    }
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text("Selesai", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text(fmtWaktu.format(Date(selesai)), style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                    }
                }
                if (tv.totalFnb > 0) {
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text("Paket", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text(
                            "${fmtRp(tv.paketHarga)} ${if (tv.paketSudahBayar) "LUNAS" else "TAGIHAN"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (tv.paketSudahBayar) NeonGreen else NeonRed,
                        )
                    }
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text("F&B", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text(
                            "${fmtRp(tv.totalFnb)} ${if (tv.fnbSudahBayar) "LUNAS" else "TAGIHAN"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (tv.fnbSudahBayar) NeonGreen else NeonRed,
                        )
                    }
                } else if (tv.paketHarga > 0) {
                    Text(
                        if (tv.paketSudahBayar) "Paket LUNAS" else "Paket TAGIHAN",
                        color = if (tv.paketSudahBayar) NeonGreen else NeonRed,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("TOTAL", style = MaterialTheme.typography.titleMedium, color = NeonGreen)
                    Text(fmtRp(totalBiaya), style = MaterialTheme.typography.titleMedium, color = NeonGreen)
                }
                if (tv.cashDiterima > 0) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text("Tunai", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text(fmtRp(tv.cashDiterima), style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                    }
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text("Kembali", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text(fmtRp(tv.kembalian), style = MaterialTheme.typography.bodySmall, color = NeonYellow, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (tv.semuaLunas && !perluBayar) "SUDAH BAYAR" else "BELUM BAYAR",
                    color = if (tv.semuaLunas && !perluBayar) NeonGreen else NeonRed,
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
                if (perluBayar) {
                    OutlinedButton(
                        onClick = onBayar,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonGreen),
                        border = BorderStroke(1.dp, NeonGreen),
                    ) { Text("Bayar") }
                }
                Button(
                    onClick = onSelesai,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = DarkBackground),
                ) { Text(if (perluBayar) "Tagihan" else "OK, Selesai") }
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
fun PembayaranDialog(
    tv: TvData,
    transaksi: List<Transaksi>,
    onDismiss: () -> Unit,
    onBayar: (ids: List<String>, cash: Int, kembalian: Int, metode: String) -> Unit,
    onTagihan: (ids: List<String>) -> Unit,
) {
    val paketTxs = transaksi.filter { it.isPaketTx() && it.paket != "BATAL" }
    val fnbTxs = transaksi.filter { it.isFnbTx() && it.paket != "BATAL" }
    val adaFnbTerpisah = fnbTxs.isNotEmpty()
    val legacyMerged = paketTxs.any { it.kategori.isBlank() && it.pesanan.isNotEmpty() }
    val paketTotal = if (legacyMerged) {
        tv.totalBelanja.coerceAtLeast(0)
    } else if (paketTxs.isNotEmpty()) {
        paketTxs.sumOf { it.paketHarga.coerceAtLeast(0) }
    } else {
        tv.paketHarga.coerceAtLeast(0)
    }
    val fnbTotal = if (fnbTxs.isNotEmpty()) {
        fnbTxs.sumOf { it.total.coerceAtLeast(0) }
    } else {
        tv.totalFnb.coerceAtLeast(0)
    }
    val paketDue = if (legacyMerged) {
        if (tv.adaTagihan) tv.totalBelanja.coerceAtLeast(0) else 0
    } else if (paketTxs.isNotEmpty()) {
        paketTxs.filter { !it.sudahBayar }.sumOf { it.paketHarga.coerceAtLeast(0) }
    } else if (!tv.paketSudahBayar) {
        tv.paketHarga.coerceAtLeast(0)
    } else 0
    val fnbDue = if (fnbTxs.isNotEmpty()) {
        fnbTxs.filter { !it.sudahBayar }.sumOf { it.total.coerceAtLeast(0) }
    } else if (!tv.fnbSudahBayar) {
        tv.totalFnb.coerceAtLeast(0)
    } else 0
    var pilihPaket by remember(tv.id, tv.sesiMulai) { mutableStateOf(paketDue > 0) }
    var pilihFnb by remember(tv.id, tv.sesiMulai, adaFnbTerpisah) {
        mutableStateOf(fnbDue > 0 && adaFnbTerpisah)
    }
    var cashInput by remember(tv.id, tv.sesiMulai) { mutableStateOf("") }
    var metode by remember(tv.id, tv.sesiMulai) { mutableStateOf("Tunai") }

    val paketIds = paketTxs.filter { !it.sudahBayar }.map { it.id }
        .ifEmpty { listOfNotNull(tv.trxId.takeIf { it.isNotBlank() && paketDue > 0 }) }
    val fnbIds = fnbTxs.filter { !it.sudahBayar }.map { it.id }
    val totalDipilih =
        (if (pilihPaket) paketDue else 0) +
            (if (pilihFnb) fnbDue else 0)
    val idsDipilih = buildList {
        if (pilihPaket) addAll(paketIds)
        if (pilihFnb) addAll(fnbIds)
    }
    val cash = if (metode == "Tunai") {
        cashInput.filter { it.isDigit() }.toIntOrNull() ?: 0
    } else {
        totalDipilih
    }
    val kembali = if (metode == "Tunai") (cash - totalDipilih).coerceAtLeast(0) else 0
    val labelBayar = when {
        pilihPaket && pilihFnb -> "Lunas Semua"
        pilihFnb -> "Lunas F&B"
        else -> "Lunas Paket"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = { Text("PEMBAYARAN", color = NeonGreen) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(tv.nama, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Spacer(Modifier.height(10.dp))
                if (paketTotal > 0) {
                    PembayaranRow(
                        label = if (adaFnbTerpisah) "Paket" else "Paket + Pesanan",
                        nominal = if (paketDue > 0) paketDue else paketTotal,
                        lunas = paketDue <= 0,
                        checked = pilihPaket,
                        enabled = paketDue > 0,
                        onChecked = { pilihPaket = it },
                    )
                }
                if (fnbTotal > 0 && adaFnbTerpisah) {
                    PembayaranRow(
                        label = "Makanan / Minuman",
                        nominal = if (fnbDue > 0) fnbDue else fnbTotal,
                        lunas = fnbDue <= 0,
                        checked = pilihFnb,
                        enabled = fnbDue > 0,
                        onChecked = { pilihFnb = it },
                    )
                } else if (fnbTotal > 0 && !legacyMerged) {
                    Text(
                        "Pesanan ${fmtRp(fnbTotal)} ikut status paket.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextDim,
                    )
                }
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = DarkSurfaceV3)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("Harus dibayar", color = TextSecondary)
                    Text(fmtRp(totalDipilih), color = NeonGreen, fontWeight = FontWeight.Bold)
                }
                if (paketDue > 0 && !pilihPaket) {
                    Text("Paket tidak dipilih (tagihan).", color = NeonYellow, style = MaterialTheme.typography.bodySmall)
                }
                if (fnbDue > 0 && !pilihFnb && adaFnbTerpisah) {
                    Text("F&B tidak dipilih (tagihan).", color = NeonYellow, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(10.dp))
                Text("Metode bayar", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Tunai", "QRIS", "Transfer").forEach { m ->
                        FilterChip(
                            selected = metode == m,
                            onClick = { metode = m },
                            label = { Text(m, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
                if (metode == "Tunai") {
                    OutlinedTextField(
                        value = cashInput,
                        onValueChange = { cashInput = it.filter { c -> c.isDigit() }.take(12) },
                        label = { Text("Uang tunai") },
                        leadingIcon = { Text("Rp", color = TextSecondary) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = fieldColors(),
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(totalDipilih, 50000, 100000).distinct().forEach { nominal ->
                            AssistChip(
                                onClick = { cashInput = nominal.toString() },
                                label = { Text(if (nominal == totalDipilih) "Uang Pas" else fmtRp(nominal), fontSize = 11.sp) },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text("Kembalian", color = TextSecondary)
                        Text(fmtRp(kembali), color = if (cash >= totalDipilih) NeonYellow else TextDim, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text(
                        "Pembayaran $metode sebesar ${fmtRp(totalDipilih)}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NeonCyan,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cashDibayar = if (metode == "Tunai") cash else 0
                    val kembaliDibayar = if (metode == "Tunai") kembali else 0
                    onBayar(idsDipilih, cashDibayar, kembaliDibayar, metode)
                },
                enabled = idsDipilih.isNotEmpty() && totalDipilih > 0 &&
                    (metode != "Tunai" || cash >= totalDipilih),
                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = DarkBackground),
            ) { Text(labelBayar) }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(
                    onClick = { onTagihan(idsDipilih) },
                    enabled = idsDipilih.isNotEmpty(),
                ) { Text("Tagihan", color = NeonYellow) }
                TextButton(onClick = onDismiss) { Text("Batal", color = TextSecondary) }
            }
        },
    )
}

@Composable
private fun PembayaranRow(
    label: String,
    nominal: Int,
    lunas: Boolean,
    checked: Boolean,
    enabled: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = if (lunas) true else checked,
            onCheckedChange = { if (enabled) onChecked(it) },
            enabled = enabled,
            colors = CheckboxDefaults.colors(checkedColor = NeonGreen, uncheckedColor = NeonYellow),
        )
        Column(Modifier.weight(1f)) {
            Text(label, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
            Text(fmtRp(nominal), color = NeonGreen, style = MaterialTheme.typography.bodySmall)
        }
        Text(
            if (lunas) "LUNAS" else "TAGIHAN",
            color = if (lunas) NeonGreen else NeonRed,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelSmall,
        )
    }
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
    tvViewModel: com.billingps.aptv.TvViewModel,
    autoPowerSent: Boolean = false,
    onDismiss: () -> Unit,
    onSudahBayar: () -> Unit,
) {
    fun sendPowerOff() {
        if (!autoPowerSent && tv.certPem.isNotEmpty() && tv.keyPem.isNotEmpty()) {
            tvViewModel.sendPower(tv.ip, tv.certPem, tv.keyPem, false)
        }
    }

    Dialog(onDismissRequest = {
        sendPowerOff()
        onDismiss()
    }) {
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
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("Total", color = TextSecondary)
                    Text(fmtRp(tv.totalBelanja), color = NeonGreen, fontWeight = FontWeight.Bold)
                }
                Text(
                    if (tv.paketSudahBayar && tv.fnbSudahBayar) "LUNAS"
                    else "TAGIHAN: ${fmtRp(tv.totalBelanja)}",
                    color = if (tv.adaTagihan) NeonRed else NeonGreen,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        sendPowerOff()
                        onSudahBayar()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = DarkBackground),
                ) { Text(if (tv.adaTagihan) "Bayar Sekarang" else "OK") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        sendPowerOff()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonYellow),
                    border = BorderStroke(1.dp, NeonYellow),
                ) { Text(if (tv.adaTagihan) "Nanti / Tagihan" else "Tutup") }
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
