package com.suppprith.dms.web

import android.graphics.Bitmap
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

/** Native half of the cage: URL gate, outbound links, errors. Decisions are made by [WebHost]. */
class CageClient(private val host: WebHost) : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        if (!request.isForMainFrame) return false
        return host.overrideNavigation(request.url.toString())
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        host.onPageStarted(url)
    }

    override fun onPageFinished(view: WebView, url: String?) {
        host.onPageFinished(url)
    }

    /** Fires on every history change, including Instagram's in-page (pushState) navigation. */
    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
        host.onHistoryChanged(url)
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (request.isForMainFrame) host.onMainFrameError(error.errorCode)
    }

    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        host.onRendererGone()
        return true
    }
}
