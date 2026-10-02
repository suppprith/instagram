package com.suppprith.dms.web

import android.Manifest
import android.annotation.SuppressLint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.webkit.ScriptHandler
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.suppprith.dms.MainActivity
import com.suppprith.dms.R
import com.suppprith.dms.cage.RuleMatcher
import com.suppprith.dms.cage.Rules
import com.suppprith.dms.cage.UrlParts
import com.suppprith.dms.graph
import com.suppprith.dms.notify.BadgeCount
import com.suppprith.dms.ui.Route
import com.suppprith.dms.ui.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

/**
 * Owns the caged WebView: settings, injection, the bridge, the native gate, navigation and
 * session. One per [MainActivity].
 */
class WebHost(private val activity: MainActivity, private val state: UiState) {
    private val graph = activity.graph
    private val bridge = JsBridge(::onPageMessage)
    private val docStartSupported = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
    private var scriptHandler: ScriptHandler? = null
    private var matcher = RuleMatcher(graph.patches.rules.value)
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var destroyed = false
    private var avatarUrl: String? = null

    val webView: WebView = WebView(activity)

    init {
        configure()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configure() {
        webView.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        // Match the theme so dark mode never flashes white.
        webView.setBackgroundColor(ContextCompat.getColor(activity, R.color.bg))
        webView.overScrollMode = View.OVER_SCROLL_NEVER

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = false
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            userAgentString = UserAgent.chromeMobile(activity)
        }
        // Follow the app theme through prefers-color-scheme; Instagram has its own dark mode.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, false)
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true) // Meta's login chain
        }

        webView.webViewClient = CageClient(this)
        webView.webChromeClient = CageChromeClient(this)
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            saveDownload(url, userAgent, contentDisposition, mimeType)
        }

        if (!bridge.install(webView)) graph.log.w("web", "WebMessageListener unsupported; bridge off")
        installScript(graph.patches.rules.value)

        // Rules patched while running: native gate now, page now, next document at start.
        activity.lifecycleScope.launch {
            graph.patches.rules.collect { rules ->
                if (rules == matcher.rules) return@collect
                matcher = RuleMatcher(rules)
                installScript(rules)
                bridge.send("rules", mapOf("rules" to Json.parseToJsonElement(rules.toJson())))
            }
        }
    }

    private fun installScript(rules: Rules) {
        if (!docStartSupported) return
        scriptHandler?.remove()
        scriptHandler = WebViewCompat.addDocumentStartJavaScript(webView, graph.patches.injectionScript(rules), setOf(JsBridge.ORIGIN))
    }

    fun start(savedState: Bundle?) {
        val restored = savedState?.let { webView.restoreState(it) } != null
        if (!restored) webView.loadUrl(INBOX_URL)
        refreshSignedIn()
    }

    fun saveState(out: Bundle) {
        webView.saveState(out)
    }

    // ------------------------------------------------------------------ navigation decisions

    /** Main-frame navigation. Returns true when the WebView must not load the URL itself. */
    fun overrideNavigation(url: String): Boolean {
        if (matcher.isBlockedUrl(url)) {
            graph.log.i("cage", "blocked load ${UrlParts.parse(url)?.path}")
            webView.loadUrl(INBOX_URL)
            return true
        }
        return when (val decision = UrlPolicy.decide(url)) {
            NavDecision.InApp -> false
            is NavDecision.External -> {
                Links.openExternal(activity, decision.url)
                true
            }
            is NavDecision.System -> {
                Links.openWithSystem(activity, decision.url)
                true
            }
            NavDecision.Ignore -> true
        }
    }

    fun openFromPopup(url: String) {
        if (!overrideNavigation(url)) webView.loadUrl(url)
    }

    fun onPageStarted(url: String?) {
        bridge.reset()
        setRoute(url)
        // Old WebViews without document-start scripts: inject as early as we can. The native gate covers the gap.
        if (!docStartSupported && Route.of(url).isInstagram && UrlParts.parse(url)?.host == JsBridge.HOST) {
            webView.evaluateJavascript(graph.patches.injectionScript(matcher.rules), null)
        }
    }

    fun onPageFinished(url: String?) {
        setRoute(url)
        refreshSignedIn()
        if (!state.ready && !Route.of(url).isInstagram) markReady() // Meta login pages have no cage
    }

    /** Second line of defence: Instagram's in-page routing passes through here too. */
    fun onHistoryChanged(url: String?) {
        setRoute(url)
        if (matcher.isBlockedUrl(url)) {
            // Give cage.js a moment to bounce first (it may return to the last thread instead).
            webView.postDelayed({
                if (!destroyed && matcher.isBlockedUrl(webView.url)) {
                    graph.log.i("cage", "native gate bounced ${UrlParts.parse(webView.url)?.path}")
                    webView.loadUrl(INBOX_URL)
                }
            }, 400)
        }
    }

    fun onMainFrameError(code: Int) {
        if (code in OFFLINE_ERRORS) {
            graph.log.w("web", "main frame error $code")
            state.offline = true
            markReady()
        }
    }

    fun onRendererGone() {
        graph.log.w("web", "renderer gone; recreating")
        destroy()
        activity.recreate()
    }

    private fun setRoute(url: String?) {
        if (url == null) return
        val next = Route.of(url)
        if (next != state.route) state.route = next
    }

    private fun refreshSignedIn() {
        val cookies = runCatching { CookieManager.getInstance().getCookie(INSTAGRAM_ORIGIN) }.getOrNull()
        state.signedIn = BadgeCount.cookie(cookies, "sessionid") != null
    }

    fun markReady() {
        state.ready = true
    }

    // ------------------------------------------------------------------ bridge

    private fun onPageMessage(message: PageMessage) {
        when (message) {
            is PageMessage.Hello -> refreshSignedIn()
            is PageMessage.Ready -> {
                refreshSignedIn()
                markReady()
            }
            is PageMessage.RouteChanged -> setRoute(INSTAGRAM_ORIGIN + message.path)
            is PageMessage.Blocked -> graph.log.i("cage", "page bounced ${message.path}")
            is PageMessage.Badge -> {
                state.badge = message.count
                activity.lifecycleScope.launch { graph.settings.setLastBadge(message.count) }
            }
            is PageMessage.User -> {
                state.username = message.username
                message.avatarUrl?.let(::loadAvatar)
            }
            PageMessage.Haptic -> haptic()
            is PageMessage.Error -> graph.log.w("cage.js", "${message.where}: ${message.message}")
        }
    }

    /** The Profile tab shows the user's photo, like Instagram's own tab bar. */
    fun loadCachedAvatar() {
        activity.lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) { graph.avatars.cached() }
            if (bitmap != null && state.avatar == null) state.avatar = bitmap.asImageBitmap()
        }
    }

    private fun loadAvatar(url: String) {
        if (url == avatarUrl) return
        avatarUrl = url
        activity.lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                if (graph.avatars.cachedUrl() == url) graph.avatars.cached() else graph.avatars.download(url)
            }
            if (bitmap != null) state.avatar = bitmap.asImageBitmap()
        }
    }

    private fun haptic() {
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        }
        webView.performHapticFeedback(constant)
    }

    fun navigatePath(path: String) {
        val sent = state.route.isInstagram && bridge.send("navigate", mapOf("path" to JsonPrimitive(path)))
        if (!sent) webView.loadUrl(INSTAGRAM_ORIGIN + path)
    }

    /** Ask the page for a fresh unread count (app returned to the foreground). */
    fun pollBadge() {
        bridge.send("badge")
    }

    /** Back: leave full-screen video, then walk back skipping feed pages, then the inbox, then exit. */
    fun handleBack(): Boolean {
        if (state.customView != null) {
            hideCustomView()
            return true
        }
        val route = state.route
        if (route.isInbox || (!state.signedIn && !webView.canGoBack())) return false
        val history = webView.copyBackForwardList()
        var index = history.currentIndex - 1
        while (index >= 0 && matcher.isBlockedUrl(history.getItemAtIndex(index)?.url)) index--
        if (index >= 0) {
            webView.goBackOrForward(index - history.currentIndex)
        } else {
            webView.loadUrl(INBOX_URL)
        }
        return true
    }

    fun retry() {
        state.offline = false
        if (webView.url.isNullOrBlank()) webView.loadUrl(INBOX_URL) else webView.reload()
    }

    // ------------------------------------------------------------------ session

    fun signOut(onDone: () -> Unit) {
        val cookies = CookieManager.getInstance()
        cookies.removeAllCookies {
            cookies.flush()
            WebStorage.getInstance().deleteAllData()
            webView.clearCache(true)
            webView.clearHistory()
            state.signedIn = false
            state.username = null
            state.avatar = null
            avatarUrl = null
            graph.avatars.clear()
            state.badge = 0
            webView.loadUrl(INBOX_URL)
            graph.log.i("session", "signed out of this device")
            onDone()
        }
    }

    fun onPause() {
        webView.onPause()
        CookieManager.getInstance().flush()
    }

    fun onResume() {
        webView.onResume()
        refreshSignedIn()
        pollBadge()
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        scriptHandler?.remove()
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.stopLoading()
        webView.webChromeClient = null
        webView.webViewClient = WebViewClient()
        webView.destroy()
    }

    // ------------------------------------------------------------------ chrome client hooks

    fun showFileChooser(callback: ValueCallback<Array<Uri>>, params: WebChromeClient.FileChooserParams): Boolean =
        activity.filePicker.show(callback, params)

    /** Microphone for voice notes, only for Instagram, only after the app holds RECORD_AUDIO. */
    fun onPermissionRequest(request: PermissionRequest) {
        activity.runOnUiThread {
            val fromInstagram = request.origin.scheme == "https" && request.origin.host == JsBridge.HOST
            val wanted = request.resources.filter { it == PermissionRequest.RESOURCE_AUDIO_CAPTURE }
            if (!fromInstagram || wanted.isEmpty()) {
                request.deny()
                return@runOnUiThread
            }
            activity.withPermission(Manifest.permission.RECORD_AUDIO) { granted ->
                if (granted) request.grant(wanted.toTypedArray()) else request.deny()
            }
        }
    }

    fun showCustomView(view: View, callback: WebChromeClient.CustomViewCallback) {
        if (state.customView != null) {
            callback.onCustomViewHidden()
            return
        }
        customViewCallback = callback
        state.customView = view
        activity.setFullScreen(true)
    }

    fun hideCustomView() {
        val callback = customViewCallback
        customViewCallback = null
        state.customView = null
        activity.setFullScreen(false)
        callback?.onCustomViewHidden()
    }

    private fun saveDownload(url: String, userAgent: String, contentDisposition: String?, mimeType: String?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            activity.withPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) { granted ->
                if (granted) Downloads.enqueue(activity, url, userAgent, contentDisposition, mimeType)
            }
        } else {
            Downloads.enqueue(activity, url, userAgent, contentDisposition, mimeType)
        }
    }

    companion object {
        const val INSTAGRAM_ORIGIN = "https://www.instagram.com"
        const val INBOX_PATH = Rules.INBOX_PATH
        const val INBOX_URL = INSTAGRAM_ORIGIN + INBOX_PATH

        private val OFFLINE_ERRORS = setOf(
            WebViewClient.ERROR_HOST_LOOKUP,
            WebViewClient.ERROR_CONNECT,
            WebViewClient.ERROR_TIMEOUT,
            WebViewClient.ERROR_IO,
            WebViewClient.ERROR_PROXY_AUTHENTICATION,
        )
    }
}
