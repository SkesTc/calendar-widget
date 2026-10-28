package com.skestc.calendarwidget.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import com.skestc.calendarwidget.settings.WidgetSettings

/** 所有 widget 共用的更新與背景排程。 */
object WidgetUpdater {

    internal val widgets: List<Pair<GlanceAppWidget, Class<*>>>
        get() = listOf(
            AgendaWidget() to AgendaWidgetReceiver::class.java,
            MonthWidget() to MonthWidgetReceiver::class.java,
            TimelineWidget() to TimelineWidgetReceiver::class.java,
            NextEventWidget() to NextEventWidgetReceiver::class.java,
            WeekWidget() to WeekWidgetReceiver::class.java,
        )

    /**
     * 讓每個 widget 重新讀取日曆資料。
     *
     * 只呼叫 update() 的話，正在執行中的 session 只會重組、不會重新查詢，
     * 所以先把每個 widget 狀態裡的 refresh 計數器加一，讓資料載入的 key 改變。
     */
    suspend fun refreshAll(context: Context) {
        val manager = GlanceAppWidgetManager(context)
        for ((widget, _) in widgets) {
            for (id in manager.getGlanceIds(widget.javaClass)) {
                updateAppWidgetState(context, id) { prefs ->
                    prefs[WidgetSettings.KEY_REFRESH] = (prefs[WidgetSettings.KEY_REFRESH] ?: 0L) + 1
                }
                widget.update(context, id)
            }
        }
    }

    fun hasAnyWidget(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        return widgets.any { (_, receiver) ->
            manager.getAppWidgetIds(ComponentName(context, receiver)).isNotEmpty()
        }
    }

    /** 有任何 widget 就排好背景更新；全部移除後就取消。 */
    fun syncSchedules(context: Context) {
        if (hasAnyWidget(context)) {
            CalendarChangeWorker.schedule(context)
            DayChangeScheduler.schedule(context)
        } else {
            CalendarChangeWorker.cancel(context)
            DayChangeScheduler.cancel(context)
        }
    }
}
