package com.suppprith.dms.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class UserAgentFormatTest {
    @Test fun stripsWebViewTokens() {
        val default = "Mozilla/5.0 (Linux; Android 14; Pixel 7 Build/UQ1A.240205.004; wv) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Version/4.0 Chrome/120.0.6099.230 Mobile Safari/537.36"
        val ua = UserAgentFormat.chromeMobile(default)
        assertEquals(
            "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0.6099.230 Mobile Safari/537.36",
            ua,
        )
        assertFalse(ua.contains("wv"))
        assertFalse(ua.contains("Version/"))
    }

    @Test fun leavesChromeUserAgentAlone() {
        val chrome = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.36"
        assertEquals(chrome, UserAgentFormat.chromeMobile(chrome))
    }
}
