package com.billingps.aptv.utils

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.billingps.aptv.models.Transaksi
import java.io.IOException
import java.io.OutputStream
import java.lang.reflect.Method
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.*

object ThermalPrinter {
    private const val TAG = "ThermalPrinter"
    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val ALTERNATE_UUIDS = listOf(
        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"),
        UUID.fromString("0000110A-0000-1000-8000-00805F9B34FB"),
        UUID.fromString("0000110C-0000-1000-8000-00805F9B34FB"),
        UUID.fromString("0000110E-0000-1000-8000-00805F9B34FB"),
        UUID.fromString("0000111F-0000-1000-8000-00805F9B34FB"),
        UUID.fromString("00001112-0000-1000-8000-00805F9B34FB"),
        UUID.fromString("00001115-0000-1000-8000-00805F9B34FB"),
    )

    fun getAllBondedDevices(): List<BluetoothDevice> {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
        return adapter.bondedDevices?.toList() ?: emptyList()
    }

    fun getDevice(address: String): BluetoothDevice? {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return null
        return adapter.getRemoteDevice(address)
    }

    private var discoveryReceiver: BroadcastReceiver? = null

    fun discoverDevices(
        context: Context,
        onFound: (device: BluetoothDevice) -> Unit,
        onFinished: () -> Unit,
    ) {
        cancelDiscovery(context)
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        discoveryReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        @Suppress("DEPRECATION")
                        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                        if (device != null) onFound(device)
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        onFinished()
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        context.registerReceiver(discoveryReceiver, filter)
        adapter.startDiscovery()
    }

    fun cancelDiscovery(context: Context?) {
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter?.isDiscovering == true) adapter.cancelDiscovery()
        } catch (_: Exception) {}
        if (context != null && discoveryReceiver != null) {
            try { context.unregisterReceiver(discoveryReceiver) } catch (_: Exception) {}
            discoveryReceiver = null
        }
    }

    data class ConnectionResult(val success: Boolean, val message: String)

    fun connect(address: String): ConnectionResult {
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return ConnectionResult(false, "Bluetooth tidak tersedia")
            val device = adapter.getRemoteDevice(address) ?: return ConnectionResult(false, "Device tidak ditemukan")

            try { if (adapter.isDiscovering) adapter.cancelDiscovery() } catch (_: Exception) {}
            try { Thread.sleep(500) } catch (_: Exception) {}

            if (device.bondState != BluetoothDevice.BOND_BONDED) {
                return ConnectionResult(false, "Printer belum dipairing")
            }

            var lastError = ""

            val result1 = tryConnect(device, SPP_UUID, secure = true)
            if (result1.success) return result1

            val result2 = tryConnect(device, SPP_UUID, secure = false)
            if (result2.success) return result2

            for (uuid in ALTERNATE_UUIDS) {
                val r = tryConnect(device, uuid, secure = false)
                if (r.success) return r
            }

            // Try reflection channel 1..5
            for (channel in 1..5) {
                try {
                    val m: Method = device.javaClass.getMethod("createRfcommSocket", Int::class.java)
                    val fallbackSocket = m.invoke(device, channel) as BluetoothSocket
                    fallbackSocket.connect()
                    socket?.let { try { it.close() } catch (_: Exception) {} }
                    socket = fallbackSocket
                    outputStream = fallbackSocket.outputStream
                    Log.i(TAG, "Connected via reflection channel $channel to ${device.name}")
                    return ConnectionResult(true, "Terhubung ke ${device.name}")
                } catch (e: Exception) {
                    lastError = "Reflection ch$channel: ${e.message}"
                    try { (socket as? BluetoothSocket)?.close() } catch (_: Exception) {}
                    socket = null
                    outputStream = null
                }
            }

            return ConnectionResult(false, "Gagal: $lastError")
        } catch (e: Exception) {
            Log.e(TAG, "connect unexpected error: ${e.message}")
            disconnect()
            return ConnectionResult(false, "Error: ${e.message}")
        }
    }

    private fun tryConnect(device: BluetoothDevice, uuid: UUID, secure: Boolean): ConnectionResult {
        try {
            val s = if (secure) {
                device.createRfcommSocketToServiceRecord(uuid)
            } else {
                device.createInsecureRfcommSocketToServiceRecord(uuid)
            }
            s.connect()
            socket?.let { try { it.close() } catch (_: Exception) {} }
            socket = s
            outputStream = s.outputStream
            Log.i(TAG, "Connected via ${if (secure) "secure" else "insecure"} UUID=$uuid to ${device.name}")
            return ConnectionResult(true, "Terhubung ke ${device.name}")
        } catch (e: IOException) {
            try { (socket as? BluetoothSocket)?.close() } catch (_: Exception) {}
            socket = null
            outputStream = null
            return ConnectionResult(false, "${uuid.toString().take(8)}: ${e.message}")
        }
    }

    fun disconnect() {
        try { outputStream?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        outputStream = null
        socket = null
        Log.i(TAG, "Disconnected")
    }

    fun isConnected(): Boolean = socket?.isConnected == true

    private fun write(data: ByteArray) {
        outputStream?.write(data)
        outputStream?.flush()
    }

    private fun esc(vararg cmd: Int) = write(byteArrayOf(0x1B, *cmd.map { it.toByte() }.toByteArray()))

    private fun text(data: String, charset: String = "Windows-1252") {
        write(data.toByteArray(charset = Charset.forName(charset.trim())))
    }

    fun printStruk(
        transaksi: Transaksi,
        tvNama: String,
        tvJenisPs: String = "",
        kasir: String,
        namaRental: String = "",
        alamatRental: String = "",
        riwayatTransaksi: List<Transaksi> = emptyList(),
        menuMakanan: Map<String, Int> = emptyMap(),
        menuMinuman: Map<String, Int> = emptyMap(),
        csWhatsapp: String = "082180208414",
        bebas: Boolean = false,
        durasiBebasDetik: Long = 0L,
    ): Boolean {
        if (!isConnected()) return false
        try {
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("id", "ID"))

            esc(0x40)
            esc(0x61, 0x01)
            esc(0x45, 0x01)

            text("\n")
            text(if (namaRental.isNotBlank()) "$namaRental\n" else "RR BILLING PRO\n")
            esc(0x45, 0x00)
            if (alamatRental.isNotBlank()) text("$alamatRental\n")
            text("================================\n")

            esc(0x61, 0x00)
            text("No    : ${transaksi.id.takeLast(12)}\n")
            text("Tgl   : ${sdf.format(Date())}\n")
            text("TV    : $tvNama\n")
            if (tvJenisPs.isNotBlank()) text("PS    : $tvJenisPs\n")
            text("Kasir : $kasir\n")
            text("--------------------------------\n")

            val txRiwayat = if (riwayatTransaksi.isNotEmpty()) {
                riwayatTransaksi.distinctBy { it.id }
            } else {
                listOf(transaksi)
            }

            val sortedByWaktu = txRiwayat.sortedBy { it.waktu }

            val baseTx = sortedByWaktu.firstOrNull { t ->
                !t.paket.startsWith("Tambah Waktu:") && t.paket != "Pesanan Tambahan" && t.paket != "Main Bebas" && !t.paket.startsWith("BATAL:")
            }
            val tambahTxs = txRiwayat.filter { it.paket.startsWith("Tambah Waktu:") }
            val bebasTx = txRiwayat.firstOrNull { it.paket == "Main Bebas" }

            if (baseTx != null) {
                val baseName = baseTx.paket
                val basePrice = "Rp ${String.format("%,d", baseTx.paketHarga).replace(",", ".")}"
                text("$baseName${" ".repeat(maxOf(1, 20 - baseName.length))}$basePrice\n")
            }
            tambahTxs.forEach { t ->
                val addName = t.paket.replace("Tambah Waktu: ", "+ ")
                val addPrice = "Rp ${String.format("%,d", t.paketHarga).replace(",", ".")}"
                text("$addName${" ".repeat(maxOf(1, 20 - addName.length))}$addPrice\n")
            }
            if (bebasTx != null) {
                val durasiMnt = if (durasiBebasDetik > 0) durasiBebasDetik / 60 else 0
                val bebasLine = "Main Bebas ${durasiMnt} Menit"
                val bebasPrice = "Rp ${String.format("%,d", bebasTx.paketHarga).replace(",", ".")}"
                text("$bebasLine${" ".repeat(maxOf(1, 24 - bebasLine.length))}$bebasPrice\n")
            } else if (bebas) {
                val durasiMnt = if (durasiBebasDetik > 0) durasiBebasDetik / 60 else 0
                val bebasLine = "Main Bebas ${durasiMnt} Menit"
                text("$bebasLine\n")
            }
            text("--------------------------------\n")

            val aggregatedFood = mutableMapOf<String, Pair<Int, Int>>()
            txRiwayat.forEach { t ->
                t.pesanan.forEach { (name, qty) ->
                    if (qty <= 0) return@forEach
                    val itemPrice = t.pesananHarga[name]
                    val itemTotal = if (itemPrice != null && itemPrice > 0) {
                        itemPrice
                    } else {
                        val unitPrice = menuMakanan[name] ?: menuMinuman[name] ?: 0
                        unitPrice * qty
                    }
                    val existing = aggregatedFood[name]
                    if (existing != null) {
                        aggregatedFood[name] = (existing.first + qty) to (existing.second + itemTotal)
                    } else {
                        aggregatedFood[name] = qty to itemTotal
                    }
                }
            }
            if (aggregatedFood.isNotEmpty()) {
                aggregatedFood.forEach { (name, pair) ->
                    val qty = pair.first
                    val itemTotal = pair.second
                    val line = "$qty $name"
                    val priceStr = "Rp ${String.format("%,d", itemTotal).replace(",", ".")}"
                    val padding = maxOf(1, 24 - line.length)
                    text("$line${" ".repeat(padding)}$priceStr\n")
                }
                text("--------------------------------\n")
            }

            esc(0x61, 0x02)
            val totalSemua = txRiwayat.sumOf { it.total }
            val totalStr = "Rp ${String.format("%,d", totalSemua).replace(",", ".")}"
            text("TOTAL: $totalStr\n")

            esc(0x61, 0x01)
            text("--------------------------------\n")
            text("Terimakasih Telah Menggunakan\n")
            text("RR BILLING PRO\n")
            text("cs $csWhatsapp\n")
            text("\n\n\n")

            write(byteArrayOf(0x1D, 0x56, 0x00))
            Log.i(TAG, "Struk printed successfully")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Print failed: ${e.message}")
            return false
        }
    }

    fun printTestPage(): Boolean {
        if (!isConnected()) return false
        try {
            esc(0x40)
            esc(0x61, 0x01)
            esc(0x45, 0x01)
            text("RR BILLING PRO\n")
            esc(0x45, 0x00)
            text("Test Print\n")
            text("================\n")
            esc(0x61, 0x00)
            text("Printer: OK\n")
            text("Tanggal: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("id", "ID")).format(Date())}\n")
            text("================\n")
            esc(0x61, 0x01)
            text("Terima kasih!\n")
            text("\n\n\n")
            write(byteArrayOf(0x1D, 0x56, 0x00))
            Log.i(TAG, "Test page printed")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Test print failed: ${e.message}")
            return false
        }
    }
}
