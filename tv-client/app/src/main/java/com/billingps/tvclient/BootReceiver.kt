package com.billingps.tvclient

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("tvclient_prefs", Context.MODE_PRIVATE)
            val username = prefs.getString(TvClientService.PREF_USERNAME, "") ?: ""
            val tvId = prefs.getString(TvClientService.PREF_TV_ID, "") ?: ""
            if (username.isNotEmpty() && tvId.isNotEmpty()) {
                val serviceIntent = Intent(context, TvClientService::class.java).apply {
                    action = TvClientService.ACTION_START
                }
                context.startForegroundService(serviceIntent)
            }
        }
    }
}
