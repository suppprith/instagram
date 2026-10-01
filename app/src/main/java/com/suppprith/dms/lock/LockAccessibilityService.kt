package com.suppprith.dms.lock

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import com.suppprith.dms.graph
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Shows the lock sheet when the Instagram app comes to the front without a running pass.
 *
 * Receives window state changes only (no window content). For each one it compares the package
 * name with the Instagram app's and keeps a single boolean, "is Instagram in front", which is
 * needed to send the user home when a pass ends. Nothing about other apps is stored or logged.
 */
class LockAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val expiry = Runnable { onExpiryTimer() }
    private var instagramInFront = false
    private var lastLaunch = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        graph.log.i("lock", "service connected")
        graph.passes.snapshot().pass.endsAt?.let { scheduleExpiry(it) }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // Overlays that sit on top of the current app (shade, keyboard) do not change what is in front.
        if (pkg == SYSTEM_UI || pkg == currentKeyboardPackage()) return
        instagramInFront = pkg == INSTAGRAM_PACKAGE
        if (instagramInFront) onInstagramInFront()
    }

    private fun onInstagramInFront() {
        val settings = graph.settings.state.value.takeIf { it.loaded } ?: runBlocking { graph.settings.current() }
        if (!settings.lockEnabled) return

        val now = System.currentTimeMillis()
        val stored = graph.passes.data.value.pass
        val data = graph.passes.snapshot(now)
        if (data.pass.isActive(now)) return

        // Backstop: the stored pass should have ended already (missed alarm, killed timer).
        if (stored.endsAt != null) graph.scope.launch { Passes.end(applicationContext, "backstop") }
        showLock()
    }

    private fun showLock() {
        val t = SystemClock.elapsedRealtime()
        if (t - lastLaunch < 700) return
        lastLaunch = t
        startActivity(
            Intent(this, LockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION),
        )
    }

    /** Timer inside the always-running service: the most precise of the three relock layers. */
    fun scheduleExpiry(endsAt: Long) {
        handler.removeCallbacks(expiry)
        handler.postDelayed(expiry, (endsAt - System.currentTimeMillis()).coerceAtLeast(0))
    }

    private fun onExpiryTimer() {
        val stored = graph.passes.data.value.pass
        if (stored.endsAt == null) return
        graph.scope.launch { Passes.end(applicationContext, "timer") }
    }

    /** Called when a pass ends for any reason. At zero the user is sent home. */
    fun onPassEnded() {
        handler.post {
            handler.removeCallbacks(expiry)
            if (instagramInFront) performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    fun goHome() {
        handler.post { performGlobalAction(GLOBAL_ACTION_HOME) }
    }

    private fun currentKeyboardPackage(): String? =
        Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.substringBefore('/')

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        const val INSTAGRAM_PACKAGE = "com.instagram.android"
        private const val SYSTEM_UI = "com.android.systemui"

        @Volatile
        var instance: LockAccessibilityService? = null
            private set
    }
}
