package com.billingps.aptv.utils

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.chaquo.python.Python
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class AdbPairingResult(
    val success: Boolean,
    val message: String,
    val paired: Boolean = false,
)

class ClientApkManager(private val context: Context) {

    companion object {
        private const val CLIENT_APK_ASSET = "tv-client.apk"
    }

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
