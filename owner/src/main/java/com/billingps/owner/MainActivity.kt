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
                val localTimer by vm.localTimer.collectAsStateWithLifecycle()

                if (!state.isLoggedIn) {
                    LoginScreen(
                        savedEmail = state.savedEmail,
                        savedUsername = state.savedUsername,
                        unlockMode = state.unlockMode,
                        loading = state.isLoading,
                        error = state.error,
                        onLogin = vm::login,
                        onGoogleLogin = vm::loginWithGoogle,
                        onUnlock = vm::unlock,
                        onReset = vm::logout,
                        onDismissError = vm::clearError,
                    )
                } else {
                    OwnerMainScreen(
                        state = state,
                        localTimer = localTimer,
                        onRefresh = { vm.refreshTransactions(true) },
                        onPeriodChange = vm::setPeriod,
                        onSearchChange = vm::setSearch,
                        onSubmitOpname = vm::submitStockOpname,
                        onClearOpnameNotice = vm::clearOpnameNotice,
                        onSubmitCashRequest = vm::submitCashDrawerRequest,
                        onClearCashRequestNotice = vm::clearCashRequestNotice,
                        onSubmitFnbItem = vm::submitFnbItem,
                        onClearFnbItemNotice = vm::clearFnbItemNotice,
                        onSubmitPackage = vm::submitPackage,
                        onClearPackageNotice = vm::clearPackageNotice,
                        onSubmitGroup = vm::submitGroup,
                        onClearGroupNotice = vm::clearGroupNotice,
                        onLogout = vm::logout,
                        onDismissError = vm::clearError,
                    )
                }
            }
        }
    }
}
