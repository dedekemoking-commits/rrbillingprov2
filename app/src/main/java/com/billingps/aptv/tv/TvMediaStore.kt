package com.billingps.aptv.tv

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.File

object TvMediaStore {
    private const val TAG = "TvMediaStore"
    private const val DIR = "tv_media"

    private fun safeId(tvId: String): String = tvId.ifBlank { "tv" }
        .replace(Regex("[^A-Za-z0-9_-]"), "_")
        .take(48)

    fun saveMedia(context: Context, tvId: String, kind: String, uri: Uri): File {
        val safe = safeId(tvId)
        val safeKind = if (kind == "lockscreen") "lockscreen" else "promo"
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val ext = extensionFor(context, uri, kind)
        val target = File(dir, "${safe}_${safeKind}.$ext")
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

    /** Nama file yang diterima TV untuk media ini (lockscreen.jpg/png/mp4). */
    fun remoteName(kind: String, file: File): String {
        if (kind != "lockscreen") return "promo.mp4"
        return "lockscreen.${extensionForFile(file)}"
    }

    fun isImage(file: File): Boolean =
        extensionForFile(file).let { it == "jpg" || it == "png" }

    private fun extensionForFile(file: File): String = when (file.extension.lowercase()) {
        "jpg", "jpeg" -> "jpg"
        "png" -> "png"
        else -> "mp4"
    }

    private fun extensionFor(context: Context, uri: Uri, kind: String): String {
        if (kind != "lockscreen") return "mp4"
        val mime = try { context.contentResolver.getType(uri)?.lowercase() } catch (_: Exception) { null }
        when (mime) {
            "image/jpeg", "image/jpg" -> return "jpg"
            "image/png" -> return "png"
            "video/mp4" -> return "mp4"
        }
        val name = displayName(context, uri)?.lowercase().orEmpty()
        val fromName = name.substringAfterLast('.', "")
        return when (fromName) {
            "jpg", "jpeg" -> "jpg"
            "png" -> "png"
            "mp4" -> "mp4"
            else -> if (mime?.startsWith("video/") == true) "mp4"
            else throw IllegalArgumentException("Format lockscreen harus JPG, PNG, atau MP4")
        }
    }

    private fun displayName(context: Context, uri: Uri): String? = try {
        context.contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    } catch (_: Exception) {
        null
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
            File(root, "${safe}_lockscreen.jpg"),
            File(root, "${safe}_lockscreen.png"),
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
