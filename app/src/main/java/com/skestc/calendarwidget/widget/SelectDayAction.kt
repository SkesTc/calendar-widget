package com.skestc.calendarwidget.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import com.skestc.calendarwidget.settings.WidgetSettings

/** 月曆 widget 上點選某一天：記住選取的日期，下方改列那一天的行程。 */
class SelectDayAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val epochDay = parameters[EpochDayKey] ?: return
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[WidgetSettings.KEY_SELECTED_DAY] = epochDay
        }
        MonthWidget().update(context, glanceId)
    }

    companion object {
        val EpochDayKey = ActionParameters.Key<Long>("epochDay")
    }
}
