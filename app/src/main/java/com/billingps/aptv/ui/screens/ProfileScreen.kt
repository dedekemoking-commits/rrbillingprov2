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

    val lic = state.licenseStatus
    val licColor = when (lic.status) {
        "active" -> NeonGreen
        "trial" -> NeonYellow
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
            ProfileHeader(state.currentUser, state.currentRole)

            // License Status Info
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = DarkSurface), border = BorderStroke(1.dp, DarkSurfaceV3)) {
                Column(Modifier.padding(16.dp)) {
                    Text("INFORMASI LISENSI", style = MaterialTheme.typography.labelLarge, color = NeonCyan)
                    Spacer(Modifier.height(8.dp))
                    val trialSisa = if (state.trialBatas > System.currentTimeMillis()) {
                        val s = (state.trialBatas - System.currentTimeMillis()) / 1000
                        "${s / 86400} hari ${(s % 86400) / 3600} jam"
                    } else ""
                    val displayText = when {
                        lic.status == "active" -> lic.pesan
                        trialSisa.isNotEmpty() -> "Masa Trial: $trialSisa tersisa (max 2 TV)"
                        else -> "Lisensi tidak aktif"
                    }
                    val displayMaxInfo = if (state.maxTv > 0) "Maksimal $state.maxTv perangkat TV" else "Unlimited perangkat TV"
                    val displayColor = when {
                        lic.status == "active" -> NeonGreen
                        trialSisa.isNotEmpty() -> NeonYellow
                        else -> NeonRed
                    }
                    Box(modifier = Modifier.fillMaxWidth().border(BorderStroke(1.dp, displayColor)).padding(10.dp), contentAlignment = Alignment.Center) {
                        Text(displayText, style = MaterialTheme.typography.bodyMedium, color = displayColor, textAlign = TextAlign.Center)
                    }
                    Text(displayMaxInfo, style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.padding(top = 4.dp))
                }
            }

            // License Activation
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = DarkSurface), border = BorderStroke(1.dp, DarkSurfaceV3)) {
                Column(Modifier.padding(16.dp)) {
                    Text("AKTIVASI LISENSI", style = MaterialTheme.typography.labelLarge, color = NeonCyan)
                    Spacer(Modifier.height(8.dp))
                    Box(modifier = Modifier.fillMaxWidth().border(BorderStroke(1.dp, licColor)).padding(10.dp), contentAlignment = Alignment.Center) {
                        Text(lic.pesan.ifEmpty { "Lisensi tidak aktif" }, style = MaterialTheme.typography.bodyMedium, color = licColor, textAlign = TextAlign.Center)
                    }
                    val maxInfo = if (state.maxTv > 0) "Maksimal $state.maxTv perangkat TV" else "Unlimited perangkat TV"
                    Text(maxInfo, style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = kodeAktivasi, onValueChange = { kodeAktivasi = it.uppercase(); aktMsg = "" }, label = { Text("Kode Aktivasi") }, placeholder = { Text("RR-XXXX-XXXX-XXXX") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = fieldColors())
                    if (aktMsg.isNotEmpty()) { Spacer(Modifier.height(4.dp)); Text(aktMsg, style = MaterialTheme.typography.bodySmall, color = if (aktOk) NeonGreen else NeonRed) }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        val (ok, msg) = viewModel.aktivasiLisensi(kodeAktivasi)
                        aktOk = ok; aktMsg = msg
                    }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = DarkBackground)) { Text("Aktifkan Lisensi") }
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
                            genMsg = "Generating..."; genKode = ""
                            genKode = viewModel.generateLicenseKode(genPaket, genUname)
                            genMsg = ""
                        }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = DarkBackground)) { Text("Generate Kode") }
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

            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun ProfileHeader(username: String, role: String) {
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
                "Versi" to "1.0.0",
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
