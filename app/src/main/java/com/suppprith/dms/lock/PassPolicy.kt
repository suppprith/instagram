package com.suppprith.dms.lock

/**
 * The whole pass policy is one list of pass lengths. Everything else derives from it.
 * `listOf(5, 5)` means two passes a day, five minutes each.
 */
data class PassPolicy(val lengthsMinutes: List<Int>) {
    init {
        require(lengthsMinutes.isNotEmpty() && lengthsMinutes.all { it in 1..120 }) { "bad policy $lengthsMinutes" }
    }

    val perDay get() = lengthsMinutes.size

    val longestMinutes get() = lengthsMinutes.max()

    /** Length of the next pass after [used] passes today, or null when none are left. */
    fun minutesAfterUsed(used: Int): Int? = lengthsMinutes.getOrNull(used)

    /** "2 × 5 min", "5 + 1 min" */
    val label: String
        get() = if (lengthsMinutes.distinct().size == 1) {
            "${lengthsMinutes.size} × ${lengthsMinutes[0]} min"
        } else {
            lengthsMinutes.joinToString(" + ") + " min"
        }

    fun encode(): String = lengthsMinutes.joinToString(",")

    companion object {
        val Default = PassPolicy(listOf(5, 5))

        val Presets = listOf(
            PassPolicy(listOf(5)),
            Default,
            PassPolicy(listOf(5, 5, 5)),
            PassPolicy(listOf(5, 1)),
            PassPolicy(listOf(10, 10)),
        )

        fun decode(text: String?): PassPolicy = runCatching {
            PassPolicy(text!!.split(',').map { it.trim().toInt() })
        }.getOrDefault(Default)
    }
}
