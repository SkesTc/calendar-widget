package com.skestc.calendarwidget.data

import com.skestc.calendarwidget.lunar.DayLabeler
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
    /** 當天事件的顏色（不重複，最多 [MonthBuilder.MAX_DOTS] 個），不含節慶假日。 */
    val eventColors: List<Int>,
    /** 節慶假日日曆標示的類型；同一天有多個時，放假優先。 */
    val holiday: HolidayKind? = null,
    /** 格子裡的小字：假日名稱 > 傳統節日 > 節氣 > 農曆。 */
    val label: String? = null,
    /** 小字是假日、傳統節日或節氣（不是一般農曆日期），顯示時用強調色。 */
    val labelHighlighted: Boolean = false,
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

    /** [labeler] 為 null 時不顯示農曆／節氣／傳統節日（節慶假日日曆的名稱仍會顯示）。 */
    fun build(
        month: YearMonth,
        today: LocalDate,
        events: List<CalendarEvent>,
        labeler: DayLabeler? = null,
    ): MonthData {
        val range = gridRange(month)
        val colorsByDay = mutableMapOf<LocalDate, LinkedHashSet<Int>>()
        val holidaysByDay = mutableMapOf<LocalDate, MutableList<CalendarEvent>>()

        for (event in events) {
            val (first, last) = dateSpan(event)
            var day = maxOf(first, range.start)
            val stop = minOf(last, range.endInclusive)
            while (day <= stop) {
                if (event.holiday != null) {
                    holidaysByDay.getOrPut(day) { mutableListOf() } += event
                } else {
                    val colors = colorsByDay.getOrPut(day) { LinkedHashSet() }
                    if (colors.size < MAX_DOTS) colors += event.color
                }
                day = day.plusDays(1)
            }
        }

        val days = generateSequence(range.start) { it.plusDays(1) }
            .takeWhile { it <= range.endInclusive }
            .map { date ->
                // 放假 > 補班 > 紀念日。
                val holiday = holidaysByDay[date]?.minByOrNull { it.holiday!!.ordinal }
                val dayLabel = labeler?.labelOf(date)
                DayCell(
                    date = date,
                    inMonth = YearMonth.from(date) == month,
                    eventColors = colorsByDay[date]?.toList().orEmpty(),
                    holiday = holiday?.holiday,
                    label = holiday?.title ?: dayLabel?.short,
                    labelHighlighted = holiday != null || dayLabel?.festival != null || dayLabel?.solarTerm != null,
                )
            }
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

    /** [date] 當天的行程（不含節慶假日），全天事件在前，依開始時間排序。 */
    fun eventsOn(events: List<CalendarEvent>, date: LocalDate): List<CalendarEvent> =
        events
            .filter { it.holiday == null }
            .filter { event -> dateSpan(event).let { (first, last) -> date in first..last } }
            .sortedWith(compareBy({ !it.allDay }, { it.begin }))

    /**
     * 月曆上被選取的日期：使用者點過、而且在這個月的格子裡就用它；
     * 否則今天在這個月就選今天，再否則選這個月 1 號。
     */
    fun selectedDate(month: YearMonth, today: LocalDate, picked: LocalDate?): LocalDate {
        val range = gridRange(month)
        return when {
            picked != null && picked in range -> picked
            YearMonth.from(today) == month -> today
            else -> month.atDay(1)
        }
    }

    companion object {
        const val MAX_DOTS = 3
    }
}
