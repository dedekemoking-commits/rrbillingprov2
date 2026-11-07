package com.billingps.owner.data

import android.content.Context
import java.security.MessageDigest
import java.util.Locale

class OwnerPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("owner_prefs", Context.MODE_PRIVATE)

    val email: String
        get() = prefs.getString(KEY_EMAIL, "").orEmpty()

    /** Username rental yang dimiliki akun owner. */
    val username: String
        get() = prefs.getString(KEY_USERNAME, "").orEmpty()

    val pinHash: String
        get() = prefs.getString(KEY_PIN_HASH, "").orEmpty()

    val hasPin: Boolean
        get() = pinHash.isNotBlank()

    val hasSavedAccount: Boolean
        get() = email.isNotBlank() && username.isNotBlank()

    fun saveSession(email: String, rentalUsername: String, pin: String) {
        prefs.edit()
            .putString(KEY_EMAIL, email.trim())
            .putString(KEY_USERNAME, rentalUsername.trim())
            .putString(KEY_PIN_HASH, hashPin(email, rentalUsername, pin))
            .apply()
    }

    fun verifyPin(pin: String): Boolean {
        if (!hasPin) return false
        return pinHash.equals(hashPin(email, username, pin), ignoreCase = true)
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun hashPin(email: String, rentalUsername: String, pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest("$email:$rentalUsername:$pin".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { String.format(Locale.US, "%02x", it) }
    }

    companion object {
        private const val KEY_EMAIL = "owner_email"
        private const val KEY_USERNAME = "owner_username"
        private const val KEY_PIN_HASH = "owner_pin_hash"
    }
}
