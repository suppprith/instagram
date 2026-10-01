package com.suppprith.dms.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.suppprith.dms.R
import com.suppprith.dms.lock.PassPolicy
import com.suppprith.dms.ui.components.Body
import com.suppprith.dms.ui.components.Caption
import com.suppprith.dms.ui.components.OneJobScreen
import com.suppprith.dms.ui.components.PrimaryButton
import com.suppprith.dms.ui.components.QuietButton
import com.suppprith.dms.ui.components.Title
import com.suppprith.dms.ui.theme.Dms

/**
 * Screen 7: a checklist. Each row turns into a check when done; the app detects completion,
 * the user never confirms a step manually.
 */
@Composable
fun LockSetupScreen(
    serviceEnabled: Boolean,
    lockEnabled: Boolean,
    instagramInstalled: Boolean,
    restrictedApplies: Boolean,
    policy: PassPolicy,
    actions: Actions,
) {
    var choosingPolicy by rememberSaveable { mutableStateOf(false) }
    // Step 1 cannot be read back from the system; it is done once step 2 is possible and done.
    val restrictedDone = serviceEnabled

    OneJobScreen(
        content = {
            Title(stringResource(R.string.lock_setup_title))
            Spacer(Modifier.height(12.dp))
            Body(stringResource(R.string.lock_setup_body), muted = true)
            if (!instagramInstalled) {
                Spacer(Modifier.height(8.dp))
                Caption(stringResource(R.string.lock_not_installed))
            }
            Spacer(Modifier.height(24.dp))
            var step = 1
            if (restrictedApplies) {
                StepRow(step++, stringResource(R.string.lock_step_restricted), done = restrictedDone, onClick = actions::openAppInfo)
                if (!restrictedDone) RestrictedSettingsHelp()
            }
            StepRow(step++, stringResource(R.string.lock_step_service), done = serviceEnabled, onClick = actions::openAccessibilitySettings)
            StepRow(step, stringResource(R.string.lock_step_passes), value = policy.label, onClick = { choosingPolicy = true })
        },
        actions = {
            PrimaryButton(
                stringResource(R.string.done),
                onClick = { if (lockEnabled) actions.closeScreen() else actions.enableLock() },
                enabled = serviceEnabled,
            )
            if (lockEnabled) {
                QuietButton(
                    stringResource(R.string.lock_turn_off),
                    actions::disableLock,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = Dms.colors.danger,
                )
            }
        },
    )

    if (choosingPolicy) {
        PolicyDialog(policy, onPick = { actions.setPassPolicy(it); choosingPolicy = false }, onDismiss = { choosingPolicy = false })
    }
}

@Composable
private fun StepRow(number: Int, title: String, done: Boolean = false, value: String? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier
                .size(28.dp)
                .background(if (done) Dms.colors.accent else Dms.colors.surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = Dms.colors.onAccent, modifier = Modifier.size(18.dp))
            } else {
                Text("$number", style = Dms.type.label, color = Dms.colors.text)
            }
        }
        Text(title, style = Dms.type.body, color = Dms.colors.text, modifier = Modifier.weight(1f))
        when {
            value != null -> Text(value, style = Dms.type.body, color = Dms.colors.textMuted)
            !done -> Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = Dms.colors.textMuted, modifier = Modifier.size(20.dp))
        }
    }
}

/** The two system screens, drawn plainly, because "Allow restricted settings" is easy to miss. */
@Composable
private fun RestrictedSettingsHelp() {
    Column(Modifier.padding(start = 44.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Caption(stringResource(R.string.lock_step_restricted_help))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.clearAndSetSemantics { }) {
            MockScreen(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Caption(stringResource(R.string.mock_app_info), color = Dms.colors.text)
                    Box(
                        Modifier.border(1.5.dp, Dms.colors.accent, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp),
                    ) { Caption("⋮", color = Dms.colors.accent) }
                }
                Spacer(Modifier.height(10.dp))
                MockLine(0.7f)
                MockLine(0.5f)
            }
            MockScreen(Modifier.weight(1f)) {
                Caption(stringResource(R.string.mock_uninstall))
                Spacer(Modifier.height(6.dp))
                Box(Modifier.border(1.5.dp, Dms.colors.accent, RoundedCornerShape(6.dp)).padding(4.dp)) {
                    Caption(stringResource(R.string.mock_allow_restricted), color = Dms.colors.accent)
                }
            }
        }
    }
}

@Composable
private fun MockScreen(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .border(1.dp, Dms.colors.divider, RoundedCornerShape(12.dp))
            .background(Dms.colors.surface, RoundedCornerShape(12.dp))
            .padding(10.dp),
    ) { content() }
}

@Composable
private fun MockLine(fraction: Float) {
    Box(
        Modifier
            .padding(vertical = 3.dp)
            .fillMaxWidth(fraction)
            .height(6.dp)
            .background(Dms.colors.divider, RoundedCornerShape(3.dp)),
    )
}

@Composable
private fun PolicyDialog(current: PassPolicy, onPick: (PassPolicy) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Dms.colors.bg,
        title = { Text(stringResource(R.string.lock_step_passes), style = Dms.type.title, color = Dms.colors.text) },
        text = {
            Column(Modifier.selectableGroup()) {
                PassPolicy.Presets.forEach { policy ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(selected = policy == current, onClick = { onPick(policy) }, role = Role.RadioButton),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(
                            selected = policy == current,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = Dms.colors.accent, unselectedColor = Dms.colors.textMuted),
                        )
                        Text(policy.label, style = Dms.type.body, color = Dms.colors.text)
                    }
                }
            }
        },
        confirmButton = { QuietButton(stringResource(R.string.cancel), onDismiss) },
    )
}
