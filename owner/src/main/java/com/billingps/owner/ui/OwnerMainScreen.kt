package com.billingps.owner.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddBusiness
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.billingps.owner.R
import com.billingps.owner.data.FnbItemInput
import com.billingps.owner.data.GroupInput
import com.billingps.owner.data.PackageInput
import com.billingps.owner.data.ReportPeriod
import com.billingps.owner.data.StockOpnameItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerMainScreen(
    state: OwnerUiState,
    localTimer: Map<String, Long>,
    onRefresh: () -> Unit,
    onPeriodChange: (ReportPeriod) -> Unit,
    onSearchChange: (String) -> Unit,
    onSubmitOpname: (List<StockOpnameItem>, String) -> Unit,
    onClearOpnameNotice: () -> Unit,
    onSubmitCashRequest: (Int, String) -> Unit,
    onClearCashRequestNotice: () -> Unit,
    onSubmitFnbItem: (FnbItemInput) -> Unit,
    onClearFnbItemNotice: () -> Unit,
    onSubmitPackage: (PackageInput) -> Unit,
    onClearPackageNotice: () -> Unit,
    onSubmitGroup: (GroupInput) -> Unit,
    onClearGroupNotice: () -> Unit,
    onLogout: () -> Unit,
    onDismissError: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val title = state.rental.namaRental.ifBlank {
        state.username.ifBlank { "RR Owner" }
    }

    Scaffold(
        containerColor = OwnerBg,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(Color.White, RoundedCornerShape(9.dp))
                                    .padding(3.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.rr_owner_logo),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            Spacer(Modifier.size(10.dp))
                            Column {
                                Text(
                                    title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                    color = OwnerText,
                                )
                                Text(
                                    "Owner • ${state.username}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OwnerMuted,
                                )
                            }
                        }
                    },
                    actions = {
                        RefreshIconButton(refreshing = state.isRefreshing, onClick = onRefresh)
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = OwnerBg,
                        titleContentColor = OwnerText,
                        actionIconContentColor = OwnerGreen,
                    ),
                )
                if (state.isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = OwnerGreen,
                        trackColor = OwnerBorder,
                    )
                }
                HorizontalDivider(color = OwnerBorder)
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = OwnerPanel,
                modifier = Modifier.navigationBarsPadding(),
                windowInsets = WindowInsets(0, 0, 0, 0),
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Tv, contentDescription = null) },
                    label = { Text("TV") },
                    colors = navColors(),
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Assessment, contentDescription = null) },
                    label = { Text("Laporan") },
                    colors = navColors(),
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Inventory2, contentDescription = null) },
                    label = { Text("Stok") },
                    colors = navColors(),
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null) },
                    label = { Text("Kas") },
                    colors = navColors(),
                )
                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    icon = { Icon(Icons.Default.AddBusiness, contentDescription = null) },
                    label = { Text("Kelola") },
                    colors = navColors(),
                )
                NavigationBarItem(
                    selected = selectedTab == 5,
                    onClick = { selectedTab = 5 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Setelan") },
                    colors = navColors(),
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (state.error != null) {
                    ErrorBanner(
                        message = state.error,
                        onRetry = onRefresh,
                        onDismiss = onDismissError,
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        0 -> TvStatusScreen(
                            state = state,
                            localTimer = localTimer,
                            onRefresh = onRefresh,
                        )
                        1 -> ReportsScreen(
                            state = state,
                            onPeriodChange = onPeriodChange,
                            onSearchChange = onSearchChange,
                        )
                        2 -> StockScreen(
                            state = state,
                            onSubmit = onSubmitOpname,
                            onClearNotice = onClearOpnameNotice,
                        )
                        3 -> CashScreen(
                            state = state,
                            onSubmit = onSubmitCashRequest,
                            onClearNotice = onClearCashRequestNotice,
                        )
                        4 -> ManageScreen(
                            state = state,
                            onSubmitFnbItem = onSubmitFnbItem,
                            onClearFnbItemNotice = onClearFnbItemNotice,
                            onSubmitPackage = onSubmitPackage,
                            onClearPackageNotice = onClearPackageNotice,
                            onSubmitGroup = onSubmitGroup,
                            onClearGroupNotice = onClearGroupNotice,
                        )
                        else -> SettingsScreen(
                            state = state,
                            onRefresh = onRefresh,
                            onLogout = onLogout,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun navColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = OwnerGreen,
    selectedTextColor = OwnerGreen,
    indicatorColor = OwnerGreen.copy(alpha = 0.15f),
    unselectedIconColor = OwnerMuted,
    unselectedTextColor = OwnerMuted,
)
