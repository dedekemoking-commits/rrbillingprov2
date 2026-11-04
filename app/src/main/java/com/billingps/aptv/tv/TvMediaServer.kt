package com.billingps.aptv.tv

import android.content.Context
import android.util.Log
import com.billingps.aptv.utils.ApkServer
import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.Locale

object TvMediaServer {
    private const val TAG = "TvMediaServer"
    private const val PREFERRED_PORT = 18081
    private const val MEDIA_DIR = "tv_media"

    @Volatile private var rootDir: File? = null
    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var running = false
    @Volatile var port: Int = PREFERRED_PORT
        private set

    fun start(context: Context) {
        if (running) return
        synchronized(this) {
            if (running) return
            val root = File(context.filesDir, MEDIA_DIR).apply { mkdirs() }
            val socket = bindSocket()
            if (socket == null) {
                Log.e(TAG, "Media server gagal bind port $PREFERRED_PORT")
                return
            }
            rootDir = root
            serverSocket = socket
            port = socket.localPort
            running = true
            Thread({ acceptLoop(socket) }, "tv-media-server").apply {
                isDaemon = true
                start()
            }
            Log.i(TAG, "Media server aktif di port $port")
        }
    }

    fun stop() {
        running = false
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        Log.i(TAG, "Media server berhenti")
    }

    fun urlFor(context: Context, file: File): String? {
        if (!file.exists() || file.length() <= 0L) return null
        start(context)
        if (!running) return null
        val host = ApkServer.getLocalIpAddress(context)?.trim()
        if (host.isNullOrBlank()) return null
        val version = file.lastModified()
        return "http://$host:$port/media/${file.name}?v=$version"
    }

    private fun bindSocket(): ServerSocket? {
        return try {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(PREFERRED_PORT))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Port $PREFERRED_PORT dipakai, memakai port acak: ${e.message}")
            try {
                ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(0))
                }
            } catch (e2: Exception) {
                Log.e(TAG, "Bind media server gagal: ${e2.message}")
                null
            }
        }
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (running) {
            try {
                val client = socket.accept()
                Thread({ handleClient(client) }, "tv-media-client").apply {
                    isDaemon = true
                    start()
                }
            } catch (e: Exception) {
                if (!running || socket.isClosed) break
                Log.e(TAG, "Accept media gagal: ${e.message}")
            }
        }
    }

    private fun handleClient(client: Socket) {
        try {
            client.soTimeout = 15_000
            val reader = client.getInputStream().bufferedReader(Charsets.ISO_8859_1)
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            val method = parts.getOrElse(0) { "GET" }.uppercase(Locale.ROOT)
            val rawPath = parts.getOrElse(1) { "/" }.substringBefore('?')
            val path = try {
                URLDecoder.decode(rawPath, "UTF-8")
            } catch (_: Exception) {
                rawPath
            }

            var rangeHeader: String? = null
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                if (line.startsWith("Range:", ignoreCase = true)) rangeHeader = line.substringAfter(":").trim()
            }

            if (method != "GET" && method != "HEAD") {
                writeStatus(client.getOutputStream(), 405, "Method Not Allowed", "text/plain", "Method Not Allowed".toByteArray())
                return
            }
            if (!path.startsWith("/media/")) {
                writeStatus(client.getOutputStream(), 404, "Not Found", "text/plain", "Not Found".toByteArray())
                return
            }

            val root = rootDir ?: return
            val filename = path.removePrefix("/media/")
            if (filename.isBlank() || filename.contains('/') || filename.contains('\\') || filename.contains("..")) {
                writeStatus(client.getOutputStream(), 400, "Bad Request", "text/plain", "Bad Request".toByteArray())
                return
            }
            val file = File(root, filename)
            val canonicalRoot = root.canonicalFile
            val canonicalFile = file.canonicalFile
            if (canonicalFile.parentFile != canonicalRoot || !canonicalFile.exists() || !canonicalFile.isFile) {
                writeStatus(client.getOutputStream(), 404, "Not Found", "text/plain", "Not Found".toByteArray())
                return
            }

            serveFile(client.getOutputStream(), canonicalFile, rangeHeader, method == "HEAD")
        } catch (e: Exception) {
            Log.e(TAG, "Serve media gagal: ${e.message}")
        } finally {
            try { client.close() } catch (_: Exception) {}
        }
    }

    private fun serveFile(out: OutputStream, file: File, rangeHeader: String?, headOnly: Boolean) {
        val total = file.length()
        val mime = mimeType(file.name)
        val range = parseRange(rangeHeader, total)
        if (rangeHeader != null && range == null) {
            val header = "HTTP/1.1 416 Range Not Satisfiable\r\n" +
                "Content-Range: bytes */$total\r\n" +
                "Content-Length: 0\r\n" +
                "Connection: close\r\n" +
                "\r\n"
            out.write(header.toByteArray())
            out.flush()
            return
        }

        val start = range?.first ?: 0L
        val end = range?.second ?: (total - 1L).coerceAtLeast(0L)
        val length = (end - start + 1L).coerceAtLeast(0L)
        val status = if (range != null) "206 Partial Content" else "200 OK"
        val header = buildString {
            append("HTTP/1.1 ").append(status).append("\r\n")
            append("Content-Type: ").append(mime).append("\r\n")
            append("Content-Length: ").append(length).append("\r\n")
            append("Accept-Ranges: bytes\r\n")
            append("Cache-Control: no-cache\r\n")
            append("Connection: close\r\n")
            if (range != null) append("Content-Range: bytes ").append(start).append('-').append(end).append('/').append(total).append("\r\n")
            append("\r\n")
        }
        out.write(header.toByteArray())
        if (headOnly || length <= 0L) {
            out.flush()
            return
        }

        RandomAccessFile(file, "r").use { raf ->
            raf.seek(start)
            val buffer = ByteArray(64 * 1024)
            var remaining = length
            while (remaining > 0L) {
                val read = raf.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (read <= 0) break
                out.write(buffer, 0, read)
                remaining -= read
            }
        }
        out.flush()
    }

    private fun parseRange(header: String?, total: Long): Pair<Long, Long>? {
        if (header.isNullOrBlank() || total <= 0L) return null
        if (!header.startsWith("bytes=", ignoreCase = true)) return null
        val value = header.substringAfter("=").trim()
        val startText = value.substringBefore('-').trim()
        val endText = value.substringAfter('-', "").trim()
        return try {
            val start = if (startText.isBlank()) {
                val suffix = endText.toLongOrNull() ?: return null
                (total - suffix).coerceAtLeast(0L)
            } else {
                startText.toLongOrNull() ?: return null
            }
            val end = if (endText.isBlank() || startText.isBlank()) {
                total - 1L
            } else {
                endText.toLongOrNull()?.coerceAtMost(total - 1L) ?: return null
            }
            if (start < 0L || start >= total || end < start) null else start to end
        } catch (_: Exception) {
            null
        }
    }

    private fun mimeType(name: String): String = when (name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
        "mp4" -> "video/mp4"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webm" -> "video/webm"
        "gif" -> "image/gif"
        else -> "application/octet-stream"
    }

    private fun writeStatus(out: OutputStream, code: Int, status: String, mime: String, body: ByteArray) {
        val header = "HTTP/1.1 $code $status\r\n" +
            "Content-Type: $mime\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Connection: close\r\n" +
            "\r\n"
        out.write(header.toByteArray())
        out.write(body)
        out.flush()
    }
}
