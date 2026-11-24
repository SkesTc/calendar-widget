package com.skestc.calendarwidget.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.skestc.calendarwidget.settings.WidgetSettings

/** 月曆 widget 的「上個月／下個月／回到本月」按鈕。 */
class ChangeMonthAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val startedAt = android.os.SystemClock.elapsedRealtime()
        val delta = parameters[DeltaKey] ?: 0
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[WidgetSettings.KEY_MONTH_OFFSET] =
                if (delta == 0) 0 else (prefs[WidgetSettings.KEY_MONTH_OFFSET] ?: 0) + delta
            // 換月份時清掉選取的日期，改選今天或該月 1 號。
            prefs.remove(WidgetSettings.KEY_SELECTED_DAY)
        }
        renderMonthChange(context, glanceId, startedAt)
    }

    companion object {
        /** -1 上個月、+1 下個月、0 回到本月。 */
        val DeltaKey = ActionParameters.Key<Int>("delta")
    }
}

/**
 * 切換月份之後重畫。這個月還沒讀過時分兩步：先畫只有日期的月曆（不查日曆，幾乎馬上出現），
 * 讀完行程再畫完整的一次；已經讀過（例如來回切換）就直接畫完整的。
 */
internal suspend fun renderMonthChange(context: Context, glanceId: GlanceId, startedAt: Long) {
    val settings = WidgetSettings.from(getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId))
    val widget = MonthWidget()
    val cached = MonthCache.peek(settings.monthKey()) != null
    if (!cached) {
        MonthCache.requestQuickFrame(glanceId)
        // 反應時間記這一次：使用者看到月份換掉的時間。
        InstantRender.render(context, widget, glanceId, startedAt)
    }
    InstantRender.render(context, widget, glanceId, startedAt, recordTiming = cached)
}
