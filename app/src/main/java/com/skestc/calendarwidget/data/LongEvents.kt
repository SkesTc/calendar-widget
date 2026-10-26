package com.skestc.calendarwidget.data

import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 一個會佔滿 widget 的行程：跨很多天，或在查詢範圍內重複出現很多次。
 * 重複事件的多個實例合併成一筆；[first]～[last] 是最早一次開始到最後一次結束。
 */
data class LongEvent(
    val eventId: Long,
    val title: String,
    val color: Int,
    val first: LocalDate,
    val last: LocalDate,
    /** 單一次最長跨幾天。 */
    val longestDays: Int,
    /** 在查詢範圍內出現幾次（不重複的行程為 1）。 */
    val occurrences: Int,
) {
    val days: Int get() = (ChronoUnit.DAYS.between(first, last) + 1).toInt()
    val isRecurring: Boolean get() = occurrences > 1
}

/**
 * 找出會佔滿 widget 的長期行程，讓使用者個別隱藏：
 * - 跨 [MIN_DAYS] 天以上（例如學期、整個月的活動）
 * - 或在範圍內重複 [MIN_OCCURRENCES] 次以上（例如每天的早自習、每週例會）
 * 節慶假日不算（那是另外的日曆，用日曆勾選就能關掉）。
 */
class LongEvents(zone: ZoneId = ZoneId.systemDefault()) {

    private val spans = MonthBuilder(zone)

    fun find(
        events: List<CalendarEvent>,
        minDays: Int = MIN_DAYS,
        minOccurrences: Int = MIN_OCCURRENCES,
    ): List<LongEvent> =
        events
            .filter { it.holiday == null }
            // 重複事件的每個實例 eventId 相同，合併成一筆。
            .groupBy { it.eventId }
            .map { (eventId, instances) ->
                val spans = instances.map { spans.dateSpan(it) }
                val sample = instances.minBy { it.begin }
                LongEvent(
                    eventId = eventId,
                    title = sample.title,
                    color = sample.color,
                    first = spans.minOf { it.first },
                    last = spans.maxOf { it.second },
                    longestDays = spans.maxOf { (first, last) -> ChronoUnit.DAYS.between(first, last).toInt() + 1 },
                    occurrences = instances.size,
                )
            }
            .filter { it.longestDays >= minDays || it.occurrences >= minOccurrences }
            .sortedWith(compareBy({ it.first }, { it.title }))

    companion object {
        const val MIN_DAYS = 3
        const val MIN_OCCURRENCES = 3
    }
}
