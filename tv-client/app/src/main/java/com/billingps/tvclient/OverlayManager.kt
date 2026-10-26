package com.billingps.tvclient

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
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

/**
 * Menampilkan warning & video iklan sebagai system overlay window (TYPE_APPLICATION_OVERLAY).
 * Overlay tampil di atas semua aplikasi/layer (termasuk feed HDMI pada Android TV) dan
 * tidak terpengaruh background activity launch restriction.
 */
object OverlayManager {

    var onVideoEnded: (() -> Unit)? = null

    private var warningContainer: FrameLayout? = null
    private var warningPlayer: ExoPlayer? = null
    private var warningDismiss: Runnable? = null

    private var videoContainer: FrameLayout? = null
    private var videoPlayer: ExoPlayer? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    fun canDrawOverlays(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        return Settings.canDrawOverlays(context)
    }

    fun showWarning(context: Context, message: String, durationMs: Long = 8000, alignStart: Boolean = false) {
        mainHandler.post {
            try {
                hideWarningInternal()
                if (!canDrawOverlays(context)) {
                    Log.w("OverlayManager", "showWarning skipped: SYSTEM_ALERT_WINDOW not granted")
                    return@post
                }
                val prefs = context.getSharedPreferences("tvclient_prefs", Context.MODE_PRIVATE)
                val warningVideoPath = prefs.getString(TvClientService.PREF_CACHED_WARNING_VIDEO, null)
                val warningImagePath = prefs.getString(TvClientService.PREF_CACHED_WARNING_IMAGE, null)

                val overlay = FrameLayout(context).apply {
                    setBackgroundColor(Color.TRANSPARENT)
                }

                if (warningVideoPath != null && File(warningVideoPath).exists()) {
                    warningPlayer?.release()
                    warningPlayer = ExoPlayer.Builder(context).build().apply {
                        setMediaItem(MediaItem.fromUri(Uri.fromFile(File(warningVideoPath))))
                        prepare()
                        playWhenReady = true
                        repeatMode = Player.REPEAT_MODE_ONE
                        volume = 0.8f
                    }
                    val playerView = PlayerView(context).apply {
                        this.player = warningPlayer
                        useController = false
                    }
                    val videoW = (context.resources.displayMetrics.widthPixels * 0.25).toInt()
                    val videoH = (videoW * 0.5625).toInt()
                    val params = FrameLayout.LayoutParams(videoW, videoH)
                    params.gravity = Gravity.TOP or Gravity.END
                    params.topMargin = 16
                    params.rightMargin = 16
                    overlay.addView(playerView, params)
                } else if (warningImagePath != null && File(warningImagePath).exists()) {
                    val imageView = ImageView(context).apply {
                        setImageBitmap(BitmapFactory.decodeFile(warningImagePath))
                        scaleType = ImageView.ScaleType.FIT_CENTER
                    }
                    val imgSize = (context.resources.displayMetrics.widthPixels * 0.30).toInt()
                    val params = FrameLayout.LayoutParams(imgSize, imgSize)
                    params.gravity = Gravity.TOP or Gravity.END
                    params.topMargin = 16
                    params.rightMargin = 16
                    overlay.addView(imageView, params)
                }

                if (message.isNotEmpty()) {
                    val messageView = TextView(context).apply {
                        text = message
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
                    val videoH = (context.resources.displayMetrics.widthPixels * 0.25 * 0.5625).toInt()
                    if (alignStart) {
                        // PIN panggil kasir: pojok KIRI atas (terpisah dari warning sisa waktu)
                        params.gravity = Gravity.TOP or Gravity.START
                        params.topMargin = 16
                        params.leftMargin = 16
                    } else {
                        params.gravity = Gravity.TOP or Gravity.END
                        params.topMargin = 16 + videoH + 16
                        params.rightMargin = 16
                    }
                    overlay.addView(messageView, params)
                }

                addOverlay(context, overlay)
                warningContainer = overlay
                Log.i("OverlayManager", "Warning overlay shown: $message")
            } catch (e: Exception) {
                Log.e("OverlayManager", "showWarning failed: ${e.message}")
            }
        }

        warningDismiss?.let { mainHandler.removeCallbacks(it) }
        warningDismiss = Runnable { hideWarning() }
        mainHandler.postDelayed(warningDismiss!!, durationMs)
    }

    fun hideWarning() {
        mainHandler.post {
            hideWarningInternal()
        }
    }

    private fun hideWarningInternal() {
        try {
            warningContainer?.let { removeOverlay(it) }
        } catch (e: Exception) {
            Log.e("OverlayManager", "hideWarning remove failed: ${e.message}")
        }
        warningContainer = null
        try {
            warningPlayer?.release()
        } catch (_: Exception) { }
        warningPlayer = null
    }

    fun playCachedVideo(context: Context, cachedPath: String) {
        mainHandler.post {
            try {
                stopVideoInternal()
                if (!canDrawOverlays(context)) {
                    Log.w("OverlayManager", "playCachedVideo skipped: SYSTEM_ALERT_WINDOW not granted")
                    onVideoEnded?.invoke()
                    return@post
                }
                val file = File(cachedPath)
                if (!file.exists()) {
                    Log.w("OverlayManager", "playCachedVideo skipped: cache missing $cachedPath")
                    onVideoEnded?.invoke()
                    return@post
                }

                val overlay = FrameLayout(context).apply {
                    setBackgroundColor(Color.BLACK)
                }

                videoPlayer = ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                    prepare()
                    playWhenReady = true
                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            if (state == Player.STATE_ENDED) {
                                Log.i("OverlayManager", "Video ended, closing overlay")
                                stopVideo()
                            }
                        }
                    })
                }
                val playerView = PlayerView(context).apply {
                    this.player = videoPlayer
                    useController = false
                }
                overlay.addView(playerView, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ))

                addOverlay(context, overlay)
                videoContainer = overlay
                Log.i("OverlayManager", "Video overlay playing: $cachedPath")
            } catch (e: Exception) {
                Log.e("OverlayManager", "playCachedVideo failed: ${e.message}")
                onVideoEnded?.invoke()
            }
        }
    }

    fun stopVideo() {
        mainHandler.post {
            stopVideoInternal()
            onVideoEnded?.invoke()
        }
    }

    private fun stopVideoInternal() {
        try {
            videoContainer?.let { removeOverlay(it) }
        } catch (e: Exception) {
            Log.e("OverlayManager", "stopVideo remove failed: ${e.message}")
        }
        videoContainer = null
        try {
            videoPlayer?.release()
        } catch (_: Exception) { }
        videoPlayer = null
    }

    fun cleanup() {
        mainHandler.post {
            hideWarningInternal()
            stopVideoInternal()
            onVideoEnded = null
        }
    }

    private fun addOverlay(context: Context, view: View) {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        wm.addView(view, params)
    }

    private fun removeOverlay(view: View) {
        val wm = view.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm.removeView(view)
    }
}
