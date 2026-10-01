package com.suppprith.dms.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.suppprith.dms.R
import com.suppprith.dms.ui.components.Hairline
import com.suppprith.dms.ui.theme.Dms

/** Messages, Activity, Profile. Icons only; TalkBack still reads a label for each. */
@Composable
fun BottomBar(active: Tab?, unread: Boolean, onTab: (Tab) -> Unit, onLongPress: (Tab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Dms.colors.bg).navigationBarsPadding()) {
        Hairline()
        Row(Modifier.fillMaxWidth().height(56.dp)) {
            TabItem(Tab.Messages, active, R.drawable.ic_messages, R.drawable.ic_messages_filled, R.string.tab_messages, unread, onTab, onLongPress)
            TabItem(Tab.Activity, active, R.drawable.ic_activity, R.drawable.ic_activity_filled, R.string.tab_activity, false, onTab, onLongPress)
            TabItem(Tab.Profile, active, R.drawable.ic_profile, R.drawable.ic_profile_filled, R.string.tab_profile, false, onTab, onLongPress)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun androidx.compose.foundation.layout.RowScope.TabItem(
    tab: Tab,
    active: Tab?,
    @DrawableRes outlined: Int,
    @DrawableRes filled: Int,
    label: Int,
    dot: Boolean,
    onTab: (Tab) -> Unit,
    onLongPress: (Tab) -> Unit,
) {
    val isActive = tab == active
    val name = stringResource(label)
    val unreadLabel = stringResource(R.string.unread)
    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 28.dp),
                role = Role.Tab,
                onClick = { onTab(tab) },
                onLongClick = { onLongPress(tab) },
            )
            .semantics {
                contentDescription = name
                selected = isActive
                if (dot) stateDescription = unreadLabel
            },
        contentAlignment = Alignment.Center,
    ) {
        Box {
            Icon(
                painterResource(if (isActive) filled else outlined),
                contentDescription = null,
                tint = if (isActive) Dms.colors.accent else Dms.colors.text,
                modifier = Modifier.size(24.dp),
            )
            if (dot) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 3.dp, y = (-1).dp)
                        .size(8.dp)
                        .background(Dms.colors.accent, CircleShape),
                )
            }
        }
    }
}
