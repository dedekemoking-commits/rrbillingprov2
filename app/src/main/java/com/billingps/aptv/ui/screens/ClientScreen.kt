package com.billingps.aptv.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.billingps.aptv.TvViewModel
import com.billingps.aptv.models.MainViewModel
import com.billingps.aptv.models.TvData
import com.billingps.aptv.ui.theme.*
import com.billingps.aptv.utils.ApkServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientScreen(
    viewModel: MainViewModel,
    tvViewModel: TvViewModel,
) {
    val state by viewModel.state.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var uploadTargetTv by remember { mutableStateOf<TvData?>(null) }
    var uploadMode by remember { mutableStateOf("media") }
    var showMediaUploader by remember { mutableStateOf(false) }
    var showNotifUploader by remember { mutableStateOf(false) }
    var uploadStatus by remember { mutableStateOf("") }
    var installTargetTv by remember { mutableStateOf<TvData?>(null) }

    fun startUpload(uri: Uri, tv: TvData, username: String, mode: String) {
        uploadStatus = "Mengupload..."
        scope.launch {
            try {
                val inputStream = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri) }
                if (inputStream == null) { uploadStatus = "Gagal: tidak bisa baca file"; return@launch }
                val name = uri.lastPathSegment?.substringAfterLast("/") ?: "video.mp4"
                val cacheFile = File(ctx.cacheDir, "uploads/$name")
                withContext(Dispatchers.IO) {
                    cacheFile.parentFile?.mkdirs()
                    cacheFile.outputStream().use { out -> inputStream.copyTo(out) }
                    inputStream.close()
                }
                val ip = withContext(Dispatchers.IO) { ApkServer.getLocalIpAddress(ctx) }
                if (ip == null) { uploadStatus = "Gagal: tidak bisa dapat IP lokal"; return@launch }
                withContext(Dispatchers.IO) {
                    val apkFile = File(ctx.cacheDir, "tv-client.apk")
                    if (!apkFile.exists()) {
                        ctx.assets.open("tv-client.apk").use { input ->
                            apkFile.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                    ApkServer.start(ctx, apkFile)
                }
                val url = "http://$ip:${ApkServer.port}/uploads/$name"
                withContext(Dispatchers.IO) {
                    if (mode == "media") {
                        viewModel.cacheMediaOnTv(username, tv.id, url)
                    } else {
                        viewModel.sendWarningVideoToTv(username, tv.id, url)
                    }
                }
                uploadStatus = "Upload berhasil!"
            } catch (e: Exception) {
                uploadStatus = "Gagal upload: ${e.message}"
            }
        }
    }

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null && uploadTargetTv != null) {
            startUpload(uri, uploadTargetTv!!, state.currentUser, uploadMode)
        }
    }

    LaunchedEffect(showMediaUploader) {
        if (showMediaUploader) {
            uploadMode = "media"
            mediaPickerLauncher.launch(arrayOf("video/*", "image/*"))
            showMediaUploader = false
        }
    }

    LaunchedEffect(showNotifUploader) {
        if (showNotifUploader) {
            uploadMode = "notifikasi"
            mediaPickerLauncher.launch(arrayOf("video/*", "image/*"))
            showNotifUploader = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(DarkSurface).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("CLIENT TV", style = MaterialTheme.typography.titleLarge, color = NeonGreen)
            Spacer(Modifier.width(12.dp))
            Icon(Icons.Filled.Android, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
        }

        if (uploadStatus.isNotEmpty()) {
            Text(uploadStatus, style = MaterialTheme.typography.bodySmall, color = NeonCyan, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }

        val pairedTvs = state.tvList.filter { it.paired }

        if (pairedTvs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Android, contentDescription = null, modifier = Modifier.size(64.dp), tint = TextDim)
                    Spacer(Modifier.height(12.dp))
                    Text("Belum ada TV yang di-pair", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
                    Text("Pair TV terlebih dahulu di Dashboard", style = MaterialTheme.typography.bodySmall, color = TextDim)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(pairedTvs) { _, tv ->
                    val clientStatus = state.clientStatus[tv.id]
                    val isOnline = clientStatus?.online == true

                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = BorderStroke(1.dp, if (isOnline) NeonGreen.copy(alpha = 0.5f) else DarkSurfaceV3),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(tv.nama, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                                    Text(tv.ip, style = MaterialTheme.typography.bodySmall, color = TextDim)
                                }
                                Box(
                                    modifier = Modifier.size(10.dp).clip(CircleShape).background(
                                        if (isOnline) NeonGreen else NeonRed
                                    )
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(if (isOnline) "Online" else "Offline", style = MaterialTheme.typography.labelSmall, color = if (isOnline) NeonGreen else TextDim)
                            }

                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (isOnline) {
                                    OutlinedButton(
                                        onClick = {},
                                        enabled = false,
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonGreen),
                                        border = BorderStroke(1.dp, NeonGreen),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    ) { Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Online", style = MaterialTheme.typography.labelSmall) }
                                } else {
                                    OutlinedButton(
                                        onClick = { installTargetTv = tv },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                                        border = BorderStroke(1.dp, NeonCyan),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    ) { Icon(Icons.Filled.Android, contentDescription = null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Install Client", style = MaterialTheme.typography.labelSmall) }
                                }
                                OutlinedButton(
                                    onClick = {
                                        uploadTargetTv = tv
                                        showMediaUploader = true
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                                    border = BorderStroke(1.dp, NeonCyan),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                ) { Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Media", style = MaterialTheme.typography.labelSmall) }
                                OutlinedButton(
                                    onClick = {
                                        uploadTargetTv = tv
                                        showNotifUploader = true
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonYellow),
                                    border = BorderStroke(1.dp, NeonYellow),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                ) { Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Notif", style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (installTargetTv != null) {
        ClientApkInstallerDialog(
            tvIp = installTargetTv!!.ip,
            tvNama = installTargetTv!!.nama,
            onDismiss = { installTargetTv = null },
        )
    }
}
