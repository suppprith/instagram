package com.suppprith.dms.web

import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

/** File chooser, microphone, full-screen video and window.open, all routed through [WebHost]. */
class CageChromeClient(private val host: WebHost) : WebChromeClient() {

    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams,
    ): Boolean = host.showFileChooser(filePathCallback, fileChooserParams)

    override fun onPermissionRequest(request: PermissionRequest) {
        host.onPermissionRequest(request)
    }

    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        host.showCustomView(view, callback)
    }

    override fun onHideCustomView() {
        host.hideCustomView()
    }

    override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
        callback.invoke(origin, false, false)
    }

    /** Removes the grey play icon WebView draws before a video's first frame. */
    override fun getDefaultVideoPoster(): Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

    /**
     * window.open and target=_blank: catch the first URL in a throwaway WebView, then route it
     * like any other navigation (Instagram stays here, everything else goes to a Custom Tab).
     */
    override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
        if (!isUserGesture) return false
        val popup = WebView(view.context)
        var handled = false
        fun route(url: String?) {
            if (handled || url.isNullOrBlank() || url == "about:blank") return
            handled = true
            host.openFromPopup(url)
            popup.post { popup.stopLoading(); popup.destroy() }
        }
        popup.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                route(request.url.toString())
                return true
            }

            override fun onPageStarted(v: WebView, url: String?, favicon: Bitmap?) = route(url)
        }
        val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
        transport.webView = popup
        resultMsg.sendToTarget()
        return true
    }
}
