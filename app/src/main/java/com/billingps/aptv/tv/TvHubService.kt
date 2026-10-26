package com.billingps.aptv.tv

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.billingps.aptv.R
import com.billingps.aptv.MainActivity

class TvHubService : Service() {
    companion object {
        const val ACTION_START = "com.billingps.aptv.action.TV_HUB_START"
        const val ACTION_STOP = "com.billingps.aptv.action.TV_HUB_STOP"
        private const val CHANNEL_ID = "tv_hub_channel"
        private const val NOTIFICATION_ID = 1002
        private const val TAG = "TvHubService"
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            TvHubManager.shutdownAll()
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        TvHubManager.startServer(applicationContext)
        TvHubManager.startAdvert(applicationContext)
        Log.i(TAG, "TV hub service aktif")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        TvHubManager.shutdownAll()
        Log.i(TAG, "TV hub service berhenti")
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "TV Hub",
                        NotificationManager.IMPORTANCE_MIN,
                    ).apply {
                        description = "Koneksi TV dan kontrol timer"
                        setShowBadge(false)
                    },
                )
            }
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, TvHubService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("RR Billing TV Hub")
            .setContentText("Siap menerima koneksi TV")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .addAction(0, "Berhenti", stopIntent)
            .build()
    }
}
