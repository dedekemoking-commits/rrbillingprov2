package com.billingps.aptv.utils

import android.content.Context
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

    /**
     * Extract the bundled TV client APK from assets to cache dir.
     */
    fun extractClientApk(): File? {
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
        return withContext(Dispatchers.IO) {
            try {
                if (!Python.isStarted()) {
                    return@withContext AdbPairingResult(false, "Python tidak tersedia")
                }
                val result = Python.getInstance()
                    .getModule("adb_installer")
                    .callAttr("adb_direct_install", ip, port, apkPath)

                val success = result.callAttr("get", "success")?.toBoolean() ?: false
                val message = result.callAttr("get", "message")?.toString() ?: "Unknown"

                if (message == "DEVICE_UNAUTHORIZED") {
                    AdbPairingResult(false, "Perlu pairing terlebih dahulu", paired = false)
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
                val result = Python.getInstance()
                    .getModule("adb_installer")
                    .callAttr("adb_pair", ip, pairingPort, pairingCode)

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
        return withContext(Dispatchers.IO) {
            try {
                if (!Python.isStarted()) {
                    return@withContext AdbPairingResult(false, "Python tidak tersedia")
                }
                val result = Python.getInstance()
                    .getModule("adb_installer")
                    .callAttr("adb_connect_and_install", ip, connectionPort, apkPath)

                val success = result.callAttr("get", "success")?.toBoolean() ?: false
                val message = result.callAttr("get", "message")?.toString() ?: "Unknown"
                AdbPairingResult(success, message, paired = success)
            } catch (e: Exception) {
                Log.e("ClientApkManager", "Install error: ${e.message}")
                AdbPairingResult(false, "Error: ${e.message}")
            }
        }
    }

    fun cleanup() {
        ApkServer.stop()
    }
}
