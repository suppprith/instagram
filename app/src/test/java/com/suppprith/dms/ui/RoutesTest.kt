package com.suppprith.dms.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutesTest {
    private fun r(path: String) = Route.of("https://www.instagram.com$path")

    @Test fun tabs() {
        assertEquals(Tab.Messages, r("/direct/inbox/").tab("me"))
        assertEquals(Tab.Messages, r("/direct/t/1/").tab("me"))
        assertEquals(Tab.Activity, r("/notifications/").tab("me"))
        assertEquals(Tab.Activity, r("/accounts/activity/").tab("me"))
        assertEquals(Tab.Profile, r("/me/").tab("me"))
        assertEquals(Tab.Profile, r("/Me").tab("me"))
        assertNull(r("/someone/").tab("me"))
        assertNull(r("/me/").tab(null))
        assertNull(Route.of("https://www.facebook.com/").tab("me"))
    }

    @Test fun barHiddenInThreadsMediaSignInAndWithKeyboard() {
        assertTrue(bottomBarVisible(r("/direct/inbox/"), signedIn = true, keyboardOpen = false, fullScreenVideo = false))
        assertTrue(bottomBarVisible(r("/someone/"), true, false, false))
        assertTrue(bottomBarVisible(r("/p/abc/"), true, false, false))
        assertFalse(bottomBarVisible(r("/direct/t/1/"), true, false, false))
        assertFalse(bottomBarVisible(r("/direct/new/"), true, false, false))
        assertFalse(bottomBarVisible(r("/stories/a/1/"), true, false, false))
        assertFalse(bottomBarVisible(r("/reel/abc/"), true, false, false))
        assertFalse(bottomBarVisible(r("/accounts/login/"), true, false, false))
        assertFalse(bottomBarVisible(r("/challenge/x/"), true, false, false))
        assertFalse(bottomBarVisible(Route.of("https://www.facebook.com/x"), true, false, false))
        assertFalse(bottomBarVisible(r("/direct/inbox/"), signedIn = false, keyboardOpen = false, fullScreenVideo = false))
        assertFalse(bottomBarVisible(r("/direct/inbox/"), true, keyboardOpen = true, fullScreenVideo = false))
        assertFalse(bottomBarVisible(r("/direct/inbox/"), true, false, fullScreenVideo = true))
    }

    @Test fun inbox() {
        assertTrue(r("/direct/inbox/").isInbox)
        assertFalse(r("/direct/t/1/").isInbox)
        assertFalse(Route.of(null).isInbox)
    }
}
