package com.billingps.aptv.tv

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File

object TvMediaStore {
    private const val TAG = "TvMediaStore"
    private const val DIR = "tv_media"

    private fun safeId(tvId: String): String = tvId.ifBlank { "tv" }
        .replace(Regex("[^A-Za-z0-9_-]"), "_")
        .take(48)

    fun saveVideo(context: Context, tvId: String, kind: String, uri: Uri): File {
        val safe = safeId(tvId)
        val safeKind = if (kind == "lockscreen") "lockscreen" else "promo"
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val target = File(dir, "${safe}_${safeKind}.mp4")
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("File tidak bisa dibaca")
        input.use { source ->
            target.outputStream().use { output ->
                source.copyTo(output)
            }
        }
        Log.i(TAG, "Media disimpan: ${target.absolutePath} (${target.length()} byte)")
        return target
    }

    fun deleteForTv(context: Context, tvId: String, paths: List<String> = emptyList()) {
        val dir = File(context.filesDir, DIR)
        val root = try {
            dir.canonicalFile
        } catch (_: Exception) {
            return
        }
        if (!root.exists()) return
        val safe = safeId(tvId)
        val targets = linkedSetOf(
            File(root, "${safe}_promo.mp4"),
            File(root, "${safe}_lockscreen.mp4"),
        )
        paths.forEach { path ->
            if (path.isBlank()) return@forEach
            try {
                val file = File(path).canonicalFile
                if (file.parentFile == root) targets.add(file)
            } catch (_: Exception) {
            }
        }
        targets.forEach { file ->
            try {
                if (file.exists()) file.delete()
            } catch (_: Exception) {
            }
        }
        try {
            if (root.list()?.isEmpty() == true) root.delete()
        } catch (_: Exception) {
        }
    }

    fun delete(path: String) {
        if (path.isBlank()) return
        try {
            File(path).delete()
        } catch (_: Exception) {
        }
    }
}
