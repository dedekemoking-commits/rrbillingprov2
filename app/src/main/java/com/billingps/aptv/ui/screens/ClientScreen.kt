package com.billingps.aptv.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.billingps.aptv.models.MainViewModel
import com.billingps.aptv.models.TvData
import com.billingps.aptv.tv.TvHubProtocol
import com.billingps.aptv.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientScreen(
    viewModel: MainViewModel,
) {
    val state by viewModel.state.collectAsState()
    val connections by viewModel.tvConnections.collectAsState()
    val ctx = LocalContext.current

    var uploadTargetTv by remember { mutableStateOf<TvData?>(null) }
    var uploadKind by remember { mutableStateOf("promo") }
    var configTargetTv by remember { mutableStateOf<TvData?>(null) }
    var installTargetTv by remember { mutableStateOf<TvData?>(null) }
    var uploadStatus by remember { mutableStateOf("") }

    fun isOnline(tv: TvData): Boolean {
        val id = TvHubProtocol.normalizeId(tv.nama.ifBlank { tv.id })
        val hubOnline = connections.values.any {
            TvHubProtocol.normalizeId(it.mejaId) == id && it.online
        }
        return hubOnline || state.clientStatus[tv.id]?.online == true
    }

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val target = uploadTargetTv
        val kind = uploadKind
        if (uri != null && target != null) {
            uploadStatus = "Mengupload ${if (kind == "lockscreen") "video lockscreen" else "video promosi"}..."
            viewModel.uploadTvMedia(target.id, kind, uri) { ok, message ->
                uploadStatus = message
                if (ok) {
                    Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
        uploadTargetTv = null
    }

    fun pickMedia(tv: TvData, kind: String) {
        uploadTargetTv = tv
        uploadKind = kind
        mediaPickerLauncher.launch(arrayOf("video/*"))
    }

    val licenseActive = state.licenseStatus.status == "active"
    val trialActive = state.trialBatas > System.currentTimeMillis()
    if (!state.licenseStatus.clientEnabled || (!licenseActive && !trialActive)) {
        Box(
            modifier = Modifier.fillMaxSize().background(DarkBackground).padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.25f)),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Filled.Android, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(44.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (!state.licenseStatus.clientEnabled) "Fitur Client Tidak Tersedia" else "Lisensi Tidak Aktif",
                        style = MaterialTheme.typography.titleMedium,
                        color = NeonCyan,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (!state.licenseStatus.clientEnabled) {
                            "Paket Bulanan hanya mendukung pairing TV tanpa aplikasi client. " +
                                "Upgrade ke paket Lifetime untuk menggunakan Client APK."
                        } else {
                            "Aktifkan lisensi terlebih dahulu untuk menggunakan fitur Client TV."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(DarkSurface).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("CLIENT TV", style = MaterialTheme.typography.titleLarge, color = NeonGreen)
            Spacer(Modifier.width(10.dp))
            Icon(Icons.Filled.Android, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
            Spacer(Modifier.weight(1f))
            val onlineCount = state.tvList.count { isOnline(it) }
            Text("$onlineCount/${state.tvList.size} online", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }

        Text(
            "Meja ID di APK client harus sama dengan nama TV di kasir.",
            style = MaterialTheme.typography.labelSmall,
            color = TextDim,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )

        if (uploadStatus.isNotEmpty()) {
            Text(
                uploadStatus,
                style = MaterialTheme.typography.bodySmall,
                color = NeonCyan,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        if (state.tvList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Android, contentDescription = null, modifier = Modifier.size(64.dp), tint = TextDim)
                    Spacer(Modifier.height(12.dp))
                    Text("Belum ada TV", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
                    Text("Tambahkan TV terlebih dahulu di Dashboard", style = MaterialTheme.typography.bodySmall, color = TextDim)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.tvList, key = { it.id }) { tv ->
                    val online = isOnline(tv)
                    val hub = connections.values.firstOrNull {
                        TvHubProtocol.normalizeId(it.mejaId) == TvHubProtocol.normalizeId(tv.nama.ifBlank { tv.id })
                    }
                    val statusText = when {
                        hub?.screenOn == false -> "Online - layar mati"
                        hub?.locked == true -> "Online - terkunci"
                        online -> "Online"
                        else -> "Offline"
                    }

                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = BorderStroke(1.dp, if (online) NeonGreen.copy(alpha = 0.5f) else DarkSurfaceV3),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        tv.nama,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(tv.ip, style = MaterialTheme.typography.bodySmall, color = TextDim)
                                }
                                Box(
                                    modifier = Modifier.size(10.dp).clip(CircleShape)
                                        .background(if (online) NeonGreen else NeonRed)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    statusText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (online) NeonGreen else TextDim,
                                )
                            }

                            Spacer(Modifier.height(10.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                OutlinedButton(
                                    onClick = { configTargetTv = tv },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonGreen),
                                    border = BorderStroke(1.dp, NeonGreen),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                ) {
                                    Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Atur TV", style = MaterialTheme.typography.labelSmall)
                                }
                                if (online) {
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.tvStart(tv.id)
                                            Toast.makeText(ctx, "Timer dikirim ke ${tv.nama}", Toast.LENGTH_SHORT).show()
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                                        border = BorderStroke(1.dp, NeonCyan),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    ) {
                                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Timer", style = MaterialTheme.typography.labelSmall)
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = { installTargetTv = tv },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                                        border = BorderStroke(1.dp, NeonCyan),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    ) {
                                        Icon(Icons.Filled.Android, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Install Client", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }

                            if (tv.promoVideoPath.isNotBlank() || tv.lockscreenVideoPath.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    buildString {
                                        if (tv.promoVideoPath.isNotBlank()) append("Promo: ${tv.promoVideoPath.substringAfterLast("/")}")
                                        if (tv.lockscreenVideoPath.isNotBlank()) {
                                            if (isNotEmpty()) append("  |  ")
                                            append("Lock: ${tv.lockscreenVideoPath.substringAfterLast("/")}")
                                        }
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextDim,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    configTargetTv?.let { tv ->
        TvControlDialog(
            tv = state.tvList.firstOrNull { it.id == tv.id } ?: tv,
            online = isOnline(tv),
            onDismiss = { configTargetTv = null },
            onSave = { updates ->
                viewModel.updateTV(tv.id, updates)
                viewModel.tvApplyConfig(tv.id)
                Toast.makeText(ctx, "Pengaturan TV dikirim", Toast.LENGTH_SHORT).show()
            },
            onUploadPromo = { pickMedia(tv, "promo") },
            onUploadLockscreen = { pickMedia(tv, "lockscreen") },
            onTestPopup = {
                viewModel.tvMessage(tv.id, "TEST POPUP dari kasir", 5)
            },
            onLock = { viewModel.tvLock(tv.id) },
            onUnlock = { viewModel.tvUnlock(tv.id) },
            onStopTimer = { viewModel.tvStop(tv.id) },
        )
    }

    if (installTargetTv != null) {
        ClientApkInstallerDialog(
            tvIp = installTargetTv!!.ip,
            tvNama = installTargetTv!!.nama,
            onDismiss = { installTargetTv = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TvControlDialog(
    tv: TvData,
    online: Boolean,
    onDismiss: () -> Unit,
    onSave: (Map<String, Any>) -> Unit,
    onUploadPromo: () -> Unit,
    onUploadLockscreen: () -> Unit,
    onTestPopup: () -> Unit,
    onLock: () -> Unit,
    onUnlock: () -> Unit,
    onStopTimer: () -> Unit,
) {
    var rental by remember(tv.id) { mutableStateOf(tv.namaRental) }
    var popupMode by remember(tv.id) { mutableStateOf(TvHubProtocol.overlayMode(tv)) }
    var lastMinutes by remember(tv.id) { mutableStateOf(tv.popupLastMinutes.toString()) }
    var bebasInterval by remember(tv.id) { mutableStateOf(tv.bebasInterval.toString()) }
    var bebasDurasi by remember(tv.id) { mutableStateOf(tv.bebasDurasi.toString()) }
    var hdmiPort by remember(tv.id) { mutableStateOf(tv.hdmiPort) }
    var hdmiHint by remember(tv.id) { mutableStateOf(tv.hdmiInputHint) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Atur ${tv.nama}", color = NeonGreen) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(8.dp).clip(CircleShape)
                            .background(if (online) NeonGreen else NeonRed)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (online) "TV terhubung ke WebSocket kasir" else "TV belum terhubung ke WebSocket kasir",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (online) NeonGreen else TextDim,
                    )
                }

                OutlinedTextField(
                    value = rental,
                    onValueChange = { rental = it },
                    label = { Text("Nama rental di popup") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("Kapan popup tampil", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("always" to "Selalu", "last_minutes" to "Menit akhir", "hidden" to "Sembunyi").forEach { (mode, label) ->
                        FilterChip(
                            selected = popupMode == mode,
                            onClick = { popupMode = mode },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
                if (popupMode == "last_minutes") {
                    OutlinedTextField(
                        value = lastMinutes,
                        onValueChange = { lastMinutes = it.filter(Char::isDigit) },
                        label = { Text("Tampil pada N menit terakhir") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Text("Main Bebas", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = bebasInterval,
                        onValueChange = { bebasInterval = it.filter(Char::isDigit) },
                        label = { Text("Interval (menit)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = bebasDurasi,
                        onValueChange = { bebasDurasi = it.filter(Char::isDigit) },
                        label = { Text("Durasi popup (detik)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    "Popup bebas muncul tiap N menit, selama durasi yang diisi.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextDim,
                )

                Text("Auto HDMI", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(-1 to "Jangan", 0 to "Auto", 1 to "1", 2 to "2", 3 to "3", 4 to "4").forEach { (port, label) ->
                        FilterChip(
                            selected = hdmiPort == port,
                            onClick = { hdmiPort = port },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
                OutlinedTextField(
                    value = hdmiHint,
                    onValueChange = { hdmiHint = it },
                    label = { Text("Nama input HDMI (opsional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                HorizontalDivider(color = DarkSurfaceV3)
                Text("Media TV", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Text(
                    "Promo: ${tv.promoVideoPath.substringAfterLast("/").ifBlank { "belum ada" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextDim,
                )
                Text(
                    "Lockscreen: ${tv.lockscreenVideoPath.substringAfterLast("/").ifBlank { "belum ada" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextDim,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onUploadPromo,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    ) { Text("Upload Promo", style = MaterialTheme.typography.labelSmall) }
                    OutlinedButton(
                        onClick = onUploadLockscreen,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    ) { Text("Upload Lock", style = MaterialTheme.typography.labelSmall) }
                }

                HorizontalDivider(color = DarkSurfaceV3)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onTestPopup,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    ) { Text("Test Popup", style = MaterialTheme.typography.labelSmall) }
                    OutlinedButton(
                        onClick = onLock,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    ) { Text("Kunci", style = MaterialTheme.typography.labelSmall) }
                    OutlinedButton(
                        onClick = onUnlock,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    ) { Text("Buka Kunci", style = MaterialTheme.typography.labelSmall) }
                }
                TextButton(onClick = onStopTimer) {
                    Text("Stop Timer di TV", style = MaterialTheme.typography.labelSmall, color = NeonRed)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        mapOf(
                            "namaRental" to rental.trim(),
                            "popupMode" to popupMode,
                            "popupLastMinutes" to (lastMinutes.toIntOrNull() ?: 5).coerceIn(1, 120),
                            "bebasInterval" to (bebasInterval.toIntOrNull() ?: 60).coerceIn(1, 600),
                            "bebasDurasi" to (bebasDurasi.toIntOrNull() ?: 30).coerceIn(1, 120),
                            "hdmiPort" to hdmiPort,
                            "hdmiInputHint" to hdmiHint.trim(),
                        )
                    )
                    onDismiss()
                },
            ) { Text("Simpan & Kirim") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Tutup") }
        },
    )
}
