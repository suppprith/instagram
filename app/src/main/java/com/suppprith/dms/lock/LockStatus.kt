package com.suppprith.dms.lock

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

object LockStatus {
    fun isServiceEnabled(context: Context): Boolean {
        val expected = ComponentName(context, LockAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
    }

    fun isInstagramInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(LockAccessibilityService.INSTAGRAM_PACKAGE, 0)
    }.isSuccess

    /**
     * Android 13+ greys out accessibility for sideloaded apps until the user allows restricted
     * settings in App info. Earlier versions never need the step.
     */
    fun restrictedSettingsApply(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun appInfoIntent(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun accessibilitySettingsIntent() =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
