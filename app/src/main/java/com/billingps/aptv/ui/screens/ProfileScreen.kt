package com.billingps.aptv.ui.screens

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.billingps.aptv.models.*
import com.billingps.aptv.ui.theme.*
import com.billingps.aptv.ui.theme.ThemeOption
import kotlinx.coroutines.launch
import android.bluetooth.BluetoothDevice
import android.util.Log

@Composable
fun ProfileScreen(
    viewModel: MainViewModel,
    onLogout: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val ctx = LocalContext.current

    var newPass by remember { mutableStateOf("") }
    var tvPass by remember { mutableStateOf("") }
    var tvPassConfirm by remember { mutableStateOf("") }
    var tvPassMsg by remember { mutableStateOf("") }
    var tvPassMsgOk by remember { mutableStateOf(false) }

    var regUser by remember { mutableStateOf("") }
    var regPass by remember { mutableStateOf("") }
    var regRole by remember { mutableStateOf("kasir") }
    var regEmail by remember { mutableStateOf("") }
    var regMsg by remember { mutableStateOf("") }
    var regMsgOk by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()



    Column(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
            Row(
                modifier = Modifier.fillMaxWidth().background(DarkSurface).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("PROFIL & AKTIVASI", style = MaterialTheme.typography.titleLarge, color = NeonGreen)
            }

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Profile Card
            ProfileHeader(state.currentUser, state.currentRole, state.appVersionName)

            // Notifications Inbox
            if (state.currentRole == "admin") {
                var showNotif by remember { mutableStateOf(false) }
                val notifCount = state.unreadNotifCount
                val notifList = state.notifications
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = DarkSurface), border = BorderStroke(1.dp, if (notifCount > 0) NeonCyan.copy(alpha = 0.5f) else DarkSurfaceV3)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { showNotif = !showNotif; if (!showNotif) viewModel.markNotifRead() }) {
                            Icon(Icons.Filled.Notifications, contentDescription = null, tint = if (notifCount > 0) NeonCyan else TextSecondary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("NOTIFIKASI", style = MaterialTheme.typography.labelLarge, color = NeonCyan, modifier = Modifier.weight(1f))
                            if (notifCount > 0) {
                                Surface(shape = RoundedCornerShape(50), color = NeonRed) {
                                    Text("$notifCount", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.bodySmall, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(Modifier.width(4.dp))
                            Icon(if (showNotif) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null, tint = TextSecondary)
                        }
                        if (showNotif) {
                            Spacer(Modifier.height(8.dp))
                            if (notifList.isEmpty()) {
                                Text("Belum ada notifikasi", style = MaterialTheme.typography.bodySmall, color = TextDim)
                            } else {
                                notifList.take(20).forEach { n ->
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                        Icon(
                                            when (n.type) { "promo" -> Icons.Filled.LocalOffer; "license_confirmed" -> Icons.Filled.CheckCircle; else -> Icons.Filled.Info },
                                            contentDescription = null, tint = when (n.type) { "promo" -> NeonOrange; "license_confirmed" -> NeonGreen; else -> NeonCyan },
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(n.title, style = MaterialTheme.typography.bodySmall, color = if (n.read) TextSecondary else TextPrimary, fontWeight = if (n.read) FontWeight.Normal else FontWeight.Bold)
                                            Text(n.body, style = MaterialTheme.typography.bodySmall, color = TextDim)
                                        }
                                    }
                                    HorizontalDivider(color = DarkSurfaceV3.copy(alpha = 0.5f), thickness = 0.5.dp)
                                }
                            }
                        }
                    }
                }
            }

            // Change Password
            if (state.currentRole == "admin") {
                var newPassVisible by remember { mutableStateOf(false) }
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = DarkSurface), border = BorderStroke(1.dp, DarkSurfaceV3)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("GANTI PASSWORD", style = MaterialTheme.typography.labelLarge, color = NeonCyan)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = newPass, onValueChange = { newPass = it }, label = { Text("Password baru (min 6)") }, singleLine = true, visualTransformation = if (newPassVisible) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { IconButton(onClick = { newPassVisible = !newPassVisible }) { Icon(if (newPassVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null, tint = TextSecondary) } }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = fieldColors())
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            if (newPass.length < 6) { Toast.makeText(ctx, "Minimal 6 karakter", Toast.LENGTH_SHORT).show(); return@Button }
                            if (viewModel.gantiPassword(state.currentUser, newPass)) { Toast.makeText(ctx, "Password berhasil diubah", Toast.LENGTH_SHORT).show(); newPass = "" }
                            else { Toast.makeText(ctx, "Gagal mengubah password", Toast.LENGTH_SHORT).show() }
                        }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = DarkBackground)) { Text("Simpan Password") }
                    }
                }
            }



            // TV Password
            if (state.currentRole == "admin") {
                var tvPassVisible by remember { mutableStateOf(false) }
                var tvPassConfirmVisible by remember { mutableStateOf(false) }
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = DarkSurface), border = BorderStroke(1.dp, DarkSurfaceV3)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("PASSWORD TV", style = MaterialTheme.typography.labelLarge, color = NeonCyan)
                        Spacer(Modifier.height(4.dp))
                        Text("Digunakan untuk proteksi hapus TV di Dashboard. Kasir tidak bisa melihat ini.", style = MaterialTheme.typography.bodySmall, color = TextDim)
                        Spacer(Modifier.height(8.dp))

                        val hash = state.tvPasswordHash
                        val hasPassword = hash.isNotEmpty()

                        OutlinedTextField(value = tvPass, onValueChange = { tvPass = it; tvPassMsg = "" }, label = { Text(if (hasPassword) "Password baru (min 4)" else "Password (min 4)") }, singleLine = true, visualTransformation = if (tvPassVisible) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { IconButton(onClick = { tvPassVisible = !tvPassVisible }) { Icon(if (tvPassVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null, tint = TextSecondary) } }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = fieldColors())

                        if (hasPassword) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(value = tvPassConfirm, onValueChange = { tvPassConfirm = it; tvPassMsg = "" }, label = { Text("Konfirmasi password baru") }, singleLine = true, visualTransformation = if (tvPassConfirmVisible) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { IconButton(onClick = { tvPassConfirmVisible = !tvPassConfirmVisible }) { Icon(if (tvPassConfirmVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null, tint = TextSecondary) } }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = fieldColors())
                        }

                        if (tvPassMsg.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(tvPassMsg, style = MaterialTheme.typography.bodySmall, color = if (tvPassMsgOk) NeonGreen else NeonRed)
                        }

                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                if (tvPass.length < 4) { tvPassMsg = "Minimal 4 karakter"; tvPassMsgOk = false; return@Button }
                                if (hasPassword && tvPass != tvPassConfirm) { tvPassMsg = "Konfirmasi tidak cocok"; tvPassMsgOk = false; return@Button }
                                viewModel.setTvPassword(tvPass)
                                tvPass = ""; tvPassConfirm = ""
                                tvPassMsg = "Password TV tersimpan"; tvPassMsgOk = true
                            }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = DarkBackground)) { Text(if (hasPassword) "Ubah Password" else "Simpan Password") }

                            if (hasPassword) {
                                OutlinedButton(onClick = {
                                    viewModel.setTvPassword("")
                                    tvPass = ""; tvPassConfirm = ""
                                    tvPassMsg = "Password TV dihapus"; tvPassMsgOk = true
                                }, shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonRed), border = BorderStroke(1.dp, NeonRed)) { Text("Hapus") }
                            }
                        }
                    }
                }
            }

            // User Management (Admin)
            if (state.currentRole == "admin") {
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = DarkSurface), border = BorderStroke(1.dp, DarkSurfaceV3)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("MANAJEMEN KASIR", style = MaterialTheme.typography.labelLarge, color = NeonCyan)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = regUser, onValueChange = { regUser = it; regMsg = "" }, label = { Text("Username baru") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = fieldColors())
                        var regPassVisible by remember { mutableStateOf(false) }
                        OutlinedTextField(value = regPass, onValueChange = { regPass = it }, label = { Text("Password (min 6, huruf besar & angka)") }, singleLine = true, visualTransformation = if (regPassVisible) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { IconButton(onClick = { regPassVisible = !regPassVisible }) { Icon(if (regPassVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null, tint = TextSecondary) } }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = fieldColors())
                        Spacer(Modifier.height(4.dp))
                        Text("Role: kasir", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        OutlinedTextField(value = regEmail, onValueChange = { regEmail = it }, label = { Text("Email (opsional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = fieldColors())
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            if (regUser.isBlank() || regPass.length < 6) { regMsg = "Username & password min 6"; regMsgOk = false; return@Button }
                            if (!regPass.any { it.isUpperCase() }) { regMsg = "Password harus huruf besar"; regMsgOk = false; return@Button }
                            if (!regPass.any { it.isDigit() }) { regMsg = "Password harus angka"; regMsgOk = false; return@Button }
                            if (viewModel.cekUsername(regUser)) { regMsg = "Username sudah ada"; regMsgOk = false; return@Button }
                            scope.launch {
                                val result = viewModel.daftarUser(regUser, regPass, regEmail, regRole)
                                regMsg = result.message
                                regMsgOk = result.success
                                if (result.success) {
                                    regUser = ""; regPass = ""; regEmail = ""
                                }
                            }
                        }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = DarkBackground)) { Text("Daftarkan User") }
                        if (regMsg.isNotEmpty()) { Spacer(Modifier.height(4.dp)); Text(regMsg, style = MaterialTheme.typography.bodySmall, color = if (regMsgOk) NeonGreen else NeonRed) }
                    }
                }

                }

            // Theme Picker
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = DarkSurface), border = BorderStroke(1.dp, DarkSurfaceV3)) {
                Column(Modifier.padding(16.dp)) {
                    Text("TEMA TAMPILAN", style = MaterialTheme.typography.labelLarge, color = NeonCyan)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ThemeOption.entries.forEach { opt ->
                            val selected = state.themeOption == opt
                            val accent = when (opt) {
                                ThemeOption.GAMING_DARK -> Color(0xFF39FF14)
                                ThemeOption.CYBER_BLUE -> Color(0xFF00E5FF)
                                ThemeOption.NEON_PURPLE -> Color(0xFFBB00FF)
                                ThemeOption.CLASSIC_DARK -> Color(0xFF4CAF50)
                                ThemeOption.LIGHT_MODE -> Color(0xFF388E3C)
                            }
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.setTheme(opt) },
                                label = { Text(opt.displayName, style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = {
                                    Box(modifier = Modifier.size(10.dp).background(accent, RoundedCornerShape(2.dp)))
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accent.copy(alpha = 0.2f),
                                    selectedLabelColor = accent,
                                ),
                            )
                        }
                    }
                }
            }

            // Printer Struk
            if (state.currentRole == "admin") {
                var printerDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
                var scanMsg by remember { mutableStateOf("") }
                var connectingAddress by remember { mutableStateOf("") }
                var isScanning by remember { mutableStateOf(false) }
                var pairTargetAddress by remember { mutableStateOf("") }
                var pairTargetName by remember { mutableStateOf("") }

                val bluetoothPerms = if (android.os.Build.VERSION.SDK_INT >= 31)
                    arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT, android.Manifest.permission.BLUETOOTH_SCAN)
                else emptyArray()

                val doBluetoothScan = fun(context: Context, onResult: (List<BluetoothDevice>) -> Unit) {
                    com.billingps.aptv.utils.ThermalPrinter.cancelDiscovery(context)
                    val bonded = com.billingps.aptv.utils.ThermalPrinter.getAllBondedDevices()
                    val allDevices = bonded.toMutableList()
                    val seen = bonded.map { it.address }.toMutableSet()
                    com.billingps.aptv.utils.ThermalPrinter.discoverDevices(
                        context,
                        onFound = { device ->
                            if (device.address !in seen && !allDevices.any { it.address == device.address }) {
                                allDevices.add(device)
                                seen.add(device.address)
                                onResult(allDevices.toList())
                            }
                        },
                        onFinished = { onResult(allDevices.toList()) }
                    )
                    // Also show bonded immediately
                    onResult(allDevices.toList())
                }
                val permLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { granted ->
                    if (granted.values.all { it } || bluetoothPerms.isEmpty()) {
                        doBluetoothScan(ctx) { list -> printerDevices = list; scanMsg = if (list.isEmpty()) "Tidak ada perangkat Bluetooth" else ""; isScanning = false }
                    } else {
                        scanMsg = "Izin Bluetooth ditolak"
                        isScanning = false
                    }
                }

                val bluetoothSettingsLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
                ) { _ ->
                    if (pairTargetAddress.isNotEmpty()) {
                        val device = com.billingps.aptv.utils.ThermalPrinter.getDevice(pairTargetAddress)
                        if (device?.bondState == android.bluetooth.BluetoothDevice.BOND_BONDED) {
                            val name = pairTargetName
                            scanMsg = "Pairing berhasil, menghubungkan ke $name..."
                            viewModel.savePrinter(pairTargetAddress, name)
                            connectingAddress = pairTargetAddress
                            viewModel.connectPrinter(pairTargetAddress)
                            pairTargetAddress = ""
                            pairTargetName = ""
                        } else {
                            scanMsg = "Printer belum dipairing. Buka Pengaturan → Bluetooth untuk pairing manual"
                        }
                    }
                }

                // Auto-connect when pairing completes in Bluetooth Settings
                DisposableEffect(pairTargetAddress) {
                    if (pairTargetAddress.isEmpty()) return@DisposableEffect onDispose {}

                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(context: Context, intent: Intent) {
                            val device = if (android.os.Build.VERSION.SDK_INT >= 33) {
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                            }
                            val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)
                            val addr = pairTargetAddress
                            if (device != null && device.address == addr && bondState == BluetoothDevice.BOND_BONDED) {
                                val name = pairTargetName
                                scanMsg = "Pairing berhasil, menghubungkan ke $name..."
                                viewModel.savePrinter(addr, name)
                                connectingAddress = addr
                                viewModel.connectPrinter(addr)
                                pairTargetAddress = ""
                                pairTargetName = ""
                            }
                        }
                    }

                    val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                    if (android.os.Build.VERSION.SDK_INT >= 33) {
                        ctx.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
                    } else {
                        @Suppress("DEPRECATION")
                        ctx.registerReceiver(receiver, filter)
                    }

                    onDispose {
                        ctx.unregisterReceiver(receiver)
                    }
                }

                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = DarkSurface), border = BorderStroke(1.dp, DarkSurfaceV3)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("PRINTER STRUK", style = MaterialTheme.typography.labelLarge, color = NeonCyan)
                        Spacer(Modifier.height(4.dp))
                        Text("Hubungkan ke printer thermal Bluetooth.", style = MaterialTheme.typography.bodySmall, color = TextDim)
                        Spacer(Modifier.height(8.dp))

                        // Update connectingAddress when printerConnected changes
                        LaunchedEffect(state.printerConnected, state.statusMessage) {
                            if (connectingAddress.isNotEmpty() && (state.printerConnected || state.statusMessage.startsWith("Gagal") || state.statusMessage.startsWith("Error"))) {
                                connectingAddress = ""
                            }
                        }

                        // Status printer + error message
                        if (state.printerConnected) {
                            Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = NeonGreen.copy(alpha = 0.1f))) {
                                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.BluetoothConnected, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(state.printerName.ifEmpty { "Printer" }, style = MaterialTheme.typography.bodySmall, color = NeonGreen, fontWeight = FontWeight.Bold)
                                        Text(state.printerAddress, style = MaterialTheme.typography.bodySmall, color = TextDim)
                                    }
                                }
                            }
                        } else if (state.printerAddress.isNotEmpty() && connectingAddress.isEmpty()) {
                            Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = NeonRed.copy(alpha = 0.1f))) {
                                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.BluetoothDisabled, contentDescription = null, tint = NeonRed, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(state.printerName.ifEmpty { "Printer" }, style = MaterialTheme.typography.bodySmall, color = NeonRed)
                                        Text("Terputus", style = MaterialTheme.typography.bodySmall, color = TextDim)
                                    }
                                }
                            }
                        }
                        if (state.statusMessage.startsWith("Terhubung") || state.statusMessage.startsWith("Gagal") || state.statusMessage.startsWith("Error") || state.statusMessage.contains(":")) {
                            Spacer(Modifier.height(4.dp))
                            val isError = !state.statusMessage.startsWith("Terhubung")
                            Text(state.statusMessage, style = MaterialTheme.typography.bodySmall, color = if (isError) NeonRed else NeonGreen)
                        }
                        Spacer(Modifier.height(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    connectingAddress = ""
                                    if (bluetoothPerms.isNotEmpty()) {
                                        isScanning = true
                                        scanMsg = "Meminta izin Bluetooth..."
                                        permLauncher.launch(bluetoothPerms)
                                    } else {
                                        isScanning = true
                                        scanMsg = "Mencari printer..."
                                        doBluetoothScan(ctx) { list -> printerDevices = list; scanMsg = if (list.isEmpty()) "Tidak ada perangkat Bluetooth" else ""; isScanning = false }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                                border = BorderStroke(1.dp, NeonCyan),
                            ) { if (isScanning) CircularProgressIndicator(modifier = Modifier.size(14.dp), color = NeonCyan, strokeWidth = 2.dp) else Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Cari") }

                            if (state.printerConnected) {
                                OutlinedButton(
                                    onClick = { viewModel.disconnectPrinter() },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonRed),
                                    border = BorderStroke(1.dp, NeonRed),
                                ) { Icon(Icons.Filled.BluetoothDisabled, contentDescription = null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Putus") }

                                OutlinedButton(
                                    onClick = { viewModel.printTestPage() },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonGreen),
                                    border = BorderStroke(1.dp, NeonGreen),
                                ) { Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Test") }
                            }
                        }

                        if (scanMsg.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text(scanMsg, style = MaterialTheme.typography.bodySmall, color = TextDim)
                        }

                        if (printerDevices.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text("Pilih printer:", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            Spacer(Modifier.height(4.dp))
                            printerDevices.forEach { device ->
                                val isSelected = state.printerAddress == device.address
                                val isConnecting = connectingAddress == device.address
                                val connFailed = isSelected && !state.printerConnected && connectingAddress.isEmpty()
                                val isBonded = device.bondState == android.bluetooth.BluetoothDevice.BOND_BONDED
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).background(
                                        if (connFailed) NeonRed.copy(0.08f) else if (isSelected) NeonCyan.copy(0.08f) else Color.Transparent,
                                        RoundedCornerShape(8.dp),
                                    ),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = {
                                            viewModel.savePrinter(device.address, device.name)
                                            if (isBonded) {
                                                connectingAddress = device.address
                                                scanMsg = "Menghubungkan ke ${device.name}..."
                                                viewModel.connectPrinter(device.address)
                                            } else {
                                                pairTargetAddress = device.address
                                                pairTargetName = device.name ?: "Printer"
                                                scanMsg = "Buka Pengaturan Bluetooth untuk pairing ${device.name}..."
                                                bluetoothSettingsLauncher.launch(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                                            }
                                        },
                                        colors = RadioButtonDefaults.colors(selectedColor = if (connFailed) NeonRed else NeonCyan),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Icon(
                                        Icons.Filled.Bluetooth,
                                        contentDescription = null,
                                        tint = if (connFailed) NeonRed else if (isSelected) NeonCyan else TextDim,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(device.name, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                                        Text(device.address, style = MaterialTheme.typography.bodySmall, color = TextDim)
                                    }
                                    if (!isBonded) {
                                        Text("Baru", style = MaterialTheme.typography.bodySmall, color = NeonYellow, fontWeight = FontWeight.Bold)
                                    } else if (isConnecting) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = NeonCyan, strokeWidth = 2.dp)
                                    } else if (isSelected && state.printerConnected) {
                                        Text("Siap", style = MaterialTheme.typography.bodySmall, color = NeonGreen, fontWeight = FontWeight.Bold)
                                    } else if (connFailed) {
                                        Text("Gagal", style = MaterialTheme.typography.bodySmall, color = NeonRed)
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        var showRentalDialog by remember { mutableStateOf(false) }
                        val currentUser = state.users[state.currentUser]
                        var rentalNama by remember { mutableStateOf(currentUser?.namaRental ?: "") }
                        var rentalAlamat by remember { mutableStateOf(currentUser?.alamatRental ?: "") }
                        var rentalWa by remember { mutableStateOf(currentUser?.whatsappRental ?: "") }
                        OutlinedButton(
                            onClick = { rentalNama = currentUser?.namaRental ?: ""; rentalAlamat = currentUser?.alamatRental ?: ""; rentalWa = currentUser?.whatsappRental ?: ""; showRentalDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonOrange),
                            border = BorderStroke(1.dp, NeonOrange),
                        ) { Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Data Rental") }
                        if (showRentalDialog) {
                            AlertDialog(
                                onDismissRequest = { showRentalDialog = false },
                                containerColor = DarkSurface,
                                titleContentColor = NeonOrange,
                                textContentColor = TextPrimary,
                                title = { Text("DATA RENTAL") },
                                text = {
                                    Column {
                                        Text("Data ini akan muncul di struk print.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                                        Spacer(Modifier.height(12.dp))
                                        OutlinedTextField(
                                            value = rentalNama,
                                            onValueChange = { rentalNama = it },
                                            label = { Text("Nama Rental") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = NeonOrange, unfocusedBorderColor = DarkSurfaceV3,
                                                cursorColor = NeonOrange, unfocusedContainerColor = DarkSurfaceV2, focusedContainerColor = DarkSurfaceV2,
                                                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                                            ),
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        OutlinedTextField(
                                            value = rentalAlamat,
                                            onValueChange = { rentalAlamat = it },
                                            label = { Text("Alamat") },
                                            maxLines = 3,
                                            modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = NeonOrange, unfocusedBorderColor = DarkSurfaceV3,
                                                cursorColor = NeonOrange, unfocusedContainerColor = DarkSurfaceV2, focusedContainerColor = DarkSurfaceV2,
                                                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                                            ),
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        OutlinedTextField(
                                            value = rentalWa,
                                            onValueChange = { rentalWa = it },
                                            label = { Text("No. WhatsApp") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = NeonOrange, unfocusedBorderColor = DarkSurfaceV3,
                                                cursorColor = NeonOrange, unfocusedContainerColor = DarkSurfaceV2, focusedContainerColor = DarkSurfaceV2,
                                                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                                            ),
                                        )
                                    }
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            viewModel.updateDataRental(rentalNama, rentalAlamat, rentalWa)
                                            showRentalDialog = false
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = NeonOrange, contentColor = DarkBackground),
                                    ) { Text("SIMPAN") }
                                },
                                dismissButton = {
                                    OutlinedButton(
                                        onClick = { showRentalDialog = false },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonRed),
                                    ) { Text("BATAL") }
                                },
                            )
                        }

                    }
                }
            }

            // Check Update
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = DarkSurface), border = BorderStroke(1.dp, DarkSurfaceV3)) {
                Column(Modifier.padding(16.dp)) {
                    Text("PERIKSA PEMBARUAN", style = MaterialTheme.typography.labelLarge, color = NeonCyan)
                    Spacer(Modifier.height(8.dp))
                    val updateInfo = state.updateInfo
                    val updateChecked = state.updateChecked
                    if (updateInfo != null) {
                        Text("Versi ${updateInfo.versionName} tersedia!", style = MaterialTheme.typography.bodyMedium, color = NeonGreen)
                        Spacer(Modifier.height(8.dp))
                        if (state.downloadProgress >= 0) {
                            LinearProgressIndicator(progress = { state.downloadProgress / 100f }, modifier = Modifier.fillMaxWidth().height(12.dp), color = NeonGreen, trackColor = DarkSurfaceV3)
                            Spacer(Modifier.height(4.dp))
                            Text("Download: ${state.downloadProgress}%", style = MaterialTheme.typography.bodySmall, color = NeonGreen)
                        } else {
                            Button(onClick = { viewModel.downloadAndInstall(ctx) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = DarkBackground)) {
                                Text("Download & Install v${updateInfo.versionName}")
                            }
                        }
                    } else if (updateChecked) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("✓", color = NeonGreen, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Spacer(Modifier.width(6.dp))
                            Text("Versi terbaru (v${state.appVersionName})", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Button(
                        onClick = { viewModel.checkForUpdate(); Toast.makeText(ctx, "Memeriksa...", Toast.LENGTH_SHORT).show() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = DarkBackground),
                    ) { Text("Cek Update") }
                }
            }

            // Logout section
            var showLogoutDialog by remember { mutableStateOf(false) }
            var showGoogleLogoutDialog by remember { mutableStateOf(false) }

            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = DarkSurface.copy(alpha = 0.5f)), border = BorderStroke(1.dp, NeonRed.copy(alpha = 0.3f))) {
                Column(Modifier.padding(16.dp)) {
                    if (state.loginMethod == "google") {
                        OutlinedButton(
                            onClick = { showGoogleLogoutDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF4285F4)),
                            border = BorderStroke(1.dp, Color(0xFF4285F4)),
                            contentPadding = PaddingValues(vertical = 12.dp),
                        ) {
                            Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Logout dari Google", fontWeight = FontWeight.Medium)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Button(
                        onClick = { showLogoutDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonRed, contentColor = androidx.compose.ui.graphics.Color.White),
                        contentPadding = PaddingValues(vertical = 12.dp),
                    ) {
                        Icon(Icons.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Keluar Aplikasi", fontWeight = FontWeight.Medium)
                    }
                }
            }

            if (showLogoutDialog) {
                AlertDialog(
                    onDismissRequest = { showLogoutDialog = false },
                    containerColor = DarkSurface,
                    title = { Text("KONFIRMASI KELUAR", fontWeight = FontWeight.Bold, color = NeonRed) },
                    text = { Text("Apakah Anda yakin ingin keluar? Data yang belum tersimpan akan hilang.", color = TextSecondary) },
                    confirmButton = {
                        Button(
                            onClick = { showLogoutDialog = false; onLogout() },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonRed, contentColor = DarkBackground),
                            shape = RoundedCornerShape(10.dp),
                        ) { Text("Keluar") }
                    },
                    dismissButton = {
                        OutlinedButton(
                            onClick = { showLogoutDialog = false },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                            border = BorderStroke(1.dp, TextSecondary.copy(alpha = 0.5f)),
                        ) { Text("Batal") }
                    },
                )
            }

            if (showGoogleLogoutDialog) {
                AlertDialog(
                    onDismissRequest = { showGoogleLogoutDialog = false },
                    containerColor = DarkSurface,
                    title = { Text("LOGOUT DARI GOOGLE", fontWeight = FontWeight.Bold, color = Color(0xFF4285F4)) },
                    text = { Text("Anda akan logout dari akun Google. Perlu login ulang dengan password atau Google Sign-In nantinya.", color = TextSecondary) },
                    confirmButton = {
                        Button(
                            onClick = { showGoogleLogoutDialog = false; viewModel.logout(true) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4), contentColor = androidx.compose.ui.graphics.Color.White),
                            shape = RoundedCornerShape(10.dp),
                        ) { Text("Logout dari Google") }
                    },
                    dismissButton = {
                        OutlinedButton(
                            onClick = { showGoogleLogoutDialog = false },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                            border = BorderStroke(1.dp, TextSecondary.copy(alpha = 0.5f)),
                        ) { Text("Batal") }
                    },
                )
            }

            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun ProfileHeader(username: String, role: String, appVersionName: String) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkSurfaceV3),
    ) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = androidx.compose.ui.res.painterResource(com.billingps.aptv.R.drawable.logo_transparant_profile),
                contentDescription = "Logo",
                modifier = Modifier.size(96.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text("RR BILLING PRO", style = MaterialTheme.typography.titleLarge, color = NeonGreen)
            Text("Sistem Billing Rental PlayStation & TV", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Spacer(Modifier.height(12.dp))
            listOf(
                "Versi" to appVersionName,
                "Developer" to "RR Developer",
                "Kontak" to "082180208414",
                "User" to "$username [$role]",
            ).forEach { (label, value) ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Text(value, style = MaterialTheme.typography.bodySmall, color = TextPrimary, fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("© 2026 RR BILLING PRO", style = MaterialTheme.typography.bodySmall, color = TextDim)
        }
    }
}

private val paketHargaMap = mapOf(
    "1 Bulan" to 99000,
    "3 Bulan" to 299000,
    "1 Tahun" to 999000,
    "LIFETIME" to 2000000,
)

@Composable private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = NeonGreen, unfocusedBorderColor = DarkSurfaceV3,
    cursorColor = NeonGreen, focusedLabelColor = NeonGreen,
    unfocusedContainerColor = DarkSurfaceV2, focusedContainerColor = DarkSurfaceV2,
)
