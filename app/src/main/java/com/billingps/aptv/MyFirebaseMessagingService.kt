package com.billingps.aptv

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.billingps.aptv.models.AppNotification
import com.billingps.aptv.utils.NotificationEventBus
import com.billingps.aptv.utils.StorageUtil
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    private var notificationIdCounter = 0

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i("FCM", "New token: $token")
        StorageUtil.saveFcmToken(token)
        val username = StorageUtil.loadCurrentUser()
        if (username.isNotEmpty()) {
            try {
                FirebaseFirestore.getInstance()
                    .collection("billingps_users").document(username)
                    .set(mapOf("fcmToken" to token), SetOptions.merge())
            } catch (e: Exception) { Log.e("FCM", "save token to firestore: ${e.message}") }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.i("FCM", "Message received: ${message.data}")
        val title = message.data["title"] ?: message.notification?.title ?: "RR BILLING Pro"
        val body = message.data["body"] ?: message.notification?.body ?: ""
        val msgType = message.data["type"] ?: "info"
        showNotification(title, body, msgType)
        saveNotificationLocally(title, body, msgType)
        if (msgType == "admin") {
            NotificationEventBus.emit(
                AppNotification(
                    id = "${System.currentTimeMillis()}",
                    title = title,
                    body = body,
                    type = "admin",
                    sentAt = System.currentTimeMillis(),
                )
            )
        }
    }

    private fun saveNotificationLocally(title: String, body: String, type: String) {
        try {
            val notif = AppNotification(
                id = "${System.currentTimeMillis()}",
                title = title,
                body = body,
                type = type,
                sentAt = System.currentTimeMillis(),
            )
            val current = StorageUtil.loadNotifications().toMutableList()
            current.add(0, notif)
            if (current.size > 100) {
                val excess = current.size - 100
                for (i in 0 until excess) current.removeAt(current.lastIndex)
            }
            StorageUtil.saveNotifications(current)
        } catch (e: Exception) {
            Log.e("FCM", "saveNotificationLocally failed: ${e.message}")
        }
    }

    private fun showNotification(title: String, body: String, type: String = "info") {
        val channelId = "billing_channel"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Billing Notifikasi", NotificationManager.IMPORTANCE_HIGH)
            nm.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notif = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        nm.notify(++notificationIdCounter, notif)
    }
}
