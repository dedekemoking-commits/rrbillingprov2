package com.billingps.aptv.utils

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import java.io.File
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException

object ApkServer {
    @Volatile
    var clientAllowed: Boolean = true


    private const val PORT = 18080
    private var serverThread: Thread? = null
    private var serverSocket: ServerSocket? = null
    private var apkFile: File? = null
    private var uploadsDir: File? = null
    @Volatile private var running = false

    val port: Int get() = PORT

    fun start(context: Context, apk: File) {
        if (running) stop()
        apkFile = apk
        uploadsDir = File(context.cacheDir, "uploads").also { it.mkdirs() }
        running = true
        serverThread = Thread({
            try {
                serverSocket = ServerSocket()
                serverSocket?.bind(InetSocketAddress(PORT))
                serverSocket?.soTimeout = 30000
                Log.i("ApkServer", "HTTP server started on port $PORT")
                while (running) {
                    try {
                        val client = serverSocket?.accept() ?: continue
                        Thread({
                            handleClient(client)
                        }).apply { start() }
                    } catch (e: SocketTimeoutException) {

                    } catch (e: Exception) {
                        if (running) Log.e("ApkServer", "Accept error: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                if (running) Log.e("ApkServer", "Server error: ${e.message}")
            }
        }, "apk-server")
        serverThread?.start()
    }

    fun stop() {
        running = false
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        serverThread?.interrupt()
        serverThread = null
        Log.i("ApkServer", "HTTP server stopped")
    }

    private fun handleClient(client: Socket) {
        try {
            client.soTimeout = 15000

            val input = client.getInputStream()
            val requestBuf = ByteArray(8192)
            val bytesRead = try {
                input.read(requestBuf)
            } catch (e: Exception) {
                -1
            }

            if (bytesRead <= 0) {
                client.close()
                return
            }

            val request = String(requestBuf, 0, bytesRead)
            val lines = request.split("\r\n")
            val firstLine = lines.firstOrNull() ?: ""
            val parts = firstLine.split(" ")
            val method = parts.getOrElse(0) { "GET" }
            val requestedPath = parts.getOrElse(1) { "/" }

            when {
                method == "POST" && requestedPath.startsWith("/upload") -> {
                    handleUpload(client, requestBuf, bytesRead, lines)
                }
                requestedPath.startsWith("/uploads/") -> {
                    serveUploadedFile(client.getOutputStream(), requestedPath)
                }
                requestedPath.contains("tv-client.apk") || requestedPath == "/" -> {
                    if (clientAllowed) {
                        sendApk(client.getOutputStream())
                    } else {
                        sendForbidden(client.getOutputStream())
                    }
                }
                else -> {
                    send404(client.getOutputStream())
                }
            }

            client.close()
        } catch (e: Exception) {
            Log.e("ApkServer", "Client error: ${e.message}")
            try { client.close() } catch (_: Exception) {}
        }
    }

    private fun handleUpload(client: Socket, buf: ByteArray, bytesRead: Int, headers: List<String>) {
        try {
            val contentLength = headers.firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }
                ?.split(":")?.getOrElse(1) { "" }?.trim()?.toIntOrNull() ?: return
            val disposition = headers.firstOrNull { it.startsWith("Content-Disposition:", ignoreCase = true) }
            val filename = disposition?.let { parseFilename(it) } ?: "upload_${System.currentTimeMillis()}"

            val outFile = File(uploadsDir, filename)
            val input = client.getInputStream()
            outFile.outputStream().use { out ->
                // Write already-read body from buf
                val headerEnd = String(buf, 0, bytesRead).indexOf("\r\n\r\n") + 4
                val bodyStart = headerEnd
                val alreadyRead = bytesRead - bodyStart
                if (alreadyRead > 0) {
                    out.write(buf, bodyStart, alreadyRead)
                }
                // Read remaining body
                val remaining = contentLength - alreadyRead
                if (remaining > 0) {
                    val buffer = ByteArray(8192)
                    var toRead = remaining
                    while (toRead > 0) {
                        val n = input.read(buffer, 0, minOf(buffer.size, toRead.toInt()))
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        toRead -= n
                    }
                }
            }

            val urlPath = "/uploads/${outFile.name}"
            val body = """{"url":"$urlPath","filename":"${outFile.name}"}"""
            val header = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: application/json\r\n" +
                    "Content-Length: ${body.length}\r\n" +
                    "Connection: close\r\n" +
                    "\r\n"
            client.getOutputStream().write(header.toByteArray())
            client.getOutputStream().write(body.toByteArray())
            client.getOutputStream().flush()
            Log.i("ApkServer", "Upload saved: ${outFile.name} ($contentLength bytes)")
        } catch (e: Exception) {
            Log.e("ApkServer", "Upload error: ${e.message}")
            val body = """{"error":"${e.message}"}"""
            val header = "HTTP/1.1 500 Internal Server Error\r\n" +
                    "Content-Type: application/json\r\n" +
                    "Content-Length: ${body.length}\r\n" +
                    "Connection: close\r\n" +
                    "\r\n"
            try {
                client.getOutputStream().write(header.toByteArray())
                client.getOutputStream().write(body.toByteArray())
                client.getOutputStream().flush()
            } catch (_: Exception) {}
        }
    }

    private fun serveUploadedFile(out: OutputStream, path: String) {
        val dir = uploadsDir ?: return
        val filename = path.removePrefix("/uploads/").substringBefore("?")
        val file = File(dir, filename)
        if (!file.exists() || !file.isFile) {
            send404(out)
            return
        }
        val fileLen = file.length()
        val mime = when {
            filename.endsWith(".mp4") -> "video/mp4"
            filename.endsWith(".jpg") || filename.endsWith(".jpeg") -> "image/jpeg"
            filename.endsWith(".png") -> "image/png"
            filename.endsWith(".gif") -> "image/gif"
            filename.endsWith(".webm") -> "video/webm"
            else -> "application/octet-stream"
        }
        val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: $mime\r\n" +
                "Content-Length: $fileLen\r\n" +
                "Connection: close\r\n" +
                "Accept-Ranges: bytes\r\n" +
                "\r\n"
        try {
            out.write(header.toByteArray())
            file.inputStream().use { input -> input.copyTo(out, bufferSize = 8192) }
            out.flush()
        } catch (e: Exception) {
            Log.e("ApkServer", "Send upload error: ${e.message}")
        }
    }

    private fun parseFilename(disposition: String): String {
        val parts = disposition.split(";")
        for (part in parts) {
            val trimmed = part.trim()
            if (trimmed.startsWith("filename=", ignoreCase = true)) {
                var name = trimmed.substringAfter("=").trim('"', ' ')
                if (name.isNotEmpty()) return name
            }
        }
        return "upload_${System.currentTimeMillis()}"
    }

    private fun sendApk(out: OutputStream) {
        val file = apkFile ?: return
        val fileLen = file.length()

        val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/vnd.android.package-archive\r\n" +
                "Content-Disposition: attachment; filename=\"${file.name}\"\r\n" +
                "Content-Length: $fileLen\r\n" +
                "Connection: close\r\n" +
                "Accept-Ranges: none\r\n" +
                "\r\n"

        try {
            out.write(header.toByteArray())
            file.inputStream().use { input ->
                input.copyTo(out, bufferSize = 8192)
            }
            out.flush()
        } catch (e: Exception) {
            Log.e("ApkServer", "Send error: ${e.message}")
        }
    }

    private fun send404(out: OutputStream) {
        val body = "<html><body><h1>404 Not Found</h1></body></html>"
        val header = "HTTP/1.1 404 Not Found\r\n" +
                "Content-Type: text/html\r\n" +
                "Content-Length: ${body.length}\r\n" +
                "Connection: close\r\n" +
                "\r\n"
        try {
            out.write(header.toByteArray())
            out.write(body.toByteArray())
            out.flush()
        } catch (e: Exception) {}
    }

    private fun sendForbidden(out: OutputStream) {
        val body = "<html><body><h1>Fitur client tidak aktif pada paket ini.</h1></body></html>"
        val header = "HTTP/1.1 403 Forbidden\r\n" +
                "Content-Type: text/html; charset=utf-8\r\n" +
                "Content-Length: ${body.toByteArray().size}\r\n" +
                "Connection: close\r\n" +
                "\r\n"
        try {
            out.write(header.toByteArray())
            out.write(body.toByteArray())
            out.flush()
        } catch (_: Exception) {}
    }

    fun getLocalIpAddress(context: Context): String? {
        try {
            val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo
            val ipInt = wifiInfo.ipAddress
            if (ipInt != 0) {
                return String.format("%d.%d.%d.%d",
                    ipInt and 0xFF,
                    (ipInt shr 8) and 0xFF,
                    (ipInt shr 16) and 0xFF,
                    (ipInt shr 24) and 0xFF
                )
            }
        } catch (e: Exception) {
            Log.e("ApkServer", "getLocalIpAddress error: ${e.message}")
        }

        return try {
            NetworkInterface.getNetworkInterfaces()?.asSequence()
                ?.flatMap { it.inetAddresses.asSequence() }
                ?.firstOrNull {
                    !it.isLoopbackAddress && it is InetAddress && it.address.size == 4
                }
                ?.hostAddress
        } catch (e: Exception) {
            null
        }
    }
}
