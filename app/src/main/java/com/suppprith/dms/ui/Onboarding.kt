package com.suppprith.dms.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.suppprith.dms.R
import com.suppprith.dms.ui.components.Body
import com.suppprith.dms.ui.components.Caption
import com.suppprith.dms.ui.components.OneJobScreen
import com.suppprith.dms.ui.components.PrimaryButton
import com.suppprith.dms.ui.components.QuietButton
import com.suppprith.dms.ui.components.Title

/** Screen 2: one screen, no carousel, no quiz. */
@Composable
fun FirstRunScreen(onSignIn: () -> Unit) {
    OneJobScreen(
        content = {
            Title(stringResource(R.string.first_run_title))
            Spacer(Modifier.height(16.dp))
            Body(stringResource(R.string.first_run_body), muted = true)
        },
        actions = {
            PrimaryButton(stringResource(R.string.first_run_sign_in), onSignIn)
            Caption(
                stringResource(R.string.first_run_caption),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                align = TextAlign.Center,
            )
        },
    )
}

/** Screen 4: once, after the first sign-in. "Not now" is never asked again automatically. */
@Composable
fun NotifyPromptScreen(onAllow: () -> Unit, onNotNow: () -> Unit) {
    OneJobScreen(
        content = {
            Title(stringResource(R.string.notify_title))
            Spacer(Modifier.height(16.dp))
            Body(stringResource(R.string.notify_body), muted = true)
        },
        actions = {
            PrimaryButton(stringResource(R.string.allow), onAllow)
            QuietButton(
                stringResource(R.string.not_now),
                onNotNow,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp),
            )
        },
    )
}
