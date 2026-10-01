package com.suppprith.dms.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.suppprith.dms.BuildConfig
import com.suppprith.dms.R
import com.suppprith.dms.ui.components.Hairline
import com.suppprith.dms.ui.components.QuietButton
import com.suppprith.dms.ui.components.SettingsRow
import com.suppprith.dms.ui.theme.Dms
import com.suppprith.dms.util.AppSettings

/** Screen 6: plain grouped list, no icons. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(state: UiState, settings: AppSettings, batteryOptimized: Boolean, actions: Actions) {
    ModalBottomSheet(
        onDismissRequest = actions::closeSettings,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = Dms.sheetShape,
        containerColor = Dms.colors.bg,
        contentColor = Dms.colors.text,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 8.dp)) {
            SettingsRow(
                title = stringResource(R.string.settings_notifications),
                value = stringResource(if (settings.notificationsEnabled) R.string.on else R.string.off),
                onClick = { actions.setNotifications(!settings.notificationsEnabled) },
            )
            if (settings.notificationsEnabled && batteryOptimized) {
                SettingsRow(
                    title = stringResource(R.string.settings_battery),
                    caption = stringResource(R.string.settings_battery_caption),
                    chevron = true,
                    onClick = actions::openBatterySettings,
                )
            }
            SettingsRow(
                title = stringResource(R.string.settings_lock),
                value = stringResource(if (settings.lockEnabled) R.string.on else R.string.off),
                onClick = actions::openLockSetup,
            )
            Hairline(Modifier.padding(vertical = 8.dp))
            SettingsRow(title = stringResource(R.string.settings_account), chevron = true, onClick = actions::openAccount)
            SettingsRow(
                title = stringResource(R.string.settings_sign_out),
                titleColor = Dms.colors.danger,
                onClick = actions::confirmSignOut,
            )
            Hairline(Modifier.padding(vertical = 8.dp))
            val update = state.update
            if (update != null) {
                SettingsRow(
                    title = stringResource(R.string.settings_update_available, update.version.toString()),
                    titleColor = Dms.colors.accent,
                    chevron = true,
                    onClick = { actions.openUpdate(update) },
                )
            } else {
                SettingsRow(
                    title = stringResource(R.string.settings_check_updates),
                    value = if (state.checkingUpdate) stringResource(R.string.checking) else "v${BuildConfig.VERSION_NAME}",
                    onClick = actions::checkForUpdates,
                )
            }
            SettingsRow(title = stringResource(R.string.settings_debug_log), chevron = true, onClick = actions::openDebugLog)
        }
    }
}

@Composable
fun SignOutDialog(actions: Actions) {
    AlertDialog(
        onDismissRequest = actions::cancelSignOut,
        containerColor = Dms.colors.bg,
        title = { Text(stringResource(R.string.sign_out_title), style = Dms.type.title, color = Dms.colors.text) },
        text = { Text(stringResource(R.string.sign_out_body), style = Dms.type.body, color = Dms.colors.textMuted) },
        confirmButton = { QuietButton(stringResource(R.string.sign_out), actions::signOut, color = Dms.colors.danger) },
        dismissButton = { QuietButton(stringResource(R.string.cancel), actions::cancelSignOut) },
    )
}
