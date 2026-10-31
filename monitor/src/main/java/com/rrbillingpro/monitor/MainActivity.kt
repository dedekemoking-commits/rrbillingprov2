package com.rrbillingpro.monitor

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RrMonitorTheme {
                MonitorRoot()
            }
        }
    }
}

@Composable
private fun MonitorRoot() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("rr_monitor", Context.MODE_PRIVATE) }
    var username by remember { mutableStateOf(prefs.getString("username", "").orEmpty()) }
    var draft by remember { mutableStateOf(username) }
    var authError by remember { mutableStateOf<String?>(null) }
    var ready by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            MonitorRepository.ensureAuth()
            ready = true
        } catch (e: Exception) {
            authError = e.message ?: "Gagal terhubung ke Firebase"
        }
    }

    if (username.isBlank()) {
        LoginScreen(
            draft = draft,
            onDraftChange = { draft = it },
            ready = ready,
            error = authError,
            onOpen = {
                val value = draft.trim()
                if (value.isNotEmpty()) {
                    prefs.edit().putString("username", value).apply()
                    username = value
                }
            },
        )
    } else {
        DevicesScreen(
            username = username,
            onSwitchUser = {
                prefs.edit().remove("username").apply()
                draft = ""
                username = ""
            },
        )
    }
}

@Composable
private fun LoginScreen(
    draft: String,
    onDraftChange: (String) -> Unit,
    ready: Boolean,
    error: String?,
    onOpen: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "RR Monitor",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Monitoring log TV online",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(28.dp))
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                label = { Text("Username rental") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onOpen,
                enabled = ready && draft.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (!ready && error == null) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Buka Monitor")
                }
            }
            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DevicesScreen(username: String, onSwitchUser: () -> Unit) {
    var devices by remember { mutableStateOf<List<TvLogDoc>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedId by remember { mutableStateOf<String?>(null) }

    DisposableEffect(username) {
        val registration: ListenerRegistration = MonitorRepository.listenLogs(
            username = username,
            onUpdate = {
                devices = it
                loading = false
                error = null
            },
            onError = {
                error = it
                loading = false
            },
        )
        onDispose { registration.remove() }
    }

    val selected = devices.firstOrNull { it.tvId == selectedId }
    if (selected != null) {
        DeviceDetailScreen(
            username = username,
            initial = selected,
            onBack = { selectedId = null },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("RR Monitor", fontWeight = FontWeight.Bold)
                        Text(
                            text = username,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onSwitchUser) { Text("Ganti") }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when {
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                error != null -> Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Pastikan username benar dan app billing sudah pernah online.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                devices.isEmpty() -> Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Belum ada laporan log.", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "TV mengirim log otomatis setiap 2 menit selama app billing aktif.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(devices, key = { it.tvId }) { device ->
                        DeviceCard(device = device, onClick = { selectedId = device.tvId })
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(device: TvLogDoc, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = device.tvId,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    color = if (device.errorCount > 0) Color(0x33EF4444) else Color(0x3322C55E),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = "${device.errorCount} error",
                        color = if (device.errorCount > 0) Color(0xFFF87171) else Color(0xFF4ADE80),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "v${device.version.ifBlank { "-" }} • ${relativeTime(device.lastReportMs)}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (device.lines.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = device.lines.last(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = logColor(device.lines.last()),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceDetailScreen(username: String, initial: TvLogDoc, onBack: () -> Unit) {
    var device by remember { mutableStateOf(initial) }
    var command by remember { mutableStateOf<MonitorCommand?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }

    DisposableEffect(username, initial.tvId) {
        val logRegistration = MonitorRepository.listenLog(
            username = username,
            tvId = initial.tvId,
            onUpdate = { doc -> doc?.let { device = it } },
            onError = { error = it },
        )
        val commandRegistration = MonitorRepository.listenCommand(
            username = username,
            tvId = initial.tvId,
            onUpdate = { command = it },
        )
        onDispose {
            logRegistration.remove()
            commandRegistration.remove()
        }
    }

    val recommendation = recommend(device.lines)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(device.tvId, fontWeight = FontWeight.Bold)
                        Text(
                            text = "v${device.version.ifBlank { "-" }} • ${relativeTime(device.lastReportMs)}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Kembali") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 12.dp),
        ) {
            RecommendationCard(
                command = recommendation.first,
                reason = recommendation.second,
                onUse = { pending = recommendation.first },
            )
            Spacer(Modifier.height(8.dp))
            CommandStatus(command)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SmallCommandButton("Log", Modifier.weight(1f)) { pending = "REPORT_LOGS" }
                SmallCommandButton("Reconnect", Modifier.weight(1f)) { pending = "RECONNECT_WS" }
                SmallCommandButton("Restart", Modifier.weight(1f)) { pending = "RESTART_CLIENT" }
            }
            if (error != null) {
                Spacer(Modifier.height(6.dp))
                Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${device.errorCount} error • ${device.totalLines} baris terakhir",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF0D1117), RoundedCornerShape(10.dp))
                    .padding(8.dp),
            ) {
                if (device.lines.isEmpty()) {
                    item {
                        Text(
                            text = "Belum ada log. Kirim perintah Log untuk meminta TV mengirim log terbaru.",
                            color = Color(0xFF8B949E),
                            fontSize = 12.sp,
                        )
                    }
                }
                items(device.lines) { line ->
                    Text(
                        text = line,
                        color = logColor(line),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(vertical = 1.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }

    val commandToSend = pending
    if (commandToSend != null) {
        AlertDialog(
            onDismissRequest = { if (!sending) pending = null },
            title = { Text("Kirim perintah") },
            text = {
                Text(
                    "Kirim ${commandLabel(commandToSend)} ke ${device.tvId}? " +
                        "App billing akan meneruskannya ke TV lewat hub lokal."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !sending,
                    onClick = {
                        sending = true
                        MonitorRepository.sendCommand(username, device.tvId, commandToSend) { ok, message ->
                            sending = false
                            pending = null
                            if (!ok) error = message
                        }
                    },
                ) {
                    Text(if (sending) "Mengirim..." else "Kirim")
                }
            },
            dismissButton = {
                TextButton(enabled = !sending, onClick = { pending = null }) { Text("Batal") }
            },
        )
    }
}

@Composable
private fun RecommendationCard(command: String, reason: String, onUse: () -> Unit) {
    Surface(
        color = Color(0x1A4ADE80),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Rekomendasi: ${commandLabel(command)}",
                    color = Color(0xFF4ADE80),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = reason,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onUse) { Text("Pakai") }
        }
    }
}

@Composable
private fun CommandStatus(command: MonitorCommand?) {
    if (command == null || command.commandId.isBlank()) {
        Text(
            text = "Belum ada perintah dari monitor.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val color = when (command.status) {
        "sent" -> Color(0xFF22C55E)
        "offline" -> Color(0xFFEF4444)
        else -> Color(0xFFF59E0B)
    }
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(
                text = "${commandLabel(command.command)} • ${statusLabel(command.status)}",
                color = color,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (command.result.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = command.result,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SmallCommandButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier, contentPadding = PaddingValues(vertical = 10.dp)) {
        Text(text, fontSize = 12.sp)
    }
}

@Composable
private fun RrMonitorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF4ADE80),
            onPrimary = Color(0xFF052012),
            background = Color(0xFF0B0F14),
            onBackground = Color(0xFFE6EDF3),
            surface = Color(0xFF121821),
            onSurface = Color(0xFFE6EDF3),
            surfaceVariant = Color(0xFF1B2430),
            onSurfaceVariant = Color(0xFF9AA7B4),
            error = Color(0xFFF87171),
        ),
        content = content,
    )
}

private fun recommend(lines: List<String>): Pair<String, String> {
    val text = lines.joinToString("\n").lowercase(Locale.ROOT)
    return when {
        listOf("fatal exception", "anr in", "outofmemory", "addview", "windowmanager", "overlay").any { it in text } ->
            "RESTART_CLIENT" to "Error overlay/crash terdeteksi, restart client aman."
        listOf("websocket", "connection reset", "failed to connect", "timeout", "socket", "broken pipe").any { it in text } ->
            "RECONNECT_WS" to "Masalah koneksi WebSocket terdeteksi."
        else -> "REPORT_LOGS" to "Minta log terbaru dari TV."
    }
}

private fun commandLabel(command: String): String = when (command.uppercase(Locale.ROOT)) {
    "REPORT_LOGS" -> "Minta Log"
    "RECONNECT_WS" -> "Reconnect WebSocket"
    "RESTART_CLIENT" -> "Restart Client"
    else -> command
}

private fun statusLabel(status: String): String = when (status) {
    "pending" -> "menunggu app billing"
    "sent" -> "terkirim ke TV"
    "offline" -> "TV offline"
    else -> status.ifBlank { "belum diproses" }
}

private fun logColor(line: String): Color {
    val lower = line.lowercase(Locale.ROOT)
    return when {
        " e " in lower || "error" in lower || "fatal" in lower || "exception" in lower -> Color(0xFFF87171)
        " w " in lower || "warning" in lower || "warn" in lower -> Color(0xFFFBBF24)
        else -> Color(0xFF9CA3AF)
    }
}

private fun relativeTime(ms: Long): String {
    if (ms <= 0L) return "-"
    val diff = System.currentTimeMillis() - ms
    return when {
        diff < 0 || diff < 60_000 -> "baru saja"
        diff < 3_600_000 -> "${diff / 60_000} mnt lalu"
        diff < 86_400_000 -> "${diff / 3_600_000} jam lalu"
        else -> SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(ms))
    }
}
