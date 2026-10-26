package com.skestc.calendarwidget.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class AgendaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AgendaWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        CalendarChangeWorker.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        CalendarChangeWorker.cancel(context)
    }
}
