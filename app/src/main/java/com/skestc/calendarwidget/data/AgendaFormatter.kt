package com.skestc.calendarwidget.data

import android.content.Context
import android.text.format.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

/** 清單 widget 的一列：日期標題或事件。 */
sealed interface AgendaRow {
    val id: Long

    data class Header(val date: LocalDate, val label: String) : AgendaRow {
        override val id: Long = -date.toEpochDay() - 1
    }

    data class Event(val event: CalendarEvent, val timeLabel: String) : AgendaRow {
        override val id: Long = event.eventId * 31 + event.begin
    }
}

class AgendaFormatter(
    private val context: Context,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val locale: Locale = Locale.getDefault(),
) {
    private val timeFormat = DateFormat.getTimeFormat(context)
    private val headerFormat = DateTimeFormatter.ofPattern(
        DateFormat.getBestDateTimePattern(locale, "MMMdEEEE"), locale,
    )

    /** 事件要顯示在哪一天：全天事件用 UTC 換算；已經開始的跨日事件放在今天。 */
    fun displayDate(event: CalendarEvent, today: LocalDate): LocalDate {
        val start = Instant.ofEpochMilli(event.begin)
            .atZone(if (event.allDay) ZoneOffset.UTC else zone)
            .toLocalDate()
        return maxOf(start, today)
    }

    fun rows(events: List<CalendarEvent>, today: LocalDate = LocalDate.now(zone)): List<AgendaRow> =
        events
            .groupBy { displayDate(it, today) }
            .toSortedMap()
            .flatMap { (date, dayEvents) ->
                listOf<AgendaRow>(AgendaRow.Header(date, dayLabel(date, today))) +
                    dayEvents
                        .sortedWith(compareBy({ !it.allDay }, { it.begin }))
                        .map { AgendaRow.Event(it, timeLabel(it)) }
            }

    fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "今天 · ${headerFormat.format(date)}"
        today.plusDays(1) -> "明天 · ${headerFormat.format(date)}"
        else -> headerFormat.format(date)
    }

    fun timeLabel(event: CalendarEvent): String =
        if (event.allDay) {
            "全天"
        } else {
            "${timeFormat.format(Date(event.begin))} – ${timeFormat.format(Date(event.end))}"
        }
}
