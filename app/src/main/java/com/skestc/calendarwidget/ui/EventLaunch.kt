package com.skestc.calendarwidget.ui

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import com.skestc.calendarwidget.data.CalendarEvent
import com.skestc.calendarwidget.settings.EventEditor
import com.skestc.calendarwidget.widget.CalendarLaunchActivity

/** 直接打開行程的編輯畫面（優先用 Google 日曆）；日曆 App 不支援「編輯」時改成開啟檢視畫面。 */
fun openEventEditor(context: Context, event: CalendarEvent) {
    val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId)
    fun withTimes(action: String) = Intent(action, uri)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.begin)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end)
    context.startActivity(
        CalendarLaunchActivity.intentFor(
            context,
            target = withTimes(Intent.ACTION_EDIT),
            preferredPackage = EventEditor.GOOGLE_CALENDAR,
            fallback = withTimes(Intent.ACTION_VIEW),
        ),
    )
}
