package com.billingps.aptv.models

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.util.Patterns
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.billingps.aptv.utils.ConnectivityObserver
import com.billingps.aptv.utils.ECDSAUtils
import com.billingps.aptv.utils.FcmSender
import com.billingps.aptv.utils.StorageUtil
import com.billingps.aptv.cloud.CloudRepository
import com.billingps.aptv.ui.theme.ThemeOption
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.messaging.FirebaseMessaging
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
    val invoices: List<Invoice> = emptyList(),
    val pendingInvoiceCount: Int = 0,
    val tvPasswordHash: String = "",
    val themeOption: ThemeOption = ThemeOption.GAMING_DARK,
    val promo: PromoSettings = PromoSettings(),
)

fun defaultPaketMain(): Map<String, Map<String, Int>> {
    val defaultPS = mapOf(
        "15 Menit" to 5000, "30 Menit" to 10000, "1 Jam" to 15000,
        "2 Jam" to 25000, "3 Jam" to 35000,
    )
    return mapOf("PS3" to defaultPS, "PS4" to defaultPS, "PS5" to defaultPS)
}

fun defaultPaketDurasi(): Map<String, Int> = mapOf(
    "15 Menit" to 15, "30 Menit" to 30, "1 Jam" to 60,
    "2 Jam" to 120, "3 Jam" to 180, "Main Bebas" to 0,
)

    const val APP_VERSION = "2.2.4"

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val cloudRepo = CloudRepository(application)
    private val firebaseAuth = FirebaseAuth.getInstance()
    private var userDocListener: ListenerRegistration? = null
    private var invoicesListener: ListenerRegistration? = null
    private val connectivityObserver = ConnectivityObserver(application)

    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    init {
        StorageUtil.init(application)
        ECDSAUtils.init(application)
        FcmSender.init(application)
        loadAll()
        // Offline-first: auto-sync when connectivity restored
        connectivityObserver.start()
        viewModelScope.launch {
            connectivityObserver.status.collect { status ->
                if (status == ConnectivityObserver.Status.AVAILABLE && _state.value.isLoggedIn) {
                    Log.i("MainVM", "Connectivity restored, auto-syncing...")
                    syncToCloud()
                }
            }
        }
    }

    private fun loadAll() {
        val users = StorageUtil.loadUsers()
        val cu = StorageUtil.loadCurrentUser()
        val cr = StorageUtil.loadCurrentRole()
        val lic = StorageUtil.loadLicense()
        val trial = StorageUtil.loadTrialBatas()
        Log.i("MainVM", "loadAll: license status='${lic.status}' expires='${lic.expiresAt}' maxTv=${lic.maxTv} trial=$trial")
        val themeName = StorageUtil.loadThemeOption()
        val savedTheme = try { ThemeOption.valueOf(themeName) } catch (_: Exception) { ThemeOption.GAMING_DARK }
        _state.value = _state.value.copy(
            isLoggedIn = cu.isNotEmpty(),
            currentUser = cu,
            currentRole = cr,
            themeOption = savedTheme,
            users = users,
            tvList = StorageUtil.loadTvList(),
            transaksiList = StorageUtil.loadTransaksi(),
            paketMain = StorageUtil.loadPaketMain().ifEmpty { defaultPaketMain() },
            paketDurasi = StorageUtil.loadPaketDurasi().ifEmpty { defaultPaketDurasi() },
            menuMakanan = StorageUtil.loadMenuMakanan(),
            menuMinuman = StorageUtil.loadMenuMinuman(),
            licenseStatus = lic,
            smtp = StorageUtil.loadSmtp(),
            kodeGenerasiList = StorageUtil.loadKodeGenerasiList(),
            trialBatas = trial,
            maxTv = lic.maxTv,
            invoices = StorageUtil.loadInvoices(),
            tvPasswordHash = StorageUtil.loadTvPassword(),
            promo = StorageUtil.loadPromo(),
        )
        _state.value = _state.value.copy(pendingInvoiceCount = _state.value.invoices.count { it.status == "WAITING_CONFIRMATION" })
        // Check trial expiry
        val s = _state.value
        if (s.trialBatas > 0 && s.trialBatas < System.currentTimeMillis() && s.licenseStatus.status != "active") {
            Log.i("MainVM", "loadAll: trial expired, clearing")
            _state.value = _state.value.copy(trialBatas = 0L, maxTv = 0)
            StorageUtil.saveTrial(0L)
        }
        // Start real-time license listener if logged in
        if (cu.isNotEmpty()) {
            startLicenseListener(cu)
            startPromoListener()
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
                try { sendFirebasePasswordReset(user.email) } catch (e: Exception) { Log.e("MainVM", "sendFirebasePasswordReset after resetPasswordWithCode: ${e.message}") }
            }
        }
        return AuthResult(true, "Password berhasil direset! Silakan login.")
    }

    private suspend fun sendFirebasePasswordReset(email: String) {
        try {
            firebaseAuth.sendPasswordResetEmail(email)
                .addOnSuccessListener { Log.i("MainVM", "Firebase password reset email sent to $email") }
                .addOnFailureListener { Log.i("MainVM", "Firebase password reset email failed: ${it.message}") }
        } catch (e: Exception) { Log.e("MainVM", "sendFirebasePasswordReset exception: ${e.message}") }
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
                val sessionUser = if (effectiveUser != null) {
                    effectiveUser
                } else {
                    val remoteUsername = withContext(Dispatchers.IO) { cloudRepo.findUsernameByEmail(input) }
                    if (remoteUsername != null) {
                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                        val restoredUser = UserData(
                            username = remoteUsername, passwordHash = sha256(password), role = "admin",
                            email = input, dibuat = sdf.format(java.util.Date()), emailVerified = true,
                        )
                        val updatedUsers = _state.value.users + (remoteUsername to restoredUser)
                        _state.value = _state.value.copy(users = updatedUsers)
                        StorageUtil.saveUsers(updatedUsers)
                        restoredUser
                    } else {
                        UserData(username = input, role = "admin", email = input)
                    }
                }
                StorageUtil.saveCurrentSession(sessionUser.username, sessionUser.role)
                val smtp = _state.value.smtp
                _state.value = _state.value.copy(
                    isLoggedIn = true,
                    currentUser = sessionUser.username,
                    currentRole = sessionUser.role,
                    needsSmtpSetup = smtp.user.isBlank() || smtp.pass.isBlank(),
                )
                restoreLicenseAndTrial(sessionUser.username)
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
                    restoreLicenseAndTrial(fallbackUser.username)
                    return AuthResult(true, "")
                }
            }
            return firebaseResult
        }

        if (localUser == null) return AuthResult(false, "Username/Password salah")
        val hash = sha256(password)
        if (localUser.passwordHash != hash) return AuthResult(false, "Username/Password salah")
        addActivityLog("Login")
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
            } catch (e: Exception) { Log.e("MainVM", "fetchTransaksiForUser after login: ${e.message}") }
            restoreLicenseAndTrial(localUser.username)
            try { syncToCloud() } catch (e: Exception) { Log.e("MainVM", "syncToCloud after login: ${e.message}") }
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
                    addActivityLog("Login via Google")
                    StorageUtil.saveCurrentSession(existing.username, existing.role)
                    _state.value = _state.value.copy(
                        isLoggedIn = true, currentUser = existing.username, currentRole = existing.role,
                        needsSmtpSetup = _state.value.smtp.user.isEmpty(),
                        isBusy = false, statusMessage = "",
                    )
                    restoreLicenseAndTrial(existing.username)
                } else {
                    val remoteUsername = withContext(Dispatchers.IO) { cloudRepo.findUsernameByEmail(verifiedEmail) }
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                    if (remoteUsername != null) {
                        val restoredUser = UserData(
                            username = remoteUsername, passwordHash = "", role = "admin",
                            email = verifiedEmail, dibuat = sdf.format(java.util.Date()), emailVerified = true,
                        )
                        val updatedUsers = users + (remoteUsername to restoredUser)
                        StorageUtil.saveUsers(updatedUsers)
                        StorageUtil.saveCurrentSession(remoteUsername, "admin")
                        _state.value = _state.value.copy(
                            users = updatedUsers, isLoggedIn = true, currentUser = remoteUsername, currentRole = "admin",
                            isBusy = false, statusMessage = "",
                        )
                        restoreLicenseAndTrial(remoteUsername)
                    } else {
                        val username = generateUniqueUsername(displayName)
                        val newUser = UserData(
                            username = username, passwordHash = "", role = "admin", email = verifiedEmail,
                            dibuat = sdf.format(java.util.Date()), emailVerified = true,
                        )
                        val updatedUsers = users + (username to newUser)
                        StorageUtil.saveUsers(updatedUsers)
                        StorageUtil.saveCurrentSession(username, "admin")
                        _state.value = _state.value.copy(
                            users = updatedUsers, isLoggedIn = true, currentUser = username, currentRole = "admin",
                            isBusy = false, statusMessage = "",
                        )
                        if (_state.value.trialBatas == 0L && _state.value.licenseStatus.status != "active") {
                            val trialEnd = System.currentTimeMillis() + 3 * 24 * 60 * 60 * 1000L
                            _state.value = _state.value.copy(trialBatas = trialEnd, maxTv = 2)
                            StorageUtil.saveTrial(trialEnd)
                            launch { try { cloudRepo.saveTrialToCloud(username, trialEnd) } catch (e: Exception) { Log.e("MainVM", "saveTrialToCloud on Google sign-in new user: ${e.message}") } }
                        }
                    }
                }
                try {
                    firebaseAuth.currentUser?.let { u ->
                        launch { withContext(Dispatchers.IO) { cloudRepo.fetchTransaksiForUser(u.email ?: verifiedEmail) } }
                    }
                    launch { try { syncToCloud() } catch (e: Exception) { Log.e("MainVM", "syncToCloud after Google sign-in: ${e.message}") } }
                } catch (e: Exception) { Log.e("MainVM", "post-Google-sign-in sync block: ${e.message}") }
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

    fun setTheme(option: ThemeOption) {
        StorageUtil.saveThemeOption(option.name)
        _state.value = _state.value.copy(themeOption = option)
    }

    fun logout() {
        addActivityLog("Logout")
        stopLicenseListener()
        stopPromoListener()
        try { firebaseAuth.signOut() } catch (e: Exception) { Log.e("MainVM", "firebaseAuth.signOut on logout: ${e.message}") }
        StorageUtil.clearSession()
        _state.value = _state.value.copy(isLoggedIn = false, currentUser = "", currentRole = "")
    }

    private suspend fun restoreLicenseAndTrial(username: String) {
        try {
            val cloudLic = withContext(Dispatchers.IO) { cloudRepo.fetchLicenseForUser(username) }
            if (cloudLic != null && cloudLic.status.isNotEmpty()) {
                _state.value = _state.value.copy(licenseStatus = cloudLic, trialBatas = 0L, maxTv = cloudLic.maxTv)
                StorageUtil.saveLicense(cloudLic)
            }
        } catch (e: Exception) { Log.e("MainVM", "fetchLicenseForUser in restoreLicenseAndTrial: ${e.message}") }
        val s = _state.value
        if (s.trialBatas == 0L && s.licenseStatus.status != "active") {
            val cloudTrial = withContext(Dispatchers.IO) { cloudRepo.loadTrialFromCloud(username) }
            if (cloudTrial <= 0L) {
                // No existing trial in cloud — this is a truly new user, give trial
                val trialEnd = System.currentTimeMillis() + 3 * 24 * 60 * 60 * 1000L
                _state.value = _state.value.copy(trialBatas = trialEnd, maxTv = 2)
                StorageUtil.saveTrial(trialEnd)
                withContext(Dispatchers.IO) { cloudRepo.saveTrialToCloud(username, trialEnd) }
            } else if (cloudTrial > System.currentTimeMillis()) {
                // Cloud trial still active — restore it
                _state.value = _state.value.copy(trialBatas = cloudTrial, maxTv = 2)
                StorageUtil.saveTrial(cloudTrial)
            }
            // else: cloud trial expired — don't give new trial, leave trialBatas = 0
        }
        startLicenseListener(username)
        startPromoListener()
        initFcmToken(username)
    }

    private fun startLicenseListener(username: String) {
        stopLicenseListener()
        Log.i("MainVM", "startLicenseListener for $username")
        userDocListener = cloudRepo.listenUserLicense(username) { cloudLic ->
            if (cloudLic != null) {
                Log.i("MainVM", "listener: license updated to ${cloudLic.status} expires=${cloudLic.expiresAt}")
                _state.value = _state.value.copy(licenseStatus = cloudLic, trialBatas = 0L, maxTv = cloudLic.maxTv)
                StorageUtil.saveLicense(cloudLic)
                // Activate the unactivated license doc so License Generator history shows it as activated
                viewModelScope.launch {
                    withContext(Dispatchers.IO) {
                        cloudRepo.findAndActivateLicenseByUsername(username, cloudLic.expiresAt, deviceType = "android")
                    }
                }
            } else {
                // Only clear if current license is not active locally
                val current = _state.value.licenseStatus
                if (current.pesan.contains("✅")) {
                    // license was active on this device from local activation — don't clear
                    return@listenUserLicense
                }
            }
        }
        invoicesListener = cloudRepo.listenUserInvoices(username) { cloudInv ->
            val currentInvs = _state.value.invoices.toMutableList()
            val idx = currentInvs.indexOfFirst { it.id == cloudInv.id }
            if (idx >= 0) {
                val existing = currentInvs[idx]
                if (existing.status != "CONFIRMED") {
                    currentInvs[idx] = cloudInv
                    _state.value = _state.value.copy(
                        invoices = currentInvs,
                        pendingInvoiceCount = currentInvs.count { it.status == "WAITING_CONFIRMATION" },
                    )
                    StorageUtil.saveInvoices(currentInvs)
                    Log.i("MainVM", "invoices listener: updated invoice ${cloudInv.id} to CONFIRMED")
                }
            } else {
                currentInvs.add(cloudInv)
                _state.value = _state.value.copy(
                    invoices = currentInvs,
                    pendingInvoiceCount = currentInvs.count { it.status == "WAITING_CONFIRMATION" },
                )
                StorageUtil.saveInvoices(currentInvs)
                Log.i("MainVM", "invoices listener: added CONFIRMED invoice ${cloudInv.id}")
            }
        }
    }

    private var promoListener: ListenerRegistration? = null

    private fun startPromoListener() {
        stopPromoListener()
        viewModelScope.launch {
            cloudRepo.ensureSignedIn()
            promoListener = cloudRepo.listenGlobalSettings { promo ->
                _state.value = _state.value.copy(promo = promo)
                StorageUtil.savePromo(promo)
            }
        }
    }

    private fun stopPromoListener() {
        promoListener?.remove()
        promoListener = null
    }

    private fun initFcmToken(username: String) {
        viewModelScope.launch {
            try {
                cloudRepo.ensureSignedIn()
                var token = StorageUtil.loadFcmToken()
                if (token.isEmpty()) {
                    token = suspendCancellableCoroutine { cont ->
                        FirebaseMessaging.getInstance().token
                            .addOnSuccessListener { t -> Log.i("FCM", "Fetched token: ${t.take(20)}..."); cont.resume(t) }
                            .addOnFailureListener { e -> Log.e("FCM", "Failed to get token: ${e.message}"); cont.resume("") }
                    }
                    if (token.isNotEmpty()) StorageUtil.saveFcmToken(token)
                }
                if (token.isNotEmpty()) {
                    cloudRepo.saveFcmToken(username, token)
                    Log.i("FCM", "Token saved to Firestore for $username")
                } else {
                    Log.w("FCM", "No token available for $username")
                }
            } catch (e: Exception) {
                Log.e("FCM", "initFcmToken exception: ${e.message}")
            }
        }
    }

    fun hargaSetelahDiskon(namaPaket: String, hargaAsli: Int): Int {
        val s = _state.value
        if (!s.promo.promoAktif) return hargaAsli
        val key = when (namaPaket) {
            "1 Bulan", "BULANAN" -> "1 Bulan"
            "3 Bulan", "3BULAN" -> "3 Bulan"
            "1 Tahun", "TAHUNAN" -> "1 Tahun"
            "LIFETIME" -> "LIFETIME"
            else -> null
        }
        val diskon = key?.let { s.promo.diskonPerPaket[it] } ?: 0
        if (diskon <= 0) return hargaAsli
        return (hargaAsli * (100 - diskon.coerceIn(0, 100))) / 100
    }

    fun getAddTvOverride(namaPaket: String): Int? {
        val s = _state.value
        if (!s.promo.promoAktif) return null
        val key = when (namaPaket) {
            "1 Bulan", "BULANAN" -> "1 Bulan"
            "3 Bulan", "3BULAN" -> "3 Bulan"
            "1 Tahun", "TAHUNAN" -> "1 Tahun"
            "LIFETIME" -> "LIFETIME"
            else -> null
        }
        return key?.let { s.promo.addTvOverride[it] }
    }

    private fun stopLicenseListener() {
        userDocListener?.remove()
        userDocListener = null
        invoicesListener?.remove()
        invoicesListener = null
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
        addActivityLog("Login kasir")
        startPromoListener()
        initFcmToken(user.username)
        return true
    }

    fun getKasirList(): List<Pair<String, String>> =
        _state.value.users.filter { it.value.role == "kasir" }.map { it.key to it.value.role }.sortedBy { it.first }

    // ── Auto Push Notifications ────────────────────────────
    private fun sendPushNotification(title: String, body: String) {
        val username = _state.value.currentUser
        if (username.isBlank()) return
        viewModelScope.launch {
            try {
                val token = suspendCancellableCoroutine<String?> { cont ->
                    com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("billingps_users").document(username).get()
                        .addOnSuccessListener { cont.resume(it.getString("fcmToken")) }
                        .addOnFailureListener { cont.resume(null) }
                }
                if (!token.isNullOrBlank()) {
                    FcmSender.sendToUser(token, title, body)
                }
            } catch (e: Exception) {
                Log.e("MainVM", "sendPushNotification failed: ${e.message}")
            }
        }
    }

    // ── Activity Log ───────────────────────────────────────
    private fun addActivityLog(event: String) {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        val time = sdf.format(java.util.Date())
        val user = _state.value.currentUser
        val entry = "[$time] $user: $event"
        val log = StorageUtil.loadActivityLog().toMutableList()
        log.add(entry)
        if (log.size > 200) log.removeAt(0) // Keep last 200 entries
        StorageUtil.saveActivityLog(log)
    }

    fun getActivityLog(): List<String> = StorageUtil.loadActivityLog().reversed()

    // ── Backup & Restore ──────────────────────────────────
    fun exportBackupJson(): String {
        val s = _state.value
        val obj = org.json.JSONObject()
        obj.put("exportVersion", APP_VERSION)
        obj.put("exportedAt", System.currentTimeMillis())
        obj.put("users", org.json.JSONObject(StorageUtil.loadUsers().mapValues { (_, u) ->
            org.json.JSONObject().apply {
                put("username", u.username); put("role", u.role); put("email", u.email); put("dibuat", u.dibuat)
            }
        }))
        obj.put("tvList", org.json.JSONArray(StorageUtil.loadTvList().map { tv ->
            org.json.JSONObject().apply {
                put("id", tv.id); put("nama", tv.nama); put("ip", tv.ip); put("port", tv.port)
                put("jenisPs", tv.jenisPs); put("paired", tv.paired)
            }
        }))
        obj.put("transaksiList", org.json.JSONArray(StorageUtil.loadTransaksi().map { t ->
            org.json.JSONObject().apply {
                put("id", t.id); put("waktu", t.waktu); put("kasir", t.kasir)
                put("kota", t.kota); put("paket", t.paket); put("total", t.total)
            }
        }))
        obj.put("paketMain", org.json.JSONObject(s.paketMain.mapValues { (_, v) -> org.json.JSONObject(v) }))
        obj.put("paketDurasi", org.json.JSONObject(s.paketDurasi))
        obj.put("menuMakanan", org.json.JSONObject(s.menuMakanan))
        obj.put("menuMinuman", org.json.JSONObject(s.menuMinuman))
        return obj.toString(2)
    }

    fun importBackupJson(jsonStr: String): String {
        return try {
            val obj = org.json.JSONObject(jsonStr)
            if (!obj.has("exportVersion")) return "File backup tidak valid"

            // Restore users
            if (obj.has("users")) {
                val usersRaw = obj.getJSONObject("users")
                val users = mutableMapOf<String, UserData>()
                usersRaw.keys().forEach { key ->
                    val u = usersRaw.getJSONObject(key)
                    users[key] = UserData(
                        username = u.optString("username"),
                        passwordHash = "",
                        role = u.optString("role", "kasir"),
                        email = u.optString("email"),
                        dibuat = u.optString("dibuat"),
                    )
                }
                StorageUtil.saveUsers(users)
                _state.value = _state.value.copy(users = users)
            }

            // Restore TV list
            if (obj.has("tvList")) {
                val arr = obj.getJSONArray("tvList")
                val list = mutableListOf<TvData>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(TvData(
                        id = o.optString("id"), nama = o.optString("nama"),
                        ip = o.optString("ip"), port = o.optInt("port", 5555),
                        jenisPs = o.optString("jenisPs", "PS3"), paired = o.optBoolean("paired"),
                    ))
                }
                StorageUtil.saveTvList(list)
                _state.value = _state.value.copy(tvList = list)
            }

            // Restore transactions
            if (obj.has("transaksiList")) {
                val arr = obj.getJSONArray("transaksiList")
                val list = mutableListOf<Transaksi>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(Transaksi(
                        id = o.optString("id"), waktu = o.optString("waktu"),
                        kasir = o.optString("kasir"), kota = o.optString("kota"),
                        paket = o.optString("paket"), total = o.optInt("total"),
                    ))
                }
                StorageUtil.saveTransaksi(list)
                _state.value = _state.value.copy(transaksiList = list)
            }

            addActivityLog("Restore backup")
            "✅ Backup berhasil direstore! ${obj.optString("exportedAt", "")}"
        } catch (e: Exception) {
            "❌ Gagal restore: ${e.message}"
        }
    }

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
            } catch (e: Exception) { Log.e("MainVM", "fetchTransaksiForUser in switchUser: ${e.message}") }
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

        // Set trial 3 hari max 2 TV untuk semua user tanpa lisensi aktif
        if (_state.value.trialBatas == 0L && _state.value.licenseStatus.status != "active") {
            val trialEnd = System.currentTimeMillis() + 3 * 24 * 60 * 60 * 1000L
            _state.value = _state.value.copy(trialBatas = trialEnd, maxTv = 2)
            StorageUtil.saveTrial(trialEnd)
            viewModelScope.launch { try { cloudRepo.saveTrialToCloud(key, trialEnd) } catch (e: Exception) { Log.e("MainVM", "saveTrialToCloud after daftarUser: ${e.message}") } }
        }

        // Sync user list ke Firestore agar License Generator melihat user baru
        viewModelScope.launch { try { syncToCloud() } catch (e: Exception) { Log.e("MainVM", "syncToCloud after daftarUser: ${e.message}") } }

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

    // ── Auto Update (via Firestore Cloud) ─────────────────
    fun refreshTv() {
        val list = StorageUtil.loadTvList()
        _state.value = _state.value.copy(tvList = list)
    }

    fun checkForUpdate() {
        viewModelScope.launch {
            _state.value = _state.value.copy(updateChecked = false, updateInfo = null)
            try {
                val remote = withContext(Dispatchers.IO) { cloudRepo.fetchLatestVersion() }
                if (remote != null) {
                    val (versionName, apkUrl, changelog) = remote
                    if (versionName > APP_VERSION && apkUrl.isNotBlank()) {
                        _state.value = _state.value.copy(
                            updateInfo = UpdateInfo(versionName, apkUrl, changelog),
                            updateChecked = true,
                        )
                    } else {
                        _state.value = _state.value.copy(updateChecked = true)
                    }
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

    fun publishUpdate(versionName: String, apkUrl: String, changelog: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val ok = withContext(Dispatchers.IO) { cloudRepo.publishUpdate(versionName, apkUrl, changelog) }
                if (ok) {
                    addActivityLog("Publish update v$versionName")
                    onResult(true, "✅ Update v$versionName dipublikasikan!")
                } else {
                    onResult(false, "❌ Gagal publish (auth/network)")
                }
            } catch (e: Exception) {
                onResult(false, "❌ ${e.message}")
            }
        }
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
                            "timerStart" -> t.copy(timerStart = (v as? Number)?.toLong() ?: return@forEach)
                            "timerDurasi" -> t.copy(timerDurasi = (v as? Number)?.toLong() ?: return@forEach)
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
        addActivityLog("Transaksi ${tx.paket} Rp${tx.total} di ${tx.kota}")
        // Auto-sync to cloud after new transaction
        try {
            Log.i("MainVM", "auto-sync triggered after tambahTransaksi id=${tx.id}")
            syncToCloud()
        } catch (ex: Exception) { Log.i("MainVM", "auto-sync failed to start: ${ex.message}") }
    }

    fun bersihkanTransaksi() {
        _state.value = _state.value.copy(transaksiList = emptyList())
        StorageUtil.saveTransaksi(emptyList())
        addActivityLog("Bersihkan semua transaksi")
        // Auto-sync to cloud after clearing transactions
        try { syncToCloud() } catch (e: Exception) { Log.e("MainVM", "syncToCloud after bersihkanTransaksi: ${e.message}") }
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
        val DEVICE_TYPE = "android"
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
            // Multi-device check
            val activatedDevices = record.activatedDevices
            val maxActivations = record.maxActivations
            if (activatedDevices.isNotEmpty()) {
                // Check if this device type already registered
                val alreadyAndroid = activatedDevices.any { it.deviceType == DEVICE_TYPE }
                if (alreadyAndroid) {
                    onResult(false, "Kode ini sudah teraktivasi di perangkat Android lain.")
                    return@launch
                }
                if (activatedDevices.size >= maxActivations) {
                    onResult(false, "Kode ini sudah mencapai batas aktivasi ($maxActivations perangkat). Hubungi admin.")
                    return@launch
                }
            } else if (record.activatedAt > 0) {
                // Backward compat: old single-activation code
                // Allow it on Android since it was activated on Desktop (or vice versa)
                // We'll let it pass and record as multi-device
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
            val existingExp = _state.value.licenseStatus
            if (existingExp.status == "active" && existingExp.expiresAt.isNotEmpty()) {
                try {
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                    val existingDate = sdf.parse(existingExp.expiresAt)
                    if (existingDate != null && existingDate.after(java.util.Date())) {
                        cal.time = existingDate
                    }
                } catch (e: Exception) { Log.e("MainVM", "parse existing expiry in aktivasiLisensi: ${e.message}") }
            }
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

            // Activate with device type (multi-device support)
            cloudRepo.activateLicense(record.id, expires, deviceType = DEVICE_TYPE)

            // Write licenseStatus ke user doc untuk sync antar device
            val username = _state.value.currentUser
            if (username.isNotBlank()) {
                try {
                    val userLs = mapOf(
                        "status" to "active",
                        "pesan" to ls.pesan,
                        "expiresAt" to expires,
                        "maxTv" to maxTv,
                        "maxPc" to maxTv,
                        "cloud_restored" to true,
                    )
                    cloudRepo.ensureSignedIn()
                    cloudRepo.writeLicenseStatusToUserDoc(username, userLs)
                } catch (e: Exception) { Log.e("MainVM", "writeLicenseStatusToUserDoc in aktivasiLisensi: ${e.message}") }
            }

            try { syncToCloud() } catch (e: Exception) { Log.e("MainVM", "syncToCloud after aktivasiLisensi: ${e.message}") }
            addActivityLog("Lisensi $paket diaktivasi (s.d. $expires)")
            sendPushNotification("✅ Lisensi Aktif!", "Paket $paket aktif hingga $expires")
            onResult(true, ls.pesan)
        }
    }

    private fun generateKode(paket: String): String {
        val paketChar = when (paket) { "BULANAN" -> "B"; "3BULAN" -> "T"; "TAHUNAN" -> "S"; "LIFETIME" -> "L"; else -> "X" }
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = (1..13).map { chars.random() }.joinToString("")
        return "RR$paketChar$random"
    }

    fun generateLicenseKode(paket: String, username: String, email: String = "", onDone: (String) -> Unit) {
        val kode = generateKode(paket)
        val nonce = java.util.UUID.randomUUID().toString().take(8)
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_YEAR, when (paket) {
            "BULANAN" -> 30; "3BULAN" -> 90; "TAHUNAN" -> 360; "LIFETIME" -> 99999; else -> 30
        })
        val expiry = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(cal.time)
        val payload = """{"p":"$paket","u":"$username","e":"$expiry","n":"$nonce","m":"$email"}"""
        val signature = ECDSAUtils.sign(payload) ?: run {
            onDone("GAGAL: Private key tidak dikonfigurasi. Gunakan License Generator app.")
            return
        }
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
            } catch (e: Exception) { Log.e("MainVM", "save license doc to Firestore in generateLicenseKode: ${e.message}") }
            onDone(kode)
        }
    }

    // ── Invoices ────────────────────────────────────────────
    private fun generateInvoiceId(): String {
        val sdf = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
        val datePart = sdf.format(java.util.Date())
        val random = (1..4).map { "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".random() }.joinToString("")
        return "INV-$datePart-$random"
    }

    fun buatInvoice(paket: String, harga: Int): Invoice {
        val id = generateInvoiceId()
        val username = _state.value.currentUser
        val email = _state.value.users[username]?.email ?: ""
        val inv = Invoice(
            id = id,
            username = username,
            email = email,
            paket = paket,
            harga = harga,
            status = "PENDING",
            dibuat = System.currentTimeMillis(),
        )
        val newList = _state.value.invoices + inv
        _state.value = _state.value.copy(invoices = newList)
        _state.value = _state.value.copy(pendingInvoiceCount = newList.count { it.status == "WAITING_CONFIRMATION" })
        StorageUtil.saveInvoices(newList)
        viewModelScope.launch { try { cloudRepo.saveInvoiceToCloud(inv) } catch (e: Exception) { Log.e("MainVM", "saveInvoiceToCloud in buatInvoice: ${e.message}") } }
        return inv
    }

    fun uploadBukti(invoiceId: String, base64: String) {
        val newList = _state.value.invoices.map {
            if (it.id == invoiceId) it.copy(status = "WAITING_CONFIRMATION", buktiBase64 = base64) else it
        }
        _state.value = _state.value.copy(invoices = newList)
        _state.value = _state.value.copy(pendingInvoiceCount = newList.count { it.status == "WAITING_CONFIRMATION" })
        StorageUtil.saveInvoices(newList)
        val updated = newList.firstOrNull { it.id == invoiceId }
        if (updated != null) {
            viewModelScope.launch { try { cloudRepo.saveInvoiceToCloud(updated) } catch (e: Exception) { Log.e("MainVM", "saveInvoiceToCloud in uploadBukti: ${e.message}") } }
        }
    }

    fun konfirmasiPembayaran(invoiceId: String) {
        val inv = _state.value.invoices.firstOrNull { it.id == invoiceId } ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isBusy = true, statusMessage = "Memproses pembayaran...")
            generateLicenseKode(inv.paket, inv.username, inv.email) { kode ->
                viewModelScope.launch {
                    val tvOverride = getAddTvOverride(inv.paket)
                    val maxTv = tvOverride ?: when (inv.paket) {
                        "1 Bulan", "BULANAN" -> 5; "3 Bulan", "3BULAN" -> 8
                        "1 Tahun", "TAHUNAN" -> 15; "LIFETIME" -> 0; else -> 2
                    }
                    val cal = java.util.Calendar.getInstance()
                    val existingExp = _state.value.licenseStatus
                    if (existingExp.status == "active" && existingExp.expiresAt.isNotEmpty()) {
                        try {
                            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                            val existingDate = sdf.parse(existingExp.expiresAt)
                        if (existingDate != null && existingDate.after(java.util.Date())) {
                            cal.time = existingDate
                        }
                    } catch (e: Exception) { Log.e("MainVM", "parse existing expiry in konfirmasiPembayaran: ${e.message}") }
                    }
                    cal.add(java.util.Calendar.DAY_OF_YEAR, when (inv.paket) {
                        "1 Bulan", "BULANAN" -> 30; "3 Bulan", "3BULAN" -> 90
                        "1 Tahun", "TAHUNAN" -> 360; "LIFETIME" -> 99999; else -> 30
                    })
                    val expiry = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(cal.time)
                    val ls = LicenseStatus(
                        status = "active",
                        pesan = "✅ Lisensi ${inv.paket} aktif hingga $expiry",
                        expiresAt = expiry, maxTv = maxTv,
                    )
                    _state.value = _state.value.copy(licenseStatus = ls, trialBatas = 0L, maxTv = maxTv)
                    StorageUtil.saveLicense(ls)
                    try {
                        val record = cloudRepo.findLicenseByCode(kode)
                        if (record != null) { cloudRepo.activateLicense(record.id, expiry) }
                    } catch (e: Exception) { Log.e("MainVM", "activateLicense in konfirmasiPembayaran: ${e.message}") }
                    val confirmedInv = _state.value.invoices.firstOrNull { it.id == invoiceId }?.copy(
                        status = "CONFIRMED", dibayar = System.currentTimeMillis(),
                        confirmedBy = _state.value.currentUser, kodeLisensi = kode,
                    )
                    val newInvoices = _state.value.invoices.map {
                        if (it.id == invoiceId) confirmedInv!! else it
                    }
                    _state.value = _state.value.copy(invoices = newInvoices, pendingInvoiceCount = newInvoices.count { it.status == "WAITING_CONFIRMATION" })
                    StorageUtil.saveInvoices(newInvoices)
                    if (confirmedInv != null) { try { cloudRepo.saveInvoiceToCloud(confirmedInv) } catch (e: Exception) { Log.e("MainVM", "saveInvoiceToCloud in konfirmasiPembayaran: ${e.message}") } }
                    addActivityLog("Pembayaran ${inv.paket} dikonfirmasi (kode: $kode)")
                    sendPushNotification("💰 Pembayaran Dikonfirmasi", "${inv.paket} - Kode: $kode")
                    try { syncToCloud() } catch (e: Exception) { Log.e("MainVM", "syncToCloud after konfirmasiPembayaran: ${e.message}") }
                    _state.value = _state.value.copy(isBusy = false, statusMessage = "Pembayaran dikonfirmasi!")
                }
            }
        }
    }

    fun getPendingInvoices(): List<Invoice> = _state.value.invoices.filter { it.status == "WAITING_CONFIRMATION" || it.status == "PENDING" }.sortedByDescending { it.dibuat }

    // ── License Management ─────────────────────────────────
    fun getActiveLicenses(callback: (List<LicenseRecord>) -> Unit) {
        val username = _state.value.currentUser
        viewModelScope.launch {
            try {
                val licenses = cloudRepo.getActiveLicensesForUser(username)
                callback(licenses)
            } catch (e: Exception) {
                Log.e("MainVM", "getActiveLicenses failed: ${e.message}")
                callback(emptyList())
            }
        }
    }

    fun revokeLicense(docId: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = cloudRepo.revokeLicense(docId)
            if (ok) {
                addActivityLog("Lisensi direvoke (docId: $docId)")
                sendPushNotification("⚠️ Lisensi Dicabut", "Lisensi $docId telah dicabut")
            }
            onResult(ok)
        }
    }

    fun getMyInvoices(): List<Invoice> = _state.value.invoices.filter { it.username == _state.value.currentUser }.sortedByDescending { it.dibuat }

    fun gantiPassword(username: String, newPassword: String): Boolean {
        if (newPassword.length < 6) return false
        val users = _state.value.users.toMutableMap()
        val existing = users[username] ?: return false
        users[username] = existing.copy(passwordHash = sha256(newPassword))
        _state.value = _state.value.copy(users = users)
        StorageUtil.saveUsers(users)
        return true
    }

    fun setTvPassword(password: String) {
        val hash = sha256(password)
        StorageUtil.saveTvPassword(hash)
        _state.value = _state.value.copy(tvPasswordHash = hash)
    }

    fun verifyTvPassword(password: String): Boolean {
        val hash = _state.value.tvPasswordHash
        if (hash.isEmpty()) return true
        return sha256(password) == hash
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

    override fun onCleared() {
        connectivityObserver.stop()
        stopLicenseListener()
        stopPromoListener()
        super.onCleared()
    }
}
