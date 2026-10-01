package com.suppprith.dms.cage

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The cage rules. The same JSON is read here (native gate) and by cage.js (in-page gate),
 * so the two can never disagree about which paths are blocked.
 */
@Serializable
data class Rules(
    val version: Int = 1,
    /** Regex sources matched against `location.pathname`. A match bounces to [redirect]. */
    val block: List<String> = emptyList(),
    /** CSS selectors hidden with `display:none !important` inside allowed pages. */
    val hide: List<String> = emptyList(),
    /** Raw CSS appended after the hide rules. */
    val css: String = "",
    val redirect: String = INBOX_PATH,
) {
    /** Bundled rules plus a validated patch. Additive only: nothing in the base can be removed. */
    fun merge(patch: RulesPatch?): Rules {
        if (patch == null) return this
        return copy(
            block = (block + patch.block).distinct(),
            hide = (hide + patch.hide).distinct(),
            css = listOf(css, patch.css).filter { it.isNotBlank() }.joinToString("\n"),
        )
    }

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        const val INBOX_PATH = "/direct/inbox/"

        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun parse(text: String): Rules = json.decodeFromString(serializer(), text)
    }
}

/** Compiled form of [Rules] for the native gate. Bad regexes are skipped, never fatal. */
class RuleMatcher(val rules: Rules) {
    private val patterns: List<Regex> = rules.block.mapNotNull { runCatching { Regex(it) }.getOrNull() }

    fun isBlockedPath(path: String): Boolean {
        val p = path.ifEmpty { "/" }
        return patterns.any { it.containsMatchIn(p) }
    }

    /** True only for Instagram's own web pages whose path matches a block rule. */
    fun isBlockedUrl(url: String?): Boolean {
        val parts = UrlParts.parse(url) ?: return false
        if (!parts.isHttp || parts.host !in INSTAGRAM_WEB_HOSTS) return false
        return isBlockedPath(parts.path)
    }

    companion object {
        val INSTAGRAM_WEB_HOSTS = setOf("www.instagram.com", "instagram.com", "m.instagram.com")
    }
}
