package com.suppprith.dms.web

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.widget.Toast
import com.suppprith.dms.R

/** User-initiated saves only. blob: and data: URLs cannot go through DownloadManager. */
object Downloads {
    fun enqueue(context: Context, url: String, userAgent: String, contentDisposition: String?, mimeType: String?): Boolean {
        if (!url.startsWith("https://")) {
            Toast.makeText(context, R.string.cannot_save_file, Toast.LENGTH_SHORT).show()
            return false
        }
        val name = URLUtil.guessFileName(url, contentDisposition, mimeType)
        val request = DownloadManager.Request(Uri.parse(url))
            .setMimeType(mimeType)
            .addRequestHeader("User-Agent", userAgent)
            .setTitle(name)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
        CookieManager.getInstance().getCookie(url)?.let { request.addRequestHeader("Cookie", it) }
        val manager = context.getSystemService(DownloadManager::class.java) ?: return false
        return runCatching { manager.enqueue(request) }
            .onSuccess { Toast.makeText(context, R.string.downloading, Toast.LENGTH_SHORT).show() }
            .isSuccess
    }
}
