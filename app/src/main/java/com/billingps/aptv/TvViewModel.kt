package com.billingps.aptv

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chaquo.python.Python
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TvUiState(
    val ip: String = "",
    val pin: String = "",
    val status: String = "",
    val isPairing: Boolean = false,
    val isPaired: Boolean = false,
    val isBusy: Boolean = false,
)

class TvViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(TvUiState())
    val uiState: StateFlow<TvUiState> = _uiState.asStateFlow()

    private val prefs = application.getSharedPreferences("tv_certs", Context.MODE_PRIVATE)

    private fun getCert(): Pair<String, String>? {
        val cert = prefs.getString("cert_pem", null) ?: return null
        val key = prefs.getString("key_pem", null) ?: return null
        return Pair(cert, key)
    }

    fun updateIp(ip: String) {
        _uiState.value = _uiState.value.copy(ip = ip)
    }

    fun updatePin(pin: String) {
        _uiState.value = _uiState.value.copy(pin = pin)
    }

    fun startPairing() {
        val ip = _uiState.value.ip.trim()
        if (ip.isEmpty()) {
            _uiState.value = _uiState.value.copy(status = "Enter IP address first")
            return
        }
        _uiState.value = _uiState.value.copy(
            status = "Requesting PIN on TV...",
            isPairing = true,
            isBusy = true,
        )
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    Log.d("BillingPS", "start_pairing called with ip=$ip")
                    val out = Python.getInstance()
                        .getModule("tv_mesin")
                        .callAttr("TvController")
                        .callAttr("start_pairing", ip)
                    Log.d("BillingPS", "start_pairing returned: $out")
                    out
                } catch (e: Exception) {
                    Log.e("BillingPS", "start_pairing threw exception", e)
                    null
                }
            }
            if (result == null) {
                _uiState.value = _uiState.value.copy(
                    status = "Error: Failed to start pairing",
                    isPairing = false,
                    isBusy = false,
                )
                return@launch
            }
            val status = result.callAttr("get", "status")?.toString() ?: ""
            Log.d("BillingPS", "start_pairing status=$status")
            if (status == "pin_shown_on_tv") {
                val genCert = result.callAttr("get", "cert_pem")?.toString()
                val genKey = result.callAttr("get", "key_pem")?.toString()
                if (genCert != null && genKey != null) {
                    prefs.edit()
                        .putString("cert_pem", genCert)
                        .putString("key_pem", genKey)
                        .apply()
                    Log.d("BillingPS", "cert saved from library generation")
                }
                _uiState.value = _uiState.value.copy(
                    status = "PIN shown on TV. Enter the code below.",
                    isPairing = true,
                    isBusy = false,
                )
            } else {
                val err = result.callAttr("get", "error")?.toString() ?: "(null)"
                Log.e("BillingPS", "start_pairing error: $err")
                _uiState.value = _uiState.value.copy(
                    status = "Error: $err",
                    isPairing = false,
                    isBusy = false,
                )
            }
        }
    }

    fun completePairing() {
        val pin = _uiState.value.pin.trim()
        if (pin.isEmpty()) {
            _uiState.value = _uiState.value.copy(status = "Enter PIN code")
            return
        }
        _uiState.value = _uiState.value.copy(
            status = "Pairing...",
            isBusy = true,
        )
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    Log.d("BillingPS", "finish_pairing called with pin=$pin")
                    val out = Python.getInstance()
                        .getModule("tv_mesin")
                        .callAttr("TvController")
                        .callAttr("finish_pairing", pin)
                    Log.d("BillingPS", "finish_pairing returned: $out")
                    out
                } catch (e: Exception) {
                    Log.e("BillingPS", "finish_pairing threw", e)
                    null
                }
            }
            if (result == null) {
                _uiState.value = _uiState.value.copy(
                    status = "Error: Pairing failed",
                    isPairing = false,
                    isBusy = false,
                )
                return@launch
            }
            val paired = result.callAttr("get", "paired")?.toBoolean() ?: false
            if (paired) {
                _uiState.value = _uiState.value.copy(
                    status = "Paired successfully!",
                    isPairing = false,
                    isPaired = true,
                    isBusy = false,
                )
            } else {
                val err = result.callAttr("get", "error")?.toString() ?: "(null)"
                Log.e("BillingPS", "finish_pairing error: $err")
                _uiState.value = _uiState.value.copy(
                    status = "Pairing failed: $err",
                    isPairing = false,
                    isBusy = false,
                )
            }
        }
    }

    fun sendKeyCommand(ip: String, certPem: String, keyPem: String, keyCode: String, label: String) {
        _uiState.value = _uiState.value.copy(
            status = "Sending $label...",
            isBusy = true,
        )
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    Log.d("BillingPS", "send_key ip=$ip key=$keyCode")
                    val out = Python.getInstance()
                        .getModule("tv_mesin")
                        .callAttr("TvController")
                        .callAttr("send_key", ip, certPem, keyPem, keyCode)
                    Log.d("BillingPS", "send_key returned: $out")
                    out
                } catch (e: Exception) {
                    Log.e("BillingPS", "send_key threw", e)
                    null
                }
            }
            if (result == null) {
                _uiState.value = _uiState.value.copy(
                    status = "Error: Failed to send command",
                    isBusy = false,
                )
                return@launch
            }
            val sent = result.callAttr("get", "sent")?.toBoolean() ?: false
            val err = result.callAttr("get", "error")?.toString() ?: ""
            _uiState.value = _uiState.value.copy(
                status = if (sent) "$label sent" else "Failed: $err",
                isBusy = false,
            )
        }
    }

    fun sendPower(ip: String, certPem: String, keyPem: String) {
        _uiState.value = _uiState.value.copy(status = "Sending Power...", isBusy = true)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    Python.getInstance().getModule("tv_mesin").callAttr("TvController")
                        .callAttr("send_power", ip, certPem, keyPem)
                } catch (e: Exception) { Log.e("BillingPS", "send_power threw", e); null }
            }
            if (result != null) {
                val sent = result.callAttr("get", "sent")?.toBoolean() ?: false
                _uiState.value = _uiState.value.copy(
                    status = if (sent) "Power sent" else "Power failed",
                    isBusy = false)
            } else { _uiState.value = _uiState.value.copy(status = "Error", isBusy = false) }
        }
    }

    fun sendVolumeUp(ip: String, certPem: String, keyPem: String) {
        _uiState.value = _uiState.value.copy(status = "Volume Up...", isBusy = true)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    Python.getInstance().getModule("tv_mesin").callAttr("TvController")
                        .callAttr("send_volume_up", ip, certPem, keyPem)
                } catch (e: Exception) { Log.e("BillingPS", "vol_up threw", e); null }
            }
            if (result != null) {
                val sent = result.callAttr("get", "sent")?.toBoolean() ?: false
                _uiState.value = _uiState.value.copy(
                    status = if (sent) "Volume Up sent" else "Volume Up failed",
                    isBusy = false)
            } else { _uiState.value = _uiState.value.copy(status = "Error", isBusy = false) }
        }
    }

    fun sendVolumeDown(ip: String, certPem: String, keyPem: String) {
        _uiState.value = _uiState.value.copy(status = "Volume Down...", isBusy = true)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    Python.getInstance().getModule("tv_mesin").callAttr("TvController")
                        .callAttr("send_volume_down", ip, certPem, keyPem)
                } catch (e: Exception) { Log.e("BillingPS", "vol_down threw", e); null }
            }
            if (result != null) {
                val sent = result.callAttr("get", "sent")?.toBoolean() ?: false
                _uiState.value = _uiState.value.copy(
                    status = if (sent) "Volume Down sent" else "Volume Down failed",
                    isBusy = false)
            } else { _uiState.value = _uiState.value.copy(status = "Error", isBusy = false) }
        }
    }
}
