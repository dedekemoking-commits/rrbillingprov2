package com.billingps.aptv.models

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.util.Patterns
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.billingps.aptv.utils.ECDSAUtils
import com.billingps.aptv.utils.StorageUtil
import com.billingps.aptv.cloud.CloudRepository
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.security.MessageDigest
import java.util.*
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import kotlin.coroutines.resume

data class AppUiState(
    val isLoggedIn: Boolean = false,
    val currentUser: String = "",
    val currentRole: String = "",
    val users: Map<String, UserData> = emptyMap(),
    val tvList: List<TvData> = emptyList(),
    val transaksiList: List<Transaksi> = emptyList(),
    val paketMain: Map<String, Map<String, Int>> = defaultPaketMain(),
    val paketDurasi: Map<String, Int> = defaultPaketDurasi(),
    val menuMakanan: Map<String, Int> = emptyMap(),
    val menuMinuman: Map<String, Int> = emptyMap(),
    val licenseStatus: LicenseStatus = LicenseStatus(),
    val smtp: SmtpConfig = SmtpConfig(),
    val kodeGenerasiList: List<KodeGenerasi> = emptyList(),
    val trialBatas: Long = 0,
    val maxTv: Int = 0,
    // Cloud sync status
    val cloudStatus: String = "disconnected",
    val cloudLastSync: Long = 0L,
    val isBusy: Boolean = false,
    val statusMessage: String = "",
    val pendingVerifyUser: String = "",
    val pendingVerifyCode: String = "",
    val pendingResetCode: String = "",
    val needsSmtpSetup: Boolean = false,
    val updateInfo: UpdateInfo? = null,
    val updateChecked: Boolean = false,
    val downloadProgress: Int = -1, // -1 = idle, 0-100 = downloading
    val appVersionName: String = APP_VERSION,
)

fun defaultPaketMain(): Map<String, Map<String, Int>> {
    val defaultPS = mapOf(
        "15 Menit" to 5000, "30 Menit" to 10000, "1 Jam" to 15000,
        "2 Jam" to 25000, "3 Jam" to 35000, "Main Bebas" to 35000,
    )
    return mapOf("PS3" to defaultPS, "PS4" to defaultPS, "PS5" to defaultPS)
}

fun defaultPaketDurasi(): Map<String, Int> = mapOf(
    "15 Menit" to 15, "30 Menit" to 30, "1 Jam" to 60,
    "2 Jam" to 120, "3 Jam" to 180, "Main Bebas" to 0,
)

    const val APP_VERSION = "1.2.1"

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val cloudRepo = CloudRepository(application)
    private val firebaseAuth = FirebaseAuth.getInstance()

    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    init {
        StorageUtil.init(application)
        loadAll()
        ensureSuperAdmin()
    }

    private fun ensureSuperAdmin() {
        val users = _state.value.users.toMutableMap()
        if (!users.containsKey("rrgaming")) {
            val hash = sha256("q7fmvVOw6hUtWPAF")
            users["rrgaming"] = UserData(
                username = "rrgaming",
                passwordHash = hash,
                role = "admin",
                email = "",
                dibuat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).format(java.util.Date()),
            )
            _state.value = _state.value.copy(users = users)
            StorageUtil.saveUsers(users)
        }
    }

    private fun loadAll() {
        val users = StorageUtil.loadUsers()
        val cu = StorageUtil.loadCurrentUser()
        val cr = StorageUtil.loadCurrentRole()
        _state.value = _state.value.copy(
            isLoggedIn = cu.isNotEmpty(),
            currentUser = cu,
            currentRole = cr,
            users = users,
            tvList = StorageUtil.loadTvList(),
            transaksiList = StorageUtil.loadTransaksi(),
            paketMain = StorageUtil.loadPaketMain().ifEmpty { defaultPaketMain() },
            paketDurasi = StorageUtil.loadPaketDurasi().ifEmpty { defaultPaketDurasi() },
            menuMakanan = StorageUtil.loadMenuMakanan(),
            menuMinuman = StorageUtil.loadMenuMinuman(),
            licenseStatus = StorageUtil.loadLicense(),
            smtp = StorageUtil.loadSmtp(),
            kodeGenerasiList = StorageUtil.loadKodeGenerasiList(),
            trialBatas = StorageUtil.loadTrialBatas(),
            maxTv = StorageUtil.loadLicense().maxTv,
        )
        // Check trial expiry
        val s = _state.value
        if (s.trialBatas > 0 && s.trialBatas < System.currentTimeMillis() && s.licenseStatus.status != "active") {
            _state.value = _state.value.copy(trialBatas = 0L, maxTv = 0)
            StorageUtil.saveTrial(0L)
        }
    }

    private fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun isValidEmail(email: String): Boolean = email.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(email).matches()

    private fun firebaseErrorMessage(ex: Exception): String {
        val code = (ex as? FirebaseAuthException)?.errorCode
        return when {
            ex is FirebaseAuthWeakPasswordException -> "Password terlalu lemah. Gunakan minimal 6 karakter dan kombinasikan huruf besar/angka."
            ex is FirebaseAuthInvalidCredentialsException -> "Format email tidak valid."
            ex is FirebaseAuthUserCollisionException -> "Email sudah terdaftar. Gunakan email lain atau reset password."
            code == "ERROR_OPERATION_NOT_ALLOWED" -> "Metode Email/Password belum aktif di Firebase Console."
            code == "ERROR_NETWORK_REQUEST_FAILED" || code == "ERROR_NETWORK_ERROR" -> "Koneksi internet bermasalah. Coba lagi nanti."
            code == "ERROR_TOO_MANY_REQUESTS" -> "Terlalu banyak permintaan. Coba beberapa saat lagi."
            else -> ex.message ?: "Gagal membuat akun Firebase."
        }
    }

    private suspend fun createFirebaseUser(email: String, password: String): AuthResult = suspendCancellableCoroutine { cont ->
        firebaseAuth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { if (!cont.isCompleted) cont.resume(AuthResult(true, "")) }
            .addOnFailureListener { ex ->
                Log.i("MainVM", "Firebase create failed: ${ex.message}")
                if (!cont.isCompleted) cont.resume(AuthResult(false, firebaseErrorMessage(ex)))
            }
    }

    private suspend fun sendVerificationEmail(user: FirebaseUser): Boolean = suspendCancellableCoroutine { cont ->
        user.sendEmailVerification()
            .addOnSuccessListener { if (!cont.isCompleted) cont.resume(true) }
            .addOnFailureListener { ex ->
                Log.i("MainVM", "Send verification failed: ${ex.message}")
                if (!cont.isCompleted) cont.resume(false)
            }
    }

    private suspend fun signInWithFirebase(email: String, password: String): AuthResult = suspendCancellableCoroutine { cont ->
        firebaseAuth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                if (!cont.isCompleted) cont.resume(AuthResult(true, ""))
            }
            .addOnFailureListener { ex ->
                Log.i("MainVM", "Firebase sign-in failed: ${ex.message}")
                if (!cont.isCompleted) cont.resume(AuthResult(false, ex.message ?: "Login Firebase gagal"))
            }
    }

    private suspend fun resetPassword(email: String): Boolean = suspendCancellableCoroutine { cont ->
        firebaseAuth.sendPasswordResetEmail(email)
            .addOnSuccessListener { if (!cont.isCompleted) cont.resume(true) }
            .addOnFailureListener { ex ->
                Log.i("MainVM", "Password reset failed: ${ex.message}")
                if (!cont.isCompleted) cont.resume(false)
            }
    }

    // ── Reset Password with Code ──────────────────────────
    fun generateResetCode(usernameOrEmail: String): AuthResult {
        val input = usernameOrEmail.trim().lowercase()
        val users = _state.value.users
        val user = users[input] ?: users.values.firstOrNull { it.email.equals(input, ignoreCase = true) }
        if (user == null) return AuthResult(false, "Akun tidak ditemukan")
        if (user.email.isBlank()) return AuthResult(false, "Akun ini tidak memiliki email")
        val code = String.format("%06d", (100000..999999).random())
        val expiry = System.currentTimeMillis() + 600_000 // 10 menit
        val newUsers = users.toMutableMap()
        newUsers[user.username] = user.copy(resetCode = code, resetCodeExpiry = expiry)
        _state.value = _state.value.copy(users = newUsers, pendingResetCode = code)
        StorageUtil.saveUsers(newUsers)
        Log.i("MainVM", "Reset code for ${user.username}: $code")
        val cfg = _state.value.smtp
        if (cfg.user.isNotBlank() && cfg.pass.isNotBlank()) {
            sendEmail(user.email,
                "Kode Reset Password RR Billing Pro",
                "Halo ${user.username},\n\nKode reset password RR Billing Pro Anda: $code\n\nKode berlaku 10 menit.\n\nTerima kasih.",
            ) { ok, msg ->
                if (!ok) Log.i("MainVM", "Failed to send reset email: $msg")
            }
            return AuthResult(true, "Kode verifikasi telah dikirim ke email Anda.")
        } else {
            return AuthResult(true, "Kode verifikasi: $code")
        }
    }

    fun verifyResetCode(usernameOrEmail: String, code: String): AuthResult {
        val input = usernameOrEmail.trim().lowercase()
        val users = _state.value.users
        val user = users[input] ?: users.values.firstOrNull { it.email.equals(input, ignoreCase = true) }
        if (user == null) return AuthResult(false, "Akun tidak ditemukan")
        if (user.resetCode.isEmpty() || System.currentTimeMillis() > user.resetCodeExpiry) {
            return AuthResult(false, "Kode sudah kadaluarsa. Silakan generate ulang.")
        }
        if (user.resetCode != code) return AuthResult(false, "Kode verifikasi salah")
        return AuthResult(true, "Kode valid")
    }

    fun resetPasswordWithCode(usernameOrEmail: String, code: String, newPassword: String): AuthResult {
        val input = usernameOrEmail.trim().lowercase()
        val users = _state.value.users.toMutableMap()
        val user = users[input] ?: users.values.firstOrNull { it.email.equals(input, ignoreCase = true) }
        if (user == null) return AuthResult(false, "Akun tidak ditemukan")
        if (user.resetCode.isEmpty() || System.currentTimeMillis() > user.resetCodeExpiry) {
            return AuthResult(false, "Kode sudah kadaluarsa. Silakan generate ulang.")
        }
        if (user.resetCode != code) return AuthResult(false, "Kode verifikasi salah")
        if (newPassword.length < 6) return AuthResult(false, "Password minimal 6 karakter")
        if (!newPassword.any { it.isUpperCase() }) return AuthResult(false, "Password harus mengandung huruf besar")
        if (!newPassword.any { it.isDigit() }) return AuthResult(false, "Password harus mengandung angka")
        val hash = sha256(newPassword)
        users[user.username] = user.copy(passwordHash = hash, resetCode = "", resetCodeExpiry = 0)
        _state.value = _state.value.copy(users = users, pendingResetCode = "")
        StorageUtil.saveUsers(users)
        // Kirim Firebase password reset email agar password di Firebase juga berubah
        if (user.email.isNotBlank()) {
            viewModelScope.launch {
                try { sendFirebasePasswordReset(user.email) } catch (_: Exception) { }
            }
        }
        return AuthResult(true, "Password berhasil direset! Silakan login.")
    }

    private suspend fun sendFirebasePasswordReset(email: String) {
        try {
            firebaseAuth.sendPasswordResetEmail(email)
                .addOnSuccessListener { Log.i("MainVM", "Firebase password reset email sent to $email") }
                .addOnFailureListener { Log.i("MainVM", "Firebase password reset email failed: ${it.message}") }
        } catch (_: Exception) { }
    }

    // ── Login ──────────────────────────────────────────────
    suspend fun login(usernameOrEmail: String, password: String): Boolean = loginWithResult(usernameOrEmail, password).success

    suspend fun loginWithResult(usernameOrEmail: String, password: String): AuthResult {
        val input = usernameOrEmail.trim()
        val users = _state.value.users
        val localUser = users[input.lowercase()]
        val matchingEmailUser = users.values.firstOrNull { it.email.equals(input, ignoreCase = true) }
        val emailCandidate = input.contains("@") || matchingEmailUser != null

        if (emailCandidate && isValidEmail(input)) {
            val firebaseResult = signInWithFirebase(input, password)
            if (firebaseResult.success) {
                val effectiveUser = matchingEmailUser ?: localUser
                if (effectiveUser != null && !effectiveUser.emailVerified) {
                    firebaseAuth.signOut()
                    _state.value = _state.value.copy(pendingVerifyUser = effectiveUser.username)
                    return AuthResult(false, "Silakan verifikasi email Anda dengan memasukkan kode verifikasi")
                }
                val sessionUser = effectiveUser ?: UserData(username = input, role = "admin", email = input)
                StorageUtil.saveCurrentSession(sessionUser.username, sessionUser.role)
                val smtp = _state.value.smtp
                _state.value = _state.value.copy(
                    isLoggedIn = true,
                    currentUser = sessionUser.username,
                    currentRole = sessionUser.role,
                    needsSmtpSetup = smtp.user.isBlank() || smtp.pass.isBlank(),
                )
                return AuthResult(true, "")
            }
            // Firebase gagal — fallback ke cek password lokal
            val fallbackUser = matchingEmailUser ?: localUser
            if (fallbackUser != null) {
                val hash = sha256(password)
                if (fallbackUser.passwordHash == hash) {
                    StorageUtil.saveCurrentSession(fallbackUser.username, fallbackUser.role)
                    val smtp = _state.value.smtp
                    _state.value = _state.value.copy(
                        isLoggedIn = true,
                        currentUser = fallbackUser.username,
                        currentRole = fallbackUser.role,
                        needsSmtpSetup = smtp.user.isBlank() || smtp.pass.isBlank(),
                    )
                    return AuthResult(true, "")
                }
            }
            return firebaseResult
        }

        if (localUser == null) return AuthResult(false, "Username/Password salah")
        val hash = sha256(password)
        if (localUser.passwordHash != hash) return AuthResult(false, "Username/Password salah")
        StorageUtil.saveCurrentSession(localUser.username, localUser.role)
        val smtp = _state.value.smtp
        _state.value = _state.value.copy(
            isLoggedIn = true,
            currentUser = localUser.username,
            currentRole = localUser.role,
            needsSmtpSetup = smtp.user.isBlank() || smtp.pass.isBlank(),
        )
        // After successful login, pull remote transaksi & sync local
        viewModelScope.launch {
            try {
                val remote = cloudRepo.fetchTransaksiForUser(localUser.username)
                if (remote.isNotEmpty()) {
                    val existing = _state.value.transaksiList.toMutableList()
                    val existingIds = existing.map { it.id }.toSet()
                    val toAdd = remote.filter { it.id.isNotEmpty() && !existingIds.contains(it.id) }
                    if (toAdd.isNotEmpty()) {
                        val merged = (existing + toAdd).sortedByDescending { it.waktu }
                        _state.value = _state.value.copy(transaksiList = merged)
                        StorageUtil.saveTransaksi(merged)
                    }
                }
            } catch (_: Exception) { }
            try { syncToCloud() } catch (_: Exception) { }
        }
        return AuthResult(true, "")
    }

    // ── Google Sign-In ───────────────────────────────────────
    fun signInWithGoogle(idToken: String, email: String, displayName: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isBusy = true, statusMessage = "Memproses login Google...")
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                var verifiedEmail = email
                val firebaseOk = suspendCancellableCoroutine<Boolean> { cont ->
                    firebaseAuth.signInWithCredential(credential)
                        .addOnSuccessListener {
                            verifiedEmail = it.user?.email ?: email
                            if (!cont.isCompleted) cont.resume(true)
                        }
                        .addOnFailureListener { ex ->
                            Log.i("MainVM", "Firebase Google sign-in: ${ex.message}")
                            if (ex is FirebaseAuthUserCollisionException) {
                                if (!cont.isCompleted) cont.resume(true) // email valid, lanjut local
                            } else {
                                if (!cont.isCompleted) cont.resume(false)
                            }
                        }
                }
                if (!firebaseOk) {
                    _state.value = _state.value.copy(isBusy = false, statusMessage = "Gagal verifikasi Google. Coba lagi.")
                    return@launch
                }
                val users = _state.value.users
                val existing = users.values.firstOrNull { it.email.equals(verifiedEmail, ignoreCase = true) }
                if (existing != null) {
                    StorageUtil.saveCurrentSession(existing.username, existing.role)
                    _state.value = _state.value.copy(
                        isLoggedIn = true, currentUser = existing.username, currentRole = existing.role,
                        needsSmtpSetup = _state.value.smtp.user.isEmpty(),
                        isBusy = false, statusMessage = "",
                    )
                } else {
                    val username = generateUniqueUsername(displayName)
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                    val newUser = UserData(
                        username = username, passwordHash = "", role = "admin", email = verifiedEmail,
                        dibuat = sdf.format(java.util.Date()), emailVerified = true,
                    )
                    val updatedUsers = users + (username to newUser)
                    StorageUtil.saveUsers(updatedUsers)
                    StorageUtil.saveCurrentSession(username, "admin")
                    _state.value = _state.value.copy(
                        users = updatedUsers, isLoggedIn = true, currentUser = username, currentRole = "admin",
                        needsSmtpSetup = _state.value.smtp.user.isEmpty(),
                        isBusy = false, statusMessage = "",
                    )
                }
                try {
                    firebaseAuth.currentUser?.let { u ->
                        launch { withContext(Dispatchers.IO) { cloudRepo.fetchTransaksiForUser(u.email ?: verifiedEmail) } }
                    }
                    launch { try { syncToCloud() } catch (_: Exception) { } }
                } catch (_: Exception) { }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isBusy = false,
                    statusMessage = "Google Sign-In gagal: ${e.localizedMessage}",
                )
            }
        }
    }

    private fun generateUniqueUsername(base: String): String {
        val sanitized = base.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
        val prefix = sanitized.take(12).ifEmpty { "user" }
        val users = _state.value.users
        if (!users.containsKey(prefix)) return prefix
        var suffix = 1
        while (users.containsKey("$prefix$suffix")) suffix++
        return "$prefix$suffix"
    }

    suspend fun sendPasswordReset(email: String): AuthResult {
        val normalized = email.trim()
        if (!isValidEmail(normalized)) return AuthResult(false, "Format email tidak valid")
        val ok = resetPassword(normalized)
        return if (ok) AuthResult(true, "Link reset password telah dikirim ke email Anda") else AuthResult(false, "Gagal mengirim link reset password. Pastikan email sudah terdaftar")
    }

    fun logout() {
        try { firebaseAuth.signOut() } catch (_: Exception) { }
        StorageUtil.clearSession()
        _state.value = _state.value.copy(isLoggedIn = false, currentUser = "", currentRole = "")
    }

    // ── Kasir ──────────────────────────────────────────────
    fun loginKasir(username: String, password: String): Boolean {
        val users = _state.value.users
        val user = users[username.lowercase()] ?: return false
        if (user.role != "kasir") return false
        val hash = sha256(password)
        if (user.passwordHash != hash) return false
        StorageUtil.saveCurrentSession(user.username, user.role)
        _state.value = _state.value.copy(
            isLoggedIn = true,
            currentUser = user.username,
            currentRole = user.role,
        )
        return true
    }

    fun getKasirList(): List<Pair<String, String>> =
        _state.value.users.filter { it.value.role == "kasir" }.map { it.key to it.value.role }.sortedBy { it.first }

    fun switchUser(username: String) {
        val users = _state.value.users
        val user = users[username.lowercase()] ?: return
        StorageUtil.saveCurrentSession(user.username, user.role)
        _state.value = _state.value.copy(
            currentUser = user.username,
            currentRole = user.role,
        )
        // Pull remote transaksi for new user
        viewModelScope.launch {
            try {
                val remote = cloudRepo.fetchTransaksiForUser(user.username)
                if (remote.isNotEmpty()) {
                    val existing = _state.value.transaksiList.toMutableList()
                    val existingIds = existing.map { it.id }.toSet()
                    val toAdd = remote.filter { it.id.isNotEmpty() && !existingIds.contains(it.id) }
                    if (toAdd.isNotEmpty()) {
                        val merged = (existing + toAdd).sortedByDescending { it.waktu }
                        _state.value = _state.value.copy(transaksiList = merged)
                        StorageUtil.saveTransaksi(merged)
                    }
                }
            } catch (_: Exception) { }
        }
    }

    // ── Registrasi ─────────────────────────────────────────
    fun cekUsername(username: String): Boolean = _state.value.users.containsKey(username.lowercase())

    suspend fun daftarUser(username: String, password: String, email: String, role: String): AuthResult {
        val key = username.lowercase().trim()
        val normalizedEmail = email.trim()
        if (key.isEmpty() || password.length < 6) return AuthResult(false, "Username & password minimal 6 karakter")
        if (!password.any { it.isUpperCase() }) return AuthResult(false, "Password harus mengandung huruf besar")
        if (!password.any { it.isDigit() }) return AuthResult(false, "Password harus mengandung angka")
        if (cekUsername(key)) return AuthResult(false, "Username sudah terdaftar")
        if (normalizedEmail.isNotBlank() && !isValidEmail(normalizedEmail)) return AuthResult(false, "Format email tidak valid")
        if (normalizedEmail.isNotBlank() && _state.value.users.values.any { it.email.equals(normalizedEmail, ignoreCase = true) }) {
            return AuthResult(false, "Email sudah terdaftar")
        }

        // Generate 6-digit verification code
        val verificationCode = String.format("%06d", (100000..999999).random())
        val hash = sha256(password)
        val user = UserData(
            username = key,
            passwordHash = hash,
            role = role,
            email = normalizedEmail,
            dibuat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).format(java.util.Date()),
            emailVerified = false,
            verificationCode = verificationCode,
        )

        var firebaseCreated = true
        var firebaseMessage = ""
        if (normalizedEmail.isNotBlank()) {
            val firebaseResult = createFirebaseUser(normalizedEmail, password)
            firebaseCreated = firebaseResult.success
            firebaseMessage = firebaseResult.message
            // Don't send Firebase verification email, use our own code
        }

        if (!firebaseCreated && normalizedEmail.isNotBlank()) {
            return AuthResult(false, firebaseMessage.ifBlank { "Gagal membuat akun Firebase. Cek email/password atau koneksi" })
        }

        val newUsers = _state.value.users.toMutableMap()
        newUsers[key] = user
        _state.value = _state.value.copy(users = newUsers)
        StorageUtil.saveUsers(newUsers)

        val cfg = _state.value.smtp
        val smtpReady = cfg.user.isNotBlank() && cfg.pass.isNotBlank()
        if (normalizedEmail.isNotBlank() && smtpReady) {
            sendEmail(normalizedEmail,
                "Kode Verifikasi RR Billing Pro",
                "Halo $key,\n\nKode verifikasi akun RR Billing Pro Anda: $verificationCode\n\nKode berlaku 10 menit.\n\nTerima kasih.",
            ) { ok, msg ->
                if (!ok) Log.i("MainVM", "Failed to send verification email: $msg")
            }
        }

        // Set trial 3 hari untuk admin pertama
        if (role == "admin" && _state.value.users.size <= 1 && _state.value.trialBatas == 0L) {
            val trialEnd = System.currentTimeMillis() + 3 * 24 * 60 * 60 * 1000L
            _state.value = _state.value.copy(trialBatas = trialEnd, maxTv = 2)
            StorageUtil.saveTrial(trialEnd)
            StorageUtil.saveLicense(_state.value.licenseStatus.copy(maxTv = 2))
        }

        // Sync user list ke Firestore agar License Generator melihat user baru
        viewModelScope.launch { try { syncToCloud() } catch (_: Exception) { } }

        if (normalizedEmail.isNotBlank()) {
            _state.value = _state.value.copy(
                pendingVerifyUser = key,
                pendingVerifyCode = if (smtpReady) "" else verificationCode,
            )
        }

        return if (normalizedEmail.isBlank()) {
            AuthResult(true, "Akun berhasil dibuat! Silakan login.")
        } else {
            AuthResult(true, "Akun berhasil dibuat!${if (smtpReady) " Kode verifikasi telah dikirim ke email Anda." else " Kode verifikasi: $verificationCode"}")
        }
    }

    // ── Verifikasi Email ────────────────────────────────────
    fun verifyEmailCode(username: String, code: String): Boolean {
        val users = _state.value.users.toMutableMap()
        val user = users[username] ?: return false
        if (user.verificationCode != code) return false
        users[username] = user.copy(emailVerified = true, verificationCode = "")
        _state.value = _state.value.copy(users = users, pendingVerifyUser = "", pendingVerifyCode = "")
        StorageUtil.saveUsers(users)
        return true
    }

    fun clearPendingVerify() {
        _state.value = _state.value.copy(pendingVerifyUser = "", pendingVerifyCode = "")
    }

    fun resendCode(username: String): String {
        val users = _state.value.users.toMutableMap()
        val user = users[username] ?: return ""
        val newCode = String.format("%06d", (100000..999999).random())
        users[username] = user.copy(verificationCode = newCode)
        _state.value = _state.value.copy(users = users)
        StorageUtil.saveUsers(users)
        if (user.email.isNotBlank()) {
            sendEmail(user.email,
                "Kode Verifikasi RR Billing Pro",
                "Halo $username,\n\nKode verifikasi akun RR Billing Pro Anda: $newCode\n\nKode berlaku 10 menit.\n\nTerima kasih.",
            ) { ok, msg ->
                if (!ok) Log.i("MainVM", "Failed to resend verification email: $msg")
            }
        }
        return newCode
    }

    // ── Auto Update ────────────────────────────────────────
    private val githubApiUrl = "https://api.github.com/repos/dedekemoking-commits/rrbillingprov2/releases/latest"

    fun checkForUpdate() {
        viewModelScope.launch {
            _state.value = _state.value.copy(updateChecked = false, updateInfo = null)
            try {
                val json = withContext(Dispatchers.IO) {
                    URL(githubApiUrl).readText()
                }
                val obj = JSONObject(json)
                val tagName = obj.optString("tag_name", "").removePrefix("v")
                val apkUrl = obj.optJSONArray("assets")
                    ?.optJSONObject(0)
                    ?.optString("browser_download_url", "") ?: ""
                val changelog = obj.optString("body", "")

                if (tagName > APP_VERSION && apkUrl.isNotBlank()) {
                    _state.value = _state.value.copy(
                        updateInfo = UpdateInfo(tagName, apkUrl, changelog),
                        updateChecked = true,
                    )
                } else {
                    _state.value = _state.value.copy(updateChecked = true)
                }
            } catch (e: Exception) {
                Log.i("MainVM", "checkForUpdate failed: ${e.message}")
                _state.value = _state.value.copy(updateChecked = true)
            }
        }
    }

    fun dismissUpdate() {
        _state.value = _state.value.copy(updateInfo = null)
    }

    fun downloadAndInstall(ctx: android.content.Context) {
        val info = _state.value.updateInfo ?: return
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(downloadProgress = 0)
                val file = withContext(Dispatchers.IO) {
                    val cacheDir = ctx.cacheDir
                    val apkFile = File(cacheDir, "update.apk")
                    val url = URL(info.apkUrl)
                    url.openConnection().let { conn ->
                        conn.connect()
                        val totalBytes = conn.contentLengthLong
                        var downloaded = 0L
                        conn.getInputStream().use { input ->
                            FileOutputStream(apkFile).use { output ->
                                val buffer = ByteArray(8192)
                                var read: Int
                                while (input.read(buffer).also { read = it } != -1) {
                                    output.write(buffer, 0, read)
                                    downloaded += read
                                    if (totalBytes > 0) {
                                        val pct = ((downloaded * 100) / totalBytes).toInt()
                                        _state.value = _state.value.copy(downloadProgress = pct.coerceIn(0, 99))
                                    }
                                }
                            }
                        }
                    }
                    apkFile
                }
                _state.value = _state.value.copy(downloadProgress = 100)
                val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(intent)
                _state.value = _state.value.copy(updateInfo = null, downloadProgress = -1)
            } catch (e: Exception) {
                Log.i("MainVM", "downloadAndInstall failed: ${e.message}")
                _state.value = _state.value.copy(downloadProgress = -1)
            }
        }
    }

    // ── TV Management ──────────────────────────────────────
    fun tambahTV(tv: TvData): Boolean {
        val maxTv = _state.value.maxTv
        if (maxTv > 0 && _state.value.tvList.size >= maxTv) return false
        val list = _state.value.tvList.toMutableList()
        list.add(tv)
        _state.value = _state.value.copy(tvList = list)
        StorageUtil.saveTvList(list)
        return true
    }

    fun updateTV(id: String, updates: Map<String, Any>) {
        val list = _state.value.tvList.map { tv ->
            if (tv.id != id) tv
            else {
                var t = tv
                updates.forEach { (k, v) ->
                    t = try {
                        when (k) {
                            "nama" -> t.copy(nama = v as? String ?: return@forEach)
                            "ip" -> t.copy(ip = v as? String ?: return@forEach)
                            "port" -> t.copy(port = (v as? Number)?.toInt() ?: return@forEach)
                            "jenisPs" -> t.copy(jenisPs = v as? String ?: return@forEach)
                            "paketAktif" -> t.copy(paketAktif = v as? String ?: return@forEach)
                            "sisaDetik" -> t.copy(sisaDetik = (v as? Number)?.toLong() ?: return@forEach)
                            "timerActive" -> t.copy(timerActive = v as? Boolean ?: return@forEach)
                            "bebas" -> t.copy(bebas = v as? Boolean ?: return@forEach)
                            "paketHarga" -> t.copy(paketHarga = (v as? Number)?.toInt() ?: return@forEach)
                            "totalPesanan" -> t.copy(totalPesanan = (v as? Number)?.toInt() ?: return@forEach)
                            "bebasMulai" -> t.copy(bebasMulai = (v as? Number)?.toLong() ?: return@forEach)
                            "bebasHargaPerJam" -> t.copy(bebasHargaPerJam = (v as? Number)?.toInt() ?: return@forEach)
                            "bebasPesananTotal" -> t.copy(bebasPesananTotal = (v as? Number)?.toInt() ?: return@forEach)
                            "paired" -> t.copy(paired = v as? Boolean ?: return@forEach)
                            "certPem" -> t.copy(certPem = v as? String ?: return@forEach)
                            "keyPem" -> t.copy(keyPem = v as? String ?: return@forEach)
                            "cancelBatas" -> t.copy(cancelBatas = (v as? Number)?.toLong() ?: return@forEach)
                            "sudahBayar" -> t.copy(sudahBayar = v as? Boolean ?: return@forEach)
                            else -> t
                        }
                    } catch (e: Exception) {
                        Log.w("MainVM", "updateTV: skip bad value for $k: ${v?.javaClass?.name} ($e)")
                        t
                    }
                }
                t
            }
        }
        _state.value = _state.value.copy(tvList = list)
        StorageUtil.saveTvList(list)
    }

    fun hapusTV(id: String) {
        val list = _state.value.tvList.filter { it.id != id }
        _state.value = _state.value.copy(tvList = list)
        StorageUtil.saveTvList(list)
    }

    // ── Transaksi ──────────────────────────────────────────
    fun tambahTransaksi(t: Transaksi) {
        val tx = t.copy(id = "tx_${System.currentTimeMillis()}")
        val list = (_state.value.transaksiList + tx)
        _state.value = _state.value.copy(transaksiList = list)
        StorageUtil.saveTransaksi(list)
        // Auto-sync to cloud after new transaction
        try {
            Log.i("MainVM", "auto-sync triggered after tambahTransaksi id=${tx.id}")
            syncToCloud()
        } catch (ex: Exception) { Log.i("MainVM", "auto-sync failed to start: ${ex.message}") }
    }

    fun bersihkanTransaksi() {
        _state.value = _state.value.copy(transaksiList = emptyList())
        StorageUtil.saveTransaksi(emptyList())
        // Auto-sync to cloud after clearing transactions
        try { syncToCloud() } catch (_: Exception) { /* ignore */ }
    }

    // ── Harga ──────────────────────────────────────────────
    fun saveHarga(paketMain: Map<String, Map<String, Int>>, paketDurasi: Map<String, Int>,
                  menuMakanan: Map<String, Int>, menuMinuman: Map<String, Int>) {
        _state.value = _state.value.copy(
            paketMain = paketMain, paketDurasi = paketDurasi,
            menuMakanan = menuMakanan, menuMinuman = menuMinuman,
        )
        StorageUtil.saveHarga(paketMain, paketDurasi, menuMakanan, menuMinuman)
    }

    // ── License ────────────────────────────────────────────
    fun aktivasiLisensi(kode: String, onResult: (Boolean, String) -> Unit) {
        val trimmed = kode.trim().uppercase()
        if (trimmed.length < 4) {
            onResult(false, "Kode tidak valid"); return
        }
        viewModelScope.launch {
            val record = cloudRepo.findLicenseByCode(trimmed)
            if (record == null) {
                onResult(false, "Kode tidak ditemukan di server. Periksa kembali atau hubungi admin.")
                return@launch
            }
            if (record.revoked) {
                onResult(false, "Kode ini sudah dicabut. Hubungi admin.")
                return@launch
            }
            if (record.activatedAt > 0) {
                onResult(false, "Kode ini sudah digunakan. Hubungi admin untuk kode baru.")
                return@launch
            }
            if (!ECDSAUtils.verify(record.payload, record.signature)) {
                onResult(false, "Kode tidak valid (signature mismatch). Hubungi admin.")
                return@launch
            }
            val currentEmail = _state.value.users[_state.value.currentUser]?.email ?: ""
            if (record.email.isNotBlank() && !record.email.equals(currentEmail, ignoreCase = true)) {
                onResult(false, "Kode ini khusus untuk ${record.email}. Gunakan akun email yang sesuai.")
                return@launch
            }
            val paket = record.paket
            val durasiHari = when (paket) {
                "BULANAN" -> 30; "3BULAN" -> 90; "TAHUNAN" -> 360; "LIFETIME" -> 99999; else -> 30
            }
            val maxTv = when (paket) {
                "BULANAN" -> 5; "3BULAN" -> 8; "TAHUNAN" -> 15; "LIFETIME" -> 0; else -> 2
            }
            val cal = java.util.Calendar.getInstance()
            cal.add(java.util.Calendar.DAY_OF_YEAR, durasiHari)
            val expires = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(cal.time)
            val ls = LicenseStatus(
                status = "active",
                pesan = "✅ Lisensi $paket aktif hingga $expires",
                expiresAt = expires,
                maxTv = maxTv,
            )
            _state.value = _state.value.copy(licenseStatus = ls, trialBatas = 0L, maxTv = maxTv)
            StorageUtil.saveLicense(ls)
            cloudRepo.activateLicense(record.id)
            onResult(true, ls.pesan)
        }
    }

    fun generateLicenseKode(paket: String, username: String, email: String = "", onDone: (String) -> Unit) {
        val shortCode = (10000000..99999999).random().toString() + (65..90).random().toChar()
        val nonce = java.util.UUID.randomUUID().toString().take(8)
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_YEAR, when (paket) {
            "BULANAN" -> 30; "3BULAN" -> 90; "TAHUNAN" -> 360; "LIFETIME" -> 99999; else -> 30
        })
        val expiry = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(cal.time)
        val payload = """{"p":"$paket","u":"$username","e":"$expiry","n":"$nonce","m":"$email"}"""
        val signature = ECDSAUtils.sign(payload)
        val kode = shortCode
        val record = KodeGenerasi(
            waktu = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).format(java.util.Date()),
            username = _state.value.currentUser,
            paket = paket,
            kode = kode,
        )
        val newList = _state.value.kodeGenerasiList + record
        _state.value = _state.value.copy(kodeGenerasiList = newList)
        StorageUtil.saveKodeGenerasiList(newList)
        // Write to Firestore
        viewModelScope.launch {
            try {
                if (cloudRepo.ensureSignedIn()) {
                    val doc = HashMap<String, Any>()
                    doc["kode"] = kode
                    doc["payload"] = payload
                    doc["signature"] = signature
                    doc["paket"] = paket
                    doc["username"] = username
                    doc["email"] = email
                    doc["expiry"] = expiry
                    doc["generatedBy"] = _state.value.currentUser
                    doc["generatedAt"] = System.currentTimeMillis()
                    doc["activatedAt"] = 0L
                    doc["activatedDeviceId"] = ""
                    doc["revoked"] = false
                    com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("licenses").add(doc)
                }
            } catch (_: Exception) { }
            onDone(kode)
        }
    }

    fun gantiPassword(username: String, newPassword: String): Boolean {
        if (newPassword.length < 6) return false
        val users = _state.value.users.toMutableMap()
        val existing = users[username] ?: return false
        users[username] = existing.copy(passwordHash = sha256(newPassword))
        _state.value = _state.value.copy(users = users)
        StorageUtil.saveUsers(users)
        return true
    }

    fun saveSmtp(cfg: SmtpConfig) {
        _state.value = _state.value.copy(smtp = cfg, needsSmtpSetup = false)
        StorageUtil.saveSmtp(cfg)
    }

    fun clearNeedsSmtpSetup() {
        _state.value = _state.value.copy(needsSmtpSetup = false)
    }

    fun sendEmail(recipient: String, subject: String, body: String, onResult: (Boolean, String) -> Unit) {
        val cfg = _state.value.smtp
        if (cfg.user.isBlank() || cfg.pass.isBlank()) {
            onResult(false, "SMTP belum dikonfigurasi. Konfigurasi di halaman Profil.")
            return
        }
        Thread {
            try {
                val props = Properties().apply {
                    put("mail.smtp.host", cfg.host.ifBlank { "smtp.gmail.com" })
                    put("mail.smtp.port", cfg.port.toString())
                    put("mail.smtp.auth", "true")
                    put("mail.smtp.starttls.enable", "true")
                }
                val session = Session.getInstance(props, object : Authenticator() {
                    override fun getPasswordAuthentication() = PasswordAuthentication(cfg.user, cfg.pass)
                })
                val message = MimeMessage(session).apply {
                    setFrom(InternetAddress(cfg.user))
                    setRecipient(Message.RecipientType.TO, InternetAddress(recipient))
                    setSubject(subject)
                    setText(body)
                }
                Transport.send(message)
                onResult(true, "Email terkirim")
            } catch (e: Exception) {
                Log.i("MainVM", "sendEmail failed: ${e.message}")
                onResult(false, "Gagal kirim email: ${e.message}")
            }
        }.start()
    }

    fun setStatus(msg: String) {
        _state.value = _state.value.copy(statusMessage = msg)
    }

    fun importFromCloud() {
        val username = _state.value.currentUser
        if (username.isBlank()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(statusMessage = "Mengimpor transaksi...")
            try {
                val remote = withContext(Dispatchers.IO) { cloudRepo.fetchTransaksiForUser(username) }
                if (remote.isNotEmpty()) {
                    val existing = _state.value.transaksiList.toMutableList()
                    val existingIds = existing.map { it.id }.toSet()
                    val toAdd = remote.filter { it.id.isNotEmpty() && !existingIds.contains(it.id) }
                    if (toAdd.isNotEmpty()) {
                        val merged = (existing + toAdd).sortedByDescending { it.waktu }
                        _state.value = _state.value.copy(transaksiList = merged)
                        StorageUtil.saveTransaksi(merged)
                    }
                    _state.value = _state.value.copy(statusMessage = "Import: ${toAdd.size} transaksi baru")
                } else {
                    _state.value = _state.value.copy(statusMessage = "Import: tidak ada data")
                }
            } catch (e: Exception) {
                Log.i("MainVM", "importFromCloud failed: ${e.message}")
                _state.value = _state.value.copy(statusMessage = "Import gagal")
            }
        }
    }

    fun syncToCloud() {
        Log.i("MainVM", "trigger syncToCloud for user=${_state.value.currentUser}")
        viewModelScope.launch {
            _state.value = _state.value.copy(cloudStatus = "syncing", statusMessage = "Syncing to cloud...")
            val snapshot = _state.value
            val ok = try { withContext(Dispatchers.IO) { cloudRepo.syncAll(snapshot, snapshot.currentUser) } } catch (t: Throwable) { Log.i("MainVM", "syncToCloud exception: ${t.message}"); false }
            val now = if (ok) System.currentTimeMillis() else _state.value.cloudLastSync
            Log.i("MainVM", "syncToCloud result ok=$ok")
            _state.value = _state.value.copy(
                cloudStatus = if (ok) "connected" else "error",
                cloudLastSync = now,
                statusMessage = if (ok) "Cloud sync successful" else "Cloud sync failed"
            )
        }
    }
}
