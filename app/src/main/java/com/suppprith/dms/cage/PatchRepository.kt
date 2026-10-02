package com.suppprith.dms.cage

import android.content.Context
import com.suppprith.dms.BuildConfig
import com.suppprith.dms.util.DebugLog
import com.suppprith.dms.util.Http
import com.suppprith.dms.util.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Effective rules = bundled `assets/cage/rules.json` + the last good remote patch.
 * The patch is fetched on start and at most once an hour, validated, and cached on the phone.
 */
class PatchRepository(
    private val context: Context,
    private val settings: Settings,
    private val log: DebugLog,
) {
    private val cacheFile = File(context.filesDir, "rules-patch.json")
    private val mutex = Mutex()

    val bundled: Rules = context.assets.open("cage/rules.json").bufferedReader().use { Rules.parse(it.readText()) }

    private val _rules = MutableStateFlow(bundled.merge(readCachedPatch()))

    /** What the native gate and the next document-start injection use. */
    val rules: StateFlow<Rules> = _rules.asStateFlow()

    /** The page script, read once. */
    val cageScript: String by lazy {
        context.assets.open("cage/cage.js").bufferedReader().use { it.readText() }
    }

    /** cage.js with the merged rules inlined ahead of it, so the page never fetches the patch itself. */
    fun injectionScript(rules: Rules = this.rules.value): String =
        "window.__DMS_RULES__=${rules.toJson()};\n$cageScript"

    private fun readCachedPatch(): RulesPatch? {
        val text = runCatching { cacheFile.readText() }.getOrNull() ?: return null
        return (PatchValidator.validate(text) as? PatchResult.Valid)?.patch
    }

    /** Returns true when the rules changed. */
    suspend fun refresh(force: Boolean = false): Boolean = mutex.withLock {
        val url = BuildConfig.PATCH_URL
        if (url.isBlank()) return false
        val now = System.currentTimeMillis()
        if (!force && now - settings.current().lastPatchFetch < HOUR) return false
        settings.setLastPatchFetch(now)

        val text = withContext(Dispatchers.IO) {
            runCatching { Http.get(url, maxBytes = PatchValidator.MAX_BYTES) }
                .onFailure { log.w("patch", "fetch failed", it) }
                .getOrNull()
                ?.takeIf { it.code == 200 }
                ?.body
        } ?: return false

        when (val result = PatchValidator.validate(text)) {
            is PatchResult.Invalid -> {
                log.w("patch", "rejected: ${result.reason}")
                false
            }
            is PatchResult.Valid -> {
                val next = bundled.merge(result.patch)
                if (next == _rules.value) return false
                withContext(Dispatchers.IO) { runCatching { cacheFile.writeText(text) } }
                _rules.value = next
                log.i("patch", "applied: +${result.patch.block.size} block, +${result.patch.hide.size} hide")
                true
            }
        }
    }

    private companion object {
        const val HOUR = 60 * 60 * 1000L
    }
}
