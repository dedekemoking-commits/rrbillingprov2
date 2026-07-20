package com.billingps.aptv.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.billingps.aptv.models.*
import com.billingps.aptv.ui.theme.*
import com.billingps.aptv.ui.theme.ThemeOption
import kotlinx.coroutines.launch
import android.util.Log

@Composable
fun ProfileScreen(
    viewModel: MainViewModel,
    onLogout: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val ctx = LocalContext.current

    var newPass by remember { mutableStateOf("") }
    var tvPass by remember { mutableStateOf("") }
    var tvPassConfirm by remember { mutableStateOf("") }
    var tvPassMsg by remember { mutableStateOf("") }
    var tvPassMsgOk by remember { mutableStateOf(false) }

    var regUser by remember { mutableStateOf("") }
    var regPass by remember { mutableStateOf("") }
    var regRole by remember { mutableStateOf("kasir") }
    var regEmail by remember { mutableStateOf("") }
    var regMsg by remember { mutableStateOf("") }
    var regMsgOk by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()



    Column(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(DarkSurface).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("PROFIL & AKTIVASI", style = MaterialTheme.typography.titleLarge, color = NeonGreen)
            var showLogoutDialog by remember { mutableStateOf(false) }
            OutlinedButton(
                onClick = { showLogoutDialog = true },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonRed),
                border = BorderStroke(1.dp, NeonRed),
            ) { Icon(Icons.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Keluar") }
            if (showLogoutDialog) {
                AlertDialog(
                    onDismissRequest = { showLogoutDialog = false },
                    containerColor = DarkSurface,
                    title = { Text("KONFIRMASI KELUAR", fontWeight = FontWeight.Bold, color = NeonRed) },
                    text = { Text("Apakah Anda yakin ingin keluar? Data yang belum tersimpan akan hilang.", color = TextSecondary) },
                    confirmButton = {
                        Button(
                            onClick = { showLogoutDialog = false; onLogout() },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonRed, contentColor = DarkBackground),
                            shape = RoundedCornerShape(10.dp),
                        ) { Text("Keluar") }
                    },
                    dismissButton = {
                        OutlinedButton(
                            onClick = { showLogoutDialog = false },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                            border = BorderStroke(1.dp, TextSecondary.copy(alpha = 0.5f)),
                        ) { Text("Batal") }
                    },
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Profile Card
            ProfileHeader(state.currentUser, state.currentRole, state.appVersionName)



            // Change Password
            if (state.currentRole == "admin") {
                var newPassVisible by remember { mutableStateOf(false) }
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = DarkSurface), border = BorderStroke(1.dp, DarkSurfaceV3)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("GANTI PASSWORD", style = MaterialTheme.typography.labelLarge, color = NeonCyan)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = newPass, onValueChange = { newPass = it }, label = { Text("Password baru (min 6)") }, singleLine = true, visualTransformation = if (newPassVisible) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { IconButton(onClick = { newPassVisible = !newPassVisible }) { Icon(if (newPassVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null, tint = TextSecondary) } }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = fieldColors())
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            if (newPass.length < 6) { Toast.makeText(ctx, "Minimal 6 karakter", Toast.LENGTH_SHORT).show(); return@Button }
                            if (viewModel.gantiPassword(state.currentUser, newPass)) { Toast.makeText(ctx, "Password berhasil diubah", Toast.LENGTH_SHORT).show(); newPass = "" }
                            else { Toast.makeText(ctx, "Gagal mengubah password", Toast.LENGTH_SHORT).show() }
                        }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = DarkBackground)) { Text("Simpan Password") }
                    }
                }
            }



            // TV Password
            if (state.currentRole == "admin") {
                var tvPassVisible by remember { mutableStateOf(false) }
                var tvPassConfirmVisible by remember { mutableStateOf(false) }
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = DarkSurface), border = BorderStroke(1.dp, DarkSurfaceV3)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("PASSWORD TV", style = MaterialTheme.typography.labelLarge, color = NeonCyan)
                        Spacer(Modifier.height(4.dp))
                        Text("Digunakan untuk proteksi hapus TV di Dashboard. Kasir tidak bisa melihat ini.", style = MaterialTheme.typography.bodySmall, color = TextDim)
                        Spacer(Modifier.height(8.dp))

                        val hash = state.tvPasswordHash
                        val hasPassword = hash.isNotEmpty()

                        OutlinedTextField(value = tvPass, onValueChange = { tvPass = it; tvPassMsg = "" }, label = { Text(if (hasPassword) "Password baru (min 4)" else "Password (min 4)") }, singleLine = true, visualTransformation = if (tvPassVisible) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { IconButton(onClick = { tvPassVisible = !tvPassVisible }) { Icon(if (tvPassVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null, tint = TextSecondary) } }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = fieldColors())

                        if (hasPassword) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(value = tvPassConfirm, onValueChange = { tvPassConfirm = it; tvPassMsg = "" }, label = { Text("Konfirmasi password baru") }, singleLine = true, visualTransformation = if (tvPassConfirmVisible) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { IconButton(onClick = { tvPassConfirmVisible = !tvPassConfirmVisible }) { Icon(if (tvPassConfirmVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null, tint = TextSecondary) } }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = fieldColors())
                        }

                        if (tvPassMsg.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(tvPassMsg, style = MaterialTheme.typography.bodySmall, color = if (tvPassMsgOk) NeonGreen else NeonRed)
                        }

                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                if (tvPass.length < 4) { tvPassMsg = "Minimal 4 karakter"; tvPassMsgOk = false; return@Button }
                                if (hasPassword && tvPass != tvPassConfirm) { tvPassMsg = "Konfirmasi tidak cocok"; tvPassMsgOk = false; return@Button }
                                viewModel.setTvPassword(tvPass)
                                tvPass = ""; tvPassConfirm = ""
                                tvPassMsg = "Password TV tersimpan"; tvPassMsgOk = true
                            }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = DarkBackground)) { Text(if (hasPassword) "Ubah Password" else "Simpan Password") }

                            if (hasPassword) {
                                OutlinedButton(onClick = {
                                    viewModel.setTvPassword("")
                                    tvPass = ""; tvPassConfirm = ""
                                    tvPassMsg = "Password TV dihapus"; tvPassMsgOk = true
                                }, shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonRed), border = BorderStroke(1.dp, NeonRed)) { Text("Hapus") }
                            }
                        }
                    }
                }
            }

            // User Management (Admin)
            if (state.currentRole == "admin") {
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = DarkSurface), border = BorderStroke(1.dp, DarkSurfaceV3)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("MANAJEMEN KASIR", style = MaterialTheme.typography.labelLarge, color = NeonCyan)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = regUser, onValueChange = { regUser = it; regMsg = "" }, label = { Text("Username baru") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = fieldColors())
                        var regPassVisible by remember { mutableStateOf(false) }
                        OutlinedTextField(value = regPass, onValueChange = { regPass = it }, label = { Text("Password (min 6, huruf besar & angka)") }, singleLine = true, visualTransformation = if (regPassVisible) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { IconButton(onClick = { regPassVisible = !regPassVisible }) { Icon(if (regPassVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null, tint = TextSecondary) } }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = fieldColors())
                        Spacer(Modifier.height(4.dp))
                        Text("Role: kasir", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        OutlinedTextField(value = regEmail, onValueChange = { regEmail = it }, label = { Text("Email (opsional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = fieldColors())
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            if (regUser.isBlank() || regPass.length < 6) { regMsg = "Username & password min 6"; regMsgOk = false; return@Button }
                            if (!regPass.any { it.isUpperCase() }) { regMsg = "Password harus huruf besar"; regMsgOk = false; return@Button }
                            if (!regPass.any { it.isDigit() }) { regMsg = "Password harus angka"; regMsgOk = false; return@Button }
                            if (viewModel.cekUsername(regUser)) { regMsg = "Username sudah ada"; regMsgOk = false; return@Button }
                            scope.launch {
                                val result = viewModel.daftarUser(regUser, regPass, regEmail, regRole)
                                regMsg = result.message
                                regMsgOk = result.success
                                if (result.success) {
                                    regUser = ""; regPass = ""; regEmail = ""
                                }
                            }
                        }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = DarkBackground)) { Text("Daftarkan User") }
                        if (regMsg.isNotEmpty()) { Spacer(Modifier.height(4.dp)); Text(regMsg, style = MaterialTheme.typography.bodySmall, color = if (regMsgOk) NeonGreen else NeonRed) }
                    }
                }

                }

            // Theme Picker
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = DarkSurface), border = BorderStroke(1.dp, DarkSurfaceV3)) {
                Column(Modifier.padding(16.dp)) {
                    Text("TEMA TAMPILAN", style = MaterialTheme.typography.labelLarge, color = NeonCyan)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ThemeOption.entries.forEach { opt ->
                            val selected = state.themeOption == opt
                            val accent = when (opt) {
                                ThemeOption.GAMING_DARK -> Color(0xFF39FF14)
                                ThemeOption.CYBER_BLUE -> Color(0xFF00E5FF)
                                ThemeOption.NEON_PURPLE -> Color(0xFFBB00FF)
                                ThemeOption.CLASSIC_DARK -> Color(0xFF4CAF50)
                                ThemeOption.LIGHT_MODE -> Color(0xFF388E3C)
                            }
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.setTheme(opt) },
                                label = { Text(opt.displayName, style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = {
                                    Box(modifier = Modifier.size(10.dp).background(accent, RoundedCornerShape(2.dp)))
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accent.copy(alpha = 0.2f),
                                    selectedLabelColor = accent,
                                ),
                            )
                        }
                    }
                }
            }

            // Check Update
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = DarkSurface), border = BorderStroke(1.dp, DarkSurfaceV3)) {
                Column(Modifier.padding(16.dp)) {
                    Text("PERIKSA PEMBARUAN", style = MaterialTheme.typography.labelLarge, color = NeonCyan)
                    Spacer(Modifier.height(8.dp))
                    val updateInfo = state.updateInfo
                    val updateChecked = state.updateChecked
                    if (updateInfo != null) {
                        Text("Versi ${updateInfo.versionName} tersedia!", style = MaterialTheme.typography.bodyMedium, color = NeonGreen)
                        Spacer(Modifier.height(8.dp))
                        if (state.downloadProgress >= 0) {
                            LinearProgressIndicator(progress = { state.downloadProgress / 100f }, modifier = Modifier.fillMaxWidth().height(12.dp), color = NeonGreen, trackColor = DarkSurfaceV3)
                            Spacer(Modifier.height(4.dp))
                            Text("Download: ${state.downloadProgress}%", style = MaterialTheme.typography.bodySmall, color = NeonGreen)
                        } else {
                            Button(onClick = { viewModel.downloadAndInstall(ctx) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = DarkBackground)) {
                                Text("Download & Install v${updateInfo.versionName}")
                            }
                        }
                    } else if (updateChecked) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("✓", color = NeonGreen, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Spacer(Modifier.width(6.dp))
                            Text("Versi terbaru (v${state.appVersionName})", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Button(
                        onClick = { viewModel.checkForUpdate(); Toast.makeText(ctx, "Memeriksa...", Toast.LENGTH_SHORT).show() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = DarkBackground),
                    ) { Text("Cek Update") }
                }
            }

            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun ProfileHeader(username: String, role: String, appVersionName: String) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkSurfaceV3),
    ) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = androidx.compose.ui.res.painterResource(com.billingps.aptv.R.drawable.logo_transparant_profile),
                contentDescription = "Logo",
                modifier = Modifier.size(96.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text("RR BILLING PRO", style = MaterialTheme.typography.titleLarge, color = NeonGreen)
            Text("Sistem Billing Rental PlayStation & TV", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Spacer(Modifier.height(12.dp))
            listOf(
                "Versi" to appVersionName,
                "Developer" to "RR Developer",
                "Kontak" to "082180208414",
                "User" to "$username [$role]",
            ).forEach { (label, value) ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Text(value, style = MaterialTheme.typography.bodySmall, color = TextPrimary, fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("© 2026 RR BILLING PRO", style = MaterialTheme.typography.bodySmall, color = TextDim)
        }
    }
}

private val paketHargaMap = mapOf(
    "1 Bulan" to 99000,
    "3 Bulan" to 299000,
    "1 Tahun" to 999000,
    "LIFETIME" to 2000000,
)

@Composable private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = NeonGreen, unfocusedBorderColor = DarkSurfaceV3,
    cursorColor = NeonGreen, focusedLabelColor = NeonGreen,
    unfocusedContainerColor = DarkSurfaceV2, focusedContainerColor = DarkSurfaceV2,
)
