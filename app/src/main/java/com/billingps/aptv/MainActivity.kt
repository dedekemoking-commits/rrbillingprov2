package com.billingps.aptv

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.billingps.aptv.ui.screens.*
import com.billingps.aptv.ui.theme.*
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
            BillingPSTheme {
                AppRoot()
            }
        }
    }
}

@Composable
fun AppRoot(
    mainViewModel: MainViewModel = viewModel(),
    tvViewModel: TvViewModel = viewModel(),
) {
    val mainState by mainViewModel.state.collectAsStateWithLifecycle()
    var smtpEmail by remember { mutableStateOf("") }
    var smtpPass by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current

    if (!mainState.isLoggedIn) {
        LoginScreen(
            viewModel = mainViewModel,
            onLoginSuccess = { /* state akan berubah otomatis */ },
        )
    } else {
        MainScreen(mainViewModel, tvViewModel)
    }

    if (mainState.needsSmtpSetup) {
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
                    OutlinedTextField(
                        value = smtpPass,
                        onValueChange = { smtpPass = it },
                        label = { Text("App Password Gmail") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
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
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { mainViewModel.saveSmtp(SmtpConfig(host = "", port = 0, user = "", pass = "")); mainViewModel.clearNeedsSmtpSetup() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonRed),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonRed.copy(alpha = 0.5f)),
                        ) { Text("Nanti", color = NeonRed) }
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

enum class BottomTab(val title: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", Icons.Filled.Home),
    RIWAYAT("Riwayat", Icons.Filled.History),
    KONTROL_HARGA("Kontrol Harga", Icons.Filled.Star),
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

    val tabList = remember(role) {
        when (role) {
            "kasir" -> listOf(BottomTab.DASHBOARD, BottomTab.RIWAYAT)
            else -> BottomTab.entries.toList()
        }
    }
    var selectedTab by remember { mutableStateOf(BottomTab.DASHBOARD) }

    LaunchedEffect(role) { selectedTab = BottomTab.DASHBOARD }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                tabList.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
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
            when (selectedTab) {
                BottomTab.DASHBOARD -> DashboardScreen(mainViewModel, tvViewModel)
                BottomTab.RIWAYAT -> RiwayatScreen(mainViewModel)
                BottomTab.KONTROL_HARGA -> KontrolHargaScreen(mainViewModel)
                BottomTab.PROFILE -> ProfileScreen(mainViewModel, onLogout = { mainViewModel.logout() })
            }
        }
    }
}
