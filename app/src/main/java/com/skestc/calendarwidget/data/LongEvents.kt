package com.skestc.calendarwidget.data

import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** 一個跨很多天的行程（重複事件的多個實例合併成一筆）。 */
data class LongEvent(
    val eventId: Long,
    val title: String,
    val color: Int,
    val first: LocalDate,
    val last: LocalDate,
) {
    val days: Int get() = (ChronoUnit.DAYS.between(first, last) + 1).toInt()
}

/**
 * 找出會佔滿 widget 的長期行程（例如學期、整個月的活動），讓使用者個別隱藏。
 * 節慶假日不算（那是另外的日曆，用日曆勾選就能關掉）。
 */
class LongEvents(zone: ZoneId = ZoneId.systemDefault()) {

    private val spans = MonthBuilder(zone)

    fun find(events: List<CalendarEvent>, minDays: Int = MIN_DAYS): List<LongEvent> =
        events
            .asSequence()
            .filter { it.holiday == null }
            .map { event ->
                val (first, last) = spans.dateSpan(event)
                LongEvent(event.eventId, event.title, event.color, first, last)
            }
            .filter { it.days >= minDays }
            // 重複事件的每個實例 eventId 相同，只留最早的那一次。
            .groupBy { it.eventId }
            .map { (_, instances) -> instances.minBy { it.first } }
            .sortedWith(compareBy({ it.first }, { it.title }))
            .toList()

    companion object {
        const val MIN_DAYS = 3
    }
}
