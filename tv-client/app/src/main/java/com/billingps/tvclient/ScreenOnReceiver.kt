package com.billingps.tvclient

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File

class ScreenOnReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_SCREEN_ON || intent.action == Intent.ACTION_USER_PRESENT) {
            if (TvClientService.isRunning()) {
                // Reset stale flag so wake always (re)plays cached media
                TvClientService.resetMediaPlaying()
                Log.i("ScreenOnReceiver", "Screen turned on, checking cached media")
                playCachedMedia(context)
                return
            }
            Log.i("ScreenOnReceiver", "Screen on but service not running, restarting...")
            val serviceIntent = Intent(context, TvClientService::class.java).apply {
                action = TvClientService.ACTION_START
            }
            ContextCompat.startForegroundService(context, serviceIntent)
            Handler(Looper.getMainLooper()).postDelayed({
                playCachedMedia(context)
            }, 3000)
        }
    }

    private fun playCachedMedia(context: Context) {
        val prefs = context.getSharedPreferences("tvclient_prefs", Context.MODE_PRIVATE)
        val cachedPath = prefs.getString(TvClientService.PREF_CACHED_MEDIA, null)
        if (cachedPath != null && File(cachedPath).exists()) {
            TvClientService.markMediaPlaying()
            OverlayManager.playCachedVideo(context.applicationContext, cachedPath)
            Log.i("ScreenOnReceiver", "Playing cached media via overlay: $cachedPath")
        }
    }
}
