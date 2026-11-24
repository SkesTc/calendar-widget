package com.skestc.calendarwidget.widget

import androidx.compose.runtime.Composable
import androidx.glance.LocalContext
import androidx.glance.action.Action
import com.skestc.calendarwidget.data.ViewMode
import com.skestc.calendarwidget.settings.TitleTap
import com.skestc.calendarwidget.theme.LocalWidgetStyle
import com.skestc.calendarwidget.ui.DateMenuActivity
import com.skestc.calendarwidget.ui.EventViewerActivity
import com.skestc.calendarwidget.ui.MonthPickerActivity
import java.time.LocalDate
import java.time.YearMonth
import androidx.glance.appwidget.action.actionStartActivity as actionStartIntent

/**
 * 點行程清單左上角的日期（[isMonth] = false）或月曆的月份標題（true）時的動作，
 * 依這個 widget 設定的 [TitleTap]：選單、App 內的行程檢視、選擇月份，或指定的日曆 App。
 */
@Composable
internal fun titleTapAction(isMonth: Boolean, monthOffset: Int = 0): Action {
    val context = LocalContext.current
    val style = LocalWidgetStyle.current
    val hidden = style.hiddenCalendarIds
    val appWidgetId = appWidgetIdOrNull()
    // 月曆看的是別的月份時，以那個月的 1 號為準。
    val date = if (isMonth && monthOffset != 0) YearMonth.now().plusMonths(monthOffset.toLong()).atDay(1) else LocalDate.now()
    val viewer = EventViewerActivity.intent(context, date, hidden, mode = if (isMonth) ViewMode.MONTH else ViewMode.DAY)
    val intent = when (val tap = style.titleTap) {
        TitleTap.Menu -> if (isMonth) {
            DateMenuActivity.monthIntent(context, appWidgetId, monthOffset, hidden)
        } else {
            DateMenuActivity.agendaIntent(context, appWidgetId, hidden)
        }
        TitleTap.Viewer -> viewer
        TitleTap.MonthPicker -> if (isMonth && appWidgetId != null) {
            MonthPickerActivity.intentFor(context, appWidgetId, monthOffset)
        } else {
            viewer
        }
        // 指定的日曆 App 開到那一天；沒裝（或被移除）時交給系統預設的日曆 App。
        is TitleTap.App -> CalendarLaunchActivity.intentFor(context, openDayIntent(date), preferredPackage = tap.packageName)
    }
    return actionStartIntent(intent)
}
