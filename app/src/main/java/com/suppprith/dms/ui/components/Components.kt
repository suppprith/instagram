package com.suppprith.dms.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.suppprith.dms.R
import com.suppprith.dms.ui.theme.Dms

/** Full-screen overlays sit on top of the WebView; this keeps touches from reaching it. */
fun Modifier.blockTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent()
    }
}

@Composable
fun Title(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier.semantics { heading() }, style = Dms.type.title, color = Dms.colors.text)
}

@Composable
fun Body(text: String, modifier: Modifier = Modifier, muted: Boolean = false, align: TextAlign? = null) {
    Text(text, modifier = modifier, style = Dms.type.body, color = if (muted) Dms.colors.textMuted else Dms.colors.text, textAlign = align)
}

@Composable
fun Caption(text: String, modifier: Modifier = Modifier, align: TextAlign? = null, color: Color = Dms.colors.textMuted) {
    Text(text, modifier = modifier, style = Dms.type.caption, color = color, textAlign = align)
}

/** The one accent-coloured action on a screen. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = Dms.rowShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Dms.colors.accent,
            contentColor = Dms.colors.onAccent,
            disabledContainerColor = Dms.colors.surface,
            disabledContentColor = Dms.colors.textMuted,
        ),
    ) {
        Text(text, style = Dms.type.label)
    }
}

@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Dms.colors.text) {
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp)) {
        Text(text, style = Dms.type.label, color = color)
    }
}

/**
 * Full-screen layout for one-job screens: title and body at the top, actions at the bottom.
 * Scrolls instead of truncating at large font scales.
 */
@Composable
fun OneJobScreen(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
    actions: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .blockTouches()
            .background(Dms.colors.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val viewport = maxHeight
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Column(
                Modifier.fillMaxWidth().heightIn(min = viewport).padding(Dms.screenPadding),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Spacer(Modifier.height(48.dp))
                    content()
                }
                Column(Modifier.padding(top = 24.dp)) { actions() }
            }
        }
    }
}

/** A plain settings row: title on the left, value or chevron on the right. */
@Composable
fun SettingsRow(
    title: String,
    onClick: () -> Unit,
    value: String? = null,
    chevron: Boolean = false,
    titleColor: Color = Dms.colors.text,
    caption: String? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Dms.screenPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Dms.type.body, color = titleColor)
            if (caption != null) Caption(caption)
        }
        if (value != null) Text(value, style = Dms.type.body, color = Dms.colors.textMuted)
        if (chevron) {
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = Dms.colors.textMuted, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, thickness = 1.dp, color = Dms.colors.divider)
}

@Composable
fun CenteredMessage(title: String, action: String, onAction: () -> Unit) {
    Box(
        Modifier.fillMaxSize().blockTouches().background(Dms.colors.bg).windowInsetsPadding(WindowInsets.safeDrawing).padding(Dms.screenPadding),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Body(title, align = TextAlign.Center)
            PrimaryButton(action, onAction, Modifier.fillMaxWidth(0.6f))
        }
    }
}
