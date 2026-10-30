package com.billingps.tvclient

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.billingps.tvclient.firestore.CloudHelper
import com.billingps.tvclient.firestore.TvCommand
import com.billingps.tvclient.firestore.TvStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.io.File
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URI
import java.net.URL
import java.util.Collections
import java.util.concurrent.TimeUnit

class TvClientService : Service() {

    companion object {
        const val CHANNEL_ID = "tvclient_channel"
        const val NOTIFICATION_ID = 2001
        const val ACTION_START = "com.billingps.tvclient.START"
        const val ACTION_STOP = "com.billingps.tvclient.STOP"
        const val ACTION_MEDIA_STOPPED = "com.billingps.tvclient.MEDIA_STOPPED"

        const val PREF_USERNAME = "billing_username"
        const val PREF_TV_ID = "tv_id"
        const val PREF_TV_NAMA = "tv_nama"
        const val PREF_CACHED_MEDIA = "cached_media_path"
        const val PREF_CACHED_WARNING_IMAGE = "cached_warning_image_path"
        const val PREF_CACHED_WARNING_VIDEO = "cached_warning_video_path"
        const val PREF_HDMI_PORT = "hdmi_port"
        const val PREF_HDMI_HINT = "hdmi_input_hint"
        const val PREF_HDMI_COMMAND = "hdmi_command"

        private var running = false
        private var currentWarningShown = false
        private var lastSisaDetik = 0L
        private var isPlayingMedia = false
        fun isRunning() = running
        fun isMediaPlaying() = isPlayingMedia
        fun resetMediaPlaying() { isPlayingMedia = false }
        fun markMediaPlaying() { isPlayingMedia = true }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val cloudHelper = CloudHelper()
    private val _warningTriggered = MutableStateFlow(false)
    val warningTriggered: StateFlow<Boolean> = _warningTriggered
    private var heartbeatJob: Job? = null
    private var powerPollJob: Job? = null
    private var hdmiSwitchJob: Job? = null
    private var mediaStoppedReceiver: BroadcastReceiver? = null
    private var screenOnReceiver: BroadcastReceiver? = null
    @Volatile private var sessionActive = false
    private var lastWakeElapsed = 0L
    private var lastHdmiTestId = ""

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        mediaStoppedReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == ACTION_MEDIA_STOPPED) {
                    isPlayingMedia = false
                    Log.i("TvClientService", "Media stopped, flag reset")
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(mediaStoppedReceiver, IntentFilter(ACTION_MEDIA_STOPPED), RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(mediaStoppedReceiver, IntentFilter(ACTION_MEDIA_STOPPED))
        }
        OverlayManager.onVideoCompleted = {
            isPlayingMedia = false
            Log.i("TvClientService", "Overlay video completed")
            scheduleHdmiAfterPromo(500L)
        }
        OverlayManager.onVideoUnavailable = {
            isPlayingMedia = false
            Log.w("TvClientService", "Overlay video unavailable")
            scheduleHdmiAfterPromo(1500L)
        }
        screenOnReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_SCREEN_ON || intent.action == Intent.ACTION_USER_PRESENT) {
                    Log.i("TvClientService", "Screen on (dynamic receiver)")
                    handleWake("screen-on")
                }
            }
        }
        val screenFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenOnReceiver, screenFilter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenOnReceiver, screenFilter)
        }
    }

    override fun onDestroy() {
        running = false
        isPlayingMedia = false
        heartbeatJob?.cancel()
        powerPollJob?.cancel()
        hdmiSwitchJob?.cancel()
        sessionActive = false
        mediaStoppedReceiver?.let { unregisterReceiver(it) }
        screenOnReceiver?.let { unregisterReceiver(it) }
        OverlayManager.cleanup()
        cloudHelper.cleanup()
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun playCachedMedia(): Boolean {
        if (isPlayingMedia) {
            Log.i("TvClientService", "playCachedMedia skipped: already playing")
            return true
        }
        return try {
            val prefs = getSharedPreferences("tvclient_prefs", Context.MODE_PRIVATE)
            val cachedPath = prefs.getString(PREF_CACHED_MEDIA, null)
            if (cachedPath != null && File(cachedPath).exists()) {
                hdmiSwitchJob?.cancel()
                isPlayingMedia = true
                OverlayManager.playCachedVideo(this, cachedPath)
                Log.i("TvClientService", "Playing cached media via overlay: $cachedPath")
                true
            } else {
                Log.w("TvClientService", "playCachedMedia skipped: cache missing ($cachedPath)")
                false
            }
        } catch (e: Exception) {
            Log.e("TvClientService", "playCachedMedia failed: ${e.message}")
            false
        }
    }

    private fun handleWake(reason: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastWakeElapsed < 8_000L) {
            Log.i("TvClientService", "Wake ignored ($reason): debounce")
            return
        }
        lastWakeElapsed = now
        if (isPlayingMedia) {
            Log.i("TvClientService", "Wake ignored ($reason): media already playing")
            return
        }
        if (sessionActive) {
            if (!playCachedMedia()) scheduleHdmiAfterPromo(3_000L)
        }
    }

    private fun onSessionStarted() {
        if (isPlayingMedia) return
        if (!playCachedMedia()) scheduleHdmiAfterPromo(3_000L)
    }

    private fun scheduleHdmiAfterPromo(delayMs: Long) {
        if (!sessionActive) return
        val prefs = getSharedPreferences("tvclient_prefs", Context.MODE_PRIVATE)
        if (prefs.getInt(PREF_HDMI_PORT, 0) < 0) return
        hdmiSwitchJob?.cancel()
        hdmiSwitchJob = scope.launch {
            delay(delayMs)
            if (!sessionActive) return@launch
            switchToHdmiSmart()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val action = intent?.action
            if (action == ACTION_STOP) {
                stopSelf()
                return START_NOT_STICKY
            }
            // ACTION_START or null (START_STICKY restart after process kill)
            val prefs = getSharedPreferences("tvclient_prefs", Context.MODE_PRIVATE)
            val username = prefs.getString(PREF_USERNAME, "") ?: ""
            val tvId = prefs.getString(PREF_TV_ID, "") ?: ""
            if (!running) {
                running = true
                try {
                    startForeground(NOTIFICATION_ID, buildNotification())
                } catch (e: Exception) {
                    Log.w("TvClientService", "startForeground failed (notification perm?): ${e.message}")
                }
            }
            if (action == ACTION_START) {
                handleWake("start")
            } else {
                Log.i("TvClientService", "onStartCommand restart (no intent), re-attaching listeners")
                handleWake("restart")
            }
            if (username.isNotEmpty() && tvId.isNotEmpty()) {
                startListening(username, tvId)
            } else if (action == ACTION_START) {
                Log.w("TvClientService", "ACTION_START but username/tvId not configured")
            }
        } catch (e: Exception) {
            Log.e("TvClientService", "onStartCommand error: ${e.message}", e)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startListening(username: String, tvId: String) {
        startHeartbeat(username, tvId)
        startPowerPolling()
        scope.launch {
            cloudHelper.ensureSignedIn()
            cloudHelper.listenTvStatus(username, tvId) { status ->
                handleTvStatus(username, tvId, status)
            }
            cloudHelper.listenTvCommands(username, tvId) { cmd ->
                handleCommand(username, tvId, cmd)
            }
        }
    }

    private fun startPowerPolling() {
        powerPollJob?.cancel()
        val pm = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
        powerPollJob = scope.launch {
            var lastInteractive = pm.isInteractive
            while (isActive) {
                delay(3000)
                try {
                    val now = pm.isInteractive
                    if (lastInteractive == false && now == true) {
                        Log.i("TvClientService", "Power polling: screen wake detected")
                        handleWake("power-poll")
                    }
                    lastInteractive = now
                } catch (e: Exception) {
                    Log.e("TvClientService", "Power polling error: ${e.message}")
                }
            }
        }
    }

    private fun handleCommand(username: String, tvId: String, cmd: TvCommand) {
        if (cmd.warningImageUrl.isNotEmpty()) {
            scope.launch {
                downloadAndCacheWarningImage(cmd.warningImageUrl)
            }
        }
        if (cmd.warningVideoUrl.isNotEmpty()) {
            scope.launch {
                downloadAndCacheWarningVideo(cmd.warningVideoUrl)
            }
        }
        if (cmd.mediaUrl.isNotEmpty() && cmd.cacheMedia) {
            scope.launch {
                downloadAndCacheMedia(cmd.mediaUrl)
            }
        }
        if (cmd.playVideo && cmd.videoUrl.isNotEmpty()) {
            scope.launch {
                downloadAndCacheMedia(cmd.videoUrl)
            }
        }
        if (cmd.hdmiPort >= 0) {
            getSharedPreferences("tvclient_prefs", Context.MODE_PRIVATE)
                .edit().putInt(PREF_HDMI_PORT, cmd.hdmiPort).apply()
        }
        if (cmd.hideWarning) {
            currentWarningShown = false
            OverlayManager.hideWarning()
        }
        if (cmd.showWarning && !currentWarningShown) {
            currentWarningShown = true
            _warningTriggered.value = true
            // Pesan dari kasir (mis. PIN QR) diprioritaskan; fallback sisa waktu.
            val waktuMsg = if (lastSisaDetik > 0) {
                val menit = lastSisaDetik / 60
                val detik = lastSisaDetik % 60
                "Sisa ${menit}m ${detik}d!"
            } else {
                "⚠️ Waktu hampir habis!"
            }
            val isPin = cmd.warningMessage.isNotBlank()
            val msg = if (isPin) cmd.warningMessage else waktuMsg
            showWarningOverlay(msg, alignStart = isPin)
        }
        if (cmd.stopVideo) {
            isPlayingMedia = false
            hdmiSwitchJob?.cancel()
            OverlayManager.stopVideo()
            Log.i("TvClientService", "Stop video command, overlay closed")
        }
        if (cmd.playCachedMedia) {
            if (!playCachedMedia() && sessionActive) scheduleHdmiAfterPromo(3_000L)
        }
        if (cmd.hdmiTestId.isNotEmpty() && cmd.hdmiTestId != lastHdmiTestId) {
            lastHdmiTestId = cmd.hdmiTestId
            scope.launch {
                val result = runHdmiTest(cmd.hdmiTestPort, cmd.hdmiTestCommand)
                cloudHelper.reportHdmiTest(username, tvId, cmd.hdmiTestId, result)
                Log.i("TvClientService", "HDMI test ${cmd.hdmiTestId}: $result")
            }
        }
    }

    private fun runHdmiTest(port: Int, command: String): String {
        if (command.startsWith("ws:")) {
            val ok = sendWsPayload(command.removePrefix("ws:"))
            return if (ok) "OK: WebSocket payload terkirim" else "FAIL: WebSocket tidak terhubung"
        }
        val input = findHdmiInput(port, "")
            ?: return "FAIL: input HDMI $port tidak ditemukan"
        return if (executeClientStrategy(input, port, command)) {
            "OK: ${command} diterima"
        } else {
            "FAIL: ${command} tidak didukung/ditolak"
        }
    }

    private fun showWarningOverlay(message: String, alignStart: Boolean = false) {
        OverlayManager.showWarning(this, message, alignStart = alignStart)
    }

    private fun downloadAndCacheMedia(urlStr: String) {
        try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.connect()
            val ext = urlStr.substringAfterLast(".", "mp4").substringBefore("?")
            val file = File(cacheDir, "cached_media.$ext")
            conn.inputStream.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
            conn.disconnect()
            getSharedPreferences("tvclient_prefs", Context.MODE_PRIVATE)
                .edit().putString(PREF_CACHED_MEDIA, file.absolutePath).apply()
            Log.i("TvClientService", "Media cached: ${file.absolutePath} (${file.length()} bytes)")
            playCachedMedia()
        } catch (e: Exception) {
            Log.e("TvClientService", "Cache media failed: ${e.message}")
        }
    }

    private fun downloadAndCacheWarningImage(urlStr: String) {
        try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.connect()
            val ext = urlStr.substringAfterLast(".", "png").substringBefore("?")
            val file = File(cacheDir, "warning_image.$ext")
            conn.inputStream.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
            conn.disconnect()
            getSharedPreferences("tvclient_prefs", Context.MODE_PRIVATE)
                .edit().putString(PREF_CACHED_WARNING_IMAGE, file.absolutePath).apply()
            Log.i("TvClientService", "Warning image cached: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e("TvClientService", "Cache warning image failed: ${e.message}")
        }
    }

    private fun downloadAndCacheWarningVideo(urlStr: String) {
        try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.connect()
            val ext = urlStr.substringAfterLast(".", "mp4").substringBefore("?")
            val file = File(cacheDir, "warning_video.$ext")
            conn.inputStream.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
            conn.disconnect()
            getSharedPreferences("tvclient_prefs", Context.MODE_PRIVATE)
                .edit().putString(PREF_CACHED_WARNING_VIDEO, file.absolutePath).apply()
            Log.i("TvClientService", "Warning video cached: ${file.absolutePath}")
            if (lastSisaDetik > 0 && lastSisaDetik <= 300) {
                showWarningOverlay("Sisa ${lastSisaDetik / 60} menit!")
            }
        } catch (e: Exception) {
            Log.e("TvClientService", "Cache warning video failed: ${e.message}")
        }
    }

    private fun startHeartbeat(username: String, tvId: String) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                try {
                    val signedIn = cloudHelper.ensureSignedIn()
                    if (!signedIn) {
                        Log.w("TvClientService", "Not signed in, skipping heartbeat")
                        delay(30_000)
                        continue
                    }
                    val ok = cloudHelper.sendHeartbeat(username, tvId, "4.3")
                    if (ok) Log.i("TvClientService", "Heartbeat sent for $tvId")
                    else Log.w("TvClientService", "Heartbeat FAILED for $tvId")
                } catch (e: Exception) {
                    Log.e("TvClientService", "Heartbeat exception: ${e.message}")
                }
                delay(30_000)
            }
        }
    }

    private fun handleTvStatus(username: String, tvId: String, status: TvStatus) {
        lastSisaDetik = status.sisaDetik
        val prefs = getSharedPreferences("tvclient_prefs", Context.MODE_PRIVATE)
        if (prefs.getInt(PREF_HDMI_PORT, 0) != status.hdmiPort ||
            (prefs.getString(PREF_HDMI_HINT, "") ?: "") != status.hdmiInputHint ||
            (prefs.getString(PREF_HDMI_COMMAND, "") ?: "") != status.hdmiCommand
        ) {
            prefs.edit()
                .putInt(PREF_HDMI_PORT, status.hdmiPort)
                .putString(PREF_HDMI_HINT, status.hdmiInputHint)
                .putString(PREF_HDMI_COMMAND, status.hdmiCommand)
                .apply()
            Log.i("TvClientService", "HDMI config: port=${status.hdmiPort} hint=${status.hdmiInputHint} command=${status.hdmiCommand}")
        }

        val nowActive = status.paketAktif.isNotEmpty() || status.timerActive
        if (nowActive && !sessionActive) {
            sessionActive = true
            currentWarningShown = false
            Log.i("TvClientService", "Session started, promo then HDMI")
            onSessionStarted()
        } else if (!nowActive && sessionActive) {
            sessionActive = false
            hdmiSwitchJob?.cancel()
            isPlayingMedia = false
            OverlayManager.stopVideo()
            OverlayManager.hideWarning()
            currentWarningShown = false
            Log.i("TvClientService", "Session ended, HDMI switch cancelled")
        }

        val warningLimitSec = when (status.popupMode.trim().lowercase()) {
            "hidden" -> 0L
            "last_minutes" -> status.popupLastMinutes * 60L
            else -> 300L
        }
        val isAlmostHabis = status.timerActive && status.sisaDetik > 0 && status.sisaDetik <= warningLimitSec
        if (isAlmostHabis && !currentWarningShown) {
            val menit = status.sisaDetik / 60
            val detik = status.sisaDetik % 60
            val message = "Sisa ${menit}m ${detik}d!"
            currentWarningShown = true
            _warningTriggered.value = true
            showWarningOverlay(message)
        }
        if (!isAlmostHabis) {
            currentWarningShown = false
        }
    }

    private fun switchToHdmiSmart() {
        val prefs = getSharedPreferences("tvclient_prefs", Context.MODE_PRIVATE)
        val port = prefs.getInt(PREF_HDMI_PORT, 0)
        if (port < 0) {
            Log.i("TvClientService", "HDMI switch disabled")
            return
        }
        val hint = prefs.getString(PREF_HDMI_HINT, "") ?: ""
        val command = prefs.getString(PREF_HDMI_COMMAND, "") ?: ""
        if (command.startsWith("ws:")) {
            if (sendWsPayload(command.removePrefix("ws:"))) {
                prefs.edit()
                    .putString("hdmi_last_strategy", "websocket")
                    .apply()
                Log.i("TvClientService", "HDMI switch OK via WebSocket payload")
                return
            }
            Log.w("TvClientService", "WebSocket HDMI failed, fallback ke TIF")
        }
        val input = findHdmiInput(port, hint)
        if (input == null) {
            Log.w("TvClientService", "HDMI input not found (port=$port hint=$hint)")
            return
        }
        if (command.isNotBlank() && !command.startsWith("ws:")) {
            if (executeClientStrategy(input, port, command)) {
                Log.i("TvClientService", "HDMI switch OK: ${input.id} via $command")
                return
            }
            Log.w("TvClientService", "Custom HDMI command failed: $command, fallback TIF")
        }
        val channelUri = TvContract.buildChannelUriForPassthroughInput(input.id)
        val intents = listOf(
            Intent(Intent.ACTION_VIEW).setData(channelUri),
            Intent("android.media.tv.action.VIEW").setData(channelUri).putExtra("inputId", input.id),
            Intent("android.tv.action.SWITCH_INPUT")
                .putExtra("inputId", input.id)
                .putExtra("hdmiPort", port),
        )
        intents.forEach { intent ->
            if (launchHdmiIntent(intent, input.id)) return
        }
        Log.w("TvClientService", "HDMI switch failed for ${input.id}")
    }

    private fun executeClientStrategy(input: TvInputInfo, port: Int, command: String): Boolean {
        val channelUri = TvContract.buildChannelUriForPassthroughInput(input.id)
        val intent = when (command) {
            "tif_view" -> Intent(Intent.ACTION_VIEW).setData(channelUri)
            "tif_view_id" -> Intent(Intent.ACTION_VIEW)
                .setData(channelUri)
                .putExtra("inputId", input.id)
            "tif_media_view" -> Intent("android.media.tv.action.VIEW")
                .setData(channelUri)
                .putExtra("inputId", input.id)
            "tif_switch_input" -> Intent("android.tv.action.SWITCH_INPUT")
                .putExtra("inputId", input.id)
                .putExtra("hdmiPort", port)
            "vendor_tcl" -> vendorHdmiIntent("com.tcl.tv.action.SWITCH_INPUT", input, port)
            "vendor_hisense" -> vendorHdmiIntent("com.hisense.tv.action.SWITCH_INPUT", input, port)
            "vendor_skyworth" -> vendorHdmiIntent("com.skyworth.tv.action.SWITCH_INPUT", input, port)
            "vendor_mstar" -> vendorHdmiIntent("com.mstar.android.tv.action.SWITCH_INPUT", input, port)
            "vendor_mediatek" -> vendorHdmiIntent("com.mediatek.tv.action.SWITCH_INPUT", input, port)
            "vendor_philips" -> vendorHdmiIntent("com.philips.tv.action.SWITCH_INPUT", input, port)
            "vendor_sony" -> vendorHdmiIntent("com.sony.tv.action.SWITCH_INPUT", input, port)
            "vendor_lg" -> vendorHdmiIntent("com.lg.app.tv.action.SWITCH_INPUT", input, port)
            "vendor_samsung" -> vendorHdmiIntent("com.samsung.android.tv.action.SWITCH_INPUT", input, port)
            else -> null
        } ?: return false
        return launchHdmiIntent(intent, input.id, command)
    }

    private fun vendorHdmiIntent(action: String, input: TvInputInfo, port: Int): Intent =
        Intent(action)
            .putExtra("inputId", input.id)
            .putExtra("tv_input_id", input.id)
            .putExtra("hdmiPort", port)
            .putExtra("port", port)

    private fun launchHdmiIntent(intent: Intent, inputId: String, strategy: String = ""): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            startActivity(intent)
            getSharedPreferences("tvclient_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("hdmi_last_input", inputId)
                .putString("hdmi_last_strategy", strategy.ifBlank { intent.action ?: "" })
                .apply()
            true
        } catch (e: Exception) {
            Log.w("TvClientService", "HDMI intent failed (${intent.action}): ${e.message}")
            false
        }
    }

    private fun sendWsPayload(payload: String): Boolean {
        val targets = linkedSetOf("127.0.0.1")
        localIpv4()?.let { targets.add(it) }
        for (host in targets) {
            val client = object : WebSocketClient(URI("ws://$host:8080")) {
                override fun onOpen(handshakedata: ServerHandshake?) = Unit
                override fun onMessage(message: String?) = Unit
                override fun onClose(code: Int, reason: String?, remote: Boolean) = Unit
                override fun onError(ex: Exception?) {
                    Log.w("TvClientService", "WS HDMI error $host: ${ex?.message}")
                }
            }
            try {
                if (client.connectBlocking(2, TimeUnit.SECONDS) && client.isOpen) {
                    client.send(payload)
                    Thread.sleep(300)
                    client.close()
                    Log.i("TvClientService", "WS HDMI sent to $host:8080")
                    return true
                }
            } catch (e: Exception) {
                Log.w("TvClientService", "WS HDMI failed $host: ${e.message}")
            } finally {
                try {
                    client.close()
                } catch (_: Exception) {
                }
            }
        }
        return false
    }

    private fun localIpv4(): String? = try {
        Collections.list(NetworkInterface.getNetworkInterfaces())
            .flatMap { Collections.list(it.inetAddresses) }
            .firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
            ?.hostAddress
    } catch (_: Exception) {
        null
    }

    private fun findHdmiInput(port: Int, hint: String): TvInputInfo? {
        val manager = getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager ?: return null
        val hdmi = try {
            manager.tvInputList.filter { it.type == TvInputInfo.TYPE_HDMI && it.isPassthroughInput }
        } catch (e: Exception) {
            Log.e("TvClientService", "Enumerate HDMI inputs failed: ${e.message}")
            return null
        }
        if (hdmi.isEmpty()) return null
        val labeled = hdmi.map { info ->
            val label = try {
                info.loadLabel(this)?.toString() ?: ""
            } catch (_: Exception) {
                ""
            }
            Triple(info, label, portFromLabel(label))
        }.sortedWith(compareBy({ it.third ?: 99 }, { it.second }, { it.first.id }))

        val wantedHint = hint.trim()
        if (wantedHint.isNotEmpty()) {
            labeled.firstOrNull { (info, label, _) ->
                label.contains(wantedHint, ignoreCase = true) ||
                    info.id.contains(wantedHint, ignoreCase = true)
            }?.let { return it.first }
            portFromLabel(wantedHint)?.let { hintedPort ->
                labeled.firstOrNull { it.third == hintedPort }?.let { return it.first }
            }
        }
        if (port > 0) {
            labeled.firstOrNull { it.third == port }?.let { return it.first }
            labeled.getOrNull(port - 1)?.let { return it.first }
        }
        return labeled.firstOrNull()?.first
    }

    private fun portFromLabel(text: String): Int? =
        Regex("(?i)(?:hdmi|input)\\s*([0-9]{1,2})")
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BillingPS TV Client")
            .setContentText("Menunggu perintah...")
            .setSmallIcon(com.billingps.tvclient.R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "TV Client Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Layanan klien TV untuk notifikasi billing"
                setSound(null, null)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }
}
