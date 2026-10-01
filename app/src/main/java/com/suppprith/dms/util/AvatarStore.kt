package com.suppprith.dms.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * The signed-in user's profile photo for the Profile tab, as Instagram shows it.
 * Downloaded from Instagram's CDN once per URL and kept on the phone only.
 */
class AvatarStore(context: Context) {
    private val file = File(context.filesDir, "avatar.png")
    private val urlFile = File(context.filesDir, "avatar.url")

    fun cached(): Bitmap? = runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull()

    fun cachedUrl(): String? = runCatching { urlFile.readText() }.getOrNull()

    /** Blocking; call off the main thread. */
    fun download(url: String): Bitmap? {
        val (code, bytes) = runCatching { Http.getBytes(url, maxBytes = MAX_BYTES) }.getOrNull() ?: return null
        if (code != 200) return null
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val bitmap = Bitmap.createScaledBitmap(decoded, SIZE_PX, SIZE_PX, true)
        runCatching {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            urlFile.writeText(url)
        }
        return bitmap
    }

    fun clear() {
        file.delete()
        urlFile.delete()
    }

    private companion object {
        const val SIZE_PX = 96
        const val MAX_BYTES = 2 * 1024 * 1024
    }
}
