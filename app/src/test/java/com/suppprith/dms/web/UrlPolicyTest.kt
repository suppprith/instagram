package com.suppprith.dms.web

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlPolicyTest {
    @Test fun instagramAndLoginChainStayInApp() {
        listOf(
            "https://www.instagram.com/direct/inbox/",
            "https://instagram.com/accounts/login/",
            "https://accountscenter.instagram.com/",
            "https://www.facebook.com/login.php",
            "https://m.facebook.com/dialog/oauth",
            "https://scontent.cdninstagram.com/v/t51/abc.jpg",
            "https://static.xx.fbcdn.net/rsrc.php",
            "https://auth.meta.com/",
            "https://www.google.com/recaptcha/api2/anchor",
            "https://www.recaptcha.net/recaptcha/api.js",
            "about:blank",
            "blob:https://www.instagram.com/123",
        ).forEach { assertEquals(it, NavDecision.InApp, UrlPolicy.decide(it)) }
    }

    @Test fun otherSitesOpenExternally() {
        listOf(
            "https://example.com/",
            "https://www.google.com/search?q=x",
            "https://notinstagram.com/",
            "https://instagram.com.evil.example/",
        ).forEach { assertEquals(it, NavDecision.External(it), UrlPolicy.decide(it)) }
    }

    @Test fun linkShimIsUnwrapped() {
        val wrapped = "https://l.instagram.com/?u=https%3A%2F%2Fexample.com%2Fpage%3Fa%3D1&e=AT0"
        assertEquals(NavDecision.External("https://example.com/page?a=1"), UrlPolicy.decide(wrapped))
    }

    @Test fun linkShimToInstagramStaysInApp() {
        val wrapped = "https://l.instagram.com/?u=https%3A%2F%2Fwww.instagram.com%2Fp%2Fabc%2F"
        assertEquals(NavDecision.InApp, UrlPolicy.decide(wrapped))
    }

    @Test fun linkShimWithoutTargetOpensExternally() {
        val url = "https://l.instagram.com/?x=1"
        assertEquals(NavDecision.External(url), UrlPolicy.decide(url))
    }

    @Test fun appSchemesAreIgnored() {
        assertEquals(NavDecision.Ignore, UrlPolicy.decide("instagram://direct-inbox"))
        assertEquals(NavDecision.Ignore, UrlPolicy.decide("fb://profile"))
        assertEquals(NavDecision.Ignore, UrlPolicy.decide("intent://x#Intent;scheme=instagram;end"))
    }

    @Test fun intentWithWebFallbackOpensFallback() {
        val url = "intent://x#Intent;scheme=instagram;S.browser_fallback_url=https%3A%2F%2Fexample.com%2F;end"
        assertEquals(NavDecision.External("https://example.com/"), UrlPolicy.decide(url))
    }

    @Test fun systemSchemesGoToOtherApps() {
        assertEquals(NavDecision.System("mailto:a@b.c"), UrlPolicy.decide("mailto:a@b.c"))
        assertEquals(NavDecision.System("tel:+123"), UrlPolicy.decide("tel:+123"))
    }
}
