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
import com.billingps.aptv.models.isFnbTx
import com.billingps.aptv.models.isPaketTx
import com.billingps.aptv.models.namaPaketBersih
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
        sesiMulai: Long = 0L,
        sesiSelesai: Long = 0L,
        cashDiterima: Int = 0,
        kembalian: Int = 0,
        paketSudahBayar: Boolean = true,
        fnbSudahBayar: Boolean = true,
    ): Boolean {
        if (!isConnected()) return false
        try {
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("id", "ID"))
            fun rp(value: Int): String =
                "Rp" + String.format("%,d", value).replace(",", ".")
            fun baris(kiri: String, kanan: String): String =
                kiri + " ".repeat(maxOf(1, 24 - kiri.length)) + kanan

            esc(0x40)
            esc(0x61, 0x01)
            esc(0x45, 0x01)
            text("\n")
            text(if (namaRental.isNotBlank()) "$namaRental\n" else "RR BILLING PRO\n")
            esc(0x45, 0x00)
            if (alamatRental.isNotBlank()) text("$alamatRental\n")
            text("================================\n")

            val txRiwayat = (if (riwayatTransaksi.isNotEmpty()) riwayatTransaksi else listOf(transaksi))
                .filter { it.paket != "BATAL" && !it.paket.startsWith("BATAL:") }
                .distinctBy { it.id }
                .sortedBy { it.waktu }
            val packageTxs = txRiwayat.filter { it.isPaketTx() }
            val fnbTxs = txRiwayat.filter { it.isFnbTx() }
            val legacyMerged = packageTxs.any { it.kategori.isBlank() && it.pesanan.isNotEmpty() }
            val paketLunas = if (packageTxs.isEmpty()) paketSudahBayar else packageTxs.all { it.sudahBayar }
            val fnbLunas = if (legacyMerged) {
                packageTxs.all { it.sudahBayar }
            } else if (fnbTxs.isEmpty()) {
                fnbSudahBayar
            } else {
                fnbTxs.all { it.sudahBayar }
            }

            esc(0x61, 0x00)
            text("No    : ${transaksi.id.takeLast(12)}\n")
            text("Tgl   : ${sdf.format(Date())}\n")
            text("TV    : $tvNama\n")
            if (tvJenisPs.isNotBlank()) text("PS    : $tvJenisPs\n")
            text("Kasir : $kasir\n")
            val metode = txRiwayat.firstOrNull { it.metode.isNotBlank() && it.metode != "BATAL" }?.metode
                ?: transaksi.metode
            if (metode.isNotBlank()) text("Bayar : $metode\n")
            val mulai = sesiMulai.takeIf { it > 0L }
                ?: txRiwayat.mapNotNull { it.sesiMulai.takeIf { t -> t > 0L } }.minOrNull()
            val selesai = sesiSelesai.takeIf { it > 0L }
                ?: txRiwayat.mapNotNull { it.sesiSelesai.takeIf { t -> t > 0L } }.maxOrNull()
            if (mulai != null) text("Mulai : ${sdf.format(Date(mulai))}\n")
            if (selesai != null) text("Selesai: ${sdf.format(Date(selesai))}\n")
            val durasiDetik = if (mulai != null && selesai != null) {
                ((selesai - mulai) / 1000).coerceAtLeast(0L)
            } else durasiBebasDetik
            if (durasiDetik > 0) {
                val menit = (durasiDetik / 60).toInt()
                text("Durasi: ${menit / 60}j ${menit % 60}m\n")
            }
            text("--------------------------------\n")

            if (packageTxs.isNotEmpty()) {
                val grouped = packageTxs.groupBy { it.namaPaketBersih().ifBlank { it.paket } }
                grouped.forEach { (nama, items) ->
                    val total = items.sumOf { it.paketHarga }
                    val status = if (items.all { it.sudahBayar }) "LUNAS" else "TAGIHAN"
                    val qty = items.sumOf { it.jumlah.coerceAtLeast(1) }
                    val label = if (qty > 1) "$nama x$qty" else nama
                    text(baris(label, rp(total)) + "\n")
                    text("  $status\n")
                }
            } else if (bebas) {
                val menit = (durasiBebasDetik / 60).toInt()
                text("Main Bebas ${menit} Menit\n")
            }
            if (packageTxs.isNotEmpty()) text("--------------------------------\n")

            val aggregatedFood = mutableMapOf<String, Pair<Int, Int>>()
            fnbTxs.forEach { t ->
                t.pesanan.forEach { (name, qty) ->
                    if (qty <= 0) return@forEach
                    val unitPrice = t.pesananHarga[name]
                        ?.takeIf { it > 0 }
                        ?: menuMakanan[name]
                        ?: menuMinuman[name]
                        ?: 0
                    val old = aggregatedFood[name]
                    aggregatedFood[name] = ((old?.first ?: 0) + qty) to ((old?.second ?: 0) + unitPrice * qty)
                }
            }
            if (aggregatedFood.isNotEmpty()) {
                aggregatedFood.forEach { (name, pair) ->
                    text(baris("${pair.first}x $name", rp(pair.second)) + "\n")
                }
                text("--------------------------------\n")
            }

            val totalPaket = packageTxs.sumOf { it.paketHarga }
            val totalFnb = if (legacyMerged) {
                packageTxs.sumOf { (it.total - it.paketHarga).coerceAtLeast(0) }
            } else {
                fnbTxs.sumOf { it.total }
            }
            val totalSemua = totalPaket + totalFnb
            val lunas = (if (paketLunas) totalPaket else 0) + (if (fnbLunas) totalFnb else 0)
            val tagihan = (totalSemua - lunas).coerceAtLeast(0)

            esc(0x61, 0x02)
            text("TOTAL: ${rp(totalSemua)}\n")
            if (tagihan > 0) {
                text("LUNAS: ${rp(lunas)}\n")
                text("TAGIHAN: ${rp(tagihan)}\n")
            } else {
                text("LUNAS\n")
            }
            if (cashDiterima > 0) {
                esc(0x61, 0x00)
                text("Tunai : ${rp(cashDiterima)}\n")
                text("Kembali: ${rp(kembalian)}\n")
            }

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
