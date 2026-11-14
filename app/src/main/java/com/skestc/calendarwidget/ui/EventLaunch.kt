package com.skestc.calendarwidget.ui

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import com.skestc.calendarwidget.data.CalendarEvent
import com.skestc.calendarwidget.widget.CalendarLaunchActivity

/**
 * 用「＋」設定裡選的日曆 App（預設 Google 日曆）打開這個行程，按 App 裡的編輯就能修改。
 * 用「檢視」而不是「編輯」：Google 日曆收到「編輯」時會開成新增行程的畫面。
 * 一律直接開日曆 App，不轉回 App 內的行程檢視（從行程檢視點進來時才不會繞回自己）。
 */
fun openEventEditor(context: Context, event: CalendarEvent) {
    val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId)
    val view = Intent(Intent.ACTION_VIEW, uri)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.begin)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end)
    context.startActivity(CalendarLaunchActivity.intentFor(context, target = view, chosenApp = true))
}
