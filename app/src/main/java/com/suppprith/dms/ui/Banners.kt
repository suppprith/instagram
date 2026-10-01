package com.suppprith.dms.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.suppprith.dms.R
import com.suppprith.dms.ui.components.Hairline
import com.suppprith.dms.ui.components.QuietButton
import com.suppprith.dms.ui.theme.Dms

/** A quiet one-line banner above the bottom bar: text, one verb, optional dismiss. */
@Composable
fun Banner(text: String, action: String, onAction: () -> Unit, onDismiss: (() -> Unit)? = null) {
    Hairline()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(Dms.colors.surface)
            .padding(start = Dms.screenPadding, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text, style = Dms.type.body, color = Dms.colors.text, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
        QuietButton(action, onAction, color = Dms.colors.accent)
        if (onDismiss != null) {
            IconButton(onClick = onDismiss) {
                Icon(painterResource(R.drawable.ic_close), stringResource(R.string.dismiss), tint = Dms.colors.textMuted, modifier = Modifier.size(20.dp))
            }
        }
    }
}
