package com.skestc.calendarwidget.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

class EventSearchTest {

    private val zone = ZoneId.of("Asia/Taipei")
    private val today = LocalDate.of(2026, 10, 27)
    private fun at(date: LocalDate, h: Int) = LocalDateTime.of(date, LocalTime.of(h, 0)).atZone(zone).toInstant().toEpochMilli()
    private fun timed(id: Long, date: LocalDate, h: Int) =
        CalendarEvent(id, "e$id", at(date, h), at(date, h + 1), false, 1, null, "cal")
    private fun allDay(id: Long, date: LocalDate) = CalendarEvent(
        id, "a$id",
        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        true, 1, null, "cal",
    )

    @Test
    fun splitsIntoUpcomingAscendingAndPastDescending() {
        val now = at(today, 12)
        val events = listOf(
            timed(1, today.minusDays(30), 9),
            timed(2, today, 9), // 今天已結束：仍算即將到來
            timed(3, today.plusDays(5), 9),
            timed(4, today.minusDays(2), 9),
            allDay(5, today.minusDays(1)), // 昨天的全天行程：過去
            allDay(6, today.plusDays(1)),
        )
        val r = EventSearch.split(events, now, zone)
        assertEquals(listOf("e2", "a6", "e3"), r.upcoming.map { it.title })
        assertEquals(listOf("a5", "e4", "e1"), r.past.map { it.title })
    }

    @Test
    fun matchesAreCaseInsensitiveAndNonOverlapping() {
        assertEquals(listOf(0..1, 6..7), EventSearch.matches("Go to go", "go"))
        assertEquals(listOf(2..3), EventSearch.matches("期末考試", "考試"))
        assertTrue(EventSearch.matches("anything", "  ").isEmpty())
    }

    @Test
    fun rangeCoversAYearBackAndTwoYearsAhead() {
        val (start, end) = EventSearch.range(at(today, 12), zone)
        assertEquals(at(today.minusDays(365), 0), start)
        assertEquals(at(today.plusDays(730), 0), end)
    }
}
