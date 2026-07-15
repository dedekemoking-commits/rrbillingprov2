package com.billingps.aptv.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.border
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.billingps.aptv.models.*
import com.billingps.aptv.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    viewModel: MainViewModel,
    onLogout: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val ctx = LocalContext.current
    val canGenerateLicense = state.currentUser == "rrgaming"

    var kodeAktivasi by remember { mutableStateOf("") }
    var aktMsg by remember { mutableStateOf("") }
    var aktOk by remember { mutableStateOf(false) }
    var aktLoading by remember { mutableStateOf(false) }
    var newPass by remember { mutableStateOf("") }

    var regUser by remember { mutableStateOf("") }
    var regPass by remember { mutableStateOf("") }
    var regRole by remember { mutableStateOf("kasir") }
    var regEmail by remember { mutableStateOf("") }
    var regMsg by remember { mutableStateOf("") }
    var regMsgOk by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    var genUname by remember { mutableStateOf("") }
    var genPaket by remember { mutableStateOf("BULANAN") }
    var genKode by remember { mutableStateOf("") }
    var genMsg by remember { mutableStateOf("") }
    var genLoading by remember { mutableStateOf(false) }

    val lic = state.licenseStatus
    val now = System.currentTimeMillis()
    val licSisa = if (lic.status == "active" && lic.expiresAt.isNotEmpty()) {
        try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val expDate = sdf.parse(lic.expiresAt)
            val diff = expDate.time - now
            if (diff > 0) "${diff / 86400000} hari" else "Hari ini"
        } catch (_: Exception) { "" }
    } else ""
    val trialSisa = if (state.trialBatas > now) {
        val s = (state.trialBatas - now) / 1000
        "${s / 86400} hari ${(s % 86400) / 3600} jam"
    } else ""
    val displayColor = when {
        lic.status == "active" -> NeonGreen
        trialSisa.isNotEmpty() -> NeonYellow
        else -> NeonRed
    }

    Column(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(DarkSurface).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("PROFIL & AKTIVASI", style = MaterialTheme.typography.titleLarge, color = NeonGreen)
            OutlinedButton(
                onClick = onLogout,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonRed),
                border = BorderStroke(1.dp, NeonRed),
            ) { Icon(Icons.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Keluar") }
        }

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Profile Card
            ProfileHeader(state.currentUser, state.currentRole, state.appVersionName)

            // License Status Info
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = DarkSurface), border = BorderStroke(1.dp, DarkSurfaceV3)) {
                Column(Modifier.padding(16.dp)) {
                    Text("INFORMASI LISENSI", style = MaterialTheme.typography.labelLarge, color = NeonCyan)
                    Spacer(Modifier.height(8.dp))
                    val displayText = when {
                        lic.status == "active" -> "✅ Lisensi Aktif" + if (licSisa.isNotEmpty()) " ($licSisa lagi)" else ""
                        trialSisa.isNotEmpty() -> "⏳ Masa Trial: $trialSisa tersisa (max 2 TV)"
                        else -> "Lisensi tidak aktif"
                    }
                    val displayMaxInfo = when {
                        lic.status == "active" && state.maxTv > 0 -> "Maksimal $state.maxTv perangkat TV"
                        lic.status == "active" -> "Unlimited perangkat TV"
                        trialSisa.isNotEmpty() -> "Maksimal 2 perangkat TV (trial)"
                        else -> ""
                    }
                    Box(modifier = Modifier.fillMaxWidth().border(BorderStroke(1.dp, displayColor)).padding(10.dp), contentAlignment = Alignment.Center) {
                        Text(displayText, style = MaterialTheme.typography.bodyMedium, color = displayColor, textAlign = TextAlign.Center)
                    }
                    if (displayMaxInfo.isNotEmpty()) {
                        Text(displayMaxInfo, style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }

            // License Activation
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = DarkSurface), border = BorderStroke(1.dp, DarkSurfaceV3)) {
                Column(Modifier.padding(16.dp)) {
                    Text("AKTIVASI LISENSI", style = MaterialTheme.typography.labelLarge, color = NeonCyan)
                    Spacer(Modifier.height(8.dp))
                    val licDisplay = when {
                        lic.status == "active" && licSisa.isNotEmpty() -> "✅ Lisensi Aktif ($licSisa lagi)"
                        lic.status == "active" -> "✅ Lisensi Aktif"
                        trialSisa.isNotEmpty() -> "⏳ Trial: $trialSisa tersisa"
                        else -> "Lisensi tidak aktif"
                    }
                    Box(modifier = Modifier.fillMaxWidth().border(BorderStroke(1.dp, displayColor)).padding(10.dp), contentAlignment = Alignment.Center) {
                        Text(licDisplay, style = MaterialTheme.typography.bodyMedium, color = displayColor, textAlign = TextAlign.Center)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = kodeAktivasi, onValueChange = { kodeAktivasi = it.uppercase(); aktMsg = "" }, label = { Text("Kode Aktivasi") }, placeholder = { Text("Masukkan kode dari admin") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = fieldColors())
                    if (aktMsg.isNotEmpty()) { Spacer(Modifier.height(4.dp)); Text(aktMsg, style = MaterialTheme.typography.bodySmall, color = if (aktOk) NeonGreen else NeonRed) }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        if (kodeAktivasi.isBlank()) { aktMsg = "Masukkan kode"; aktOk = false; return@Button }
                        aktMsg = "Memverifikasi..."; aktOk = false; aktLoading = true
                        viewModel.aktivasiLisensi(kodeAktivasi) { ok, msg ->
                            aktOk = ok; aktMsg = msg; aktLoading = false
                        }
                    }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = DarkBackground)) {
                        if (aktLoading) { CircularProgressIndicator(modifier = Modifier.size(18.dp), color = DarkBackground, strokeWidth = 2.dp) }
                        else { Text("Aktifkan Lisensi") }
                    }
                }
            }

            // Subscription Packages
            SubscriptionPackages { paket -> openWA(ctx, paket) }

            // Super Admin: Generate Kode
            if (canGenerateLicense) {
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = DarkSurface), border = BorderStroke(1.dp, DarkSurfaceV3)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("GENERATE KODE AKTIVASI", style = MaterialTheme.typography.labelLarge, color = NeonCyan)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = genUname, onValueChange = { genUname = it }, label = { Text("Username Pelanggan") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = fieldColors())
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("BULANAN", "3BULAN", "TAHUNAN", "LIFETIME").forEach { p ->
                                FilterChip(selected = genPaket == p, onClick = { genPaket = p; genKode = "" }, label = { Text(p, style = MaterialTheme.typography.labelSmall) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NeonGreen, selectedLabelColor = DarkBackground))
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        val maxTvText = when (genPaket) {
                            "BULANAN" -> "max 5 TV"
                            "3BULAN" -> "max 8 TV"
                            "TAHUNAN" -> "max 15 TV"
                            else -> "Unlimited"
                        }
                        Text(maxTvText, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            if (genUname.isBlank()) { genMsg = "Masukkan username"; return@Button }
                            genMsg = "Generating..."; genKode = ""; genLoading = true
                            viewModel.generateLicenseKode(genPaket, genUname) { kode ->
                                genKode = kode; genMsg = ""; genLoading = false
                            }
                        }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = DarkBackground)) {
                            if (genLoading) { CircularProgressIndicator(modifier = Modifier.size(18.dp), color = DarkBackground, strokeWidth = 2.dp) }
                            else { Text("Generate Kode") }
                        }
                        if (genMsg.isNotEmpty()) { Spacer(Modifier.height(4.dp)); Text(genMsg, style = MaterialTheme.typography.bodySmall, color = NeonYellow) }
                        if (genKode.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth()
                                    .border(BorderStroke(2.dp, NeonGreen))
                                    .clickable {
                                        val clip = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clip.setPrimaryClip(ClipData.newPlainText("Kode Aktivasi", genKode))
                                        Toast.makeText(ctx, "Kode tersalin", Toast.LENGTH_SHORT).show()
                                    },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = GamersGreenDark),
                            ) {
                                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(genKode, style = MaterialTheme.typography.titleLarge, color = NeonGreen, letterSpacing = 2.sp, textAlign = TextAlign.Center)
                                    Text("Tap untuk copy", style = MaterialTheme.typography.bodySmall, color = TextDim)
                                }
                            }
                        }
                    }
                }
            }

            // Riwayat Generate Kode
            if (canGenerateLicense && state.kodeGenerasiList.isNotEmpty()) {
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = DarkSurface), border = BorderStroke(1.dp, DarkSurfaceV3)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("RIWAYAT GENERATE KODE", style = MaterialTheme.typography.labelLarge, color = NeonCyan)
                        Spacer(Modifier.height(8.dp))
                        val reversed = state.kodeGenerasiList.sortedByDescending { it.waktu }
                        reversed.forEach { g ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(g.kode, style = MaterialTheme.typography.bodySmall, color = NeonGreen, fontWeight = FontWeight.Bold)
                                    Text("${g.username} • ${g.paket}", style = MaterialTheme.typography.bodySmall, color = TextDim)
                                }
                            }
                        }
                    }
                }
            }

            // Change Password
            if (state.currentRole == "admin") {
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = DarkSurface), border = BorderStroke(1.dp, DarkSurfaceV3)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("GANTI PASSWORD", style = MaterialTheme.typography.labelLarge, color = NeonCyan)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = newPass, onValueChange = { newPass = it }, label = { Text("Password baru (min 6)") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = fieldColors())
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            if (newPass.length < 6) { Toast.makeText(ctx, "Minimal 6 karakter", Toast.LENGTH_SHORT).show(); return@Button }
                            if (viewModel.gantiPassword(state.currentUser, newPass)) { Toast.makeText(ctx, "Password berhasil diubah", Toast.LENGTH_SHORT).show(); newPass = "" }
                            else { Toast.makeText(ctx, "Gagal mengubah password", Toast.LENGTH_SHORT).show() }
                        }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = DarkBackground)) { Text("Simpan Password") }
                    }
                }
            }

            // User Management (Admin)
            if (state.currentRole == "admin") {
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = DarkSurface), border = BorderStroke(1.dp, DarkSurfaceV3)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("MANAJEMEN USER", style = MaterialTheme.typography.labelLarge, color = NeonCyan)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = regUser, onValueChange = { regUser = it; regMsg = "" }, label = { Text("Username baru") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = fieldColors())
                        OutlinedTextField(value = regPass, onValueChange = { regPass = it }, label = { Text("Password (min 6, huruf besar & angka)") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = fieldColors())
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

            // SMTP Config
            if (state.currentRole == "admin") {
                val smtpCfg = state.smtp
                var smtpEmail by remember { mutableStateOf(smtpCfg.user) }
                var smtpPass by remember { mutableStateOf(smtpCfg.pass) }
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = DarkSurface), border = BorderStroke(1.dp, DarkSurfaceV3)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("KONFIGURASI EMAIL", style = MaterialTheme.typography.labelLarge, color = NeonCyan)
                        Spacer(Modifier.height(4.dp))
                        Text("Digunakan untuk kirim kode verifikasi & reset password via Gmail.", style = MaterialTheme.typography.bodySmall, color = TextDim)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = smtpEmail, onValueChange = { smtpEmail = it }, label = { Text("Email Gmail") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = fieldColors())
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(value = smtpPass, onValueChange = { smtpPass = it }, label = { Text("App Password Gmail") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = fieldColors())
                        Spacer(Modifier.height(4.dp))
                        Text("Cara buat App Password: Google Account -> Keamanan -> App Password", style = MaterialTheme.typography.bodySmall, color = TextDim)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            viewModel.saveSmtp(SmtpConfig(host = "smtp.gmail.com", port = 587, user = smtpEmail, pass = smtpPass))
                            Toast.makeText(ctx, "Konfigurasi email tersimpan", Toast.LENGTH_SHORT).show()
                        }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DarkBackground)) { Text("Simpan Konfigurasi Email") }
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
                painter = androidx.compose.ui.res.painterResource(com.billingps.aptv.R.drawable.logo_transparant),
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
                "Kontak" to "081270647744",
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

@Composable
private fun SubscriptionPackages(onBayar: (String) -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkSurfaceV3),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("PAKET BERLANGGANAN", style = MaterialTheme.typography.labelLarge, color = NeonCyan)
            Spacer(Modifier.height(8.dp))
            listOf(
                SubData("1 Bulan", "Rp99.000", "ADD TV 5", NeonGreen),
                SubData("3 Bulan", "Rp299.000", "ADD TV 10", NeonCyan),
                SubData("1 Tahun", "Rp999.000", "ADD TV 15", NeonYellow),
                SubData("LIFETIME", "Rp2.000.000", "ADD TV unlimited", NeonOrange),
            ).forEach { p ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceV2),
                    border = BorderStroke(1.dp, p.color),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(p.nama, style = MaterialTheme.typography.bodyMedium, color = p.color, fontWeight = FontWeight.Bold)
                            Text(p.harga, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                            Text(p.desc, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        Button(
                            onClick = { onBayar(p.nama) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = p.color, contentColor = DarkBackground),
                        ) { Text("Bayar") }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { onBayar("") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonGreen),
                border = BorderStroke(1.dp, NeonGreen),
            ) { Icon(Icons.Filled.Email, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Hubungi Admin via WhatsApp") }
        }
    }
}

private data class SubData(val nama: String, val harga: String, val desc: String, val color: Color)

private fun openWA(ctx: android.content.Context, paket: String) {
    val msg = "Halo Admin RR BILLING PRO,\n\nSaya ingin berlangganan:\n📦 Paket: $paket\n\nMohon info pembayaran. Terima kasih!"
    try {
        val uri = Uri.parse("https://wa.me/6281270647744?text=${Uri.encode(msg)}")
        ctx.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (_: Exception) {
        Toast.makeText(ctx, "WhatsApp tidak ditemukan", Toast.LENGTH_SHORT).show()
    }
}

@Composable private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = NeonGreen, unfocusedBorderColor = DarkSurfaceV3,
    cursorColor = NeonGreen, focusedLabelColor = NeonGreen,
    unfocusedContainerColor = DarkSurfaceV2, focusedContainerColor = DarkSurfaceV2,
)
