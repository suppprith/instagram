package com.suppprith.dms.cage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PatchValidatorTest {
    private fun invalid(text: String) = PatchValidator.validate(text) as? PatchResult.Invalid
        ?: throw AssertionError("expected invalid: $text")

    private fun valid(text: String) = (PatchValidator.validate(text) as? PatchResult.Valid)?.patch
        ?: throw AssertionError("expected valid: $text -> ${PatchValidator.validate(text)}")

    @Test fun acceptsMinimalPatch() {
        assertEquals(RulesPatch(), valid("""{"schema":1}"""))
    }

    @Test fun acceptsFullPatch() {
        val p = valid("""{"schema":1,"block":["^/threads(/|$)"],"hide":["div[role='banner']"],"css":"x{y:z}","note":"why"}""")
        assertEquals(listOf("^/threads(/|$)"), p.block)
        assertEquals(listOf("div[role='banner']"), p.hide)
        assertEquals("x{y:z}", p.css)
    }

    @Test fun hostedPatchFileIsValid() {
        valid(File("../patch/rules-patch.json").readText())
    }

    @Test fun rejectsBadJson() {
        invalid("{")
        invalid("[]")
        invalid("\"text\"")
        invalid("")
    }

    @Test fun rejectsWrongSchema() {
        invalid("""{"block":[]}""")
        invalid("""{"schema":2}""")
        invalid("""{"schema":"1"}""")
    }

    @Test fun rejectsBadRegex() {
        assertTrue(invalid("""{"schema":1,"block":["^/(["]}""").reason.contains("compile"))
    }

    @Test fun rejectsUnanchoredRegex() {
        invalid("""{"schema":1,"block":["explore"]}""")
    }

    @Test fun rejectsOversized() {
        val big = "a".repeat(PatchValidator.MAX_BYTES)
        assertTrue(invalid("""{"schema":1,"css":"$big"}""").reason.contains("64"))
    }

    @Test fun rejectsUnblockingAttempts() {
        invalid("""{"schema":1,"unblock":["^/explore(/|$)"]}""")
        invalid("""{"schema":1,"allow":["^/$"]}""")
        invalid("""{"schema":1,"redirect":"/"}""")
        invalid("""{"schema":1,"remove":{"block":[0]}}""")
    }

    @Test fun rejectsRulesThatWouldLockOutInboxOrLogin() {
        invalid("""{"schema":1,"block":["^/direct/"]}""")
        invalid("""{"schema":1,"block":["^/accounts/"]}""")
        invalid("""{"schema":1,"block":["^/challenge"]}""")
        invalid("""{"schema":1,"block":["^/.*"]}""")
    }

    @Test fun rejectsWrongTypes() {
        invalid("""{"schema":1,"block":"^/x"}""")
        invalid("""{"schema":1,"block":[1]}""")
        invalid("""{"schema":1,"hide":[{"a":1}]}""")
        invalid("""{"schema":1,"css":["x"]}""")
    }

    @Test fun rejectsSelectorsThatBreakOutOfTheirRule() {
        invalid("""{"schema":1,"hide":["a{display:block} b"]}""")
        invalid("""{"schema":1,"hide":[" "]}""")
        invalid("""{"schema":1,"css":"</style><script>"}""")
    }
}
