package com.suppprith.dms.cage

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RulesTest {
    private val bundled = Rules.parse(File("src/main/assets/cage/rules.json").readText())
    private val matcher = RuleMatcher(bundled)
    private val fixture = Json.parseToJsonElement(File("src/test/resources/cage/paths.json").readText()) as JsonObject

    private fun paths(key: String) = fixture[key]!!.jsonArray.map { it.jsonPrimitive.content }

    @Test fun bundledRulesHaveTheFiveBlockRules() {
        assertEquals(5, bundled.block.size)
        assertEquals(Rules.INBOX_PATH, bundled.redirect)
    }

    @Test fun blockedPathsAreBlocked() {
        for (path in paths("blocked")) assertTrue("expected blocked: $path", matcher.isBlockedPath(path))
    }

    @Test fun allowedPathsPassThrough() {
        for (path in paths("allowed")) assertFalse("expected allowed: $path", matcher.isBlockedPath(path))
    }

    @Test fun reelPermalinkDiffersFromReelsFeedByOneLetter() {
        assertFalse(matcher.isBlockedPath("/reel/abc/"))
        assertTrue(matcher.isBlockedPath("/reels/"))
        assertTrue(matcher.isBlockedPath("/reel/"))
    }

    @Test fun nativeGateOnlyAppliesToInstagramHosts() {
        assertTrue(matcher.isBlockedUrl("https://www.instagram.com/"))
        assertTrue(matcher.isBlockedUrl("https://www.instagram.com/explore/?hl=en"))
        assertTrue(matcher.isBlockedUrl("https://instagram.com/reels/"))
        assertFalse(matcher.isBlockedUrl("https://www.instagram.com/direct/inbox/"))
        assertFalse(matcher.isBlockedUrl("https://www.facebook.com/"))
        assertFalse(matcher.isBlockedUrl("https://help.instagram.com/"))
        assertFalse(matcher.isBlockedUrl(null))
        assertFalse(matcher.isBlockedUrl("not a url"))
    }

    @Test fun emptyPathCountsAsRoot() {
        assertTrue(matcher.isBlockedPath(""))
    }

    @Test fun badRegexIsSkippedNotFatal() {
        val m = RuleMatcher(Rules(block = listOf("([", "^/explore(/|$)")))
        assertTrue(m.isBlockedPath("/explore/"))
        assertFalse(m.isBlockedPath("/direct/inbox/"))
    }

    @Test fun mergeIsAdditive() {
        val merged = bundled.merge(RulesPatch(block = listOf("^/new_feed/"), hide = listOf("div.x"), css = "a{}"))
        assertTrue(merged.block.containsAll(bundled.block))
        assertTrue("^/new_feed/" in merged.block)
        assertTrue(merged.hide.containsAll(bundled.hide))
        assertTrue("div.x" in merged.hide)
        assertTrue(merged.css.contains("a{}"))
        assertEquals(bundled.redirect, merged.redirect)
    }

    @Test fun mergeDeduplicates() {
        val merged = bundled.merge(RulesPatch(block = bundled.block, hide = bundled.hide))
        assertEquals(bundled.block, merged.block)
        assertEquals(bundled.hide, merged.hide)
    }

    @Test fun jsonRoundTrip() {
        assertEquals(bundled, Rules.parse(bundled.toJson()))
    }
}
