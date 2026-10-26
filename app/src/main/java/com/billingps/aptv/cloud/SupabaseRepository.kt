package com.billingps.aptv.cloud

import android.util.Log
import com.billingps.aptv.models.Transaksi
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
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

    companion object {
        private const val TAG = "SupabaseRepo"
        private const val BASE_URL = "https://nqaucjpbnewckedqcezb.supabase.co"
        private const val API_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im5xYXVjanBibmV3Y2tlZHFjZXpiIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODg1MjUyNzUsImV4cCI6MjEwNDEwMTI3NX0.xi-h-3E2Yww95HzXsNmmiOmdvMKi_TaFNw8Op7xryig"
    }
}
