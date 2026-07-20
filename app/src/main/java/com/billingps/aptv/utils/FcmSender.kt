package com.billingps.aptv.utils

import android.content.Context
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec

object FcmSender {

    private const val PROJECT_ID = "rrbillingpro"
    private var cachedAccessToken: String? = null
    private var tokenExpiresAt: Long = 0L
    private var serviceAccountJson: String? = null

    fun init(context: Context) {
        try {
            val inputStream = context.resources.openRawResource(
                context.resources.getIdentifier("fcm_key", "raw", context.packageName)
            )
            serviceAccountJson = inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e("FcmSender", "init failed: ${e.message}")
        }
    }

    suspend fun sendToUser(token: String, title: String, body: String) {
        if (serviceAccountJson == null) {
            Log.w("FcmSender", "Service account not configured")
            return
        }
        if (token.isBlank()) return
        sendFcmV1(token, title, body)
    }

    private suspend fun sendFcmV1(deviceToken: String, title: String, body: String) {
        withContext(Dispatchers.IO) {
            try {
                val accessToken = getAccessToken() ?: return@withContext
                val url = URL("https://fcm.googleapis.com/v1/projects/$PROJECT_ID/messages:send")
                val conn = url.openConnection() as HttpURLConnection
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Authorization", "Bearer $accessToken")
                val escapedTitle = title.replace("\"", "\\\"").replace("\n", "\\n")
                val escapedBody = body.replace("\"", "\\\"").replace("\n", "\\n")
                val json = """{"message":{"token":"$deviceToken","notification":{"title":"$escapedTitle","body":"$escapedBody"},"data":{"title":"$escapedTitle","body":"$escapedBody"}}}"""
                conn.outputStream.write(json.toByteArray(Charsets.UTF_8))
                val code = conn.responseCode
                if (code != 200) {
                    val err = conn.errorStream?.bufferedReader()?.readText() ?: ""
                    Log.e("FcmSender", "sendFcmV1 failed: HTTP $code — $err")
                } else {
                    Log.i("FcmSender", "sendFcmV1 success for token=${deviceToken.take(20)}...")
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.e("FcmSender", "sendFcmV1 exception: ${e.message}")
            }
        }
    }

    private fun getAccessToken(): String? {
        val jsonStr = serviceAccountJson ?: return null
        val now = System.currentTimeMillis() / 1000
        if (cachedAccessToken != null && now < tokenExpiresAt - 60) return cachedAccessToken
        try {
            val json = org.json.JSONObject(jsonStr)
            val clientEmail = json.getString("client_email")
            val privateKeyPem = json.getString("private_key")

            val pemStr = privateKeyPem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("\n", "").replace("\r", "")
            val keyBytes = Base64.decode(pemStr, Base64.DEFAULT)
            val keySpec = PKCS8EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance("RSA")
            val privateKey = keyFactory.generatePrivate(keySpec)

            val header = org.json.JSONObject().apply {
                put("alg", "RS256")
                put("typ", "JWT")
            }
            val claim = org.json.JSONObject().apply {
                put("iss", clientEmail)
                put("scope", "https://www.googleapis.com/auth/firebase.messaging")
                put("aud", "https://oauth2.googleapis.com/token")
                put("exp", now + 3600)
                put("iat", now)
            }

            val b64Header = Base64.encodeToString(header.toString().toByteArray(), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            val b64Claim = Base64.encodeToString(claim.toString().toByteArray(), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            val toSign = "$b64Header.$b64Claim"

            val sig = Signature.getInstance("SHA256withRSA")
            sig.initSign(privateKey)
            sig.update(toSign.toByteArray())
            val signature = sig.sign()
            val b64Sig = Base64.encodeToString(signature, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

            val jwt = "$toSign.$b64Sig"

            val tokenUrl = URL("https://oauth2.googleapis.com/token")
            val tokenConn = tokenUrl.openConnection() as HttpURLConnection
            tokenConn.doOutput = true
            tokenConn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            val body = "grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=$jwt"
            tokenConn.outputStream.write(body.toByteArray(Charsets.UTF_8))
            val resp = tokenConn.inputStream.bufferedReader().use { it.readText() }
            tokenConn.disconnect()

            val respJson = org.json.JSONObject(resp)
            val token = respJson.getString("access_token")
            val expiresIn = respJson.optInt("expires_in", 3600)
            cachedAccessToken = token
            tokenExpiresAt = now + expiresIn
            return token
        } catch (e: Exception) {
            Log.e("FcmSender", "getAccessToken failed: ${e.message}")
            return null
        }
    }
}
