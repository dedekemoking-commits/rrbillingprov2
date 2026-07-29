package com.billingps.licensegen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

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

    companion object {
        private const val PERMISSION_NOTIF = 9001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationHelper.init(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission()
        }
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

    private fun requestNotificationPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED -> { /* already granted */ }
            shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), PERMISSION_NOTIF)
            }
            else -> {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), PERMISSION_NOTIF)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_NOTIF && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            android.util.Log.i("MainActivity", "POST_NOTIFICATIONS granted")
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
    var username by remember { mutableStateOf("rrbilling") }
    var password by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var showUserMenu by remember { mutableStateOf(false) }

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

                Box {
                    OutlinedTextField(value = username, onValueChange = { username = it; msg = "" },
                        label = { Text("Username") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonGreen, unfocusedBorderColor = DarkSurfaceV3,
                            cursorColor = NeonGreen,
                            unfocusedContainerColor = DarkSurfaceV2, focusedContainerColor = DarkSurfaceV2,
                        ),
                        trailingIcon = {
                            IconButton(onClick = { showUserMenu = !showUserMenu }) {
                                Icon(Icons.Filled.ArrowDropDown, contentDescription = "Pilih admin", tint = TextSecondary)
                            }
                        },
                    )
                    DropdownMenu(expanded = showUserMenu, onDismissRequest = { showUserMenu = false }) {
                        DropdownMenuItem(text = { Text("rrbilling") }, onClick = { username = "rrbilling"; showUserMenu = false })
                        DropdownMenuItem(text = { Text("rrlisensi") }, onClick = { username = "rrlisensi"; showUserMenu = false })
                    }
                }

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
                    vm.login(username, password) { ok, err -> msg = if (ok) "" else err }
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
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var prevRequestCount by remember { mutableIntStateOf(vm.pendingRequestCount) }

    LaunchedEffect(vm.pendingRequestCount) {
        if (vm.pendingRequestCount > prevRequestCount && prevRequestCount > 0) {
            val newRequests = vm.pendingRequestCount - prevRequestCount
            scope.launch {
                snackbarHostState.showSnackbar(
                    "$newRequests request lisensi baru masuk!",
                    duration = SnackbarDuration.Short,
                )
            }
            try {
                val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val r = RingtoneManager.getRingtone(ctx, notification)
                r?.play()
            } catch (e: Exception) {}
        }
        prevRequestCount = vm.pendingRequestCount
    }

    val tabTitles = listOf("User", "Generate", "Riwayat", "Invoice", "Promo", "Broadcast", "Settings")

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = DarkBackground,
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding).background(DarkBackground)) {
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
            val reqCount = vm.pendingRequestCount
            val promoAktif = vm.promo.promoAktif

            ScrollableTabRow(
                selectedTabIndex = tab,
                containerColor = DarkSurface,
                contentColor = NeonGreen,
                edgePadding = 8.dp,
            ) {
                tabTitles.forEachIndexed { idx, title ->
                    Tab(selected = tab == idx, onClick = {
                        tab = idx
                        if (idx == 3 && reqCount > 0) vm.markLicenseRequestsRead()
                    }, text = {
                        when (idx) {
                            0 -> Text("User")
                            1 -> Text("Generate")
                            2 -> Text("Riwayat")
                            3 -> {
                                val badgeCount = pendingCount + reqCount
                                if (badgeCount > 0) {
                                    BadgedBox(badge = { Badge { Text("$badgeCount") } }) { Text("Invoice") }
                                } else Text("Invoice")
                            }
                            4 -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Promo")
                                    if (promoAktif) {
                                        Spacer(Modifier.width(4.dp))
                                        Text("🔥", fontSize = 12.sp)
                                    }
                                }
                            }
                            5 -> Text("Broadcast")
                            6 -> Text("Settings")
                        }
                    })
                }
            }

            when (tab) {
                0 -> UserListTab(vm, ctx)
                1 -> GenerateTab(vm, ctx)
                2 -> HistoryTab(vm, ctx)
                3 -> InvoiceTab(vm, ctx)
                4 -> PromoTab(vm, ctx)
                5 -> BroadcastTab(vm, ctx)
                6 -> SettingsTab(vm, ctx)
            }
        }
    }
}

@Composable
fun UserListTab(vm: LicenseGenViewModel, ctx: Context) {
    var search by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<FirestoreUser?>(null) }
    var deleteMsg by remember { mutableStateOf("") }
    val filtered = remember(vm.userList, search) {
        if (search.isBlank()) vm.userList
        else vm.userList.filter {
            it.username.contains(search, ignoreCase = true) ||
            it.email.contains(search, ignoreCase = true) ||
            it.whatsapp.contains(search) ||
            it.appVersion.contains(search, ignoreCase = true)
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("DAFTAR USER", style = MaterialTheme.typography.titleMedium, color = NeonCyan)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = search, onValueChange = { search = it },
            placeholder = { Text("Cari username, email, WA, atau versi...") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NeonGreen, unfocusedBorderColor = DarkSurfaceV3,
                cursorColor = NeonGreen, unfocusedContainerColor = DarkSurfaceV2, focusedContainerColor = DarkSurfaceV2,
                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
            ),
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp)) },
        )
        Spacer(Modifier.height(8.dp))
        val versionCounts = filtered.groupBy { it.appVersion.ifEmpty { "?" } }.mapValues { it.value.size }
        Text("${filtered.size} user", style = MaterialTheme.typography.bodySmall, color = TextDim)
        if (versionCounts.isNotEmpty()) {
            Text(versionCounts.entries.joinToString(" • ") { "${it.key}=${it.value}" }, style = MaterialTheme.typography.bodySmall, color = NeonCyan.copy(alpha = 0.7f), fontSize = 10.sp)
        }

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
                        border = BorderStroke(1.dp, if (user.whatsapp.isNotBlank()) NeonGreen.copy(alpha = 0.2f) else DarkSurfaceV3),
                    ) {
                        val waNumber = user.whatsapp.filter { it.isDigit() }
                        Row(Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(user.username, style = MaterialTheme.typography.bodyMedium, color = if (waNumber.isNotEmpty()) NeonCyan else TextPrimary, fontWeight = FontWeight.Bold)
                                    if (waNumber.isNotEmpty()) {
                                        Spacer(Modifier.width(4.dp))
                                        Icon(Icons.Filled.Phone, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(14.dp))
                                    }
                                }
                                if (user.email.isNotBlank()) {
                                    Text(user.email, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                }
                                if (user.whatsapp.isNotBlank()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Phone, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(12.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(user.whatsapp, style = MaterialTheme.typography.bodySmall, color = NeonGreen)
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${user.role} • ${user.dibuat.take(10)}", style = MaterialTheme.typography.bodySmall, color = TextDim)
                                    if (user.appVersion.isNotBlank()) {
                                        Spacer(Modifier.width(6.dp))
                                        Text("v${user.appVersion}", style = MaterialTheme.typography.bodySmall, color = NeonCyan)
                                    }
                                }
                                if (user.lastActive > 0) {
                                    val sdf = java.text.SimpleDateFormat("dd/MM/yy HH:mm", java.util.Locale.US)
                                    val lastActiveStr = sdf.format(java.util.Date(user.lastActive))
                                    Text("Terakhir: $lastActiveStr", style = MaterialTheme.typography.bodySmall, color = TextDim, fontSize = 10.sp)
                                }
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (waNumber.isNotEmpty()) {
                                    IconButton(onClick = {
                                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://wa.me/$waNumber"))
                                        ctx.startActivity(intent)
                                    }) { Icon(Icons.Filled.Chat, contentDescription = "WA", tint = NeonGreen) }
                                }
                                IconButton(onClick = { deleteTarget = user; deleteMsg = "" }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Hapus user", tint = NeonRed.copy(alpha = 0.7f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = DarkSurface,
            title = { Text("⚠️ Hapus User?", color = NeonRed, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Hapus user ${deleteTarget!!.username}?", color = TextPrimary)
                    if (deleteMsg.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(deleteMsg, style = MaterialTheme.typography.bodySmall, color = if (deleteMsg.startsWith("User")) NeonGreen else NeonRed)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteUser(deleteTarget!!.username) { ok, msg ->
                        deleteMsg = msg
                        if (ok) deleteTarget = null
                    }
                }) { Text("HAPUS", color = NeonRed) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("BATAL", color = NeonGreen) }
            },
        )
    }
}

@Composable
fun GenerateTab(vm: LicenseGenViewModel, ctx: Context) {
    var selectedUser by remember { mutableStateOf<FirestoreUser?>(null) }
    var showUserPicker by remember { mutableStateOf(false) }
    var pickerSearch by remember { mutableStateOf("") }
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
                    OutlinedTextField(value = pickerSearch, onValueChange = { pickerSearch = it },
                        placeholder = { Text("Cari username...") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, unfocusedBorderColor = DarkSurfaceV3,
                            cursorColor = NeonCyan, unfocusedContainerColor = DarkSurfaceV2, focusedContainerColor = DarkSurfaceV2,
                            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp)) },
                    )
                    Spacer(Modifier.height(8.dp))
                    val pickerList = vm.userList.filter { u ->
                        (u.email.isNotBlank() || u.whatsapp.isNotBlank()) &&
                        (pickerSearch.isBlank() || u.username.contains(pickerSearch, ignoreCase = true))
                    }
                    if (pickerList.isEmpty()) {
                        Text("Tidak ada user dengan kontak", style = MaterialTheme.typography.bodyMedium, color = TextDim, modifier = Modifier.padding(16.dp))
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(pickerList) { user ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth().clickable { selectedUser = user; showUserPicker = false },
                                    color = if (selectedUser?.username == user.username) DarkSurfaceV2 else DarkSurface,
                                ) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text(user.username, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                                        if (user.email.isNotBlank()) Text(user.email, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                        if (user.whatsapp.isNotBlank()) Text("📱 ${user.whatsapp}", style = MaterialTheme.typography.bodySmall, color = NeonGreen)
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

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("GENERATE KODE LISENSI", style = MaterialTheme.typography.titleMedium, color = NeonCyan)
        Spacer(Modifier.height(16.dp))

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
                Text("${selectedUser!!.username}", fontWeight = FontWeight.Bold)
            }
        }
        if (selectedUser != null) {
            Text("${selectedUser!!.email} • ${selectedUser!!.whatsapp.ifEmpty { "no WA"} }", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
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
                    .border(BorderStroke(2.dp, NeonGreen), RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceV2),
            ) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("KODE AKTIVASI", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    Text(generatedCode, style = MaterialTheme.typography.headlineSmall, color = NeonGreen, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Copy button
                        Button(
                            onClick = {
                                val clip = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clip.setPrimaryClip(ClipData.newPlainText("Kode Aktivasi", generatedCode))
                                Toast.makeText(ctx, "Kode tersalin!", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = DarkBackground),
                        ) { Icon(Icons.Filled.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Copy") }
                        // Share button
                        Button(
                            onClick = {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, "Kode Lisensi RR Billing: $generatedCode\nUntuk: ${selectedUser?.email ?: "-"}\nPaket: $paket")
                                    type = "text/plain"
                                }
                                ctx.startActivity(Intent.createChooser(sendIntent, "Bagikan Kode Lisensi"))
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DarkBackground),
                        ) { Icon(Icons.Filled.Share, contentDescription = "Share", modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Share") }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Untuk: ${selectedUser?.email ?: selectedUser?.username ?: "-"}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
fun HistoryTab(vm: LicenseGenViewModel, ctx: Context) {
    var search by remember { mutableStateOf("") }
    var revokeTarget by remember { mutableStateOf<LicenseRecord?>(null) }
    var revokeMsg by remember { mutableStateOf("") }

    val filtered = remember(vm.historyList, search) {
        if (search.isBlank()) vm.historyList
        else vm.historyList.filter {
            it.kode.contains(search, ignoreCase = true) ||
            it.username.contains(search, ignoreCase = true) ||
            it.email.contains(search, ignoreCase = true) ||
            it.paket.contains(search, ignoreCase = true)
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("RIWAYAT GENERATE", style = MaterialTheme.typography.titleMedium, color = NeonCyan)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = search, onValueChange = { search = it },
            placeholder = { Text("Cari kode, user, paket...") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonGreen, unfocusedBorderColor = DarkSurfaceV3,
                cursorColor = NeonGreen, unfocusedContainerColor = DarkSurfaceV2, focusedContainerColor = DarkSurfaceV2,
                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp)) },
        )
        Spacer(Modifier.height(8.dp))
        Text("${filtered.size} riwayat", style = MaterialTheme.typography.bodySmall, color = TextDim)
        Spacer(Modifier.height(8.dp))

        if (vm.historyList.isEmpty() && vm.firestoreReady) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(if (search.isBlank()) "Belum ada riwayat" else "Tidak cocok", style = MaterialTheme.typography.bodyMedium, color = TextDim) }
        } else if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(if (search.isBlank()) "Belum ada riwayat" else "Tidak cocok", style = MaterialTheme.typography.bodyMedium, color = TextDim) }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered) { rec ->
                    val time = if (rec.generatedAt > 0) {
                        val sdf = java.text.SimpleDateFormat("dd/MM/yy HH:mm", java.util.Locale.US)
                        sdf.format(java.util.Date(rec.generatedAt))
                    } else "-"
                    val status = if (rec.revoked) "❌ Dinonaktifkan" else if (rec.activatedAt > 0) "✅ Terpakai" else "⏳ Belum"
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = BorderStroke(1.dp, DarkSurfaceV3),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(rec.kode, style = MaterialTheme.typography.titleMedium, color = NeonGreen, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text(status, style = MaterialTheme.typography.bodySmall, color = if (rec.revoked) NeonRed else if (rec.activatedAt > 0) NeonGreen else TextSecondary)
                            }
                            Spacer(Modifier.height(6.dp))
                            HorizontalDivider(color = DarkSurfaceV3, thickness = 0.5.dp)
                            Spacer(Modifier.height(6.dp))
                            DetailRow("User", rec.username)
                            DetailRow("Email", rec.email.ifEmpty { "(tanpa email)" })
                            DetailRow("Paket", rec.paket)
                            DetailRow("Berlaku", rec.expiry)
                            DetailRow("Generate", time)
                            if (!rec.revoked) {
                                Spacer(Modifier.height(6.dp))
                                OutlinedButton(
                                    onClick = { revokeTarget = rec; revokeMsg = "" },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonRed),
                                    border = BorderStroke(1.dp, NeonRed.copy(alpha = 0.5f)),
                                ) { Text("NONAKTIFKAN LISENSI", fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (revokeTarget != null) {
        AlertDialog(
            onDismissRequest = { revokeTarget = null },
            containerColor = DarkSurface,
            title = { Text("⚠️ Nonaktifkan Lisensi?", color = NeonRed, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Lisensi ${revokeTarget!!.kode} untuk ${revokeTarget!!.username} akan dinonaktifkan.", color = TextPrimary)
                    Text("User tidak bisa menggunakan kode ini lagi.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    if (revokeMsg.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(revokeMsg, style = MaterialTheme.typography.bodySmall, color = if (revokeMsg.startsWith("Lisensi")) NeonGreen else NeonRed)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.revokeLicense(revokeTarget!!.id) { ok, msg ->
                        revokeMsg = msg
                        if (ok) revokeTarget = null
                    }
                }) { Text("NONAKTIFKAN", color = NeonRed) }
            },
            dismissButton = {
                TextButton(onClick = { revokeTarget = null }) { Text("BATAL", color = NeonGreen) }
            },
        )
    }
}

@Composable
fun InvoiceTab(vm: LicenseGenViewModel, ctx: Context) {
    val pending = vm.invoiceList.filter { it.status == "WAITING_CONFIRMATION" }
    val requests = vm.licenseRequests

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        // ── License Request Notifications ──
        if (requests.isNotEmpty()) {
            Text("PEMBERITAHUAN REQUEST", style = MaterialTheme.typography.titleSmall, color = NeonOrange, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            requests.take(10).forEach { req ->
                val sdf = java.text.SimpleDateFormat("dd/MM/yy HH:mm", java.util.Locale.US)
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = BorderStroke(1.dp, if (!req.read) NeonOrange.copy(alpha = 0.5f) else DarkSurfaceV3),
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = if (!req.read) NeonOrange else TextDim, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("User ${req.user} request ${req.paket}", style = MaterialTheme.typography.bodySmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                            Text("Rp${String.format("%,d", req.harga)} • ${sdf.format(java.util.Date(req.sentAt))}", style = MaterialTheme.typography.bodySmall, color = TextDim)
                        }
                        if (!req.read) {
                            Box(Modifier.size(8.dp).background(NeonOrange, shape = RoundedCornerShape(4.dp)))
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = DarkSurfaceV3, thickness = 1.dp)
            Spacer(Modifier.height(12.dp))
        }

        // ── Invoices ──
        Text("INVOICE MENUNGGU KONFIRMASI", style = MaterialTheme.typography.titleMedium, color = NeonCyan)
        Spacer(Modifier.height(8.dp))
        Text("${pending.size} menunggu", style = MaterialTheme.typography.bodySmall, color = TextDim)
        Spacer(Modifier.height(12.dp))

        if (pending.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                Text("Tidak ada invoice menunggu", style = MaterialTheme.typography.bodyMedium, color = TextDim)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                pending.forEach { inv ->
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

                            // WhatsApp contact button
                            val userWa = vm.userList.find { it.username == inv.username }?.whatsapp ?: ""
                            if (userWa.isNotBlank()) {
                                Box(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    val waMsg = "Halo ${inv.username}, kami terima pembayaran paket ${inv.paket} Rp${String.format("%,d", inv.harga)} (${inv.id}). Lisensi sedang diproses."
                                    Button(
                                        onClick = {
                                            try {
                                                val uri = Uri.parse("https://wa.me/${userWa.replace(Regex("[^0-9]"), "")}?text=${java.net.URLEncoder.encode(waMsg, "UTF-8")}")
                                                ctx.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                            } catch (e: Exception) { Toast.makeText(ctx, "Gagal buka WA: ${e.message}", Toast.LENGTH_SHORT).show() }
                                        },
                                        modifier = Modifier.fillMaxWidth().height(36.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = DarkBackground),
                                    ) {
                                        Icon(Icons.Filled.Chat, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Hubungi ${inv.username} via WA", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }

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
                                        modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).padding(top = 4.dp),
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

        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = DarkSurfaceV3, thickness = 1.dp)
        Spacer(Modifier.height(12.dp))

        val confirmed = vm.invoiceList.filter { it.status == "CONFIRMED" && it.confirmedBy.isNotBlank() }.take(20)
        Text("RIWAYAT KONFIRMASI", style = MaterialTheme.typography.titleMedium, color = NeonGreen)
        Spacer(Modifier.height(4.dp))
        Text("${confirmed.size} terkonfirmasi", style = MaterialTheme.typography.bodySmall, color = TextDim)
        Spacer(Modifier.height(8.dp))

        if (confirmed.isEmpty()) {
            Text("Belum ada konfirmasi", style = MaterialTheme.typography.bodySmall, color = TextDim)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                confirmed.forEach { inv ->
                    val sdf = java.text.SimpleDateFormat("dd/MM/yy HH:mm", java.util.Locale.US)
                    val verifTime = if (inv.dibayar > 0) sdf.format(java.util.Date(inv.dibayar)) else "-"
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = BorderStroke(1.dp, NeonGreen.copy(alpha = 0.2f)),
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(inv.id, style = MaterialTheme.typography.bodySmall, color = NeonGreen, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text(inv.paket, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                            Spacer(Modifier.height(4.dp))
                            DetailRow("User", inv.username)
                            DetailRow("Verifikasi", verifTime)
                            DetailRow("Oleh", inv.confirmedBy)
                            if (inv.kodeLisensi.isNotBlank()) {
                                DetailRow("Lisensi", inv.kodeLisensi)
                            }
                        }
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
    var promoDuration by remember { mutableStateOf(vm.promo.promoDurationHours) }

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
        promoDuration = vm.promo.promoDurationHours
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
        vm.setPromo(promoAktif, diskonMap, overrides, promoDuration) { ok, message ->
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

        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = DarkSurface), border = BorderStroke(1.dp, DarkSurfaceV3)) {
            Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Durasi Promo (jam)", style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
                OutlinedTextField(value = if (promoDuration == 0) "" else promoDuration.toString(), onValueChange = { promoDuration = it.filter { c -> c.isDigit() }.take(5).toIntOrNull() ?: 0 },
                    placeholder = { Text("0 = tanpa batas") }, singleLine = true, modifier = Modifier.width(100.dp), shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonGreen, unfocusedBorderColor = DarkSurfaceV3,
                        cursorColor = NeonGreen, unfocusedContainerColor = DarkSurfaceV2, focusedContainerColor = DarkSurfaceV2,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
                    textStyle = MaterialTheme.typography.bodySmall.copy(textAlign = TextAlign.Center),
                )
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

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = DarkSurfaceV3, thickness = 1.dp)
        Spacer(Modifier.height(16.dp))

        // ── New User Promo ──────────────────────────────────
        Text("PROMO USER BARU", style = MaterialTheme.typography.titleMedium, color = NeonCyan)
        Spacer(Modifier.height(4.dp))
        Text("Atur diskon khusus untuk user yang baru mendaftar.", style = MaterialTheme.typography.bodySmall, color = TextDim)
        Spacer(Modifier.height(12.dp))

        var nuActive by remember { mutableStateOf(vm.promo.newUserPromoActive) }
        var nuDiskon by remember { mutableStateOf(vm.promo.newUserDiscountPercent) }
        var nuDurasi by remember { mutableStateOf(vm.promo.newUserPromoDurationHours.toString()) }
        var nuCek1 by remember { mutableStateOf((vm.promo.newUserDiskonPerPaket["1 Bulan"] ?: 0) > 0) }
        var nuCek3 by remember { mutableStateOf((vm.promo.newUserDiskonPerPaket["3 Bulan"] ?: 0) > 0) }
        var nuCek12 by remember { mutableStateOf((vm.promo.newUserDiskonPerPaket["1 Tahun"] ?: 0) > 0) }
        var nuCekLt by remember { mutableStateOf((vm.promo.newUserDiskonPerPaket["LIFETIME"] ?: 0) > 0) }
        var nuMsg by remember { mutableStateOf("") }
        var nuMsgOk by remember { mutableStateOf(false) }

        LaunchedEffect(vm.promo) {
            nuActive = vm.promo.newUserPromoActive
            nuDiskon = vm.promo.newUserDiscountPercent
            nuDurasi = vm.promo.newUserPromoDurationHours.toString()
            nuCek1 = (vm.promo.newUserDiskonPerPaket["1 Bulan"] ?: 0) > 0
            nuCek3 = (vm.promo.newUserDiskonPerPaket["3 Bulan"] ?: 0) > 0
            nuCek12 = (vm.promo.newUserDiskonPerPaket["1 Tahun"] ?: 0) > 0
            nuCekLt = (vm.promo.newUserDiskonPerPaket["LIFETIME"] ?: 0) > 0
        }

        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = DarkSurface), border = BorderStroke(1.dp, DarkSurfaceV3)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Aktifkan Promo User Baru", style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
                    Switch(checked = nuActive, onCheckedChange = { nuActive = it }, colors = SwitchDefaults.colors(checkedTrackColor = NeonCyan, checkedThumbColor = DarkBackground))
                }
                if (nuActive) {
                    Spacer(Modifier.height(12.dp))
                    // Diskon slider
                    Text("Diskon: $nuDiskon%", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                    Slider(value = nuDiskon.toFloat(), onValueChange = { nuDiskon = it.toInt() }, valueRange = 0f..100f, steps = 19,
                        colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan, inactiveTrackColor = DarkSurfaceV3))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("0%", style = MaterialTheme.typography.bodySmall, color = TextDim)
                        Text("50%", style = MaterialTheme.typography.bodySmall, color = TextDim)
                        Text("100%", style = MaterialTheme.typography.bodySmall, color = TextDim)
                    }
                    Spacer(Modifier.height(8.dp))
                    // Durasi
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Durasi (jam):", style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(value = nuDurasi, onValueChange = { nuDurasi = it.filter { c -> c.isDigit() }.take(5) },
                            modifier = Modifier.width(100.dp), singleLine = true, shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, unfocusedBorderColor = DarkSurfaceV3,
                                cursorColor = NeonCyan, unfocusedContainerColor = DarkSurfaceV2, focusedContainerColor = DarkSurfaceV2,
                                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Pilih paket yang didiskon:", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = nuCek1, onClick = { nuCek1 = !nuCek1 }, label = { Text("1 Bulan", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NeonGreen, selectedLabelColor = DarkBackground))
                        FilterChip(selected = nuCek3, onClick = { nuCek3 = !nuCek3 }, label = { Text("3 Bulan", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NeonCyan, selectedLabelColor = DarkBackground))
                        FilterChip(selected = nuCek12, onClick = { nuCek12 = !nuCek12 }, label = { Text("1 Tahun", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NeonYellow, selectedLabelColor = DarkBackground))
                        FilterChip(selected = nuCekLt, onClick = { nuCekLt = !nuCekLt }, label = { Text("LIFETIME", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NeonOrange, selectedLabelColor = DarkBackground))
                    }
                }
            }
        }

        if (nuMsg.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(nuMsg, style = MaterialTheme.typography.bodySmall, color = if (nuMsgOk) NeonGreen else NeonRed)
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                val diskonMap = mutableMapOf<String, Int>()
                if (nuCek1) diskonMap["1 Bulan"] = nuDiskon.coerceIn(0, 100)
                if (nuCek3) diskonMap["3 Bulan"] = nuDiskon.coerceIn(0, 100)
                if (nuCek12) diskonMap["1 Tahun"] = nuDiskon.coerceIn(0, 100)
                if (nuCekLt) diskonMap["LIFETIME"] = nuDiskon.coerceIn(0, 100)
                val durasi = nuDurasi.toIntOrNull() ?: 96
                vm.setNewUserPromo(nuActive, nuDiskon, durasi, diskonMap) { ok, message ->
                    nuMsg = message; nuMsgOk = ok
                    if (ok) Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DarkBackground),
        ) { Text("SIMPAN PROMO USER BARU", fontWeight = FontWeight.Bold, fontSize = 16.sp) }

        if (vm.promo.newUserPromoActive) {
            Spacer(Modifier.height(8.dp))
            val nuPkg = vm.promo.newUserDiskonPerPaket.filter { it.value > 0 }
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = DarkSurfaceV2), border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.3f))) {
                Column(Modifier.padding(12.dp)) {
                    Text("⭐ PROMO USER BARU AKTIF", style = MaterialTheme.typography.labelLarge, color = NeonCyan, fontWeight = FontWeight.Bold)
                    Text("Diskon ${vm.promo.newUserDiscountPercent}% selama ${vm.promo.newUserPromoDurationHours} jam", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                    Text("Paket: ${nuPkg.keys.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
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
fun BroadcastTab(vm: LicenseGenViewModel, ctx: Context) {
    var targetSemua by remember { mutableStateOf(true) }
    var targetUsername by remember { mutableStateOf("") }
    var judul by remember { mutableStateOf("") }
    var pesan by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var msgOk by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }
    var modePopup by remember { mutableStateOf(false) }
    var showUserPicker by remember { mutableStateOf(false) }
    var pickerSearch by remember { mutableStateOf("") }

    val accent = if (modePopup) NeonOrange else NeonCyan

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(if (modePopup) "KIRIM POPUP" else "KIRIM NOTIFIKASI", style = MaterialTheme.typography.titleMedium, color = accent)
        Spacer(Modifier.height(4.dp))
        Text(if (modePopup) "Muncul sebagai popup di aplikasi user" else "Push notification ke perangkat user", style = MaterialTheme.typography.bodySmall, color = TextDim)
        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Notif", style = MaterialTheme.typography.bodySmall, color = if (!modePopup) NeonCyan else TextDim)
                Spacer(Modifier.width(8.dp))
                Switch(checked = modePopup, onCheckedChange = { modePopup = it }, colors = SwitchDefaults.colors(checkedTrackColor = NeonOrange, checkedThumbColor = DarkBackground, uncheckedTrackColor = NeonCyan, uncheckedThumbColor = DarkBackground))
                Spacer(Modifier.width(8.dp))
                Text("Popup", style = MaterialTheme.typography.bodySmall, color = if (modePopup) NeonOrange else TextDim)
            }
        }
        Spacer(Modifier.height(12.dp))

        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = DarkSurface), border = BorderStroke(1.dp, DarkSurfaceV3)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = targetSemua, onClick = { targetSemua = true; targetUsername = "" }, colors = RadioButtonDefaults.colors(selectedColor = accent))
                    Text("Semua User", style = MaterialTheme.typography.bodySmall, color = TextPrimary, modifier = Modifier.weight(1f))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = !targetSemua, onClick = { targetSemua = false }, colors = RadioButtonDefaults.colors(selectedColor = accent))
                    Text("User Tertentu", style = MaterialTheme.typography.bodySmall, color = TextPrimary, modifier = Modifier.weight(1f))
                }
                if (!targetSemua) {
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = { showUserPicker = true },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceV2, contentColor = TextPrimary),
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
                        enabled = !isSending,
                    ) {
                        if (targetUsername.isBlank()) {
                            Text("Pilih User Target", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(targetUsername, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = DarkSurface), border = BorderStroke(1.dp, DarkSurfaceV3)) {
            Column(Modifier.padding(16.dp)) {
                OutlinedTextField(value = judul, onValueChange = { judul = it },
                    label = { Text(if (modePopup) "Judul Popup" else "Judul Notifikasi") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), enabled = !isSending,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent, unfocusedBorderColor = DarkSurfaceV3,
                        cursorColor = accent, unfocusedContainerColor = DarkSurfaceV2, focusedContainerColor = DarkSurfaceV2,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = pesan, onValueChange = { pesan = it },
                    label = { Text(if (modePopup) "Pesan Popup" else "Pesan Notifikasi") }, minLines = 3, maxLines = 5,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), enabled = !isSending,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent, unfocusedBorderColor = DarkSurfaceV3,
                        cursorColor = accent, unfocusedContainerColor = DarkSurfaceV2, focusedContainerColor = DarkSurfaceV2,
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
                    val action: (callback: ((Int) -> Unit)?) -> Unit = if (modePopup)
                        { cb -> vm.sendPopupToAllUsers(judul, pesan, cb) }
                        else { cb -> vm.sendFcmToAllUsers(judul, pesan, cb) }
                    action { sent ->
                        isSending = false
                        msg = "${if (modePopup) "Popup" else "Notifikasi"} terkirim ke $sent user"; msgOk = true
                        judul = ""; pesan = ""
                    }
                } else {
                    if (targetUsername.isBlank()) { msg = "Masukkan username target"; msgOk = false; isSending = false; return@Button }
                    if (modePopup) vm.sendPopupToUser(targetUsername, judul, pesan) else vm.sendFcmToUser(targetUsername, judul, pesan)
                    isSending = false
                    msg = "${if (modePopup) "Popup" else "Notifikasi"} terkirim ke $targetUsername"; msgOk = true
                    judul = ""; pesan = ""; targetUsername = ""
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            enabled = !isSending,
            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = DarkBackground),
        ) { if (isSending) CircularProgressIndicator(color = DarkBackground, modifier = Modifier.size(20.dp)) else Text(if (modePopup) "KIRIM POPUP" else "KIRIM NOTIFIKASI", fontWeight = FontWeight.Bold) }
    }

    if (showUserPicker) {
        Box(Modifier.fillMaxSize().background(Color(0xCC000000)).clickable(enabled = false) { }, contentAlignment = Alignment.Center) {
            Card(
                modifier = Modifier.fillMaxWidth(0.9f).heightIn(max = 500.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.3f)),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("PILIH USER", style = MaterialTheme.typography.titleMedium, color = accent, modifier = Modifier.weight(1f))
                        IconButton(onClick = { showUserPicker = false }) { Icon(Icons.Filled.Close, contentDescription = "Tutup", tint = NeonRed) }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = pickerSearch, onValueChange = { pickerSearch = it },
                        placeholder = { Text("Cari username...") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent, unfocusedBorderColor = DarkSurfaceV3,
                            cursorColor = accent, unfocusedContainerColor = DarkSurfaceV2, focusedContainerColor = DarkSurfaceV2,
                            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp)) },
                    )
                    Spacer(Modifier.height(8.dp))
                    val pickerList = vm.userList.filter { u ->
                        pickerSearch.isBlank() || u.username.contains(pickerSearch, ignoreCase = true)
                    }
                    if (pickerList.isEmpty()) {
                        Text("Tidak ada user", style = MaterialTheme.typography.bodyMedium, color = TextDim, modifier = Modifier.padding(16.dp))
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(pickerList) { user ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth().clickable { targetUsername = user.username; showUserPicker = false },
                                    color = if (targetUsername == user.username) DarkSurfaceV2 else DarkSurface,
                                ) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text(user.username, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                                        if (user.email.isNotBlank()) Text(user.email, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
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
}

@Composable
fun SettingsTab(vm: LicenseGenViewModel, ctx: Context) {
    var showKeyInput by remember { mutableStateOf(false) }
    var keyInput by remember { mutableStateOf("") }
    var keyMsg by remember { mutableStateOf("") }
    var keyMsgOk by remember { mutableStateOf(false) }
    var showGenerateConfirm by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("PENGATURAN", style = MaterialTheme.typography.titleMedium, color = NeonCyan)
        Spacer(Modifier.height(8.dp))

        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = DarkSurface), border = BorderStroke(1.dp, DarkSurfaceV3)) {
            Column(Modifier.padding(16.dp)) {
                Text("PRIVATE KEY (ECDSA)", style = MaterialTheme.typography.labelLarge, color = NeonCyan)
                Spacer(Modifier.height(4.dp))
                val hasKey = vm.hasPrivateKey()
                Text(
                    if (hasKey) "✅ Private key terkonfigurasi" else "❌ Private key belum dikonfigurasi! Lisensi tidak bisa digenerate.",
                    style = MaterialTheme.typography.bodySmall, color = if (hasKey) NeonGreen else NeonRed,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showKeyInput = !showKeyInput; keyInput = ""; keyMsg = "" },
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DarkBackground),
                    ) { Text(if (showKeyInput) "Batal" else if (hasKey) "Ganti Key" else "Set Key") }
                    OutlinedButton(
                        onClick = { if (hasKey) showGenerateConfirm = true else vm.generateNewKeyPair { r -> keyMsg = if (r.startsWith("ERROR")) { keyMsgOk = false; r } else { keyMsgOk = true; "Key baru berhasil digenerate! Backup key ini." }; keyMsgOk = !r.startsWith("ERROR") } },
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonGreen),
                        border = BorderStroke(1.dp, NeonGreen),
                    ) { Text("Generate Baru") }
                }
                if (showKeyInput) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = keyInput, onValueChange = { keyInput = it; keyMsg = "" },
                        label = { Text("Paste Base64 Private Key") }, minLines = 3, maxLines = 5,
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonGreen, unfocusedBorderColor = DarkSurfaceV3,
                            cursorColor = NeonGreen, unfocusedContainerColor = DarkSurfaceV2, focusedContainerColor = DarkSurfaceV2,
                            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        if (keyInput.isBlank()) { keyMsg = "Masukkan key"; keyMsgOk = false; return@Button }
                        vm.setPrivateKey(keyInput.trim())
                        keyMsg = "Private key tersimpan!"; keyMsgOk = true; showKeyInput = false
                    }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = DarkBackground)) { Text("Simpan Key") }
                }
                if (keyMsg.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(keyMsg, style = MaterialTheme.typography.bodySmall, color = if (keyMsgOk) NeonGreen else NeonRed)
                }
                if (hasKey) {
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = {
                        val key = vm.getPrivateKey()
                        val clip = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clip.setPrimaryClip(ClipData.newPlainText("Private Key", key))
                        Toast.makeText(ctx, "Key tersalin (RAHASIA!)", Toast.LENGTH_SHORT).show()
                    }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonYellow, contentColor = DarkBackground)) { Text("📋 Copy Key (Backup!)", fontWeight = FontWeight.Bold) }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

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

    if (showGenerateConfirm) {
        AlertDialog(
            onDismissRequest = { showGenerateConfirm = false },
            containerColor = DarkSurface,
            title = { Text("⚠️ Generate Key Baru?", color = NeonRed, fontWeight = FontWeight.Bold) },
            text = {
                Text("Key lama akan TIDAK BISA dipakai lagi. Lisensi yang sudah digenerate dengan key lama akan gagal verifikasi. Backup key lama dulu sebelum melanjutkan!", color = TextPrimary)
            },
            confirmButton = {
                TextButton(onClick = {
                    showGenerateConfirm = false
                    vm.generateNewKeyPair { result ->
                        keyMsg = if (result.startsWith("ERROR")) { keyMsgOk = false; result } else { keyMsgOk = true; "Key baru berhasil digenerate! Backup key ini." }
                    }
                }) { Text("LANJUTKAN", color = NeonRed) }
            },
            dismissButton = {
                TextButton(onClick = { showGenerateConfirm = false }) { Text("BATAL", color = NeonGreen) }
            },
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text("$label : ", style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.width(60.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
    }
}
