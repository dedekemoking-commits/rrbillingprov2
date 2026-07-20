package com.billingps.aptv.utils

import android.content.Context
import android.util.Base64
import android.util.Log
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

    fun verify(data: String, signatureB64: String): Boolean {
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
