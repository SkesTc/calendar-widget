package com.skestc.calendarwidget.data

import android.content.ContentUris
import android.content.Intent
import android.provider.CalendarContract
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** App 內行程檢視的範圍：當天、當週、當月、近四週。 */
enum class ViewMode { DAY, WEEK, MONTH, FOUR_WEEKS }

/** 檢視畫面的一天：節慶假日（顯示在日期旁）與一般行程。 */
data class ViewDay(val date: LocalDate, val holidays: List<CalendarEvent>, val events: List<CalendarEvent>)

/** 從 widget 點進來時要顯示的日期，以及要標示的行程（點的是行程時）。 */
data class ViewTarget(val date: LocalDate, val eventId: Long?)

object EventView {
    const val FOUR_WEEKS_DAYS = 28L

    fun range(mode: ViewMode, anchor: LocalDate, firstDayOfWeek: DayOfWeek): ClosedRange<LocalDate> = when (mode) {
        ViewMode.DAY -> anchor..anchor
        ViewMode.WEEK -> anchor.with(TemporalAdjusters.previousOrSame(firstDayOfWeek)).let { it..it.plusDays(6) }
        ViewMode.MONTH -> anchor.withDayOfMonth(1)..anchor.with(TemporalAdjusters.lastDayOfMonth())
        ViewMode.FOUR_WEEKS -> anchor..anchor.plusDays(FOUR_WEEKS_DAYS - 1)
    }

    /** 上一段／下一段。 */
    fun shift(mode: ViewMode, anchor: LocalDate, delta: Long): LocalDate = when (mode) {
        ViewMode.DAY -> anchor.plusDays(delta)
        ViewMode.WEEK -> anchor.plusWeeks(delta)
        ViewMode.MONTH -> anchor.plusMonths(delta)
        ViewMode.FOUR_WEEKS -> anchor.plusWeeks(4 * delta)
    }

    /**
     * 範圍內每天的假日與行程。當天、當週列出每一天（沒行程也列）；
     * 當月、近四週只列有行程或假日的日子。
     */
    fun days(
        events: List<CalendarEvent>,
        range: ClosedRange<LocalDate>,
        mode: ViewMode,
        builder: MonthBuilder = MonthBuilder(),
    ): List<ViewDay> {
        val holidays = events.filter { it.holiday != null }
        return generateSequence(range.start) { it.plusDays(1) }
            .takeWhile { it <= range.endInclusive }
            .map { date ->
                ViewDay(
                    date = date,
                    holidays = holidays.filter { builder.dateSpan(it).let { (first, last) -> date in first..last } },
                    events = builder.eventsOn(events, date),
                )
            }
            .filter { mode == ViewMode.DAY || mode == ViewMode.WEEK || it.events.isNotEmpty() || it.holidays.isNotEmpty() }
            .toList()
    }

    /**
     * widget 原本要交給日曆 App 的 Intent → 要顯示的日期：
     * 「開啟某一天」是 content://com.android.calendar/time/毫秒，「開啟行程」帶著開始時間與行程 id。
     */
    fun targetOf(intent: Intent, zone: ZoneId = ZoneId.systemDefault()): ViewTarget? {
        val data = intent.data
        val isEvent = data != null && data.toString().startsWith(CalendarContract.Events.CONTENT_URI.toString())
        val millis = when {
            intent.hasExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME) ->
                intent.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, 0)
            data != null && data.pathSegments.firstOrNull() == "time" -> data.lastPathSegment?.toLongOrNull()
            else -> null
        } ?: return null
        val eventId = if (isEvent) runCatching { ContentUris.parseId(data!!) }.getOrNull()?.takeIf { it >= 0 } else null
        return ViewTarget(Instant.ofEpochMilli(millis).atZone(zone).toLocalDate(), eventId)
    }
}
