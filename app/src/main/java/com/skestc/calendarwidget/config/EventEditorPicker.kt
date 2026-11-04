package com.skestc.calendarwidget.config

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
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
        // 預設就是 Google 日曆，所以顯示它的圖示（沒裝時不顯示）。
        EditorRow(
            stringResource(R.string.editor_default),
            icon = rememberAppIcon(EventEditor.GOOGLE_CALENDAR),
            selected = choice == null,
        ) { select(null) }
        EditorRow(stringResource(R.string.editor_ask), icon = null, selected = choice == EventEditor.ASK) {
            select(EventEditor.ASK)
        }
        for (app in apps) {
            EditorRow(app.label, icon = rememberAppIcon(app.packageName), selected = choice == app.packageName) {
                select(app.packageName)
            }
        }
    }
}

@Composable
private fun EditorRow(label: String, icon: ImageBitmap?, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(12.dp))
        // 沒有圖示的選項（每次詢問）也留同樣寬度，文字才會對齊。
        Box(modifier = Modifier.size(ICON_SIZE)) {
            if (icon != null) Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(ICON_SIZE))
        }
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

internal val ICON_SIZE = 32.dp

/** App 的原始圖示（含自適應圖示），沒有安裝時回傳 null。 */
@Composable
internal fun rememberAppIcon(packageName: String): ImageBitmap? {
    val context = LocalContext.current
    val density = LocalDensity.current
    return remember(packageName) {
        runCatching {
            val px = with(density) { ICON_SIZE.roundToPx() }
            context.packageManager.getApplicationIcon(packageName).toBitmap(px, px).asImageBitmap()
        }.getOrNull()
    }
}
