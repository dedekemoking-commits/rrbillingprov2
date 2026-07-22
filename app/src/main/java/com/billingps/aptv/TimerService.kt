package com.billingps.aptv

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.billingps.aptv.utils.StorageUtil
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class TimerService : Service() {

    companion object {
        const val CHANNEL_ID = "timer_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.billingps.aptv.action.START_TIMER"
        const val ACTION_STOP = "com.billingps.aptv.action.STOP_TIMER"
        const val ACTION_TICK = "com.billingps.aptv.action.TICK"
        const val ACTION_TIMER_EXPIRED = "com.billingps.aptv.action.TIMER_EXPIRED"
        const val EXTRA_TV_ID = "tv_id"
        const val EXTRA_SISA_DETIK = "sisa_detik"
        const val EXTRA_PAKET_AKTIF = "paket_aktif"

        private var running = false
        fun isRunning() = running
    }

    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private var lastTickTime = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        running = true
        Log.i("TimerService", "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                StorageUtil.init(this)
                lastTickTime = System.currentTimeMillis()
                startForeground(NOTIFICATION_ID, buildNotification())
                scheduleTick()
                Log.i("TimerService", "Timer started")
            }
            ACTION_STOP -> {
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        running = false
        scheduler.shutdownNow()
        stopForeground(STOP_FOREGROUND_REMOVE)
        Log.i("TimerService", "Service destroyed")
        super.onDestroy()
    }

    private fun scheduleTick() {
        scheduler.scheduleAtFixedRate({
            if (!running) return@scheduleAtFixedRate
            try {
                tick()
            } catch (e: Exception) {
                Log.e("TimerService", "Tick error: ${e.message}")
            }
        }, 1, 1, TimeUnit.SECONDS)
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        val elapsed = (now - lastTickTime) / 1000
        if (elapsed < 1) return
        lastTickTime = now

        val tvList = StorageUtil.loadTvList().toMutableList()
        var anyActive = false
        val expiredIds = mutableListOf<String>()

        tvList.forEachIndexed { index, tv ->
            if (tv.timerActive && !tv.bebas && tv.sisaDetik > 0) {
                val newSisa = (tv.sisaDetik - elapsed).coerceAtLeast(0)
                tvList[index] = tv.copy(sisaDetik = newSisa)

                if (newSisa <= 0) {
                    tvList[index] = tv.copy(
                        timerActive = false, sisaDetik = 0L,
                        paketAktif = "WAKTU HABIS",
                    )
                    expiredIds.add(tv.id)
                } else {
                    anyActive = true
                }
            }
            if (tv.timerActive || tv.bebas) anyActive = true
        }

        // Save updated list to storage
        StorageUtil.saveTvList(tvList.toList())

        // Send broadcast for UI updates
        tvList.forEach { tv ->
            val tickIntent = Intent(ACTION_TICK).apply {
                putExtra(EXTRA_TV_ID, tv.id)
                putExtra(EXTRA_SISA_DETIK, tv.sisaDetik)
                putExtra(EXTRA_PAKET_AKTIF, tv.paketAktif)
            }
            sendBroadcast(tickIntent)
        }

        // Send expired broadcasts
        expiredIds.forEach { id ->
            val expiredIntent = Intent(ACTION_TIMER_EXPIRED).apply {
                putExtra(EXTRA_TV_ID, id)
            }
            sendBroadcast(expiredIntent)
        }

        // Update notification
        updateNotification()

        // Stop if no active timers
        if (!anyActive) {
            Log.i("TimerService", "No active timers, stopping service")
            stopSelf()
        }
    }

    private fun buildNotification(): Notification {
        val tvList = StorageUtil.loadTvList()
        val activeCount = tvList.count { it.timerActive || it.bebas }
        val contentText = if (activeCount > 0) "$activeCount timer aktif" else "Tidak ada timer aktif"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("RR Billing Pro")
            .setContentText(contentText)
            .setSmallIcon(com.billingps.aptv.R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Timer Billing",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Notifikasi timer billing berjalan di latar belakang"
                setSound(null, null)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }
}
