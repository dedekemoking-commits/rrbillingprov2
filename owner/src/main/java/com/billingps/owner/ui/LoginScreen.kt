package com.billingps.owner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.billingps.owner.BuildConfig

@Composable
fun LoginScreen(
    savedUsername: String,
    pinRequired: Boolean,
    loading: Boolean,
    error: String?,
    onLogin: (String, String) -> Unit,
    onReset: () -> Unit,
    onDismissError: () -> Unit,
) {
    var username by remember(savedUsername) { mutableStateOf(savedUsername) }
    var pin by remember { mutableStateOf("") }
    var pinVisible by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(OwnerBg, Color(0xFF0F1830), OwnerBg),
                ),
            )
            .systemBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .background(OwnerGreen.copy(alpha = 0.12f), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Tv,
                    contentDescription = null,
                    tint = OwnerGreen,
                    modifier = Modifier.size(46.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "RR BILLING OWNER",
                color = OwnerGreen,
                fontWeight = FontWeight.Black,
                fontSize = 22.sp,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Monitor kartu TV, client, dan laporan rental",
                color = OwnerMuted,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))

            if (error != null) {
                ErrorBanner(message = error, onDismiss = onDismissError)
                Spacer(Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = username,
                onValueChange = { if (!pinRequired) username = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Username Rental") },
                leadingIcon = {
                    Icon(Icons.Default.Store, contentDescription = null, tint = OwnerPurpleLight)
                },
                singleLine = true,
                enabled = !pinRequired && !loading,
                shape = RoundedCornerShape(12.dp),
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = pin,
                onValueChange = { value ->
                    pin = value.filter { it.isDigit() }.take(8)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (pinRequired) "PIN Owner" else "PIN Owner (opsional)") },
                leadingIcon = {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = OwnerPurpleLight)
                },
                trailingIcon = {
                    IconButton(onClick = { pinVisible = !pinVisible }) {
                        Icon(
                            if (pinVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null,
                            tint = OwnerMuted,
                        )
                    }
                },
                visualTransformation = if (pinVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                enabled = !loading,
                shape = RoundedCornerShape(12.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (pinRequired) {
                    "Masukkan PIN yang sudah dibuat untuk membuka data rental."
                } else {
                    "PIN opsional. Jika diisi, PIN diperlukan saat aplikasi dibuka lagi."
                },
                modifier = Modifier.fillMaxWidth(),
                color = OwnerMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(20.dp))

            Button(
                onClick = { onLogin(username, pin) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = !loading,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OwnerGreen,
                    contentColor = Color(0xFF06281D),
                ),
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF06281D),
                    )
                } else {
                    Text("MASUK", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }
            }

            if (savedUsername.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = onReset, enabled = !loading) {
                    Text("Ganti / reset rental", color = OwnerMuted)
                }
            }

            Spacer(Modifier.height(26.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Data TV: Firestore",
                    color = OwnerMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("  •  ", color = OwnerBorder)
                Text(
                    "Laporan: Supabase",
                    color = OwnerMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "v${BuildConfig.VERSION_NAME}  •  Waktu WIB",
                color = OwnerBorder,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
