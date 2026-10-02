package com.suppprith.dms.web

import com.suppprith.dms.cage.UrlParts

/** Where a main-frame navigation should go. Pure logic, shared by the WebView client and tests. */
sealed interface NavDecision {
    /** Let the WebView load it (Instagram pages, Meta login and verification chain). */
    data object InApp : NavDecision

    /** Open outside the app in a Custom Tab. */
    data class External(val url: String) : NavDecision

    /** Hand to another app (mailto:, tel:, sms:). */
    data class System(val url: String) : NavDecision

    /** Drop silently (instagram://, intent:// without a web fallback, unknown schemes). */
    data object Ignore : NavDecision
}

object UrlPolicy {
    /** Hosts that must stay in the WebView so sign-in, two-factor and challenges are never cut off. */
    private val inAppSuffixes = listOf(
        "instagram.com",
        "facebook.com",
        "fbcdn.net",
        "cdninstagram.com",
        "meta.com",
        "fbsbx.com",
        "recaptcha.net",
    )

    /** Specific paths on otherwise external hosts that belong to the login chain. */
    private val inAppPrefixes = listOf(
        "www.google.com/recaptcha",
        "www.gstatic.com/recaptcha",
    )

    private const val LINK_SHIM_HOST = "l.instagram.com"

    fun decide(url: String): NavDecision {
        val parts = UrlParts.parse(url) ?: return NavDecision.Ignore
        return when (parts.scheme) {
            "http", "https" -> decideWeb(url, parts)
            "mailto", "tel", "sms", "geo" -> NavDecision.System(url)
            "intent" -> intentFallback(url)?.let { NavDecision.External(it) } ?: NavDecision.Ignore
            "about", "data", "blob", "javascript" -> NavDecision.InApp
            else -> NavDecision.Ignore
        }
    }

    private fun decideWeb(url: String, parts: UrlParts): NavDecision {
        if (parts.host == LINK_SHIM_HOST) {
            // Outbound link wrapper: l.instagram.com/?u=<encoded target>
            val target = parts.queryParam("u")
            val targetParts = UrlParts.parse(target)
            return if (target != null && targetParts != null && targetParts.isHttp) {
                decideWeb(target, targetParts)
            } else {
                NavDecision.External(url)
            }
        }
        if (isInAppHost(parts.host)) return NavDecision.InApp
        val hostAndPath = parts.host + parts.path
        if (inAppPrefixes.any { hostAndPath.startsWith(it) }) return NavDecision.InApp
        return NavDecision.External(url)
    }

    fun isInAppHost(host: String): Boolean =
        host != LINK_SHIM_HOST && inAppSuffixes.any { host == it || host.endsWith(".$it") }

    /** `intent://...#Intent;...;S.browser_fallback_url=<encoded>;end` */
    private fun intentFallback(url: String): String? {
        val marker = "S.browser_fallback_url="
        val start = url.indexOf(marker).takeIf { it >= 0 } ?: return null
        val raw = url.substring(start + marker.length).substringBefore(';')
        val decoded = runCatching { java.net.URLDecoder.decode(raw, "UTF-8") }.getOrNull() ?: return null
        return decoded.takeIf { UrlParts.parse(it)?.isHttp == true }
    }
}
