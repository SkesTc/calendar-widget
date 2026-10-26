package com.skestc.calendarwidget.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

data class DayCell(
    val date: LocalDate,
    val inMonth: Boolean,
    /** 當天事件的顏色（不重複，最多 [MonthBuilder.MAX_DOTS] 個）。 */
    val eventColors: List<Int>,
)

data class MonthData(
    val month: YearMonth,
    val today: LocalDate,
    val firstDayOfWeek: DayOfWeek,
    val weeks: List<List<DayCell>>,
)

/** 把事件攤到月曆格子上：跨日事件會在每一天都打點。 */
class MonthBuilder(
    private val zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
) {
    val firstDayOfWeek: DayOfWeek = WeekFields.of(locale).firstDayOfWeek

    /** 月曆格子的第一天與最後一天（含），只涵蓋需要的週數（4～6 週）。 */
    fun gridRange(month: YearMonth): ClosedRange<LocalDate> {
        val start = month.atDay(1).with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
        val lastDayOfWeek = firstDayOfWeek.minus(1)
        val end = month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(lastDayOfWeek))
        return start..end
    }

    fun build(month: YearMonth, today: LocalDate, events: List<CalendarEvent>): MonthData {
        val range = gridRange(month)
        val colorsByDay = mutableMapOf<LocalDate, LinkedHashSet<Int>>()

        for (event in events) {
            val (first, last) = dateSpan(event)
            var day = maxOf(first, range.start)
            val stop = minOf(last, range.endInclusive)
            while (day <= stop) {
                val colors = colorsByDay.getOrPut(day) { LinkedHashSet() }
                if (colors.size < MAX_DOTS) colors += event.color
                day = day.plusDays(1)
            }
        }

        val days = generateSequence(range.start) { it.plusDays(1) }
            .takeWhile { it <= range.endInclusive }
            .map { DayCell(it, YearMonth.from(it) == month, colorsByDay[it]?.toList().orEmpty()) }
            .toList()

        return MonthData(month, today, firstDayOfWeek, days.chunked(7))
    }

    /** 事件佔用的日期（含頭尾）。全天事件以 UTC 午夜計，結束時間是「隔天 00:00」所以要減一天。 */
    fun dateSpan(event: CalendarEvent): Pair<LocalDate, LocalDate> {
        return if (event.allDay) {
            val first = Instant.ofEpochMilli(event.begin).atZone(ZoneOffset.UTC).toLocalDate()
            val last = Instant.ofEpochMilli(event.end).atZone(ZoneOffset.UTC).toLocalDate().minusDays(1)
            first to maxOf(first, last)
        } else {
            val first = Instant.ofEpochMilli(event.begin).atZone(zone).toLocalDate()
            // 結束在午夜 00:00 的事件不算佔用隔天。
            val last = Instant.ofEpochMilli(maxOf(event.begin, event.end - 1)).atZone(zone).toLocalDate()
            first to last
        }
    }

    companion object {
        const val MAX_DOTS = 3
    }
}
