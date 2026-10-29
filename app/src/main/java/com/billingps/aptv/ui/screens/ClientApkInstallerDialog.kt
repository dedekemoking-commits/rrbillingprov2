package com.billingps.aptv.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.unit.sp
import com.billingps.aptv.utils.ClientApkManager
import com.billingps.aptv.utils.ApkServer
import com.billingps.aptv.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

enum class InstallStep {
    CHECK_ADB,
    INSTALL_OPTIONS,
    INSTALLING,
    COMPLETE,
}

@Composable
fun ClientApkInstallerDialog(
    tvIp: String,
    tvNama: String,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember { ClientApkManager(ctx) }

    var currentStep by remember { mutableStateOf(InstallStep.CHECK_ADB) }
    var statusMessage by remember { mutableStateOf("Memulai...") }
    var isBusy by remember { mutableStateOf(false) }
    var logLines by remember { mutableStateOf(listOf("Memulai instalasi client APK...")) }
    var apkExtractedPath by remember { mutableStateOf("") }
    var fallbackUrl by remember { mutableStateOf("") }
    var apkCopiedToDownloads by remember { mutableStateOf(false) }
    var pairingPort by remember { mutableStateOf("") }
    var pairingCode by remember { mutableStateOf("") }
    var connectPort by remember { mutableStateOf("") }
    var pairingStatus by remember { mutableStateOf("") }
    var pairingBusy by remember { mutableStateOf(false) }
    var pairedOnce by remember { mutableStateOf(false) }

    fun addLog(msg: String) {
        logLines = logLines + msg
    }

    fun startPairingInstall() {
        val pairPort = pairingPort.toIntOrNull()
        val code = pairingCode.filter(Char::isDigit)
        if (!pairedOnce) {
            if (pairPort == null || pairPort !in 1..65535) {
                pairingStatus = "Isi port pairing yang tampil di TV."
                return
            }
            if (code.length < 4) {
                pairingStatus = "Isi kode pairing yang tampil di TV."
                return
            }
        }
        if (pairingBusy || isBusy) return

        scope.launch {
            pairingBusy = true
            pairingStatus = ""
            currentStep = InstallStep.INSTALLING
            val apk = apkExtractedPath
                .takeIf { it.isNotBlank() && File(it).exists() }
                ?.let { File(it) }
                ?: manager.extractClientApk()
            if (apk == null) {
                pairingBusy = false
                currentStep = InstallStep.INSTALL_OPTIONS
                pairingStatus = "Gagal mengekstrak APK client"
                return@launch
            }

            if (!pairedOnce) {
                val targetPairPort = pairPort ?: run {
                    pairingBusy = false
                    currentStep = InstallStep.INSTALL_OPTIONS
                    pairingStatus = "Port pairing tidak valid."
                    return@launch
                }
                addLog("Pairing ADB ke $tvIp:$targetPairPort...")
                val pairResult = manager.pairDevice(tvIp, targetPairPort, code)
                addLog(pairResult.message)
                if (!pairResult.success) {
                    pairingBusy = false
                    currentStep = InstallStep.INSTALL_OPTIONS
                    pairingStatus = pairResult.message
                    return@launch
                }
                pairedOnce = true
                pairingStatus = "Pairing berhasil. Mencari port koneksi..."
            } else {
                pairingStatus = "Sudah pairing. Mencari port koneksi..."
                addLog("Melewati pairing; key ADB sudah terdaftar di TV.")
            }

            var port = connectPort.toIntOrNull()
            if (port == null || port !in 1..65535) {
                pairingStatus = "Mencari port koneksi ADB via mDNS..."
                addLog("Mencari service _adb-tls-connect._tcp...")
                port = manager.discoverConnectPort(tvIp)
            }
            if (port == null) {
                pairingBusy = false
                currentStep = InstallStep.INSTALL_OPTIONS
                pairingStatus = "Pairing berhasil, tetapi port koneksi tidak ditemukan. Isi port koneksi dari layar Wireless Debugging TV, lalu tekan lagi."
                return@launch
            }

            addLog("Install via ADB TLS port $port...")
            val installResult = manager.installAfterPairing(tvIp, port, apk.absolutePath)
            addLog(installResult.message)
            pairingBusy = false
            if (installResult.success) {
                currentStep = InstallStep.COMPLETE
                statusMessage = "Instalasi berhasil!"
            } else {
                currentStep = InstallStep.INSTALL_OPTIONS
                pairingStatus = installResult.message
            }
        }
    }

    LaunchedEffect(Unit) {
        val apkFile = manager.extractClientApk()
        if (apkFile == null) {
            addLog("Gagal mengekstrak APK client")
            statusMessage = "Gagal mengekstrak APK"
            currentStep = InstallStep.INSTALL_OPTIONS
            return@LaunchedEffect
        }
        apkExtractedPath = apkFile.absolutePath
        addLog("APK client berhasil diekstrak (${apkFile.length() / 1024} KB)")

        // Try direct ADB first (works for Android 10- or custom firmware)
        addLog("Mencoba ADB direct ke $tvIp:5555...")
        isBusy = true
        val directResult = manager.tryDirectInstall(tvIp, 5555, apkFile.absolutePath)
        isBusy = false

        if (directResult.success) {
            addLog(directResult.message)
            currentStep = InstallStep.COMPLETE
            statusMessage = "Instalasi berhasil!"
            return@LaunchedEffect
        }

        // ADB direct failed - show install options
        addLog("ADB direct: ${directResult.message}")
        addLog("Menampilkan opsi instalasi manual...")
        currentStep = InstallStep.INSTALL_OPTIONS

        // Start HTTP server as one of the options
        ApkServer.start(ctx, apkFile)
        val ip = ApkServer.getLocalIpAddress(ctx) ?: "localhost"
        fallbackUrl = "http://$ip:${ApkServer.port}/tv-client.apk"
        addLog("HTTP server siap di $fallbackUrl")
    }

    AlertDialog(
        onDismissRequest = { manager.cleanup(); ApkServer.stop(); onDismiss() },
        containerColor = DarkSurface,
        title = {
            Text(
                "INSTALL CLIENT APK",
                color = NeonGreen,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .width(500.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                when (currentStep) {
                    InstallStep.CHECK_ADB -> {
                        Text("Memeriksa koneksi ADB ke $tvNama ($tvIp)...", color = TextPrimary)
                        Spacer(Modifier.height(12.dp))
                        if (isBusy) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = NeonGreen,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Menghubungkan...", color = NeonYellow)
                            }
                        }
                    }

                    InstallStep.INSTALL_OPTIONS -> {
                        Text("PILIH METODE INSTALASI", color = NeonCyan, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("ADB tidak bisa terhubung otomatis. Gunakan salah satu metode di bawah:", color = TextSecondary, fontSize = 13.sp)
                        Spacer(Modifier.height(12.dp))

                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkSurfaceV2),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Wifi, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("METODE D: Android 11+ Wireless Debugging", color = NeonGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Spacer(Modifier.height(6.dp))
                                Text("Di TV buka Settings > System > Developer options > Wireless debugging > Pair device with pairing code.", color = TextSecondary, fontSize = 12.sp)
                                Text("Masukkan port pairing dan kode yang tampil di TV. Port koneksi boleh dikosongkan agar dicari otomatis lewat mDNS.", color = TextSecondary, fontSize = 12.sp)
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = pairingPort,
                                        onValueChange = { pairingPort = it.filter(Char::isDigit).take(5) },
                                        label = { Text("Port pairing", fontSize = 11.sp) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        colors = fieldColors(),
                                        modifier = Modifier.weight(1f),
                                    )
                                    OutlinedTextField(
                                        value = pairingCode,
                                        onValueChange = { pairingCode = it.filter(Char::isDigit).take(8) },
                                        label = { Text("Kode pairing", fontSize = 11.sp) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        colors = fieldColors(),
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = connectPort,
                                    onValueChange = { connectPort = it.filter(Char::isDigit).take(5) },
                                    label = { Text("Port koneksi (opsional)", fontSize = 11.sp) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = fieldColors(),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(6.dp))
                                TextButton(
                                    onClick = {
                                        if (pairingBusy) return@TextButton
                                        scope.launch {
                                            pairingBusy = true
                                            pairingStatus = "Mencari port pairing lewat mDNS..."
                                            val discovered = manager.discoverPairingPort(tvIp)
                                            pairingBusy = false
                                            if (discovered != null) {
                                                pairingPort = discovered.toString()
                                                pairingStatus = "Port pairing ditemukan: $discovered"
                                            } else {
                                                pairingStatus = "Port pairing tidak ditemukan. Isi manual dari layar TV."
                                            }
                                        }
                                    },
                                    enabled = !pairingBusy && !isBusy,
                                ) {
                                    Text("Cari port pairing otomatis", color = NeonCyan, fontSize = 11.sp)
                                }
                                Spacer(Modifier.height(6.dp))
                                Button(
                                    onClick = { startPairingInstall() },
                                    enabled = !pairingBusy && !isBusy,
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = DarkBackground),
                                ) {
                                    Text(
                                        when {
                                            pairingBusy -> "Memproses..."
                                            pairedOnce -> "Install via ADB TLS"
                                            else -> "Pairing & Install"
                                        },
                                        fontSize = 12.sp,
                                    )
                                }
                                Text(
                                    "Port koneksi berubah setiap TV reboot atau Wireless Debugging dimatikan. Pairing hanya perlu sekali.",
                                    color = TextDim,
                                    fontSize = 11.sp,
                                )
                                if (pairedOnce) {
                                    TextButton(
                                        onClick = {
                                            pairedOnce = false
                                            pairingStatus = "Isi kode pairing baru dari TV untuk pairing ulang."
                                        },
                                        enabled = !pairingBusy && !isBusy,
                                    ) {
                                        Text("Pairing ulang", color = NeonYellow, fontSize = 11.sp)
                                    }
                                }
                                if (pairingStatus.isNotBlank()) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        pairingStatus,
                                        color = when {
                                            pairingStatus.contains("berhasil", ignoreCase = true) -> NeonGreen
                                            pairingStatus.contains("gagal", ignoreCase = true) -> NeonRed
                                            else -> NeonYellow
                                        },
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // Method A: PC ADB
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkSurfaceV2),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Computer, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("METODE A: Via PC / Laptop", color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Spacer(Modifier.height(6.dp))
                                Text("1. Hubungkan HP ke PC via USB", color = TextSecondary, fontSize = 12.sp)
                                Text("2. Copy APK: adb push tv-client.apk /sdcard/", color = TextSecondary, fontSize = 12.sp)
                                Text("3. Pair TV: adb pair TV_IP:PAIR_PORT", color = TextSecondary, fontSize = 12.sp)
                                Text("4. Install: adb connect TV_IP:CONN_PORT && adb -s TV_IP:CONN_PORT install /sdcard/tv-client.apk", color = TextSecondary, fontSize = 12.sp)
                                Spacer(Modifier.height(6.dp))
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            val apkFile = manager.extractClientApk()
                                            if (apkFile != null) {
                                                val dest = File(ctx.getExternalFilesDir("Downloads") ?: ctx.cacheDir, "tv-client.apk")
                                                apkFile.copyTo(dest, overwrite = true)
                                                apkCopiedToDownloads = true
                                                addLog("APK siap di ${dest.absolutePath}")
                                            }
                                        }
                                    }
                                ) {
                                    Text(if (apkCopiedToDownloads) "APK Siap!" else "Copy APK ke Downloads", color = NeonGreen, fontSize = 12.sp)
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // Method B: Download from phone
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkSurfaceV2),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Wifi, contentDescription = null, tint = NeonOrange, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("METODE B: Download Langsung", color = NeonOrange, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Spacer(Modifier.height(6.dp))
                                Text("1. Di TV, buka browser (Chrome/Edge)", color = TextSecondary, fontSize = 12.sp)
                                Text("2. Buka URL di bawah ini", color = TextSecondary, fontSize = 12.sp)
                                Text("3. Download APK dan install", color = TextSecondary, fontSize = 12.sp)
                                Spacer(Modifier.height(8.dp))
                                Text("URL:", color = TextDim, fontSize = 11.sp)
                                Text(fallbackUrl, color = NeonGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Spacer(Modifier.height(4.dp))
                                Text("Pastikan TV dan HP dalam jaringan yang sama!", color = NeonOrange, fontSize = 11.sp)
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // Method C: USB
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkSurfaceV2),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Storage, contentDescription = null, tint = NeonYellow, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("METODE C: Flashdisk USB", color = NeonYellow, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Spacer(Modifier.height(6.dp))
                                Text("1. Copy file APK ke flashdisk via PC", color = TextSecondary, fontSize = 12.sp)
                                Text("2. Colok flashdisk ke TV", color = TextSecondary, fontSize = 12.sp)
                                Text("3. Buka File Manager dan install APK", color = TextSecondary, fontSize = 12.sp)
                            }
                        }

                        if (statusMessage.isNotEmpty() && !statusMessage.startsWith("Memulai")) {
                            Spacer(Modifier.height(8.dp))
                            Text(statusMessage, color = if (statusMessage.contains("Gagal")) NeonRed else NeonGreen, fontSize = 12.sp)
                        }
                    }

                    InstallStep.INSTALLING -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = NeonGreen,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(pairingStatus.ifBlank { "Memproses pairing dan install..." }, color = NeonYellow)
                        }
                    }

                    InstallStep.COMPLETE -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = NeonGreen,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text("INSTALASI BERHASIL!", color = NeonGreen, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("Client APK berhasil terinstall di $tvNama", color = TextPrimary)
                            Spacer(Modifier.height(4.dp))
                            Text("Aplikasi akan muncul di layar TV. Buka dan hubungkan dengan username billing.", color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }

                // Log output
                if (logLines.size > 1) {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = DarkSurfaceV3)
                    Spacer(Modifier.height(8.dp))
                    Text("LOG:", color = TextDim, fontSize = 11.sp)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceV2),
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Column(Modifier.padding(8.dp)) {
                            logLines.forEach { line ->
                                Text(
                                    line,
                                    color = when {
                                        line.startsWith("berhasil") -> NeonGreen
                                        line.startsWith("Gagal") -> NeonRed
                                        line.startsWith("Mencoba") -> NeonYellow
                                        line.startsWith("ADB") -> NeonCyan
                                        else -> TextSecondary
                                    },
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (currentStep == InstallStep.COMPLETE) {
                Button(
                    onClick = { manager.cleanup(); ApkServer.stop(); onDismiss() },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = DarkBackground),
                ) { Text("Selesai") }
            }
        },
        dismissButton = {
            TextButton(
                onClick = { manager.cleanup(); ApkServer.stop(); onDismiss() },
            ) { Text("Tutup", color = TextSecondary) }
        },
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = NeonGreen,
    unfocusedBorderColor = DarkSurfaceV3,
    cursorColor = NeonGreen,
    focusedLabelColor = NeonGreen,
    unfocusedContainerColor = DarkSurfaceV2,
    focusedContainerColor = DarkSurfaceV2,
)
