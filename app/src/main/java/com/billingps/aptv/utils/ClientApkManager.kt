package com.billingps.aptv.utils

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import com.chaquo.python.Python
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class AdbPairingResult(
    val success: Boolean,
    val message: String,
    val paired: Boolean = false,
)

data class ClientApkDownload(
    val file: File,
    val versionName: String,
    val versionCode: Long,
    val releaseTag: String,
    val assetName: String,
)

class ClientApkManager(private val context: Context) {

    companion object {
        private const val CLIENT_APK_ASSET = "tv-client.apk"
        private const val CLIENT_APK_DOWNLOAD = "tv-client-latest.apk"
        private const val CLIENT_PACKAGE = "com.rrbillingpro.tvclient"
        private const val TV_APK_ASSET = "RRBillingPro-TV.apk"
        private const val GITHUB_RELEASES_URL =
            "https://api.github.com/repos/dedekemoking-commits/rr_billing_pro_windows/releases?per_page=30"
    }

    private data class RemoteClientApk(
        val releaseTag: String,
        val assetName: String,
        val downloadUrl: String,
    )

    private val keyDir: String = File(context.filesDir, "adb").absolutePath

    /**
     * Extract the bundled TV client APK from assets to cache dir.
     */
    fun extractClientApk(): File? {
        if (!ApkServer.clientAllowed) {
            Log.i("ClientApkManager", "Client APK blocked: feature disabled for this plan")
            return null
        }
        return try {
            val apkFile = File(context.cacheDir, "tv-client.apk")
            if (apkFile.exists()) apkFile.delete()
            context.assets.open(CLIENT_APK_ASSET).use { input ->
                apkFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            Log.i("ClientApkManager", "APK extracted to ${apkFile.absolutePath} (${apkFile.length()} bytes)")
            apkFile
        } catch (e: Exception) {
            Log.e("ClientApkManager", "Failed to extract APK: ${e.message}")
            null
        }
    }

    suspend fun downloadLatestClientApk(
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
    ): ClientApkDownload {
        if (!ApkServer.clientAllowed) {
            throw IOException("Fitur Client APK tidak aktif pada paket ini")
        }
        return withContext(Dispatchers.IO) {
            val remote = resolveLatestClientApk()
                ?: throw IOException("APK client TV tidak ditemukan di GitHub release")
            val target = File(context.cacheDir, CLIENT_APK_DOWNLOAD)
            val temp = File(context.cacheDir, "$CLIENT_APK_DOWNLOAD.part")
            if (temp.exists()) temp.delete()

            val conn = URL(remote.downloadUrl).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000
            conn.setRequestProperty("User-Agent", "RRBillingPro-Android")
            try {
                val responseCode = conn.responseCode
                if (responseCode !in 200..299) {
                    throw IOException("Download APK gagal (HTTP $responseCode)")
                }

                val total = conn.contentLength.toLong()
                var downloaded = 0L
                var lastPercent = -1
                var lastReported = 0L
                conn.inputStream.use { input ->
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloaded += read
                            val shouldReport = if (total > 0) {
                                val percent = ((downloaded * 100) / total).toInt()
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    true
                                } else {
                                    false
                                }
                            } else {
                                downloaded - lastReported >= 256 * 1024L
                            }
                            if (shouldReport) {
                                lastReported = downloaded
                                onProgress(downloaded, total)
                            }
                        }
                    }
                }

                if (downloaded < 1_000_000L || temp.length() < 1_000_000L) {
                    throw IOException("File APK tidak valid")
                }

                if (target.exists()) target.delete()
                if (!temp.renameTo(target)) {
                    temp.copyTo(target, overwrite = true)
                    temp.delete()
                }

                val info = context.packageManager.getPackageArchiveInfo(target.absolutePath, 0)
                    ?: throw IOException("APK tidak bisa dibaca")
                if (info.packageName != CLIENT_PACKAGE) {
                    throw IOException("APK bukan client TV (${info.packageName})")
                }
                val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    info.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    val legacyVersionCode = info.versionCode
                    legacyVersionCode.toLong()
                }

                Log.i(
                    "ClientApkManager",
                    "Downloaded client APK ${remote.releaseTag}/${remote.assetName} " +
                        "v${info.versionName} ($versionCode) -> ${target.absolutePath}",
                )
                ClientApkDownload(
                    file = target,
                    versionName = info.versionName ?: "",
                    versionCode = versionCode,
                    releaseTag = remote.releaseTag,
                    assetName = remote.assetName,
                )
            } catch (e: Exception) {
                if (temp.exists()) temp.delete()
                throw e
            } finally {
                conn.disconnect()
            }
        }
    }

    private fun resolveLatestClientApk(): RemoteClientApk? {
        val conn = URL(GITHUB_RELEASES_URL).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 15_000
        conn.readTimeout = 20_000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "RRBillingPro-Android")
        try {
            val responseCode = conn.responseCode
            if (responseCode != 200) {
                Log.w("ClientApkManager", "GitHub releases API HTTP $responseCode")
                return null
            }

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val releases = JSONArray(body)
            for (i in 0 until releases.length()) {
                val release = releases.optJSONObject(i) ?: continue
                val tag = release.optString("tag_name", "latest")
                val assets = release.optJSONArray("assets") ?: continue
                var asset = findApkAsset(assets, exact = true)
                    ?: findApkAsset(assets, exact = false)
                    ?: continue
                val url = asset.optString("browser_download_url", "")
                if (url.isNotBlank()) {
                    return RemoteClientApk(
                        releaseTag = tag,
                        assetName = asset.optString("name", TV_APK_ASSET),
                        downloadUrl = url,
                    )
                }
            }

            Log.w("ClientApkManager", "No TV APK asset found in GitHub releases")
            return null
        } catch (e: Exception) {
            Log.w("ClientApkManager", "Resolve latest client APK failed: ${e.message}")
            return null
        } finally {
            conn.disconnect()
        }
    }

    private fun findApkAsset(assets: JSONArray, exact: Boolean): JSONObject? {
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name", "")
            val matches = if (exact) {
                name.equals(TV_APK_ASSET, ignoreCase = true)
            } else {
                name.endsWith(".apk", ignoreCase = true) &&
                    name.contains("tv", ignoreCase = true)
            }
            if (matches) return asset
        }
        return null
    }

    /**
     * Try direct ADB install (Android 10- or already paired).
     */
    suspend fun tryDirectInstall(ip: String, port: Int, apkPath: String): AdbPairingResult {
        if (!ApkServer.clientAllowed) {
            return AdbPairingResult(false, "Fitur Client APK tidak aktif pada paket Bulanan")
        }
        return withContext(Dispatchers.IO) {
            try {
                if (!Python.isStarted()) {
                    return@withContext AdbPairingResult(false, "Python tidak tersedia")
                }
                val module = Python.getInstance().getModule("adb_installer")
                module.callAttr("adb_set_key_dir", keyDir)
                val result = module.callAttr("adb_direct_install", ip, port, apkPath)

                val success = result.callAttr("get", "success")?.toBoolean() ?: false
                val message = result.callAttr("get", "message")?.toString() ?: "Unknown"

                if (message == "DEVICE_UNAUTHORIZED") {
                    AdbPairingResult(false, "TV belum mengizinkan ADB. Cek prompt di TV atau lakukan pairing.", paired = false)
                } else if (message == "CONNECTION_TIMEOUT") {
                    AdbPairingResult(false, "Tidak dapat terhubung ke $ip:$port. Pastikan ADB Wireless Debugging aktif.", paired = false)
                } else {
                    AdbPairingResult(success, message, paired = success)
                }
            } catch (e: Exception) {
                Log.e("ClientApkManager", "Direct install error: ${e.message}")
                AdbPairingResult(false, "Error: ${e.message}")
            }
        }
    }

    /**
     * Pair with Android 11+ device using Wireless Debugging pairing code.
     */
    suspend fun pairDevice(ip: String, pairingPort: Int, pairingCode: String): AdbPairingResult {
        return withContext(Dispatchers.IO) {
            try {
                if (!Python.isStarted()) {
                    return@withContext AdbPairingResult(false, "Python tidak tersedia")
                }
                val module = Python.getInstance().getModule("adb_installer")
                module.callAttr("adb_set_key_dir", keyDir)
                val result = module.callAttr("adb_pair", ip, pairingPort, pairingCode)

                val success = result.callAttr("get", "success")?.toBoolean() ?: false
                val message = result.callAttr("get", "message")?.toString() ?: "Unknown"
                AdbPairingResult(success, message, paired = success)
            } catch (e: Exception) {
                Log.e("ClientApkManager", "Pair error: ${e.message}")
                AdbPairingResult(false, "Error: ${e.message}")
            }
        }
    }

    /**
     * Connect (after pairing) and install APK.
     */
    suspend fun installAfterPairing(ip: String, connectionPort: Int, apkPath: String): AdbPairingResult {
        if (!ApkServer.clientAllowed) {
            return AdbPairingResult(false, "Fitur Client APK tidak aktif pada paket Bulanan")
        }
        return withContext(Dispatchers.IO) {
            try {
                if (!Python.isStarted()) {
                    return@withContext AdbPairingResult(false, "Python tidak tersedia")
                }
                val module = Python.getInstance().getModule("adb_installer")
                module.callAttr("adb_set_key_dir", keyDir)
                val result = module.callAttr("adb_tls_install", ip, connectionPort, apkPath)

                val success = result.callAttr("get", "success")?.toBoolean() ?: false
                val message = result.callAttr("get", "message")?.toString() ?: "Unknown"
                AdbPairingResult(success, message, paired = success)
            } catch (e: Exception) {
                Log.e("ClientApkManager", "Install error: ${e.message}")
                AdbPairingResult(false, "Error: ${e.message}")
            }
        }
    }

    suspend fun discoverConnectPort(ip: String, timeoutSec: Int = 6): Int? =
        discoverAdbPort(ip, "adb_discover_connect_port", timeoutSec)

    suspend fun discoverPairingPort(ip: String, timeoutSec: Int = 6): Int? =
        discoverAdbPort(ip, "adb_discover_pairing_port", timeoutSec)

    private suspend fun discoverAdbPort(ip: String, method: String, timeoutSec: Int): Int? =
        withContext(Dispatchers.IO) {
            if (!Python.isStarted()) return@withContext null
            val wifi = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val lock = try {
                wifi?.createMulticastLock("billingps-adb-mdns")?.apply {
                    setReferenceCounted(false)
                    acquire()
                }
            } catch (e: Exception) {
                Log.w("ClientApkManager", "Multicast lock failed: ${e.message}")
                null
            }
            try {
                val module = Python.getInstance().getModule("adb_installer")
                val result = module.callAttr(method, ip, timeoutSec.toDouble())
                val success = result.callAttr("get", "success")?.toBoolean() ?: false
                if (success) result.callAttr("get", "port")?.toInt() else null
            } catch (e: Exception) {
                Log.e("ClientApkManager", "mDNS discovery failed: ${e.message}")
                null
            } finally {
                try {
                    lock?.release()
                } catch (_: Exception) {
                }
            }
        }

    fun cleanup() {
        ApkServer.stop()
    }
}
