package com.billingps.owner.data

import android.content.Context
import java.security.MessageDigest
import java.util.Locale

class OwnerPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("owner_prefs", Context.MODE_PRIVATE)

    val username: String
        get() = prefs.getString(KEY_USERNAME, "").orEmpty()

    val pinHash: String
        get() = prefs.getString(KEY_PIN_HASH, "").orEmpty()

    val hasPin: Boolean
        get() = pinHash.isNotBlank()

    fun saveSession(username: String, pin: String?) {
        val editor = prefs.edit().putString(KEY_USERNAME, username)
        if (pin.isNullOrBlank()) {
            editor.remove(KEY_PIN_HASH)
        } else {
            editor.putString(KEY_PIN_HASH, hashPin(username, pin))
        }
        editor.apply()
    }

    fun verifyPin(username: String, pin: String): Boolean {
        if (!hasPin) return true
        return pinHash.equals(hashPin(username, pin), ignoreCase = true)
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun hashPin(username: String, pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest("$username:$pin".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { String.format(Locale.US, "%02x", it) }
    }

    companion object {
        private const val KEY_USERNAME = "owner_username"
        private const val KEY_PIN_HASH = "owner_pin_hash"
    }
}
