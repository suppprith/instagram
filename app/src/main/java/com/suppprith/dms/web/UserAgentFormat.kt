package com.suppprith.dms.web

/**
 * Turns Android WebView's default user agent into one that reads as Chrome for Android.
 *
 * The default contains `; wv)` and `Version/4.0`, which mark an embedded browser. Some Meta and
 * Google flows refuse those, so both tokens are removed, along with the build id.
 */
object UserAgentFormat {
    private val wvToken = Regex(""";\s*wv\)""")
    private val versionToken = Regex("""\s*Version/[\d.]+""")
    private val buildToken = Regex("""\s*Build/[^;)]+""")

    fun chromeMobile(defaultUserAgent: String): String = defaultUserAgent
        .replace(wvToken, ")")
        .replace(buildToken, "")
        .replace(versionToken, "")
        .replace(Regex("""\s{2,}"""), " ")
        .replace("; )", ")")
        .trim()
}
