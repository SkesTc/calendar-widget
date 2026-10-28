package com.skestc.calendarwidget.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class TimelineBuilderTest {

    private val zone = ZoneId.of("Asia/Taipei")
    private val today = LocalDate.of(2026, 10, 26)
    private val builder = TimelineBuilder(zone)

    private fun at(h: Int, m: Int = 0, date: LocalDate = today) =
        LocalDateTime.of(date, java.time.LocalTime.of(h, m)).atZone(zone).toInstant().toEpochMilli()

    private fun timed(id: Long, from: Long, to: Long) =
        CalendarEvent(id, "e$id", from, to, false, 1, null, "cal")

    private fun allDay(id: Long, date: LocalDate, holiday: HolidayKind? = null) = CalendarEvent(
        id, "a$id",
        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        true, 1, null, "cal", holiday = holiday,
    )

    private fun Timeline.shape() = items.map {
        when (it) {
            is TimelineItem.Event -> "${it.event.title}:${it.status}"
            TimelineItem.NowMarker -> "NOW"
            is TimelineItem.Gap -> "gap${it.minutes}"
        }
    }

    @Test
    fun previewsTomorrowOnlyWhenTodayIsDone() {
        val tomorrow = today.plusDays(1)
        val events = listOf(
            timed(1, at(9), at(10)),
            timed(2, at(14, date = tomorrow), at(15, date = tomorrow)),
            allDay(3, tomorrow),
            allDay(4, tomorrow, HolidayKind.OBSERVANCE),
        )
        val evening = at(20)
        val done = builder.build(events, today, evening)
        assertEquals(listOf("a3", "e2"), builder.tomorrowPreview(events, today, done).map { it.title })
        // 今天還有行程時不預告。
        val morning = at(8)
        assertTrue(builder.tomorrowPreview(events, today, builder.build(events, today, morning)).isEmpty())
    }

    @Test
    fun marksPastCurrentAndFutureEvents() {
        val events = listOf(
            timed(1, at(9), at(10)),
            timed(2, at(11), at(12)),
            timed(3, at(12), at(13)),
        )
        val timeline = builder.build(events, today, now = at(11, 30))
        // 有進行中的行程時不放「現在」標記；12 點緊接著開始，不算空檔。
        assertEquals(listOf("e1:PAST", "e2:NOW", "e3:FUTURE"), timeline.shape())
    }

    @Test
    fun placesNowMarkerAndGapBetweenEvents() {
        val events = listOf(timed(1, at(9), at(10)), timed(2, at(15), at(16)))
        val timeline = builder.build(events, today, now = at(12))
        // 空檔從「現在」12:00 算到 15:00。
        assertEquals(listOf("e1:PAST", "NOW", "gap180", "e2:FUTURE"), timeline.shape())
    }

    @Test
    fun shortGapsAreNotShown() {
        val events = listOf(timed(1, at(13), at(14)), timed(2, at(14, 30), at(15)))
        val timeline = builder.build(events, today, now = at(12, 30))
        assertEquals(listOf("NOW", "e1:FUTURE", "e2:FUTURE"), timeline.shape())
    }

    @Test
    fun overlappingEventsDoNotCreateNegativeGaps() {
        val events = listOf(timed(1, at(13), at(17)), timed(2, at(14), at(15)), timed(3, at(19), at(20)))
        val timeline = builder.build(events, today, now = at(8))
        assertEquals(listOf("NOW", "gap300", "e1:FUTURE", "e2:FUTURE", "gap120", "e3:FUTURE"), timeline.shape())
    }

    @Test
    fun nowMarkerGoesLastWhenEverythingIsOver() {
        val timeline = builder.build(listOf(timed(1, at(9), at(10))), today, now = at(22))
        assertEquals(listOf("e1:PAST", "NOW"), timeline.shape())
    }

    @Test
    fun separatesAllDayEventsAndHolidaysAndIgnoresOtherDays() {
        val events = listOf(
            allDay(1, today),
            allDay(2, today, HolidayKind.OBSERVANCE),
            allDay(3, today.plusDays(1)),
            timed(4, at(9, date = today.plusDays(1)), at(10, date = today.plusDays(1))),
        )
        val timeline = builder.build(events, today, now = at(8))
        assertEquals(listOf("a2", "a1"), timeline.allDay.map { it.title })
        assertTrue(timeline.items.isEmpty())
    }

    @Test
    fun nextBoundaryIsTheNextStartOrEnd() {
        val events = listOf(timed(1, at(9), at(10)), timed(2, at(11), at(12)))
        val timeline = builder.build(events, today, now = at(9, 30))
        assertEquals(at(10), builder.nextBoundary(timeline, at(9, 30)))
        assertEquals(null, builder.nextBoundary(builder.build(events, today, at(13)), at(13)))
    }
}
