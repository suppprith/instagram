package com.suppprith.dms.ui

import com.suppprith.dms.cage.UrlParts

enum class Tab { Messages, Activity, Profile }

/** What the native frame shows for a page. Pure, so it is tested without Android. */
data class Route(val url: String?, val host: String, val path: String) {
    val isInstagram get() = host == "www.instagram.com" || host == "instagram.com"

    val isInbox get() = isInstagram && (path == "/direct/inbox/" || path == "/direct/inbox" || path == "/direct/")

    val isThread get() = isInstagram && (path.startsWith("/direct/t/") || path.startsWith("/direct/new"))

    /** Stories, a single reel: full-screen media where no frame should show. */
    val isFullScreenMedia get() = isInstagram && (path.startsWith("/stories/") || path.startsWith("/reel/"))

    /** Sign-in, two-factor, challenges and anything off Instagram's main site. */
    val isAuthFlow
        get() = !isInstagram || authPrefixes.any { path.startsWith(it) }

    fun tab(ownUsername: String?): Tab? = when {
        !isInstagram -> null
        path.startsWith("/direct/") -> Tab.Messages
        path.startsWith("/notifications") || path.startsWith("/accounts/activity") -> Tab.Activity
        ownUsername != null && path.trimEnd('/').equals("/$ownUsername", ignoreCase = true) -> Tab.Profile
        else -> null
    }

    fun isOwnProfile(ownUsername: String?): Boolean = tab(ownUsername) == Tab.Profile

    companion object {
        private val authPrefixes = listOf(
            "/accounts/login",
            "/accounts/signup",
            "/accounts/emailsignup",
            "/accounts/password",
            "/accounts/onetap",
            "/accounts/suspended",
            "/accounts/disabled",
            "/challenge",
            "/two_factor",
            "/auth_platform",
        )

        val Empty = Route(null, "", "/")

        fun of(url: String?): Route {
            val parts = UrlParts.parse(url) ?: return Empty.copy(url = url)
            return Route(url, parts.host, parts.path)
        }
    }
}

/** The bottom bar shows only on the main Instagram pages, after sign-in. */
fun bottomBarVisible(route: Route, signedIn: Boolean, keyboardOpen: Boolean, fullScreenVideo: Boolean): Boolean =
    signedIn && !keyboardOpen && !fullScreenVideo &&
        !route.isAuthFlow && !route.isThread && !route.isFullScreenMedia
