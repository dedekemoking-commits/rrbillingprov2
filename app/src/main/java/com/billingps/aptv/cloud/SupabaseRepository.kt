package com.billingps.aptv.cloud

import android.util.Log
import com.billingps.aptv.models.Invoice
import com.billingps.aptv.models.LicenseStatus
import com.billingps.aptv.models.Transaksi
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

class SupabaseRepository {

    fun syncTransactions(email: String, username: String, transactions: List<Transaksi>): Boolean {
        val normalizedEmail = email.trim().lowercase(Locale.ROOT)
        if (normalizedEmail.isBlank()) {
            Log.w(TAG, "syncTransactions skipped: email is blank")
            return true
        }
        val rows = transactions.filter { it.id.isNotBlank() }
        if (rows.isEmpty()) {
            Log.i(TAG, "syncTransactions skipped: no transactions")
            return true
        }

        return try {
            rows.chunked(200).all { chunk -> upsertChunk(normalizedEmail, username, chunk) }
        } catch (e: Exception) {
            Log.e(TAG, "syncTransactions failed: ${e.message}")
            false
        }
    }

    private fun upsertChunk(email: String, username: String, transactions: List<Transaksi>): Boolean {
        val body = JSONArray()
        transactions.forEach { t ->
            body.put(
                JSONObject().apply {
                    put("id", t.id)
                    put("username", username)
                    put("email", email)
                    put("waktu", t.waktu)
                    put("kasir", t.kasir)
                    put("kota", t.kota)
                    put("paket", t.paket)
                    put("total", t.total)
                    put("paid", t.sudahBayar)
                    put("tvjenisps", t.tvJenisPs)
                    put("pesanan", JSONObject(t.pesanan))
                    put("pesananharga", JSONObject(t.pesananHarga))
                    put("paketharga", t.paketHarga.coerceAtLeast(0))
                    put("diskoni", 0)
                },
            )
        }

        val connection = (URL("$BASE_URL/rest/v1/transaksi?on_conflict=id").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("apikey", API_KEY)
            setRequestProperty("Authorization", "Bearer $API_KEY")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Prefer", "resolution=merge-duplicates,return=minimal")
        }

        return try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body.toString()) }
            val code = connection.responseCode
            if (code in 200..299) {
                Log.i(TAG, "synced ${transactions.size} transactions HTTP $code")
                true
            } else {
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                Log.e(TAG, "sync failed HTTP $code: $detail")
                false
            }
        } finally {
            connection.disconnect()
        }
    }

    fun saveInvoice(invoice: Invoice): Boolean {
        if (invoice.id.isBlank()) return false
        val body = JSONObject().apply {
            put("id", invoice.id)
            put("username", invoice.username)
            put("email", invoice.email)
            put("paket", invoice.paket)
            put("harga", invoice.harga)
            put("status", invoice.status)
            put("dibuat", invoice.dibuat)
            put("dibayar", invoice.dibayar)
            put("confirmedBy", invoice.confirmedBy)
            put("kodeLisensi", invoice.kodeLisensi)
            put("buktiBase64", invoice.buktiBase64)
            put("jumlah_tv", invoice.jumlahTv)
        }
        return httpPost(
            "invoices?on_conflict=id",
            body.toString(),
            "resolution=merge-duplicates,return=minimal",
        )
    }

    fun saveLicenseRequest(
        username: String,
        paket: String,
        harga: Int,
        invoiceId: String,
        jumlahTv: Int,
    ): Boolean {
        if (username.isBlank() || invoiceId.isBlank()) return false
        val encodedInvoice = URLEncoder.encode(invoiceId, "UTF-8")
        val existing = httpGet(
            "notifications?select=id&type=eq.license_request&invoiceId=eq.$encodedInvoice&limit=1",
        )
        if (existing != null) {
            try {
                if (JSONArray(existing).length() > 0) return true
            } catch (_: Exception) {
            }
        }
        val body = JSONObject().apply {
            put("type", "license_request")
            put("username", "rrbilling")
            put("user", username)
            put("paket", paket)
            put("harga", harga)
            put("jumlah_tv", jumlahTv)
            put("invoiceId", invoiceId)
            put("title", "Permintaan Lisensi Baru")
            put("body", "$username meminta lisensi $paket (${jumlahTv} TV)")
            put("sentAt", System.currentTimeMillis())
            put("read", false)
        }
        return httpPost("notifications", body.toString())
    }

    fun fetchLicenseStatus(username: String): LicenseStatus? {
        if (username.isBlank()) return null
        val encodedUser = URLEncoder.encode(username, "UTF-8")
        val json = httpGet("license_status?select=*&id=eq.$encodedUser&limit=1") ?: return null
        return try {
            val arr = JSONArray(json)
            if (arr.length() == 0) return null
            val o = arr.getJSONObject(0)
            val status = o.optString("status", "")
            if (status.isBlank()) return null
            val paket = o.optString("paket", "")
            val pesan = o.optString("pesan", "")
            val maxTv = o.optInt("maxTv", 0)
            val maxPc = o.optInt("maxPc", 0)
            val promoAddTv = o.optInt("promoAddTv", 0)
            val totalFromPesan = Regex("total\\s+(\\d+)\\s*TV", RegexOption.IGNORE_CASE)
                .find(pesan)?.groupValues?.getOrNull(1)?.toIntOrNull()
            val effectiveMaxTv = when {
                promoAddTv > 0 -> promoAddTv
                maxTv > 0 -> maxTv
                maxPc > 0 -> maxPc
                totalFromPesan != null && totalFromPesan > 0 -> totalFromPesan
                pesan.contains("unlimited", ignoreCase = true) -> 0
                pesan.contains("LIFETIME", ignoreCase = true) -> 0
                pesan.contains("TAHUNAN", ignoreCase = true) || pesan.contains("1 Tahun", ignoreCase = true) -> 15
                pesan.contains("3BULAN", ignoreCase = true) || pesan.contains("3 Bulan", ignoreCase = true) -> 8
                pesan.contains("BULANAN", ignoreCase = true) || pesan.contains("1 Bulan", ignoreCase = true) -> 5
                else -> 2
            }
            val clientEnabled = if (o.has("clientEnabled")) {
                o.optBoolean("clientEnabled", true)
            } else {
                paket.uppercase(Locale.ROOT) != "BULANAN"
            }
            LicenseStatus(
                status = status,
                pesan = pesan,
                expiresAt = o.optString("expiresAt", ""),
                maxTv = effectiveMaxTv,
                promoAddTv = promoAddTv,
                paket = paket,
                clientEnabled = clientEnabled,
            )
        } catch (e: Exception) {
            Log.e(TAG, "parse license_status failed: ${e.message}")
            null
        }
    }

    fun fetchConfirmedInvoices(username: String): List<Invoice> {
        if (username.isBlank()) return emptyList()
        val encodedUser = URLEncoder.encode(username, "UTF-8")
        val json = httpGet(
            "invoices?select=*&username=eq.$encodedUser&status=eq.CONFIRMED&order=dibuat.desc&limit=50",
        ) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val id = o.optString("id", "")
                if (id.isBlank()) return@mapNotNull null
                Invoice(
                    id = id,
                    username = o.optString("username", ""),
                    email = o.optString("email", ""),
                    paket = o.optString("paket", ""),
                    harga = o.optInt("harga", 0),
                    status = o.optString("status", "CONFIRMED"),
                    dibuat = o.optLong("dibuat", 0L),
                    dibayar = o.optLong("dibayar", 0L),
                    confirmedBy = o.optString("confirmedBy", ""),
                    kodeLisensi = o.optString("kodeLisensi", ""),
                    buktiBase64 = o.optString("buktiBase64", ""),
                    jumlahTv = o.optInt("jumlah_tv", o.optInt("jumlahTv", 0)),
                    mode = o.optString("mode", ""),
                    baseMaxTv = o.optInt("baseMaxTv", 0),
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "parse invoices failed: ${e.message}")
            emptyList()
        }
    }

    private fun httpGet(path: String): String? {
        val connection = (URL("$BASE_URL/rest/v1/$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("apikey", API_KEY)
            setRequestProperty("Authorization", "Bearer $API_KEY")
        }
        return try {
            val code = connection.responseCode
            if (code in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                Log.e(TAG, "GET $path HTTP $code: $detail")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "GET $path failed: ${e.message}")
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun httpPost(
        path: String,
        body: String,
        prefer: String = "return=minimal",
    ): Boolean {
        val connection = (URL("$BASE_URL/rest/v1/$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("apikey", API_KEY)
            setRequestProperty("Authorization", "Bearer $API_KEY")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Prefer", prefer)
        }
        return try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
            val code = connection.responseCode
            if (code in 200..299) {
                Log.i(TAG, "POST $path HTTP $code")
                true
            } else {
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                Log.e(TAG, "POST $path HTTP $code: $detail")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "POST $path failed: ${e.message}")
            false
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val TAG = "SupabaseRepo"
        private const val BASE_URL = "https://nqaucjpbnewckedqcezb.supabase.co"
        private const val API_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im5xYXVjanBibmV3Y2tlZHFjZXpiIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODg1MjUyNzUsImV4cCI6MjEwNDEwMTI3NX0.xi-h-3E2Yww95HzXsNmmiOmdvMKi_TaFNw8Op7xryig"
    }
}
