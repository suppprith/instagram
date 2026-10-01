package com.suppprith.dms.lock

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.suppprith.dms.MainActivity
import com.suppprith.dms.R
import com.suppprith.dms.graph
import com.suppprith.dms.ui.components.Caption
import com.suppprith.dms.ui.components.Body
import com.suppprith.dms.ui.components.OneJobScreen
import com.suppprith.dms.ui.components.PrimaryButton
import com.suppprith.dms.ui.components.QuietButton
import com.suppprith.dms.ui.components.Title
import com.suppprith.dms.ui.theme.Dms
import com.suppprith.dms.ui.theme.DmsTheme
import kotlinx.coroutines.launch

/** "What do you need Instagram for?" Shown on top of the Instagram app when it opens without a pass. */
class LockActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        onBackPressedDispatcher.addCallback(this) { goBack() }
        setContent {
            DmsTheme {
                val lock by graph.passes.data.collectAsStateWithLifecycle()
                val now = System.currentTimeMillis()
                val pass = remember(lock) { graph.passes.snapshot(now).pass }
                val scope = rememberCoroutineScope()
                LockSheet(
                    left = pass.left(lock.policy),
                    nextMinutes = lock.policy.minutesAfterUsed(pass.used),
                    onOpen = { reason ->
                        scope.launch {
                            if (Passes.start(applicationContext, reason) != null) finish()
                        }
                    },
                    onGoBack = ::goBack,
                    onOpenMessages = {
                        startActivity(
                            Intent(this, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                .putExtra(MainActivity.EXTRA_OPEN_INBOX, true),
                        )
                        finish()
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // A pass may have started elsewhere (a second tap) or the lock been turned off meanwhile.
        val now = System.currentTimeMillis()
        val settings = graph.settings.state.value
        if ((settings.loaded && !settings.lockEnabled) || graph.passes.snapshot(now).pass.isActive(now)) finish()
    }

    /** Back to the home screen, leaving the Instagram app behind the lock. */
    private fun goBack() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}

@Composable
private fun LockSheet(
    left: Int,
    nextMinutes: Int?,
    onOpen: (PassReason) -> Unit,
    onGoBack: () -> Unit,
    onOpenMessages: () -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf<PassReason?>(null) }
    val noneLeft = left == 0 || nextMinutes == null

    OneJobScreen(
        content = {
            Title(stringResource(R.string.lock_title))
            Spacer(Modifier.height(24.dp))
            if (noneLeft) {
                Body(stringResource(R.string.lock_none_left), muted = true)
            } else {
                Column(Modifier.selectableGroup()) {
                    PassReason.entries.forEach { reason ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(selected = selected == reason, onClick = { selected = reason }, role = Role.RadioButton)
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            RadioButton(
                                selected = selected == reason,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = Dms.colors.accent, unselectedColor = Dms.colors.textMuted),
                            )
                            Text(reason.label, style = Dms.type.body, color = Dms.colors.text)
                        }
                    }
                }
            }
        },
        actions = {
            if (noneLeft) {
                PrimaryButton(stringResource(R.string.lock_open_messages), onOpenMessages)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    QuietButton(stringResource(R.string.lock_go_back), onGoBack)
                }
            } else {
                PrimaryButton(
                    text = stringResource(R.string.lock_open_for, nextMinutes ?: 0),
                    onClick = { selected?.let(onOpen) },
                    enabled = selected != null,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Caption(stringResource(R.string.lock_left_today, left))
                    QuietButton(stringResource(R.string.lock_go_back), onGoBack)
                }
            }
        },
    )
}
