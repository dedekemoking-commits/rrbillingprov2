package com.billingps.aptv.tv

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.billingps.aptv.models.TvData
import com.billingps.aptv.utils.ApkServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object TvHubManager {
    private const val TAG = "TvHubManager"
    private const val ADVERT_PORT = 8095
    private const val ADVERT_INTERVAL_MS = 2_500L

    @Volatile
    private var server: TvHubServer? = null

    @Volatile
    private var config: HubConfig = HubConfig()

    @Volatile
    private var logUploader: ((String, JSONObject) -> Unit)? = null

    private val _connections = MutableStateFlow<Map<String, TvConnection>>(emptyMap())
    val connections: StateFlow<Map<String, TvConnection>> = _connections.asStateFlow()

    @Volatile
    var lastError: String? = null
        private set

    @Volatile
    private var advertRunning = false
    private var advertThread: Thread? = null
    private var advertSocket: DatagramSocket? = null

    fun ensureStarted(context: Context) {
        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, TvHubService::class.java).setAction(TvHubService.ACTION_START),
            )
        } catch (e: Exception) {
            lastError = e.message
            Log.e(TAG, "Gagal start TvHubService: ${e.message}", e)
        }
    }

    fun startServer(context: Context) {
        if (server != null) return
        val host = ApkServer.getLocalIpAddress(context)
        try {
            val s = TvHubServer(
                port = TvHubServer.DEFAULT_PORT,
                bindHost = host,
                configProvider = { config },
                onConnectionsChanged = { _connections.value = it },
                onLogReport = { tvId, payload -> logUploader?.invoke(tvId, payload) },
                onServerError = { message ->
                    lastError = message
                    server = null
                    _connections.value = emptyMap()
                },
            )
            server = s
            lastError = null
            s.start()
            Log.i(TAG, "TV hub server starting on ${host ?: "0.0.0.0"}:${TvHubServer.DEFAULT_PORT}")
        } catch (e: Exception) {
            server = null
            lastError = e.message
            Log.e(TAG, "Gagal start server TV: ${e.message}", e)
        }
    }

    fun stopServer() {
        try {
            server?.shutdown()
        } catch (_: Exception) {
        }
        server = null
        _connections.value = emptyMap()
    }

    fun setLogUploader(uploader: ((String, JSONObject) -> Unit)?) {
        logUploader = uploader
    }

    fun updateConfig(tvs: List<TvData>, rentalName: String, clientEnabled: Boolean = true) {
        val old = config
        val updated = HubConfig(
            tvs = tvs.toList(),
            rentalName = rentalName.ifBlank { "RR Billing Pro" },
            clientEnabled = clientEnabled,
        )
        config = updated
        if (old.clientEnabled != clientEnabled) {
            server?.disconnectClients()
        }
        val endpointsChanged = old.tvs.size != updated.tvs.size ||
            old.rentalName != updated.rentalName ||
            old.clientEnabled != updated.clientEnabled ||
            old.tvs.map { it.id to it.nama.lowercase() } != updated.tvs.map { it.id to it.nama.lowercase() }
        if (endpointsChanged) {
            server?.refreshAll()
        }
    }

    fun isServerRunning(): Boolean = server != null

    fun isOnline(tv: TvData): Boolean {
        val id = mejaIdOf(tv)
        return connections.value.values.any {
            TvHubProtocol.normalizeId(it.mejaId) == TvHubProtocol.normalizeId(id) && it.online
        }
    }

    fun connectionFor(tv: TvData): TvConnection? {
        val id = TvHubProtocol.normalizeId(mejaIdOf(tv))
        return connections.value.values.firstOrNull {
            TvHubProtocol.normalizeId(it.mejaId) == id
        }
    }

    fun noteScreenState(tv: TvData, screenOn: Boolean) {
        val srv = server ?: return
        listOf(mejaIdOf(tv), tv.id, tv.nama)
            .filter { it.isNotBlank() }
            .distinct()
            .forEach { if (srv.noteScreenState(it, screenOn)) return }
    }

    fun noteScreenStateByIp(ip: String, screenOn: Boolean) {
        if (ip.isBlank()) return
        val conn = connections.value.values.firstOrNull { it.ip == ip }
        if (conn != null) {
            server?.noteScreenState(conn.mejaId, screenOn)
            return
        }
        val tv = config.tvs.firstOrNull { it.ip == ip } ?: return
        noteScreenState(tv, screenOn)
    }

    fun sendStartTimer(tv: TvData): Boolean {
        val id = mejaIdOf(tv)
        val rental = tv.namaRental.ifBlank { config.rentalName }.ifBlank { "RR Billing Pro" }
        return send(tv, TvHubProtocol.startTimer(tv, id, rental))
    }

    fun sendSyncTimer(tv: TvData): Boolean {
        val id = mejaIdOf(tv)
        return send(tv, TvHubProtocol.syncTimer(tv, id))
    }

    fun sendStopTimer(tv: TvData): Boolean =
        send(tv, TvHubProtocol.stopTimer(mejaIdOf(tv)))

    fun sendReportLogs(tv: TvData): Boolean =
        sendCommand(tv, TvHubProtocol.ACTION_REPORT_LOGS)

    fun sendReconnectWs(tv: TvData): Boolean =
        sendCommand(tv, TvHubProtocol.ACTION_RECONNECT_WS)

    fun sendRestartClient(tv: TvData): Boolean =
        sendCommand(tv, TvHubProtocol.ACTION_RESTART_CLIENT)

    fun sendCommand(tv: TvData, action: String, payload: JSONObject = JSONObject()): Boolean {
        val command = JSONObject(payload.toString())
            .put("action", action)
            .put("meja_id", mejaIdOf(tv))
        return send(tv, command)
    }

    fun sendSleepTv(tv: TvData): Boolean =
        send(tv, TvHubProtocol.sleepTv(mejaIdOf(tv)))

    fun sendLockScreen(tv: TvData): Boolean =
        send(tv, TvHubProtocol.lockScreen(tv, mejaIdOf(tv)))

    fun sendUnlockScreen(tv: TvData): Boolean =
        send(tv, JSONObject().put("action", TvHubProtocol.ACTION_UNLOCK_SCREEN).put("meja_id", mejaIdOf(tv)))

    fun sendOverlaySettings(tv: TvData): Boolean =
        send(tv, TvHubProtocol.overlaySettings(tv, mejaIdOf(tv)))

    fun sendUpdateRental(tv: TvData): Boolean {
        val rental = tv.namaRental.ifBlank { config.rentalName }.ifBlank { "RR Billing Pro" }
        return send(tv, TvHubProtocol.updateRental(mejaIdOf(tv), rental))
    }

    fun sendShowMedia(tv: TvData, mediaType: String = "video", local: String = "", url: String = ""): Boolean =
        send(tv, TvHubProtocol.showMedia(mejaIdOf(tv), mediaType, local, url))

    fun sendHideMedia(tv: TvData): Boolean =
        send(tv, TvHubProtocol.hideMedia(mejaIdOf(tv)))

    fun sendUpdateLockBg(tv: TvData, local: String = "", url: String = ""): Boolean =
        send(tv, TvHubProtocol.updateLockBg(mejaIdOf(tv), local, url))

    fun sendMessage(tv: TvData, pesan: String, durasi: Int = 5): Boolean =
        send(tv, TvHubProtocol.showMessage(mejaIdOf(tv), pesan, durasi))

    fun broadcastMessage(pesan: String, durasi: Int = 5): Int {
        val srv = server ?: return 0
        return try {
            srv.broadcast(TvHubProtocol.showMessage("", pesan, durasi))
        } catch (e: Exception) {
            Log.w(TAG, "broadcast SHOW_MESSAGE gagal: ${e.message}")
            0
        }
    }

    fun applyTvConfig(tv: TvData): Boolean {
        var ok = sendUpdateRental(tv)
        ok = sendOverlaySettings(tv) || ok
        return ok
    }

    fun sendStartOrSync(tv: TvData): Boolean =
        if (tv.timerActive || tv.bebas) {
            if (tv.sisaDetik >= 0 && tv.timerActive) sendStartTimer(tv) else sendSyncTimer(tv)
        } else {
            sendStopTimer(tv)
        }

    private fun send(tv: TvData, payload: JSONObject): Boolean {
        val srv = server ?: return false
        return try {
            srv.sendTo(mejaIdOf(tv), payload)
        } catch (e: Exception) {
            Log.w(TAG, "send ${payload.optString("action")} ke ${tv.nama} gagal: ${e.message}")
            false
        }
    }

    fun mejaIdOf(tv: TvData): String = tv.nama.ifBlank { tv.id }

    fun startAdvert(context: Context) {
        if (advertThread?.isAlive == true) return
        advertRunning = true
        advertThread = Thread({
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket().apply { broadcast = true }
                advertSocket = socket
                val target = InetAddress.getByName("255.255.255.255")
                while (advertRunning) {
                    try {
                        val host = ApkServer.getLocalIpAddress(context)
                        if (!host.isNullOrBlank()) {
                            val payload = JSONObject()
                                .put("app", "rrbillingpro")
                                .put("type", "hub_advert")
                                .put("host", host)
                                .put("port", TvHubServer.DEFAULT_PORT)
                                .toString()
                                .toByteArray(Charsets.UTF_8)
                            socket.send(DatagramPacket(payload, payload.size, target, ADVERT_PORT))
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "advert gagal: ${e.message}")
                    }
                    try {
                        Thread.sleep(ADVERT_INTERVAL_MS)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "advert socket gagal: ${e.message}")
            } finally {
                try {
                    socket?.close()
                } catch (_: Exception) {
                }
                advertSocket = null
            }
        }, "tv-hub-advert").apply {
            isDaemon = true
            start()
        }
    }

    fun stopAdvert() {
        advertRunning = false
        try {
            advertSocket?.close()
        } catch (_: Exception) {
        }
        advertThread?.interrupt()
        advertThread = null
        advertSocket = null
    }

    fun shutdownAll() {
        stopAdvert()
        stopServer()
    }
}
