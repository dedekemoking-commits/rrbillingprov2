package com.billingps.owner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.billingps.owner.ui.LoginScreen
import com.billingps.owner.ui.OwnerMainScreen
import com.billingps.owner.ui.OwnerTheme
import com.billingps.owner.ui.OwnerViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OwnerTheme {
                val vm: OwnerViewModel = viewModel()
                val state by vm.state.collectAsStateWithLifecycle()

                if (!state.isLoggedIn) {
                    LoginScreen(
                        savedUsername = state.savedUsername,
                        pinRequired = state.pinRequired,
                        loading = state.isLoading,
                        error = state.error,
                        onLogin = vm::login,
                        onReset = vm::logout,
                        onDismissError = vm::clearError,
                    )
                } else {
                    OwnerMainScreen(
                        state = state,
                        onRefresh = { vm.refreshTransactions(true) },
                        onPeriodChange = vm::setPeriod,
                        onSearchChange = vm::setSearch,
                        onLogout = vm::logout,
                        onDismissError = vm::clearError,
                    )
                }
            }
        }
    }
}
