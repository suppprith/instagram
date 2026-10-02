package com.suppprith.dms.web

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.webkit.ValueCallback
import android.webkit.WebChromeClient.FileChooserParams
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File

/**
 * Photo and video picking for the page's <input type=file>: the system picker for gallery and
 * files, plus camera capture into a cache file. The camera app does the capture, so this app
 * needs no camera permission.
 */
class FilePicker(private val activity: ComponentActivity) {
    private var callback: ValueCallback<Array<Uri>>? = null
    private val captures = mutableListOf<Pair<File, Uri>>()

    private val launcher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        deliver(result.resultCode, result.data)
    }

    fun show(cb: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
        callback?.onReceiveValue(null)
        callback = cb
        captures.clear()

        val accept = params.acceptTypes
            .flatMap { it.split(',') }
            .map { it.trim().lowercase() }
            .filter { it.contains('/') }
            .distinct()
        val anyType = accept.isEmpty() || "*/*" in accept
        val wantsImage = anyType || accept.any { it.startsWith("image/") }
        val wantsVideo = anyType || accept.any { it.startsWith("video/") }

        val cameraIntents = buildList {
            if (wantsImage) captureIntent(MediaStore.ACTION_IMAGE_CAPTURE, "jpg")?.let(::add)
            if (wantsVideo) captureIntent(MediaStore.ACTION_VIDEO_CAPTURE, "mp4")?.let(::add)
        }

        val content = Intent(Intent.ACTION_GET_CONTENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(if (accept.size == 1) accept[0] else "*/*")
        if (accept.size > 1) content.putExtra(Intent.EXTRA_MIME_TYPES, accept.toTypedArray())
        if (params.mode == FileChooserParams.MODE_OPEN_MULTIPLE) content.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)

        val intent = if (params.isCaptureEnabled && cameraIntents.isNotEmpty()) {
            cameraIntents.first()
        } else {
            Intent.createChooser(content, null).putExtra(Intent.EXTRA_INITIAL_INTENTS, cameraIntents.toTypedArray())
        }
        return try {
            launcher.launch(intent)
            true
        } catch (e: ActivityNotFoundException) {
            callback = null
            false
        }
    }

    private fun captureIntent(action: String, extension: String): Intent? {
        val intent = Intent(action)
        if (intent.resolveActivity(activity.packageManager) == null) return null
        val dir = File(activity.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "capture-${System.currentTimeMillis()}.$extension")
        val uri = runCatching {
            FileProvider.getUriForFile(activity, "${activity.packageName}.files", file)
        }.getOrNull() ?: return null
        captures += file to uri
        return intent
            .putExtra(MediaStore.EXTRA_OUTPUT, uri)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .also { it.clipData = ClipData.newRawUri("", uri) }
    }

    private fun deliver(resultCode: Int, data: Intent?) {
        val cb = callback ?: return
        callback = null
        if (resultCode != Activity.RESULT_OK) {
            cb.onReceiveValue(null)
            return
        }
        val picked = buildList {
            val clip = data?.clipData
            if (clip != null) for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let(::add)
            data?.data?.let { if (it !in this) add(it) }
        }.filter { uri -> captures.none { it.second == uri } }
        val captured = captures.filter { it.first.length() > 0 }.map { it.second }
        val result = picked.ifEmpty { captured }
        cb.onReceiveValue(result.takeIf { it.isNotEmpty() }?.toTypedArray())
    }
}
