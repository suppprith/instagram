package com.suppprith.dms.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BadgeCountTest {
    @Test fun parsesBadgeCount() {
        assertEquals(3, BadgeCount.parse("""{"user_id":1,"badge_count":3,"seq_id":9,"status":"ok"}"""))
        assertEquals(0, BadgeCount.parse("""{"badge_count":0}"""))
        assertNull(BadgeCount.parse("""{"status":"fail"}"""))
        assertNull(BadgeCount.parse("<html>"))
        assertNull(BadgeCount.parse("""{"badge_count":-1}"""))
    }

    @Test fun readsCookies() {
        val header = "csrftoken=abc; ds_user_id=42; sessionid=42%3Axyz; mid=m"
        assertEquals("abc", BadgeCount.cookie(header, "csrftoken"))
        assertEquals("42", BadgeCount.cookie(header, "ds_user_id"))
        assertEquals("42%3Axyz", BadgeCount.cookie(header, "sessionid"))
        assertNull(BadgeCount.cookie(header, "missing"))
        assertNull(BadgeCount.cookie(null, "sessionid"))
    }

    @Test fun textNeverHasContent() {
        assertEquals("1 unread conversation", BadgeCount.text(1))
        assertEquals("4 unread conversations", BadgeCount.text(4))
    }

    @Test fun notifiesOnlyOnIncreaseInBackground() {
        assertTrue(BadgeCount.shouldNotify(previous = 1, current = 2, appInForeground = false))
        assertFalse(BadgeCount.shouldNotify(previous = 2, current = 2, appInForeground = false))
        assertFalse(BadgeCount.shouldNotify(previous = 3, current = 1, appInForeground = false))
        assertFalse(BadgeCount.shouldNotify(previous = 0, current = 5, appInForeground = true))
    }
}
