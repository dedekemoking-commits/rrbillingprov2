package com.billingps.licensegen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.launch

private val DarkBackground = Color(0xFF121212)
private val DarkSurface = Color(0xFF1E1E1E)
private val DarkSurfaceV2 = Color(0xFF2D2D2D)
private val DarkSurfaceV3 = Color(0xFF3D3D3D)
private val NeonGreen = Color(0xFF39FF14)
private val NeonCyan = Color(0xFF00E5FF)
private val NeonRed = Color(0xFFFF1744)
private val NeonYellow = Color(0xFFFFEA00)
private val NeonOrange = Color(0xFFFF6600)
private val TextPrimary = Color(0xFFE0E0E0)
private val TextSecondary = Color(0xFF9E9E9E)
private val TextDim = Color(0xFF616161)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = DarkBackground, surface = DarkSurface,
                    primary = NeonGreen, secondary = NeonCyan, error = NeonRed,
                ),
                content = { App() }
            )
        }
    }
}

@Composable
fun App(vm: LicenseGenViewModel = viewModel()) {
    if (!vm.isLoggedIn) {
        LoginScreen(vm)
    } else if (!vm.firestoreReady) {
        Box(Modifier.fillMaxSize().background(DarkBackground), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = NeonGreen)
        }
    } else {
        MainScreen(vm)
    }
}

@Composable
fun LoginScreen(vm: LicenseGenViewModel) {
    var password by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize().background(DarkBackground).padding(24.dp), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, NeonGreen.copy(alpha = 0.3f)),
        ) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(com.billingps.licensegen.R.drawable.logo_app),
                    contentDescription = "Logo",
                    modifier = Modifier.size(150.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text("RR LICENSE GENERATOR", style = MaterialTheme.typography.titleLarge, color = NeonGreen, letterSpacing = 2.sp)
                Spacer(Modifier.height(4.dp))
                Text("Super Admin Only", style = MaterialTheme.typography.bodySmall, color = TextDim)
                Spacer(Modifier.height(24.dp))

                Text("rrbilling", style = MaterialTheme.typography.bodyMedium, color = TextSecondary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(value = password, onValueChange = { password = it; msg = "" },
                    label = { Text("Password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonGreen, unfocusedBorderColor = DarkSurfaceV3,
                        cursorColor = NeonGreen,
                        unfocusedContainerColor = DarkSurfaceV2, focusedContainerColor = DarkSurfaceV2,
                    ))

                if (msg.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(msg, style = MaterialTheme.typography.bodySmall, color = NeonRed)
                }

                Spacer(Modifier.height(16.dp))

                Button(onClick = {
                    if (password.isBlank()) { msg = "Masukkan password"; return@Button }
                    vm.login(password) { ok, err -> msg = if (ok) "" else err }
                }, modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = DarkBackground)) {
                    Text("LOGIN", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
fun MainScreen(vm: LicenseGenViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    val ctx = LocalContext.current

    Column(Modifier.fillMaxSize().background(DarkBackground)) {
        Surface(color = DarkSurface, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("RR LICENSE GEN", style = MaterialTheme.typography.titleMedium, color = NeonGreen, modifier = Modifier.weight(1f))
                Text("${vm.userList.size} user", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { vm.signOut() }) {
                    Icon(Icons.Filled.ExitToApp, contentDescription = "Logout", tint = NeonRed)
                }
            }
        }

        val pendingCount = vm.invoiceList.count { it.status == "WAITING_CONFIRMATION" }

        val promoAktif = vm.promo.promoAktif

        TabRow(selectedTabIndex = tab, containerColor = DarkSurface, contentColor = NeonGreen) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("User") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Generate") })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Riwayat") })
            Tab(selected = tab == 3, onClick = { tab = 3 }, text = {
                if (pendingCount > 0) {
                    BadgedBox(badge = { Badge { Text("$pendingCount") } }) { Text("Invoice") }
                } else { Text("Invoice") }
            })
            Tab(selected = tab == 4, onClick = { tab = 4 }, text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Promo")
                    if (promoAktif) {
                        Spacer(Modifier.width(4.dp))
                        Text("🔥", fontSize = 12.sp)
                    }
                }
            })
            Tab(selected = tab == 5, onClick = { tab = 5 }, text = { Text("Notif") })
            Tab(selected = tab == 6, onClick = { tab = 6 }, text = { Text("Settings") })
        }

        when (tab) {
            0 -> UserListTab(vm)
            1 -> GenerateTab(vm, ctx)
            2 -> HistoryTab(vm, ctx)
            3 -> InvoiceTab(vm, ctx)
            4 -> PromoTab(vm, ctx)
            5 -> NotifikasiTab(vm, ctx)
            6 -> SettingsTab(vm, ctx)
        }
    }
}

@Composable
fun UserListTab(vm: LicenseGenViewModel) {
    var search by remember { mutableStateOf("") }
    val filtered = remember(vm.userList, search) {
        if (search.isBlank()) vm.userList
        else vm.userList.filter { it.username.contains(search, ignoreCase = true) || it.email.contains(search, ignoreCase = true) }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("DAFTAR USER TERDAFTAR", style = MaterialTheme.typography.titleMedium, color = NeonCyan)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = search, onValueChange = { search = it },
            placeholder = { Text("Cari username atau email...") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NeonGreen, unfocusedBorderColor = DarkSurfaceV3,
                cursorColor = NeonGreen, unfocusedContainerColor = DarkSurfaceV2, focusedContainerColor = DarkSurfaceV2,
                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
            ),
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp)) },
        )
        Spacer(Modifier.height(8.dp))
        Text("${filtered.size} user", style = MaterialTheme.typography.bodySmall, color = TextDim)

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Tidak ada user", style = MaterialTheme.typography.bodyMedium, color = TextDim)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxSize()) {
                items(filtered) { user ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = BorderStroke(1.dp, if (user.email.isNotBlank()) NeonGreen.copy(alpha = 0.2f) else DarkSurfaceV3),
                    ) {
                        Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(user.username, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                                Text(if (user.email.isNotBlank()) user.email else "(tanpa email)", style = MaterialTheme.typography.bodySmall, color = if (user.email.isNotBlank()) TextSecondary else TextDim)
                                Text("${user.role} • ${user.dibuat.take(10)}", style = MaterialTheme.typography.bodySmall, color = TextDim)
                            }
                            if (user.email.isNotBlank()) {
                                Icon(Icons.Filled.Email, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenerateTab(vm: LicenseGenViewModel, ctx: Context) {
    var selectedUser by remember { mutableStateOf<FirestoreUser?>(null) }
    var showUserPicker by remember { mutableStateOf(false) }
    var paket by remember { mutableStateOf("BULANAN") }
    var generatedCode by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }

    if (showUserPicker) {
        Box(Modifier.fillMaxSize().background(Color(0xCC000000)).clickable(enabled = false) { }, contentAlignment = Alignment.Center) {
            Card(
                modifier = Modifier.fillMaxWidth(0.9f).heightIn(max = 500.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.3f)),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("PILIH USER", style = MaterialTheme.typography.titleMedium, color = NeonCyan, modifier = Modifier.weight(1f))
                        IconButton(onClick = { showUserPicker = false }) { Icon(Icons.Filled.Close, contentDescription = "Tutup", tint = NeonRed) }
                    }
                    Spacer(Modifier.height(8.dp))
                    val pickerList = vm.userList.filter { it.email.isNotBlank() }
                    if (pickerList.isEmpty()) {
                        Text("Tidak ada user dengan email", style = MaterialTheme.typography.bodyMedium, color = TextDim, modifier = Modifier.padding(16.dp))
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(pickerList) { user ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth().clickable { selectedUser = user; showUserPicker = false },
                                    color = if (selectedUser?.username == user.username) DarkSurfaceV2 else DarkSurface,
                                ) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text(user.username, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                                        Text(user.email, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                    }
                                }
                                HorizontalDivider(color = DarkSurfaceV3, thickness = 0.5.dp)
                            }
                        }
                    }
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("GENERATE KODE LISENSI", style = MaterialTheme.typography.titleMedium, color = NeonCyan)
        Spacer(Modifier.height(16.dp))

        // Selected user
        Button(
            onClick = { showUserPicker = true },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceV2, contentColor = TextPrimary),
            border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f)),
        ) {
            if (selectedUser == null) {
                Text("Pilih User Pelanggan", fontWeight = FontWeight.Bold)
            } else {
                Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("${selectedUser!!.username} (${selectedUser!!.email})", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(12.dp))

        Text("Paket:", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("BULANAN", "3BULAN", "TAHUNAN", "LIFETIME").forEach { p ->
                FilterChip(
                    selected = paket == p,
                    onClick = { paket = p; generatedCode = "" },
                    label = { Text(p, style = MaterialTheme.typography.labelSmall, color = if (paket == p) DarkBackground else TextPrimary) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NeonGreen, selectedLabelColor = DarkBackground),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(onClick = {
            if (selectedUser == null) { msg = "Pilih user terlebih dahulu"; return@Button }
            msg = "Generating..."
            generatedCode = ""
            vm.generateLicense(paket, selectedUser!!.username, selectedUser!!.email) { code ->
                generatedCode = code; msg = ""
            }
        }, modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = DarkBackground)) {
            if (vm.isBusy) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = DarkBackground, strokeWidth = 2.dp)
            } else {
                Text("GENERATE KODE", fontWeight = FontWeight.Bold)
            }
        }

        if (msg.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(msg, style = MaterialTheme.typography.bodySmall, color = NeonYellow, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }

        if (generatedCode.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth()
                    .border(BorderStroke(2.dp, NeonGreen), RoundedCornerShape(12.dp))
                    .clickable {
                        val clip = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clip.setPrimaryClip(ClipData.newPlainText("Kode Aktivasi", generatedCode))
                        Toast.makeText(ctx, "Kode tersalin: $generatedCode", Toast.LENGTH_SHORT).show()
                    },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceV2),
            ) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("KODE AKTIVASI", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    Text(generatedCode, style = MaterialTheme.typography.headlineSmall, color = NeonGreen, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp), tint = NeonGreen)
                        Spacer(Modifier.width(4.dp))
                        Text("Tap untuk copy", style = MaterialTheme.typography.bodySmall, color = NeonGreen)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Untuk: ${selectedUser?.email ?: "-"}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
fun HistoryTab(vm: LicenseGenViewModel, ctx: Context) {
    var items by remember { mutableStateOf<List<LicenseRecord>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        items = vm.getHistory()
        loading = false
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("RIWAYAT GENERATE", style = MaterialTheme.typography.titleMedium, color = NeonCyan)
        Spacer(Modifier.height(12.dp))

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NeonGreen)
            }
        } else if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Belum ada riwayat", style = MaterialTheme.typography.bodyMedium, color = TextDim)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items) { rec ->
                    val time = if (rec.generatedAt > 0) {
                        val sdf = java.text.SimpleDateFormat("dd/MM/yy HH:mm", java.util.Locale.US)
                        sdf.format(java.util.Date(rec.generatedAt))
                    } else "-"
                    val status = if (rec.activatedAt > 0) "✅ Terpakai" else "⏳ Belum"
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = BorderStroke(1.dp, DarkSurfaceV3),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(rec.kode, style = MaterialTheme.typography.titleMedium, color = NeonGreen, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text(status, style = MaterialTheme.typography.bodySmall, color = if (rec.activatedAt > 0) NeonGreen else TextSecondary)
                            }
                            Spacer(Modifier.height(6.dp))
                            HorizontalDivider(color = DarkSurfaceV3, thickness = 0.5.dp)
                            Spacer(Modifier.height(6.dp))
                            DetailRow("User", rec.username)
                            DetailRow("Email", rec.email.ifEmpty { "(tanpa email)" })
                            DetailRow("Paket", rec.paket)
                            DetailRow("Berlaku", rec.expiry)
                            DetailRow("Generate", time)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InvoiceTab(vm: LicenseGenViewModel, ctx: Context) {
    val pending = vm.invoiceList.filter { it.status == "WAITING_CONFIRMATION" }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("INVOICE MENUNGGU KONFIRMASI", style = MaterialTheme.typography.titleMedium, color = NeonCyan)
        Spacer(Modifier.height(8.dp))
        Text("${pending.size} menunggu", style = MaterialTheme.typography.bodySmall, color = TextDim)
        Spacer(Modifier.height(12.dp))

        if (pending.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Tidak ada invoice menunggu", style = MaterialTheme.typography.bodyMedium, color = TextDim)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(pending) { inv ->
                    var showConfirmDialog by remember { mutableStateOf(false) }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = BorderStroke(1.dp, NeonYellow.copy(alpha = 0.3f)),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(inv.id, style = MaterialTheme.typography.titleSmall, color = NeonYellow, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text(inv.paket, style = MaterialTheme.typography.bodySmall, color = NeonGreen)
                            }
                            Spacer(Modifier.height(6.dp))
                            DetailRow("User", inv.username)
                            DetailRow("Email", inv.email)
                            DetailRow("Total", "Rp${String.format("%,d", inv.harga)}")
                            val dateStr = if (inv.dibayar > 0) {
                                val sdf = java.text.SimpleDateFormat("dd/MM/yy HH:mm", java.util.Locale.US)
                                sdf.format(java.util.Date(inv.dibayar))
                            } else if (inv.dibuat > 0) {
                                val sdf = java.text.SimpleDateFormat("dd/MM/yy HH:mm", java.util.Locale.US)
                                "Dibuat: ${sdf.format(java.util.Date(inv.dibuat))}"
                            } else "-"
                            DetailRow("Tanggal", dateStr)

                            if (inv.buktiBase64.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text("Bukti Transfer:", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                val bitmap = remember(inv.buktiBase64) {
                                    try {
                                        val bytes = Base64.decode(inv.buktiBase64, Base64.DEFAULT)
                                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                    } catch (e: Exception) { null }
                                }
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = "Bukti Transfer",
                                        modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(top = 4.dp),
                                    )
                                } else {
                                    Text("(gagal decode gambar)", style = MaterialTheme.typography.bodySmall, color = NeonRed)
                                }
                            }

                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { showConfirmDialog = true },
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = DarkBackground),
                            ) {
                                if (vm.isBusy) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = DarkBackground, strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("KONFIRMASI & GENERATE LISENSI", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    if (showConfirmDialog) {
                        AlertDialog(
                            onDismissRequest = { showConfirmDialog = false },
                            containerColor = DarkSurface,
                            titleContentColor = NeonGreen,
                            textContentColor = TextPrimary,
                            title = { Text("Konfirmasi Invoice") },
                            text = {
                                Column {
                                    Text("Konfirmasi pembayaran dari:")
                                    Text("${inv.username} (${inv.email})", fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(4.dp))
                                    Text("Paket: ${inv.paket}")
                                    Text("Total: Rp${String.format("%,d", inv.harga)}")
                                    Spacer(Modifier.height(8.dp))
                                    Text("Lisensi akan digenerate otomatis dan dikaitkan ke invoice.", color = TextSecondary, fontSize = 13.sp)
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    showConfirmDialog = false
                                    vm.konfirmasiInvoice(inv) { ok, result ->
                                        val msg = if (ok) "Berhasil! Kode: $result" else "Gagal: $result"
                                        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                                    }
                                }) { Text("KONFIRMASI", color = NeonGreen) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showConfirmDialog = false }) { Text("BATAL", color = NeonRed) }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PromoTab(vm: LicenseGenViewModel, ctx: Context) {
    var promoAktif by remember { mutableStateOf(vm.promo.promoAktif) }
    var cek1 by remember { mutableStateOf((vm.promo.diskonPerPaket["1 Bulan"] ?: 0) > 0) }
    var cek3 by remember { mutableStateOf((vm.promo.diskonPerPaket["3 Bulan"] ?: 0) > 0) }
    var cek12 by remember { mutableStateOf((vm.promo.diskonPerPaket["1 Tahun"] ?: 0) > 0) }
    var cekLt by remember { mutableStateOf((vm.promo.diskonPerPaket["LIFETIME"] ?: 0) > 0) }
    var diskon1 by remember { mutableStateOf(vm.promo.diskonPerPaket["1 Bulan"] ?: 20) }
    var diskon3 by remember { mutableStateOf(vm.promo.diskonPerPaket["3 Bulan"] ?: 20) }
    var diskon12 by remember { mutableStateOf(vm.promo.diskonPerPaket["1 Tahun"] ?: 20) }
    var diskonLt by remember { mutableStateOf(vm.promo.diskonPerPaket["LIFETIME"] ?: 20) }
    var addTv1 by remember { mutableStateOf(vm.promo.addTvOverride["1 Bulan"] ?: 5) }
    var addTv3 by remember { mutableStateOf(vm.promo.addTvOverride["3 Bulan"] ?: 10) }
    var addTv12 by remember { mutableStateOf(vm.promo.addTvOverride["1 Tahun"] ?: 15) }
    var addTvLt by remember { mutableStateOf(vm.promo.addTvOverride["LIFETIME"] ?: 0) }
    var msg by remember { mutableStateOf("") }
    var msgOk by remember { mutableStateOf(false) }

    LaunchedEffect(vm.promo) {
        promoAktif = vm.promo.promoAktif
        cek1 = (vm.promo.diskonPerPaket["1 Bulan"] ?: 0) > 0
        cek3 = (vm.promo.diskonPerPaket["3 Bulan"] ?: 0) > 0
        cek12 = (vm.promo.diskonPerPaket["1 Tahun"] ?: 0) > 0
        cekLt = (vm.promo.diskonPerPaket["LIFETIME"] ?: 0) > 0
        if (cek1) diskon1 = vm.promo.diskonPerPaket["1 Bulan"] ?: 20
        if (cek3) diskon3 = vm.promo.diskonPerPaket["3 Bulan"] ?: 20
        if (cek12) diskon12 = vm.promo.diskonPerPaket["1 Tahun"] ?: 20
        if (cekLt) diskonLt = vm.promo.diskonPerPaket["LIFETIME"] ?: 20
        addTv1 = vm.promo.addTvOverride["1 Bulan"] ?: 5
        addTv3 = vm.promo.addTvOverride["3 Bulan"] ?: 10
        addTv12 = vm.promo.addTvOverride["1 Tahun"] ?: 15
        addTvLt = vm.promo.addTvOverride["LIFETIME"] ?: 0
    }

    fun save() {
        val diskonMap = mutableMapOf<String, Int>()
        if (cek1) diskonMap["1 Bulan"] = diskon1.coerceIn(0, 100)
        if (cek3) diskonMap["3 Bulan"] = diskon3.coerceIn(0, 100)
        if (cek12) diskonMap["1 Tahun"] = diskon12.coerceIn(0, 100)
        if (cekLt) diskonMap["LIFETIME"] = diskonLt.coerceIn(0, 100)
        val overrides = mapOf(
            "1 Bulan" to addTv1.coerceAtLeast(0),
            "3 Bulan" to addTv3.coerceAtLeast(0),
            "1 Tahun" to addTv12.coerceAtLeast(0),
            "LIFETIME" to addTvLt.coerceAtLeast(0),
        )
        vm.setPromo(promoAktif, diskonMap, overrides) { ok, message ->
            msg = message; msgOk = ok
            if (ok) Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("PENGATURAN PROMO", style = MaterialTheme.typography.titleMedium, color = NeonCyan)
        Spacer(Modifier.height(4.dp))
        Text("Pilih paket, atur diskon & add TV", style = MaterialTheme.typography.bodySmall, color = TextDim)
        Spacer(Modifier.height(16.dp))

        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = DarkSurface), border = BorderStroke(1.dp, DarkSurfaceV3)) {
            Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Aktifkan Promo", style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
                Switch(checked = promoAktif, onCheckedChange = { promoAktif = it }, colors = SwitchDefaults.colors(checkedTrackColor = NeonGreen, checkedThumbColor = DarkBackground))
            }
        }

        Spacer(Modifier.height(12.dp))

        PromoPerPaketCard("1 Bulan", "5 TV", NeonGreen, cek1, { cek1 = it }, diskon1, { diskon1 = it }, addTv1, { addTv1 = it.coerceAtLeast(0) })
        Spacer(Modifier.height(8.dp))
        PromoPerPaketCard("3 Bulan", "10 TV", NeonCyan, cek3, { cek3 = it }, diskon3, { diskon3 = it }, addTv3, { addTv3 = it.coerceAtLeast(0) })
        Spacer(Modifier.height(8.dp))
        PromoPerPaketCard("1 Tahun", "15 TV", NeonYellow, cek12, { cek12 = it }, diskon12, { diskon12 = it }, addTv12, { addTv12 = it.coerceAtLeast(0) })
        Spacer(Modifier.height(8.dp))
        PromoPerPaketCard("LIFETIME", "unlimited", NeonOrange, cekLt, { cekLt = it }, diskonLt, { diskonLt = it }, addTvLt, { addTvLt = it.coerceAtLeast(0) })

        Spacer(Modifier.height(16.dp))

        if (msg.isNotEmpty()) {
            Text(msg, style = MaterialTheme.typography.bodySmall, color = if (msgOk) NeonGreen else NeonRed)
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = { save() },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = DarkBackground),
        ) { Text("SIMPAN PROMO", fontWeight = FontWeight.Bold, fontSize = 16.sp) }

        Spacer(Modifier.height(8.dp))

        if (vm.promo.promoAktif) {
            val updatedAt = if (vm.promo.updatedAt > 0) {
                val sdf = java.text.SimpleDateFormat("dd/MM/yy HH:mm", java.util.Locale.US)
                "Terakhir update: ${sdf.format(java.util.Date(vm.promo.updatedAt))}"
            } else ""
            val paketList = vm.promo.diskonPerPaket.filter { it.value > 0 }
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = DarkSurfaceV2), border = BorderStroke(1.dp, NeonGreen.copy(alpha = 0.3f))) {
                Column(Modifier.padding(12.dp)) {
                    Text("🔥 PROMO SEDANG AKTIF", style = MaterialTheme.typography.labelLarge, color = NeonGreen, fontWeight = FontWeight.Bold)
                    paketList.forEach { (pkg, diskon) ->
                        val addTv = vm.promo.addTvOverride[pkg] ?: 0
                        Text("$pkg: diskon $diskon%, add TV $addTv", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                    }
                    if (updatedAt.isNotEmpty()) Text(updatedAt, style = MaterialTheme.typography.bodySmall, color = TextDim)
                }
            }
        }

        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun PromoPerPaketCard(
    nama: String, addTvNormal: String, accent: Color,
    cek: Boolean, onCek: (Boolean) -> Unit,
    diskon: Int, onDiskon: (Int) -> Unit,
    addTv: Int, onAddTv: (Int) -> Unit,
) {
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = DarkSurface), border = BorderStroke(1.dp, if (cek) accent else DarkSurfaceV3)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = cek, onCheckedChange = onCek, colors = CheckboxDefaults.colors(checkedColor = accent, checkmarkColor = DarkBackground))
                Text(nama, style = MaterialTheme.typography.bodyMedium, color = accent, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }
            if (cek) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Diskon: ${diskon}%", style = MaterialTheme.typography.bodySmall, color = TextPrimary, modifier = Modifier.weight(1f))
                    Text("$diskon%", style = MaterialTheme.typography.titleMedium, color = NeonGreen, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = diskon.toFloat(),
                    onValueChange = { onDiskon(it.toInt()) },
                    valueRange = 0f..100f, steps = 19,
                    colors = SliderDefaults.colors(thumbColor = NeonGreen, activeTrackColor = NeonGreen, inactiveTrackColor = DarkSurfaceV3),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("0%", style = MaterialTheme.typography.bodySmall, color = TextDim)
                    Text("50%", style = MaterialTheme.typography.bodySmall, color = TextDim)
                    Text("100%", style = MaterialTheme.typography.bodySmall, color = TextDim)
                }
                Spacer(Modifier.height(6.dp))
                TvOverrideField("ADD TV (normal: $addTvNormal)", addTv, onAddTv)
            }
        }
    }
}

@Composable
private fun TvOverrideField(label: String, value: Int, onValueChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(
            value = if (value == 0) "0" else value.toString(),
            onValueChange = { onValueChange(it.filter { c -> c.isDigit() }.take(3).toIntOrNull() ?: 0) },
            modifier = Modifier.width(80.dp),
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NeonGreen, unfocusedBorderColor = DarkSurfaceV3,
                cursorColor = NeonGreen, unfocusedContainerColor = DarkSurfaceV2, focusedContainerColor = DarkSurfaceV2,
                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
            ),
            textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center),
        )
    }
}

@Composable
fun NotifikasiTab(vm: LicenseGenViewModel, ctx: Context) {
    var targetSemua by remember { mutableStateOf(true) }
    var targetUsername by remember { mutableStateOf("") }
    var judul by remember { mutableStateOf("") }
    var pesan by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var msgOk by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("KIRIM NOTIFIKASI", style = MaterialTheme.typography.titleMedium, color = NeonCyan)
        Spacer(Modifier.height(4.dp))
        Text("Kirim push notification ke user", style = MaterialTheme.typography.bodySmall, color = TextDim)
        Spacer(Modifier.height(16.dp))

        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = DarkSurface), border = BorderStroke(1.dp, DarkSurfaceV3)) {
            Column(Modifier.padding(16.dp)) {
                Text("Target", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = targetSemua, onClick = { targetSemua = true; targetUsername = "" }, colors = RadioButtonDefaults.colors(selectedColor = NeonGreen))
                    Text("Semua User", style = MaterialTheme.typography.bodySmall, color = TextPrimary, modifier = Modifier.weight(1f))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = !targetSemua, onClick = { targetSemua = false }, colors = RadioButtonDefaults.colors(selectedColor = NeonCyan))
                    Text("User Tertentu", style = MaterialTheme.typography.bodySmall, color = TextPrimary, modifier = Modifier.weight(1f))
                }
                if (!targetSemua) {
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(value = targetUsername, onValueChange = { targetUsername = it },
                        placeholder = { Text("Username") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, unfocusedBorderColor = DarkSurfaceV3,
                            cursorColor = NeonCyan, unfocusedContainerColor = DarkSurfaceV2, focusedContainerColor = DarkSurfaceV2,
                            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = DarkSurface), border = BorderStroke(1.dp, DarkSurfaceV3)) {
            Column(Modifier.padding(16.dp)) {
                OutlinedTextField(value = judul, onValueChange = { judul = it },
                    label = { Text("Judul Notifikasi") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonGreen, unfocusedBorderColor = DarkSurfaceV3,
                        cursorColor = NeonGreen, unfocusedContainerColor = DarkSurfaceV2, focusedContainerColor = DarkSurfaceV2,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = pesan, onValueChange = { pesan = it },
                    label = { Text("Pesan Notifikasi") }, minLines = 3, maxLines = 5,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonGreen, unfocusedBorderColor = DarkSurfaceV3,
                        cursorColor = NeonGreen, unfocusedContainerColor = DarkSurfaceV2, focusedContainerColor = DarkSurfaceV2,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (msg.isNotEmpty()) {
            Text(msg, style = MaterialTheme.typography.bodySmall, color = if (msgOk) NeonGreen else NeonRed)
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = {
                if (judul.isBlank() || pesan.isBlank()) { msg = "Isi judul dan pesan"; msgOk = false; return@Button }
                isSending = true; msg = ""
                if (targetSemua) {
                    vm.sendFcmToAllUsers(judul, pesan) { sent ->
                        isSending = false
                        msg = "Notifikasi terkirim ke $sent user"; msgOk = true
                        judul = ""; pesan = ""
                    }
                } else {
                    if (targetUsername.isBlank()) { msg = "Masukkan username target"; msgOk = false; isSending = false; return@Button }
                    vm.sendFcmToUser(targetUsername, judul, pesan)
                    isSending = false
                    msg = "Notifikasi terkirim ke $targetUsername"; msgOk = true
                    judul = ""; pesan = ""; targetUsername = ""
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            enabled = !isSending,
            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = DarkBackground),
        ) { if (isSending) CircularProgressIndicator(color = DarkBackground, modifier = Modifier.size(20.dp)) else Text("KIRIM NOTIFIKASI", fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun SettingsTab(vm: LicenseGenViewModel, ctx: Context) {
    var showKeyInput by remember { mutableStateOf(false) }
    var keyInput by remember { mutableStateOf("") }
    var keyMsg by remember { mutableStateOf("") }
    var keyMsgOk by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("PENGATURAN", style = MaterialTheme.typography.titleMedium, color = NeonCyan)
        Spacer(Modifier.height(8.dp))

        // ECDSA Private Key
        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = DarkSurface), border = BorderStroke(1.dp, DarkSurfaceV3)) {
            Column(Modifier.padding(16.dp)) {
                Text("PRIVATE KEY (ECDSA)", style = MaterialTheme.typography.labelLarge, color = NeonCyan)
                Spacer(Modifier.height(4.dp))
                val hasKey = vm.hasPrivateKey()
                Text(
                    if (hasKey) "✅ Private key terkonfigurasi" else "❌ Private key belum dikonfigurasi! Lisensi tidak bisa digenerate.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hasKey) NeonGreen else NeonRed,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showKeyInput = !showKeyInput; keyInput = ""; keyMsg = "" },
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DarkBackground),
                    ) { Text(if (showKeyInput) "Batal" else if (hasKey) "Ganti Key" else "Set Key") }
                    OutlinedButton(
                        onClick = {
                            vm.generateNewKeyPair { result ->
                                if (result.startsWith("ERROR")) {
                                    keyMsg = result; keyMsgOk = false
                                } else {
                                    keyMsg = "Key baru berhasil digenerate! Backup key ini."; keyMsgOk = true
                                }
                            }
                        },
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonGreen),
                        border = BorderStroke(1.dp, NeonGreen),
                    ) { Text("Generate Baru") }
                }
                if (showKeyInput) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = keyInput, onValueChange = { keyInput = it; keyMsg = "" },
                        label = { Text("Paste Base64 Private Key") }, minLines = 3, maxLines = 5,
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonGreen, unfocusedBorderColor = DarkSurfaceV3,
                            cursorColor = NeonGreen, unfocusedContainerColor = DarkSurfaceV2, focusedContainerColor = DarkSurfaceV2,
                            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                        ),
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (keyInput.isBlank()) { keyMsg = "Masukkan key"; keyMsgOk = false; return@Button }
                            vm.setPrivateKey(keyInput.trim())
                            keyMsg = "Private key tersimpan!"; keyMsgOk = true
                            showKeyInput = false
                        },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = DarkBackground),
                    ) { Text("Simpan Key") }
                }
                if (keyMsg.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(keyMsg, style = MaterialTheme.typography.bodySmall, color = if (keyMsgOk) NeonGreen else NeonRed)
                }
                if (hasKey) {
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val key = vm.getPrivateKey()
                            val clip = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clip.setPrimaryClip(ClipData.newPlainText("Private Key", key))
                            Toast.makeText(ctx, "Key tersalin (RAHASIA!)", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonYellow, contentColor = DarkBackground),
                    ) { Text("📋 Copy Key (Backup!)", fontWeight = FontWeight.Bold) }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Info
        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = DarkSurface), border = BorderStroke(1.dp, DarkSurfaceV3)) {
            Column(Modifier.padding(16.dp)) {
                Text("INFORMASI", style = MaterialTheme.typography.labelLarge, color = NeonCyan)
                Spacer(Modifier.height(8.dp))
                Text("Jika private key hilang, lisensi yang sudah digenerate tidak bisa diverifikasi. Backup key dengan aman!", style = MaterialTheme.typography.bodySmall, color = NeonYellow)
                Spacer(Modifier.height(8.dp))
                Text("Public key sudah tertanam di aplikasi utama. Hanya private key yang perlu dikonfigurasi di sini.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }

        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text("$label : ", style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.width(60.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
    }
}
