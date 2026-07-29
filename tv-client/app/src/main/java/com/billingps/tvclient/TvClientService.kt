package com.billingps.tvclient

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.billingps.tvclient.firestore.CloudHelper
import com.billingps.tvclient.firestore.TvCommand
import com.billingps.tvclient.firestore.TvStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

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

        private var running = false
        private var currentWarningShown = false
        private var lastSisaDetik = 0L
        private var lastPaketAktif = ""
        private var isPlayingMedia = false
        fun isRunning() = running
        fun isMediaPlaying() = isPlayingMedia
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val cloudHelper = CloudHelper()
    private val _warningTriggered = MutableStateFlow(false)
    val warningTriggered: StateFlow<Boolean> = _warningTriggered
    private var heartbeatJob: Job? = null
    private var mediaStoppedReceiver: BroadcastReceiver? = null

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
        registerReceiver(mediaStoppedReceiver, IntentFilter(ACTION_MEDIA_STOPPED))
    }

    override fun onDestroy() {
        running = false
        isPlayingMedia = false
        heartbeatJob?.cancel()
        mediaStoppedReceiver?.let { unregisterReceiver(it) }
        cloudHelper.cleanup()
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun playCachedMedia() {
        if (isPlayingMedia) {
            Log.i("TvClientService", "playCachedMedia skipped: already playing")
            return
        }
        try {
            val prefs = getSharedPreferences("tvclient_prefs", Context.MODE_PRIVATE)
            val cachedPath = prefs.getString(PREF_CACHED_MEDIA, null)
            if (cachedPath != null && File(cachedPath).exists()) {
                isPlayingMedia = true
                val intent = Intent(this, VideoPlayerActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                }
                startActivity(intent)
                Log.i("TvClientService", "Playing cached media")
            }
        } catch (e: Exception) {
            Log.e("TvClientService", "playCachedMedia failed: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            when (intent?.action) {
                ACTION_START -> {
                    if (running) return START_STICKY
                    running = true
                    startForeground(NOTIFICATION_ID, buildNotification())
                    playCachedMedia()
                    val username = getSharedPreferences("tvclient_prefs", Context.MODE_PRIVATE)
                        .getString(PREF_USERNAME, "") ?: ""
                    val tvId = getSharedPreferences("tvclient_prefs", Context.MODE_PRIVATE)
                        .getString(PREF_TV_ID, "") ?: ""
                    if (username.isNotEmpty() && tvId.isNotEmpty()) {
                        startListening(username, tvId)
                    }
                }
                ACTION_STOP -> {
                    stopSelf()
                }
            }
        } catch (e: Exception) {
            Log.e("TvClientService", "onStartCommand error: ${e.message}", e)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startListening(username: String, tvId: String) {
        startHeartbeat(username, tvId)
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
        if (cmd.hdmiPort > 0) {
            getSharedPreferences("tvclient_prefs", Context.MODE_PRIVATE)
                .edit().putInt(PREF_HDMI_PORT, cmd.hdmiPort).apply()
        }
        if (cmd.showWarning && !currentWarningShown) {
            currentWarningShown = true
            _warningTriggered.value = true
            showWarningOverlay(cmd.warningMessage)
        }
        if (cmd.stopVideo) {
            isPlayingMedia = false
            val intent = Intent("com.billingps.tvclient.STOP_VIDEO")
            sendBroadcast(intent)
        }
    }

    private fun showWarningOverlay(message: String) {
        val intent = Intent(this, WarningOverlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            putExtra("message", message)
        }
        startActivity(intent)
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
            if (lastSisaDetik in 1..300) {
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
                    val ok = cloudHelper.sendHeartbeat(username, tvId, "1.0.0")
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
        // Play cached media when a new session starts (paketAktif becomes non-empty)
        if (status.paketAktif.isNotEmpty() && lastPaketAktif.isEmpty()) {
            playCachedMedia()
        }
        // Reset media playing flag when session ends
        if (status.paketAktif.isEmpty() && lastPaketAktif.isNotEmpty()) {
            isPlayingMedia = false
        }
        lastPaketAktif = status.paketAktif
        val isAlmostHabis = status.timerActive && status.sisaDetik in 1..300
        if (isAlmostHabis && !currentWarningShown) {
            val menit = status.sisaDetik / 60
            val detik = status.sisaDetik % 60
            val message = "Sisa ${menit}m ${detik}d!"
            currentWarningShown = true
            _warningTriggered.value = true
            showWarningOverlay(message)
            val prefs = getSharedPreferences("tvclient_prefs", Context.MODE_PRIVATE)
            val hdmiPort = prefs.getInt(PREF_HDMI_PORT, -1)
            if (hdmiPort > 0) {
                switchToHdmi(hdmiPort)
            }
        }
        if (!isAlmostHabis) {
            currentWarningShown = false
        }
    }

    private fun switchToHdmi(port: Int) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse("hdmi://input")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("TvClientService", "HDMI switch failed: ${e.message}")
        }
    }

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
