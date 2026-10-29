package com.billingps.aptv.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.billingps.aptv.models.PrayerConfig
import com.billingps.aptv.tv.TvHubManager

object PrayerAlarmScheduler {
    const val ACTION_PRAYER_TIME = "com.billingps.aptv.action.PRAYER_TIME"
    const val EXTRA_PRAYER_NAME = "prayer_name"
    private const val REQUEST_CODE = 4101
    private const val TAG = "PrayerAlarm"

    fun reschedule(context: Context, config: PrayerConfig) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val cancelIntent = Intent(context, PrayerAlarmReceiver::class.java)
            .setAction(ACTION_PRAYER_TIME)
        val cancelPending = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.cancel(cancelPending)
        if (!config.enabled || !config.locationSet) return
        val next = PrayerTimes.nextPrayer(config) ?: return
        val intent = Intent(context, PrayerAlarmReceiver::class.java)
            .setAction(ACTION_PRAYER_TIME)
            .putExtra(EXTRA_PRAYER_NAME, next.name)
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(next.atMillis, pending), pending)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.atMillis, pending)
            }
            Log.i(TAG, "Alarm ${next.name} dijadwalkan pada ${next.label}")
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.atMillis, pending)
            Log.w(TAG, "Exact alarm ditolak, memakai alarm tidak tepat: ${e.message}")
        }
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, PrayerAlarmReceiver::class.java).setAction(ACTION_PRAYER_TIME)
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.cancel(pending)
    }
}

class PrayerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        StorageUtil.init(context)
        if (StorageUtil.loadCurrentUser().isBlank()) return
        val config = StorageUtil.loadPrayerConfig()
        if (!config.enabled) return
        val prayerName = intent.getStringExtra(PrayerAlarmScheduler.EXTRA_PRAYER_NAME).orEmpty().ifBlank { "Sholat" }
        val message = config.message
            .replace("{sholat}", prayerName)
            .ifBlank { "Waktu sholat $prayerName telah masuk." }
        val pendingResult = goAsync()
        Thread {
            try {
                val tvs = StorageUtil.loadTvList()
                if (tvs.isNotEmpty()) {
                    if (!TvHubManager.isServerRunning()) {
                        TvHubManager.updateConfig(tvs, rentalName(), clientEnabled = true)
                        TvHubManager.ensureStarted(context)
                        Thread.sleep(3_500)
                    }
                    for (attempt in 0 until 4) {
                        if (attempt > 0) Thread.sleep(1_200)
                        if (TvHubManager.broadcastMessage(message, config.durationSec) > 0) break
                    }
                }
            } catch (e: Exception) {
                Log.e("PrayerAlarm", "Gagal mengirim popup sholat: ${e.message}", e)
            } finally {
                PrayerAlarmScheduler.reschedule(context, config)
                pendingResult.finish()
            }
        }.start()
    }

    private fun rentalName(): String {
        val users = StorageUtil.loadUsers()
        val current = StorageUtil.loadCurrentUser().lowercase().trim()
        return users[current]?.namaRental?.ifBlank { null }
            ?: users.values.firstOrNull { it.role == "admin" || it.role == "owner" }?.namaRental?.ifBlank { null }
            ?: "RR Billing Pro"
    }
}

class PrayerBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != Intent.ACTION_TIME_CHANGED &&
            action != Intent.ACTION_TIMEZONE_CHANGED
        ) return
        StorageUtil.init(context)
        if (StorageUtil.loadCurrentUser().isNotBlank()) {
            PrayerAlarmScheduler.reschedule(context, StorageUtil.loadPrayerConfig())
        }
    }
}
