package com.billingps.tvclient

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File

class VideoPlayerActivity : Activity() {

    private var player: ExoPlayer? = null
    private var stopReceiver: BroadcastReceiver? = null

    private fun isImageUri(uri: Uri): Boolean {
        val path = uri.path?.lowercase() ?: return false
        return path.endsWith(".jpg") || path.endsWith(".jpeg") ||
               path.endsWith(".png") || path.endsWith(".gif") || path.endsWith(".webp")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        val mediaUri = getVideoUri()
        val layout = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
        }
        setContentView(layout)

        if (mediaUri == Uri.EMPTY) {
            finish(); return
        }

        if (isImageUri(mediaUri)) {
            val imageView = ImageView(this).apply {
                setImageBitmap(BitmapFactory.decodeFile(mediaUri.path))
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            layout.addView(imageView)
            Handler(Looper.getMainLooper()).postDelayed({ finish() }, 8000)
        } else {
            player = ExoPlayer.Builder(this).build().apply {
                setMediaItem(MediaItem.fromUri(mediaUri))
                prepare()
                playWhenReady = true
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_ENDED) {
                            finish()
                        }
                    }
                })
            }
            val playerView = PlayerView(this).apply {
                this.player = this@VideoPlayerActivity.player
                useController = false
            }
            layout.addView(playerView)
        }

        stopReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == "com.billingps.tvclient.STOP_VIDEO") {
                    finish()
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stopReceiver, IntentFilter("com.billingps.tvclient.STOP_VIDEO"), RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(stopReceiver, IntentFilter("com.billingps.tvclient.STOP_VIDEO"))
        }
    }

    private fun getVideoUri(): Uri {
        val url = intent?.getStringExtra("video_url")
        if (!url.isNullOrBlank()) return Uri.parse(url)
        val cachedPath = getSharedPreferences("tvclient_prefs", Context.MODE_PRIVATE)
            .getString(TvClientService.PREF_CACHED_MEDIA, null)
        if (cachedPath != null && File(cachedPath).exists()) {
            return Uri.fromFile(File(cachedPath))
        }
        return Uri.EMPTY
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        this.intent = intent
        val mediaUri = getVideoUri()
        if (mediaUri == Uri.EMPTY) return
        Log.i("VideoPlayerActivity", "onNewIntent: re-preparing player with $mediaUri")
        try {
            player?.stop()
            player?.setMediaItem(MediaItem.fromUri(mediaUri))
            player?.prepare()
            player?.playWhenReady = true
        } catch (e: Exception) {
            Log.e("VideoPlayerActivity", "onNewIntent re-prepare failed: ${e.message}")
        }
    }

    override fun onDestroy() {
        stopReceiver?.let { unregisterReceiver(it) }
        player?.release()
        player = null
        sendBroadcast(Intent("com.billingps.tvclient.MEDIA_STOPPED"))
        super.onDestroy()
    }
}
