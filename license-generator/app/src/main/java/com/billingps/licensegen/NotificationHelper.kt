package com.billingps.licensegen

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

object NotificationHelper {

    const val CHANNEL_ID = "license_requests"
    const val CHANNEL_NAME = "Request Lisensi"
    const val CHANNEL_DESC = "Notifikasi request lisensi baru"

    private var appContext: Context? = null

    fun init(ctx: Context) {
        appContext = ctx.applicationContext
        createChannelLocked()
    }

    private fun createChannelLocked() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ctx = appContext ?: return
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                description = CHANNEL_DESC
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(channel)
            android.util.Log.i("NotifHelper", "Channel created: $CHANNEL_ID")
        }
    }

    fun show(title: String, body: String) {
        val ctx = appContext ?: run {
            android.util.Log.w("NotifHelper", "appContext null, skipping notification")
            return
        }

        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            ctx, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notifId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
            nm.notify(notifId, notification)
            android.util.Log.i("NotifHelper", "Notification posted: id=$notifId title=$title")
        } catch (e: SecurityException) {
            android.util.Log.w("NotifHelper", "POST_NOTIFICATIONS not granted: ${e.message}")
        } catch (e: Exception) {
            android.util.Log.e("NotifHelper", "Notification error: ${e.message}", e)
        }
    }
}
