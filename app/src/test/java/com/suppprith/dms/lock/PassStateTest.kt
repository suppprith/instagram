package com.suppprith.dms.lock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class PassStateTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val policy = PassPolicy.Default
    private val min = PassState.MINUTE

    private fun at(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    @Test fun policyDerivesEverythingFromTheList() {
        assertEquals(2, policy.perDay)
        assertEquals(5, policy.minutesAfterUsed(0))
        assertEquals(5, policy.minutesAfterUsed(1))
        assertNull(policy.minutesAfterUsed(2))
        assertEquals("2 × 5 min", policy.label)
        assertEquals("5 + 1 min", PassPolicy(listOf(5, 1)).label)
        assertEquals(PassPolicy(listOf(5, 1)), PassPolicy.decode("5,1"))
        assertEquals(PassPolicy.Default, PassPolicy.decode("garbage"))
        assertEquals(PassPolicy.Default, PassPolicy.decode(null))
        assertEquals(PassPolicy.Default, PassPolicy.decode(""))
    }

    @Test fun startingAPassUsesOneAndSetsTheEnd() {
        val now = at("2026-10-01T10:00:00")
        val s = PassState().start(now, zone, policy)!!
        assertEquals(1, s.used)
        assertEquals(now + 5 * min, s.endsAt)
        assertTrue(s.isActive(now + 4 * min))
        assertFalse(s.isActive(now + 5 * min))
        assertEquals(1, s.left(policy))
    }

    @Test fun cannotStartWhileOneIsRunning() {
        val now = at("2026-10-01T10:00:00")
        val s = PassState().start(now, zone, policy)!!
        assertNull(s.start(now + min, zone, policy))
    }

    @Test fun passesRunOut() {
        val t0 = at("2026-10-01T10:00:00")
        val s1 = PassState().start(t0, zone, policy)!!
        val s2 = s1.start(t0 + 10 * min, zone, policy)!!
        assertEquals(0, s2.left(policy))
        assertNull(s2.start(t0 + 30 * min, zone, policy))
    }

    @Test fun unevenPolicyUsesLengthsInOrder() {
        val p = PassPolicy(listOf(5, 1))
        val t0 = at("2026-10-01T10:00:00")
        val s1 = PassState().start(t0, zone, p)!!
        assertEquals(t0 + 5 * min, s1.endsAt)
        val t1 = t0 + 20 * min
        val s2 = s1.start(t1, zone, p)!!
        assertEquals(t1 + 1 * min, s2.endsAt)
    }

    @Test fun rollsOverAtLocalMidnight() {
        val evening = at("2026-10-01T23:50:00")
        val s = PassState().start(evening, zone, policy)!!.start(evening + 6 * min, zone, policy)!!
        assertEquals(0, s.left(policy))
        val justBefore = s.normalize(at("2026-10-01T23:59:59"), zone, policy)
        assertEquals(0, justBefore.left(policy))
        val after = s.normalize(at("2026-10-02T00:00:00"), zone, policy)
        assertEquals(2, after.left(policy))
        assertEquals("2026-10-02", after.dayKey)
    }

    @Test fun passRunningOverMidnightKeepsRunningButResetsCount() {
        val t = at("2026-10-01T23:58:00")
        val s = PassState().start(t, zone, policy)!!
        val after = s.normalize(at("2026-10-02T00:01:00"), zone, policy)
        assertTrue(after.isActive(at("2026-10-02T00:01:00")))
        assertEquals(0, after.used)
    }

    @Test fun backstopExpiresAFinishedPass() {
        val t = at("2026-10-01T10:00:00")
        val s = PassState().start(t, zone, policy)!!
        val later = s.normalize(t + 6 * min, zone, policy)
        assertNull(later.endsAt)
        assertEquals(1, later.used)
    }

    @Test fun clockMovedBackCannotStretchAPass() {
        val t = at("2026-10-01T10:00:00")
        val s = PassState().start(t, zone, policy)!!
        // User moves the clock back an hour mid-pass.
        val back = s.normalize(t - 60 * min, zone, policy)
        assertNull(back.endsAt)
        assertFalse(back.isActive(t - 60 * min))
    }

    @Test fun clockMovedBackADayDoesNotRefundPasses() {
        val t = at("2026-10-02T10:00:00")
        val s = PassState().start(t, zone, policy)!!.start(t + 10 * min, zone, policy)!!
        val yesterday = s.normalize(at("2026-10-01T10:00:00"), zone, policy)
        assertEquals(0, yesterday.left(policy))
    }

    @Test fun corruptLongPassIsCapped() {
        val t = at("2026-10-01T10:00:00")
        val s = PassState(dayKey = "2026-10-01", used = 1, endsAt = t + 600 * min, startedAt = t)
        assertNull(s.normalize(t + min, zone, policy).endsAt)
    }

    @Test fun endingEarly() {
        val t = at("2026-10-01T10:00:00")
        val s = PassState().start(t, zone, policy)!!.end()
        assertFalse(s.isActive(t + min))
        assertEquals(1, s.used)
        assertNotNull(s.start(t + 2 * min, zone, policy))
    }

    @Test fun nextMidnight() {
        assertEquals(at("2026-10-02T00:00:00"), PassState.nextMidnight(at("2026-10-01T13:00:00"), zone))
    }
}
