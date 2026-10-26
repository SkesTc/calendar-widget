package com.skestc.calendarwidget.data

import android.content.Context
import android.text.format.DateFormat
import com.skestc.calendarwidget.lunar.DayLabeler
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

    /**
     * [note] 是農曆、節氣與節慶假日名稱（例如「九月十七 · 光復節」）；[dayOff] 為 true 時標題用假日色。
     */
    data class Header(
        val date: LocalDate,
        val label: String,
        val note: String? = null,
        val dayOff: Boolean = false,
    ) : AgendaRow {
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

    /**
     * 依日期分組。節慶假日不另外列一張卡片，而是併到當天標題的 note，
     * 所以只有假日的日子也會出現（只有標題）。
     */
    fun rows(
        events: List<CalendarEvent>,
        today: LocalDate = LocalDate.now(zone),
        labeler: DayLabeler? = null,
    ): List<AgendaRow> =
        events
            .groupBy { displayDate(it, today) }
            .toSortedMap()
            .flatMap { (date, dayEvents) ->
                val (holidays, regular) = dayEvents.partition { it.holiday != null }
                val note = listOfNotNull(labeler?.labelOf(date)?.long)
                    .plus(holidays.map { it.title })
                    .joinToString(" · ")
                    .ifEmpty { null }
                val header = AgendaRow.Header(
                    date = date,
                    label = dayLabel(date, today),
                    note = note,
                    dayOff = holidays.any { it.holiday == HolidayKind.DAY_OFF },
                )
                listOf<AgendaRow>(header) +
                    regular
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
