package com.suppprith.dms.web

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import com.suppprith.dms.R

object Links {
    /** Off-Instagram links open in a Custom Tab, falling back to any browser. */
    fun openExternal(context: Context, url: String) {
        val uri = Uri.parse(url)
        try {
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setColorScheme(CustomTabsIntent.COLOR_SCHEME_SYSTEM)
                .build()
                .launchUrl(context, uri)
        } catch (e: ActivityNotFoundException) {
            openWithSystem(context, url)
        }
    }

    fun openWithSystem(context: Context, url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.no_app_to_open, Toast.LENGTH_SHORT).show()
        }
    }
}
