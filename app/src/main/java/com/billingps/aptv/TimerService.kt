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
import com.billingps.aptv.models.TvData
import com.billingps.aptv.utils.StorageUtil
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class TimerService : Service() {

    companion object {
        const val CHANNEL_ID = "timer_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.billingps.aptv.action.START_TIMER"
        const val ACTION_STOP = "com.billingps.aptv.action.STOP_TIMER"
        const val ACTION_TICK_BATCH = "com.billingps.aptv.action.TICK_BATCH"
        const val EXTRA_TICK_DATA = "tick_data"
        const val EXTRA_EXPIRED_IDS = "expired_ids"

        private var running = false
        fun isRunning() = running
    }

    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private var tickFuture: ScheduledFuture<*>? = null
    private var lastTickTime = 0L
    private var cachedTvList: List<TvData> = emptyList()
    private var persistCounter = 0

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.i("TimerService", "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_START) {
            ACTION_START -> {
                StorageUtil.init(this)
                if (tickFuture != null) {
                    cachedTvList = StorageUtil.loadTvList()
                    lastTickTime = System.currentTimeMillis()
                    updateNotification(cachedTvList)
                    Log.i("TimerService", "Duplicate start, reloaded ${cachedTvList.size} TVs")
                    return START_STICKY
                }
                cachedTvList = StorageUtil.loadTvList()
                lastTickTime = System.currentTimeMillis()
                startForeground(NOTIFICATION_ID, buildNotification(cachedTvList))
                running = true
                scheduleTick()
                Log.i("TimerService", "Timer started, cached ${cachedTvList.size} TVs")
            }
            ACTION_STOP -> {
                persistIfDirty()
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        running = false
        tickFuture?.cancel(false)
        persistIfDirty()
        scheduler.shutdownNow()
        stopForeground(STOP_FOREGROUND_REMOVE)
        Log.i("TimerService", "Service destroyed")
        super.onDestroy()
    }

    private fun scheduleTick() {
        tickFuture?.cancel(false)
        tickFuture = scheduler.scheduleWithFixedDelay({
            if (!running) return@scheduleWithFixedDelay
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

        var anyActive = false
        val expiredIds = mutableListOf<String>()
        var hasChanges = false
        var forcePersist = false

        val updatedList = cachedTvList.toMutableList()
        updatedList.forEachIndexed { index, tv ->
            if (tv.timerActive && !tv.bebas && tv.sisaDetik > 0) {
                hasChanges = true
                val newSisa = (tv.sisaDetik - elapsed).coerceAtLeast(0)
                updatedList[index] = tv.copy(sisaDetik = newSisa)

                if (newSisa <= 0) {
                    updatedList[index] = tv.copy(
                        timerActive = false, sisaDetik = 0L,
                        paketAktif = if (tv.sudahBayar) "" else "WAKTU HABIS",
                    )
                    expiredIds.add(tv.id)
                    forcePersist = true
                } else {
                    anyActive = true
                }
            }
            if (tv.timerActive || tv.bebas) anyActive = true
        }
        cachedTvList = updatedList

        // Persist immediately on expiry, otherwise max every 30 ticks
        if (forcePersist) {
            StorageUtil.saveTvList(cachedTvList)
            persistCounter = 0
        } else if (hasChanges) {
            persistCounter++
            if (persistCounter % 30 == 0) {
                StorageUtil.saveTvList(cachedTvList)
            }
        } else {
            persistCounter = 0
        }

        // Single batch broadcast
        val tickData = JSONObject()
        cachedTvList.forEach { tv ->
            tickData.put(tv.id, JSONObject().apply {
                put("sisaDetik", tv.sisaDetik)
                put("paketAktif", tv.paketAktif)
            })
        }
        val expiredArr = JSONArray(expiredIds.toList())
        val batchIntent = Intent(ACTION_TICK_BATCH).apply {
            putExtra(EXTRA_TICK_DATA, tickData.toString())
            putExtra(EXTRA_EXPIRED_IDS, expiredArr.toString())
        }
        sendBroadcast(batchIntent)

        // Update notification
        updateNotification(cachedTvList)

        // Stop if no active timers
        if (!anyActive) {
            persistIfDirty()
            Log.i("TimerService", "No active timers, stopping service")
            stopSelf()
        }
    }

    private fun persistIfDirty() {
        if (cachedTvList.isNotEmpty()) {
            StorageUtil.saveTvList(cachedTvList)
            cachedTvList = emptyList()
        }
    }

    private fun buildNotification(tvList: List<TvData>): Notification {
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

    private fun updateNotification(tvList: List<TvData>) {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(NOTIFICATION_ID, buildNotification(tvList))
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
