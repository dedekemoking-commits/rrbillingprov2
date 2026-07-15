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
import kotlinx.coroutines.launch

private val DarkBackground = Color(0xFF121212)
private val DarkSurface = Color(0xFF1E1E1E)
private val DarkSurfaceV2 = Color(0xFF2D2D2D)
private val DarkSurfaceV3 = Color(0xFF3D3D3D)
private val NeonGreen = Color(0xFF39FF14)
private val NeonCyan = Color(0xFF00E5FF)
private val NeonRed = Color(0xFFFF1744)
private val NeonYellow = Color(0xFFFFEA00)
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

        TabRow(selectedTabIndex = tab, containerColor = DarkSurface, contentColor = NeonGreen) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("User") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Generate") })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Riwayat") })
        }

        when (tab) {
            0 -> UserListTab(vm)
            1 -> GenerateTab(vm, ctx)
            2 -> HistoryTab(vm, ctx)
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
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(rec.kode, style = MaterialTheme.typography.bodyMedium, color = NeonGreen, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text(status, style = MaterialTheme.typography.bodySmall, color = if (rec.activatedAt > 0) NeonGreen else TextSecondary)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("${rec.username} • ${rec.email} • ${rec.paket} • $time", style = MaterialTheme.typography.bodySmall, color = TextDim)
                        }
                    }
                }
            }
        }
    }
}
