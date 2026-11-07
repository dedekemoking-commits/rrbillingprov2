package com.billingps.owner.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.billingps.owner.BuildConfig
import com.billingps.owner.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

@Composable
fun LoginScreen(
    savedEmail: String,
    savedUsername: String,
    unlockMode: Boolean,
    loading: Boolean,
    error: String?,
    onLogin: (String, String, String) -> Unit,
    onGoogleLogin: (String, String, String) -> Unit,
    onUnlock: (String) -> Unit,
    onReset: () -> Unit,
    onDismissError: () -> Unit,
) {
    var email by remember(savedEmail) { mutableStateOf(savedEmail) }
    var password by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var pinVisible by remember { mutableStateOf(false) }
    var googleError by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val googleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                .getResult(ApiException::class.java)
            val idToken = account.idToken
            val googleEmail = account.email.orEmpty()
            if (idToken.isNullOrBlank() || googleEmail.isBlank()) {
                googleError = "Google tidak mengembalikan token/email. Coba lagi."
            } else {
                googleError = null
                onGoogleLogin(idToken, googleEmail, pin)
            }
        } catch (e: ApiException) {
            googleError = if (e.statusCode == 12501) {
                "Login Google dibatalkan."
            } else {
                "Google Sign-In gagal (${e.statusCode})."
            }
        }
    }

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
                    .size(116.dp)
                    .background(Color.White, RoundedCornerShape(28.dp))
                    .padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.rr_owner_logo),
                    contentDescription = "RR Owner",
                    modifier = Modifier.fillMaxSize(),
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
                if (unlockMode) {
                    "Masukkan PIN untuk membuka aplikasi"
                } else {
                    "Masuk dengan akun Google admin RR Billing Pro"
                },
                color = OwnerMuted,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))

            if (error != null) {
                ErrorBanner(message = error, onDismiss = onDismissError)
                Spacer(Modifier.height(12.dp))
            }

            if (unlockMode) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = OwnerPanel),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            savedEmail,
                            color = OwnerText,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Rental: ${savedUsername.ifBlank { "-" }}",
                            color = OwnerMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = pin,
                    onValueChange = { value ->
                        pin = value.filter { it.isDigit() }.take(8)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("PIN Aplikasi") },
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
                    "PIN ini hanya tersimpan di perangkat ini.",
                    modifier = Modifier.fillMaxWidth(),
                    color = OwnerMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = { onUnlock(pin) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    enabled = !loading && pin.length >= 4,
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
                        Text("BUKA APLIKASI", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    }
                }

                Spacer(Modifier.height(10.dp))
                TextButton(onClick = onReset, enabled = !loading) {
                    Text("Lupa PIN / ganti rental", color = OwnerMuted)
                }
            } else {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { value ->
                        pin = value.filter { it.isDigit() }.take(8)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("PIN Aplikasi (4-8 digit)") },
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
                    "PIN baru dipakai untuk membuka aplikasi berikutnya. Minimal 4 digit.",
                    modifier = Modifier.fillMaxWidth(),
                    color = OwnerMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = {
                        googleError = null
                        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                            .requestIdToken(context.getString(R.string.default_web_client_id))
                            .requestEmail()
                            .build()
                        googleLauncher.launch(GoogleSignIn.getClient(context, options).signInIntent)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    enabled = !loading && pin.length >= 4,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF1F1F1F),
                    ),
                ) {
                    Text("MASUK DENGAN GOOGLE", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }
                if (googleError != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        googleError.orEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        color = OwnerRed,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Login sekali di aplikasi billing dengan akun ini agar akses owner aktif.",
                    modifier = Modifier.fillMaxWidth(),
                    color = OwnerMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    "ATAU MASUK DENGAN EMAIL & PASSWORD",
                    modifier = Modifier.fillMaxWidth(),
                    color = OwnerMuted,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Email Owner") },
                    leadingIcon = {
                        Icon(Icons.Default.Email, contentDescription = null, tint = OwnerPurpleLight)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    enabled = !loading,
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Password") },
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = OwnerPurpleLight)
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                tint = OwnerMuted,
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    enabled = !loading,
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = { onLogin(email, password, pin) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    enabled = !loading &&
                        email.isNotBlank() &&
                        password.isNotBlank() &&
                        pin.length >= 4,
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
                        Text("MASUK DENGAN EMAIL", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    }
                }

                if (savedEmail.isNotBlank() || savedUsername.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = onReset, enabled = !loading) {
                        Text("Ganti / reset rental", color = OwnerMuted)
                    }
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
                    "Login: Firebase",
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
