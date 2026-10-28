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

class SearchFilterTest {
    private val zone = ZoneId.of("Asia/Taipei")
    // 2026/10/28 是週三。
    private val today = LocalDate.of(2026, 10, 28)
    private val now = LocalDateTime.of(today, LocalTime.of(12, 0)).atZone(zone).toInstant().toEpochMilli()
    private fun on(id: Long, date: LocalDate, calendarId: Long = 1) = CalendarEvent(
        id, "e$id",
        LocalDateTime.of(date, LocalTime.of(9, 0)).atZone(zone).toInstant().toEpochMilli(),
        LocalDateTime.of(date, LocalTime.of(10, 0)).atZone(zone).toInstant().toEpochMilli(),
        false, 1, null, "cal", calendarId = calendarId,
    )

    private val events = listOf(
        on(1, today.minusDays(10)),
        on(2, today.minusDays(2)), // 10/26 週一：本週、本月
        on(3, today.plusDays(4)), // 11/1 週日：本週、下個月
        on(4, today.plusDays(20), calendarId = 2),
    )

    private fun titles(range: SearchRange, calendarId: Long? = null) =
        EventSearch.filter(events, range, calendarId, now, zone).map { it.title }

    @Test
    fun rangesPickTheRightDays() {
        assertEquals(listOf("e1", "e2", "e3", "e4"), titles(SearchRange.ALL))
        assertEquals(listOf("e3", "e4"), titles(SearchRange.UPCOMING))
        assertEquals(listOf("e2", "e3"), titles(SearchRange.THIS_WEEK))
        assertEquals(listOf("e1", "e2"), titles(SearchRange.THIS_MONTH))
    }

    @Test
    fun calendarFilterCombinesWithRange() {
        assertEquals(listOf("e4"), titles(SearchRange.ALL, calendarId = 2))
        assertEquals(emptyList<String>(), titles(SearchRange.THIS_WEEK, calendarId = 2))
    }

    @Test
    fun recentSearchesKeepNewestFirstWithoutDuplicates() {
        var items = listOf<String>()
        for (k in listOf("校慶", "會議", "校慶", "Meeting", "meeting")) items = RecentSearches.add(items, k)
        assertEquals(listOf("meeting", "校慶", "會議"), items)
        repeat(20) { items = RecentSearches.add(items, "k$it") }
        assertEquals(RecentSearches.MAX, items.size)
        assertEquals("k19", items.first())
    }
}
