package com.billingps.licensegen

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

object ECDSAUtils {

    private const val PUBLIC_KEY_B64 = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEtwmjVNVdjDQsMIl/oky9NxoAJ9pCZ7RqNUsQo9k1gzgvAOjlfDmxfXjoSMBu/T2llMjItylfC7fH680buJofuQ=="

    private val publicKey: PublicKey by lazy {
        val bytes = Base64.decode(PUBLIC_KEY_B64, Base64.DEFAULT)
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(bytes))
    }

    private var privateKeyB64: String? = null

    fun init(context: Context) {
        if (privateKeyB64 != null) return
        val prefs = context.getSharedPreferences("licensegen_prefs", Context.MODE_PRIVATE)
        var keyB64 = prefs.getString("ecdsa_private_key", null)
        if (keyB64.isNullOrBlank()) {
            Log.w("ECDSAUtils", "No private key configured. Generate one or set in admin panel.")
            // Private key must be set by admin via the UI or external configuration
            return
        }
        privateKeyB64 = keyB64
    }

    fun setPrivateKey(context: Context, b64: String) {
        val prefs = context.getSharedPreferences("licensegen_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("ecdsa_private_key", b64).apply()
        privateKeyB64 = b64
    }

    fun getPrivateKey(context: Context): String? {
        val prefs = context.getSharedPreferences("licensegen_prefs", Context.MODE_PRIVATE)
        return prefs.getString("ecdsa_private_key", null)
    }

    fun hasPrivateKey(): Boolean = !privateKeyB64.isNullOrBlank()

    fun sign(data: String): String {
        val b64 = privateKeyB64 ?: throw IllegalStateException("Private key not initialized. Call init(context) first.")
        val keyBytes = Base64.decode(b64, Base64.DEFAULT)
        val keySpec = PKCS8EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("EC")
        val pk = keyFactory.generatePrivate(keySpec)
        val sig = Signature.getInstance("SHA256withECDSA")
        sig.initSign(pk)
        sig.update(data.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(sig.sign(), Base64.DEFAULT)
    }

    fun verify(data: String, signatureB64: String): Boolean {
        return try {
            val sig = Signature.getInstance("SHA256withECDSA")
            sig.initVerify(publicKey)
            sig.update(data.toByteArray(Charsets.UTF_8))
            sig.verify(Base64.decode(signatureB64, Base64.DEFAULT))
        } catch (_: Exception) {
            false
        }
    }
}
