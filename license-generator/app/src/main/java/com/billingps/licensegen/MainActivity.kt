package com.billingps.licensegen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.ContentCopy
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
                    background = DarkBackground,
                    surface = DarkSurface,
                    primary = NeonGreen,
                    secondary = NeonCyan,
                    error = NeonRed,
                ),
                content = { App() }
            )
        }
    }
}

@Composable
fun App(vm: LicenseGenViewModel = viewModel()) {
    if (vm.isLoggedIn) {
        MainScreen(vm)
    } else {
        LoginScreen(vm)
    }
}

@Composable
fun LoginScreen(vm: LicenseGenViewModel) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    val ctx = LocalContext.current

    Box(
        modifier = Modifier.fillMaxSize().background(DarkBackground).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, NeonGreen.copy(alpha = 0.3f)),
        ) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("RR LICENSE GENERATOR", style = MaterialTheme.typography.titleLarge, color = NeonGreen, letterSpacing = 2.sp)
                Spacer(Modifier.height(8.dp))
                Text("Login Admin", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                Spacer(Modifier.height(24.dp))

                OutlinedTextField(value = email, onValueChange = { email = it; msg = "" },
                    label = { Text("Email") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonGreen, unfocusedBorderColor = DarkSurfaceV3,
                        cursorColor = NeonGreen,
                        unfocusedContainerColor = DarkSurfaceV2, focusedContainerColor = DarkSurfaceV2,
                    ))

                Spacer(Modifier.height(12.dp))

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
                    if (email.isBlank() || password.isBlank()) { msg = "Isi email & password"; return@Button }
                    msg = "Memproses..."
                    vm.login(email, password) { ok, err ->
                        msg = if (ok) "" else err
                    }
                }, modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = DarkBackground)) {
                    if (vm.isBusy) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = DarkBackground, strokeWidth = 2.dp)
                    } else {
                        Text("LOGIN", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun MainScreen(vm: LicenseGenViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    val ctx = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
        // Top bar
        Surface(color = DarkSurface, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("RR LICENSE GEN", style = MaterialTheme.typography.titleMedium, color = NeonGreen, modifier = Modifier.weight(1f))
                Text(vm.currentUser, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { vm.signOut() }) {
                    Icon(Icons.Filled.ExitToApp, contentDescription = "Logout", tint = NeonRed)
                }
            }
        }

        // Tabs
        TabRow(selectedTabIndex = tab, containerColor = DarkSurface, contentColor = NeonGreen) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Generate") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Riwayat") })
        }

        when (tab) {
            0 -> GenerateTab(vm, ctx)
            1 -> HistoryTab(vm, ctx)
        }
    }
}

@Composable
fun GenerateTab(vm: LicenseGenViewModel, ctx: Context) {
    var username by remember { mutableStateOf("") }
    var paket by remember { mutableStateOf("BULANAN") }
    var generatedCode by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("GENERATE KODE LISENSI", style = MaterialTheme.typography.titleMedium, color = NeonCyan)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(value = username, onValueChange = { username = it; generatedCode = "" },
            label = { Text("Username Pelanggan") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NeonGreen, unfocusedBorderColor = DarkSurfaceV3,
                cursorColor = NeonGreen,
                unfocusedContainerColor = DarkSurfaceV2, focusedContainerColor = DarkSurfaceV2,
            ))

        Spacer(Modifier.height(12.dp))

        Text("Paket:", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("BULANAN", "3BULAN", "TAHUNAN", "LIFETIME").forEach { p ->
                FilterChip(
                    selected = paket == p,
                    onClick = { paket = p; generatedCode = "" },
                    label = { Text(p, style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NeonGreen, selectedLabelColor = DarkBackground),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(onClick = {
            if (username.isBlank()) { msg = "Masukkan username"; return@Button }
            msg = "Generating..."; generatedCode = ""
            vm.generateLicense(paket, username) { code ->
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
            Text(msg, style = MaterialTheme.typography.bodySmall, color = NeonYellow)
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
                            Text("${rec.username} • ${rec.paket} • $time", style = MaterialTheme.typography.bodySmall, color = TextDim)
                        }
                    }
                }
            }
        }
    }
}
