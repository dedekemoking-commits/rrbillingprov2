package com.billingps.aptv.tv

import android.os.Handler
import android.os.Looper
import android.util.Log
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean

object HdmiWebSocketTester {

    data class Result(
        val connected: Boolean,
        val message: String,
        val response: String = "",
    )

    fun test(ip: String, payload: String, port: Int = 8080, onResult: (Result) -> Unit) {
        val target = ip.trim()
        if (target.isBlank()) {
            onResult(Result(false, "IP TV kosong"))
            return
        }
        val uri = try {
            URI("ws://$target:$port")
        } catch (e: Exception) {
            onResult(Result(false, "URL tidak valid: ${e.message}"))
            return
        }

        val handler = Handler(Looper.getMainLooper())
        val finished = AtomicBoolean(false)
        fun finish(result: Result) {
            if (finished.compareAndSet(false, true)) {
                handler.post { onResult(result) }
            }
        }

        val client = object : WebSocketClient(uri) {
            override fun onOpen(handshakedata: ServerHandshake?) {
                try {
                    send(payload)
                    Log.i("HdmiWs", "sent to $uri: $payload")
                    handler.postDelayed({
                        if (!finished.get()) {
                            finish(Result(true, "Payload terkirim, tidak ada respon dari TV"))
                        }
                        try {
                            close()
                        } catch (_: Exception) {
                        }
                    }, 1_500L)
                } catch (e: Exception) {
                    finish(Result(false, "Gagal mengirim: ${e.message}"))
                }
            }

            override fun onMessage(message: String?) {
                finish(Result(true, "Respon TV diterima", message?.take(500) ?: ""))
                try {
                    close()
                } catch (_: Exception) {
                }
            }

            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                if (!finished.get()) {
                    finish(Result(false, "Koneksi ditutup TV (code=$code reason=${reason ?: ""})"))
                }
            }

            override fun onError(ex: Exception?) {
                finish(Result(false, "Koneksi gagal: ${ex?.message ?: "unknown"}"))
                try {
                    close()
                } catch (_: Exception) {
                }
            }
        }

        try {
            Thread {
                try {
                    client.connect()
                } catch (e: Exception) {
                    finish(Result(false, "Tidak bisa connect: ${e.message}"))
                }
            }.start()
        } catch (e: Exception) {
            finish(Result(false, "Tidak bisa connect: ${e.message}"))
            return
        }

        handler.postDelayed({
            if (!finished.get()) {
                val open = try {
                    client.isOpen
                } catch (_: Exception) {
                    false
                }
                if (!open) {
                    finish(Result(false, "Timeout koneksi ws://$target:$port"))
                    try {
                        client.close()
                    } catch (_: Exception) {
                    }
                }
            }
        }, 4_500L)
    }
}
