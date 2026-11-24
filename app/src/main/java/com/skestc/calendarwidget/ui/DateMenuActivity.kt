package com.skestc.calendarwidget.ui

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.skestc.calendarwidget.R
import com.skestc.calendarwidget.config.rememberAppIcon
import com.skestc.calendarwidget.data.ViewMode
import com.skestc.calendarwidget.settings.EventEditor
import com.skestc.calendarwidget.ui.theme.CalendarWidgetTheme
import com.skestc.calendarwidget.widget.CalendarLaunchActivity
import com.skestc.calendarwidget.widget.openDayIntent
import java.time.LocalDate
import java.time.YearMonth

/**
 * 點行程清單左上角的日期、或月曆的月份標題時跳出的選單：
 * 選要看 App 內的行程（今天／本週／這個月）、開自選的日曆 App、選其他月份、新增或搜尋行程。
 * 透明 Activity，只顯示一個對話框。
 */
class DateMenuActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val hidden = intent.getLongArrayExtra(EXTRA_HIDDEN)?.toSet().orEmpty()
        val isMonth = intent.getBooleanExtra(EXTRA_MONTH, false)
        val monthOffset = intent.getIntExtra(EXTRA_OFFSET, 0)
        val today = LocalDate.now()
        // 月曆：看的是哪個月就以那個月為準（本月時用今天）。
        val date = if (isMonth && monthOffset != 0) YearMonth.now().plusMonths(monthOffset.toLong()).atDay(1) else today
        setContent {
            CalendarWidgetTheme {
                DateMenu(
                    isMonth = isMonth,
                    monthOffset = monthOffset,
                    canChangeMonth = appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID,
                    onViewer = { mode -> open(EventViewerActivity.intent(this, date, hidden, mode = mode)) },
                    onCalendarApp = {
                        open(CalendarLaunchActivity.intentFor(this, openDayIntent(date), chosenApp = true))
                    },
                    onPickMonth = { open(MonthPickerActivity.intentFor(this, appWidgetId, monthOffset)) },
                    onThisMonth = {
                        MonthPickerActivity.jumpTo(applicationContext, appWidgetId, YearMonth.now())
                        finish()
                    },
                    onAdd = {
                        open(CalendarLaunchActivity.intentFor(this, EventEditor.insertIntent(date), editor = true))
                    },
                    onSearch = { open(Intent(this, SearchActivity::class.java)) },
                    onDismiss = ::finish,
                )
            }
        }
    }

    private fun open(intent: Intent) {
        startActivity(intent)
        finish()
    }

    companion object {
        private const val EXTRA_HIDDEN = "hidden_calendars"
        private const val EXTRA_MONTH = "month"
        private const val EXTRA_OFFSET = "month_offset"

        /** 行程清單左上角的日期。 */
        fun agendaIntent(context: Context, appWidgetId: Int?, hiddenCalendarIds: Set<Long>): Intent =
            base(context, appWidgetId, hiddenCalendarIds)
                .setData(Uri.parse("calendarwidget://date-menu/agenda/${appWidgetId ?: -1}"))

        /** 月曆的月份標題。 */
        fun monthIntent(context: Context, appWidgetId: Int?, monthOffset: Int, hiddenCalendarIds: Set<Long>): Intent =
            base(context, appWidgetId, hiddenCalendarIds)
                .putExtra(EXTRA_MONTH, true)
                .putExtra(EXTRA_OFFSET, monthOffset)
                .setData(Uri.parse("calendarwidget://date-menu/month/${appWidgetId ?: -1}/$monthOffset"))

        private fun base(context: Context, appWidgetId: Int?, hidden: Set<Long>): Intent =
            Intent(context, DateMenuActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId ?: AppWidgetManager.INVALID_APPWIDGET_ID)
                .putExtra(EXTRA_HIDDEN, hidden.toLongArray())
    }
}

@Composable
internal fun DateMenu(
    isMonth: Boolean,
    monthOffset: Int,
    canChangeMonth: Boolean,
    onViewer: (ViewMode) -> Unit,
    onCalendarApp: () -> Unit,
    onPickMonth: () -> Unit,
    onThisMonth: () -> Unit,
    onAdd: () -> Unit,
    onSearch: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    // 「＋」設定裡選的日曆 App：顯示它的名稱與圖示。
    val choice = EventEditor.choice(context)
    val appIcon = when (choice) {
        null -> rememberAppIcon(EventEditor.GOOGLE_CALENDAR)
        EventEditor.ASK -> null
        else -> rememberAppIcon(choice)
    }
    val appLabel = if (choice == null) stringResource(R.string.google_calendar) else EventEditor.choiceLabel(context)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
        text = {
            Column {
                if (isMonth) {
                    if (canChangeMonth) {
                        MenuRow(R.drawable.ic_event, stringResource(R.string.menu_pick_month), onClick = onPickMonth)
                        if (monthOffset != 0) {
                            MenuRow(R.drawable.ic_refresh, stringResource(R.string.back_to_this_month), onClick = onThisMonth)
                        }
                    }
                    MenuRow(R.drawable.ic_event, stringResource(R.string.menu_view_month)) { onViewer(ViewMode.MONTH) }
                } else {
                    MenuRow(R.drawable.ic_event, stringResource(R.string.menu_view_today)) { onViewer(ViewMode.DAY) }
                    MenuRow(R.drawable.ic_event, stringResource(R.string.menu_view_week)) { onViewer(ViewMode.WEEK) }
                }
                MenuRow(
                    icon = R.drawable.ic_event,
                    appIcon = appIcon,
                    label = stringResource(R.string.menu_open_app, appLabel),
                    onClick = onCalendarApp,
                )
                MenuRow(R.drawable.ic_add, stringResource(R.string.add_event), onClick = onAdd)
                MenuRow(R.drawable.ic_search, stringResource(R.string.search_title), onClick = onSearch)
            }
        },
    )
}

@Composable
private fun MenuRow(icon: Int, label: String, appIcon: ImageBitmap? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            if (appIcon != null) {
                Image(bitmap = appIcon, contentDescription = null, modifier = Modifier.size(24.dp))
            } else {
                Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
