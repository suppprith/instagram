package com.suppprith.dms.update

import com.suppprith.dms.BuildConfig
import com.suppprith.dms.util.DebugLog
import com.suppprith.dms.util.Http
import com.suppprith.dms.util.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class Release(val version: Version, val url: String)

/**
 * Checks GitHub Releases once a day. The APK is installed by the system installer after the user
 * downloads it, so the app needs no install permission.
 */
class UpdateChecker(private val settings: Settings, private val log: DebugLog) {
    val current: Version? = Version.parse(BuildConfig.VERSION_NAME)

    /** Returns the newer release, if any. [force] skips the once-a-day limit (Settings row tap). */
    suspend fun check(force: Boolean = false): Release? {
        val repo = BuildConfig.RELEASES_REPO
        if (repo.isBlank()) return null
        val now = System.currentTimeMillis()
        val stored = settings.current()
        if (!force && now - stored.lastUpdateCheck < DAY) return storedRelease(stored.latestVersion, stored.latestUrl)

        val response = withContext(Dispatchers.IO) {
            runCatching {
                Http.get(
                    "https://api.github.com/repos/$repo/releases/latest",
                    mapOf("Accept" to "application/vnd.github+json", "X-GitHub-Api-Version" to "2022-11-28"),
                )
            }.getOrNull()
        }
        if (response == null || response.code != 200) {
            // 404 also covers a private repository; nothing to show.
            log.i("update", "no release info (${response?.code ?: "network"})")
            settings.setLatestRelease(stored.latestVersion, stored.latestUrl, now)
            return storedRelease(stored.latestVersion, stored.latestUrl)
        }
        val release = parse(response.body)
        settings.setLatestRelease(release?.version?.toString(), release?.url, now)
        return release?.takeIf { isNewer(it.version) }
    }

    fun isNewer(version: Version?): Boolean = version != null && current != null && version > current

    private fun storedRelease(version: String?, url: String?): Release? {
        val v = Version.parse(version) ?: return null
        return Release(v, url ?: return null).takeIf { isNewer(it.version) }
    }

    companion object {
        private const val DAY = 24 * 60 * 60 * 1000L

        /** `tag_name` plus the first `.apk` asset, falling back to the release page. */
        fun parse(body: String): Release? {
            val root = runCatching { Json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null
            val tag = (root["tag_name"] as? JsonPrimitive)?.contentOrNull
            val version = Version.parse(tag) ?: return null
            val apk = (root["assets"] as? JsonArray)
                ?.mapNotNull { (it as? JsonObject)?.get("browser_download_url") as? JsonPrimitive }
                ?.mapNotNull { it.contentOrNull }
                ?.firstOrNull { it.endsWith(".apk") }
            val page = (root["html_url"] as? JsonPrimitive)?.contentOrNull
            return Release(version, apk ?: page ?: return null)
        }
    }
}
