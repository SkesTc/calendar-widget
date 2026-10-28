package com.skestc.calendarwidget.data

import java.time.LocalDate
import java.time.ZoneId

/** 搜尋結果分成「即將到來」（今天起、時間由近到遠）與「過去」（由近到遠）。 */
data class SearchResults(val upcoming: List<CalendarEvent>, val past: List<CalendarEvent>) {
    val isEmpty: Boolean get() = upcoming.isEmpty() && past.isEmpty()
}

/** 搜尋結果的時間篩選。 */
enum class SearchRange { ALL, UPCOMING, THIS_WEEK, THIS_MONTH }

object EventSearch {
    /** 往前一年、往後兩年：涵蓋大部分會想找的行程，又不會讓查詢太慢。 */
    const val DAYS_BACK = 365L
    const val DAYS_AHEAD = 730L

    fun split(events: List<CalendarEvent>, now: Long, zone: ZoneId = ZoneId.systemDefault()): SearchResults {
        val spans = MonthBuilder(zone)
        val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        // 今天的行程（就算已經結束）算在「即將到來」，跟行程清單一樣。
        val (upcoming, past) = events.partition { spans.dateSpan(it).second >= today }
        return SearchResults(
            upcoming = upcoming.sortedWith(compareBy({ spans.dateSpan(it).first }, { !it.allDay }, { it.begin })),
            past = past.sortedWith(compareByDescending<CalendarEvent> { spans.dateSpan(it).first }.thenByDescending { it.begin }),
        )
    }

    /** [text] 裡所有 [keyword] 出現的位置（不分大小寫，不重疊），給標題加粗用。 */
    fun matches(text: String, keyword: String): List<IntRange> {
        val k = keyword.trim()
        if (k.isEmpty()) return emptyList()
        val ranges = mutableListOf<IntRange>()
        var from = 0
        while (true) {
            val i = text.indexOf(k, from, ignoreCase = true)
            if (i < 0) break
            ranges += i until i + k.length
            from = i + k.length
        }
        return ranges
    }

    /**
     * 依時間範圍與日曆篩選（[calendarId] 為 null 表示所有日曆）。
     * 本週是週一到週日；行程只要有一天落在範圍內就算。
     */
    fun filter(
        events: List<CalendarEvent>,
        range: SearchRange,
        calendarId: Long?,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<CalendarEvent> {
        val spans = MonthBuilder(zone)
        val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val window: ClosedRange<LocalDate>? = when (range) {
            SearchRange.ALL -> null
            SearchRange.UPCOMING -> today..LocalDate.MAX
            SearchRange.THIS_WEEK -> today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                .let { it..it.plusDays(6) }
            SearchRange.THIS_MONTH -> today.withDayOfMonth(1)..today.withDayOfMonth(today.lengthOfMonth())
        }
        return events.filter { event ->
            (calendarId == null || event.calendarId == calendarId) &&
                (window == null || spans.dateSpan(event).let { (first, last) -> last >= window.start && first <= window.endInclusive })
        }
    }

    /** 搜尋範圍的起訖（毫秒）。 */
    fun range(now: Long, zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> {
        val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        fun ms(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()
        return ms(today.minusDays(DAYS_BACK)) to ms(today.plusDays(DAYS_AHEAD))
    }
}
