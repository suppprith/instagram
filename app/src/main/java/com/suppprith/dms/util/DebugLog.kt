package com.suppprith.dms.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Local debug log the user can export from Settings. Nothing is uploaded.
 * Never log message content, cookies or full URLs with query strings: paths and counts only.
 */
class DebugLog(context: Context) {
    private val file = File(context.filesDir, "debug.log")
    private val lock = Any()
    private val format = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)
    private val _lines = MutableStateFlow(readFile())

    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    fun i(tag: String, message: String) = add("I", tag, message)

    fun w(tag: String, message: String, error: Throwable? = null) =
        add("W", tag, if (error == null) message else "$message: ${error.javaClass.simpleName} ${error.message.orEmpty()}")

    private fun add(level: String, tag: String, message: String) {
        val line = "${format.format(Date())} $level $tag ${message.take(MAX_LINE)}"
        Log.println(if (level == "W") Log.WARN else Log.INFO, "dms.$tag", message)
        synchronized(lock) {
            val next = (_lines.value + line).takeLast(MAX_LINES)
            _lines.value = next
            runCatching {
                if (file.length() > MAX_FILE_BYTES) file.writeText(next.joinToString("\n", postfix = "\n"))
                else file.appendText(line + "\n")
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            _lines.value = emptyList()
            runCatching { file.delete() }
        }
    }

    fun text(): String = _lines.value.joinToString("\n")

    private fun readFile(): List<String> =
        runCatching { file.readLines().takeLast(MAX_LINES) }.getOrDefault(emptyList())

    private companion object {
        const val MAX_LINES = 500
        const val MAX_LINE = 400
        const val MAX_FILE_BYTES = 200_000L
    }
}
