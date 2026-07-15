package com.billingps.aptv.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.billingps.aptv.models.MainViewModel
import com.billingps.aptv.ui.theme.*
import kotlinx.coroutines.launch
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun LoginScreen(
    viewModel: MainViewModel,
    onLoginSuccess: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var attempts by remember { mutableIntStateOf(0) }
    var lockedUntil by remember { mutableLongStateOf(0L) }
    var errMsg by remember { mutableStateOf("") }
    var showReg by remember { mutableStateOf(false) }
    var regUser by remember { mutableStateOf("") }
    var regPass by remember { mutableStateOf("") }
    var regEmail by remember { mutableStateOf("") }
    var regMsg by remember { mutableStateOf("") }
    var regMsgOk by remember { mutableStateOf(false) }
    var regLoading by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var showForgot by remember { mutableStateOf(false) }
    var forgotStep by remember { mutableIntStateOf(0) } // 0=email input, 1=show code & reset
    var forgotInput by remember { mutableStateOf("") }
    var forgotCode by remember { mutableStateOf("") }
    var forgotNewPass by remember { mutableStateOf("") }
    var forgotMsg by remember { mutableStateOf("") }
    var forgotMsgOk by remember { mutableStateOf(false) }
    var forgotLoading by remember { mutableStateOf(false) }
    var forgotCodeDisplay by remember { mutableStateOf("") }
    var showKasirLogin by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var selectedKasir by remember { mutableStateOf("") }
    var kasirPass by remember { mutableStateOf("") }
    var kasirErr by remember { mutableStateOf("") }

    val lic = state.licenseStatus
    val showLicense = lic.status == "active" || lic.status == "trial"
    val licColor = if (lic.status == "active") Color(0xFF39FF14) else Color(0xFFFFEA00)
    val licMsg = if (lic.status == "active") "Lisensi Aktif" else lic.pesan

    val loginGradient = Brush.verticalGradient(colors = listOf(Color(0xFFF0FDF4), Color(0xFF00C9A7)))

    Box(modifier = Modifier.fillMaxSize().background(loginGradient)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Top section: logo + title + license
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = androidx.compose.ui.res.painterResource(com.billingps.aptv.R.drawable.logo_transparant),
                    contentDescription = "Logo",
                    modifier = Modifier.size(180.dp),
                )

                Spacer(Modifier.height(2.dp))

                Text(
                    "Sistem Billing Rental TV & PS",
                    style = MaterialTheme.typography.titleLarge.copy(
                        shadow = Shadow(color = Color(0xFF001B3D).copy(alpha = 0.6f), blurRadius = 12f),
                        brush = Brush.horizontalGradient(colors = listOf(Color.Black, Color(0xFF001B3D)))
                    )
                )

                if (showLicense) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        licMsg,
                        style = MaterialTheme.typography.labelSmall,
                        color = licColor,
                    )
                }
            }

            // Middle: login card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, NeonGreen.copy(alpha = 0.3f)),
            ) {
                Column(Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it; errMsg = "" },
                        placeholder = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonGreen, unfocusedBorderColor = DarkSurfaceV3,
                            cursorColor = NeonGreen, focusedLabelColor = NeonGreen,
                            unfocusedContainerColor = DarkSurfaceV2, focusedContainerColor = DarkSurfaceV2,
                        ),
                    )

                    Spacer(Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it; errMsg = "" },
                            placeholder = { Text("Password") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonGreen, unfocusedBorderColor = DarkSurfaceV3,
                                cursorColor = NeonGreen, focusedLabelColor = NeonGreen,
                                unfocusedContainerColor = DarkSurfaceV2, focusedContainerColor = DarkSurfaceV2,
                            ),
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = { showPass = !showPass }) {
                            Icon(
                                if (showPass) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = "Toggle password",
                                tint = TextSecondary,
                            )
                        }
                    }

                    if (errMsg.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(errMsg, style = MaterialTheme.typography.bodySmall, color = NeonRed, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }

                    Spacer(Modifier.height(10.dp))

                    Button(
                        onClick = {
                            if (username.isBlank() || password.isBlank()) {
                                errMsg = "Isi username & password"; return@Button
                            }
                            if (lockedUntil > System.currentTimeMillis()) {
                                val sisa = ((lockedUntil - System.currentTimeMillis()) / 1000).toInt()
                                errMsg = "Terkunci ${sisa}s"; return@Button
                            }
                            loading = true
                            errMsg = ""
                           scope.launch {
                                val result = viewModel.loginWithResult(username, password)
                                loading = false
                                if (result.success) {
                                    attempts = 0
                                    onLoginSuccess()
                                } else {
                                    attempts++
                                    if (attempts >= 5) {
                                        lockedUntil = System.currentTimeMillis() + 60000
                                        errMsg = "5x salah - terkunci 1 menit"
                                    } else {
                                        errMsg = result.message.ifBlank { "Username/Password salah ($attempts/5)" }
                                    }
                                }
                            }
                        },
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = DarkBackground),
                    ) {
                        if (loading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = DarkBackground, strokeWidth = 2.dp)
                        } else {
                            Text("MASUK", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    TextButton(onClick = { showReg = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Belum punya akun? Daftar di sini", style = MaterialTheme.typography.bodySmall, color = NeonGreen)
                    }

                    TextButton(onClick = { showForgot = true; forgotInput = ""; forgotCode = ""; forgotNewPass = ""; forgotMsg = ""; forgotStep = 0; forgotCodeDisplay = "" }, modifier = Modifier.fillMaxWidth()) {
                        Text("Lupa password? Reset di sini", style = MaterialTheme.typography.bodySmall, color = NeonCyan)
                    }

                    OutlinedButton(
                        onClick = { showKasirLogin = true },
                        modifier = Modifier.fillMaxWidth().height(36.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan),
                        contentPadding = PaddingValues(0.dp),
                    ) { Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Login Kasir", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall) }
                }
            }

            // Bottom: version
            Text(
                "RR BILLING PRO v${state.appVersionName}",
                style = MaterialTheme.typography.bodySmall,
                color = TextDim,
            )
        }

        if (showReg) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xCC000000)),
                contentAlignment = Alignment.Center,
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonGreen.copy(alpha = 0.3f)),
                ) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("DAFTAR AKUN BARU", style = MaterialTheme.typography.titleLarge, color = NeonGreen)
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(value = regUser, onValueChange = { regUser = it; regMsg = "" }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = fieldColors())
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = regPass, onValueChange = { regPass = it; regMsg = "" }, label = { Text("Password (min 6, huruf besar & angka)") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = fieldColors())
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = regEmail, onValueChange = { regEmail = it }, label = { Text("Email (wajib untuk verifikasi)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = fieldColors())
                        if (regMsg.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(regMsg, style = MaterialTheme.typography.bodySmall, color = if (regMsgOk) NeonGreen else NeonRed, textAlign = TextAlign.Center)
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = {
                            if (regUser.isBlank() || regPass.length < 6) { regMsg = "Username & password minimal 6 karakter"; regMsgOk = false; return@Button }
                            if (!regPass.any { it.isUpperCase() }) { regMsg = "Password harus mengandung huruf besar"; regMsgOk = false; return@Button }
                            if (!regPass.any { it.isDigit() }) { regMsg = "Password harus mengandung angka"; regMsgOk = false; return@Button }
                            if (regEmail.isBlank()) { regMsg = "Email wajib diisi untuk verifikasi"; regMsgOk = false; return@Button }
                            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(regEmail).matches()) { regMsg = "Format email tidak valid"; regMsgOk = false; return@Button }
                            regMsg = "Memproses..."
                            regMsgOk = false
                            regLoading = true
                            scope.launch {
                                if (viewModel.cekUsername(regUser)) {
                                    regMsg = "Username sudah terdaftar"
                                    regMsgOk = false
                                } else {
                                    val result = viewModel.daftarUser(regUser, regPass, regEmail, "admin")
                                    regMsg = result.message
                                    regMsgOk = result.success
                                    if (result.success) {
                                        regUser = ""; regPass = ""; regEmail = ""
                                        showReg = false
                                    }
                                }
                                regLoading = false
                            }
                        }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = DarkBackground)) {
                            if (regLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = DarkBackground, strokeWidth = 2.dp)
                            } else {
                                Text("Daftar")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { showReg = false; regMsg = "" }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonRed)) {
                            Text("Tutup", color = NeonRed)
                        }
                    }
                }
            }
        }

        if (showForgot) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xCC000000)),
                contentAlignment = Alignment.Center,
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.3f)),
                ) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (forgotStep == 0) {
                            Text("RESET PASSWORD", style = MaterialTheme.typography.titleLarge, color = NeonCyan)
                            Spacer(Modifier.height(12.dp))
                            Text("Masukkan username atau email akun Anda.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(value = forgotInput, onValueChange = { forgotInput = it; forgotMsg = "" }, label = { Text("Username / Email") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = fieldColors())
                            if (forgotMsg.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text(forgotMsg, style = MaterialTheme.typography.bodySmall, color = if (forgotMsgOk) NeonGreen else NeonRed, textAlign = TextAlign.Center)
                            }
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = {
                                if (forgotInput.isBlank()) { forgotMsg = "Masukkan username atau email"; forgotMsgOk = false; return@Button }
                                forgotLoading = true
                                forgotMsg = ""
                                val result = viewModel.generateResetCode(forgotInput)
                                forgotMsg = result.message
                                forgotMsgOk = result.success
                                if (result.success) {
                                    forgotCodeDisplay = result.message.replace("Kode verifikasi Anda: ", "")
                                    forgotStep = 1
                                    forgotMsg = ""
                                }
                                forgotLoading = false
                            }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DarkBackground)) {
                                if (forgotLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = DarkBackground, strokeWidth = 2.dp)
                                } else {
                                    Text("Generate Kode")
                                }
                            }
                        } else {
                            Text("RESET PASSWORD", style = MaterialTheme.typography.titleLarge, color = NeonCyan)
                            Spacer(Modifier.height(8.dp))
                            if (state.pendingResetCode.isNotEmpty()) {
                                Text("Kode reset Anda: ${state.pendingResetCode}", style = MaterialTheme.typography.titleMedium, color = NeonGreen, fontWeight = FontWeight.Bold)
                                Text("(Jika email tidak masuk, gunakan kode di atas)", style = MaterialTheme.typography.bodySmall, color = TextDim)
                            } else {
                                Text("Kode verifikasi telah dikirim ke email Anda.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                Spacer(Modifier.height(4.dp))
                                Text("Cek email Anda (termasuk folder Spam)", style = MaterialTheme.typography.bodySmall, color = TextDim)
                            }
                            Spacer(Modifier.height(16.dp))
                            OutlinedTextField(value = forgotCode, onValueChange = { forgotCode = it; forgotMsg = "" }, label = { Text("Kode Verifikasi") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = fieldColors())
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(value = forgotNewPass, onValueChange = { forgotNewPass = it; forgotMsg = "" }, label = { Text("Password Baru (min 6, huruf besar & angka)") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = fieldColors())
                            if (forgotMsg.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text(forgotMsg, style = MaterialTheme.typography.bodySmall, color = if (forgotMsgOk) NeonGreen else NeonRed, textAlign = TextAlign.Center)
                            }
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = {
                                if (forgotCode.length != 6) { forgotMsg = "Kode harus 6 digit"; forgotMsgOk = false; return@Button }
                                if (forgotNewPass.length < 6) { forgotMsg = "Password minimal 6 karakter"; forgotMsgOk = false; return@Button }
                                if (!forgotNewPass.any { it.isUpperCase() }) { forgotMsg = "Password harus mengandung huruf besar"; forgotMsgOk = false; return@Button }
                                if (!forgotNewPass.any { it.isDigit() }) { forgotMsg = "Password harus mengandung angka"; forgotMsgOk = false; return@Button }
                                forgotLoading = true
                                forgotMsg = ""
                                val result = viewModel.resetPasswordWithCode(forgotInput, forgotCode, forgotNewPass)
                                forgotMsg = result.message
                                forgotMsgOk = result.success
                                forgotLoading = false
                                if (result.success) {
                                    forgotStep = 0; forgotInput = ""; forgotCode = ""; forgotNewPass = ""; forgotCodeDisplay = ""
                                }
                            }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = DarkBackground)) {
                                if (forgotLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = DarkBackground, strokeWidth = 2.dp)
                                } else {
                                    Text("Reset Password")
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            TextButton(onClick = {
                                val result = viewModel.generateResetCode(forgotInput)
                                if (result.success) {
                                    forgotMsg = "Kode baru telah dikirim ke email"
                                    forgotMsgOk = true
                                } else {
                                    forgotMsg = result.message
                                    forgotMsgOk = false
                                }
                            }) {
                                Text("Kirim ulang kode", style = MaterialTheme.typography.bodySmall, color = NeonCyan)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { showForgot = false; forgotStep = 0; forgotInput = ""; forgotCode = ""; forgotNewPass = ""; forgotMsg = ""; forgotCodeDisplay = "" }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonRed)) {
                            Text("Tutup", color = NeonRed)
                        }
                    }
                }
            }
        }

        // Verification Code Dialog
        val pendingUser = state.pendingVerifyUser
        if (pendingUser.isNotEmpty()) {
            var verCode by remember { mutableStateOf("") }
            var verMsg by remember { mutableStateOf("") }
            var verOk by remember { mutableStateOf(false) }
            var codeResent by remember { mutableStateOf(false) }
            var resendLoading by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xCC000000)),
                contentAlignment = Alignment.Center,
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.3f)),
                ) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("VERIFIKASI EMAIL", style = MaterialTheme.typography.titleLarge, color = NeonCyan)
                        Spacer(Modifier.height(8.dp))
                        Text("Masukkan kode verifikasi yang dikirim ke email Anda.", style = MaterialTheme.typography.bodySmall, color = TextSecondary, textAlign = TextAlign.Center)
                        if (state.pendingVerifyCode.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text("Kode verifikasi Anda: ${state.pendingVerifyCode}", style = MaterialTheme.typography.titleMedium, color = NeonGreen, fontWeight = FontWeight.Bold)
                            Text("(Jika email tidak masuk, gunakan kode di atas)", style = MaterialTheme.typography.bodySmall, color = TextDim)
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(value = verCode, onValueChange = { verCode = it; verMsg = "" }, label = { Text("Kode Verifikasi") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = fieldColors())
                        if (verMsg.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text(verMsg, style = MaterialTheme.typography.bodySmall, color = if (verOk) NeonGreen else NeonRed, textAlign = TextAlign.Center)
                        }
                        if (codeResent) {
                            Spacer(Modifier.height(4.dp))
                            Text("Kode baru telah dikirim ke email!", style = MaterialTheme.typography.bodySmall, color = NeonGreen, textAlign = TextAlign.Center)
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = {
                            if (verCode.length != 6 || !verCode.all { it.isDigit() }) {
                                verMsg = "Masukkan 6 digit kode"; verOk = false; return@Button
                            }
                            val ok = viewModel.verifyEmailCode(pendingUser, verCode)
                            if (ok) {
                                verMsg = "Email berhasil diverifikasi! Silakan login kembali."
                                verOk = true
                                codeResent = false
                            } else {
                                verMsg = "Kode verifikasi salah"
                                verOk = false
                            }
                        }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DarkBackground)) { Text("Verifikasi") }
                        Spacer(Modifier.height(6.dp))
                        TextButton(
                            onClick = {
                                resendLoading = true
                                viewModel.resendCode(pendingUser)
                                codeResent = true
                                resendLoading = false
                            },
                            enabled = !resendLoading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (resendLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = NeonCyan, strokeWidth = 2.dp)
                            } else {
                                Text("Kirim ulang kode", style = MaterialTheme.typography.bodySmall, color = NeonCyan)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        TextButton(onClick = {
                            viewModel.clearPendingVerify()
                            verCode = ""; verMsg = ""; verOk = false; codeResent = false
                        }, modifier = Modifier.fillMaxWidth()) { Text("Tutup", color = NeonRed) }
                    }
                }
            }
        }

        if (showKasirLogin) {
            val kasirList = viewModel.getKasirList()
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xCC000000)),
                contentAlignment = Alignment.Center,
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.3f)),
                ) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("LOGIN KASIR", style = MaterialTheme.typography.titleLarge, color = NeonCyan)
                        Spacer(Modifier.height(16.dp))
                        if (kasirList.isEmpty()) {
                            Text("Belum ada akun kasir", style = MaterialTheme.typography.bodySmall, color = TextDim)
                        } else {
                            Text("Pilih Kasir:", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                            Spacer(Modifier.height(8.dp))
                            Column {
                                kasirList.forEach { (name, _) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clickable { selectedKasir = if (selectedKasir == name) "" else name }.padding(vertical = 6.dp, horizontal = 8.dp).background(if (selectedKasir == name) NeonGreen.copy(0.1f) else Color.Transparent, RoundedCornerShape(8.dp)),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        RadioButton(selected = selectedKasir == name, onClick = { selectedKasir = name }, colors = RadioButtonDefaults.colors(selectedColor = NeonCyan))
                                        Spacer(Modifier.width(8.dp))
                                        Text(name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                                    }
                                }
                            }
                            if (selectedKasir.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                OutlinedTextField(value = kasirPass, onValueChange = { kasirPass = it; kasirErr = "" }, label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = fieldColors())
                            }
                            if (kasirErr.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                Text(kasirErr, style = MaterialTheme.typography.bodySmall, color = NeonRed)
                            }
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = {
                                if (selectedKasir.isEmpty()) return@Button
                                val ok = viewModel.loginKasir(selectedKasir, kasirPass)
                                if (ok) {
                                    selectedKasir = ""; kasirPass = ""; kasirErr = ""
                                    showKasirLogin = false
                                    onLoginSuccess()
                                } else {
                                    kasirErr = "Password salah"
                                }
                            }, modifier = Modifier.fillMaxWidth(), enabled = selectedKasir.isNotEmpty() && kasirPass.length >= 4, shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DarkBackground)) {
                                Text("Login Kasir")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { showKasirLogin = false; selectedKasir = ""; kasirPass = ""; kasirErr = "" }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonRed)) {
                            Text("Tutup", color = NeonRed)
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = NeonGreen, unfocusedBorderColor = DarkSurfaceV3,
    cursorColor = NeonGreen, focusedLabelColor = NeonGreen,
    unfocusedContainerColor = DarkSurfaceV2, focusedContainerColor = DarkSurfaceV2,
)

@Preview(showBackground = true, device = "id:pixel_5", backgroundColor = 0xFF0A0A0A)
@Composable
fun LoginScreenPreview() {
    BillingPSTheme {
        val loginGradient = Brush.verticalGradient(colors = listOf(Color(0xFFF0FDF4), Color(0xFF00C9A7)))
        Box(modifier = Modifier.fillMaxSize().background(loginGradient)) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = androidx.compose.ui.res.painterResource(com.billingps.aptv.R.drawable.logo_transparant),
                        contentDescription = "Logo",
                        modifier = Modifier.size(180.dp),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Sistem Billing Rental TV & PS",
                        style = MaterialTheme.typography.titleLarge.copy(
                            shadow = Shadow(color = NeonCyan.copy(alpha = 0.5f), blurRadius = 12f)
                        ),
                        color = NeonCyan,
                    )
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonGreen.copy(alpha = 0.3f)),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            value = "",
                            onValueChange = {},
                            placeholder = { Text("Username") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonGreen, unfocusedBorderColor = DarkSurfaceV3,
                                cursorColor = NeonGreen, focusedLabelColor = NeonGreen,
                                unfocusedContainerColor = DarkSurfaceV2, focusedContainerColor = DarkSurfaceV2,
                            ),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = "",
                            onValueChange = {},
                            placeholder = { Text("Password") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = PasswordVisualTransformation(),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonGreen, unfocusedBorderColor = DarkSurfaceV3,
                                cursorColor = NeonGreen, focusedLabelColor = NeonGreen,
                                unfocusedContainerColor = DarkSurfaceV2, focusedContainerColor = DarkSurfaceV2,
                            ),
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth().height(42.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = DarkBackground),
                        ) {
                            Text("MASUK", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("Belum punya akun? Daftar di sini", style = MaterialTheme.typography.bodySmall, color = NeonGreen, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().clickable {})
                        Spacer(Modifier.height(2.dp))
                        Text("Lupa password? Reset di sini", style = MaterialTheme.typography.bodySmall, color = NeonCyan, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().clickable {})
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth().height(36.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan),
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Login Kasir", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Text(
                    "RR BILLING PRO v1.0.8",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDim,
                )
            }
        }
    }
}
