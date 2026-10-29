package com.billingps.tvclient

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

class ScreenOnReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_SCREEN_ON && intent.action != Intent.ACTION_USER_PRESENT) return
        if (TvClientService.isRunning()) {
            // Service receiver menangani wake agar promo tidak diputar dua kali.
            return
        }
        Log.i("ScreenOnReceiver", "Screen on but service not running, restarting...")
        val serviceIntent = Intent(context, TvClientService::class.java).apply {
            action = TvClientService.ACTION_START
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}
