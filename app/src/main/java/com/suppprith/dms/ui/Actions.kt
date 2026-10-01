package com.suppprith.dms.ui

import com.suppprith.dms.lock.PassPolicy
import com.suppprith.dms.update.Release

/** Everything the native UI can ask for. Implemented by MainActivity. */
interface Actions {
    fun onTab(tab: Tab)
    fun openSettings()
    fun closeSettings()
    fun finishFirstRun()
    fun allowNotifications()
    fun declineNotifications()
    fun setNotifications(enabled: Boolean)
    fun openBatterySettings()
    fun openLockSetup()
    fun openAccount()
    fun confirmSignOut()
    fun signOut()
    fun cancelSignOut()
    fun checkForUpdates()
    fun openUpdate(release: Release)
    fun dismissUpdate(release: Release)
    fun openDebugLog()
    fun exportLog()
    fun clearLog()
    fun retry()
    fun closeScreen()
    fun openAppInfo()
    fun openAccessibilitySettings()
    fun setPassPolicy(policy: PassPolicy)
    fun enableLock()
    fun disableLock()
}
