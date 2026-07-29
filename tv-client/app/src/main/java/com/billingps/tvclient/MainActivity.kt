package com.billingps.tvclient

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.core.content.ContextCompat
import com.billingps.tvclient.firestore.CloudHelper
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*

class MainActivity : Activity() {

    private val cloudHelper = CloudHelper()
    private val scope = CoroutineScope(Dispatchers.Main)
    private var statusText: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            val prefs = getSharedPreferences("tvclient_prefs", Context.MODE_PRIVATE)
            val savedUsername = prefs.getString(TvClientService.PREF_USERNAME, "") ?: ""
            val savedTvId = prefs.getString(TvClientService.PREF_TV_ID, "") ?: ""
            val savedTvNama = prefs.getString(TvClientService.PREF_TV_NAMA, "") ?: ""

            if (savedUsername.isNotEmpty() && savedTvId.isNotEmpty()) {
                if (!TvClientService.isRunning()) {
                    val intent = Intent(this, TvClientService::class.java).apply {
                        action = TvClientService.ACTION_START
                    }
                    ContextCompat.startForegroundService(this, intent)
                }
                showConnectedScreen(savedUsername, savedTvNama)
                return
            }

            showSetupScreen()
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "onCreate error: ${e.message}", e)
            val msg = "Error: ${e.message}\n\n${e.stackTraceToString()}"
            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.parseColor("#FF1A1A2E"))
                gravity = Gravity.CENTER
                setPadding(32, 32, 32, 32)
            }
            val tv = TextView(this).apply {
                text = msg
                setTextColor(Color.RED)
                textSize = 14f
            }
            layout.addView(tv)
            setContentView(layout)
        }
    }

    private fun showSetupScreen() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#FF1A1A2E"))
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(48, 64, 48, 48)
        }

        val titleText = TextView(this).apply {
            text = "BillingPS TV Client"
            setTextColor(Color.parseColor("#FF00FF00"))
            textSize = 24f
        }

        val subtitleText = TextView(this).apply {
            text = "Masukkan username billing untuk memulai"
            setTextColor(Color.parseColor("#FFAAAAAA"))
            textSize = 14f
            setPadding(0, 8, 0, 24)
        }

        val usernameInput = EditText(this).apply {
            hint = "Username Billing"
            setHintTextColor(Color.parseColor("#FF666666"))
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#FF2A2A4E"))
            setPadding(16, 12, 16, 12)
        }

        statusText = TextView(this).apply {
            text = ""
            setTextColor(Color.parseColor("#FF00FF00"))
            textSize = 14f
            gravity = Gravity.CENTER
        }

        val connectButton = Button(this).apply {
            text = "Hubungkan"
            setBackgroundColor(Color.parseColor("#FF00C853"))
            setTextColor(Color.BLACK)
            setOnClickListener {
                val username = usernameInput.text.toString().trim()
                if (username.isEmpty()) {
                    Toast.makeText(this@MainActivity, "Masukkan username", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                statusText?.text = "Mencari TV..."
                connectToBilling(username)
            }
        }

        layout.addView(titleText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        layout.addView(subtitleText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        layout.addView(usernameInput, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, 16) })
        layout.addView(connectButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER })
        layout.addView(statusText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 16, 0, 0) })

        setContentView(layout)
    }

    private fun showConnectedScreen(username: String, tvNama: String) {
        try {
            val namaDisp = if (tvNama.isEmpty()) "TV" else tvNama
            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.parseColor("#FF1A1A2E"))
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(48, 64, 48, 48)
            }

            val connectedText = TextView(this).apply {
                text = "\u2713 TERHUBUNG"
                setTextColor(Color.parseColor("#FF00FF00"))
                textSize = 28f
                gravity = Gravity.CENTER
            }

            val namaText = TextView(this).apply {
                text = namaDisp
                setTextColor(Color.WHITE)
                textSize = 22f
                gravity = Gravity.CENTER
                setPadding(0, 16, 0, 4)
            }

            val userText = TextView(this).apply {
                text = username
                setTextColor(Color.parseColor("#FFAAAAAA"))
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 32)
            }

            val statusIndicator = TextView(this).apply {
                text = "Menunggu perintah..."
                setTextColor(Color.parseColor("#FF00FF00"))
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 32)
            }

            val disconnectButton = Button(this).apply {
                text = "Putuskan"
                setBackgroundColor(Color.parseColor("#FFD32F2F"))
                setTextColor(Color.WHITE)
                setOnClickListener {
                    stopService(Intent(this@MainActivity, TvClientService::class.java))
                    getSharedPreferences("tvclient_prefs", Context.MODE_PRIVATE).edit().clear().apply()
                    showSetupScreen()
                }
            }

            layout.addView(connectedText, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER })
            layout.addView(namaText, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER })
            layout.addView(userText, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER })
            layout.addView(statusIndicator, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER })
            layout.addView(disconnectButton, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER })

            setContentView(layout)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "showConnectedScreen error: ${e.message}", e)
        }
    }

    private fun connectToBilling(username: String) {
        scope.launch {
            try {
                val signedIn = withContext(Dispatchers.IO) { cloudHelper.ensureSignedIn() }
                if (!signedIn) {
                    Toast.makeText(this@MainActivity, "Gagal terhubung ke server", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val tvList = withContext(Dispatchers.IO) { fetchTvList(username) }
                if (tvList.isEmpty()) {
                    Toast.makeText(this@MainActivity, "Tidak ada TV ditemukan untuk user $username", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                showTvSelectionDialog(username, tvList)
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "connectToBilling error: ${e.message}", e)
                Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun fetchTvList(username: String): List<Map<String, Any>> {
        return withContext(Dispatchers.IO) {
            try {
                val task = FirebaseFirestore.getInstance()
                    .collection("billingps_users").document(username)
                    .get()
                val snap = Tasks.await(task, 15, java.util.concurrent.TimeUnit.SECONDS)
                if (!snap.exists()) return@withContext emptyList()
                snap.get("tvList") as? List<Map<String, Any>> ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    private fun showTvSelectionDialog(username: String, tvRaw: List<Map<String, Any>>) {
        val items = tvRaw.mapNotNull { it["nama"] as? String }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Pilih TV")
            .setItems(items) { _, which ->
                val tvId = tvRaw[which]["id"] as? String ?: ""
                val tvNama = items[which]
                onTvSelected(username, tvId, tvNama)
            }
            .setCancelable(false)
            .show()
    }

    private fun onTvSelected(username: String, tvId: String, tvNama: String) {
        val prefs = getSharedPreferences("tvclient_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString(TvClientService.PREF_USERNAME, username)
            .putString(TvClientService.PREF_TV_ID, tvId)
            .putString(TvClientService.PREF_TV_NAMA, tvNama)
            .apply()

        Toast.makeText(this, "Terhubung ke $tvNama", Toast.LENGTH_SHORT).show()
        startServiceAndShowStatus(username, tvNama)
    }

    private fun startServiceAndShowStatus(username: String, tvNama: String) {
        val intent = Intent(this, TvClientService::class.java).apply {
            action = TvClientService.ACTION_START
        }
        ContextCompat.startForegroundService(this, intent)
        showConnectedScreen(username, tvNama)
    }
}
