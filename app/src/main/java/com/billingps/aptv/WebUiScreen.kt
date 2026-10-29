package com.billingps.aptv

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import android.view.ViewGroup
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import com.billingps.aptv.models.MainViewModel
import com.billingps.aptv.models.TvData
import com.billingps.aptv.ui.screens.ClientApkInstallerDialog
import com.billingps.aptv.utils.ApkServer

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebUiScreen(
    mainViewModel: MainViewModel,
    tvViewModel: TvViewModel,
) {
    val context = LocalContext.current
    val activity = context as MainActivity
    val installTarget = remember { mutableStateOf<TvData?>(null) }
    val bridge = remember {
        RRBridge(activity, mainViewModel, tvViewModel) { tv ->
            installTarget.value = tv
        }
    }
    val assetLoader = remember {
        WebViewAssetLoader.Builder()
            .setDomain("localhost")
            .setHttpAllowed(true)
            .addPathHandler("/", LocalWebAssetPathHandler(context))
            .build()
    }

    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(android.graphics.Color.BLACK)

            val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            if (debuggable) {
                WebView.setWebContentsDebuggingEnabled(true)
            }

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                cacheMode = WebSettings.LOAD_DEFAULT
                textZoom = 100
            }

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): WebResourceResponse? {
                    val url = request?.url ?: return null
                    return assetLoader.shouldInterceptRequest(url)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    bridge.onPageFinished()
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onJsConfirm(
                    view: WebView?,
                    url: String?,
                    message: String?,
                    result: JsResult?,
                ): Boolean {
                    if (result == null) return false
                    AlertDialog.Builder(context)
                        .setMessage(message)
                        .setCancelable(false)
                        .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
                        .setNegativeButton(android.R.string.cancel) { _, _ -> result.cancel() }
                        .show()
                    return true
                }

                override fun onJsAlert(
                    view: WebView?,
                    url: String?,
                    message: String?,
                    result: JsResult?,
                ): Boolean {
                    if (result == null) return false
                    AlertDialog.Builder(context)
                        .setMessage(message)
                        .setCancelable(false)
                        .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
                        .show()
                    return true
                }
            }

            addJavascriptInterface(bridge, "BillingNative")
            bridge.attach(this)

            val hubHost = ApkServer.getLocalIpAddress(context)
            val url = buildString {
                append("http://localhost/index.html?native=1")
                if (!hubHost.isNullOrBlank()) append("&hub=$hubHost")
            }
            Log.i("WebUiScreen", "Loading $url")
            loadUrl(url)
        }
    }

    BackHandler {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            activity.finish()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            bridge.destroy()
            try {
                webView.removeJavascriptInterface("BillingNative")
                webView.stopLoading()
                webView.destroy()
            } catch (_: Exception) {
            }
        }
    }

    AndroidView(
        factory = { webView },
        modifier = Modifier.fillMaxSize(),
    )

    installTarget.value?.let { tv ->
        ClientApkInstallerDialog(
            tvIp = tv.ip,
            tvNama = tv.nama,
            onDismiss = { installTarget.value = null },
        )
    }
}

private class LocalWebAssetPathHandler(
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
            Log.w("WebUiScreen", "Asset not found: $assetPath (${e.message})")
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
