package com.billingps.aptv.utils

import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

object ECDSAUtils {

    private const val PUBLIC_KEY_B64 = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEtwmjVNVdjDQsMIl/oky9NxoAJ9pCZ7RqNUsQo9k1gzgvAOjlfDmxfXjoSMBu/T2llMjItylfC7fH680buJofuQ=="
    private const val PRIVATE_KEY_B64 = "MEECAQAwEwYHKoZIzj0CAQYIKoZIzj0DAQcEJzAlAgEBBCD78iOiQrMsmei7ba0bqd6jTeiZPgiePwujiiQgd+g3ZQ=="

    private val publicKey: PublicKey by lazy {
        val bytes = Base64.getDecoder().decode(PUBLIC_KEY_B64)
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(bytes))
    }

    private val privateKey: PrivateKey by lazy {
        val bytes = Base64.getDecoder().decode(PRIVATE_KEY_B64)
        KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(bytes))
    }

    fun sign(data: String): String {
        val sig = Signature.getInstance("SHA256withECDSA")
        sig.initSign(privateKey)
        sig.update(data.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(sig.sign())
    }

    fun verify(data: String, signatureB64: String): Boolean {
        return try {
            val sig = Signature.getInstance("SHA256withECDSA")
            sig.initVerify(publicKey)
            sig.update(data.toByteArray(Charsets.UTF_8))
            sig.verify(Base64.getDecoder().decode(signatureB64))
        } catch (_: Exception) {
            false
        }
    }
}
