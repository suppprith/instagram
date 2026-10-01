package com.suppprith.dms.cage

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/** A remote patch: strictly more blocks, more hides, more CSS. Data only, never code. */
data class RulesPatch(
    val block: List<String> = emptyList(),
    val hide: List<String> = emptyList(),
    val css: String = "",
)

sealed interface PatchResult {
    data class Valid(val patch: RulesPatch) : PatchResult
    data class Invalid(val reason: String) : PatchResult
}

/**
 * Validates `rules-patch.json`:
 * ```
 * { "schema": 1, "block": ["regex"], "hide": ["css selector"], "css": "raw css" }
 * ```
 * Anything that could weaken the cage or break sign-in is rejected as a whole.
 */
object PatchValidator {
    const val SCHEMA = 1
    const val MAX_BYTES = 64 * 1024
    private const val MAX_ENTRIES = 500

    private val allowedKeys = setOf("schema", "block", "hide", "css", "note")

    /** Paths a block rule must never match, or the user could be locked out of the inbox or sign-in. */
    val protectedPaths = listOf(
        Rules.INBOX_PATH,
        "/direct/t/123/",
        "/accounts/login/",
        "/accounts/login/two_factor/",
        "/challenge/",
        "/accounts/onetap/",
    )

    fun validate(text: String): PatchResult {
        if (text.toByteArray(Charsets.UTF_8).size > MAX_BYTES) return PatchResult.Invalid("larger than 64 KB")
        val root = runCatching { Rules.json.parseToJsonElement(text) }.getOrNull() as? JsonObject
            ?: return PatchResult.Invalid("not a JSON object")

        val unknown = root.keys - allowedKeys
        if (unknown.isNotEmpty()) return PatchResult.Invalid("unknown keys: ${unknown.sorted().joinToString()}")

        val schema = (root["schema"] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
        if (schema != SCHEMA) return PatchResult.Invalid("unsupported schema: ${root["schema"]}")

        val block = stringList(root, "block") ?: return PatchResult.Invalid("block must be a list of strings")
        val hide = stringList(root, "hide") ?: return PatchResult.Invalid("hide must be a list of strings")
        val cssElement = root["css"]
        val css = when {
            cssElement == null -> ""
            cssElement is JsonPrimitive && cssElement.isString -> cssElement.content
            else -> return PatchResult.Invalid("css must be a string")
        }
        if (block.size + hide.size > MAX_ENTRIES) return PatchResult.Invalid("too many entries")

        for (source in block) {
            val regex = runCatching { Regex(source) }.getOrNull()
                ?: return PatchResult.Invalid("block rule does not compile: $source")
            if (!source.startsWith("^/")) return PatchResult.Invalid("block rule must be anchored at ^/: $source")
            val hit = protectedPaths.firstOrNull { regex.containsMatchIn(it) }
            if (hit != null) return PatchResult.Invalid("block rule matches protected path $hit: $source")
        }
        for (selector in hide) {
            if (selector.isBlank() || selector.any { it == '{' || it == '}' || it == '<' }) {
                return PatchResult.Invalid("bad selector: $selector")
            }
        }
        if (css.contains("</")) return PatchResult.Invalid("css must not contain markup")

        return PatchResult.Valid(RulesPatch(block = block, hide = hide, css = css))
    }

    private fun stringList(root: JsonObject, key: String): List<String>? {
        val element = root[key] ?: return emptyList()
        val array = element as? JsonArray ?: return null
        return array.map { item ->
            val primitive = item as? JsonPrimitive ?: return null
            if (!primitive.isString) return null
            primitive.jsonPrimitive.contentOrNull ?: return null
        }
    }
}
