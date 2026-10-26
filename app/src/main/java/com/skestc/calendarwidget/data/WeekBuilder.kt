package com.skestc.calendarwidget.data

import com.skestc.calendarwidget.lunar.DayLabeler
import java.time.LocalDate
import java.time.ZoneId

data class WeekDay(
    val date: LocalDate,
    /** 當天的行程（全天事件在前），最多 [WeekBuilder.build] 的 maxPerDay 筆。 */
    val events: List<CalendarEvent>,
    /** 放不下的行程數，顯示成「+2」。 */
    val more: Int,
    val holiday: HolidayKind?,
    /** 假日名稱 > 傳統節日 > 節氣 > 農曆。 */
    val label: String?,
)

/** 一週 widget：從 [start] 起連續 [days] 天，每天列出行程。 */
class WeekBuilder(zone: ZoneId = ZoneId.systemDefault()) {

    private val spans = MonthBuilder(zone)

    fun build(
        events: List<CalendarEvent>,
        start: LocalDate,
        days: Int = 7,
        maxPerDay: Int = 3,
        labeler: DayLabeler? = null,
    ): List<WeekDay> = (0 until days).map { offset ->
        val date = start.plusDays(offset.toLong())
        val todays = events.filter { event ->
            val (first, last) = spans.dateSpan(event)
            date in first..last
        }
        val (holidays, regular) = todays.partition { it.holiday != null }
        val sorted = regular.sortedWith(compareBy({ !it.allDay }, { it.begin }))
        val holiday = holidays.minByOrNull { it.holiday!!.ordinal }
        WeekDay(
            date = date,
            events = sorted.take(maxPerDay),
            more = (sorted.size - maxPerDay).coerceAtLeast(0),
            holiday = holiday?.holiday,
            label = holiday?.title ?: labeler?.labelOf(date)?.short,
        )
    }
}

/** 「下一個行程」widget 要顯示的事件。 */
data class NextEvent(val event: CalendarEvent, val inProgress: Boolean)

object NextEventPicker {

    /**
     * 優先挑有時間的行程（進行中或最早開始的）；沒有的話才挑全天事件。節慶假日不算。
     */
    fun pick(events: List<CalendarEvent>, now: Long): NextEvent? {
        val candidates = events.filter { it.holiday == null && it.end > now }
        val timed = candidates.filter { !it.allDay }.minByOrNull { it.begin }
        val chosen = timed ?: candidates.filter { it.allDay }.minByOrNull { it.begin } ?: return null
        return NextEvent(chosen, inProgress = chosen.begin <= now)
    }

    /** 下一次畫面需要改變的時刻：行程開始（變成進行中）或結束（換下一個）。 */
    fun nextBoundary(next: NextEvent?, now: Long): Long? = next?.let {
        if (it.inProgress) it.event.end else it.event.begin
    }?.takeIf { it > now }
}
