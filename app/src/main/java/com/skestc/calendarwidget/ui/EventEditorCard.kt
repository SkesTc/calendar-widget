package com.skestc.calendarwidget.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.skestc.calendarwidget.R
import com.skestc.calendarwidget.config.EventEditorPicker
import com.skestc.calendarwidget.config.ICON_SIZE
import com.skestc.calendarwidget.config.rememberAppIcon
import com.skestc.calendarwidget.settings.EventEditor

/** 首頁的「＋新增行程使用」卡片：顯示目前選的 App，按「更改」跳出選單（所有 widget 共用）。 */
@Composable
fun EventEditorCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val apps = remember { EventEditor.apps(context) }
    var label by remember { mutableStateOf(EventEditor.choiceLabel(context, apps)) }
    var choice by remember { mutableStateOf(EventEditor.choice(context)) }
    var picking by remember { mutableStateOf(false) }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.section_editor), style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 目前選的 App 的圖示；預設是 Google 日曆，「每次詢問」沒有圖示。
                val iconPackage = when (choice) {
                    null -> EventEditor.GOOGLE_CALENDAR
                    EventEditor.ASK -> null
                    else -> choice
                }
                val icon = iconPackage?.let { rememberAppIcon(it) }
                if (icon != null) {
                    Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(ICON_SIZE))
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            OutlinedButton(onClick = { picking = true }) {
                Text(stringResource(R.string.editor_change))
            }
        }
    }

    if (picking) {
        val close = {
            picking = false
            label = EventEditor.choiceLabel(context, apps)
            choice = EventEditor.choice(context)
        }
        AlertDialog(
            onDismissRequest = close,
            title = { Text(stringResource(R.string.section_editor)) },
            // 選項會立即儲存；App 很多時可以捲動。
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { EventEditorPicker() } },
            confirmButton = { TextButton(onClick = close) { Text(stringResource(R.string.done)) } },
        )
    }
}
