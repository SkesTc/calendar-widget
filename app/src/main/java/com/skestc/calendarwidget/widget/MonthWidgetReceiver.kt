package com.skestc.calendarwidget.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class MonthWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonthWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdater.syncSchedules(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetUpdater.syncSchedules(context)
    }
}
