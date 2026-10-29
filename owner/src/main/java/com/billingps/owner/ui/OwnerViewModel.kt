package com.billingps.owner.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.billingps.owner.data.ClientStatus
import com.billingps.owner.data.OwnerPrefs
import com.billingps.owner.data.OwnerRepository
import com.billingps.owner.data.OwnerTransaction
import com.billingps.owner.data.OwnerTv
import com.billingps.owner.data.RentalInfo
import com.billingps.owner.data.RentalSnapshot
import com.billingps.owner.data.ReportPeriod
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class OwnerUiState(
    val savedUsername: String = "",
    val pinRequired: Boolean = false,
    val isLoggedIn: Boolean = false,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val username: String = "",
    val rental: RentalInfo = RentalInfo(),
    val tvs: List<OwnerTv> = emptyList(),
    val clientStatus: Map<String, ClientStatus> = emptyMap(),
    val transactions: List<OwnerTransaction> = emptyList(),
    val firestoreTransactions: List<OwnerTransaction> = emptyList(),
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
        OwnerUiState(
            savedUsername = prefs.username,
            pinRequired = prefs.hasPin,
            rental = RentalInfo(username = prefs.username),
        ),
    )
    val state = _state.asStateFlow()

    private var rentalListener: ListenerRegistration? = null
    private val clientListeners = mutableMapOf<String, ListenerRegistration>()
    private var reportJob: Job? = null
    private var autoRefreshJob: Job? = null

    init {
        val saved = prefs.username
        if (saved.isNotBlank() && !prefs.hasPin) {
            startSession(saved)
        }
    }

    fun login(inputUsername: String, pin: String) {
        val username = inputUsername.trim()
        if (username.isBlank()) {
            _state.update { it.copy(error = "Username rental wajib diisi.") }
            return
        }
        if (prefs.hasPin) {
            if (username != prefs.username) {
                _state.update {
                    it.copy(error = "Username terkunci. Tekan Ganti Rental untuk mengganti.")
                }
                return
            }
            if (!prefs.verifyPin(username, pin)) {
                _state.update { it.copy(error = "PIN owner salah.") }
                return
            }
        } else if (pin.isNotBlank() && pin.length < 4) {
            _state.update { it.copy(error = "PIN minimal 4 digit.") }
            return
        }
        prefs.saveSession(username, pin.takeIf { it.isNotBlank() })
        _state.update {
            it.copy(
                savedUsername = username,
                pinRequired = prefs.hasPin,
                error = null,
                rental = it.rental.copy(username = username),
            )
        }
        startSession(username)
    }

    private fun startSession(username: String) {
        _state.update {
            it.copy(
                isLoggedIn = true,
                username = username,
                isLoading = true,
                error = null,
            )
        }
        viewModelScope.launch {
            val signedIn = repo.ensureSignedIn()
            if (!signedIn) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "Gagal terhubung ke Firebase. Periksa koneksi internet.",
                    )
                }
                return@launch
            }
            val info = repo.fetchRentalInfo(username)
            _state.update { it.copy(rental = info) }
            attachRentalListener(username)
            refreshTransactions(showSpinner = false)
            startAutoRefresh()
        }
    }

    private fun attachRentalListener(username: String) {
        rentalListener?.remove()
        rentalListener = repo.listenRental(username) { snapshot ->
            handleRentalSnapshot(snapshot)
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
                    current.copy(clientStatus = current.clientStatus + (tv.id to status))
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

    fun setPeriod(period: ReportPeriod) {
        _state.update { it.copy(period = period) }
    }

    fun setSearch(search: String) {
        _state.update { it.copy(search = search) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun logout() {
        rentalListener?.remove()
        rentalListener = null
        clientListeners.values.forEach { it.remove() }
        clientListeners.clear()
        reportJob?.cancel()
        autoRefreshJob?.cancel()
        prefs.clear()
        _state.value = OwnerUiState()
    }

    override fun onCleared() {
        rentalListener?.remove()
        clientListeners.values.forEach { it.remove() }
        clientListeners.clear()
        reportJob?.cancel()
        autoRefreshJob?.cancel()
        super.onCleared()
    }
}
