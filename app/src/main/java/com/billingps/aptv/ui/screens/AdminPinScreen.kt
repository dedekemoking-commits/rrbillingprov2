package com.billingps.aptv.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.billingps.aptv.models.MainViewModel
import com.billingps.aptv.ui.theme.DarkBackground
import com.billingps.aptv.ui.theme.NeonGreen
import com.billingps.aptv.ui.theme.TextDim
import com.billingps.aptv.ui.theme.TextPrimary
import com.billingps.aptv.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AdminPinSetupScreen(viewModel: MainViewModel) {
    AdminPinScreen(viewModel = viewModel, setupMode = true)
}

@Composable
fun AdminPinLockScreen(viewModel: MainViewModel) {
    AdminPinScreen(viewModel = viewModel, setupMode = false)
}

@Composable
private fun AdminPinScreen(
    viewModel: MainViewModel,
    setupMode: Boolean,
) {
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    fun submit() {
        if (busy) return
        val clean = pin.trim()
        if (!clean.matches(Regex("\\d{4,6}"))) {
            error = "PIN harus 4-6 digit angka."
            return
        }
        if (setupMode && clean != confirm.trim()) {
            error = "Konfirmasi PIN tidak cocok."
            return
        }
        busy = true
        error = ""
        scope.launch {
            if (setupMode) {
                val result = withContext(Dispatchers.Default) { viewModel.setAdminPin(clean) }
                if (result != null) error = result
            } else {
                val valid = withContext(Dispatchers.Default) { viewModel.verifyAdminPin(clean) }
                if (!valid) {
                    error = "PIN salah. Coba lagi."
                    pin = ""
                }
            }
            busy = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF020617), Color(0xFF0F172A)))),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = NeonGreen,
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                if (setupMode) "Buat PIN Admin" else "Masuk PIN Admin",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (setupMode) {
                    "PIN ini diminta saat masuk memakai Google di perangkat ini."
                } else {
                    "Aplikasi dikunci untuk akun ${state.currentUser}. Masukkan PIN untuk melanjutkan."
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                border = BorderStroke(1.dp, NeonGreen.copy(alpha = 0.25f)),
            ) {
                Column(Modifier.padding(20.dp)) {
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { value ->
                            pin = value.filter(Char::isDigit).take(6)
                            error = ""
                        },
                        label = { Text(if (setupMode) "PIN baru" else "PIN") },
                        singleLine = true,
                        enabled = !busy,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )
                    if (setupMode) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = confirm,
                            onValueChange = { value ->
                                confirm = value.filter(Char::isDigit).take(6)
                                error = ""
                            },
                            label = { Text("Konfirmasi PIN") },
                            singleLine = true,
                            enabled = !busy,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        )
                    }
                    if (error.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = ::submit,
                        enabled = !busy && pin.isNotBlank() && (!setupMode || confirm.isNotBlank()),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonGreen,
                            contentColor = DarkBackground,
                        ),
                    ) {
                        Text(if (busy) "Memproses..." else if (setupMode) "Simpan PIN" else "Buka Aplikasi")
                    }
                    Spacer(Modifier.height(6.dp))
                    TextButton(
                        onClick = { viewModel.logout() },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Keluar Akun", color = TextDim, fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "PIN 4-6 digit. Jangan berikan PIN kepada kasir.",
                style = MaterialTheme.typography.labelSmall,
                color = TextDim,
                textAlign = TextAlign.Center,
            )
        }
    }
}
