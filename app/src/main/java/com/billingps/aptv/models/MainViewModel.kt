package com.billingps.aptv.models

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
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
import com.billingps.aptv.cloud.SupabaseRepository
import com.billingps.aptv.tv.TvHubManager
import com.billingps.aptv.tv.TvHubProtocol
import com.billingps.aptv.tv.TvConnection
import com.billingps.aptv.tv.TvMediaStore
import com.billingps.aptv.tv.TvMediaUploader
import com.billingps.aptv.tv.TvRemotePower
import com.billingps.aptv.ui.theme.BackgroundOption
import com.billingps.aptv.ui.theme.ThemeOption
import com.billingps.aptv.utils.VersionUtil
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import java.text.SimpleDateFormat
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
    val jenisPsList: List<String> = listOf("PS3", "PS4", "PS5"),
    val paketMain: Map<String, Map<String, Int>> = defaultPaketMain(listOf("PS3", "PS4", "PS5")),
    val paketDurasi: Map<String, Int> = defaultPaketDurasi(),
    val menuMakanan: Map<String, Int> = emptyMap(),
    val menuMinuman: Map<String, Int> = emptyMap(),
    val stokMakanan: Map<String, Int> = emptyMap(),
    val stokMinuman: Map<String, Int> = emptyMap(),
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
    val pendingGoogleRegistration: Boolean = false,
    val pendingGoogleEmail: String = "",
    val pendingGoogleDisplayName: String = "",
    val pendingGoogleIdToken: String = "",
    val pendingVerifyUser: String = "",
    val pendingVerifyCode: String = "",
    val pendingResetCode: String = "",
    val needsSmtpSetup: Boolean = false,
    val updateInfo: UpdateInfo? = null,
    val updateChecked: Boolean = false,
    val downloadProgress: Int = -1, // -1 = idle, 0-100 = downloading
    val appVersionName: String = "",
    val invoices: List<Invoice> = emptyList(),
    val pendingInvoiceCount: Int = 0,
    val tvPasswordHash: String = "",
    val themeOption: ThemeOption = ThemeOption.GAMING_DARK,
    val backgroundOption: BackgroundOption = BackgroundOption.AMOLED_BLACK,
    val promo: PromoSettings = PromoSettings(),
    val loginMethod: String = "password", // "password" or "google"
    val printerAddress: String = "",
    val printerName: String = "",
    val printerConnected: Boolean = false,
    val notifications: List<AppNotification> = emptyList(),
    val unreadNotifCount: Int = 0,
    val showPromoPopup: Boolean = false,
    val promoPopupTitle: String = "",
    val promoPopupBody: String = "",
    val promoFetched: Boolean = false,
    val promoEndTimeMs: Long = 0L,
    val showNotifPopup: Boolean = false,
    val notifPopupTitle: String = "",
    val notifPopupBody: String = "",
    val habisDismissedIds: Set<String> = emptySet(),
    val clientStatus: Map<String, ClientStatus> = emptyMap(),
    val deviceConflict: DeviceConflict? = null,
    val deviceSessionId: String = "",
    val quotaPopupVisible: Boolean = false,
    val quotaPopupTitle: String = "",
    val quotaPopupBody: String = "",
    val sessionNotice: String = "",
)

fun defaultPaketMain(groups: List<String> = listOf("PS3", "PS4", "PS5")): Map<String, Map<String, Int>> {
    val defaultPS = mapOf(
        "15 Menit" to 5000, "30 Menit" to 10000, "1 Jam" to 15000,
        "2 Jam" to 25000, "3 Jam" to 35000,
    )
    return groups.associateWith { defaultPS }
}

fun defaultPaketDurasi(): Map<String, Int> = mapOf(
    "15 Menit" to 15, "30 Menit" to 30, "1 Jam" to 60,
    "2 Jam" to 120, "3 Jam" to 180, "Main Bebas" to 0,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val cloudRepo = CloudRepository(application)
    private val supabaseRepo = SupabaseRepository()
    private val firebaseAuth = FirebaseAuth.getInstance()
    private var userDocListener: ListenerRegistration? = null
    private var invoicesListener: ListenerRegistration? = null
    private var notifListener: ListenerRegistration? = null
    private var deviceSessionListener: ListenerRegistration? = null
    private var pendingDeviceUser: UserData? = null
    private var pendingDeviceLoginMethod: String = "password"
    private val connectivityObserver = ConnectivityObserver(application)
    private val currentVersion: String by lazy {
        try {
            getApplication<Application>().packageManager.getPackageInfo(getApplication<Application>().packageName, 0).versionName ?: "0.0.0"
        } catch (e: Exception) {
            "0.0.0"
        }
    }

    private val _state = MutableStateFlow(AppUiState())
    private val deviceAuthListener = FirebaseAuth.AuthStateListener { auth ->
        if (auth.currentUser != null) adoptDeviceSessionIfNeeded()
    }
    private var deviceClaimInFlight = false
    private val warningSentSessions = mutableMapOf<String, Long>()
    val state: StateFlow<AppUiState> = _state.asStateFlow()
    val tvConnections: StateFlow<Map<String, TvConnection>> = TvHubManager.connections
    private var timerReceiver: BroadcastReceiver? = null
    private var clientStatusListeners = mutableMapOf<String, ListenerRegistration>()
    private val autoCutNextAt = mutableMapOf<String, Long>()
    private val autoCutRepeat = mutableSetOf<String>()
    private val pendingSleepJobs = mutableMapOf<String, Job>()
    private val sessionWakeJobs = mutableMapOf<String, Job>()

    init {
        StorageUtil.init(application)
        ECDSAUtils.init(application)
        FcmSender.init(application)
        firebaseAuth.addAuthStateListener(deviceAuthListener)
        _state.value = _state.value.copy(appVersionName = currentVersion)
        loadAll()
        adoptDeviceSessionIfNeeded()
        repairLegacyTransactions()
        registerTimerReceiver()
        refreshTimerService()
        scheduleExpiredPaidSleeps()
        // Sync NTP time on startup
        viewModelScope.launch { syncNtpTime() }
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
        startAutoCutMonitor()
        viewModelScope.launch {
            tvConnections.collect { syncTvIpsFromConnections(it) }
        }
        if (_state.value.isLoggedIn) {
            viewModelScope.launch { syncToCloud() }
        }
    }

    private fun syncTvIpsFromConnections(connections: Map<String, TvConnection>) {
        if (connections.isEmpty()) return
        val current = _state.value.tvList
        val changedIds = mutableListOf<String>()
        val updated = current.map { tv ->
            val conn = connections.values.firstOrNull {
                TvHubProtocol.normalizeId(it.mejaId) ==
                    TvHubProtocol.normalizeId(TvHubManager.mejaIdOf(tv))
            }
            val ip = conn?.ip?.trim().orEmpty()
            if (ip.isNotBlank() && ip != tv.ip && isValidIpv4(ip)) {
                changedIds += tv.id
                tv.copy(ip = ip)
            } else {
                tv
            }
        }
        if (changedIds.isEmpty()) return
        _state.value = _state.value.copy(tvList = updated)
        StorageUtil.saveTvList(updated)
        refreshHubConfig()
        Log.i("MainVM", "TV IP synced from client: $changedIds")
    }

    private fun isValidIpv4(value: String): Boolean {
        val parts = value.split('.')
        return parts.size == 4 && parts.all { part ->
            part.isNotEmpty() && part.all { it.isDigit() } && part.toIntOrNull() in 0..255
        }
    }

    private fun fireWarningIfAlmostHabis(tvList: List<TvData>) {
        val username = _state.value.currentUser
        if (username.isEmpty()) return
        tvList.forEach { tv ->
            val isAlmostHabis = tv.timerActive && !tv.bebas && tv.sisaDetik in 1..300
            if (isAlmostHabis && warningSentSessions[tv.id] != tv.timerStart) {
                warningSentSessions[tv.id] = tv.timerStart
                val menit = tv.sisaDetik / 60
                val detik = tv.sisaDetik % 60
                Log.i("MainVM", "Fire 5-min warning for TV ${tv.nama} (sisa ${tv.sisaDetik}s)")
                try {
                    cloudRepo.sendWarning(username, tv.id, "Sisa ${menit}m ${detik}d!")
                } catch (e: Exception) {
                    Log.e("MainVM", "sendWarning failed: ${e.message}")
                }
            }
        }
    }

    private fun startAutoCutMonitor() {
        viewModelScope.launch {
            while (isActive) {
                try {
                    checkAutoCut(System.currentTimeMillis())
                } catch (e: Exception) {
                    Log.w("MainVM", "checkAutoCut failed: ${e.message}")
                }
                delay(1000L)
            }
        }
    }

    private fun checkAutoCut(now: Long) {
        val tvs = _state.value.tvList
        val ids = tvs.map { it.id }.toSet()
        autoCutNextAt.keys.retainAll(ids)
        autoCutRepeat.retainAll(ids)

        tvs.forEach { tv ->
            val hasSession = tv.timerActive || tv.bebas || tv.paketAktif.isNotBlank()
            if (hasSession) {
                autoCutNextAt.remove(tv.id)
                autoCutRepeat.remove(tv.id)
                return@forEach
            }

            val connection = TvHubManager.connectionFor(tv)
            if (connection?.online != true || connection.screenOn != true) {
                autoCutNextAt.remove(tv.id)
                return@forEach
            }

            val nextAt = autoCutNextAt[tv.id]
            if (nextAt == null) {
                val delayMs = if (tv.id in autoCutRepeat) 5 * 60_000L else 10 * 60_000L
                autoCutNextAt[tv.id] = now + delayMs
                Log.i("MainVM", "Auto-cut armed for ${tv.nama} in ${delayMs / 60_000} min (no session)")
                return@forEach
            }

            if (now >= nextAt) {
                autoCutRepeat.add(tv.id)
                autoCutNextAt[tv.id] = now + 5 * 60_000L
                if (tv.certPem.isBlank() || tv.keyPem.isBlank()) {
                    Log.i("MainVM", "Auto-cut ${tv.nama}: skipped, TV belum dipairing")
                } else {
                    viewModelScope.launch(Dispatchers.IO) {
                        sleepTv(tv, "Auto-cut")
                    }
                }
            }
        }
    }

    private fun registerTimerReceiver() {
        timerReceiver?.let { getApplication<Application>().unregisterReceiver(it) }
        timerReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    com.billingps.aptv.TimerService.ACTION_TICK_BATCH -> {
                        val tickDataStr = intent.getStringExtra(com.billingps.aptv.TimerService.EXTRA_TICK_DATA) ?: return
                        val expiredIdsStr = intent.getStringExtra(com.billingps.aptv.TimerService.EXTRA_EXPIRED_IDS) ?: "[]"
                        try {
                            val tickData = org.json.JSONObject(tickDataStr)
                            val expiredArr = org.json.JSONArray(expiredIdsStr)
                            val expiredSet = mutableSetOf<String>()
                            for (i in 0 until expiredArr.length()) {
                                expiredSet.add(expiredArr.getString(i))
                            }
                            var currentList = _state.value.tvList
                            var changed = false
                            for (i in currentList.indices) {
                                val tv = currentList[i]
                                if (tickData.has(tv.id)) {
                                    val data = tickData.getJSONObject(tv.id)
                                    val sisaDetik = data.optLong("sisaDetik", -1L)
                                    val paketAktif = data.optString("paketAktif", "")
                                    if (tv.id in expiredSet) {
                                        Log.i("MainVM", "expiry tick ${tv.nama}: sudahBayar=${tv.sudahBayar} paket='${tv.paketAktif}' tickPaket='$paketAktif'")
                                        val finalPaket = if (tv.sudahBayar) "" else paketAktif
                                        val expiredTv = tv.copy(
                                            timerActive = false, sisaDetik = sisaDetik.coerceAtLeast(0),
                                            paketAktif = finalPaket,
                                        )
                                        TvHubManager.sendLockScreen(expiredTv)
                                        if (expiredTv.sudahBayar) {
                                            Log.i("MainVM", "expiry ${tv.nama}: schedule auto-sleep in 30s")
                                            scheduleSleepAfter(expiredTv, 30_000L)
                                        }
                                        currentList = currentList.toMutableList().also {
                                            it[i] = expiredTv
                                        }
                                        changed = true
                                    } else if (sisaDetik >= 0 && sisaDetik != tv.sisaDetik) {
                                        currentList = currentList.toMutableList().also {
                                            it[i] = tv.copy(sisaDetik = sisaDetik)
                                        }
                                        changed = true
                                    }
                                }
                            }
                            if (changed) {
                                _state.value = _state.value.copy(tvList = currentList)
                                if (expiredSet.isNotEmpty()) StorageUtil.saveTvList(currentList)
                                refreshHubConfig()
                                currentList.filter { it.timerActive || it.bebas }.forEach { tv ->
                                    TvHubManager.sendSyncTimer(tv)
                                }
                            }
                            currentList.filter { it.bebas }.forEach { tv ->
                                TvHubManager.sendSyncTimer(tv)
                            }
                            fireWarningIfAlmostHabis(currentList)
                        } catch (e: Exception) {
                            Log.w("MainVM", "Failed to parse batch tick: ${e.message}")
                        }
                    }
                }
            }
        }
        try {
            val filter = IntentFilter().apply {
                addAction(com.billingps.aptv.TimerService.ACTION_TICK_BATCH)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getApplication<Application>().registerReceiver(timerReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                getApplication<Application>().registerReceiver(timerReceiver, filter)
            }
        } catch (e: Exception) {
            Log.e("MainVM", "registerTimerReceiver failed: ${e.message}")
        }
    }

    fun startTimerService() {
        val tvList = _state.value.tvList.filter { it.timerActive || it.bebas }
        if (tvList.isEmpty()) return
        val intent = Intent(getApplication(), com.billingps.aptv.TimerService::class.java).apply {
            action = com.billingps.aptv.TimerService.ACTION_START
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getApplication<Application>().startForegroundService(intent)
            } else {
                getApplication<Application>().startService(intent)
            }
        } catch (e: Exception) {
            Log.e("MainVM", "startTimerService failed: ${e.message}")
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
        val bgName = StorageUtil.loadBackgroundOption()
        val savedBg = try { BackgroundOption.valueOf(bgName) } catch (_: Exception) { BackgroundOption.AMOLED_BLACK }
        val loadedGroups = StorageUtil.loadJenisPsList()
        val groups = if (loadedGroups.isNotEmpty()) loadedGroups else listOf("PS3", "PS4", "PS5")
        _state.value = _state.value.copy(
            isLoggedIn = cu.isNotEmpty(),
            currentUser = cu,
            currentRole = cr,
            themeOption = savedTheme,
            backgroundOption = savedBg,
            users = users,
            tvList = StorageUtil.loadTvList(),
            transaksiList = StorageUtil.loadTransaksi(),
            jenisPsList = groups,
            paketMain = StorageUtil.loadPaketMain().ifEmpty { defaultPaketMain(groups) },
            paketDurasi = StorageUtil.loadPaketDurasi().ifEmpty { defaultPaketDurasi() },
            menuMakanan = StorageUtil.loadMenuMakanan(),
            menuMinuman = StorageUtil.loadMenuMinuman(),
            stokMakanan = StorageUtil.loadStokMakanan(),
            stokMinuman = StorageUtil.loadStokMinuman(),
            licenseStatus = lic,
            smtp = StorageUtil.loadSmtp(),
            kodeGenerasiList = StorageUtil.loadKodeGenerasiList(),
            trialBatas = trial,
            maxTv = lic.maxTv,
            invoices = StorageUtil.loadInvoices(),
            tvPasswordHash = StorageUtil.loadTvPassword(),
            promo = StorageUtil.loadPromo(),
            deviceSessionId = StorageUtil.loadDeviceSessionId(),
        )
        val savedNotifications = StorageUtil.loadNotifications()
        _state.value = _state.value.copy(
            pendingInvoiceCount = _state.value.invoices.count { it.status == "WAITING_CONFIRMATION" },
            loginMethod = StorageUtil.loadLoginMethod(),
            printerAddress = StorageUtil.getSecurePreference("printerAddress") ?: "",
            printerName = StorageUtil.getSecurePreference("printerName") ?: "",
            notifications = savedNotifications,
            unreadNotifCount = savedNotifications.count { !it.read },
        )
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
            startNotifListener(cu)
            startClientStatusListeners(cu)
            // Force fetch latest promo from Firestore
            viewModelScope.launch {
                val fetched = cloudRepo.fetchPromoSettings()
                if (fetched != null) {
                    _state.value = _state.value.copy(promo = fetched, promoFetched = true)
                    StorageUtil.savePromo(fetched)
                    checkPromoPopup(fetched)
                    checkWelcomePromo(fetched)
                }
            }
        }
        refreshHubConfig()
    }

    private fun currentRentalName(): String {
        val s = _state.value
        val user = s.users[s.currentUser]
        return user?.namaRental?.takeIf { it.isNotBlank() } ?: "RR Billing Pro"
    }

    fun isClientFeatureEnabled(): Boolean {
        val s = _state.value
        val licensed = s.licenseStatus.status == "active"
        val trialActive = s.trialBatas > System.currentTimeMillis()
        return (licensed || trialActive) && s.licenseStatus.clientEnabled
    }

    private fun refreshHubConfig() {
        val clientEnabled = isClientFeatureEnabled()
        com.billingps.aptv.utils.ApkServer.clientAllowed = clientEnabled
        TvHubManager.updateConfig(_state.value.tvList, currentRentalName(), clientEnabled)
    }

    private fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun sha256Salted(text: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest((salt + text).toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun generateSalt(): String {
        val random = SecureRandom()
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // PBKDF2 with HMAC-SHA256 (built-in Java crypto, no extra dependency)
    private val PBKDF2_ITERATIONS = 60000

    private fun pbkdf2Hash(password: String, salt: String): String {
        val spec = PBEKeySpec(password.toCharArray(), salt.toByteArray(), PBKDF2_ITERATIONS, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        return "PBKDF2:$PBKDF2_ITERATIONS:$salt:" + hash.joinToString("") { "%02x".format(it) }
    }

    private fun verifyPbkdf2(password: String, stored: String): Boolean {
        val parts = stored.split(":")
        if (parts.size < 4 || parts[0] != "PBKDF2") return false
        val iterations = parts[1].toIntOrNull() ?: return false
        val salt = parts[2]
        val expectedHash = parts.drop(3).joinToString(":") // remaining parts (in case salt has colons)
        val spec = PBEKeySpec(password.toCharArray(), salt.toByteArray(), iterations, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val computed = factory.generateSecret(spec).encoded
        return computed.joinToString("") { "%02x".format(it) } == expectedHash
    }

    private fun verifyPassword(password: String, user: UserData): Boolean {
        // PBKDF2 (newest)
        if (user.passwordHash.startsWith("PBKDF2:")) {
            return verifyPbkdf2(password, user.passwordHash)
        }
        // SHA-256 with salt (legacy)
        if (user.salt.isNotEmpty()) {
            return sha256Salted(password, user.salt) == user.passwordHash
        }
        // SHA-256 without salt (oldest)
        return sha256(password) == user.passwordHash
    }

    // Upgrade password hash to PBKDF2 on successful login
    private fun upgradePasswordHash(user: UserData, password: String): UserData {
        if (user.passwordHash.startsWith("PBKDF2:")) return user // already upgraded
        val salt = if (user.salt.isNotEmpty()) user.salt else generateSalt()
        val newHash = pbkdf2Hash(password, salt)
        return user.copy(passwordHash = newHash, salt = salt)
    }

    // ── NTP Time Protection ───────────────────────────────
    private var ntpTimeOffset: Long = 0L // cached offset between NTP and device clock

    private suspend fun fetchNtpTime(): Long? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://worldtimeapi.org/api/ip")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            val resp = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val json = org.json.JSONObject(resp)
            val utcOffset = json.optString("utc_offset", "+00:00")
            val datetime = json.optString("datetime", "")
            if (datetime.isNotEmpty()) {
                // Parse ISO 8601 datetime
                val cleanDt = datetime.substringBefore(".").substringBefore("+").substringBefore("Z")
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                val ntpDate = sdf.parse(cleanDt) ?: return@withContext null
                ntpDate.time
            } else null
        } catch (e: Exception) {
            Log.w("MainVM", "NTP fetch failed: ${e.message}")
            null
        }
    }

    private fun isDeviceClockTrusted(): Boolean {
        val ntpTime = StorageUtil.loadNtpTime()
        if (ntpTime <= 0) return true // No NTP data yet, assume trusted
        val deviceTime = System.currentTimeMillis()
        val diff = Math.abs(deviceTime - ntpTime)
        // Allow max 5 minutes difference
        return diff < 300_000L
    }

    private suspend fun syncNtpTime() {
        val ntp = fetchNtpTime()
        if (ntp != null) {
            StorageUtil.saveNtpTime(ntp)
            ntpTimeOffset = ntp - System.currentTimeMillis()
            Log.i("MainVM", "NTP time synced: $ntp, offset=$ntpTimeOffset ms")
        }
    }

    private fun checkClockIntegrity(): String? {
        return if (!isDeviceClockTrusted()) {
            "⚠️ Deteksi manipulasi jam! Waktu perangkat tidak sinkron dengan server. Fitur lisensi dinonaktifkan."
        } else null
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
        val salt = if (user.salt.isNotEmpty()) user.salt else generateSalt()
        val hash = pbkdf2Hash(newPassword, salt)
        users[user.username] = user.copy(passwordHash = hash, salt = salt, resetCode = "", resetCodeExpiry = 0)
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
                        val salt = generateSalt()
                        val restoredUser = UserData(
                            username = remoteUsername, passwordHash = pbkdf2Hash(password, salt), salt = salt, role = "admin",
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
                val claim = claimDeviceForLogin(sessionUser, "password")
                if (!claim.success) return claim
                StorageUtil.saveCurrentSession(sessionUser.username, sessionUser.role)
                val smtp = _state.value.smtp
                _state.value = _state.value.copy(
                    isLoggedIn = true,
                    currentUser = sessionUser.username,
                    currentRole = sessionUser.role,
                    needsSmtpSetup = smtp.user.isBlank() || smtp.pass.isBlank(),
                )
                restoreLocalDataAfterLogin()
                restoreLicenseAndTrial(sessionUser.username)
                return AuthResult(true, "")
            }
            // Firebase gagal — fallback ke cek password lokal (degraded security)
            val fallbackUser = matchingEmailUser ?: localUser
                if (fallbackUser != null) {
                    if (verifyPassword(password, fallbackUser)) {
                        Log.w("MainVM", "⚠️ Firebase auth FAILED but local password MATCHED for $input — degraded security mode")
                        // Upgrade password hash to PBKDF2
                        val upgraded = upgradePasswordHash(fallbackUser, password)
                        if (upgraded.passwordHash != fallbackUser.passwordHash) {
                            val updatedUsers = _state.value.users.toMutableMap()
                            updatedUsers[fallbackUser.username] = upgraded
                            _state.value = _state.value.copy(users = updatedUsers)
                            StorageUtil.saveUsers(updatedUsers)
                        }
                        val claim = claimDeviceForLogin(fallbackUser, "password")
                        if (!claim.success) return claim
                        StorageUtil.saveCurrentSession(fallbackUser.username, fallbackUser.role)
                        val smtp = _state.value.smtp
                        _state.value = _state.value.copy(
                            isLoggedIn = true,
                            currentUser = fallbackUser.username,
                            currentRole = fallbackUser.role,
                            needsSmtpSetup = smtp.user.isBlank() || smtp.pass.isBlank(),
                        )
                        restoreLocalDataAfterLogin()
                        restoreLicenseAndTrial(fallbackUser.username)
                        return AuthResult(true, "")
                    }
                }
            return firebaseResult
        }

        if (localUser == null) return AuthResult(false, "Username/Password salah")
        if (!verifyPassword(password, localUser)) return AuthResult(false, "Username/Password salah")
        // Upgrade password hash to PBKDF2
        val upgraded = upgradePasswordHash(localUser, password)
        if (upgraded.passwordHash != localUser.passwordHash) {
            val updatedUsers = _state.value.users.toMutableMap()
            updatedUsers[localUser.username] = upgraded
            _state.value = _state.value.copy(users = updatedUsers)
            StorageUtil.saveUsers(updatedUsers)
        }
        val claim = claimDeviceForLogin(localUser, "password")
        if (!claim.success) return claim
        addActivityLog("Login")
        StorageUtil.saveCurrentSession(localUser.username, localUser.role)
        val smtp = _state.value.smtp
        _state.value = _state.value.copy(
            isLoggedIn = true,
            currentUser = localUser.username,
            currentRole = localUser.role,
            needsSmtpSetup = smtp.user.isBlank() || smtp.pass.isBlank(),
        )
        restoreLocalDataAfterLogin()
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

    private suspend fun claimDeviceForLogin(user: UserData, method: String): AuthResult {
        val result = withTimeoutOrNull(12_000) {
            withContext(Dispatchers.IO) { cloudRepo.claimDevice(user.username, force = false) }
        } ?: DeviceClaimResult(error = "Verifikasi perangkat timeout. Periksa internet.")
        if (result.success) {
            pendingDeviceUser = null
            StorageUtil.saveDeviceSessionId(result.sessionId)
            _state.value = _state.value.copy(deviceSessionId = result.sessionId, deviceConflict = null)
            startDeviceSessionListener(user.username, result.sessionId)
            return AuthResult(true, "")
        }
        pendingDeviceUser = user
        pendingDeviceLoginMethod = method
        _state.value = _state.value.copy(
            deviceConflict = result.conflict,
            deviceSessionId = "",
            statusMessage = result.error.ifBlank { "Akun sedang digunakan di perangkat lain" },
        )
        return AuthResult(false, result.error.ifBlank { "Akun sedang digunakan di perangkat lain" })
    }

    fun takeoverDeviceLogin() {
        val user = pendingDeviceUser ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isBusy = true, statusMessage = "Mengambil alih sesi...")
            val result = withTimeoutOrNull(12_000) {
                withContext(Dispatchers.IO) { cloudRepo.claimDevice(user.username, force = true) }
            } ?: DeviceClaimResult(error = "Verifikasi perangkat timeout. Periksa internet.")
            if (!result.success) {
                _state.value = _state.value.copy(isBusy = false, statusMessage = result.error)
                return@launch
            }
            pendingDeviceUser = null
            StorageUtil.saveCurrentSession(user.username, user.role)
            StorageUtil.saveDeviceSessionId(result.sessionId)
            _state.value = _state.value.copy(
                isLoggedIn = true,
                currentUser = user.username,
                currentRole = user.role,
                loginMethod = pendingDeviceLoginMethod,
                deviceConflict = null,
                deviceSessionId = result.sessionId,
                isBusy = false,
                statusMessage = "",
            )
            startDeviceSessionListener(user.username, result.sessionId)
            addActivityLog("Login ambil alih perangkat")
            restoreLocalDataAfterLogin()
            restoreLicenseAndTrial(user.username)
        }
    }

    fun dismissDeviceConflict() {
        pendingDeviceUser = null
        _state.value = _state.value.copy(deviceConflict = null, statusMessage = "")
    }

    fun dismissSessionNotice() {
        _state.value = _state.value.copy(sessionNotice = "")
    }

    private fun adoptDeviceSessionIfNeeded() {
        val snapshot = _state.value
        if (!snapshot.isLoggedIn || snapshot.currentUser.isBlank()) return
        val savedSessionId = StorageUtil.loadDeviceSessionId()
        if (savedSessionId.isNotBlank()) {
            if (deviceSessionListener == null) startDeviceSessionListener(snapshot.currentUser, savedSessionId)
            return
        }
        if (deviceClaimInFlight || !cloudRepo.isFullyAuthenticated()) return
        deviceClaimInFlight = true
        viewModelScope.launch {
            val result = cloudRepo.claimDevice(snapshot.currentUser, force = false)
            deviceClaimInFlight = false
            when {
                result.success -> {
                    StorageUtil.saveDeviceSessionId(result.sessionId)
                    _state.value = _state.value.copy(deviceSessionId = result.sessionId, deviceConflict = null)
                    startDeviceSessionListener(snapshot.currentUser, result.sessionId)
                }
                result.conflict != null -> handleDeviceReplaced(result.conflict)
            }
        }
    }

    private fun startDeviceSessionListener(username: String, sessionId: String) {
        deviceSessionListener?.remove()
        deviceSessionListener = cloudRepo.listenDeviceSession(username, sessionId) { conflict ->
            Log.w("MainVM", "Device session replaced by ${conflict.activeDeviceName}")
            handleDeviceReplaced(conflict)
        }
        if (deviceSessionListener != null) Log.i("MainVM", "Device session listener active")
        else Log.w("MainVM", "Device session listener not started (auth not ready)")
    }

    private fun handleDeviceReplaced(conflict: DeviceConflict) {
        deviceSessionListener?.remove()
        deviceSessionListener = null
        pendingDeviceUser = null
        stopLicenseListener()
        stopPromoListener()
        stopNotifListener()
        stopClientStatusListeners()
        StorageUtil.clearSession()
        _state.value = _state.value.copy(
            isLoggedIn = false,
            currentUser = "",
            currentRole = "",
            deviceConflict = null,
            deviceSessionId = "",
            isBusy = false,
            statusMessage = "Sesi dihentikan: akun login di ${conflict.activeDeviceName}.",
            sessionNotice = "Akun ini login di ${conflict.activeDeviceName}. Sesi pada perangkat ini telah dihentikan.",
        )
        com.billingps.aptv.utils.ApkServer.clientAllowed = false
        TvHubManager.updateConfig(emptyList(), "RR Billing Pro", clientEnabled = false)
    }

    private fun stopDeviceSessionListener() {
        deviceSessionListener?.remove()
        deviceSessionListener = null
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
                    val claim = claimDeviceForLogin(existing, "google")
                    if (!claim.success) {
                        _state.value = _state.value.copy(isBusy = false)
                        return@launch
                    }
                    addActivityLog("Login via Google")
                    StorageUtil.saveCurrentSession(existing.username, existing.role)
                    StorageUtil.saveLoginMethod("google")
                    _state.value = _state.value.copy(
                        isLoggedIn = true, currentUser = existing.username, currentRole = existing.role,
                        needsSmtpSetup = _state.value.smtp.user.isEmpty(),
                        isBusy = false, statusMessage = "", loginMethod = "google",
                    )
                    restoreLocalDataAfterLogin()
                    restoreLicenseAndTrial(existing.username)
                } else {
                    val remoteUsername = withContext(Dispatchers.IO) { cloudRepo.findUsernameByEmail(verifiedEmail) }
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                    if (remoteUsername != null) {
                        val restoredUser = UserData(
                            username = remoteUsername, passwordHash = "", role = "admin",
                            email = verifiedEmail, dibuat = sdf.format(java.util.Date()), emailVerified = true,
                        )
                        val claim = claimDeviceForLogin(restoredUser, "google")
                        if (!claim.success) {
                            _state.value = _state.value.copy(isBusy = false)
                            return@launch
                        }
                        val updatedUsers = users + (remoteUsername to restoredUser)
                        StorageUtil.saveUsers(updatedUsers)
                        StorageUtil.saveCurrentSession(remoteUsername, "admin")
                        StorageUtil.saveLoginMethod("google")
                        _state.value = _state.value.copy(
                            users = updatedUsers, isLoggedIn = true, currentUser = remoteUsername, currentRole = "admin",
                            isBusy = false, statusMessage = "", loginMethod = "google",
                        )
                        restoreLocalDataAfterLogin()
                        restoreLicenseAndTrial(remoteUsername)
                        // Restore transaksi & TV list from cloud so old data doesn't get overwritten with empty
                        launch {
                            val cloudTxs = withContext(Dispatchers.IO) { cloudRepo.fetchTransaksiForUser(remoteUsername) }
                            val cloudTvs = withContext(Dispatchers.IO) { cloudRepo.fetchTvListForUser(remoteUsername) }
                            if (cloudTxs.isNotEmpty()) {
                                _state.value = _state.value.copy(transaksiList = cloudTxs)
                                StorageUtil.saveTransaksi(cloudTxs)
                            }
                            if (cloudTvs.isNotEmpty()) {
                                val localById = _state.value.tvList.associateBy { it.id }
                                val mergedTvs = cloudTvs.map { cloud ->
                                    val local = localById[cloud.id]
                                    if (local == null) cloud else cloud.copy(
                                        certPem = local.certPem,
                                        keyPem = local.keyPem,
                                        paired = cloud.paired || local.paired,
                                        promoVideoPath = local.promoVideoPath,
                                        lockscreenVideoPath = local.lockscreenVideoPath,
                                    )
                                }
                                _state.value = _state.value.copy(tvList = mergedTvs)
                                StorageUtil.saveTvList(mergedTvs)
                                refreshHubConfig()
                            }
                            // Sync restored data to cloud
                            try { syncToCloud() } catch (e: Exception) { Log.e("MainVM", "syncToCloud after remote Google restore: ${e.message}") }
                        }
                    } else {
                        // User baru — minta isi data rental dulu
                        _state.value = _state.value.copy(
                            pendingGoogleRegistration = true,
                            pendingGoogleEmail = verifiedEmail,
                            pendingGoogleDisplayName = displayName,
                            pendingGoogleIdToken = idToken,
                            isBusy = false, statusMessage = "",
                        )
                    }
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isBusy = false,
                    statusMessage = "Google Sign-In gagal: ${e.localizedMessage}",
                )
            }
        }
    }

    fun completeGoogleRegistration(namaRental: String, alamatRental: String, whatsappRental: String) {
        val s = _state.value
        if (!s.pendingGoogleRegistration) return
        val email = s.pendingGoogleEmail
        val displayName = s.pendingGoogleDisplayName
        viewModelScope.launch {
            _state.value = _state.value.copy(isBusy = true, statusMessage = "Mendaftarkan...")
            val username = generateUniqueUsername(displayName)
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            val newUser = UserData(
                username = username, passwordHash = "", role = "admin", email = email,
                dibuat = sdf.format(java.util.Date()), emailVerified = true,
                namaRental = namaRental, alamatRental = alamatRental, whatsappRental = whatsappRental,
                registeredAt = System.currentTimeMillis(),
            )
            val claim = claimDeviceForLogin(newUser, "google")
            if (!claim.success) {
                _state.value = _state.value.copy(isBusy = false, pendingGoogleRegistration = false)
                return@launch
            }
            val updatedUsers = s.users + (username to newUser)
            StorageUtil.saveUsers(updatedUsers)
            StorageUtil.saveCurrentSession(username, "admin")
            StorageUtil.saveLoginMethod("google")
            _state.value = _state.value.copy(
                users = updatedUsers, isLoggedIn = true, currentUser = username, currentRole = "admin",
                pendingGoogleRegistration = false, pendingGoogleEmail = "", pendingGoogleDisplayName = "", pendingGoogleIdToken = "",
                isBusy = false, statusMessage = "", loginMethod = "google",
            )
            if (_state.value.trialBatas == 0L && _state.value.licenseStatus.status != "active") {
                // Rate limit trial to once per device
                val trialDeviceId = StorageUtil.getSecurePreference("trialDeviceId") ?: ""
                val currentDeviceId = try {
                    android.provider.Settings.Secure.getString(
                        getApplication<android.app.Application>().contentResolver,
                        android.provider.Settings.Secure.ANDROID_ID,
                    ) ?: ""
                } catch (e: Exception) { "" }
                if (trialDeviceId.isEmpty() || trialDeviceId == currentDeviceId) {
                    if (trialDeviceId.isEmpty()) {
                        StorageUtil.putSecurePreference("trialDeviceId", currentDeviceId)
                    }
                    val trialEnd = System.currentTimeMillis() + 3 * 24 * 60 * 60 * 1000L
                    _state.value = _state.value.copy(trialBatas = trialEnd, maxTv = 2)
                    StorageUtil.saveTrial(trialEnd)
                    launch { try { cloudRepo.saveTrialToCloud(username, trialEnd) } catch (e: Exception) { Log.e("MainVM", "saveTrialToCloud on Google sign-in new user: ${e.message}") } }
                }
            }
            try {
                launch { try { syncToCloud() } catch (e: Exception) { Log.e("MainVM", "syncToCloud after Google registration: ${e.message}") } }
            } catch (e: Exception) { Log.e("MainVM", "post-Google-registration sync: ${e.message}") }
            checkWelcomePromo(_state.value.promo)
        }
    }

    fun cancelGoogleRegistration() {
        _state.value = _state.value.copy(
            pendingGoogleRegistration = false,
            pendingGoogleEmail = "",
            pendingGoogleDisplayName = "",
            pendingGoogleIdToken = "",
            isBusy = false,
        )
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

    fun setBackgroundOption(option: BackgroundOption) {
        StorageUtil.saveBackgroundOption(option.name)
        _state.value = _state.value.copy(backgroundOption = option)
    }

    fun logout(clearGoogleSignIn: Boolean = false) {
        val username = _state.value.currentUser
        val sessionId = _state.value.deviceSessionId
        stopDeviceSessionListener()
        viewModelScope.launch {
            if (username.isNotBlank() && sessionId.isNotBlank()) {
                withTimeoutOrNull(2_500) {
                    withContext(Dispatchers.IO) { cloudRepo.releaseDeviceSession(username, sessionId) }
                }
            }
            performLocalLogout(clearGoogleSignIn)
        }
    }

    private fun performLocalLogout(clearGoogleSignIn: Boolean) {
        addActivityLog("Logout")
        stopLicenseListener()
        stopPromoListener()
        stopNotifListener()
        try { firebaseAuth.signOut() } catch (e: Exception) { Log.e("MainVM", "firebaseAuth.signOut on logout: ${e.message}") }
        if (clearGoogleSignIn) {
            try {
                val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
                    com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN
                )
                    .requestIdToken(getApplication<android.app.Application>().getString(com.billingps.aptv.R.string.default_web_client_id))
                    .requestEmail()
                    .build()
                val client = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(getApplication(), gso)
                client.signOut() // ← clears local Google Play cache so picker shows next time
                    .addOnCompleteListener { Log.i("MainVM", "Google signOut complete") }
                client.revokeAccess()
                    .addOnCompleteListener { Log.i("MainVM", "Google revokeAccess complete") }
                Log.i("MainVM", "Google signOut + revokeAccess initiated")
            } catch (e: Exception) { Log.e("MainVM", "Google signOut: ${e.message}") }
            StorageUtil.clearAllAppData()
        }
        StorageUtil.clearSession()
        StorageUtil.saveLoginMethod("password")
        _state.value = _state.value.copy(
            isLoggedIn = false, currentUser = "", currentRole = "", loginMethod = "password",
            transaksiList = emptyList(), tvList = emptyList(), invoices = emptyList(),
            notifications = emptyList(), unreadNotifCount = 0,
            promo = PromoSettings(), licenseStatus = LicenseStatus(), trialBatas = 0L, maxTv = 0,
            deviceConflict = null, deviceSessionId = "", isBusy = false,
        )
        com.billingps.aptv.utils.ApkServer.clientAllowed = false
        TvHubManager.updateConfig(emptyList(), "RR Billing Pro", clientEnabled = false)
    }

    private fun restoreLocalDataAfterLogin() {
        val lic = StorageUtil.loadLicense()
        _state.value = _state.value.copy(
            tvList = StorageUtil.loadTvList(),
            transaksiList = StorageUtil.loadTransaksi(),
            licenseStatus = lic,
            trialBatas = StorageUtil.loadTrialBatas(),
            maxTv = if (lic.status == "active") (if (lic.promoAddTv > 0) lic.promoAddTv else lic.maxTv) else 0,
            habisDismissedIds = StorageUtil.loadHabisDismissedIds(),
        )
        refreshHubConfig()
    }

    private suspend fun restoreLicenseAndTrial(username: String) {
        // 1) Cek lisensi dari penyimpanan lokal dulu — dan cek masa kedaluwarsa
        var lic = StorageUtil.loadLicense()
        var trial = StorageUtil.loadTrialBatas()
        Log.i("MainVM", "restoreLicenseAndTrial: local lic status='${lic.status}' maxTv=${lic.maxTv} promoAddTv=${lic.promoAddTv} expiresAt='${lic.expiresAt}' trial=$trial")

        if (lic.status == "active" && lic.expiresAt.isNotEmpty()) {
            try {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                val expiryDate = sdf.parse(lic.expiresAt)
                if (expiryDate != null && !expiryDate.after(java.util.Date())) {
                    Log.i("MainVM", "restoreLicenseAndTrial: local license EXPIRED on ${lic.expiresAt}")
                    lic = lic.copy(status = "inactive", pesan = "❌ Lisensi telah kedaluwarsa pada ${lic.expiresAt}")
                    StorageUtil.saveLicense(lic)
                }
            } catch (e: Exception) { Log.e("MainVM", "expiry check: ${e.message}") }
        }

        if (lic.status == "active" && !enforceLicenseIntegrity(username, lic)) {
            lic = LicenseStatus()
            trial = 0L
        }

        // 2) Coba ambil data terbaru dari cloud — PRIORITAS UTAMA
        try {
            val cloudLic = withContext(Dispatchers.IO) { cloudRepo.fetchLicenseForUser(username) }
            if (cloudLic != null && cloudLic.status == "active") {
                Log.i("MainVM", "restoreLicenseAndTrial: cloud FOUND status=${cloudLic.status} maxTv=${cloudLic.maxTv} promoAddTv=${cloudLic.promoAddTv} pesan='${cloudLic.pesan}'")
                // Cloud adalah primary — tapi jika cloud tidak punya promoAddTv, pertahankan dari lokal
                var mergedPromoAddTv = if (cloudLic.promoAddTv > 0) cloudLic.promoAddTv
                    else lic.promoAddTv
                // Jika masih 0, coba langsung dari user doc (mungkin promo ada di sana)
                if (mergedPromoAddTv == 0) {
                    try {
                        val userDocLic = withContext(Dispatchers.IO) { cloudRepo.fetchUserLicenseStatus(username) }
                        if (userDocLic != null && userDocLic.promoAddTv > 0) {
                            mergedPromoAddTv = userDocLic.promoAddTv
                            Log.i("MainVM", "restoreLicenseAndTrial: promoAddTv from userDoc=$mergedPromoAddTv")
                        }
                    } catch (e: Exception) { Log.e("MainVM", "fetchUserDoc promo failed: ${e.message}") }
                }
                lic = cloudLic.copy(promoAddTv = mergedPromoAddTv)
                if (mergedPromoAddTv > 0) {
                    lic = lic.copy(maxTv = mergedPromoAddTv)
                }
                trial = 0L
                StorageUtil.saveLicense(lic)
            } else {
                Log.i("MainVM", "restoreLicenseAndTrial: cloud null or not active: $cloudLic")
            }
        } catch (e: Exception) { Log.e("MainVM", "cloud fetch license failed: ${e.message}") }

        if (lic.status == "active" && !enforceLicenseIntegrity(username, lic)) {
            lic = LicenseStatus()
        }

        // 3) Kalau ada lisensi aktif — terapkan hak penuh sesuai paket
        if (lic.status == "active") {
            val effectiveMaxTv = when {
                lic.promoAddTv > 0 -> lic.promoAddTv // legacy promo license
                lic.maxTv > 0 -> lic.maxTv // explicit quota from new plans
                else -> parseMaxTvFromPesan(lic.pesan) ?: lic.maxTv
            }
            Log.i("MainVM", "restoreLicenseAndTrial: ACTIVE lic.maxTv=${lic.maxTv} pesan='${lic.pesan}' -> effectiveMaxTv=$effectiveMaxTv")
            _state.value = _state.value.copy(licenseStatus = lic, trialBatas = 0L, maxTv = effectiveMaxTv)
            refreshHubConfig()
            startListeners(username)
            return
        }

        // 4) Tidak ada lisensi — cek trial dari cloud, atau beri trial baru
        Log.i("MainVM", "restoreLicenseAndTrial: no active license, trial=$trial")
        if (trial <= System.currentTimeMillis()) {
            try {
                val cloudTrial = withContext(Dispatchers.IO) { cloudRepo.loadTrialFromCloud(username) }
                if (cloudTrial > System.currentTimeMillis()) trial = cloudTrial else trial = 0L
            } catch (e: Exception) { trial = 0L }
        }
        if (trial <= 0L) {
            // Rate limit: only grant new trial if device hasn't already had one
            val trialDeviceId = StorageUtil.getSecurePreference("trialDeviceId") ?: ""
            val currentDeviceId = try {
                android.provider.Settings.Secure.getString(
                    getApplication<android.app.Application>().contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID,
                ) ?: ""
            } catch (e: Exception) { "" }
            if (trialDeviceId.isEmpty()) {
                // First time — grant trial
                StorageUtil.putSecurePreference("trialDeviceId", currentDeviceId)
                trial = System.currentTimeMillis() + 3 * 24 * 60 * 60 * 1000L
                try { withContext(Dispatchers.IO) { cloudRepo.saveTrialToCloud(username, trial) } } catch (e: Exception) {}
                Log.i("MainVM", "restoreLicenseAndTrial: granting new trial (first device)")
            } else {
                // Trial already used on this device — never grant a second free trial
                Log.w("MainVM", "restoreLicenseAndTrial: trial already used on device ($trialDeviceId), blocking new trial")
                trial = 0L
            }
        }
        Log.i("MainVM", "restoreLicenseAndTrial: setting trial maxTv=2 trial=$trial")
        _state.value = _state.value.copy(trialBatas = trial, maxTv = 2)
        StorageUtil.saveTrial(trial)
        refreshHubConfig()
        startListeners(username)
    }

    private data class LicenseIntegrityResult(
        val valid: Boolean,
        val invalidIds: List<String> = emptyList(),
    )

    private fun payloadJson(record: LicenseRecord): org.json.JSONObject? {
        if (record.payload.isBlank()) return null
        return try { org.json.JSONObject(record.payload) } catch (e: Exception) { null }
    }

    private fun payloadMatchesRecord(record: LicenseRecord): Boolean {
        val json = payloadJson(record) ?: return false
        val signedUser = json.optString("u", "")
        if (signedUser.isNotBlank() && record.username.isNotBlank() &&
            !signedUser.equals(record.username, ignoreCase = true)) return false
        val signedEmail = json.optString("m", "")
        if (signedEmail.isNotBlank() && record.email.isNotBlank() &&
            !signedEmail.equals(record.email, ignoreCase = true)) return false
        return true
    }

    private fun signedExpiry(record: LicenseRecord): String {
        val json = payloadJson(record) ?: return record.expiry
        return json.optString("e", "").takeIf { it.isNotBlank() } ?: record.expiry
    }

    private fun signedMaxTv(record: LicenseRecord): Int? {
        val json = payloadJson(record) ?: return record.maxTv.takeIf { it > 0 }
        if (!json.has("q")) return record.maxTv.takeIf { it > 0 }
        return json.optInt("q", -1).takeIf { it >= 0 }
    }

    private fun parseExpiryMillis(value: String): Long? {
        if (value.isBlank()) return null
        return try {
            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .parse(value)?.time
        } catch (e: Exception) { null }
    }

    private suspend fun checkLicenseIntegrity(username: String, lic: LicenseStatus): LicenseIntegrityResult {
        if (username.equals(LicenseCatalog.VENDOR_ACCOUNT, ignoreCase = true)) {
            return LicenseIntegrityResult(true)
        }
        val email = _state.value.users[username]?.email ?: ""
        val records = withContext(Dispatchers.IO) {
            cloudRepo.getLicenseRecordsForUser(username, email)
        } ?: return LicenseIntegrityResult(true)

        if (records.isEmpty()) {
            Log.w("MainVM", "License integrity: no records for $username")
            return LicenseIntegrityResult(false)
        }

        val selfConfirmed = mutableMapOf<String, Boolean>()
        for (record in records) {
            if (record.generatedBy.isNotBlank()) {
                selfConfirmed[record.id] = withContext(Dispatchers.IO) {
                    cloudRepo.isRentalAdminAccount(record.generatedBy)
                }
            }
        }

        val trusted = records.filter { record ->
            !record.revoked &&
                selfConfirmed[record.id] != true &&
                payloadMatchesRecord(record)
        }
        if (trusted.isEmpty()) {
            val invalidIds = records
                .filter { record ->
                    record.revoked ||
                        selfConfirmed[record.id] == true ||
                        !payloadMatchesRecord(record)
                }
                .map { it.id }
            Log.w("MainVM", "License integrity: no trusted records for $username")
            return LicenseIntegrityResult(false, invalidIds)
        }

        val maxTrustedExpiry = trusted.mapNotNull { parseExpiryMillis(signedExpiry(it)) }.maxOrNull()
        val userExpiry = parseExpiryMillis(lic.expiresAt)
        if (maxTrustedExpiry != null && userExpiry != null &&
            userExpiry > maxTrustedExpiry + 2L * 24 * 60 * 60 * 1000) {
            Log.w("MainVM", "License integrity: expiry exceeds signed expiry for $username")
            return LicenseIntegrityResult(false)
        }

        val maxTrustedMaxTv = trusted.mapNotNull { signedMaxTv(it) }.maxOrNull()
        if (lic.maxTv == 0) {
            val hasUnlimited = trusted.any { record ->
                val q = signedMaxTv(record)
                q == null || q >= 999999
            }
            if (!hasUnlimited) {
                Log.w("MainVM", "License integrity: unlimited TV not signed for $username")
                return LicenseIntegrityResult(false)
            }
        } else if (maxTrustedMaxTv != null && lic.maxTv > maxTrustedMaxTv) {
            Log.w("MainVM", "License integrity: maxTv exceeds signed maxTv for $username")
            return LicenseIntegrityResult(false)
        }

        return LicenseIntegrityResult(true)
    }

    private suspend fun enforceLicenseIntegrity(username: String, lic: LicenseStatus): Boolean {
        if (lic.status != "active") return true
        val result = checkLicenseIntegrity(username, lic)
        if (result.valid) return true

        withContext(Dispatchers.IO) {
            result.invalidIds.forEach { id ->
                if (id.isNotBlank()) cloudRepo.revokeLicense(id)
            }
            cloudRepo.writeLicenseStatusToUserDoc(
                username,
                mapOf(
                    "status" to "revoked",
                    "pesan" to "Lisensi tidak valid. Hubungi admin.",
                    "expiresAt" to "",
                    "maxTv" to 0,
                    "promoAddTv" to 0,
                    "clientEnabled" to false,
                ),
            )
        }
        val revoked = LicenseStatus(
            status = "revoked",
            pesan = "Lisensi tidak valid. Hubungi admin.",
        )
        _state.value = _state.value.copy(licenseStatus = revoked, maxTv = 0)
        StorageUtil.saveLicense(revoked)
        refreshHubConfig()
        Log.w("MainVM", "License integrity failed for $username; revoked ids=${result.invalidIds}")
        return false
    }

    private fun parseMaxTvFromPesan(pesan: String): Int? {
        val u = pesan.uppercase()
        return when {
            u.contains("LIFETIME") -> 0
            u.contains("TAHUNAN") || u.contains("1 TAHUN") -> 15
            u.contains("3BULAN") || u.contains("3 BULAN") -> 8
            u.contains("BULANAN") || u.contains("1 BULAN") -> 5
            else -> null
        }
    }

    private fun stopClientStatusListeners() {
        Log.i("MainVM", "stopClientStatusListeners: ${clientStatusListeners.size} listeners")
        clientStatusListeners.values.forEach { it.remove() }
        clientStatusListeners.clear()
    }

    private fun startClientStatusListeners(username: String) {
        stopClientStatusListeners()
        val tvList = _state.value.tvList
        Log.i("MainVM", "startClientStatusListeners: tvList.size=${tvList.size} username=$username")
        for (tv in tvList) {
            Log.i("MainVM", "startClientStatusListeners: attaching for TV id=${tv.id} nama=${tv.nama}")
            val listener = cloudRepo.listenClientStatus(username, tv.id) { status ->
                Log.i("MainVM", "listenClientStatus callback: tvId=${tv.id} status=$status")
                val updated = _state.value.clientStatus.toMutableMap()
                val isStale = status.lastSeen > 0 && (System.currentTimeMillis() - status.lastSeen) > 120_000
                updated[tv.id] = if (isStale) status.copy(online = false) else status
                _state.value = _state.value.copy(clientStatus = updated)
            }
            if (listener != null) {
                clientStatusListeners[tv.id] = listener
            } else {
                Log.w("MainVM", "listenClientStatus returned null for tvId=${tv.id}")
            }
        }
    }

    private fun refreshClientStatusListeners() {
        val u = _state.value.currentUser
        if (u.isNotEmpty()) startClientStatusListeners(u)
    }

    private suspend fun startListeners(username: String) {
        startLicenseListener(username)
        startPromoListener()
        startNotifListener(username)
        initFcmToken(username)
        startClientStatusListeners(username)
    }

    private fun startLicenseListener(username: String) {
        stopLicenseListener()
        Log.i("MainVM", "startLicenseListener for $username")
        userDocListener = cloudRepo.listenUserLicense(username) { cloudLic ->
            if (cloudLic != null) {
                if (cloudLic.status == "revoked") {
                    Log.i("MainVM", "listener: license REVOKED, clearing local state")
                    _state.value = _state.value.copy(licenseStatus = LicenseStatus(), maxTv = 0)
                    StorageUtil.saveLicense(LicenseStatus())
                    refreshHubConfig()
                } else {
                    viewModelScope.launch {
                        if (!enforceLicenseIntegrity(username, cloudLic)) return@launch
                        Log.i("MainVM", "listener: license updated to ${cloudLic.status} expires=${cloudLic.expiresAt}")
                        _state.value = _state.value.copy(licenseStatus = cloudLic, trialBatas = 0L, maxTv = cloudLic.maxTv)
                        StorageUtil.saveLicense(cloudLic)
                        refreshHubConfig()
                        // Activate the unactivated license doc so License Generator history shows it as activated
                        withContext(Dispatchers.IO) {
                            cloudRepo.findAndActivateLicenseByUsername(username, cloudLic.expiresAt, deviceType = "android")
                        }
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
                // Cloud is source of truth for CONFIRMED status
                if (cloudInv.status == "CONFIRMED") {
                    // Always accept cloud CONFIRMED status
                    currentInvs[idx] = cloudInv
                    Log.i("MainVM", "Invoice ${cloudInv.id} confirmed by cloud, updating local")
                } else if (existing.status == "CONFIRMED" && cloudInv.status != "CONFIRMED") {
                    // Local manipulation detected: revert to cloud status
                    Log.w("MainVM", "⚠️ Invoice ${cloudInv.id} was locally marked CONFIRMED but cloud says ${cloudInv.status} — reverting")
                    currentInvs[idx] = cloudInv
                } else if (existing.status != "CONFIRMED") {
                    currentInvs[idx] = cloudInv
                }
                _state.value = _state.value.copy(
                    invoices = currentInvs,
                    pendingInvoiceCount = currentInvs.count { it.status == "WAITING_CONFIRMATION" },
                )
                viewModelScope.launch(Dispatchers.IO) { StorageUtil.saveInvoices(currentInvs) }
                Log.i("MainVM", "invoices listener: updated invoice ${cloudInv.id} to CONFIRMED")
            } else {
                currentInvs.add(cloudInv)
                _state.value = _state.value.copy(
                    invoices = currentInvs,
                    pendingInvoiceCount = currentInvs.count { it.status == "WAITING_CONFIRMATION" },
                )
                viewModelScope.launch(Dispatchers.IO) { StorageUtil.saveInvoices(currentInvs) }
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
                checkPromoPopup(promo)
            }
        }
    }

    fun newUserPromoRemainingHours(): Long {
        val s = _state.value
        if (!s.promo.newUserPromoActive) return -1
        val user = s.users[s.currentUser] ?: return -1
        if (user.registeredAt <= 0) return -1
        val elapsed = System.currentTimeMillis() - user.registeredAt
        val durasiMs = s.promo.newUserPromoDurationHours * 60 * 60 * 1000L
        val remaining = durasiMs - elapsed
        return if (remaining <= 0) -1 else remaining / (60 * 60 * 1000)
    }

    fun isNewUserPromoActive(): Boolean {
        val s = _state.value
        if (!s.promo.newUserPromoActive) return false
        val user = s.users[s.currentUser] ?: return false
        if (user.registeredAt <= 0) return false
        val elapsed = System.currentTimeMillis() - user.registeredAt
        return elapsed < s.promo.newUserPromoDurationHours * 60 * 60 * 1000L
    }

    fun isRegularPromoActive(): Boolean {
        val s = _state.value
        if (!s.promo.promoAktif) return false
        if (s.promo.promoDurationHours <= 0) return true
        if (s.promo.promoStartedAt <= 0) return false
        val elapsed = System.currentTimeMillis() - s.promo.promoStartedAt
        return elapsed < s.promo.promoDurationHours * 60 * 60 * 1000L
    }

    fun regularPromoRemainingHours(): Long {
        val s = _state.value
        if (!s.promo.promoAktif || s.promo.promoDurationHours <= 0) return -1
        if (s.promo.promoStartedAt <= 0) return -1
        val elapsed = System.currentTimeMillis() - s.promo.promoStartedAt
        val durasiMs = s.promo.promoDurationHours * 60 * 60 * 1000L
        val remaining = durasiMs - elapsed
        return if (remaining <= 0) -1 else remaining / (60 * 60 * 1000)
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
        val key = when (namaPaket) {
            "1 Bulan", "BULANAN" -> "1 Bulan"
            "3 Bulan", "3BULAN" -> "3 Bulan"
            "1 Tahun", "TAHUNAN" -> "1 Tahun"
            "LIFETIME" -> "LIFETIME"
            else -> null
        }
        val diskonReguler = if (s.promo.promoAktif) {
            key?.let { s.promo.diskonPerPaket[it] } ?: 0
        } else 0
        val diskonNewUser = if (isNewUserPromoActive() && key != null) {
            s.promo.newUserDiskonPerPaket[key] ?: 0
        } else 0
        val diskon = maxOf(diskonReguler, diskonNewUser)
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

    private var broadcastListener: ListenerRegistration? = null

    private fun startNotifListener(username: String) {
        stopNotifListener()
        viewModelScope.launch {
            cloudRepo.ensureSignedIn()
            // Watch personal notifications
            notifListener = cloudRepo.listenUserNotifications(username) { list ->
                val merged = mergeNotifications(list, _state.value.notifications)
                _state.value = _state.value.copy(
                    notifications = merged,
                    unreadNotifCount = merged.count { !it.read },
                )
                StorageUtil.saveNotifications(merged)
            }
            // Watch broadcast notifications
            broadcastListener = cloudRepo.listenBroadcastNotifications { list ->
                val merged = mergeNotifications(list, _state.value.notifications)
                _state.value = _state.value.copy(
                    notifications = merged,
                    unreadNotifCount = merged.count { !it.read },
                )
                StorageUtil.saveNotifications(merged)
            }
        }
        // FCM in-app popup via event bus
        viewModelScope.launch {
            com.billingps.aptv.utils.NotificationEventBus.events.collect { notif ->
                _state.value = _state.value.copy(
                    showNotifPopup = true,
                    notifPopupTitle = notif.title,
                    notifPopupBody = notif.body,
                )
            }
        }
    }

    private fun mergeNotifications(newList: List<AppNotification>, existing: List<AppNotification>): List<AppNotification> {
        val all = (newList + existing).groupBy { it.id }.mapValues { (_, list) -> list.first() }
        return all.values.sortedByDescending { it.sentAt }
    }

    private fun stopNotifListener() {
        notifListener?.remove()
        notifListener = null
        broadcastListener?.remove()
        broadcastListener = null
    }

    fun markNotifRead() {
        val updated = _state.value.notifications.map { it.copy(read = true) }
        _state.value = _state.value.copy(notifications = updated, unreadNotifCount = 0)
        StorageUtil.saveNotifications(updated)
    }

    fun dismissPromoPopup() {
        _state.value = _state.value.copy(showPromoPopup = false, promoPopupTitle = "", promoPopupBody = "", promoEndTimeMs = 0L)
    }

    fun dismissNotifPopup() {
        _state.value = _state.value.copy(showNotifPopup = false, notifPopupTitle = "", notifPopupBody = "")
    }

    private fun checkPromoPopup(fetched: PromoSettings) {
        if (!fetched.promoAktif) return

        // Jangan tampilkan regular promo jika user masih dalam periode welcome promo
        if (isNewUserPromoActive()) return

        // Show once per day per user
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date())
        val lastShown = StorageUtil.loadDailyPromoDate()
        if (lastShown == today) return // Already shown today

        val aktifPackages = fetched.diskonPerPaket.filter { it.value > 0 }
        if (aktifPackages.isEmpty()) return

        // Calculate promo end time
        val promoEnd = if (fetched.promoStartedAt > 0 && fetched.promoDurationHours > 0) {
            fetched.promoStartedAt + (fetched.promoDurationHours * 3600000L)
        } else 0L

        // Build pricing detail
        val hargaNormal = mapOf(
            "1 Bulan" to 50000, "3 Bulan" to 150000, "1 Tahun" to 500000, "LIFETIME" to 1000000
        )
        val detailLines = aktifPackages.map { (pkg, diskon) ->
            val normal = hargaNormal[pkg] ?: 0
            val disc = (normal * diskon) / 100
            val after = normal - disc
            "$pkg: diskon $diskon% (Rp${fmtRpInt(normal)} → Rp${fmtRpInt(after)})"
        }
        val detail = detailLines.joinToString("\n")

        _state.value = _state.value.copy(
            showPromoPopup = true,
            promoPopupTitle = "🔥 PROMO AKTIF!",
            promoPopupBody = detail,
            promoEndTimeMs = promoEnd,
        )
        StorageUtil.saveDailyPromoDate(today)
    }

    private fun checkWelcomePromo(fetched: PromoSettings) {
        if (StorageUtil.loadWelcomePromoShown()) return
        val user = _state.value.users[_state.value.currentUser] ?: return
        if (user.registeredAt <= 0) return
        val durasiMs = if (fetched.newUserPromoActive) fetched.newUserPromoDurationHours * 60 * 60 * 1000L else 7 * 24 * 60 * 60 * 1000L
        val isNewUser = System.currentTimeMillis() - user.registeredAt < durasiMs
        if (!isNewUser) return
        val hasPromo = fetched.newUserPromoActive || fetched.promoAktif
        if (!hasPromo) return
        val msg = if (fetched.newUserPromoActive) {
            val diskon = fetched.newUserDiscountPercent
            "Selamat datang! Dapatkan promo $diskon% untuk paket langganan pertama Anda. Promo berlaku ${fetched.newUserPromoDurationHours} jam setelah registrasi."
        } else {
            val detail = fetched.diskonPerPaket.filter { it.value > 0 }
                .map { "${it.key}: diskon ${it.value}%" }.joinToString(", ")
            "Selamat datang! Nikmati promo spesial: $detail"
        }
        _state.value = _state.value.copy(
            showPromoPopup = true,
            promoPopupTitle = "🎉 PROMO SPESIAL!",
            promoPopupBody = msg,
        )
        StorageUtil.saveWelcomePromoShown(true)
    }

    // ── Kasir ──────────────────────────────────────────────
    suspend fun loginKasir(username: String, password: String): Boolean {
        val users = _state.value.users
        val user = users[username.lowercase()] ?: return false
        if (user.role != "kasir") return false
        if (!verifyPassword(password, user)) return false
        // Upgrade password hash to PBKDF2
        val upgraded = upgradePasswordHash(user, password)
        if (upgraded.passwordHash != user.passwordHash) {
            val updatedUsers = _state.value.users.toMutableMap()
            updatedUsers[user.username] = upgraded
            _state.value = _state.value.copy(users = updatedUsers)
            StorageUtil.saveUsers(updatedUsers)
        }
        val claim = claimDeviceForLogin(user, "password")
        if (!claim.success) return false
        StorageUtil.saveCurrentSession(user.username, user.role)
        _state.value = _state.value.copy(
            isLoggedIn = true,
            currentUser = user.username,
            currentRole = user.role,
        )
        restoreLocalDataAfterLogin()
        addActivityLog("Login kasir")
        startPromoListener()
        startNotifListener(user.username)
        initFcmToken(user.username)
        viewModelScope.launch { restoreLicenseAndTrial(user.username) }
        return true
    }

    fun getKasirList(): List<Pair<String, String>> =
        _state.value.users.filter { it.value.role == "kasir" }.map { it.key to it.value.role }.sortedBy { it.first }

    fun hapusUser(username: String): Boolean {
        // Only admin can delete users, and cannot delete own account
        if (_state.value.currentRole != "admin") return false
        val key = username.lowercase().trim()
        if (key == _state.value.currentUser.lowercase()) return false
        val users = _state.value.users.toMutableMap()
        val existing = users[key] ?: return false
        if (existing.role != "kasir") return false // only kasir accounts deletable
        users.remove(key)
        _state.value = _state.value.copy(users = users)
        StorageUtil.saveUsers(users)
        addActivityLog("Hapus user kasir: $key")
        viewModelScope.launch {
            try { cloudRepo.deleteUserDoc(key) } catch (e: Exception) { Log.e("MainVM", "deleteUserDoc after hapusUser: ${e.message}") }
            try { syncToCloud() } catch (e: Exception) { Log.e("MainVM", "syncToCloud after hapusUser: ${e.message}") }
        }
        return true
    }

    // ── Auto Push Notifications ────────────────────────────
    private fun sendPushNotification(title: String, body: String) {
        val username = _state.value.currentUser
        if (username.isBlank()) return
        // Set current role for FCM authorization
        FcmSender.setAuthorizedRole(_state.value.currentRole)
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
        obj.put("exportVersion", currentVersion)
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
                put("paketAktif", tv.paketAktif); put("sisaDetik", tv.sisaDetik)
                put("timerStart", tv.timerStart); put("timerDurasi", tv.timerDurasi)
                put("timerActive", tv.timerActive); put("bebas", tv.bebas)
                put("paketHarga", tv.paketHarga); put("totalPesanan", tv.totalPesanan)
                put("bebasMulai", tv.bebasMulai); put("bebasHargaPerJam", tv.bebasHargaPerJam)
                put("bebasPesananTotal", tv.bebasPesananTotal); put("cancelBatas", tv.cancelBatas)
                put("sudahBayar", tv.sudahBayar); put("namaRental", tv.namaRental)
                put("popupMode", tv.popupMode); put("popupLastMinutes", tv.popupLastMinutes)
                put("bebasInterval", tv.bebasInterval); put("bebasDurasi", tv.bebasDurasi)
                put("hdmiPort", tv.hdmiPort); put("hdmiInputHint", tv.hdmiInputHint)
            }
        }))
        obj.put("transaksiList", org.json.JSONArray(StorageUtil.loadTransaksi().map { t ->
            org.json.JSONObject().apply {
                put("id", t.id); put("waktu", t.waktu); put("kasir", t.kasir)
                put("kota", t.kota); put("paket", t.paket); put("total", t.total)
                put("pesanan", org.json.JSONObject(t.pesanan.mapKeys { it.key }))
                put("paketHarga", t.paketHarga)
                put("pesananHarga", org.json.JSONObject(t.pesananHarga.mapKeys { it.key }))
                put("tvJenisPs", t.tvJenisPs); put("sudahBayar", t.sudahBayar)
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
                        paketAktif = o.optString("paketAktif"),
                        sisaDetik = o.optLong("sisaDetik"),
                        timerStart = o.optLong("timerStart"),
                        timerDurasi = o.optLong("timerDurasi"),
                        timerActive = o.optBoolean("timerActive"),
                        bebas = o.optBoolean("bebas"),
                        paketHarga = o.optInt("paketHarga"),
                        totalPesanan = o.optInt("totalPesanan"),
                        bebasMulai = o.optLong("bebasMulai"),
                        bebasHargaPerJam = o.optInt("bebasHargaPerJam"),
                        bebasPesananTotal = o.optInt("bebasPesananTotal"),
                        cancelBatas = o.optLong("cancelBatas"),
                        sudahBayar = o.optBoolean("sudahBayar"),
                        namaRental = o.optString("namaRental"),
                        popupMode = o.optString("popupMode", "always"),
                        popupLastMinutes = o.optInt("popupLastMinutes", 5),
                        bebasInterval = o.optInt("bebasInterval", 60),
                        bebasDurasi = o.optInt("bebasDurasi", 30),
                        hdmiPort = o.optInt("hdmiPort", 0),
                        hdmiInputHint = o.optString("hdmiInputHint"),
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
                    val pesananMap = mutableMapOf<String, Int>()
                    o.optJSONObject("pesanan")?.let { pes ->
                        pes.keys().forEach { key -> pesananMap[key] = pes.optInt(key) }
                    }
                    val pesananHargaMap = mutableMapOf<String, Int>()
                    o.optJSONObject("pesananHarga")?.let { ph ->
                        ph.keys().forEach { key -> pesananHargaMap[key] = ph.optInt(key) }
                    }
                    list.add(Transaksi(
                        id = o.optString("id"), waktu = o.optString("waktu"),
                        kasir = o.optString("kasir"), kota = o.optString("kota"),
                        paket = o.optString("paket"), total = o.optInt("total"),
                        pesanan = pesananMap, paketHarga = o.optInt("paketHarga"),
                        pesananHarga = pesananHargaMap, tvJenisPs = o.optString("tvJenisPs"),
                        sudahBayar = o.optBoolean("sudahBayar"),
                    ))
                }
                StorageUtil.saveTransaksi(list)
                _state.value = _state.value.copy(transaksiList = list)
            }

            refreshHubConfig()
            addActivityLog("Restore backup")
            "✅ Backup berhasil direstore! ${obj.optString("exportedAt", "")}"
        } catch (e: Exception) {
            "❌ Gagal restore: ${e.message}"
        }
    }

    fun switchUser(username: String, password: String = ""): Boolean {
        val users = _state.value.users
        val user = users[username.lowercase()] ?: return false
        // Require password verification for security
        if (!verifyPassword(password, user)) return false
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
        return true
    }

    // ── Registrasi ─────────────────────────────────────────
    fun cekUsername(username: String): Boolean = _state.value.users.containsKey(username.lowercase())

    suspend fun daftarUser(username: String, password: String, email: String, role: String, noHp: String = ""): AuthResult {
        val key = username.lowercase().trim()
        val normalizedEmail = email.trim()
        val normalizedHp = noHp.trim()
        if (key.isEmpty() || password.length < 6) return AuthResult(false, "Username & password minimal 6 karakter")
        if (!password.any { it.isUpperCase() }) return AuthResult(false, "Password harus mengandung huruf besar")
        if (!password.any { it.isDigit() }) return AuthResult(false, "Password harus mengandung angka")
        if (cekUsername(key)) return AuthResult(false, "Username sudah terdaftar")
        // Admin registration requires email + noHp
        if (role == "admin") {
            if (normalizedEmail.isEmpty()) return AuthResult(false, "Email wajib diisi untuk verifikasi akun")
            if (normalizedHp.isEmpty()) return AuthResult(false, "Nomor HP wajib diisi")
            if (normalizedHp.length < 10) return AuthResult(false, "Nomor HP minimal 10 digit")
        }
        if (normalizedEmail.isNotEmpty() && !isValidEmail(normalizedEmail)) return AuthResult(false, "Format email tidak valid")
        if (normalizedEmail.isNotEmpty() && _state.value.users.values.any { it.email.equals(normalizedEmail, ignoreCase = true) }) {
            return AuthResult(false, "Email sudah terdaftar")
        }

        // Generate 6-digit verification code
        val verificationCode = String.format("%06d", (100000..999999).random())
        val verificationCodeExpiry = System.currentTimeMillis() + 600_000L // 10 menit
        val salt = generateSalt()
        val hash = pbkdf2Hash(password, salt)
        val user = UserData(
            username = key,
            passwordHash = hash,
            salt = salt,
            role = role,
            email = normalizedEmail,
            noHp = normalizedHp,
            dibuat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).format(java.util.Date()),
            emailVerified = false,
            verificationCode = verificationCode,
            verificationCodeExpiry = verificationCodeExpiry,
            registeredAt = System.currentTimeMillis(),
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
            // Rate limit: only grant trial once per device
            val trialDeviceId = StorageUtil.getSecurePreference("trialDeviceId") ?: ""
            val currentDeviceId = try {
                android.provider.Settings.Secure.getString(
                    getApplication<android.app.Application>().contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID,
                ) ?: ""
            } catch (e: Exception) { "" }
            if (trialDeviceId.isNotEmpty() && trialDeviceId != currentDeviceId) {
                Log.w("MainVM", "Trial already granted to another device ($trialDeviceId), skipping")
                // Still allow login but without new trial
            } else {
                val trialEnd = System.currentTimeMillis() + 3 * 24 * 60 * 60 * 1000L
                _state.value = _state.value.copy(trialBatas = trialEnd, maxTv = 2)
                StorageUtil.saveTrial(trialEnd)
                StorageUtil.putSecurePreference("trialDeviceId", currentDeviceId)
                viewModelScope.launch { try { cloudRepo.saveTrialToCloud(key, trialEnd) } catch (e: Exception) { Log.e("MainVM", "saveTrialToCloud after daftarUser: ${e.message}") } }
            }
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
        // Check if code has expired (10 minutes)
        if (user.verificationCodeExpiry > 0 && System.currentTimeMillis() > user.verificationCodeExpiry) {
            return false // Code expired
        }
        users[username] = user.copy(emailVerified = true, verificationCode = "", verificationCodeExpiry = 0)
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
        users[username] = user.copy(verificationCode = newCode, verificationCodeExpiry = System.currentTimeMillis() + 600_000L)
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

    // ── Auto Update (via GitHub Releases) ─────────────────
    fun refreshTv() {
        val list = StorageUtil.loadTvList()
        _state.value = _state.value.copy(tvList = list)
    }

    fun checkForUpdate() {
        viewModelScope.launch {
            _state.value = _state.value.copy(updateChecked = false, updateInfo = null)
            try {
                val remote = withContext(Dispatchers.IO) { fetchLatestGitHubRelease() }
                if (remote != null) {
                    val (versionName, apkUrl, changelog) = remote
                    if (VersionUtil.isNewerVersion(versionName, currentVersion) && apkUrl.isNotBlank()) {
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

    private fun fetchLatestGitHubRelease(): Triple<String, String, String>? {
        val url = URL("https://api.github.com/repos/dedekemoking-commits/rrbillingprov2/releases/latest")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", "BillingPS-APTV2")
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        val responseCode = conn.responseCode
        if (responseCode != 200) {
            Log.i("MainVM", "GitHub API responded $responseCode")
            return null
        }
        val json = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        val release = org.json.JSONObject(json)
        val tagName = release.optString("tag_name", "")
        val versionName = tagName.removePrefix("v")
        val changelog = release.optString("body", "")
        val assets = release.optJSONArray("assets")
        var apkUrl = ""
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.optString("name", "") == "app-release.apk") {
                    apkUrl = asset.optString("browser_download_url", "")
                    break
                }
            }
        }
        if (versionName.isBlank() || apkUrl.isBlank()) return null
        return Triple(versionName, apkUrl, changelog)
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
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.instanceFollowRedirects = true
                    conn.connectTimeout = 15000
                    conn.readTimeout = 30000
                    conn.setRequestProperty("User-Agent", "BillingPS-APTV2")
                    val responseCode = conn.responseCode
                    if (responseCode !in 200..299) {
                        throw java.io.IOException("Download APK gagal (HTTP $responseCode)")
                    }
                    val totalBytes = conn.contentLengthLong
                    var downloaded = 0L
                    conn.inputStream.use { input ->
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
                    conn.disconnect()
                    if (apkFile.length() < 1024L) {
                        throw java.io.IOException("File APK tidak valid")
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
    fun canAddTV(showPopup: Boolean = false): Boolean {
        val s = _state.value
        fun deny(title: String, body: String): Boolean {
            if (showPopup) showQuotaPopup(title, body)
            return false
        }
        if (!isDeviceClockTrusted()) {
            return deny(
                "Waktu Perangkat Tidak Valid",
                "Atur tanggal dan jam perangkat secara otomatis, lalu coba lagi.",
            )
        }
        val hasLicense = s.licenseStatus.status == "active"
        val hasTrial = s.trialBatas > System.currentTimeMillis()
        if (!hasLicense && !hasTrial) {
            return deny(
                "Lisensi Tidak Aktif",
                "Aktifkan lisensi terlebih dahulu di tab Lisensi untuk menambah TV.",
            )
        }
        val maxTv = s.maxTv
        if (maxTv > 0 && s.tvList.size >= maxTv) {
            return deny(
                "Batas TV Tercapai",
                "Paket Anda mendukung $maxTv TV dan saat ini sudah terdaftar ${s.tvList.size} TV. " +
                    "Tambahkan kuota TV melalui tab Lisensi.",
            )
        }
        return true
    }

    fun showQuotaPopup(title: String, body: String) {
        _state.value = _state.value.copy(
            quotaPopupVisible = true,
            quotaPopupTitle = title,
            quotaPopupBody = body,
        )
    }

    fun dismissQuotaPopup() {
        _state.value = _state.value.copy(quotaPopupVisible = false)
    }

    fun tambahTV(tv: TvData): Boolean {
        val s = _state.value
        // Clock integrity check
        if (!isDeviceClockTrusted()) {
            addActivityLog("⚠️ Tambah TV ditolak: manipulasi jam terdeteksi")
            canAddTV(showPopup = true)
            return false
        }
        if (!canAddTV(showPopup = true)) return false
        val list = s.tvList.toMutableList()
        list.add(tv)
        _state.value = s.copy(tvList = list)
        StorageUtil.saveTvList(list)
        refreshClientStatusListeners()
        try { syncToCloud() } catch (e: Exception) { Log.e("MainVM", "syncToCloud after tambahTV: ${e.message}") }
        return true
    }

    fun updateTV(id: String, updates: Map<String, Any>) {
        val previousTv = _state.value.tvList.firstOrNull { it.id == id }
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
                            "trxId" -> t.copy(trxId = v as? String ?: return@forEach)
                            "pesanan" -> t.copy(pesanan = anyToIntMap(v) ?: return@forEach)
                            "pesananHarga" -> t.copy(pesananHarga = anyToIntMap(v) ?: return@forEach)
                            "bebasMulai" -> t.copy(bebasMulai = (v as? Number)?.toLong() ?: return@forEach)
                            "bebasHargaPerJam" -> t.copy(bebasHargaPerJam = (v as? Number)?.toInt() ?: return@forEach)
                            "bebasPesananTotal" -> t.copy(bebasPesananTotal = (v as? Number)?.toInt() ?: return@forEach)
                            "paired" -> t.copy(paired = v as? Boolean ?: return@forEach)
                            "certPem" -> t.copy(certPem = v as? String ?: return@forEach)
                            "keyPem" -> t.copy(keyPem = v as? String ?: return@forEach)
                            "cancelBatas" -> t.copy(cancelBatas = (v as? Number)?.toLong() ?: return@forEach)
                            "sudahBayar" -> t.copy(sudahBayar = v as? Boolean ?: return@forEach)
                            "namaRental" -> t.copy(namaRental = v as? String ?: return@forEach)
                            "promoVideoPath" -> t.copy(promoVideoPath = v as? String ?: return@forEach)
                            "lockscreenVideoPath" -> t.copy(lockscreenVideoPath = v as? String ?: return@forEach)
                            "popupMode" -> t.copy(popupMode = v as? String ?: return@forEach)
                            "popupLastMinutes" -> t.copy(popupLastMinutes = (v as? Number)?.toInt() ?: return@forEach)
                            "bebasInterval" -> t.copy(bebasInterval = (v as? Number)?.toInt() ?: return@forEach)
                            "bebasDurasi" -> t.copy(bebasDurasi = (v as? Number)?.toInt() ?: return@forEach)
                            "hdmiPort" -> t.copy(hdmiPort = (v as? Number)?.toInt() ?: return@forEach)
                            "hdmiInputHint" -> t.copy(hdmiInputHint = v as? String ?: return@forEach)
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
        refreshHubConfig()
        val updatedTv = list.firstOrNull { it.id == id }
        if (updatedTv != null) {
            val keys = updates.keys
            val configKeys = setOf(
                "namaRental", "popupMode", "popupLastMinutes", "bebasInterval",
                "bebasDurasi", "hdmiPort", "hdmiInputHint",
            )
            val wasActive = previousTv?.let { it.timerActive || it.bebas } == true
            val bebasChanged = updates.containsKey("bebas") &&
                (updates["bebas"] as? Boolean) != previousTv?.bebas
            val nowActive = updatedTv.timerActive || updatedTv.bebas
            when {
                nowActive && (!wasActive || bebasChanged) -> {
                    startSessionOnTv(updatedTv)
                }
                nowActive -> {
                    TvHubManager.sendSyncTimer(updatedTv)
                }
                wasActive -> {
                    TvHubManager.sendStopTimer(updatedTv)
                }
            }
            if (keys.any { it in configKeys }) {
                TvHubManager.applyTvConfig(updatedTv)
            }
        }
        refreshTimerService()
        try { syncToCloud() } catch (e: Exception) { Log.e("MainVM", "syncToCloud after updateTV: ${e.message}") }
    }

    private fun anyToIntMap(value: Any?): Map<String, Int>? {
        val map = value as? Map<*, *> ?: return null
        val result = mutableMapOf<String, Int>()
        map.forEach { (key, raw) ->
            val name = key as? String ?: return@forEach
            val number = (raw as? Number)?.toInt() ?: return@forEach
            result[name] = number
        }
        return result
    }

    fun cancelAutoSleep(id: String) {
        pendingSleepJobs.remove(id)?.cancel()
    }

    private fun cancelSessionWake(id: String) {
        sessionWakeJobs.remove(id)?.cancel()
    }

    private suspend fun sleepTv(tv: TvData, label: String) {
        Log.i("MainVM", "$label ${tv.nama}: sending SLEEP_TV")
        val sent = TvHubManager.sendSleepTv(tv)
        if (sent) {
            repeat(6) {
                delay(500L)
                val conn = TvHubManager.connectionFor(tv)
                if (conn == null || !conn.online || conn.screenOn == false) {
                    Log.i("MainVM", "$label ${tv.nama}: SLEEP_TV confirmed")
                    return
                }
            }
            Log.w("MainVM", "$label ${tv.nama}: SLEEP_TV tanpa konfirmasi, fallback power")
        }
        val result = TvRemotePower.send(tv.ip, tv.certPem, tv.keyPem, false)
        if (result.ok) TvHubManager.noteScreenState(tv, false)
        Log.i("MainVM", "$label ${tv.nama}: power sent=${result.ok} ${result.message}")
    }

    private fun repairLegacyTransactions() {
        if (StorageUtil.isRepairV1Done()) return
        val original = _state.value.transaksiList
        if (original.isEmpty()) {
            StorageUtil.markRepairV1Done()
            return
        }
        try {
            StorageUtil.backupBeforeRepair()
            val passthrough = original.filter { it.kota == "KASIR F&B" || it.paket == "BATAL" }
            val tvRows = original.filterNot { it.kota == "KASIR F&B" || it.paket == "BATAL" }
            val repaired = mutableListOf<Transaksi>()
            repaired.addAll(passthrough)

            tvRows.groupBy { it.kota }.forEach { (_, rows) ->
                val sorted = rows.sortedWith(compareBy<Transaksi>({ it.waktu }, { it.id }))
                var group = mutableListOf<Transaksi>()
                var previousTotal = 0

                fun flushGroup() {
                    if (group.isEmpty()) return
                    val first = group.first()
                    val last = group.last()
                    val mergedPesanan = mutableMapOf<String, Int>()
                    val mergedHarga = mutableMapOf<String, Int>()
                    group.forEach { row ->
                        row.pesanan.forEach { (name, qty) ->
                            mergedPesanan[name] = (mergedPesanan[name] ?: 0) + qty
                        }
                        row.pesananHarga.forEach { (name, price) ->
                            if (!mergedHarga.containsKey(name)) mergedHarga[name] = price
                        }
                    }
                    val fnb = mergedPesanan.entries.sumOf { (name, qty) ->
                        (mergedHarga[name] ?: 0) * qty
                    }
                    val total = maxOf(last.total, fnb)
                    repaired.add(
                        first.copy(
                            waktu = first.waktu.ifBlank { last.waktu },
                            kasir = last.kasir.ifBlank { first.kasir },
                            paket = first.paket.ifBlank { last.paket },
                            pesanan = mergedPesanan,
                            pesananHarga = mergedHarga,
                            total = total,
                            paketHarga = (total - fnb).coerceAtLeast(0),
                            sudahBayar = last.sudahBayar,
                            metode = group.lastOrNull { it.metode.isNotBlank() }?.metode ?: last.metode,
                            tvJenisPs = first.tvJenisPs.ifBlank { last.tvJenisPs },
                        )
                    )
                    group = mutableListOf()
                    previousTotal = 0
                }

                sorted.forEach { row ->
                    val previous = group.lastOrNull()
                    val startLike = previous != null && previous.sudahBayar && !row.sudahBayar &&
                        row.paketHarga > 0 &&
                        row.total <= row.paketHarga + row.fnbTotal()
                    if (group.isNotEmpty() && (row.total < previousTotal || startLike)) {
                        flushGroup()
                    }
                    group.add(row)
                    previousTotal = row.total
                }
                flushGroup()
            }

            _state.value = _state.value.copy(transaksiList = repaired)
            StorageUtil.saveTransaksi(repaired)
            StorageUtil.markRepairV1Done()
            Log.i("MainVM", "repairLegacyTransactions: ${original.size} -> ${repaired.size} rows")
        } catch (e: Exception) {
            Log.e("MainVM", "repairLegacyTransactions failed: ${e.message}", e)
        }
    }

    private fun scheduleExpiredPaidSleeps() {
        _state.value.tvList
            .filter { it.sudahBayar && !it.timerActive && !it.bebas && it.timerDurasi > 0 && it.sisaDetik <= 0 }
            .forEach { scheduleSleepAfter(it, 30_000L) }
    }

    private fun scheduleSleepAfter(tv: TvData, delayMs: Long) {
        cancelAutoSleep(tv.id)
        Log.i("MainVM", "scheduleSleepAfter ${tv.nama}: delay=${delayMs}ms")
        pendingSleepJobs[tv.id] = viewModelScope.launch(Dispatchers.IO) {
            delay(delayMs)
            val current = _state.value.tvList.firstOrNull { it.id == tv.id } ?: return@launch
            Log.i("MainVM", "auto-sleep check ${current.nama}: timerActive=${current.timerActive} bebas=${current.bebas} paket='${current.paketAktif}'")
            if (current.timerActive || current.bebas || current.paketAktif.isNotBlank()) return@launch
            sleepTv(current, "Auto-sleep")
        }
    }

    private fun wakeForSession(tv: TvData) {
        if (!tv.paired || tv.certPem.isBlank() || tv.keyPem.isBlank()) return
        val conn = TvHubManager.connectionFor(tv)
        if (conn?.online == true && conn.screenOn != false) return
        cancelSessionWake(tv.id)
        sessionWakeJobs[tv.id] = viewModelScope.launch(Dispatchers.IO) {
            val result = TvRemotePower.send(tv.ip, tv.certPem, tv.keyPem, true)
            if (result.ok) TvHubManager.noteScreenState(tv, true)
            Log.i("MainVM", "Wake ${tv.nama} for session: sent=${result.ok} ${result.message}")
            repeat(30) {
                delay(500L)
                val current = _state.value.tvList.firstOrNull { it.id == tv.id } ?: return@launch
                if (!current.timerActive && !current.bebas) return@launch
                if (TvHubManager.connectionFor(current)?.online == true) {
                    TvHubManager.sendStartTimer(current)
                    return@launch
                }
            }
        }
    }

    private fun startSessionOnTv(tv: TvData): Boolean {
        cancelAutoSleep(tv.id)
        val sent = TvHubManager.sendStartTimer(tv)
        wakeForSession(tv)
        return sent
    }

    fun tvStart(id: String): Boolean {
        val tv = _state.value.tvList.firstOrNull { it.id == id } ?: return false
        return startSessionOnTv(tv)
    }

    fun tvStop(id: String, sleep: Boolean = false): Boolean {
        val tv = _state.value.tvList.firstOrNull { it.id == id } ?: return false
        val wasBebas = tv.bebas
        cancelAutoSleep(id)
        cancelSessionWake(id)
        val stopped = tv.copy(
            timerActive = false,
            bebas = false,
            sisaDetik = 0L,
            timerStart = 0L,
            timerDurasi = 0L,
            paketAktif = "",
            paketHarga = 0,
            totalPesanan = 0,
            trxId = "",
            pesanan = emptyMap(),
            pesananHarga = emptyMap(),
            bebasMulai = 0L,
            bebasHargaPerJam = 0,
            bebasPesananTotal = 0,
            cancelBatas = 0L,
            sudahBayar = false,
        )
        val list = _state.value.tvList.map { if (it.id == id) stopped else it }
        val dismissed = _state.value.habisDismissedIds - id
        _state.value = _state.value.copy(tvList = list, habisDismissedIds = dismissed)
        StorageUtil.saveTvList(list)
        StorageUtil.saveHabisDismissedIds(dismissed)
        refreshHubConfig()
        val sent = TvHubManager.sendStopTimer(tv)
        if (sleep || wasBebas) {
            viewModelScope.launch(Dispatchers.IO) {
                delay(800L)
                val current = _state.value.tvList.firstOrNull { it.id == id } ?: return@launch
                if (current.timerActive || current.bebas) return@launch
                sleepTv(current, "Sleep after stop")
            }
        }
        refreshTimerService()
        try { syncToCloud() } catch (e: Exception) { Log.e("MainVM", "syncToCloud after tvStop: ${e.message}") }
        return sent
    }

    fun tvSync(id: String): Boolean {
        val tv = _state.value.tvList.firstOrNull { it.id == id } ?: return false
        return TvHubManager.sendSyncTimer(tv)
    }

    fun tvLock(id: String): Boolean {
        val tv = _state.value.tvList.firstOrNull { it.id == id } ?: return false
        return TvHubManager.sendLockScreen(tv)
    }

    fun tvUnlock(id: String): Boolean {
        val tv = _state.value.tvList.firstOrNull { it.id == id } ?: return false
        cancelAutoSleep(id)
        return TvHubManager.sendUnlockScreen(tv)
    }

    fun tvApplyConfig(id: String): Boolean {
        val tv = _state.value.tvList.firstOrNull { it.id == id } ?: return false
        return TvHubManager.applyTvConfig(tv)
    }

    fun tvMessage(id: String, pesan: String, durasi: Int = 5): Boolean {
        val tv = _state.value.tvList.firstOrNull { it.id == id } ?: return false
        return TvHubManager.sendMessage(tv, pesan, durasi)
    }

    fun cancelSession(id: String): AuthResult {
        val tv = _state.value.tvList.firstOrNull { it.id == id }
            ?: return AuthResult(false, "TV tidak ditemukan")
        if (!tv.timerActive && !tv.bebas && tv.paketAktif.isBlank()) {
            return AuthResult(false, "Tidak ada sesi aktif")
        }
        if (tv.sudahBayar) {
            return AuthResult(false, "Sesi sudah dibayar, tidak bisa dibatalkan")
        }
        val startedAt = if (tv.bebas) tv.bebasMulai else tv.timerStart
        if (startedAt <= 0L) {
            return AuthResult(false, "Waktu mulai sesi tidak ditemukan")
        }
        val elapsedMs = System.currentTimeMillis() - startedAt
        if (elapsedMs > 10 * 60_000L) {
            return AuthResult(false, "Sesi sudah berjalan lebih dari 10 menit")
        }

        val txId = "TRX-${System.currentTimeMillis()}"
        val transaction = Transaksi(
            id = txId,
            waktu = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                .format(java.util.Date()),
            kasir = _state.value.currentUser.ifBlank { "Kasir" },
            kota = tv.nama,
            paket = "BATAL",
            pesanan = emptyMap(),
            total = 0,
            paketHarga = 0,
            pesananHarga = emptyMap(),
            tvJenisPs = tv.jenisPs,
            sudahBayar = true,
            metode = "BATAL",
        )

        val stopped = tv.copy(
            timerActive = false,
            bebas = false,
            sisaDetik = 0L,
            timerStart = 0L,
            timerDurasi = 0L,
            paketAktif = "",
            paketHarga = 0,
            totalPesanan = 0,
            trxId = "",
            pesanan = emptyMap(),
            pesananHarga = emptyMap(),
            bebasMulai = 0L,
            bebasHargaPerJam = 0,
            bebasPesananTotal = 0,
            cancelBatas = 0L,
            sudahBayar = false,
        )
        val list = _state.value.tvList.map { if (it.id == id) stopped else it }
        val dismissed = _state.value.habisDismissedIds - id
        _state.value = _state.value.copy(tvList = list, habisDismissedIds = dismissed)
        StorageUtil.saveTvList(list)
        StorageUtil.saveHabisDismissedIds(dismissed)
        refreshHubConfig()
        cancelAutoSleep(id)
        cancelSessionWake(id)
        TvHubManager.sendStopTimer(tv)
        val trxList = _state.value.transaksiList.toMutableList()
        var trxIdx = -1
        if (tv.trxId.isNotBlank()) {
            trxIdx = trxList.indexOfFirst { it.id == tv.trxId }
        }
        if (trxIdx < 0) {
            trxIdx = trxList.indexOfLast { it.kota == tv.nama && it.paket != "BATAL" }
        }
        if (trxIdx >= 0) {
            trxList[trxIdx] = trxList[trxIdx].copy(
                paket = "BATAL",
                pesanan = emptyMap(),
                total = 0,
                paketHarga = 0,
                pesananHarga = emptyMap(),
                sudahBayar = true,
                metode = "BATAL",
                kasir = _state.value.currentUser.ifBlank { trxList[trxIdx].kasir },
            )
            _state.value = _state.value.copy(transaksiList = trxList)
            StorageUtil.saveTransaksi(trxList)
            addActivityLog("Transaksi BATAL Rp0 di ${tv.nama}")
            try { syncToCloud() } catch (e: Exception) { Log.e("MainVM", "syncToCloud after cancelSession: ${e.message}") }
        } else {
            tambahTransaksi(transaction)
        }
        refreshTimerService()
        return AuthResult(true, "Sesi dibatalkan. Laporan Rp 0.")
    }

    fun tvShowMedia(id: String, local: String, url: String = ""): Boolean {
        val tv = _state.value.tvList.firstOrNull { it.id == id } ?: return false
        return TvHubManager.sendShowMedia(tv, "video", local, url)
    }

    fun tvHideMedia(id: String): Boolean {
        val tv = _state.value.tvList.firstOrNull { it.id == id } ?: return false
        return TvHubManager.sendHideMedia(tv)
    }

    fun tvSetLockBg(id: String, local: String, url: String = ""): Boolean {
        val tv = _state.value.tvList.firstOrNull { it.id == id } ?: return false
        return TvHubManager.sendUpdateLockBg(tv, local, url)
    }

    fun uploadTvMedia(
        id: String,
        kind: String,
        uri: Uri,
        onDone: (Boolean, String) -> Unit = { _, _ -> },
    ) {
        val tv = _state.value.tvList.firstOrNull { it.id == id }
        if (tv == null) {
            onDone(false, "TV tidak ditemukan")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = TvMediaStore.saveVideo(getApplication(), tv.id, kind, uri)
                TvMediaUploader.upload(tv, file, kind) { ok, message ->
                    if (ok) {
                        if (kind == "lockscreen") {
                            updateTV(tv.id, mapOf("lockscreenVideoPath" to file.absolutePath))
                            TvHubManager.sendUpdateLockBg(
                                tv.copy(lockscreenVideoPath = file.absolutePath),
                                local = "lockscreen.mp4",
                            )
                        } else {
                            updateTV(tv.id, mapOf("promoVideoPath" to file.absolutePath))
                            TvHubManager.sendShowMedia(
                                tv.copy(promoVideoPath = file.absolutePath),
                                mediaType = "video",
                                local = "promo.mp4",
                            )
                        }
                    }
                    onDone(ok, message)
                }
            } catch (e: Exception) {
                Log.e("MainVM", "uploadTvMedia($kind) gagal: ${e.message}", e)
                onDone(false, e.message ?: "Gagal menyimpan media")
            }
        }
    }

    fun dismissWaktuHabis(id: String) {
        val ids = _state.value.habisDismissedIds + id
        _state.value = _state.value.copy(habisDismissedIds = ids)
        StorageUtil.saveHabisDismissedIds(ids)
    }

    private fun refreshTimerService() {
        val app = getApplication<Application>()
        val activeTvs = _state.value.tvList.filter { it.timerActive || it.bebas }
        if (activeTvs.isEmpty() && !com.billingps.aptv.TimerService.isRunning()) return
        val intent = Intent(app, com.billingps.aptv.TimerService::class.java).apply {
            action = com.billingps.aptv.TimerService.ACTION_START
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                app.startForegroundService(intent)
            } else {
                app.startService(intent)
            }
        } catch (e: Exception) {
            Log.e("MainVM", "refreshTimerService failed: ${e.message}")
        }
    }

    fun hapusTV(id: String) {
        try {
            val deleted = _state.value.tvList.firstOrNull { it.id == id }
            val list = _state.value.tvList.filter { it.id != id }
            val dismissed = _state.value.habisDismissedIds - id
            _state.value = _state.value.copy(
                tvList = list,
                clientStatus = _state.value.clientStatus - id,
                habisDismissedIds = dismissed,
            )
            StorageUtil.saveTvList(list)
            StorageUtil.saveHabisDismissedIds(dismissed)
            StorageUtil.clearTimerExpiredDismissed(id)
            if (deleted != null) {
                try {
                    TvMediaStore.deleteForTv(
                        getApplication<Application>(),
                        deleted.id,
                        listOf(deleted.promoVideoPath, deleted.lockscreenVideoPath),
                    )
                } catch (e: Exception) {
                    Log.e("MainVM", "hapusTV media cleanup: ${e.message}")
                }
                if (list.none { it.paired }) {
                    StorageUtil.clearTvCerts()
                } else {
                    StorageUtil.clearTvCertsIfMatches(deleted.certPem, deleted.keyPem)
                }
            }
            refreshHubConfig()
            refreshClientStatusListeners()
            refreshTimerService()
            try { syncToCloud() } catch (e: Exception) { Log.e("MainVM", "syncToCloud after hapusTV: ${e.message}") }
            val username = _state.value.currentUser
            if (username.isNotBlank()) {
                viewModelScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            cloudRepo.deleteTvCommandDoc(username, id)
                        }
                    } catch (e: Exception) {
                        Log.e("MainVM", "deleteTvCommandDoc after hapusTV: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "hapusTV error: ${e.message}", e)
        }
    }

    fun pindahTV(sourceId: String, targetId: String) {
        try {
            val list = _state.value.tvList.map { tv ->
                if (tv.id == sourceId) {
                    tv.copy(
                        paketAktif = "", sisaDetik = 0L,
                        timerStart = 0L, timerDurasi = 0L, timerActive = false,
                        bebas = false, bebasMulai = 0L, bebasHargaPerJam = 0, bebasPesananTotal = 0,
                        paketHarga = 0, totalPesanan = 0,
                        cancelBatas = 0L,
                    )
                } else if (tv.id == targetId) {
                    val src = _state.value.tvList.find { it.id == sourceId } ?: return@map tv
                    val now = System.currentTimeMillis()
                    tv.copy(
                        paketAktif = src.paketAktif, sisaDetik = src.sisaDetik,
                        timerStart = now, timerDurasi = src.timerDurasi, timerActive = src.timerActive,
                        bebas = src.bebas, bebasMulai = src.bebasMulai, bebasHargaPerJam = src.bebasHargaPerJam, bebasPesananTotal = src.bebasPesananTotal,
                        paketHarga = src.paketHarga, totalPesanan = src.totalPesanan,
                        cancelBatas = src.cancelBatas,
                        sudahBayar = src.sudahBayar,
                    )
                } else tv
            }
            _state.value = _state.value.copy(tvList = list)
            StorageUtil.saveTvList(list)
            refreshHubConfig()
            _state.value.tvList.firstOrNull { it.id == sourceId }?.let { TvHubManager.sendStopTimer(it) }
            _state.value.tvList.firstOrNull { it.id == targetId }?.let {
                if (it.timerActive || it.bebas) TvHubManager.sendStartTimer(it) else TvHubManager.sendStopTimer(it)
            }
            refreshTimerService()
            try { syncToCloud() } catch (e: Exception) { Log.e("MainVM", "syncToCloud after pindahTV: ${e.message}") }
        } catch (e: Exception) {
            Log.e("MainViewModel", "pindahTV error: ${e.message}", e)
        }
    }

    fun playVideoOnTv(username: String, tvId: String, videoUrl: String) {
        cloudRepo.sendPlayVideo(username, tvId, videoUrl)    }

    fun playCachedMediaOnTv(username: String, tvId: String) {
        cloudRepo.sendPlayCachedMedia(username, tvId)
    }

    fun cacheMediaOnTv(username: String, tvId: String, mediaUrl: String) {
        cloudRepo.sendCacheMedia(username, tvId, mediaUrl)
    }

    fun sendWarningVideoToTv(username: String, tvId: String, videoUrl: String) {
        cloudRepo.sendWarningVideo(username, tvId, videoUrl)
    }

    // ── Transaksi ──────────────────────────────────────────
    fun tambahTransaksi(t: Transaksi) {
        val tx = if (t.id.isNotBlank()) t else t.copy(id = "tx_${System.currentTimeMillis()}")
        val isNew = _state.value.transaksiList.none { it.id == tx.id }
        val list = (_state.value.transaksiList + tx)
        _state.value = _state.value.copy(transaksiList = list)
        StorageUtil.saveTransaksi(list)
        if (isNew) deductStockForOrder(tx.pesanan)
        addActivityLog("Transaksi ${tx.paket} Rp${tx.total} di ${tx.kota}")
        refreshTimerService()
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
        try { syncToCloud() } catch (e: Exception) { Log.e("MainVM", "syncToCloud after bersihkanTransaksi: ${e.message}") }
    }

    fun upsertTransaksi(t: Transaksi) {
        val tx = if (t.id.isNotBlank()) t else t.copy(id = "tx_${System.currentTimeMillis()}")
        val list = _state.value.transaksiList.toMutableList()
        val idx = list.indexOfFirst { it.id == tx.id }
        val previousPesanan = if (idx >= 0) list[idx].pesanan else emptyMap()
        if (idx >= 0) {
            list[idx] = tx
        } else {
            list.add(tx)
        }
        _state.value = _state.value.copy(transaksiList = list)
        StorageUtil.saveTransaksi(list)
        val stockDelta = mutableMapOf<String, Int>()
        tx.pesanan.forEach { (name, qty) ->
            val diff = qty - (previousPesanan[name] ?: 0)
            if (diff > 0) stockDelta[name] = diff
        }
        if (stockDelta.isNotEmpty()) deductStockForOrder(stockDelta)
        addActivityLog("Transaksi ${tx.paket} Rp${tx.total} di ${tx.kota}")
        refreshTimerService()
        try {
            Log.i("MainVM", "auto-sync triggered after upsertTransaksi id=${tx.id}")
            syncToCloud()
        } catch (ex: Exception) { Log.i("MainVM", "auto-sync failed to start: ${ex.message}") }
    }

    fun setTransaksiSudahBayar(kota: String, sudahBayar: Boolean, metode: String = "", id: String = "") {
        val list = _state.value.transaksiList.toMutableList()
        val idx = if (id.isNotBlank()) {
            list.indexOfFirst { it.id == id }
        } else {
            list.indexOfLast { it.kota == kota }
        }
        if (idx >= 0) {
            list[idx] = list[idx].copy(
                sudahBayar = sudahBayar,
                metode = if (metode.isNotBlank()) metode else list[idx].metode,
                kasir = if (sudahBayar) _state.value.currentUser else list[idx].kasir,
            )
            _state.value = _state.value.copy(transaksiList = list)
            StorageUtil.saveTransaksi(list)
            try { syncToCloud() } catch (e: Exception) { Log.e("MainVM", "syncToCloud after setTransaksiSudahBayar: ${e.message}") }
        }
    }

    fun settleTransaksi(
        kota: String,
        total: Int,
        paketHarga: Int,
        metode: String,
        sudahBayar: Boolean = true,
        id: String = "",
        tvId: String = "",
        pesanan: Map<String, Int>? = null,
        pesananHarga: Map<String, Int>? = null,
    ) {
        val list = _state.value.transaksiList.toMutableList()
        var idx = -1
        if (id.isNotBlank()) {
            idx = list.indexOfFirst { it.id == id }
        }
        if (idx < 0 && tvId.isNotBlank()) {
            val trxId = _state.value.tvList.firstOrNull { it.id == tvId }?.trxId.orEmpty()
            if (trxId.isNotBlank()) idx = list.indexOfFirst { it.id == trxId }
        }
        if (idx < 0) idx = list.indexOfLast { it.kota == kota }
        if (idx >= 0) {
            val old = list[idx]
            list[idx] = old.copy(
                total = total.coerceAtLeast(0),
                paketHarga = paketHarga.coerceAtLeast(0),
                metode = metode,
                sudahBayar = sudahBayar,
                kasir = if (sudahBayar) _state.value.currentUser else old.kasir,
                pesanan = pesanan ?: old.pesanan,
                pesananHarga = pesananHarga ?: old.pesananHarga,
            )
            _state.value = _state.value.copy(transaksiList = list)
            StorageUtil.saveTransaksi(list)
            try { syncToCloud() } catch (e: Exception) { Log.e("MainVM", "syncToCloud after settleTransaksi: ${e.message}") }
        }
    }

    // ── Harga ──────────────────────────────────────────────
    fun saveHarga(paketMain: Map<String, Map<String, Int>>, paketDurasi: Map<String, Int>,
                  menuMakanan: Map<String, Int>, menuMinuman: Map<String, Int>) {
        _state.value = _state.value.copy(
            paketMain = paketMain, paketDurasi = paketDurasi,
            menuMakanan = menuMakanan, menuMinuman = menuMinuman,
        )
        StorageUtil.saveHarga(paketMain, paketDurasi, menuMakanan, menuMinuman)
        StorageUtil.saveJenisPsList(_state.value.jenisPsList)
    }

    fun saveStok(makanan: Map<String, Int>, minuman: Map<String, Int>) {
        _state.value = _state.value.copy(
            stokMakanan = makanan,
            stokMinuman = minuman,
        )
        StorageUtil.saveStok(makanan, minuman)
    }

    private fun deductStockForOrder(items: Map<String, Int>) {
        if (items.isEmpty()) return
        val makanan = _state.value.stokMakanan.toMutableMap()
        val minuman = _state.value.stokMinuman.toMutableMap()
        var changed = false
        items.forEach { (name, qty) ->
            if (qty <= 0) return@forEach
            when {
                makanan.containsKey(name) -> {
                    makanan[name] = ((makanan[name] ?: 0) - qty).coerceAtLeast(0)
                    changed = true
                }
                minuman.containsKey(name) -> {
                    minuman[name] = ((minuman[name] ?: 0) - qty).coerceAtLeast(0)
                    changed = true
                }
            }
        }
        if (changed) saveStok(makanan, minuman)
    }

    fun jualFnb(
        itemsMakanan: Map<String, Int>,
        itemsMinuman: Map<String, Int>,
        metode: String = "Tunai",
    ): FnbSaleResult {
        val makanan = itemsMakanan.filterValues { it > 0 }
        val minuman = itemsMinuman.filterValues { it > 0 }
        if (makanan.isEmpty() && minuman.isEmpty()) {
            return FnbSaleResult(false, "Pilih menu terlebih dahulu")
        }

        for ((name, qty) in makanan) {
            val stock = _state.value.stokMakanan[name]
            if (stock != null && qty > stock) {
                return FnbSaleResult(false, "Stok $name tidak cukup (sisa $stock)")
            }
        }
        for ((name, qty) in minuman) {
            val stock = _state.value.stokMinuman[name]
            if (stock != null && qty > stock) {
                return FnbSaleResult(false, "Stok $name tidak cukup (sisa $stock)")
            }
        }

        val pesanan = mutableMapOf<String, Int>()
        val pesananHarga = mutableMapOf<String, Int>()
        var total = 0
        makanan.forEach { (name, qty) ->
            val price = _state.value.menuMakanan[name] ?: 0
            pesanan[name] = (pesanan[name] ?: 0) + qty
            pesananHarga[name] = price
            total += price * qty
        }
        minuman.forEach { (name, qty) ->
            val price = _state.value.menuMinuman[name] ?: 0
            pesanan[name] = (pesanan[name] ?: 0) + qty
            pesananHarga[name] = price
            total += price * qty
        }

        val txId = "TRX-${System.currentTimeMillis()}"
        val transaction = Transaksi(
            id = txId,
            waktu = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                .format(java.util.Date()),
            kasir = _state.value.currentUser.ifBlank { "Kasir" },
            kota = "KASIR F&B",
            paket = "F&B",
            pesanan = pesanan,
            total = total,
            paketHarga = 0,
            pesananHarga = pesananHarga,
            tvJenisPs = "",
            sudahBayar = true,
            metode = metode.ifBlank { "Tunai" },
        )
        tambahTransaksi(transaction)
        return FnbSaleResult(true, "Penjualan F&B tersimpan", txId)
    }

    // ── Grup ────────────────────────────────────────────────
    fun tambahGrup(nama: String) {
        val groups = _state.value.jenisPsList.toMutableList()
        if (groups.contains(nama)) return
        groups.add(nama)
        val newPaketMain = _state.value.paketMain.toMutableMap()
        val defaultPS = mapOf(
            "15 Menit" to 5000, "30 Menit" to 10000, "1 Jam" to 15000,
            "2 Jam" to 25000, "3 Jam" to 35000,
        )
        newPaketMain[nama] = defaultPS
        _state.value = _state.value.copy(jenisPsList = groups, paketMain = newPaketMain)
        StorageUtil.saveJenisPsList(groups)
        StorageUtil.saveHarga(_state.value.paketMain, _state.value.paketDurasi, _state.value.menuMakanan, _state.value.menuMinuman)
    }

    fun renameGrup(lama: String, baru: String) {
        if (lama == baru || baru.isBlank()) return
        val groups = _state.value.jenisPsList.toMutableList()
        val idx = groups.indexOf(lama)
        if (idx < 0) return
        groups[idx] = baru
        val newPaketMain = _state.value.paketMain.toMutableMap()
        newPaketMain[baru] = newPaketMain.remove(lama) ?: emptyMap()
        val newTvList = _state.value.tvList.map { if (it.jenisPs == lama) it.copy(jenisPs = baru) else it }
        _state.value = _state.value.copy(jenisPsList = groups, paketMain = newPaketMain, tvList = newTvList)
        StorageUtil.saveJenisPsList(groups)
        StorageUtil.saveTvList(newTvList)
        StorageUtil.saveHarga(_state.value.paketMain, _state.value.paketDurasi, _state.value.menuMakanan, _state.value.menuMinuman)
    }

    fun hapusGrup(nama: String) {
        val groups = _state.value.jenisPsList.toMutableList()
        if (!groups.remove(nama)) return
        val newPaketMain = _state.value.paketMain.toMutableMap()
        newPaketMain.remove(nama)
        val fallback = groups.firstOrNull() ?: "PS3"
        val newTvList = _state.value.tvList.map { if (it.jenisPs == nama) it.copy(jenisPs = fallback) else it }
        _state.value = _state.value.copy(jenisPsList = groups, paketMain = newPaketMain, tvList = newTvList)
        StorageUtil.saveJenisPsList(groups)
        StorageUtil.saveTvList(newTvList)
        StorageUtil.saveHarga(_state.value.paketMain, _state.value.paketDurasi, _state.value.menuMakanan, _state.value.menuMinuman)
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
            val durasiHari = LicenseCatalog.durationDays(paket)
            val payloadJson = try { org.json.JSONObject(record.payload) } catch (e: Exception) { null }
            val signedMaxTv = payloadJson?.optInt("q", -1) ?: -1
            val signedClient = if (payloadJson?.has("c") == true) payloadJson.optBoolean("c", true) else null
            val signedMode = payloadJson?.optString("o", "")?.takeIf { it.isNotBlank() }
            val signedExpiry = payloadJson?.optString("e", "").orEmpty()

            val localLic = StorageUtil.loadLicense()
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val today = java.util.Date()
            val oldActive = localLic.status == "active" && localLic.expiresAt.isNotEmpty()
            val oldExpiryDate = if (oldActive) {
                try { sdf.parse(localLic.expiresAt) } catch (e: Exception) { null }
            } else null
            val oldStillValid = oldExpiryDate != null && oldExpiryDate.after(today)

            val signedExpiryDate = if (signedExpiry.isNotBlank()) {
                try { sdf.parse(signedExpiry) } catch (e: Exception) { null }
            } else null
            val expiresDate = when {
                signedExpiryDate != null && signedMaxTv >= 0 ->
                    if (oldStillValid && oldExpiryDate != null && oldExpiryDate.after(signedExpiryDate)) oldExpiryDate
                    else signedExpiryDate
                signedExpiryDate != null && !oldStillValid -> signedExpiryDate
                oldStillValid -> {
                    val cal = java.util.Calendar.getInstance()
                    cal.time = oldExpiryDate!!
                    cal.add(java.util.Calendar.DAY_OF_YEAR, durasiHari)
                    cal.time
                }
                else -> {
                    val cal = java.util.Calendar.getInstance()
                    cal.add(java.util.Calendar.DAY_OF_YEAR, durasiHari)
                    cal.time
                }
            }
            val expires = sdf.format(expiresDate)

            val targetMaxTv = when {
                signedMaxTv >= 0 -> signedMaxTv
                record.maxTv > 0 -> record.maxTv
                else -> LicenseCatalog.legacyMaxTv(paket)
            }
            val oldUnlimited = oldActive && localLic.maxTv == 0 && localLic.promoAddTv == 0
            val maxTv = when {
                oldUnlimited -> 0 // never downgrade a legacy unlimited license
                signedMaxTv >= 0 && targetMaxTv > 0 -> maxOf(targetMaxTv, localLic.maxTv)
                else -> maxOf(targetMaxTv, if (oldActive) localLic.maxTv else 0)
            }
            val promoAddTv = if (signedMaxTv >= 0) 0 else localLic.promoAddTv
            val clientEnabled = signedClient
                ?: record.clientEnabled
                ?: LicenseCatalog.clientEnabled(paket)

            val ls = LicenseStatus(
                status = "active",
                pesan = "✅ Lisensi $paket aktif hingga $expires",
                expiresAt = expires,
                maxTv = maxTv,
                promoAddTv = promoAddTv,
                paket = paket,
                clientEnabled = clientEnabled,
            )
            _state.value = _state.value.copy(licenseStatus = ls, trialBatas = 0L, maxTv = maxTv)
            StorageUtil.saveLicense(ls)
            refreshHubConfig()

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
                        "paket" to paket,
                        "maxTv" to maxTv,
                        "maxPc" to maxTv,
                        "promoAddTv" to promoAddTv,
                        "clientEnabled" to clientEnabled,
                        "mode" to (signedMode ?: LicenseCatalog.MODE_NEW),
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

    // ── Invoices ────────────────────────────────────────────
    private fun generateInvoiceId(): String {
        val sdf = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
        val datePart = sdf.format(java.util.Date())
        val random = (1..4).map { "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".random() }.joinToString("")
        return "INV-$datePart-$random"
    }

    fun buatInvoice(
        paket: String,
        harga: Int,
        jumlahTv: Int = 1,
        mode: String = LicenseCatalog.MODE_NEW,
    ): Invoice {
        val id = generateInvoiceId()
        val username = _state.value.currentUser
        val email = _state.value.users[username]?.email ?: ""
        val plan = LicenseCatalog.find(paket)
        val qty = jumlahTv.coerceIn(1, 99)
        val current = _state.value.licenseStatus
        val requestedMode = LicenseCatalog.normalizeMode(mode)
        val normalizedMode = if (current.status == "active" && requestedMode == LicenseCatalog.MODE_NEW) {
            LicenseCatalog.MODE_ADD
        } else requestedMode
        val currentMaxTv = if (current.status == "active") current.maxTv else 0
        val nativePrice = plan?.unitPrice?.times(qty) ?: harga
        val inv = Invoice(
            id = id,
            username = username,
            email = email,
            paket = plan?.code ?: paket,
            harga = nativePrice,
            status = "PENDING",
            dibuat = System.currentTimeMillis(),
            jumlahTv = if (plan != null) qty else 0,
            mode = if (plan != null) normalizedMode else "",
            baseMaxTv = if (plan != null) currentMaxTv else 0,
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
        _state.value = _state.value.copy(invoices = newList, statusMessage = "Mengirim bukti...")
        _state.value = _state.value.copy(pendingInvoiceCount = newList.count { it.status == "WAITING_CONFIRMATION" })
        StorageUtil.saveInvoices(newList)
        val updated = newList.firstOrNull { it.id == invoiceId }
        if (updated != null) {
            viewModelScope.launch {
                try {
                    cloudRepo.saveInvoiceToCloud(updated)
                    cloudRepo.saveLicenseRequestNotification(updated.username, updated.paket, updated.harga, updated.id)
                    _state.value = _state.value.copy(statusMessage = "Bukti terkirim! Menunggu konfirmasi RR License Generator.")
                } catch (e: Exception) {
                    Log.e("MainVM", "saveInvoiceToCloud in uploadBukti: ${e.message}")
                    _state.value = _state.value.copy(
                        statusMessage = "Gagal mengirim bukti: ${e.message}. Coba lagi.",
                    )
                    // Revert status locally so user can retry
                    val revertList = _state.value.invoices.map {
                        if (it.id == invoiceId) it.copy(status = "PENDING", buktiBase64 = "") else it
                    }
                    _state.value = _state.value.copy(invoices = revertList)
                    StorageUtil.saveInvoices(revertList)
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

    fun revokeLicense(docId: String, licenseUsername: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = cloudRepo.revokeLicense(docId)
            if (ok) {
                addActivityLog("Lisensi direvoke (docId: $docId, user: $licenseUsername)")
                sendPushNotification("⚠️ Lisensi Dicabut", "Lisensi $docId telah dicabut")
                cloudRepo.writeLicenseStatusToUserDoc(licenseUsername, mapOf(
                    "status" to "revoked",
                    "pesan" to "❌ Lisensi dicabut oleh admin",
                    "expiresAt" to "",
                ))
            }
            onResult(ok)
        }
    }

    private fun fmtRpInt(angka: Int): String = String.format("%,d", angka)

    fun getMyInvoices(): List<Invoice> = _state.value.invoices.filter { it.username == _state.value.currentUser }.sortedByDescending { it.dibuat }

    fun gantiPassword(username: String, newPassword: String): Boolean {
        // Only admin can change passwords (including their own)
        if (_state.value.currentRole != "admin") return false
        if (newPassword.length < 6) return false
        if (!newPassword.any { it.isUpperCase() }) return false
        if (!newPassword.any { it.isDigit() }) return false
        val users = _state.value.users.toMutableMap()
        val existing = users[username] ?: return false
        val salt = if (existing.salt.isNotEmpty()) existing.salt else generateSalt()
        val hash = pbkdf2Hash(newPassword, salt)
        users[username] = existing.copy(passwordHash = hash, salt = salt)
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
        // Only admin can configure SMTP
        if (_state.value.currentRole != "admin") return
        _state.value = _state.value.copy(smtp = cfg, needsSmtpSetup = false)
        StorageUtil.saveSmtp(cfg)
    }

    fun clearNeedsSmtpSetup() {
        _state.value = _state.value.copy(needsSmtpSetup = false)
    }

    fun savePrinter(address: String, name: String) {
        StorageUtil.putSecurePreference("printerAddress", address)
        StorageUtil.putSecurePreference("printerName", name)
        _state.value = _state.value.copy(printerAddress = address, printerName = name)
    }

    fun loadPrinter() {
        val addr = StorageUtil.getSecurePreference("printerAddress") ?: ""
        val name = StorageUtil.getSecurePreference("printerName") ?: ""
        _state.value = _state.value.copy(printerAddress = addr, printerName = name)
    }

    fun connectPrinter(address: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = com.billingps.aptv.utils.ThermalPrinter.connect(address)
            _state.value = _state.value.copy(
                printerConnected = result.success,
                statusMessage = result.message,
            )
        }
    }

    fun disconnectPrinter() {
        com.billingps.aptv.utils.ThermalPrinter.disconnect()
        _state.value = _state.value.copy(printerConnected = false)
    }

    fun printReceipt(transaksi: com.billingps.aptv.models.Transaksi, tvData: com.billingps.aptv.models.TvData) {
        if (!com.billingps.aptv.utils.ThermalPrinter.isConnected()) {
            _state.value = _state.value.copy(statusMessage = "Printer belum terhubung")
            return
        }
        val userData = _state.value.users[_state.value.currentUser]
        viewModelScope.launch(Dispatchers.IO) {
            com.billingps.aptv.utils.ThermalPrinter.printStruk(
                transaksi = transaksi,
                tvNama = tvData.nama.ifBlank { transaksi.kota },
                tvJenisPs = tvData.jenisPs.ifBlank { transaksi.tvJenisPs },
                kasir = transaksi.kasir.ifBlank { _state.value.currentUser },
                namaRental = userData?.namaRental ?: "",
                alamatRental = userData?.alamatRental ?: "",
                riwayatTransaksi = listOf(transaksi),
                menuMakanan = _state.value.menuMakanan,
                menuMinuman = _state.value.menuMinuman,
                csWhatsapp = "082180208414",
                bebas = tvData.bebas,
                durasiBebasDetik = tvData.timerDurasi,
            )
            _state.value = _state.value.copy(statusMessage = "Struk dicetak")
        }
    }

    fun updateDataRental(nama: String, alamat: String, wa: String) {
        val users = _state.value.users.toMutableMap()
        val userData = users[_state.value.currentUser] ?: return
        users[_state.value.currentUser] = userData.copy(namaRental = nama, alamatRental = alamat, whatsappRental = wa)
        _state.value = _state.value.copy(users = users)
        StorageUtil.saveUsers(users)
    }

    fun printTestPage() {
        if (!com.billingps.aptv.utils.ThermalPrinter.isConnected()) {
            _state.value = _state.value.copy(statusMessage = "Printer belum terhubung")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            com.billingps.aptv.utils.ThermalPrinter.printTestPage()
            _state.value = _state.value.copy(statusMessage = "Test print selesai")
        }
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

    private fun supabaseOwnerIdentity(): Pair<String, String>? {
        val snapshot = _state.value
        val current = snapshot.users[snapshot.currentUser]
        if (current != null && current.email.isNotBlank()) {
            return current.username to current.email
        }
        val admin = snapshot.users.values.firstOrNull { it.role == "admin" && it.email.isNotBlank() }
            ?: snapshot.users.values.firstOrNull { it.email.isNotBlank() }
        return admin?.let { it.username to it.email }
    }

    fun syncToCloud() {
        Log.i("MainVM", "trigger syncToCloud for user=${_state.value.currentUser}")
        viewModelScope.launch {
            _state.value = _state.value.copy(cloudStatus = "syncing", statusMessage = "Syncing to cloud...")
            val snapshot = _state.value
            val firestoreOk = try { withContext(Dispatchers.IO) { cloudRepo.syncAll(snapshot, snapshot.currentUser, snapshot.appVersionName) } } catch (t: Throwable) { Log.i("MainVM", "syncToCloud exception: ${t.message}"); false }
            val owner = supabaseOwnerIdentity()
            val supabaseOk = if (owner != null) {
                try {
                    withContext(Dispatchers.IO) {
                        supabaseRepo.syncTransactions(owner.second, owner.first, snapshot.transaksiList)
                    }
                } catch (t: Throwable) {
                    Log.i("MainVM", "supabase sync exception: ${t.message}")
                    false
                }
            } else {
                Log.i("MainVM", "supabase sync skipped: no owner email")
                true
            }
            val ok = firestoreOk && supabaseOk
            val now = if (ok) System.currentTimeMillis() else _state.value.cloudLastSync
            Log.i("MainVM", "syncToCloud result firestore=$firestoreOk supabase=$supabaseOk ok=$ok")
            _state.value = _state.value.copy(
                cloudStatus = if (ok) "connected" else "error",
                cloudLastSync = now,
                statusMessage = if (ok) "Cloud sync successful" else "Cloud sync failed"
            )
        }
    }

    override fun onCleared() {
        connectivityObserver.stop()
        firebaseAuth.removeAuthStateListener(deviceAuthListener)
        stopLicenseListener()
        stopPromoListener()
        stopNotifListener()
        stopDeviceSessionListener()
        super.onCleared()
    }
}
