package com.skestc.calendarwidget.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import com.skestc.calendarwidget.settings.WidgetSettings

/** 月曆 widget 的「上個月／下個月／回到本月」按鈕。 */
class ChangeMonthAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val delta = parameters[DeltaKey] ?: 0
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[WidgetSettings.KEY_MONTH_OFFSET] =
                if (delta == 0) 0 else (prefs[WidgetSettings.KEY_MONTH_OFFSET] ?: 0) + delta
            // 換月份時清掉選取的日期，改選今天或該月 1 號。
            prefs.remove(WidgetSettings.KEY_SELECTED_DAY)
        }
        MonthWidget().update(context, glanceId)
    }

    companion object {
        /** -1 上個月、+1 下個月、0 回到本月。 */
        val DeltaKey = ActionParameters.Key<Int>("delta")
    }
}
