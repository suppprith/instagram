package com.suppprith.dms.cage

import java.net.URI
import java.net.URLDecoder

/** Minimal URL parsing on java.net.URI so it is unit-testable without Android. */
data class UrlParts(val scheme: String, val host: String, val path: String, val query: String?) {
    val isHttp get() = scheme == "http" || scheme == "https"

    fun queryParam(name: String): String? = query
        ?.split('&')
        ?.map { it.split('=', limit = 2) }
        ?.firstOrNull { it[0] == name }
        ?.getOrNull(1)
        ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }

    companion object {
        fun parse(url: String?): UrlParts? {
            if (url.isNullOrBlank()) return null
            val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
            val scheme = uri.scheme?.lowercase() ?: return null
            return UrlParts(
                scheme = scheme,
                host = uri.host?.lowercase().orEmpty(),
                path = uri.rawPath.orEmpty().ifEmpty { "/" },
                query = uri.rawQuery,
            )
        }
    }
}
