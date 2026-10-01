package com.suppprith.dms.ui

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.suppprith.dms.R
import com.suppprith.dms.lock.LockData
import com.suppprith.dms.ui.components.CenteredMessage
import com.suppprith.dms.ui.theme.Dms
import com.suppprith.dms.util.AppSettings

/** Extra state the activity reads from the system for the native frame. */
data class SystemState(
    val batteryOptimized: Boolean = false,
    val instagramInstalled: Boolean = true,
    val restrictedSettingsApply: Boolean = false,
)

@Composable
fun AppScreen(
    state: UiState,
    settings: AppSettings,
    lock: LockData,
    system: SystemState,
    logLines: List<String>,
    webView: WebView,
    actions: Actions,
) {
    val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val route = state.route
    val barVisible = bottomBarVisible(route, state.signedIn, keyboardOpen, state.customView != null)
    val activeTab = route.tab(state.username)

    Box(Modifier.fillMaxSize().background(Dms.colors.bg)) {
        Column(Modifier.fillMaxSize().imePadding()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
            ) {
                CageWebView(webView, route)
                if (barVisible && route.isOwnProfile(state.username)) {
                    IconButton(
                        onClick = actions::openSettings,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .background(Dms.colors.bg.copy(alpha = 0.92f), CircleShape),
                    ) {
                        Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.settings), tint = Dms.colors.text, modifier = Modifier.size(24.dp))
                    }
                }
            }

            if (barVisible) {
                val update = state.update
                when {
                    settings.lockEnabled && !state.lockServiceEnabled ->
                        Banner(stringResource(R.string.banner_lock_off), stringResource(R.string.turn_on), actions::openLockSetup)
                    update != null && route.isInbox && settings.dismissedUpdate != update.version.toString() ->
                        Banner(
                            stringResource(R.string.banner_update, update.version.toString()),
                            stringResource(R.string.download),
                            onAction = { actions.openUpdate(update) },
                            onDismiss = { actions.dismissUpdate(update) },
                        )
                }
                BottomBar(
                    active = activeTab,
                    unread = state.badge > 0,
                    avatar = state.avatar,
                    onTab = actions::onTab,
                    onLongPress = { if (it == Tab.Profile) actions.openSettings() },
                )
            } else if (state.customView == null) {
                Spacer(Modifier.navigationBarsPadding())
            }
        }

        // Overlays, in priority order.
        val showFirstRun = settings.loaded && !settings.firstRunDone && !state.signedIn
        val showNotifyPrompt = settings.loaded && !settings.notifyAsked && state.signedIn && route.isInbox && state.ready
        when {
            state.customView != null -> FullScreenVideo(state.customView!!)
            state.offline -> CenteredMessage(stringResource(R.string.offline_title), stringResource(R.string.retry), actions::retry)
            showFirstRun -> FirstRunScreen(actions::finishFirstRun)
            showNotifyPrompt -> NotifyPromptScreen(actions::allowNotifications, actions::declineNotifications)
        }

        // Full screens on top of the WebView, which stays attached so its page never reloads.
        when (state.screen) {
            Screen.LockSetup -> {
                BackHandler { actions.closeScreen() }
                LockSetupScreen(
                    serviceEnabled = state.lockServiceEnabled,
                    lockEnabled = settings.lockEnabled,
                    instagramInstalled = system.instagramInstalled,
                    restrictedApplies = system.restrictedSettingsApply,
                    policy = lock.policy,
                    actions = actions,
                )
            }
            Screen.DebugLog -> {
                BackHandler { actions.closeScreen() }
                DebugLogScreen(logLines, actions)
            }
            Screen.Main -> Unit
        }

        if (state.showSettings) SettingsSheet(state, settings, system.batteryOptimized, actions)
        if (state.signOutConfirm) SignOutDialog(actions)
    }
}

/**
 * The WebView itself. Opening or leaving a thread slides it a short way on the horizontal axis,
 * so the change reads as native navigation rather than a page load.
 */
@Composable
private fun CageWebView(webView: WebView, route: Route) {
    val offset = remember { Animatable(0f) }
    val inThread = route.isThread
    val first = remember { booleanArrayOf(true) }
    LaunchedEffect(inThread) {
        if (first[0]) {
            first[0] = false
            return@LaunchedEffect
        }
        offset.snapTo(if (inThread) 1f else -1f)
        offset.animateTo(0f, tween(durationMillis = Dms.MOTION_MS, easing = FastOutSlowInEasing))
    }
    val shift = with(LocalDensity.current) { 32.dp.toPx() }
    AndroidView(
        factory = {
            (webView.parent as? android.view.ViewGroup)?.removeView(webView)
            webView
        },
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = offset.value * shift
                alpha = 1f - kotlin.math.abs(offset.value) * 0.4f
            },
    )
}

@Composable
private fun FullScreenVideo(view: android.view.View) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
    }
}
