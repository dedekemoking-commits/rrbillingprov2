package com.billingps.aptv.ui.screens

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.billingps.aptv.TvViewModel
import com.billingps.aptv.models.MainViewModel
import com.billingps.aptv.models.PrayerConfig
import com.billingps.aptv.models.TvData
import com.billingps.aptv.tv.HdmiCommandCatalog
import com.billingps.aptv.tv.HdmiTestCommand
import com.billingps.aptv.tv.HdmiTestKind
import com.billingps.aptv.tv.HdmiWebSocketTester
import com.billingps.aptv.tv.TvHubProtocol
import com.billingps.aptv.ui.theme.*
import com.billingps.aptv.utils.PrayerTimes
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientScreen(
    viewModel: MainViewModel,
    tvViewModel: TvViewModel,
) {
    val state by viewModel.state.collectAsState()
    val connections by viewModel.tvConnections.collectAsState()
    val ctx = LocalContext.current

    var uploadTargetTv by remember { mutableStateOf<TvData?>(null) }
    var uploadKind by remember { mutableStateOf("promo") }
    var configTargetTv by remember { mutableStateOf<TvData?>(null) }
    var installTargetTv by remember { mutableStateOf<TvData?>(null) }
    var uploadStatus by remember { mutableStateOf("") }
    var showPrayerDialog by remember { mutableStateOf(false) }
    var prayerStatus by remember { mutableStateOf("") }

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
            uploadStatus = "Mengupload ${if (kind == "lockscreen") "media lockscreen" else "video promosi"}..."
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
        val mimeTypes = if (kind == "lockscreen") {
            arrayOf("image/jpeg", "image/png", "video/mp4")
        } else {
            arrayOf("video/*")
        }
        mediaPickerLauncher.launch(mimeTypes)
    }

    val prayerPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) {
            viewModel.detectPrayerLocation { _, message -> prayerStatus = message }
        } else {
            prayerStatus = "Izin lokasi ditolak"
        }
    }

    fun detectPrayerLocation() {
        val fine = ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (fine || coarse) {
            viewModel.detectPrayerLocation { _, message -> prayerStatus = message }
        } else {
            prayerPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            )
        }
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

    if (showPrayerDialog) {
        PrayerSettingsDialog(
            config = state.prayerConfig,
            locationStatus = prayerStatus.ifBlank { state.prayerLocationStatus },
            onDismiss = { showPrayerDialog = false },
            onSave = { config ->
                viewModel.savePrayerConfig(config)
                prayerStatus = ""
                showPrayerDialog = false
            },
            onDetectLocation = { detectPrayerLocation() },
            onTest = { message, durationSec -> viewModel.testPrayerPopup(message, durationSec) },
        )
    }

    configTargetTv?.let { tv ->
        TvControlDialog(
            tv = state.tvList.firstOrNull { it.id == tv.id } ?: tv,
            online = isOnline(tv),
            viewModel = viewModel,
            tvViewModel = tvViewModel,
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
            onPrayer = {
                configTargetTv = null
                showPrayerDialog = true
            },
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
private fun PrayerSettingsDialog(
    config: PrayerConfig,
    locationStatus: String,
    onDismiss: () -> Unit,
    onSave: (PrayerConfig) -> Unit,
    onDetectLocation: () -> Unit,
    onTest: (String, Int) -> Int,
) {
    var enabled by remember { mutableStateOf(config.enabled) }
    var message by remember { mutableStateOf(config.message) }
    var duration by remember { mutableStateOf(config.durationSec.toString()) }
    var latitude by remember {
        mutableStateOf(if (config.locationSet) String.format(Locale.US, "%.6f", config.latitude) else "")
    }
    var longitude by remember {
        mutableStateOf(if (config.locationSet) String.format(Locale.US, "%.6f", config.longitude) else "")
    }
    var testStatus by remember { mutableStateOf("") }

    LaunchedEffect(config.locationSet, config.latitude, config.longitude) {
        if (config.locationSet) {
            latitude = String.format(Locale.US, "%.6f", config.latitude)
            longitude = String.format(Locale.US, "%.6f", config.longitude)
        }
    }

    val latitudeValue = latitude.trim().toDoubleOrNull()
    val longitudeValue = longitude.trim().toDoubleOrNull()
    val validCoordinates = latitudeValue != null && longitudeValue != null &&
        latitudeValue in -90.0..90.0 && longitudeValue in -180.0..180.0
    val schedule = remember(latitudeValue, longitudeValue) {
        val lat = latitudeValue
        val lon = longitudeValue
        if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
            PrayerTimes.scheduleFor(Calendar.getInstance(), lat, lon)
        } else {
            null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Schedule, contentDescription = null, tint = NeonCyan)
                Spacer(Modifier.width(8.dp))
                Text("Popup Waktu Sholat")
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Aktifkan otomatis", color = TextPrimary)
                        Text(
                            "Popup dikirim ke semua TV saat waktu sholat tiba.",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextDim,
                        )
                    }
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it.take(300) },
                    label = { Text("Pesan popup") },
                    supportingText = { Text("{sholat} diganti nama waktu, mis. Subuh.") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it.filter(Char::isDigit).take(3) },
                    label = { Text("Durasi popup (detik)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Text("Lokasi", fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(
                    if (validCoordinates) {
                        String.format(Locale.US, "%.4f, %.4f", latitudeValue, longitudeValue)
                    } else {
                        "Belum diatur"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (validCoordinates) NeonGreen else TextDim,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = latitude,
                        onValueChange = { latitude = it },
                        label = { Text("Latitude") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    OutlinedTextField(
                        value = longitude,
                        onValueChange = { longitude = it },
                        label = { Text("Longitude") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
                OutlinedButton(
                    onClick = onDetectLocation,
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, NeonCyan),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                ) {
                    Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Deteksi Lokasi")
                }
                if (locationStatus.isNotBlank()) {
                    Text(locationStatus, style = MaterialTheme.typography.bodySmall, color = NeonCyan)
                }
                if (enabled && !validCoordinates) {
                    Text(
                        "Atur lokasi dulu agar jadwal otomatis berjalan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NeonRed,
                    )
                }
                if (schedule != null) {
                    Text("Jadwal hari ini", fontWeight = FontWeight.Bold, color = TextPrimary)
                    schedule.all().forEach { prayer ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(prayer.name, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            Text(prayer.label, style = MaterialTheme.typography.bodySmall, color = NeonGreen)
                        }
                    }
                } else {
                    Text(
                        "Jadwal muncul setelah koordinat lokasi diisi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextDim,
                    )
                }
                Text(
                    "Metode Kemenag: Subuh 20°, Isya 18°, Ashar Syafi'i, ihtiyati 2 menit.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextDim,
                )
                Text(
                    "Jika popup telat, izinkan Alarm & pengingat di setelan Android.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextDim,
                )
                OutlinedButton(
                    onClick = {
                        val count = onTest(
                            message,
                            duration.toIntOrNull() ?: config.durationSec,
                        )
                        testStatus = if (count > 0) "Terkirim ke $count TV" else "Tidak ada TV online"
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Tes Popup ke Semua TV")
                }
                if (testStatus.isNotBlank()) {
                    Text(testStatus, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val hasCoordinates = validCoordinates
                    onSave(
                        config.copy(
                            enabled = enabled,
                            message = message,
                            durationSec = duration.toIntOrNull() ?: config.durationSec,
                            latitude = if (hasCoordinates) latitudeValue!! else config.latitude,
                            longitude = if (hasCoordinates) longitudeValue!! else config.longitude,
                            locationSet = hasCoordinates,
                            locationLabel = if (hasCoordinates) {
                                String.format(Locale.US, "%.4f, %.4f", latitudeValue, longitudeValue)
                            } else {
                                ""
                            },
                        )
                    )
                },
            ) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Tutup") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TvControlDialog(
    tv: TvData,
    online: Boolean,
    viewModel: MainViewModel,
    tvViewModel: TvViewModel,
    onDismiss: () -> Unit,
    onSave: (Map<String, Any>) -> Unit,
    onUploadPromo: () -> Unit,
    onUploadLockscreen: () -> Unit,
    onTestPopup: () -> Unit,
    onLock: () -> Unit,
    onUnlock: () -> Unit,
    onStopTimer: () -> Unit,
    onPrayer: () -> Unit,
) {
    var rental by remember(tv.id) { mutableStateOf(tv.namaRental) }
    var popupMode by remember(tv.id) { mutableStateOf(TvHubProtocol.overlayMode(tv)) }
    var lastMinutes by remember(tv.id) { mutableStateOf(tv.popupLastMinutes.toString()) }
    var bebasInterval by remember(tv.id) { mutableStateOf(tv.bebasInterval.toString()) }
    var bebasDurasi by remember(tv.id) { mutableStateOf(tv.bebasDurasi.toString()) }
    var hdmiPort by remember(tv.id) { mutableStateOf(tv.hdmiPort) }
    var hdmiHint by remember(tv.id) { mutableStateOf(tv.hdmiInputHint) }
    var hdmiCommand by remember(tv.id) { mutableStateOf(tv.hdmiCommand) }
    var hdmiRemoteCommand by remember(tv.id) { mutableStateOf(tv.hdmiRemoteCommand) }
    var showHdmiTest by remember(tv.id) { mutableStateOf(false) }

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
                Text(
                    "Default client: ${hdmiCommand.ifBlank { "TIF otomatis" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Default remote: ${hdmiRemoteCommand.ifBlank { "belum diset" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                OutlinedButton(
                    onClick = { showHdmiTest = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Icon(Icons.Filled.Tv, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Verifikasi HDMI 1-4", style = MaterialTheme.typography.labelSmall)
                }

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
                Text(
                    "Lockscreen mendukung JPG, PNG, atau MP4.",
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
                TextButton(onClick = onPrayer) {
                    Icon(
                        Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Pengaturan Sholat", style = MaterialTheme.typography.labelSmall, color = NeonCyan)
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
                            "hdmiCommand" to hdmiCommand,
                            "hdmiRemoteCommand" to hdmiRemoteCommand,
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

    if (showHdmiTest) {
        HdmiTestDialog(
            tv = tv,
            viewModel = viewModel,
            tvViewModel = tvViewModel,
            initialPort = if (hdmiPort in 1..4) hdmiPort else 1,
            currentCommand = hdmiCommand,
            currentRemoteCommand = hdmiRemoteCommand,
            onDismiss = { showHdmiTest = false },
            onSave = { port, command, remoteCommand ->
                hdmiPort = port
                hdmiCommand = command
                hdmiRemoteCommand = remoteCommand
                onSave(
                    mapOf(
                        "hdmiPort" to port,
                        "hdmiInputHint" to hdmiHint.trim(),
                        "hdmiCommand" to command,
                        "hdmiRemoteCommand" to remoteCommand,
                    )
                )
                showHdmiTest = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HdmiTestDialog(
    tv: TvData,
    viewModel: MainViewModel,
    tvViewModel: TvViewModel,
    initialPort: Int,
    currentCommand: String,
    currentRemoteCommand: String,
    onDismiss: () -> Unit,
    onSave: (Int, String, String) -> Unit,
) {
    val appState by viewModel.state.collectAsState()
    val remoteState by tvViewModel.uiState.collectAsState()
    var port by remember { mutableStateOf(initialPort.coerceIn(1, 4)) }
    var kind by remember { mutableStateOf(HdmiTestKind.WEBSOCKET) }
    var result by remember { mutableStateOf("Pilih command lalu tekan Coba satu per satu.") }
    var tested by remember { mutableStateOf<HdmiTestCommand?>(null) }
    var testing by remember { mutableStateOf(false) }
    var pendingTestId by remember { mutableStateOf("") }
    var pendingCommand by remember { mutableStateOf<HdmiTestCommand?>(null) }
    val commands = remember(port, kind) { HdmiCommandCatalog.commandsFor(kind, port) }
    val clientStatus = appState.clientStatus[tv.id]

    LaunchedEffect(clientStatus?.hdmiTestId, clientStatus?.hdmiTestResult, pendingTestId) {
        val pending = pendingCommand ?: return@LaunchedEffect
        if (pendingTestId.isNotBlank() && clientStatus?.hdmiTestId == pendingTestId) {
            val response = clientStatus.hdmiTestResult
            if (response.isNotBlank()) {
                result = response
                tested = if (response.contains("FAIL", ignoreCase = true)) null else pending
                testing = false
                pendingTestId = ""
                pendingCommand = null
            }
        }
    }

    LaunchedEffect(pendingTestId) {
        if (pendingTestId.isBlank()) return@LaunchedEffect
        delay(9_000L)
        if (pendingTestId.isNotBlank()) {
            result = "Tidak ada hasil dari TV client dalam 9 detik. Pastikan client 4.3+ online."
            testing = false
            pendingTestId = ""
            pendingCommand = null
        }
    }

    fun test(command: HdmiTestCommand) {
        if (testing) return
        tested = null
        testing = true
        result = "Mengirim ${command.label}..."
        pendingTestId = ""
        pendingCommand = null
        when (command.kind) {
            HdmiTestKind.WEBSOCKET -> {
                if (tv.ip.isBlank()) {
                    testing = false
                    result = "IP TV kosong"
                    return
                }
                HdmiWebSocketTester.test(tv.ip, command.payload) { res ->
                    testing = false
                    result = buildString {
                        append(res.message)
                        if (res.response.isNotBlank()) append("\nRespon: ${res.response}")
                    }
                    if (res.connected) tested = command
                }
            }
            HdmiTestKind.REMOTE -> {
                if (!tvViewModel.hasPairing()) {
                    testing = false
                    result = "Remote belum dipairing. Pairing TV dulu di tab TV."
                    return
                }
                tvViewModel.sendKeys(tv.ip, command.keys, command.label) { ok, message ->
                    testing = false
                    result = message
                    if (ok) tested = command
                }
            }
            HdmiTestKind.CLIENT -> {
                val testId = viewModel.sendHdmiTest(tv.id, port, command.id)
                if (testId.isBlank()) {
                    testing = false
                    result = "Gagal mengirim test. Pastikan user login."
                } else {
                    pendingTestId = testId
                    pendingCommand = command
                    result = "Menunggu hasil dari TV client..."
                }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = DarkSurface,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.92f),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Verifikasi HDMI", style = MaterialTheme.typography.titleMedium, color = NeonGreen, fontWeight = FontWeight.Bold)
                        Text("${tv.nama} · ${tv.ip.ifBlank { "IP belum diisi" }}", style = MaterialTheme.typography.labelSmall, color = TextDim)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Tutup", tint = TextSecondary)
                    }
                }

                Text("Target HDMI", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..4).forEach { n ->
                        FilterChip(
                            selected = port == n,
                            onClick = {
                                port = n
                                tested = null
                                testing = false
                                pendingTestId = ""
                                pendingCommand = null
                                result = "Target HDMI $n dipilih."
                            },
                            label = { Text("HDMI $n", style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HdmiTestKind.entries.forEach { k ->
                        FilterChip(
                            selected = kind == k,
                            onClick = {
                                kind = k
                                tested = null
                                testing = false
                                pendingTestId = ""
                                pendingCommand = null
                                result = "Metode ${k.label} dipilih."
                            },
                            label = { Text(k.label, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }

                Text(
                    "Default client: ${currentCommand.ifBlank { "TIF otomatis" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Default remote: ${currentRemoteCommand.ifBlank { "belum diset" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceV2),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        result,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (result.startsWith("FAIL") || result.contains("gagal", true)) NeonRed else TextPrimary,
                        modifier = Modifier.padding(10.dp),
                    )
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(commands, key = { it.id }) { command ->
                        HdmiCommandRow(
                            command = command,
                            busy = testing,
                            onTest = { test(command) },
                        )
                    }
                }

                tested?.let { command ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceV2),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "Apakah TV sudah pindah ke HDMI $port?",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "Simpan ${command.label} sebagai default ${tv.nama}.",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextDim,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        val clientCommand = when (command.kind) {
                                            HdmiTestKind.WEBSOCKET -> "ws:${command.payload}"
                                            HdmiTestKind.CLIENT -> command.id
                                            HdmiTestKind.REMOTE -> currentCommand
                                        }
                                        val remoteCommand = when (command.kind) {
                                            HdmiTestKind.REMOTE -> command.id
                                            else -> currentRemoteCommand
                                        }
                                        onSave(port, clientCommand, remoteCommand)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = DarkBackground),
                                ) { Text("Ya, simpan default", style = MaterialTheme.typography.labelSmall) }
                                OutlinedButton(onClick = {
                                    tested = null
                                    result = "Belum berhasil. Coba command berikutnya."
                                }) { Text("Belum", style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                    }
                }

                if (kind == HdmiTestKind.REMOTE) {
                    Text(
                        "Remote key hanya untuk tombol manual. Auto HDMI memakai command client/WebSocket.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextDim,
                    )
                }
                if (kind == HdmiTestKind.CLIENT) {
                    Text(
                        "Client test butuh TV online dan aplikasi client 4.3+. Hasil muncul otomatis.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextDim,
                    )
                }
                if (remoteState.isBusy) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun HdmiCommandRow(
    command: HdmiTestCommand,
    busy: Boolean,
    onTest: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceV2),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(command.label, style = MaterialTheme.typography.bodySmall, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                if (command.description.isNotBlank()) {
                    Text(command.description, style = MaterialTheme.typography.labelSmall, color = TextDim)
                }
                if (command.payload.isNotBlank()) {
                    Text(
                        command.payload,
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonCyan,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (command.keys.isNotEmpty()) {
                    Text(
                        command.keys.joinToString(" > "),
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonCyan,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = onTest,
                enabled = !busy,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
            ) { Text("Coba", style = MaterialTheme.typography.labelSmall) }
        }
    }
}
