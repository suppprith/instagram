package com.suppprith.dms.web

import android.content.Context
import android.webkit.WebSettings

object UserAgent {
    @Volatile private var cached: String? = null

    /** Chrome-for-Android user agent built from this device's WebView. See [UserAgentFormat]. */
    fun chromeMobile(context: Context): String = cached ?: UserAgentFormat
        .chromeMobile(WebSettings.getDefaultUserAgent(context.applicationContext))
        .also { cached = it }
}
