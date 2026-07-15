package com.billingps.aptv

import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.billingps.aptv.models.MainViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.billingps.aptv.ui.screens.*
import com.billingps.aptv.ui.theme.*
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        try {
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(this))
            }
        } catch (e: Exception) {
            Log.e("BillingPS", "Python start failed", e)
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

    if (!mainState.isLoggedIn) {
        LoginScreen(
            viewModel = mainViewModel,
            onLoginSuccess = { /* state akan berubah otomatis */ },
        )
    } else {
        MainScreen(mainViewModel, tvViewModel)
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
