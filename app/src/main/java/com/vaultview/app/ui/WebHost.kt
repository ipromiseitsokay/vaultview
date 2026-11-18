package com.vaultview.app.ui

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box

/**
 * The one vimm.net page the app keeps open behind its own screens.
 *
 * Creating a WebView makes Android redraw the whole window in an odd way for a frame or two, which
 * showed up as the side rail flashing every time a game page opened. So there is exactly one,
 * created with the app's main screen and reused by every game page.
 */
object WebHost {
    var view: WebView? = null
        private set
    /** True while a game page wants the real site visible (the human check). */
    var showing by mutableStateOf(false)
    var userAgent = ""
        private set

    /** What the current game page wants to hear about. */
    interface Listener {
        fun onPageFinished(view: WebView)
        fun onFormDownload(url: String, method: String, fieldsJson: String)
        fun onFileDownload(url: String, userAgent: String?)
        fun onExternalLink(url: String)
    }

    private var listener: Listener? = null
    private var loadedUrl: String? = null
    private var finished = false

    /** Shows [url] for [l]; if that page is already loaded, reports it straight away. */
    fun open(url: String, l: Listener) {
        listener = l
        val v = view ?: return
        if (loadedUrl == url && finished) { l.onPageFinished(v); return }
        loadedUrl = url
        finished = false
        v.loadUrl(url)
    }

    fun reload() { finished = false; view?.reload() }

    fun release(l: Listener) {
        if (listener !== l) return
        listener = null
        showing = false
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun create(ctx: android.content.Context) = WebView(ctx).apply {
        // Transparent: the screens in front give the dark colour, and nothing can bleed past them.
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        isSoundEffectsEnabled = false
        // Always VISIBLE: vimm.net's Cloudflare check only finishes on a page that's being drawn.
        // The app covers it with an opaque layer instead (see Host below).
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        userAgent = settings.userAgentString
        val main = Handler(Looper.getMainLooper())
        addJavascriptInterface(object {
            @JavascriptInterface
            fun download(url: String, method: String, fieldsJson: String) {
                main.post { listener?.onFormDownload(url, method, fieldsJson) }
            }
        }, "VaultView")
        setDownloadListener { url, ua, _, _, _ -> listener?.onFileDownload(url, ua) }
        // While hidden behind the app, taps that fall through empty space must not reach the page.
        setOnTouchListener { _, _ -> !showing }
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                if (url == "about:blank") return
                finished = true
                CookieManager.getInstance().flush()
                listener?.onPageFinished(view)
            }
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val host = request.url.host.orEmpty()
                val ours = host.endsWith("vimm.net") || host.endsWith("cloudflare.com")
                if (!ours && request.isForMainFrame) { listener?.onExternalLink(request.url.toString()); return true }
                return false
            }
        }
    }

    /** Lives in the root layout, under every screen, for as long as the activity does. */
    @Composable
    fun Host(modifier: Modifier = Modifier) {
        Box(modifier) {
            Page(Modifier.matchParentSize())
            // Opaque cover while the page isn't wanted: it keeps rendering (so checks complete), unseen.
            if (!showing) Box(Modifier.matchParentSize().background(Bg))
        }
    }

    @Composable
    private fun Page(modifier: Modifier) {
        AndroidView(
            factory = { ctx -> create(ctx).also { view = it; loadedUrl = null; finished = false } },
            update = { w ->
                // While hidden, the page must not take the D-pad either.
                w.isFocusable = showing
                w.isFocusableInTouchMode = showing
                w.descendantFocusability = if (showing) ViewGroup.FOCUS_AFTER_DESCENDANTS else ViewGroup.FOCUS_BLOCK_DESCENDANTS
                if (!showing) w.clearFocus()
            },
            modifier = modifier,
        )
        DisposableEffect(Unit) {
            onDispose { view?.destroy(); view = null; listener = null; showing = false }
        }
    }
}
