package com.suppprith.dms.lock

import java.time.Instant
import java.time.ZoneId

/** Why the user opened the Instagram app. Stored on the phone only. */
enum class PassReason(val label: String) {
    PostStory("Post a story"),
    PostPhoto("Post a photo or Reel"),
    Call("Call someone"),
    Other("Something else"),
}

/**
 * Pass bookkeeping for one day. Immutable; every change returns a new state.
 *
 * @param dayKey local date `yyyy-MM-dd` that [used] belongs to
 * @param used passes started on [dayKey]
 * @param endsAt epoch millis when the running pass ends, or null
 */
data class PassState(
    val dayKey: String = "",
    val used: Int = 0,
    val endsAt: Long? = null,
    val startedAt: Long? = null,
) {
    fun isActive(now: Long): Boolean = endsAt != null && now < endsAt

    fun remainingMillis(now: Long): Long = if (isActive(now)) endsAt!! - now else 0

    fun left(policy: PassPolicy): Int = (policy.perDay - used).coerceAtLeast(0)

    /**
     * Rolls the day over at local midnight and expires a finished pass.
     *
     * Clock changes: the day only rolls forward (moving the clock back a day does not refund
     * passes), and a pass can never run longer than the longest pass in the policy, so moving the
     * clock back cannot stretch a running pass.
     */
    fun normalize(now: Long, zone: ZoneId, policy: PassPolicy): PassState {
        val today = dayKeyOf(now, zone)
        var next = this
        if (dayKey.isEmpty() || today > dayKey) next = next.copy(dayKey = today, used = 0)
        val end = next.endsAt
        if (end != null) {
            val maxLength = policy.longestMinutes * MINUTE
            val started = next.startedAt
            val clockWentBack = started != null && now < started
            val tooLong = end - now > maxLength
            if (now >= end || clockWentBack || tooLong) next = next.copy(endsAt = null, startedAt = null)
        }
        return next
    }

    /** Starts the next pass if one is left. Returns null when none are left or one is already running. */
    fun start(now: Long, zone: ZoneId, policy: PassPolicy): PassState? {
        val current = normalize(now, zone, policy)
        if (current.isActive(now)) return null
        val minutes = policy.minutesAfterUsed(current.used) ?: return null
        return current.copy(used = current.used + 1, startedAt = now, endsAt = now + minutes * MINUTE)
    }

    fun end(): PassState = copy(endsAt = null, startedAt = null)

    companion object {
        const val MINUTE = 60_000L

        fun dayKeyOf(now: Long, zone: ZoneId): String =
            Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toString()

        /** Epoch millis of the next local midnight. */
        fun nextMidnight(now: Long, zone: ZoneId): Long =
            Instant.ofEpochMilli(now).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }
}
