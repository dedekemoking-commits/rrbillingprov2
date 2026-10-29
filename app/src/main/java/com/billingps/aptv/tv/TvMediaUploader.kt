package com.billingps.aptv.tv

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.billingps.aptv.models.TvData
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object TvMediaUploader {
    private const val TAG = "TvMediaUploader"
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun upload(
        tv: TvData,
        file: File,
        kind: String,
        callback: (Boolean, String) -> Unit,
    ) {
        executor.execute {
            try {
                val host = tv.ip.trim()
                    .removePrefix("http://")
                    .removePrefix("https://")
                    .substringBefore("/")
                    .substringBefore(":")
                if (host.isBlank()) {
                    return@execute finish(callback, false, "IP TV belum diisi")
                }
                if (!file.exists() || file.length() <= 0L) {
                    return@execute finish(callback, false, "File media tidak valid")
                }
                val name = TvMediaStore.remoteName(kind, file)
                val url = URL("http://$host:8089/upload?name=$name")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 8_000
                    readTimeout = 20_000
                    setRequestProperty("Content-Type", "application/octet-stream")
                    setFixedLengthStreamingMode(file.length())
                }
                try {
                    file.inputStream().use { input ->
                        conn.outputStream.use { output -> input.copyTo(output) }
                    }
                    val code = conn.responseCode
                    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                    val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
                    if (code in 200..299) {
                        finish(callback, true, "Upload berhasil (${file.length() / 1024} KB)")
                    } else {
                        finish(callback, false, "Upload gagal HTTP $code ${body.take(120)}")
                    }
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Upload ke ${tv.ip} gagal", e)
                finish(callback, false, e.message ?: "Upload gagal")
            }
        }
    }

    private fun finish(callback: (Boolean, String) -> Unit, ok: Boolean, message: String) {
        mainHandler.post { callback(ok, message) }
    }
}
