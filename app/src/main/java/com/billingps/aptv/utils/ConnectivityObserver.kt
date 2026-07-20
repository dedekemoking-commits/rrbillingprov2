package com.billingps.aptv.utils

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow

class ConnectivityObserver(private val app: Application) {

    enum class Status { AVAILABLE, UNAVAILABLE, LOST }

    private val connectivityManager =
        app.getSystemService(Application.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _status = MutableStateFlow(Status.UNAVAILABLE)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _status.value = Status.AVAILABLE
        }

        override fun onLost(network: Network) {
            _status.value = Status.LOST
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            _status.value = if (hasInternet) Status.AVAILABLE else Status.LOST
        }

        override fun onUnavailable() {
            _status.value = Status.UNAVAILABLE
        }
    }

    fun start() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
        // Check initial state
        val activeNetwork = connectivityManager.activeNetwork
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
        _status.value = if (caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true) {
            Status.AVAILABLE
        } else {
            Status.UNAVAILABLE
        }
    }

    fun stop() {
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }
}
