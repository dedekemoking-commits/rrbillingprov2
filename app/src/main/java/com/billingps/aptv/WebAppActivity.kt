package com.billingps.aptv

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebViewAssetLoader
import com.billingps.aptv.tv.TvHubManager
import com.billingps.aptv.utils.ApkServer
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WebAppActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var assetLoader: WebViewAssetLoader
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val callback = fileChooserCallback
        fileChooserCallback = null
        if (callback == null) return@registerForActivityResult
        if (result.resultCode != RESULT_OK) {
            callback.onReceiveValue(null)
            return@registerForActivityResult
        }
        val data = result.data
        val uris = when {
            data?.clipData != null -> {
                val clip = data.clipData!!
                Array(clip.itemCount) { clip.getItemAt(it).uri }
            }
            data?.data != null -> arrayOf(data.data!!)
            else -> emptyArray()
        }
        callback.onReceiveValue(uris)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createNotificationChannel()
        TvHubManager.ensureStarted(this)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!Python.isStarted()) {
                    Python.start(AndroidPlatform(this@WebAppActivity))
                }
            } catch (e: Exception) {
                Log.e("BillingPS", "Python start failed", e)
            }
        }

        val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (debuggable) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        assetLoader = WebViewAssetLoader.Builder()
            .setDomain("localhost")
            .setHttpAllowed(true)
            .addPathHandler("/", WebAssetPathHandler(this))
            .build()

        webView = WebView(this)
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?,
            ): WebResourceResponse? {
                val url = request?.url ?: return null
                return assetLoader.shouldInterceptRequest(url)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?,
            ): Boolean {
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = filePathCallback
                val intent = try {
                    fileChooserParams?.createIntent()
                } catch (_: Exception) {
                    null
                } ?: Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }
                return try {
                    fileChooserLauncher.launch(intent)
                    true
                } catch (e: Exception) {
                    Log.e("WebAppActivity", "File chooser failed: ${e.message}")
                    fileChooserCallback = null
                    false
                }
            }
        }

        val hubHost = ApkServer.getLocalIpAddress(this)
        val url = buildString {
            append("http://localhost/index.html")
            if (!hubHost.isNullOrBlank()) append("?hub=$hubHost")
        }
        Log.i("WebAppActivity", "Loading $url")
        webView.loadUrl(url)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })
    }

    override fun onDestroy() {
        fileChooserCallback?.onReceiveValue(null)
        fileChooserCallback = null
        try {
            webView.destroy()
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    private class WebAssetPathHandler(
        private val context: Context,
    ) : WebViewAssetLoader.PathHandler {
        override fun handle(path: String): WebResourceResponse? {
            val cleanPath = path.substringBefore('?').substringBefore('#')
            val assetPath = when {
                cleanPath.isBlank() || cleanPath == "/" -> "web/index.html"
                else -> "web" + if (cleanPath.startsWith("/")) cleanPath else "/$cleanPath"
            }
            return try {
                val mime = mimeType(assetPath)
                val encoding = if (
                    mime.startsWith("text/") ||
                    mime.contains("javascript") ||
                    mime.contains("json")
                ) "UTF-8" else null
                WebResourceResponse(mime, encoding, context.assets.open(assetPath)).apply {
                    setResponseHeaders(mapOf("Cache-Control" to "no-store"))
                }
            } catch (e: Exception) {
                Log.w("WebAppActivity", "Asset not found: $assetPath (${e.message})")
                null
            }
        }

        private fun mimeType(path: String): String = when {
            path.endsWith(".html") -> "text/html"
            path.endsWith(".js") -> "application/javascript"
            path.endsWith(".css") -> "text/css"
            path.endsWith(".json") -> "application/json"
            path.endsWith(".png") -> "image/png"
            path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
            path.endsWith(".gif") -> "image/gif"
            path.endsWith(".svg") -> "image/svg+xml"
            path.endsWith(".webp") -> "image/webp"
            path.endsWith(".woff") -> "font/woff"
            path.endsWith(".woff2") -> "font/woff2"
            path.endsWith(".ttf") -> "font/ttf"
            path.endsWith(".mp4") -> "video/mp4"
            else -> "application/octet-stream"
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val billingChannel = NotificationChannel(
                "billing_channel",
                "Billing Notifikasi",
                NotificationManager.IMPORTANCE_HIGH,
            )
            val timerChannel = NotificationChannel(
                com.billingps.aptv.TimerService.CHANNEL_ID,
                "Timer Billing",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Notifikasi timer billing berjalan di latar belakang"
                setSound(null, null)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(billingChannel)
            nm.createNotificationChannel(timerChannel)
        }
    }
}
