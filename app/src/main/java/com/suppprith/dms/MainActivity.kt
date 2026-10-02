package com.suppprith.dms

import android.Manifest
import android.animation.ValueAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.suppprith.dms.lock.LockStatus
import com.suppprith.dms.lock.PassPolicy
import com.suppprith.dms.lock.Passes
import com.suppprith.dms.notify.Notifications
import com.suppprith.dms.ui.Actions
import com.suppprith.dms.ui.AppScreen
import com.suppprith.dms.ui.Screen
import com.suppprith.dms.ui.SystemState
import com.suppprith.dms.ui.UiState
import com.suppprith.dms.ui.theme.DmsTheme
import com.suppprith.dms.update.Release
import com.suppprith.dms.web.FilePicker
import com.suppprith.dms.web.Links
import com.suppprith.dms.web.WebHost
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity(), Actions {
    private val state = UiState()
    private val system = mutableStateOf(SystemState())
    private lateinit var host: WebHost
    lateinit var filePicker: FilePicker
        private set

    private var pendingPermission: ((Boolean) -> Unit)? = null
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val callback = pendingPermission
        pendingPermission = null
        callback?.invoke(granted)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val accessibilityObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean) {
            state.lockServiceEnabled = LockStatus.isServiceEnabled(this@MainActivity)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        filePicker = FilePicker(this)
        host = WebHost(this, state)

        // Screen 1: icon on the theme background until the inbox (or sign-in) renders, at most 8 s.
        splash.setKeepOnScreenCondition { keepSplash() }
        splash.setOnExitAnimationListener { provider ->
            if (!ValueAnimator.areAnimatorsEnabled()) {
                provider.remove()
            } else {
                provider.view.animate().alpha(0f).setDuration(200).withEndAction { provider.remove() }.start()
            }
        }
        mainHandler.postDelayed({ state.splashTimedOut = true }, SPLASH_TIMEOUT_MS)

        host.start(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)

        onBackPressedDispatcher.addCallback(this) {
            if (state.showSettings) {
                closeSettings()
            } else if (!host.handleBack()) {
                // At the inbox: hand back to the system (moves the task to the back on Android 12+).
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        }

        setContent {
            DmsTheme {
                val settings by graph.settings.state.collectAsStateWithLifecycle()
                val lock by graph.passes.data.collectAsStateWithLifecycle()
                val lines by graph.log.lines.collectAsStateWithLifecycle()
                val sys by system
                AppScreen(state, settings, lock, sys, lines, host.webView, this)
            }
        }

        // A session that already exists (reinstall over an old build) skips the first-run screen.
        lifecycleScope.launch {
            snapshotFlow { state.signedIn }.filter { it }.first()
            if (!graph.settings.current().firstRunDone) graph.settings.setFirstRunDone()
        }
        lifecycleScope.launch {
            state.update = graph.updates.check()
        }
        registerNetworkCallback()
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            false,
            accessibilityObserver,
        )
    }

    private fun keepSplash(): Boolean {
        if (state.ready || state.splashTimedOut || state.offline) return false
        val settings = graph.settings.state.value
        if (!settings.loaded) return true
        return settings.firstRunDone || state.signedIn
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == Intent.ACTION_SEND) {
            // Share into the app: put the text on the clipboard and open the inbox to paste it.
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!text.isNullOrBlank()) {
                getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(text.take(40), text))
                Toast.makeText(this, R.string.shared_text_copied, Toast.LENGTH_SHORT).show()
            }
            openInbox()
        } else if (intent.getBooleanExtra(EXTRA_OPEN_INBOX, false)) {
            openInbox()
        }
    }

    private fun openInbox() {
        state.screen = Screen.Main
        state.showSettings = false
        if (!state.route.isInbox && state.route.isInstagram) host.navigatePath(WebHost.INBOX_PATH)
    }

    override fun onResume() {
        super.onResume()
        host.onResume()
        Notifications.cancelUnread(this)
        refreshSystemState()
        lifecycleScope.launch { graph.patches.refresh() }
    }

    override fun onPause() {
        host.onPause()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        host.saveState(outState)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        contentResolver.unregisterContentObserver(accessibilityObserver)
        networkCallback?.let { runCatching { getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(it) } }
        host.destroy()
        super.onDestroy()
    }

    private fun refreshSystemState() {
        state.lockServiceEnabled = LockStatus.isServiceEnabled(this)
        val power = getSystemService(PowerManager::class.java)
        system.value = SystemState(
            batteryOptimized = power?.isIgnoringBatteryOptimizations(packageName) == false,
            instagramInstalled = LockStatus.isInstagramInstalled(this),
            restrictedSettingsApply = LockStatus.restrictedSettingsApply(),
        )
    }

    /** Retries automatically when the network returns. */
    private fun registerNetworkCallback() {
        val connectivity = getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                mainHandler.post { if (state.offline) host.retry() }
            }
        }
        runCatching { connectivity.registerDefaultNetworkCallback(callback) }.onSuccess { networkCallback = callback }
    }

    // ------------------------------------------------------------------ helpers used by WebHost

    /** Asks for a runtime permission at the moment of use, never up front. */
    fun withPermission(permission: String, onResult: (Boolean) -> Unit) {
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            onResult(true)
            return
        }
        pendingPermission?.invoke(false)
        pendingPermission = onResult
        permissionLauncher.launch(permission)
    }

    fun setFullScreen(fullScreen: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (fullScreen) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // ------------------------------------------------------------------ Actions

    override fun openSettings() {
        state.showSettings = true
    }

    override fun closeSettings() {
        state.showSettings = false
    }

    override fun finishFirstRun() {
        lifecycleScope.launch { graph.settings.setFirstRunDone() }
    }

    override fun allowNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            withPermission(Manifest.permission.POST_NOTIFICATIONS) { granted ->
                lifecycleScope.launch { graph.settings.setNotifications(granted) }
            }
        } else {
            lifecycleScope.launch { graph.settings.setNotifications(true) }
        }
    }

    override fun declineNotifications() {
        lifecycleScope.launch { graph.settings.setNotifications(false) }
    }

    override fun setNotifications(enabled: Boolean) {
        if (!enabled) {
            declineNotifications()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            withPermission(Manifest.permission.POST_NOTIFICATIONS) { granted ->
                if (granted) {
                    lifecycleScope.launch { graph.settings.setNotifications(true) }
                } else {
                    // Denied before: the system will not ask again, so open the app's notification settings.
                    startSafely(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
                }
            }
        } else {
            lifecycleScope.launch { graph.settings.setNotifications(true) }
        }
    }

    override fun openBatterySettings() {
        startSafely(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    override fun openLockSetup() {
        state.showSettings = false
        refreshSystemState()
        state.screen = Screen.LockSetup
    }

    override fun openAccount() {
        state.showSettings = false
        host.navigatePath("/accounts/edit/")
    }

    override fun confirmSignOut() {
        state.showSettings = false
        state.signOutConfirm = true
    }

    override fun signOut() {
        state.signOutConfirm = false
        host.signOut {
            lifecycleScope.launch { graph.settings.setLastBadge(0) }
            Notifications.cancelUnread(this)
        }
    }

    override fun cancelSignOut() {
        state.signOutConfirm = false
    }

    override fun checkForUpdates() {
        if (state.checkingUpdate) return
        state.checkingUpdate = true
        lifecycleScope.launch {
            state.update = graph.updates.check(force = true)
            state.checkingUpdate = false
            if (state.update == null) Toast.makeText(this@MainActivity, R.string.settings_up_to_date, Toast.LENGTH_SHORT).show()
        }
    }

    override fun openUpdate(release: Release) {
        Links.openWithSystem(this, release.url)
    }

    override fun dismissUpdate(release: Release) {
        lifecycleScope.launch { graph.settings.dismissUpdate(release.version.toString()) }
    }

    override fun openDebugLog() {
        state.showSettings = false
        state.screen = Screen.DebugLog
    }

    override fun exportLog() {
        val dir = File(cacheDir, "logs").apply { mkdirs() }
        val file = File(dir, "debug-log.txt")
        runCatching { file.writeText(graph.log.text()) }.onFailure { return }
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startSafely(Intent.createChooser(send, getString(R.string.debug_log_title)))
    }

    override fun clearLog() = graph.log.clear()

    override fun retry() = host.retry()

    override fun closeScreen() {
        state.screen = Screen.Main
    }

    override fun openAppInfo() {
        startSafely(LockStatus.appInfoIntent(this))
    }

    override fun openAccessibilitySettings() {
        startSafely(LockStatus.accessibilitySettingsIntent())
    }

    override fun setPassPolicy(policy: PassPolicy) {
        lifecycleScope.launch { graph.passes.setPolicy(policy) }
    }

    override fun enableLock() {
        lifecycleScope.launch { graph.settings.setLockEnabled(true) }
        graph.log.i("lock", "lock on")
        state.screen = Screen.Main
    }

    override fun disableLock() {
        lifecycleScope.launch {
            graph.settings.setLockEnabled(false)
            Passes.end(applicationContext, "lock turned off")
        }
        state.screen = Screen.Main
    }

    private fun startSafely(intent: Intent) {
        runCatching { startActivity(intent) }
            .onFailure { Toast.makeText(this, R.string.no_app_to_open, Toast.LENGTH_SHORT).show() }
    }

    companion object {
        const val EXTRA_OPEN_INBOX = "com.suppprith.dms.extra.OPEN_INBOX"
        private const val SPLASH_TIMEOUT_MS = 8_000L
    }
}
