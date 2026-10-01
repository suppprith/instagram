package com.suppprith.dms.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionTest {
    @Test fun parses() {
        assertEquals(Version(1, 2, 3), Version.parse("v1.2.3"))
        assertEquals(Version(1, 2, 3), Version.parse("1.2.3"))
        assertEquals(Version(1, 0, 0), Version.parse("v1"))
        assertEquals(Version(1, 1, 0), Version.parse("1.1.0-debug"))
        assertNull(Version.parse("latest"))
        assertNull(Version.parse(null))
    }

    @Test fun orders() {
        assertTrue(Version.parse("v1.10.0")!! > Version.parse("v1.9.9")!!)
        assertTrue(Version.parse("v2.0.0")!! > Version.parse("v1.99.99")!!)
        assertEquals(10203, Version(1, 2, 3).code)
    }
}
