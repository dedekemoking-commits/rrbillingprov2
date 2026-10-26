package com.billingps.aptv.tv

import android.util.Log
import com.chaquo.python.PyObject
import com.chaquo.python.Python

object TvRemotePower {
    data class Result(
        val ok: Boolean,
        val message: String = "",
        val confirmed: Boolean = ok,
        val isOn: Boolean? = null,
    )

    fun send(ip: String, certPem: String, keyPem: String, on: Boolean? = null): Result {
        if (ip.isBlank() || certPem.isBlank() || keyPem.isBlank()) {
            return Result(false, "TV belum dipairing")
        }
        return try {
            val controller = Python.getInstance()
                .getModule("tv_mesin")
                .callAttr("TvController")
            val result = if (on == null) {
                controller.callAttr("send_power", ip, certPem, keyPem)
            } else {
                controller.callAttr("send_power", ip, certPem, keyPem, on)
            }
            val sent = result.callAttr("get", "sent").asBooleanOrNull() ?: false
            val confirmed = result.callAttr("get", "confirmed").asBooleanOrNull() ?: sent
            val isOn = result.callAttr("get", "target_on").asBooleanOrNull()
            val error = result.callAttr("get", "error")?.toString().orEmpty()
            val message = result.callAttr("get", "message")?.toString().orEmpty()
            val text = if (sent) {
                message.ifBlank {
                    if (confirmed) "TV mengonfirmasi perintah power." else "Perintah power terkirim ke TV."
                }
            } else {
                error.ifBlank {
                    message.ifBlank { "Perintah power gagal. Periksa pairing dan jaringan TV." }
                }
            }
            Log.i("TvRemotePower", "send_power $ip on=$on sent=$sent confirmed=$confirmed isOn=$isOn msg=$text")
            Result(sent, text, confirmed, isOn)
        } catch (e: Exception) {
            Log.e("TvRemotePower", "send_power gagal: ${e.message}", e)
            Result(false, e.message ?: "Perintah power gagal")
        }
    }

    private fun PyObject?.asBooleanOrNull(): Boolean? {
        if (this == null) return null
        val value = toString()
        if (value == "None" || value.isBlank()) return null
        return toBoolean()
    }
}
