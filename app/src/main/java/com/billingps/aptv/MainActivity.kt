package com.billingps.aptv

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.billingps.aptv.models.MainViewModel
import com.billingps.aptv.models.SmtpConfig
import com.billingps.aptv.utils.StorageUtil
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.billingps.aptv.ui.screens.*
import com.billingps.aptv.ui.theme.BillingPSTheme
import com.billingps.aptv.ui.theme.*
import com.billingps.aptv.tv.TvHubManager
import com.billingps.aptv.utils.ConnectivityObserver
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var mainViewModel: MainViewModel
    private lateinit var tvViewModel: TvViewModel
    private var pendingDocumentPick: ((Uri?) -> Unit)? = null
    private var pendingCreateDocument: ((Uri?) -> Unit)? = null
    private var pendingBluetoothPermission: ((Boolean) -> Unit)? = null

    private val documentPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val callback = pendingDocumentPick
        pendingDocumentPick = null
        callback?.invoke(uri)
    }

    private val documentCreator = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        val callback = pendingCreateDocument
        pendingCreateDocument = null
        callback?.invoke(uri)
    }

    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result[Manifest.permission.BLUETOOTH_CONNECT] == true &&
            result[Manifest.permission.BLUETOOTH_SCAN] == true
        val callback = pendingBluetoothPermission
        pendingBluetoothPermission = null
        callback?.invoke(granted)
    }

    fun pickDocument(mimeTypes: Array<String>, onResult: (Uri?) -> Unit) {
        pendingDocumentPick?.invoke(null)
        pendingDocumentPick = onResult
        try {
            documentPicker.launch(mimeTypes)
        } catch (e: Exception) {
            Log.e("MainActivity", "pickDocument gagal: ${e.message}")
            pendingDocumentPick = null
            onResult(null)
        }
    }

    fun createDocument(suggestedName: String, onResult: (Uri?) -> Unit) {
        pendingCreateDocument?.invoke(null)
        pendingCreateDocument = onResult
        try {
            documentCreator.launch(suggestedName)
        } catch (e: Exception) {
            Log.e("MainActivity", "createDocument gagal: ${e.message}")
            pendingCreateDocument = null
            onResult(null)
        }
    }

    fun requestBluetoothPermissions(onResult: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            onResult(true)
            return
        }

        val permissions = arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
        )
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            onResult(true)
            return
        }

        pendingBluetoothPermission?.invoke(false)
        pendingBluetoothPermission = onResult
        try {
            bluetoothPermissionLauncher.launch(missing.toTypedArray())
        } catch (e: Exception) {
            Log.e("MainActivity", "requestBluetoothPermissions gagal: ${e.message}")
            pendingBluetoothPermission = null
            onResult(false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mainViewModel = ViewModelProvider(this)[MainViewModel::class.java]
        tvViewModel = ViewModelProvider(this)[TvViewModel::class.java]

        createNotificationChannel()
        TvHubManager.ensureStarted(this)
        // Init Python di background agar tidak blokir UI thread (fix ANR)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!Python.isStarted()) {
                    Python.start(AndroidPlatform(this@MainActivity))
                }
            } catch (e: Exception) {
                Log.e("BillingPS", "Python start failed", e)
            }
        }
        setContent {
            val mainState by mainViewModel.state.collectAsStateWithLifecycle()
            BillingPSTheme(fontTheme = mainState.themeOption, bgTheme = mainState.backgroundOption) {
                AppRoot(mainViewModel, tvViewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val billingChannel = NotificationChannel(
                "billing_channel",
                "Billing Notifikasi",
                NotificationManager.IMPORTANCE_HIGH,
            )
            val timerChannel = NotificationChannel(
                com.billingps.aptv.TimerService.CHANNEL_ID,
                "Timer Billing",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Notifikasi timer billing berjalan di latar belakang"
                setSound(null, null)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(billingChannel)
            nm.createNotificationChannel(timerChannel)
        }
    }
}

@Composable
fun AppRoot(
    mainViewModel: MainViewModel,
    tvViewModel: TvViewModel,
) {
    val mainState by mainViewModel.state.collectAsStateWithLifecycle()
    var smtpEmail by remember { mutableStateOf("") }
    var smtpPass by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
    var showNotifDialog by remember { mutableStateOf(false) }

    LaunchedEffect(mainState.isLoggedIn) {
        if (mainState.isLoggedIn && !StorageUtil.loadNotifDialogShown()) {
            showNotifDialog = true
        }
    }

    if (showNotifDialog) {
        NotificationPermissionDialog(onDismiss = {
            showNotifDialog = false
            StorageUtil.saveNotifDialogShown(true)
        })
    }

    // Promo popup with countdown
    if (mainState.showPromoPopup) {
        var remainingText by remember { mutableStateOf("") }
        LaunchedEffect(mainState.promoEndTimeMs) {
            while (isActive) {
                val now = System.currentTimeMillis()
                val diff = mainState.promoEndTimeMs - now
                if (diff <= 0) {
                    remainingText = "Promo telah berakhir!"
                } else {
                    val h = diff / 3600000L
                    val m = (diff % 3600000L) / 60000L
                    val s = (diff % 60000L) / 1000L
                    remainingText = "⏱ Sisa: ${h}j ${m}m ${s}d"
                }
                delay(1000)
            }
        }
        AlertDialog(
            onDismissRequest = { mainViewModel.dismissPromoPopup() },
            containerColor = DarkSurface,
            titleContentColor = NeonOrange,
            textContentColor = TextPrimary,
            title = { Text(mainState.promoPopupTitle, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(mainState.promoPopupBody, color = TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    if (remainingText.isNotEmpty()) {
                        Text(remainingText, color = NeonCyan, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Cek detail promo di menu Profil → Notifikasi.", style = MaterialTheme.typography.bodySmall, color = TextDim)
                }
            },
            confirmButton = {
                Button(
                    onClick = { mainViewModel.dismissPromoPopup() },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonOrange, contentColor = DarkBackground),
                ) { Text("OK") }
            },
        )
    }

    // Admin notification popup
    if (mainState.showNotifPopup) {
        AlertDialog(
            onDismissRequest = { mainViewModel.dismissNotifPopup() },
            containerColor = DarkSurface,
            titleContentColor = NeonCyan,
            textContentColor = TextPrimary,
            title = { Text(mainState.notifPopupTitle, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(mainState.notifPopupBody, color = TextPrimary)
                    Spacer(Modifier.height(12.dp))
                    Text("Dari Admin RR Billing Pro", style = MaterialTheme.typography.bodySmall, color = TextDim)
                }
            },
            confirmButton = {
                Button(
                    onClick = { mainViewModel.dismissNotifPopup() },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DarkBackground),
                ) { Text("OK") }
            },
        )
    }

    if (!mainState.isLoggedIn) {
        LoginScreen(
            viewModel = mainViewModel,
            onLoginSuccess = { /* state akan berubah otomatis */ },
        )
    } else {
        WebUiScreen(mainViewModel, tvViewModel)
    }

    if (mainState.quotaPopupVisible) {
        AlertDialog(
            onDismissRequest = { mainViewModel.dismissQuotaPopup() },
            containerColor = DarkSurface,
            titleContentColor = NeonCyan,
            textContentColor = TextPrimary,
            title = { Text(mainState.quotaPopupTitle, fontWeight = FontWeight.Bold) },
            text = { Text(mainState.quotaPopupBody, color = TextPrimary) },
            confirmButton = {
                Button(
                    onClick = { mainViewModel.dismissQuotaPopup() },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DarkBackground),
                ) { Text("Mengerti") }
            },
        )
    }

    if (mainState.needsSmtpSetup && !StorageUtil.loadSmtpSkipPermanently()) {
        var smtpSkipPermanent by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xCC000000)),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.3f)),
            ) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Email, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("KONFIGURASI EMAIL", style = MaterialTheme.typography.titleLarge, color = NeonCyan, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Untuk mengirim kode verifikasi & reset password, silakan konfigurasi email Gmail Anda.", style = MaterialTheme.typography.bodySmall, color = TextSecondary, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = smtpEmail,
                        onValueChange = { smtpEmail = it },
                        label = { Text("Email Gmail") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan, unfocusedBorderColor = DarkSurfaceV3,
                            cursorColor = NeonCyan, focusedLabelColor = NeonCyan,
                            unfocusedContainerColor = DarkSurfaceV2, focusedContainerColor = DarkSurfaceV2,
                        ),
                    )
                    Spacer(Modifier.height(8.dp))
                    var smtpPassVisible by remember { mutableStateOf(false) }
                    OutlinedTextField(
                        value = smtpPass,
                        onValueChange = { smtpPass = it },
                        label = { Text("App Password Gmail") },
                        singleLine = true,
                        visualTransformation = if (smtpPassVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { smtpPassVisible = !smtpPassVisible }) {
                                Icon(if (smtpPassVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = null, tint = TextSecondary)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan, unfocusedBorderColor = DarkSurfaceV3,
                            cursorColor = NeonCyan, focusedLabelColor = NeonCyan,
                            unfocusedContainerColor = DarkSurfaceV2, focusedContainerColor = DarkSurfaceV2,
                        ),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("Cara buat App Password: Google Account → Keamanan → App Password", style = MaterialTheme.typography.bodySmall, color = TextDim)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = smtpSkipPermanent,
                            onCheckedChange = { smtpSkipPermanent = it },
                            colors = CheckboxDefaults.colors(checkmarkColor = NeonRed, checkedColor = NeonRed.copy(alpha = 0.2f), uncheckedColor = TextDim),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Jangan tampilkan lagi", style = MaterialTheme.typography.bodySmall, color = TextDim, modifier = Modifier.clickable { smtpSkipPermanent = !smtpSkipPermanent })
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                if (smtpSkipPermanent) StorageUtil.saveSmtpSkipPermanently(true)
                                mainViewModel.saveSmtp(SmtpConfig(host = "", port = 0, user = "", pass = ""))
                                mainViewModel.clearNeedsSmtpSetup()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonRed),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonRed.copy(alpha = 0.5f)),
                        ) { Text(if (smtpSkipPermanent) "Lewati Permanen" else "Nanti", color = NeonRed) }
                        Button(
                            onClick = {
                                if (smtpEmail.isNotBlank() && smtpPass.isNotBlank()) {
                                    mainViewModel.saveSmtp(SmtpConfig(host = "smtp.gmail.com", port = 587, user = smtpEmail, pass = smtpPass))
                                    Toast.makeText(context, "Email tersimpan", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Isi email dan app password", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DarkBackground),
                        ) { Text("Simpan") }
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationPermissionDialog(onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var showDialog by remember { mutableStateOf(true) }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false; onDismiss() },
            containerColor = DarkSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            icon = { Icon(Icons.Filled.Notifications, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(40.dp)) },
            title = { Text("IZIN NOTIFIKASI", fontWeight = FontWeight.Bold, color = NeonCyan) },
            text = {
                Text(
                    "RR Billing Pro perlu izin notifikasi untuk memberi tahu kamu saat:\n\n" +
                    "• Lisensi aktif\n• Invoice dikonfirmasi\n• Pengumuman penting lainnya\n\n" +
                    "Izinkan notifikasi sekarang?",
                    color = TextSecondary,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        }
                        showDialog = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DarkBackground),
                    shape = RoundedCornerShape(10.dp),
                ) { Text("Izinkan") }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDialog = false; onDismiss() },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TextSecondary.copy(alpha = 0.5f)),
                ) { Text("Nanti") }
            },
        )
    }
}

enum class BottomTab(val title: String, val icon: ImageVector) {
    TV("TV", Icons.Filled.Tv),
    RIWAYAT("Riwayat", Icons.Filled.History),
    HARGA("Harga", Icons.Filled.AttachMoney),
    CLIENT("Client", Icons.Filled.Android),
    LISENSI("Lisensi", Icons.Filled.Verified),
    PROFILE("Profile", Icons.Filled.Person),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    tvViewModel: TvViewModel,
) {
    val mainState by mainViewModel.state.collectAsState()
    val role = mainState.currentRole

    val context = androidx.compose.ui.platform.LocalContext.current
    val pendingCount = mainState.pendingInvoiceCount
    val hasActiveTrial = mainState.trialBatas > 0L && mainState.trialBatas > System.currentTimeMillis()
    val isLocked = mainState.licenseStatus.status != "active" && !hasActiveTrial

    // Network status
    val connectivityObserver = remember { ConnectivityObserver(context.applicationContext as android.app.Application).also { it.start() } }
    val networkStatus by connectivityObserver.status.collectAsState()
    DisposableEffect(Unit) { onDispose { connectivityObserver.stop() } }
    val tabList = remember(role) {
        when (role) {
            "kasir" -> listOf(BottomTab.TV, BottomTab.RIWAYAT, BottomTab.LISENSI)
            else -> BottomTab.entries.toList()
        }
    }
    var selectedTab by remember { mutableStateOf(BottomTab.TV) }
    var showLockedDialog by remember { mutableStateOf(false) }
    val unlockTabs = listOf(BottomTab.RIWAYAT, BottomTab.LISENSI, BottomTab.PROFILE)

    LaunchedEffect(role) { selectedTab = BottomTab.TV }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                tabList.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = {
                            if (isLocked && tab !in unlockTabs) {
                                showLockedDialog = true
                            } else {
                                selectedTab = tab
                            }
                        },
                        icon = {
                            if (tab == BottomTab.LISENSI && pendingCount > 0) {
                                BadgedBox(badge = { Badge { Text("$pendingCount") } }) {
                                    Icon(tab.icon, contentDescription = tab.title)
                                }
                            } else {
                                Icon(tab.icon, contentDescription = tab.title)
                            }
                        },
                        label = { Text(tab.title, style = MaterialTheme.typography.labelSmall) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
                if (role == "kasir") {
                    NavigationBarItem(
                        selected = false,
                        onClick = { mainViewModel.logout() },
                        icon = { Icon(Icons.Filled.Logout, contentDescription = "Logout", tint = NeonRed) },
                        label = { Text("Keluar", style = MaterialTheme.typography.labelSmall, color = NeonRed) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = NeonRed.copy(alpha = 0.1f)),
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Network offline banner
            if (networkStatus == ConnectivityObserver.Status.UNAVAILABLE || networkStatus == ConnectivityObserver.Status.LOST) {
                Surface(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                    color = NeonRed.copy(alpha = 0.9f),
                    tonalElevation = 4.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.WifiOff, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Tidak ada koneksi internet", color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    }
                }
            }
            if (showLockedDialog) {
                AlertDialog(
                    onDismissRequest = { showLockedDialog = false },
                    containerColor = DarkSurface,
                    title = { Text("LISENSI TIDAK AKTIF", color = NeonRed, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                    text = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.Lock, contentDescription = null, tint = NeonRed, modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("Aktifkan lisensi terlebih dahulu melalui menu Profil > Aktivasi Lisensi", color = TextPrimary, textAlign = TextAlign.Center)
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { showLockedDialog = false; selectedTab = BottomTab.PROFILE },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DarkBackground),
                        ) { Text("Buka Profil") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showLockedDialog = false }) { Text("Tutup", color = TextDim) }
                    },
                )
            }
            when (selectedTab) {
                BottomTab.TV -> DashboardScreen(mainViewModel, tvViewModel)
                BottomTab.RIWAYAT -> RiwayatScreen(mainViewModel)
                BottomTab.HARGA -> KontrolHargaScreen(mainViewModel)
                BottomTab.CLIENT -> ClientScreen(mainViewModel)
                BottomTab.LISENSI -> VerifikasiScreen(mainViewModel)
                BottomTab.PROFILE -> ProfileScreen(mainViewModel, onLogout = { mainViewModel.logout() })
            }
        }
    }
}
