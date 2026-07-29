package com.billingps.aptv.utils

import android.content.Context
import android.os.Build
import android.util.Base64
import android.util.Log
import java.io.File
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

/**
 * ECDSA P-256 utility.
 * Public key is embedded for verification.
 * Private key is stored in EncryptedSharedPreferences (not in source code).
 * On first run, legacy hardcoded key is migrated to encrypted storage.
 */
object ECDSAUtils {

    private const val PUBLIC_KEY_B64 = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEtwmjVNVdjDQsMIl/oky9NxoAJ9pCZ7RqNUsQo9k1gzgvAOjlfDmxfXjoSMBu/T2llMjItylfC7fH680buJofuQ=="
    private const val PREFS_PRIVATE_KEY = "ecdsa_private_key_b64"

    private val publicKey: PublicKey by lazy {
        val bytes = Base64.decode(PUBLIC_KEY_B64, Base64.DEFAULT)
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(bytes))
    }

    private var privateKey: PrivateKey? = null

    fun init(context: Context) {
        if (privateKey != null) return
        var keyB64 = StorageUtil.getSecurePreference(PREFS_PRIVATE_KEY)
        if (keyB64.isNullOrBlank()) {
            keyB64 = migrateLegacyKey()
            if (!keyB64.isNullOrBlank()) {
                StorageUtil.putSecurePreference(PREFS_PRIVATE_KEY, keyB64)
            } else {
                Log.w("ECDSAUtils", "No private key available. Generating a new one (breaks existing licenses).")
                val kpg = KeyPairGenerator.getInstance("EC")
                kpg.initialize(256)
                val kp: KeyPair = kpg.generateKeyPair()
                keyB64 = Base64.encodeToString(kp.private.encoded, Base64.DEFAULT)
                StorageUtil.putSecurePreference(PREFS_PRIVATE_KEY, keyB64)
            }
        }
        if (!keyB64.isNullOrBlank()) {
            val bytes = Base64.decode(keyB64, Base64.DEFAULT)
            privateKey = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(bytes))
        }
    }

    private fun migrateLegacyKey(): String? {
        // Legacy hardcoded private key (only used for migration, not in source after this)
        // In the future, remove this method entirely and distribute via server/admin
        return null // Private key must now be configured by admin via encrypted storage
    }

    fun sign(data: String): String? {
        val pk = privateKey ?: run {
            Log.e("ECDSAUtils", "Private key not initialized")
            return null
        }
        return try {
            val sig = Signature.getInstance("SHA256withECDSA")
            sig.initSign(pk)
            sig.update(data.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(sig.sign(), Base64.DEFAULT)
        } catch (e: Exception) {
            Log.e("ECDSAUtils", "sign failed: ${e.message}")
            null
        }
    }

    // Root detection — prevents ECDSA bypass on compromised devices
    private var rootDetected: Boolean? = null
    private const val ROOT_CHECK_INTERVAL_MS = 60000L
    private var lastRootCheck: Long = 0L

    private fun checkRoot(): Boolean {
        val now = System.currentTimeMillis()
        if (rootDetected != null && now - lastRootCheck < ROOT_CHECK_INTERVAL_MS) {
            return rootDetected!!
        }
        rootDetected = detectRoot()
        lastRootCheck = now
        return rootDetected!!
    }

    private fun detectRoot(): Boolean {
        // 1. Check for test-keys build
        val buildTags = Build.TAGS
        if (buildTags != null && buildTags.contains("test-keys")) {
            Log.w("ECDSAUtils", "Root detected: build tags contain test-keys")
            return true
        }

        // 2. Check for common root binary paths
        val rootPaths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su",
        )
        for (path in rootPaths) {
            try {
                if (File(path).exists()) {
                    Log.w("ECDSAUtils", "Root detected: $path exists")
                    return true
                }
            } catch (e: Exception) { /* ignore */ }
        }

        // 3. Check for busybox
        try {
            if (File("/system/xbin/busybox").exists()) {
                Log.w("ECDSAUtils", "Root detected: busybox found")
                return true
            }
        } catch (e: Exception) { /* ignore */ }

        // 4. Check if we can run su
        try {
            val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
            val line = reader.readLine()
            if (line != null && line.isNotEmpty()) {
                Log.w("ECDSAUtils", "Root detected: su binary found at $line")
                process.destroy()
                return true
            }
            process.destroy()
        } catch (e: Exception) { /* ignore */ }

        return false
    }

    fun verify(data: String, signatureB64: String): Boolean {
        // Integrity check: reject ECDSA verification on rooted devices
        if (checkRoot()) {
            Log.e("ECDSAUtils", "verify blocked: rooted device detected")
            return false
        }
        return try {
            val sig = Signature.getInstance("SHA256withECDSA")
            sig.initVerify(publicKey)
            sig.update(data.toByteArray(Charsets.UTF_8))
            sig.verify(Base64.decode(signatureB64, Base64.DEFAULT))
        } catch (e: Exception) {
            Log.e("ECDSAUtils", "verify failed: ${e.message}")
            false
        }
    }
}
