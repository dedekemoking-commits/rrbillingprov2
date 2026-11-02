package com.billingps.aptv.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log

object SessionAlert {
    private var loopPlayer: MediaPlayer? = null
    private var oncePlayer: MediaPlayer? = null

    @Synchronized
    fun startLoop(context: Context): Boolean {
        if (loopPlayer != null) return true
        val player = create(context) ?: return false
        return try {
            player.isLooping = true
            player.setOnErrorListener { mp, what, extra ->
                Log.w("SessionAlert", "loop error what=$what extra=$extra")
                synchronized(this) {
                    if (loopPlayer === mp) loopPlayer = null
                }
                runCatching { mp.release() }
                true
            }
            player.start()
            loopPlayer = player
            true
        } catch (e: Exception) {
            Log.w("SessionAlert", "startLoop failed: ${e.message}")
            runCatching { player.release() }
            false
        }
    }

    @Synchronized
    fun playOnce(context: Context, onComplete: (() -> Unit)? = null): Boolean {
        releaseOnce()
        val player = create(context) ?: return false
        return try {
            player.isLooping = false
            player.setOnCompletionListener { finishOnce(it, onComplete) }
            player.setOnErrorListener { mp, what, extra ->
                Log.w("SessionAlert", "once error what=$what extra=$extra")
                finishOnce(mp, onComplete)
                true
            }
            player.start()
            oncePlayer = player
            true
        } catch (e: Exception) {
            Log.w("SessionAlert", "playOnce failed: ${e.message}")
            runCatching { player.release() }
            false
        }
    }

    @Synchronized
    fun stopLoop() {
        releaseLoop()
    }

    @Synchronized
    fun stopOnce() {
        releaseOnce()
    }

    @Synchronized
    fun stopAll() {
        releaseLoop()
        releaseOnce()
    }

    private fun finishOnce(player: MediaPlayer, onComplete: (() -> Unit)?) {
        synchronized(this) {
            if (oncePlayer === player) oncePlayer = null
        }
        runCatching { player.release() }
        onComplete?.invoke()
    }

    private fun releaseLoop() {
        loopPlayer?.let { player ->
            runCatching { player.stop() }
            runCatching { player.release() }
        }
        loopPlayer = null
    }

    private fun releaseOnce() {
        oncePlayer?.let { player ->
            runCatching { player.stop() }
            runCatching { player.release() }
        }
        oncePlayer = null
    }

    private fun create(context: Context): MediaPlayer? {
        val uri = SessionSound.resolveUri() ?: return null
        val player = MediaPlayer()
        return try {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            if (uri.scheme == "file") {
                val path = uri.path
                if (path.isNullOrBlank()) {
                    player.release()
                    return null
                }
                player.setDataSource(path)
            } else {
                player.setDataSource(context.applicationContext, uri)
            }
            player.prepare()
            player
        } catch (e: Exception) {
            Log.w("SessionAlert", "create failed: ${e.message}")
            runCatching { player.release() }
            null
        }
    }
}
