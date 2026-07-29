package com.billingps.tvclient

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File

class WarningOverlayActivity : Activity() {

    private var videoPlayer: ExoPlayer? = null
    private var countDownTimer: CountDownTimer? = null
    private lateinit var overlayLayout: FrameLayout
    private var messageView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        overlayLayout = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        setContentView(overlayLayout)

        buildOverlay(intent)
        startDismissTimer()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        this.intent = intent
        buildOverlay(intent)
        startDismissTimer()
    }

    private fun buildOverlay(intent: Intent) {
        overlayLayout.removeAllViews()

        val message = intent.getStringExtra("message") ?: ""

        val prefs = getSharedPreferences("tvclient_prefs", Context.MODE_PRIVATE)
        val warningVideoPath = prefs.getString(TvClientService.PREF_CACHED_WARNING_VIDEO, null)
        val warningImagePath = prefs.getString(TvClientService.PREF_CACHED_WARNING_IMAGE, null)

        if (warningVideoPath != null && File(warningVideoPath).exists()) {
            videoPlayer?.release()
            videoPlayer = ExoPlayer.Builder(this).build().apply {
                val mediaItem = MediaItem.fromUri(Uri.fromFile(File(warningVideoPath)))
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_ONE
                volume = 0.8f
            }
            val playerView = PlayerView(this).apply {
                this.player = videoPlayer
                useController = false
            }
            val videoW = (resources.displayMetrics.widthPixels * 0.25).toInt()
            val videoH = (videoW * 0.5625).toInt()
            val params = FrameLayout.LayoutParams(videoW, videoH)
            params.gravity = Gravity.TOP or Gravity.END
            params.topMargin = 16
            params.rightMargin = 16
            overlayLayout.addView(playerView, params)
        } else if (warningImagePath != null && File(warningImagePath).exists()) {
            val imageView = ImageView(this).apply {
                setImageBitmap(BitmapFactory.decodeFile(warningImagePath))
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            val imgSize = (resources.displayMetrics.widthPixels * 0.30).toInt()
            val params = FrameLayout.LayoutParams(imgSize, imgSize)
            params.gravity = Gravity.TOP or Gravity.END
            params.topMargin = 16
            params.rightMargin = 16
            overlayLayout.addView(imageView, params)
        }

        if (message.isNotEmpty()) {
            messageView?.let { overlayLayout.removeView(it) }
            messageView = TextView(this).apply {
                this.text = message
                setTextColor(0xFFFC0000.toInt())
                textSize = 28f
                gravity = Gravity.START
                setPadding(20, 12, 20, 12)
                setBackgroundColor(0xAA000000.toInt())
            }
            val params = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            params.gravity = Gravity.TOP or Gravity.END
            val videoH = (resources.displayMetrics.widthPixels * 0.25 * 0.5625).toInt()
            params.topMargin = 16 + videoH + 16
            params.rightMargin = 16
            messageView?.let { overlayLayout.addView(it, params) }
        }
    }

    private fun startDismissTimer() {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                finish()
            }
        }.start()
    }

    override fun onDestroy() {
        countDownTimer?.cancel()
        videoPlayer?.release()
        videoPlayer = null
        super.onDestroy()
    }
}
