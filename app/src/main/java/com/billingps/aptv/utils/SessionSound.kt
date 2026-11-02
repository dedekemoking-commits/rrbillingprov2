package com.billingps.aptv.utils

import android.content.Context
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.File

object SessionSound {
    private const val FILE_PREFIX = "session_expired_sound"

    fun isCustom(): Boolean = StorageUtil.loadExpiredSoundUri().isNotBlank()

    fun currentName(): String {
        if (!isCustom()) return "Default sistem"
        return StorageUtil.loadExpiredSoundName().ifBlank { "Suara pilihan" }
    }

    fun install(context: Context, source: Uri): Boolean {
        val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        if (defaultUri != null && source == defaultUri) {
            reset(context)
            return true
        }
        return try {
            val title = displayName(context, source) ?: try {
                RingtoneManager.getRingtone(context, source)?.getTitle(context)
                    ?.takeUnless { it.startsWith("msf:") }
            } catch (_: Exception) {
                null
            }
            val extension = when (context.contentResolver.getType(source)) {
                "audio/mpeg" -> "mp3"
                "audio/mp4", "audio/m4a", "audio/x-m4a" -> "m4a"
                "audio/wav", "audio/x-wav" -> "wav"
                "audio/ogg" -> "ogg"
                else -> "ogg"
            }
            val target = File(context.filesDir, "$FILE_PREFIX.$extension")
            val input = context.contentResolver.openInputStream(source) ?: return false
            input.use { stream ->
                target.outputStream().use { output -> stream.copyTo(output) }
            }
            context.filesDir.listFiles()
                ?.filter { it.name.startsWith("$FILE_PREFIX.") && it != target }
                ?.forEach { it.delete() }
            StorageUtil.saveExpiredSound(
                Uri.fromFile(target).toString(),
                title?.takeIf { it.isNotBlank() } ?: "Suara pilihan",
            )
            true
        } catch (e: Exception) {
            Log.w("SessionSound", "install failed: ${e.message}")
            false
        }
    }

    fun reset(context: Context) {
        StorageUtil.clearExpiredSound()
        context.filesDir.listFiles()
            ?.filter { it.name.startsWith("$FILE_PREFIX.") }
            ?.forEach { it.delete() }
    }

    private fun displayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index < 0) null else cursor.getString(index)?.takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun play(context: Context): Ringtone? {
        return try {
            val uri = resolveUri() ?: return null
            RingtoneManager.getRingtone(context.applicationContext, uri)?.also { it.play() }
        } catch (e: Exception) {
            Log.w("SessionSound", "play failed: ${e.message}")
            null
        }
    }

    internal fun resolveUri(): Uri? {
        val raw = StorageUtil.loadExpiredSoundUri()
        if (raw.isNotBlank()) {
            val saved = Uri.parse(raw)
            if (saved.scheme == "file") {
                val path = saved.path
                if (!path.isNullOrBlank() && File(path).exists()) return saved
            } else {
                return saved
            }
        }
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    }
}
