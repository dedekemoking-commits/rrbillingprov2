package com.billingps.owner.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.billingps.owner.data.ReportPeriod

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerMainScreen(
    state: OwnerUiState,
    onRefresh: () -> Unit,
    onPeriodChange: (ReportPeriod) -> Unit,
    onSearchChange: (String) -> Unit,
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
                    label = { Text("Status TV") },
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
                        0 -> TvStatusScreen(state = state, onRefresh = onRefresh)
                        1 -> ReportsScreen(
                            state = state,
                            onPeriodChange = onPeriodChange,
                            onSearchChange = onSearchChange,
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
