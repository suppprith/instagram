package com.suppprith.dms.util

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** The two or three small GETs the app makes. No networking library needed. */
object Http {
    class Response(val code: Int, val body: String)

    class TooLarge : Exception("response too large")

    fun get(url: String, headers: Map<String, String> = emptyMap(), maxBytes: Int = 256 * 1024): Response {
        val (code, bytes) = getBytes(url, headers, maxBytes)
        return Response(code, bytes.toString(Charsets.UTF_8))
    }

    fun getBytes(url: String, headers: Map<String, String> = emptyMap(), maxBytes: Int = 256 * 1024): Pair<Int, ByteArray> {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = true
            connection.useCaches = false
            headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    if (out.size() > maxBytes) throw TooLarge()
                }
                out.toByteArray()
            } ?: ByteArray(0)
            return code to body
        } finally {
            connection.disconnect()
        }
    }
}
