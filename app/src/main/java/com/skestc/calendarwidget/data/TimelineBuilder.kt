package com.skestc.calendarwidget.data

import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

enum class EventStatus { PAST, NOW, FUTURE }

sealed interface TimelineItem {
    data class Event(val event: CalendarEvent, val status: EventStatus) : TimelineItem

    /** 「現在」的位置：放在已結束和未開始的行程之間（有進行中的行程時不顯示）。 */
    data object NowMarker : TimelineItem

    /** 兩個行程之間的空檔（只標示現在之後、至少 [TimelineBuilder.MIN_GAP_MINUTES] 分鐘的）。 */
    data class Gap(val minutes: Long) : TimelineItem
}

data class Timeline(
    val allDay: List<CalendarEvent>,
    val items: List<TimelineItem>,
) {
    val isEmpty: Boolean get() = allDay.isEmpty() && items.isEmpty()
}

/** 時間軸 widget 的資料：今天的全天事件 + 依時間排列的行程、現在位置、空檔。 */
class TimelineBuilder(private val zone: ZoneId = ZoneId.systemDefault()) {

    private val spans = MonthBuilder(zone)

    fun build(events: List<CalendarEvent>, today: LocalDate, now: Long): Timeline {
        val todays = events.filter { event ->
            val (first, last) = spans.dateSpan(event)
            today in first..last
        }
        val (allDay, timed) = todays.partition { it.allDay || it.holiday != null }
        val sorted = timed.sortedBy { it.begin }
        val hasCurrent = sorted.any { now >= it.begin && now < it.end }

        val items = mutableListOf<TimelineItem>()
        var cursor: Long? = null
        var markerPlaced = false
        for (event in sorted) {
            if (!markerPlaced && !hasCurrent && event.begin > now) {
                items += TimelineItem.NowMarker
                markerPlaced = true
                cursor = maxOf(cursor ?: now, now)
            }
            val start = cursor
            if (event.begin > now && start != null) {
                val gap = TimeUnit.MILLISECONDS.toMinutes(event.begin - maxOf(start, now))
                if (gap >= MIN_GAP_MINUTES) items += TimelineItem.Gap(gap)
            }
            val status = when {
                event.end <= now -> EventStatus.PAST
                event.begin <= now -> EventStatus.NOW
                else -> EventStatus.FUTURE
            }
            items += TimelineItem.Event(event, status)
            cursor = maxOf(cursor ?: event.end, event.end)
        }
        if (!markerPlaced && !hasCurrent && sorted.isNotEmpty()) items += TimelineItem.NowMarker

        return Timeline(allDay.sortedBy { it.holiday == null }, items)
    }

    /** 下一個會讓時間軸改變的時刻（某個行程開始或結束），用來排程更新。 */
    fun nextBoundary(timeline: Timeline, now: Long): Long? =
        timeline.items
            .filterIsInstance<TimelineItem.Event>()
            .flatMap { listOf(it.event.begin, it.event.end) }
            .filter { it > now }
            .minOrNull()

    companion object {
        const val MIN_GAP_MINUTES = 60L
    }
}

/** 「2 小時 30 分」「45 分」。 */
fun formatDuration(minutes: Long): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0L -> "$m 分"
        m == 0L -> "$h 小時"
        else -> "$h 小時 $m 分"
    }
}
