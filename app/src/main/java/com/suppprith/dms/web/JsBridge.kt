package com.suppprith.dms.web

import android.net.Uri
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.suppprith.dms.cage.UrlParts
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** A typed message from cage.js. */
sealed interface PageMessage {
    data class Hello(val path: String) : PageMessage
    data class Ready(val path: String) : PageMessage
    data class RouteChanged(val path: String) : PageMessage
    data class Blocked(val path: String) : PageMessage
    data class Badge(val count: Int) : PageMessage
    data class User(val username: String, val avatarUrl: String?) : PageMessage
    data object Haptic : PageMessage
    data class Error(val where: String, val message: String) : PageMessage
}

/**
 * The page <-> app bridge, exposed only to https://www.instagram.com's main frame as `window.dms`.
 * Unlike addJavascriptInterface, nothing is visible to other origins or iframes.
 */
class JsBridge(private val onMessage: (PageMessage) -> Unit) : WebViewCompat.WebMessageListener {
    private var reply: JavaScriptReplyProxy? = null

    val isSupported: Boolean get() = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)

    /** True once the current document has said hello, so app -> page messages can be delivered. */
    val isConnected: Boolean get() = reply != null

    fun install(webView: WebView): Boolean {
        if (!isSupported) return false
        WebViewCompat.addWebMessageListener(webView, OBJECT_NAME, setOf(ORIGIN), this)
        return true
    }

    /** A new document is loading; its reply proxy arrives with its first message. */
    fun reset() {
        reply = null
    }

    override fun onPostMessage(
        view: WebView,
        message: WebMessageCompat,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        replyProxy: JavaScriptReplyProxy,
    ) {
        if (!isMainFrame || sourceOrigin.scheme != "https" || sourceOrigin.host != HOST) return
        reply = replyProxy
        val text = message.data?.takeIf { it.length <= MAX_MESSAGE } ?: return
        parse(text)?.let(onMessage)
    }

    fun send(type: String, fields: Map<String, JsonElement> = emptyMap()): Boolean {
        val proxy = reply ?: return false
        val body = buildJsonObject {
            put("type", JsonPrimitive(type))
            fields.forEach { (k, v) -> put(k, v) }
        }
        return runCatching { proxy.postMessage(body.toString()) }.isSuccess
    }

    companion object {
        const val OBJECT_NAME = "dms"
        const val HOST = "www.instagram.com"
        const val ORIGIN = "https://$HOST"
        private const val MAX_MESSAGE = 8 * 1024

        /** Profile photos come from Instagram's CDNs only. */
        fun isInstagramImage(url: String): Boolean {
            val parts = UrlParts.parse(url) ?: return false
            return parts.scheme == "https" &&
                (parts.host.endsWith(".cdninstagram.com") || parts.host.endsWith(".fbcdn.net"))
        }

        fun parse(text: String): PageMessage? {
            val obj = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return null
            fun str(key: String) = (obj[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
            return when (str("type")) {
                "hello" -> PageMessage.Hello(str("path"))
                "ready" -> PageMessage.Ready(str("path"))
                "route" -> PageMessage.RouteChanged(str("path"))
                "blocked" -> PageMessage.Blocked(str("path"))
                "badge" -> (obj["count"] as? JsonPrimitive)?.intOrNull?.takeIf { it >= 0 }?.let { PageMessage.Badge(it) }
                "user" -> str("username").takeIf { Regex("^[A-Za-z0-9._]{1,30}$").matches(it) }
                    ?.let { PageMessage.User(it, str("avatar").takeIf(::isInstagramImage)) }
                "haptic" -> PageMessage.Haptic
                "error" -> PageMessage.Error(str("where").take(40), str("message").take(300))
                else -> null
            }
        }
    }
}
