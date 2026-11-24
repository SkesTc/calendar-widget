package com.skestc.calendarwidget.config

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.skestc.calendarwidget.R
import com.skestc.calendarwidget.settings.EventEditor
import com.skestc.calendarwidget.settings.TitleTap

/**
 * 點左上角的日期（行程清單）或月份標題（月曆）時要做什麼：
 * 跳出選單、App 內的行程檢視、選擇月份（月曆），或手機上任何一個日曆 App（含內建的）。
 */
@Composable
internal fun TitleTapPicker(isMonth: Boolean, selected: TitleTap, onSelect: (TitleTap) -> Unit) {
    val context = LocalContext.current
    val apps = remember { EventEditor.apps(context) }
    Column {
        Option(stringResource(R.string.title_tap_menu), R.drawable.ic_more_horiz, null, selected == TitleTap.Menu) {
            onSelect(TitleTap.Menu)
        }
        Option(
            stringResource(if (isMonth) R.string.menu_view_month else R.string.menu_view_today),
            R.drawable.ic_event,
            null,
            selected == TitleTap.Viewer,
        ) { onSelect(TitleTap.Viewer) }
        if (isMonth) {
            Option(stringResource(R.string.menu_pick_month), R.drawable.ic_event, null, selected == TitleTap.MonthPicker) {
                onSelect(TitleTap.MonthPicker)
            }
        }
        for (app in apps) {
            Option(
                stringResource(R.string.menu_open_app, app.label),
                R.drawable.ic_event,
                rememberAppIcon(app.packageName),
                selected == TitleTap.App(app.packageName),
            ) { onSelect(TitleTap.App(app.packageName)) }
        }
        // 選過的 App 被移除了：仍列出來，讓使用者知道現在是交給系統預設。
        val missing = (selected as? TitleTap.App)?.takeIf { s -> apps.none { it.packageName == s.packageName } }
        if (missing != null) {
            Option(stringResource(R.string.title_tap_missing_app), R.drawable.ic_event, null, selected = true) {}
        }
    }
}

@Composable
private fun Option(label: String, icon: Int, appIcon: ImageBitmap?, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(12.dp))
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            if (appIcon != null) {
                Image(bitmap = appIcon, contentDescription = null, modifier = Modifier.size(28.dp))
            } else {
                Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
