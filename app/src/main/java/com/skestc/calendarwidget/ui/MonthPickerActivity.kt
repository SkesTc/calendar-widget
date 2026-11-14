package com.skestc.calendarwidget.ui

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import com.skestc.calendarwidget.R
import com.skestc.calendarwidget.settings.WidgetSettings
import com.skestc.calendarwidget.ui.theme.CalendarWidgetTheme
import com.skestc.calendarwidget.widget.InstantRender
import com.skestc.calendarwidget.widget.MonthWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** 點月曆標題開啟：選年份與月份直接跳過去，或「回到本月」。透明 Activity，只顯示一個對話框。 */
class MonthPickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val shown = YearMonth.now().plusMonths(intent.getIntExtra(EXTRA_OFFSET, 0).toLong())
        setContent {
            CalendarWidgetTheme {
                MonthPickerDialog(
                    shown = shown,
                    thisMonth = YearMonth.now(),
                    onPick = { picked ->
                        jumpTo(applicationContext, appWidgetId, picked)
                        finish()
                    },
                    onDismiss = ::finish,
                )
            }
        }
    }

    companion object {
        private const val EXTRA_OFFSET = "month_offset"

        fun intentFor(context: Context, appWidgetId: Int, monthOffset: Int): Intent =
            Intent(context, MonthPickerActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                .putExtra(EXTRA_OFFSET, monthOffset)
                // 每個 widget、每個顯示中的月份各一個 PendingIntent。
                .setData(Uri.parse("calendarwidget://month-picker/$appWidgetId/$monthOffset"))

        /** 把月曆 widget 切到 [month]：存成相對本月的位移，清掉選取的日期，然後馬上重畫。 */
        private fun jumpTo(context: Context, appWidgetId: Int, month: YearMonth) {
            if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
            val offset = ChronoUnit.MONTHS.between(YearMonth.now(), month).toInt()
            // 在背景完成（不跟著這個對話框一起被取消）。
            CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                val glanceId = runCatching { GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId) }.getOrNull()
                    ?: return@launch
                updateAppWidgetState(context, glanceId) { prefs ->
                    prefs[WidgetSettings.KEY_MONTH_OFFSET] = offset
                    prefs.remove(WidgetSettings.KEY_SELECTED_DAY)
                }
                InstantRender.render(context, MonthWidget(), glanceId)
            }
        }
    }
}

/** 年份左右切換、12 個月份按鈕；顯示中的月份實心、本月外框。 */
@Composable
internal fun MonthPickerDialog(
    shown: YearMonth,
    thisMonth: YearMonth,
    onPick: (YearMonth) -> Unit,
    onDismiss: () -> Unit,
) {
    var year by remember { mutableIntStateOf(shown.year) }
    val locale = Locale.getDefault()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { year-- }) {
                    Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = stringResource(R.string.previous_year))
                }
                Text(
                    stringResource(R.string.year_title, year),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { year++ }) {
                    Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = stringResource(R.string.next_year))
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (row in Month.entries.chunked(3)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        for (month in row) {
                            val value = YearMonth.of(year, month)
                            val label = month.getDisplayName(TextStyle.SHORT, locale)
                            val modifier = Modifier.weight(1f).height(44.dp)
                            when {
                                value == shown -> Button(onClick = { onPick(value) }, modifier = modifier) {
                                    Text(label, maxLines = 1)
                                }
                                value == thisMonth -> OutlinedButton(onClick = { onPick(value) }, modifier = modifier) {
                                    Text(label, maxLines = 1, color = MaterialTheme.colorScheme.primary)
                                }
                                else -> TextButton(
                                    onClick = { onPick(value) },
                                    modifier = modifier,
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                                ) { Text(label, maxLines = 1) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(thisMonth) }) { Text(stringResource(R.string.back_to_this_month)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}
