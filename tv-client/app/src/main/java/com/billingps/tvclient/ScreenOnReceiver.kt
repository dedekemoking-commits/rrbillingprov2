package com.billingps.tvclient

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.File

class ScreenOnReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_SCREEN_ON || intent.action == Intent.ACTION_USER_PRESENT) {
            if (TvClientService.isMediaPlaying()) {
                Log.i("ScreenOnReceiver", "Screen on but media already playing, skipping")
                return
            }
            if (!TvClientService.isRunning()) {
                Log.i("ScreenOnReceiver", "Screen on but service not running, skipping")
                return
            }
            Log.i("ScreenOnReceiver", "Screen turned on, checking cached media")
            val prefs = context.getSharedPreferences("tvclient_prefs", Context.MODE_PRIVATE)
            val cachedPath = prefs.getString(TvClientService.PREF_CACHED_MEDIA, null)
            if (cachedPath != null && File(cachedPath).exists()) {
                val playIntent = Intent(context, VideoPlayerActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                }
                context.startActivity(playIntent)
            }
        }
    }
}
