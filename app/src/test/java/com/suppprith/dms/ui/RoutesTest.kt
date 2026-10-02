package com.suppprith.dms.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutesTest {
    private fun r(path: String) = Route.of("https://www.instagram.com$path")

    @Test fun inboxControlsShowOnlyOnTheSignedInInbox() {
        assertTrue(inboxControlsVisible(r("/direct/inbox/"), signedIn = true, keyboardOpen = false, fullScreenVideo = false))
        assertTrue(inboxControlsVisible(r("/direct/inbox"), true, false, false))
        assertFalse(inboxControlsVisible(r("/direct/t/1/"), true, false, false))
        assertFalse(inboxControlsVisible(r("/direct/new/"), true, false, false))
        assertFalse(inboxControlsVisible(r("/someone/"), true, false, false))
        assertFalse(inboxControlsVisible(r("/reel/abc/"), true, false, false))
        assertFalse(inboxControlsVisible(r("/accounts/login/"), true, false, false))
        assertFalse(inboxControlsVisible(Route.of("https://www.facebook.com/x"), true, false, false))
        assertFalse(inboxControlsVisible(r("/direct/inbox/"), signedIn = false, keyboardOpen = false, fullScreenVideo = false))
        assertFalse(inboxControlsVisible(r("/direct/inbox/"), true, keyboardOpen = true, fullScreenVideo = false))
        assertFalse(inboxControlsVisible(r("/direct/inbox/"), true, false, fullScreenVideo = true))
    }

    @Test fun inbox() {
        assertTrue(r("/direct/inbox/").isInbox)
        assertFalse(r("/direct/t/1/").isInbox)
        assertFalse(Route.of(null).isInbox)
    }

    @Test fun authFlow() {
        assertTrue(r("/accounts/login/").isAuthFlow)
        assertTrue(r("/challenge/x/").isAuthFlow)
        assertTrue(Route.of("https://www.facebook.com/x").isAuthFlow)
        assertFalse(r("/direct/inbox/").isAuthFlow)
    }
}
