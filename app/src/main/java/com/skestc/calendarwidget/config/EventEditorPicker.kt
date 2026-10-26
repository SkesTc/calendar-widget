package com.skestc.calendarwidget.config

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.skestc.calendarwidget.R
import com.skestc.calendarwidget.settings.EventEditor

/** 選「＋」要開哪個 App 的新增行程畫面；選了立即儲存，所有 widget 共用。 */
@Composable
fun EventEditorPicker() {
    val context = LocalContext.current
    val apps = remember { EventEditor.apps(context) }
    var choice by remember { mutableStateOf(EventEditor.choice(context)) }

    fun select(value: String?) {
        choice = value
        EventEditor.setChoice(context, value)
    }

    Column {
        Text(
            stringResource(R.string.editor_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        EditorRow(stringResource(R.string.editor_default), selected = choice == null) { select(null) }
        EditorRow(stringResource(R.string.editor_ask), selected = choice == EventEditor.ASK) { select(EventEditor.ASK) }
        for (app in apps) {
            EditorRow(app.label, selected = choice == app.packageName) { select(app.packageName) }
        }
    }
}

@Composable
private fun EditorRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
