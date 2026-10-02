package com.suppprith.dms.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JsBridgeTest {
    @Test fun parsesEveryPageMessage() {
        assertEquals(PageMessage.Hello("/direct/inbox/"), JsBridge.parse("""{"type":"hello","path":"/direct/inbox/"}"""))
        assertEquals(PageMessage.Ready("/x/"), JsBridge.parse("""{"type":"ready","path":"/x/"}"""))
        assertEquals(PageMessage.RouteChanged("/direct/t/1/"), JsBridge.parse("""{"type":"route","path":"/direct/t/1/"}"""))
        assertEquals(PageMessage.Blocked("/reels/"), JsBridge.parse("""{"type":"blocked","path":"/reels/"}"""))
        assertEquals(PageMessage.Badge(3), JsBridge.parse("""{"type":"badge","count":3}"""))
        assertEquals(PageMessage.Haptic, JsBridge.parse("""{"type":"haptic"}"""))
        assertEquals(PageMessage.Error("scan", "boom"), JsBridge.parse("""{"type":"error","where":"scan","message":"boom"}"""))
    }

    @Test fun rejectsJunk() {
        assertNull(JsBridge.parse("not json"))
        assertNull(JsBridge.parse("""{"type":"unknown"}"""))
        assertNull(JsBridge.parse("""{"type":"badge","count":-1}"""))
        assertNull(JsBridge.parse("""{"type":"badge","count":"3"}"""))
        assertNull(JsBridge.parse("""{"type":"user","username":"me"}"""))
        assertNull(JsBridge.parse("[]"))
    }
}
