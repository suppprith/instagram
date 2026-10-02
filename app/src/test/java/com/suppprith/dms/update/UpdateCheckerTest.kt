package com.suppprith.dms.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateCheckerTest {
    @Test fun prefersTheApkAsset() {
        val body = """
            {"tag_name":"v1.2.0","html_url":"https://github.com/o/r/releases/tag/v1.2.0",
             "assets":[{"browser_download_url":"https://x/instagram-dms-1.2.0.apk.sha256"},
                       {"browser_download_url":"https://x/instagram-dms-1.2.0.apk"}]}
        """.trimIndent()
        assertEquals(Release(Version(1, 2, 0), "https://x/instagram-dms-1.2.0.apk"), UpdateChecker.parse(body))
    }

    @Test fun fallsBackToTheReleasePage() {
        val body = """{"tag_name":"v1.2.0","html_url":"https://github.com/o/r/releases/tag/v1.2.0","assets":[]}"""
        assertEquals("https://github.com/o/r/releases/tag/v1.2.0", UpdateChecker.parse(body)?.url)
    }

    @Test fun ignoresJunk() {
        assertNull(UpdateChecker.parse("""{"message":"Not Found"}"""))
        assertNull(UpdateChecker.parse("<html>"))
    }
}
