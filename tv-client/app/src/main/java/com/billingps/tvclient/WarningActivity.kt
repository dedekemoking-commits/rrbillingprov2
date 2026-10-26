package com.billingps.tvclient

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class WarningActivity : Activity() {

    private val autoDismissHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)

        val message = intent?.getStringExtra("message") ?: "⚠️ Waktu hampir habis!"

        val layout = FrameLayout(this).apply {
            setBackgroundColor(0xCC000000.toInt())
        }

        val textView = TextView(this).apply {
            this.text = message
            setTextColor(0xFFFF4500.toInt())
            textSize = 48f
            gravity = Gravity.CENTER
            setPadding(48, 24, 48, 24)
        }

        layout.addView(textView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))

        setContentView(layout)

        // Turunkan volume TV saat warning muncul
        var originalVolume = -1
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 3, 0)
        } catch (e: Exception) {
            Log.e("WarningActivity", "Volume change failed: ${e.message}")
        }

        val volToRestore = originalVolume
        autoDismissHandler.postDelayed({
            if (volToRestore >= 0) {
                try {
                    val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volToRestore, 0)
                } catch (e: Exception) {
                    Log.e("WarningActivity", "Volume restore failed: ${e.message}")
                }
            }
            finish()
        }, 6000)

        scope.launch {
            delay(6000)
            finish()
        }
    }

    override fun onDestroy() {
        autoDismissHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
