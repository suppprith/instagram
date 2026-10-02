package com.suppprith.dms.notify

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

object BadgeCount {
    const val ENDPOINT = "https://www.instagram.com/api/v1/direct_v2/get_badge_count/?no_raven=1"

    /** Instagram's web app id, the same one the website sends. */
    const val IG_APP_ID = "936619743392459"

    /** Reads `badge_count` from the endpoint's JSON. Null when the shape is unexpected. */
    fun parse(body: String): Int? {
        val root = runCatching { Json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null
        return (root["badge_count"] as? JsonPrimitive)?.intOrNull?.takeIf { it >= 0 }
    }

    /** Pulls one cookie value out of a `Cookie` header string. */
    fun cookie(header: String?, name: String): String? = header
        ?.split(';')
        ?.map { it.trim().split('=', limit = 2) }
        ?.firstOrNull { it.size == 2 && it[0] == name }
        ?.get(1)
        ?.takeIf { it.isNotEmpty() }

    /** Copy rule: literal, no message content. */
    fun text(count: Int): String = if (count == 1) "1 unread conversation" else "$count unread conversations"

    fun shouldNotify(previous: Int, current: Int, appInForeground: Boolean): Boolean =
        !appInForeground && current > previous
}
