package com.suppprith.dms.ui

import android.view.View
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.suppprith.dms.update.Release

enum class Screen { Main, LockSetup, DebugLog }

/** Everything the native frame draws, fed by the WebView, the bridge and the activity. */
@Stable
class UiState {
    var route by mutableStateOf(Route.Empty)
    var ready by mutableStateOf(false)
    var splashTimedOut by mutableStateOf(false)
    var offline by mutableStateOf(false)
    var signedIn by mutableStateOf(false)
    var badge by mutableIntStateOf(0)
    var customView by mutableStateOf<View?>(null)
    var showSettings by mutableStateOf(false)
    var screen by mutableStateOf(Screen.Main)
    var update by mutableStateOf<Release?>(null)
    var checkingUpdate by mutableStateOf(false)
    var lockServiceEnabled by mutableStateOf(false)
    var signOutConfirm by mutableStateOf(false)
}
