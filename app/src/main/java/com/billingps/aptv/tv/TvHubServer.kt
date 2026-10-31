package com.billingps.aptv.tv

import android.util.Log
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class TvHubServer(
    port: Int = DEFAULT_PORT,
    bindHost: String? = null,
    private val configProvider: () -> HubConfig,
    private val onConnectionsChanged: (Map<String, TvConnection>) -> Unit,
    private val onLogReport: (String, JSONObject) -> Unit = { _, _ -> },
    private val onServerError: (String) -> Unit = {},
) : WebSocketServer(
    if (bindHost.isNullOrBlank()) InetSocketAddress(port)
    else InetSocketAddress(bindHost, port),
) {

    companion object {
        const val DEFAULT_PORT = 8080
        private const val TAG = "TvHubServer"
        private const val PING_INTERVAL_SECONDS = 5L
        private const val STALE_TIMEOUT_MS = 20_000L
    }

    private data class Session(
        val socket: WebSocket,
        val isDashboard: Boolean = false,
        var mejaId: String = "",
        var nama: String = "",
        var ip: String = "",
        var device: String = "",
        var lastSeen: Long = System.currentTimeMillis(),
        var screenOn: Boolean? = null,
        var inputLabel: String = "",
        var uiMode: String = "",
        var locked: Boolean = false,
    )

    private val sessions = ConcurrentHashMap<WebSocket, Session>()
    private val byMeja = ConcurrentHashMap<String, WebSocket>()
    private val lockedByTv = ConcurrentHashMap<String, Boolean>()
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    init {
        connectionLostTimeout = 0
    }

    @Volatile
    private var started = false

    override fun onStart() {
        started = true
        Log.i(TAG, "WebSocket hub listening on $address")
        executor.scheduleWithFixedDelay(
            { heartbeat() },
            PING_INTERVAL_SECONDS,
            PING_INTERVAL_SECONDS,
            TimeUnit.SECONDS,
        )
    }

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        val isDashboard = handshake.resourceDescriptor
            ?.contains("role=dashboard", ignoreCase = true) == true
        sessions[conn] = Session(socket = conn, isDashboard = isDashboard)
        if (isDashboard) {
            Log.i(TAG, "Dashboard terhubung dari ${conn.remoteSocketAddress}")
            sendState(conn)
        } else {
            if (!configProvider().clientEnabled) {
                Log.i(TAG, "Client ditolak: fitur client tidak aktif")
                send(
                    conn,
                    JSONObject()
                        .put("type", TvHubProtocol.TYPE_ERROR)
                        .put("message", "CLIENT_DISABLED"),
                )
                conn.close(1008, "client_disabled")
                return
            }
            Log.i(TAG, "Client terhubung dari ${conn.remoteSocketAddress}")
        }
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
        val session = sessions.remove(conn)
        if (session != null && session.mejaId.isNotBlank()) {
            byMeja.remove(TvHubProtocol.normalizeId(session.mejaId), conn)
            Log.i(TAG, "Client putus: ${session.mejaId} ($reason)")
        }
        publishConnections()
    }

    override fun onMessage(conn: WebSocket, message: String) {
        val session = sessions[conn] ?: return
        val obj = try {
            JSONObject(message)
        } catch (_: Exception) {
            send(conn, JSONObject().put("type", TvHubProtocol.TYPE_ERROR).put("message", "Invalid JSON"))
            return
        }
        if (session.isDashboard) {
            handleDashboardMessage(conn, session, obj)
            return
        }
        if (!configProvider().clientEnabled) {
            send(
                conn,
                JSONObject()
                    .put("type", TvHubProtocol.TYPE_ERROR)
                    .put("message", "CLIENT_DISABLED"),
            )
            conn.close(1008, "client_disabled")
            return
        }
        when (obj.optString("type")) {
            TvHubProtocol.TYPE_REGISTER -> register(conn, session, obj)
            TvHubProtocol.TYPE_PONG -> {
                session.lastSeen = System.currentTimeMillis()
                publishConnections()
            }
            TvHubProtocol.TYPE_SCREEN_STATE -> {
                session.lastSeen = System.currentTimeMillis()
                session.screenOn = if (obj.has("screen_on")) obj.optBoolean("screen_on") else null
                session.inputLabel = obj.optString("input_label", "")
                session.uiMode = obj.optString("ui_mode", "")
                publishConnections()
            }
            TvHubProtocol.TYPE_CLIENT_EVENT -> {
                session.lastSeen = System.currentTimeMillis()
                val event = obj.optString("event", "client_event")
                val key = TvHubProtocol.normalizeId(session.mejaId)
                when (event) {
                    "tv_lockscreen", "tv_waktu_habis" -> {
                        val locked = obj.optBoolean("ok", true)
                        session.locked = locked
                        if (locked) lockedByTv[key] = true else lockedByTv.remove(key)
                    }
                    "tv_unlock", "tv_timer_stop" -> {
                        session.locked = false
                        lockedByTv.remove(key)
                    }
                    "tv_sleep", "tv_tidur" -> {
                        session.screenOn = false
                    }
                    "tv_bangun" -> {
                        session.screenOn = true
                        val config = configProvider()
                        val tv = TvHubProtocol.findTv(config.tvs, session.mejaId)
                        if (lockedByTv[key] == true && tv != null) {
                            val socket = session.socket
                            val command = TvHubProtocol.lockScreen(tv, session.mejaId)
                            executor.schedule({
                                send(socket, command)
                                Log.i(TAG, "${session.mejaId} re-lock after wake")
                            }, 1500, TimeUnit.MILLISECONDS)
                        }
                    }
                }
                publishConnections()
                Log.i(
                    TAG,
                    "${session.mejaId} event=$event ok=${obj.optBoolean("ok", false)} " +
                        "msg=${obj.optString("message", "")}",
                )
            }
            TvHubProtocol.TYPE_LOG_REPORT -> {
                session.lastSeen = System.currentTimeMillis()
                onLogReport(session.mejaId.ifBlank { session.nama }, obj)
                publishConnections()
            }
            TvHubProtocol.TYPE_GET_TVS -> {
                send(conn, JSONObject().put("type", TvHubProtocol.TYPE_TVS_RESPONSE).put("tv", tvsJson()))
            }
            else -> {
                if (obj.has("action")) {
                    Log.d(TAG, "Pesan tanpa type dari ${session.mejaId}: ${obj.optString("action")}")
                }
            }
        }
    }

    private fun handleDashboardMessage(conn: WebSocket, session: Session, obj: JSONObject) {
        session.lastSeen = System.currentTimeMillis()
        when (obj.optString("type")) {
            "CMD" -> {
                val mejaId = obj.optString("meja_id").trim()
                val action = obj.optString("action").trim()
                val payload = obj.optJSONObject("payload") ?: JSONObject()
                val command = JSONObject(payload.toString())
                    .put("action", action)
                    .put("meja_id", mejaId)
                val ok = mejaId.isNotBlank() && action.isNotBlank() && sendTo(mejaId, command)
                send(conn, JSONObject()
                    .put("type", "ACK")
                    .put("action", action)
                    .put("meja_id", mejaId)
                    .put("ok", ok))
                publishConnections()
            }
            "GET_STATE", "STATE" -> sendState(conn)
            else -> Log.d(TAG, "Dashboard message ignored: ${obj.optString("type")}")
        }
    }

    private fun sendState(conn: WebSocket) {
        val config = configProvider()
        val hub = JSONObject()
            .put("name", config.rentalName)
            .put("port", DEFAULT_PORT)
        send(conn, JSONObject()
            .put("type", "STATE")
            .put("tvs", tvsJson())
            .put("sessions", JSONObject())
            .put("hub", hub))
    }

    override fun onError(conn: WebSocket?, ex: Exception) {
        Log.w(TAG, "WebSocket error: ${ex.message}")
        if (!started) onServerError(ex.message ?: ex.javaClass.simpleName)
    }

    private fun register(conn: WebSocket, session: Session, obj: JSONObject) {
        val mejaId = obj.optString("meja_id").trim()
        if (mejaId.isBlank()) {
            send(conn, JSONObject().put("type", TvHubProtocol.TYPE_ERROR).put("message", "meja_id wajib diisi"))
            return
        }
        val oldKey = TvHubProtocol.normalizeId(session.mejaId)
        if (oldKey.isNotEmpty()) byMeja.remove(oldKey, conn)
        session.mejaId = mejaId
        session.nama = obj.optString("nama", mejaId)
        session.ip = obj.optString("ip", "").ifBlank {
            conn.remoteSocketAddress?.address?.hostAddress ?: ""
        }
        session.device = obj.optString("device", "android_tv")
        session.lastSeen = System.currentTimeMillis()
        byMeja[TvHubProtocol.normalizeId(mejaId)] = conn

        val config = configProvider()
        val tv = TvHubProtocol.findTv(config.tvs, mejaId)
        val key = TvHubProtocol.normalizeId(mejaId)
        val expired = tv != null && !tv.timerActive && !tv.bebas &&
            tv.timerDurasi > 0 && tv.sisaDetik <= 0
        val isLocked = lockedByTv[key] == true || expired
        if (expired) lockedByTv[key] = true
        session.locked = isLocked
        val commands = snapshotCommands(tv, mejaId, config.rentalName, isLocked)
        val state = JSONArray()
        commands.forEach { state.put(it) }
        send(conn, JSONObject()
            .put("type", TvHubProtocol.TYPE_REGISTERED)
            .put("status", "OK")
            .put("meja_id", mejaId)
            .put("state", state))
        commands.forEach { send(conn, it) }
        publishConnections()
        Log.i(TAG, "REGISTER $mejaId ${session.ip} (${commands.size} snapshot)")
    }

    private fun snapshotCommands(
        tv: com.billingps.aptv.models.TvData?,
        mejaId: String,
        rentalName: String,
        locked: Boolean,
    ): List<JSONObject> {
        if (tv == null) return emptyList()
        val commands = mutableListOf<JSONObject>()
        val tampilRental = tv.namaRental.ifBlank { rentalName }.ifBlank { "RR Billing Pro" }
        commands += TvHubProtocol.updateRental(mejaId, tampilRental)
        commands += TvHubProtocol.overlaySettings(tv, mejaId)
        when {
            locked -> commands += TvHubProtocol.lockScreen(tv, mejaId)
            tv.timerActive || tv.bebas -> commands += TvHubProtocol.startTimer(tv, mejaId, tampilRental)
            else -> commands += TvHubProtocol.stopTimer(mejaId)
        }
        return commands
    }

    private fun heartbeat() {
        val now = System.currentTimeMillis()
        sessions.entries.toList().forEach { (conn, session) ->
            if (session.isDashboard) {
                session.lastSeen = now
                return@forEach
            }
            if (now - session.lastSeen > STALE_TIMEOUT_MS) {
                Log.w(TAG, "Client stale: ${session.mejaId.ifBlank { conn.remoteSocketAddress?.toString() ?: "?" }}")
                try {
                    conn.close()
                } catch (_: Exception) {
                }
                return@forEach
            }
            send(conn, JSONObject().put("action", TvHubProtocol.ACTION_PING).put("timestamp", now / 1000))
        }
        publishConnections()
    }

    private fun send(conn: WebSocket, payload: JSONObject) {
        try {
            if (conn.isOpen) {
                synchronized(conn) {
                    conn.send(payload.toString())
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "send gagal: ${e.message}")
        }
    }

    fun sendTo(mejaId: String, payload: JSONObject): Boolean {
        val key = TvHubProtocol.normalizeId(mejaId)
        when (payload.optString("action")) {
            TvHubProtocol.ACTION_LOCK_SCREEN -> applyLocked(key, true)
            TvHubProtocol.ACTION_UNLOCK_SCREEN,
            TvHubProtocol.ACTION_STOP_TIMER,
            TvHubProtocol.ACTION_START_TIMER,
            -> applyLocked(key, false)
        }
        val conn = byMeja[key] ?: return false
        val session = sessions[conn] ?: return false
        if (System.currentTimeMillis() - session.lastSeen > STALE_TIMEOUT_MS) return false
        if (!conn.isOpen) {
            byMeja.remove(key, conn)
            return false
        }
        send(conn, payload)
        return true
    }

    private fun applyLocked(key: String, locked: Boolean) {
        if (key.isEmpty()) return
        if (locked) lockedByTv[key] = true else lockedByTv.remove(key)
        sessions.values.forEach { session ->
            if (TvHubProtocol.normalizeId(session.mejaId) == key) session.locked = locked
        }
    }

    fun noteScreenState(mejaId: String, screenOn: Boolean): Boolean {
        val key = TvHubProtocol.normalizeId(mejaId)
        val conn = byMeja[key] ?: return false
        val session = sessions[conn] ?: return false
        if (session.screenOn == screenOn) return true
        session.screenOn = screenOn
        publishConnections()
        return true
    }

    fun broadcast(payload: JSONObject): Int {
        var sent = 0
        byMeja.values.toSet().forEach { conn ->
            if (conn.isOpen) {
                send(conn, payload)
                sent++
            }
        }
        return sent
    }

    fun disconnectClients() {
        sessions.values.filter { !it.isDashboard }.forEach { session ->
            try {
                send(
                    session.socket,
                    JSONObject()
                        .put("type", TvHubProtocol.TYPE_ERROR)
                        .put("message", "CLIENT_DISABLED"),
                )
                session.socket.close(1008, "client_disabled")
            } catch (_: Exception) {
            }
        }
    }

    fun refreshAll() {
        val config = configProvider()
        sessions.values.filter { it.mejaId.isNotBlank() }.forEach { session ->
            val tv = TvHubProtocol.findTv(config.tvs, session.mejaId)
            val key = TvHubProtocol.normalizeId(session.mejaId)
            val expired = tv != null && !tv.timerActive && !tv.bebas &&
                tv.timerDurasi > 0 && tv.sisaDetik <= 0
            if (expired) lockedByTv[key] = true
            val isLocked = lockedByTv[key] == true || expired
            session.locked = isLocked
            snapshotCommands(tv, session.mejaId, config.rentalName, isLocked)
                .forEach { send(session.socket, it) }
        }
    }

    fun connectedTvs(): Map<String, TvConnection> {
        val now = System.currentTimeMillis()
        return sessions.values
            .filter { it.mejaId.isNotBlank() }
            .associateBy { TvHubProtocol.normalizeId(it.mejaId) }
            .mapValues { (_, s) ->
                TvConnection(
                    mejaId = s.mejaId,
                    nama = s.nama,
                    ip = s.ip,
                    device = s.device,
                    online = now - s.lastSeen <= STALE_TIMEOUT_MS,
                    screenOn = s.screenOn,
                    inputLabel = s.inputLabel,
                    uiMode = s.uiMode,
                    locked = s.locked,
                    lastSeen = s.lastSeen,
                )
            }
    }

    fun tvsJson(): JSONArray {
        val arr = JSONArray()
        connectedTvs().values.forEach { c ->
            arr.put(JSONObject()
                .put("meja_id", c.mejaId)
                .put("nama", c.nama)
                .put("ip", c.ip)
                .put("device", c.device)
                .put("online", c.online)
                .put("screen_on", c.screenOn)
                .put("input_label", c.inputLabel)
                .put("ui_mode", c.uiMode)
                .put("locked", c.locked)
                .put("last_seen", c.lastSeen / 1000))
        }
        return arr
    }

    private fun publishConnections() {
        try {
            onConnectionsChanged(Collections.unmodifiableMap(connectedTvs()))
        } catch (e: Exception) {
            Log.w(TAG, "publishConnections gagal: ${e.message}")
        }
        sessions.entries
            .filter { it.value.isDashboard }
            .forEach { (conn, _) -> sendState(conn) }
    }

    fun shutdown() {
        try {
            executor.shutdownNow()
        } catch (_: Exception) {
        }
        sessions.keys.toList().forEach {
            try {
                it.close()
            } catch (_: Exception) {
            }
        }
        sessions.clear()
        byMeja.clear()
        try {
            stop(1000)
        } catch (_: Exception) {
        }
    }
}
