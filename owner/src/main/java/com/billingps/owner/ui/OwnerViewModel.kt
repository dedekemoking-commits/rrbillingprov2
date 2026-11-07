package com.billingps.owner.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.billingps.owner.data.CashDrawerRequest
import com.billingps.owner.data.ClientStatus
import com.billingps.owner.data.FnbItemInput
import com.billingps.owner.data.GroupInput
import com.billingps.owner.data.OwnerCashDrawer
import com.billingps.owner.data.OwnerHubClient
import com.billingps.owner.data.OwnerHubSnapshot
import com.billingps.owner.data.OwnerPrefs
import com.billingps.owner.data.OwnerRepository
import com.billingps.owner.data.OwnerTransaction
import com.billingps.owner.data.OwnerTv
import com.billingps.owner.data.PackageInput
import com.billingps.owner.data.RentalInfo
import com.billingps.owner.data.RentalSnapshot
import com.billingps.owner.data.ReportPeriod
import com.billingps.owner.data.StockOpnameItem
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

data class OwnerUiState(
    val savedEmail: String = "",
    val savedUsername: String = "",
    val unlockMode: Boolean = false,
    val isLoggedIn: Boolean = false,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val username: String = "",
    val rental: RentalInfo = RentalInfo(),
    val tvs: List<OwnerTv> = emptyList(),
    val clientStatus: Map<String, ClientStatus> = emptyMap(),
    val hubConnected: Boolean = false,
    val hubRental: String = "",
    val hubHost: String = "",
    val hubStatus: Map<String, ClientStatus> = emptyMap(),
    val transactions: List<OwnerTransaction> = emptyList(),
    val firestoreTransactions: List<OwnerTransaction> = emptyList(),
    val menuMakanan: Map<String, Int> = emptyMap(),
    val menuMinuman: Map<String, Int> = emptyMap(),
    val stokMakanan: Map<String, Int> = emptyMap(),
    val stokMinuman: Map<String, Int> = emptyMap(),
    val jenisPsList: List<String> = emptyList(),
    val paketMain: Map<String, Map<String, Int>> = emptyMap(),
    val paketDurasi: Map<String, Int> = emptyMap(),
    val isSubmittingOpname: Boolean = false,
    val opnameNotice: String? = null,
    val isSubmittingFnbItem: Boolean = false,
    val fnbItemNotice: String? = null,
    val isSubmittingPackage: Boolean = false,
    val packageNotice: String? = null,
    val isSubmittingGroup: Boolean = false,
    val groupNotice: String? = null,
    val cashDrawer: OwnerCashDrawer = OwnerCashDrawer(),
    val cashDrawerRequests: List<CashDrawerRequest> = emptyList(),
    val isSubmittingCashRequest: Boolean = false,
    val cashRequestNotice: String? = null,
    val lastSync: Long = 0L,
    val appVersion: String = "",
    val period: ReportPeriod = ReportPeriod.WEEK,
    val search: String = "",
    val dataSource: String = "",
)

class OwnerViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = OwnerPrefs(application)
    private val repo = OwnerRepository()

    private val _state = MutableStateFlow(
        run {
            // Sesi lama (sebelum login email) tidak punya email. Paksa login ulang.
            if (prefs.username.isNotBlank() && !prefs.hasSavedAccount) {
                prefs.clear()
            }
            val savedEmail = prefs.email
            val savedRental = prefs.username
            val canUnlock = prefs.hasSavedAccount &&
                prefs.hasPin &&
                repo.hasRealSession() &&
                repo.currentEmail().equals(savedEmail, ignoreCase = true)
            OwnerUiState(
                savedEmail = savedEmail,
                savedUsername = savedRental,
                unlockMode = canUnlock,
                rental = RentalInfo(username = savedRental),
            )
        },
    )
    val state = _state.asStateFlow()

    // Local timer for TV session remaining time (realtime, independent of Firestore)
    private val _localTimer = MutableStateFlow<Map<String, Long>>(emptyMap())
    val localTimer = _localTimer.asStateFlow()

    private var rentalListener: ListenerRegistration? = null
    private var cashRequestListener: ListenerRegistration? = null
    private val clientListeners = mutableMapOf<String, ListenerRegistration>()
    private var hubClient: OwnerHubClient? = null
    private var reportJob: Job? = null
    private var autoRefreshJob: Job? = null
    private var timerJob: Job? = null

    fun login(inputEmail: String, password: String, pin: String) {
        val email = inputEmail.trim()
        if (email.isBlank() || !email.contains("@")) {
            _state.update { it.copy(error = "Email owner wajib diisi dengan benar.") }
            return
        }
        if (password.isBlank()) {
            _state.update { it.copy(error = "Password wajib diisi.") }
            return
        }
        if (!validatePin(pin)) return

        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val authResult = repo.signIn(email, password)
            if (!authResult.success) {
                _state.update { it.copy(isLoading = false, error = authResult.message) }
                return@launch
            }
            completeLogin(email, pin)
        }
    }

    fun loginWithGoogle(idToken: String, inputEmail: String, pin: String) {
        val email = inputEmail.trim()
        if (email.isBlank() || !email.contains("@")) {
            _state.update { it.copy(error = "Email Google tidak valid.") }
            return
        }
        if (idToken.isBlank()) {
            _state.update { it.copy(error = "Token Google tidak valid. Coba lagi.") }
            return
        }
        if (!validatePin(pin)) return

        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val authResult = repo.signInWithGoogle(idToken)
            if (!authResult.success) {
                _state.update { it.copy(isLoading = false, error = authResult.message) }
                return@launch
            }
            completeLogin(repo.currentEmail().ifBlank { email }, pin)
        }
    }

    private fun validatePin(pin: String): Boolean {
        if (pin.length < 4) {
            _state.update { it.copy(error = "PIN aplikasi minimal 4 digit.") }
            return false
        }
        if (pin.length > 8) {
            _state.update { it.copy(error = "PIN aplikasi maksimal 8 digit.") }
            return false
        }
        return true
    }

    private suspend fun completeLogin(email: String, pin: String) {
        val rental = repo.resolveRentalUsername(email)
        if (rental.isNullOrBlank()) {
            repo.signOut()
            _state.update {
                it.copy(
                    isLoading = false,
                    error = "Akun ini belum terdaftar sebagai admin rental. " +
                        "Buka aplikasi billing dengan akun ini, pastikan sudah sinkron, lalu coba lagi.",
                )
            }
            return
        }
        prefs.saveSession(email, rental, pin)
        _state.update {
            it.copy(
                savedEmail = email,
                savedUsername = rental,
                unlockMode = false,
                error = null,
                rental = it.rental.copy(username = rental),
            )
        }
        startSession(rental)
    }

    fun unlock(pin: String) {
        val savedEmail = prefs.email
        val savedRental = prefs.username
        if (savedEmail.isBlank() || savedRental.isBlank() || !prefs.hasPin) {
            _state.update {
                it.copy(
                    unlockMode = false,
                    error = "Sesi tidak ditemukan. Silakan login ulang dengan email & password.",
                )
            }
            return
        }
        if (!prefs.verifyPin(pin)) {
            _state.update { it.copy(error = "PIN salah.") }
            return
        }
        if (!repo.hasRealSession() || !repo.currentEmail().equals(savedEmail, ignoreCase = true)) {
            _state.update {
                it.copy(
                    unlockMode = false,
                    error = "Sesi login berakhir. Masuk ulang dengan email & password.",
                )
            }
            return
        }
        _state.update {
            it.copy(
                error = null,
                rental = it.rental.copy(username = savedRental),
            )
        }
        startSession(savedRental)
    }

    private fun startSession(username: String) {
        if (!repo.hasRealSession()) {
            _state.update {
                it.copy(
                    isLoggedIn = false,
                    isLoading = false,
                    unlockMode = false,
                    error = "Sesi login berakhir. Masuk ulang dengan email & password.",
                )
            }
            return
        }
        _state.update {
            it.copy(
                isLoggedIn = true,
                unlockMode = false,
                username = username,
                isLoading = true,
                error = null,
            )
        }
        viewModelScope.launch {
            val info = repo.fetchRentalInfo(username)
            _state.update { it.copy(rental = info) }
            attachRentalListener(username)
            attachCashRequestListener(username)
            startHubClient()
            refreshTransactions(showSpinner = false)
            startAutoRefresh()
            startLocalTimer()
        }
    }

    private fun startHubClient() {
        hubClient?.stop()
        hubClient = OwnerHubClient(
            context = getApplication(),
            expectedRentalNames = {
                buildSet {
                    _state.value.rental.namaRental
                        .takeIf { it.isNotBlank() }
                        ?.let { add(it) }
                    _state.value.tvs
                        .mapNotNull { it.namaRental.takeIf { name -> name.isNotBlank() } }
                        .forEach { add(it) }
                }
            },
            onSnapshot = ::handleHubSnapshot,
        ).also { it.start() }
    }

    private fun handleHubSnapshot(snapshot: OwnerHubSnapshot) {
        if (!snapshot.connected) {
            _state.update {
                it.copy(
                    hubConnected = false,
                    hubRental = "",
                    hubHost = "",
                    hubStatus = emptyMap(),
                )
            }
            return
        }

        val current = _state.value
        val ownerIdByKey = mutableMapOf<String, String>()
        current.tvs.forEach { tv ->
            normalizeHubId(tv.id).takeIf { it.isNotEmpty() }?.let { ownerIdByKey[it] = tv.id }
            normalizeHubId(tv.nama).takeIf { it.isNotEmpty() }?.let { ownerIdByKey[it] = tv.id }
        }

        val statuses = mutableMapOf<String, ClientStatus>()
        snapshot.tvs.forEach { hubTv ->
            val ownerId = ownerIdByKey[normalizeHubId(hubTv.mejaId)]
                ?: ownerIdByKey[normalizeHubId(hubTv.nama)]
                ?: return@forEach
            statuses[ownerId] = ClientStatus(
                online = hubTv.online,
                version = current.clientStatus[ownerId]?.version.orEmpty(),
                lastSeen = hubTv.lastSeen,
            )
        }
        current.tvs.forEach { tv ->
            if (tv.id.isNotBlank() && tv.id !in statuses) {
                statuses[tv.id] = ClientStatus(
                    online = false,
                    version = current.clientStatus[tv.id]?.version.orEmpty(),
                    lastSeen = 0L,
                )
            }
        }

        _state.update {
            it.copy(
                hubConnected = true,
                hubRental = snapshot.rentalName,
                hubHost = "${snapshot.host}:${snapshot.port}",
                hubStatus = statuses,
            )
        }
    }

    private fun normalizeHubId(value: String): String =
        value.trim().lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "")

    private fun attachRentalListener(username: String) {
        rentalListener?.remove()
        rentalListener = repo.listenRental(username) { snapshot ->
            handleRentalSnapshot(snapshot)
        }
    }

    private fun attachCashRequestListener(username: String) {
        cashRequestListener?.remove()
        cashRequestListener = repo.listenCashDrawerRequests(username) { requests ->
            _state.update { it.copy(cashDrawerRequests = requests) }
        }
    }

    private fun handleRentalSnapshot(snapshot: RentalSnapshot) {
        if (snapshot.error != null) {
            _state.update {
                it.copy(isLoading = false, error = snapshot.error)
            }
            return
        }
        if (!snapshot.exists) {
            _state.update {
                it.copy(
                    isLoading = false,
                    error = "Rental \"${it.username}\" tidak ditemukan di Firestore.",
                )
            }
            return
        }

        val useFirestoreFallback =
            _state.value.transactions.isEmpty() && snapshot.transaksiList.isNotEmpty()
        val rental = _state.value.rental.let { current ->
            if (current.namaRental.isBlank()) {
                current.copy(
                    namaRental = snapshot.tvList.firstOrNull()?.namaRental.orEmpty(),
                )
            } else {
                current
            }
        }

        _state.update {
            it.copy(
                tvs = snapshot.tvList,
                firestoreTransactions = snapshot.transaksiList,
                transactions = if (useFirestoreFallback) snapshot.transaksiList else it.transactions,
                menuMakanan = snapshot.menuMakanan,
                menuMinuman = snapshot.menuMinuman,
                stokMakanan = snapshot.stokMakanan,
                stokMinuman = snapshot.stokMinuman,
                jenisPsList = snapshot.jenisPsList,
                paketMain = snapshot.paketMain,
                paketDurasi = snapshot.paketDurasi,
                cashDrawer = snapshot.cashDrawer,
                dataSource = if (useFirestoreFallback) "Firestore" else it.dataSource,
                lastSync = snapshot.lastSync,
                appVersion = snapshot.appVersion,
                rental = rental,
                isLoading = false,
                error = null,
            )
        }
        syncClientListeners(snapshot.tvList)
    }

    private fun syncClientListeners(tvs: List<OwnerTv>) {
        val username = _state.value.username
        if (username.isBlank()) return
        val ids = tvs.mapNotNull { it.id.takeIf { id -> id.isNotBlank() } }.toSet()

        clientListeners.keys.filter { it !in ids }.forEach { id ->
            clientListeners.remove(id)?.remove()
        }
        _state.update {
            it.copy(clientStatus = it.clientStatus.filterKeys { id -> id in ids })
        }

        tvs.forEach { tv ->
            if (tv.id.isBlank() || clientListeners.containsKey(tv.id)) return@forEach
            val registration = repo.listenClientStatus(username, tv.id) { status ->
                _state.update { current ->
                    val statuses = if (status == null) {
                        current.clientStatus - tv.id
                    } else {
                        current.clientStatus + (tv.id to status)
                    }
                    current.copy(clientStatus = statuses)
                }
            }
            if (registration != null) clientListeners[tv.id] = registration
        }
    }

    fun refreshTransactions(showSpinner: Boolean = true) {
        val username = _state.value.username
        if (username.isBlank()) return
        if (showSpinner) {
            _state.update { it.copy(isRefreshing = true) }
        }
        reportJob?.cancel()
        reportJob = viewModelScope.launch {
            val remote = repo.fetchTransactions(username)
            if (remote != null) {
                val fallback = _state.value.firestoreTransactions
                val useFallback = remote.isEmpty() && fallback.isNotEmpty()
                _state.update {
                    it.copy(
                        transactions = if (useFallback) fallback else remote,
                        dataSource = if (useFallback) "Firestore" else "Supabase",
                        isRefreshing = false,
                        error = null,
                    )
                }
            } else {
                val fallback = _state.value.firestoreTransactions
                _state.update {
                    it.copy(
                        transactions = fallback,
                        dataSource = "Firestore",
                        isRefreshing = false,
                        error = if (fallback.isEmpty()) {
                            "Gagal memuat laporan. Periksa koneksi internet."
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }

    private fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (isActive) {
                delay(60_000)
                refreshTransactions(showSpinner = false)
            }
        }
    }

    private fun startLocalTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                val currentTime = System.currentTimeMillis()
                val updated = _state.value.tvs.associate { tv ->
                    tv.id to calculateRemaining(tv, currentTime)
                }
                _localTimer.update { updated }
            }
        }
    }

    private fun calculateRemaining(tv: OwnerTv, now: Long): Long {
        if (tv.timerStart <= 0L || tv.timerDurasi <= 0L) return tv.sisaDetik
        // If timer is active, count down locally from timerDurasi (seconds)
        if (tv.timerActive) {
            val elapsedSeconds = ((now - tv.timerStart) / 1000L).coerceAtLeast(0L)
            return (tv.timerDurasi - elapsedSeconds).coerceIn(0L, tv.timerDurasi)
        }
        // Otherwise use stored sisaDetik as last known value
        return tv.sisaDetik
    }

    fun setPeriod(period: ReportPeriod) {
        _state.update { it.copy(period = period) }
    }

    fun setSearch(search: String) {
        _state.update { it.copy(search = search) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun clearOpnameNotice() {
        _state.update { it.copy(opnameNotice = null) }
    }

    fun submitStockOpname(items: List<StockOpnameItem>, note: String) {
        val rental = _state.value.username
        val email = _state.value.savedEmail.ifBlank { prefs.email }
        if (rental.isBlank()) {
            _state.update { it.copy(error = "Rental belum dikenali. Login ulang.") }
            return
        }
        if (items.isEmpty()) {
            _state.update { it.copy(error = "Isi minimal satu jumlah tambahan stok.") }
            return
        }
        _state.update {
            it.copy(
                isSubmittingOpname = true,
                opnameNotice = null,
                error = null,
            )
        }
        viewModelScope.launch {
            val result = repo.submitStockOpname(rental, email, items, note)
            _state.update {
                it.copy(
                    isSubmittingOpname = false,
                    opnameNotice = if (result.success) result.message else null,
                    error = if (result.success) null else result.message,
                )
            }
        }
    }

    fun clearFnbItemNotice() {
        _state.update { it.copy(fnbItemNotice = null) }
    }

    fun submitFnbItem(input: FnbItemInput) {
        val rental = _state.value.username
        val email = _state.value.savedEmail.ifBlank { prefs.email }
        if (rental.isBlank()) {
            _state.update { it.copy(error = "Rental belum dikenali. Login ulang.") }
            return
        }
        val name = input.name.trim()
        if (name.isBlank()) {
            _state.update { it.copy(error = "Nama item wajib diisi.") }
            return
        }
        val category = input.category.trim().lowercase()
        if (category != "makanan" && category != "minuman") {
            _state.update { it.copy(error = "Kategori item tidak valid.") }
            return
        }
        val action = input.action.trim().lowercase()
        if (action != "add" && action != "update") {
            _state.update { it.copy(error = "Aksi item F&B tidak valid.") }
            return
        }
        if (input.price < 0 || (action == "add" && input.initialStock < 0)) {
            _state.update { it.copy(error = "Harga dan stok awal tidak boleh negatif.") }
            return
        }
        val current = _state.value
        val foodExists = current.menuMakanan.containsKey(name) ||
            current.stokMakanan.containsKey(name)
        val drinkExists = current.menuMinuman.containsKey(name) ||
            current.stokMinuman.containsKey(name)
        if (action == "add") {
            if (foodExists || drinkExists) {
                _state.update { it.copy(error = "Item \"$name\" sudah ada.") }
                return
            }
        } else {
            val existsInCategory = when (category) {
                "makanan" -> foodExists
                "minuman" -> drinkExists
                else -> false
            }
            if (!existsInCategory) {
                _state.update {
                    it.copy(error = "Item \"$name\" tidak ditemukan di kategori $category.")
                }
                return
            }
        }
        _state.update {
            it.copy(
                isSubmittingFnbItem = true,
                fnbItemNotice = null,
                error = null,
            )
        }
        viewModelScope.launch {
            val result = repo.submitFnbItem(
                rental,
                email,
                input.copy(
                    action = action,
                    category = category,
                    name = name,
                    initialStock = if (action == "update") 0 else input.initialStock,
                ),
            )
            _state.update {
                it.copy(
                    isSubmittingFnbItem = false,
                    fnbItemNotice = if (result.success) result.message else null,
                    error = if (result.success) null else result.message,
                )
            }
        }
    }

    fun clearPackageNotice() {
        _state.update { it.copy(packageNotice = null) }
    }

    fun submitPackage(input: PackageInput) {
        val rental = _state.value.username
        val email = _state.value.savedEmail.ifBlank { prefs.email }
        if (rental.isBlank()) {
            _state.update { it.copy(error = "Rental belum dikenali. Login ulang.") }
            return
        }
        val groupName = input.groupName.trim()
        val name = input.name.trim()
        if (groupName.isBlank()) {
            _state.update { it.copy(error = "Grup paket wajib dipilih.") }
            return
        }
        if (name.isBlank()) {
            _state.update { it.copy(error = "Nama paket wajib diisi.") }
            return
        }
        if (input.price < 0 || input.durationMinutes < 0) {
            _state.update { it.copy(error = "Harga dan durasi tidak boleh negatif.") }
            return
        }
        val current = _state.value
        val packages = current.paketMain[groupName].orEmpty()
        if (input.action == "add" && packages.containsKey(name)) {
            _state.update { it.copy(error = "Paket \"$name\" sudah ada di grup $groupName.") }
            return
        }
        if (input.action == "update" && !packages.containsKey(name)) {
            _state.update { it.copy(error = "Paket \"$name\" tidak ditemukan di grup $groupName.") }
            return
        }
        _state.update {
            it.copy(
                isSubmittingPackage = true,
                packageNotice = null,
                error = null,
            )
        }
        viewModelScope.launch {
            val result = repo.submitPackage(
                rental,
                email,
                input.copy(groupName = groupName, name = name),
            )
            _state.update {
                it.copy(
                    isSubmittingPackage = false,
                    packageNotice = if (result.success) result.message else null,
                    error = if (result.success) null else result.message,
                )
            }
        }
    }

    fun clearGroupNotice() {
        _state.update { it.copy(groupNotice = null) }
    }

    fun submitGroup(input: GroupInput) {
        val rental = _state.value.username
        val email = _state.value.savedEmail.ifBlank { prefs.email }
        if (rental.isBlank()) {
            _state.update { it.copy(error = "Rental belum dikenali. Login ulang.") }
            return
        }
        val name = input.name.trim()
        if (name.isBlank()) {
            _state.update { it.copy(error = "Nama grup wajib diisi.") }
            return
        }
        if (_state.value.jenisPsList.any { it.equals(name, ignoreCase = true) }) {
            _state.update { it.copy(error = "Grup \"$name\" sudah ada.") }
            return
        }
        _state.update {
            it.copy(
                isSubmittingGroup = true,
                groupNotice = null,
                error = null,
            )
        }
        viewModelScope.launch {
            val result = repo.submitGroup(rental, email, input.copy(name = name))
            _state.update {
                it.copy(
                    isSubmittingGroup = false,
                    groupNotice = if (result.success) result.message else null,
                    error = if (result.success) null else result.message,
                )
            }
        }
    }

    fun clearCashRequestNotice() {
        _state.update { it.copy(cashRequestNotice = null) }
    }

    fun submitCashDrawerRequest(nominal: Int, note: String) {
        val rental = _state.value.username
        val email = _state.value.savedEmail.ifBlank { prefs.email }
        if (rental.isBlank()) {
            _state.update { it.copy(error = "Rental belum dikenali. Login ulang.") }
            return
        }
        if (!_state.value.cashDrawer.aktif) {
            _state.update { it.copy(error = "Belum ada shift kasir yang aktif.") }
            return
        }
        if (nominal <= 0) {
            _state.update { it.copy(error = "Nominal penarikan harus lebih dari 0.") }
            return
        }
        if (nominal > _state.value.cashDrawer.cashExpected) {
            _state.update {
                it.copy(
                    error = "Nominal melebihi saldo laci " +
                        formatRupiah(_state.value.cashDrawer.cashExpected),
                )
            }
            return
        }
        _state.update {
            it.copy(
                isSubmittingCashRequest = true,
                cashRequestNotice = null,
                error = null,
            )
        }
        viewModelScope.launch {
            val result = repo.submitCashDrawerRequest(rental, email, nominal, note)
            _state.update {
                it.copy(
                    isSubmittingCashRequest = false,
                    cashRequestNotice = if (result.success) result.message else null,
                    error = if (result.success) null else result.message,
                )
            }
        }
    }

    private fun formatRupiah(value: Int): String =
        "Rp " + String.format(Locale("id", "ID"), "%,d", value)

    fun logout() {
        rentalListener?.remove()
        rentalListener = null
        cashRequestListener?.remove()
        cashRequestListener = null
        clientListeners.values.forEach { it.remove() }
        clientListeners.clear()
        hubClient?.stop()
        hubClient = null
        reportJob?.cancel()
        autoRefreshJob?.cancel()
        timerJob?.cancel()
        _localTimer.value = emptyMap()
        repo.signOut()
        prefs.clear()
        _state.value = OwnerUiState()
    }

    override fun onCleared() {
        rentalListener?.remove()
        cashRequestListener?.remove()
        clientListeners.values.forEach { it.remove() }
        clientListeners.clear()
        hubClient?.stop()
        hubClient = null
        reportJob?.cancel()
        autoRefreshJob?.cancel()
        timerJob?.cancel()
        super.onCleared()
    }
}
