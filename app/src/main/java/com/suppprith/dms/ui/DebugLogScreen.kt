package com.suppprith.dms.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.suppprith.dms.R
import com.suppprith.dms.ui.components.Body
import com.suppprith.dms.ui.components.blockTouches
import com.suppprith.dms.ui.components.Hairline
import com.suppprith.dms.ui.components.QuietButton
import com.suppprith.dms.ui.components.Title
import com.suppprith.dms.ui.theme.Dms

@Composable
fun DebugLogScreen(lines: List<String>, actions: Actions) {
    Column(Modifier.fillMaxSize().blockTouches().background(Dms.colors.bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(
            Modifier.fillMaxWidth().padding(start = Dms.screenPadding, end = 8.dp, top = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Title(stringResource(R.string.debug_log_title), Modifier.weight(1f))
            QuietButton(stringResource(R.string.clear), actions::clearLog)
            QuietButton(stringResource(R.string.export), actions::exportLog, color = Dms.colors.accent)
        }
        Hairline()
        if (lines.isEmpty()) {
            Body(stringResource(R.string.debug_log_empty), muted = true, modifier = Modifier.padding(Dms.screenPadding))
        } else {
            val list = rememberLazyListState()
            LaunchedEffect(lines.size) { list.scrollToItem(lines.lastIndex) }
            LazyColumn(state = list, contentPadding = PaddingValues(Dms.screenPadding), modifier = Modifier.weight(1f)) {
                items(lines) { line ->
                    Text(line, style = Dms.type.caption.copy(fontFamily = FontFamily.Monospace), color = Dms.colors.text)
                }
            }
        }
    }
}
