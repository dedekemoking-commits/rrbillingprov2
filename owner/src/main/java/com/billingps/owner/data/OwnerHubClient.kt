package com.billingps.owner.data

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.util.Locale
import java.util.concurrent.TimeUnit

data class OwnerHubTv(
    val mejaId: String = "",
    val nama: String = "",
    val ip: String = "",
    val device: String = "",
    val online: Boolean = false,
    val lastSeen: Long = 0L,
    val screenOn: Boolean? = null,
    val locked: Boolean = false,
)

data class OwnerHubSnapshot(
    val host: String = "",
    val port: Int = 8080,
    val rentalName: String = "",
    val connected: Boolean = false,
    val tvs: List<OwnerHubTv> = emptyList(),
    val error: String? = null,
)

class OwnerHubClient(
    context: Context,
    private val expectedRentalNames: () -> Set<String>,
    private val onSnapshot: (OwnerHubSnapshot) -> Unit,
) {
    companion object {
        private const val TAG = "OwnerHubClient"
        private const val DISCOVERY_PORT = 8095
        private const val DEFAULT_HUB_PORT = 8080
        private const val REJECT_COOLDOWN_MS = 60_000L
        private val CLEAN_ID = Regex("[^a-z0-9]")
    }

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val http = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var discoveryJob: Job? = null
    private var socket: WebSocket? = null
    private var currentHost: String = ""
    private var currentPort: Int = DEFAULT_HUB_PORT
    private var multicastLock: WifiManager.MulticastLock? = null
    private var connected = false
    @Volatile
    private var stopped = false
    private val rejected = mutableMapOf<String, Long>()

    fun start() {
        if (discoveryJob?.isActive == true) return
        stopped = false
        acquireMulticastLock()
        discoveryJob = scope.launch { discoveryLoop() }
    }

    fun stop() {
        stopped = true
        discoveryJob?.cancel()
        discoveryJob = null
        synchronized(lock) {
            socket?.cancel()
            socket = null
            currentHost = ""
            connected = false
        }
        scope.cancel()
        releaseMulticastLock()
    }

    private suspend fun discoveryLoop() {
        while (!stopped && currentCoroutineContext().isActive) {
            var udp: DatagramSocket? = null
            try {
                udp = DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    bind(InetSocketAddress(DISCOVERY_PORT))
                    soTimeout = 4000
                }
                val buffer = ByteArray(4096)
                while (!stopped && currentCoroutineContext().isActive) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        udp.receive(packet)
                        handleAdvert(String(packet.data, packet.offset, packet.length, Charsets.UTF_8))
                    } catch (_: SocketTimeoutException) {
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Discovery gagal: ${t.message}")
            } finally {
                try {
                    udp?.close()
                } catch (_: Throwable) {
                }
            }
            if (!stopped && currentCoroutineContext().isActive) delay(3000)
        }
    }

    private fun handleAdvert(raw: String) {
        val obj = try {
            JSONObject(raw)
        } catch (_: Throwable) {
            return
        }
        if (obj.optString("type") != "hub_advert") return
        val host = obj.optString("host").trim()
        val port = obj.optInt("port", DEFAULT_HUB_PORT)
        if (host.isBlank() || port !in 1..65535) return
        maybeConnect(host, port)
    }

    private fun maybeConnect(host: String, port: Int) {
        val key = "$host:$port"
        synchronized(lock) {
            if (stopped) return
            val rejectedUntil = rejected[key] ?: 0L
            if (rejectedUntil > System.currentTimeMillis()) return
            if (host == currentHost && port == currentPort && socket != null) return
            socket?.cancel()
            socket = null
            currentHost = host
            currentPort = port
            connected = false
        }
        connect(host, port)
    }

    private fun connect(host: String, port: Int) {
        val request = try {
            Request.Builder()
                .url("ws://$host:$port/?role=dashboard")
                .build()
        } catch (t: Throwable) {
            Log.w(TAG, "URL hub tidak valid: $host:$port (${t.message})")
            return
        }
        Log.i(TAG, "Menghubungkan hub $host:$port")
        val ws = http.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    if (stopped) {
                        webSocket.close(1000, "stopped")
                        return
                    }
                    webSocket.send("""{"type":"GET_STATE"}""")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (stopped) return
                    handleHubMessage(host, port, text)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    handleDisconnect(host, port, t.message ?: "Koneksi hub gagal")
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    handleDisconnect(host, port, reason.ifBlank { "Koneksi hub ditutup" })
                }
            },
        )
        synchronized(lock) {
            if (stopped || host != currentHost || port != currentPort) {
                ws.cancel()
            } else {
                socket = ws
            }
        }
    }

    private fun handleHubMessage(host: String, port: Int, raw: String) {
        val obj = try {
            JSONObject(raw)
        } catch (_: Throwable) {
            return
        }
        if (obj.optString("type") != "STATE") return
        val hub = obj.optJSONObject("hub")
        val rentalName = hub?.optString("name").orEmpty()
        if (!matchesRental(rentalName)) {
            val key = "$host:$port"
            synchronized(lock) {
                rejected[key] = System.currentTimeMillis() + REJECT_COOLDOWN_MS
                if (host == currentHost && port == currentPort) {
                    socket?.cancel()
                    socket = null
                    currentHost = ""
                    connected = false
                }
            }
            Log.i(TAG, "Hub $host:$port dilewati, rental tidak cocok: $rentalName")
            return
        }
        val tvs = parseTvs(obj.optJSONArray("tvs"))
        synchronized(lock) {
            if (stopped || host != currentHost || port != currentPort) return
            connected = true
        }
        onSnapshot(
            OwnerHubSnapshot(
                host = host,
                port = port,
                rentalName = rentalName,
                connected = true,
                tvs = tvs,
            ),
        )
    }

    private fun handleDisconnect(host: String, port: Int, message: String) {
        val wasConnected: Boolean
        synchronized(lock) {
            if (host != currentHost || port != currentPort) return
            wasConnected = connected
            connected = false
            socket = null
            currentHost = ""
        }
        Log.i(TAG, "Hub $host:$port terputus: $message")
        if (!stopped && wasConnected) {
            onSnapshot(
                OwnerHubSnapshot(
                    host = host,
                    port = port,
                    connected = false,
                    error = message,
                ),
            )
        }
    }

    private fun parseTvs(array: JSONArray?): List<OwnerHubTv> {
        if (array == null) return emptyList()
        val result = mutableListOf<OwnerHubTv>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            result += OwnerHubTv(
                mejaId = obj.optString("meja_id"),
                nama = obj.optString("nama"),
                ip = obj.optString("ip"),
                device = obj.optString("device"),
                online = obj.optBoolean("online", false),
                lastSeen = obj.optLong("last_seen", 0L) * 1000L,
                screenOn = if (obj.has("screen_on") && !obj.isNull("screen_on")) {
                    obj.optBoolean("screen_on")
                } else {
                    null
                },
                locked = obj.optBoolean("locked", false),
            )
        }
        return result
    }

    private fun matchesRental(hubName: String): Boolean {
        val wanted = expectedRentalNames()
            .map(::normalize)
            .filter { it.isNotEmpty() }
            .toSet()
        if (wanted.isEmpty()) return true
        val got = normalize(hubName)
        return got.isEmpty() || wanted.contains(got)
    }

    private fun normalize(value: String): String =
        value.trim().lowercase(Locale.US).replace(CLEAN_ID, "")

    private fun acquireMulticastLock() {
        try {
            val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
            val lock = wifi.createMulticastLock("rr-owner-hub")
            lock.setReferenceCounted(false)
            lock.acquire()
            multicastLock = lock
        } catch (t: Throwable) {
            Log.w(TAG, "Multicast lock gagal: ${t.message}")
        }
    }

    private fun releaseMulticastLock() {
        try {
            multicastLock?.release()
        } catch (_: Throwable) {
        }
        multicastLock = null
    }
}
