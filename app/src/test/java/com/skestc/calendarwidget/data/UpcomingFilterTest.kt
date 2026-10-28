package com.skestc.calendarwidget.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** 行程清單、下一個行程要列哪些事件；重點是全天事件以 UTC 午夜為界的時區問題。 */
class UpcomingFilterTest {

    private val zone = ZoneId.of("Asia/Taipei")
    private val today = LocalDate.of(2026, 10, 26)

    private fun at(date: LocalDate, h: Int) =
        LocalDateTime.of(date, LocalTime.of(h, 0)).atZone(zone).toInstant().toEpochMilli()

    private fun allDay(first: LocalDate, last: LocalDate = first) = CalendarEvent(
        1, "a",
        first.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        last.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        true, 1, null, "cal",
    )

    private fun timed(from: Int, to: Int) = CalendarEvent(2, "t", at(today, from), at(today, to), false, 1, null, "cal")

    private fun upcoming(event: CalendarEvent, hour: Int, keepEndedToday: Boolean = true) =
        CalendarRepository.isUpcoming(event, at(today, hour), zone, keepEndedToday)

    @Test
    fun yesterdaysAllDayEventIsGoneAllDayToday() {
        // 結束時間是今天 08:00（台灣），以前會被當成今天的行程。
        for (hour in listOf(0, 7, 9, 23)) {
            assertFalse("hour $hour", upcoming(allDay(today.minusDays(1)), hour))
            assertFalse("hour $hour", upcoming(allDay(today.minusDays(1)), hour, keepEndedToday = false))
        }
    }

    @Test
    fun todaysAndMultiDayAllDayEventsStay() {
        assertTrue(upcoming(allDay(today), 23))
        assertTrue(upcoming(allDay(today.minusDays(2), today), 12))
        assertTrue(upcoming(allDay(today.plusDays(1)), 12))
    }

    @Test
    fun timedEventsEndedTodayStayOnlyWhenAsked() {
        val morning = timed(9, 10)
        assertTrue(upcoming(morning, 12, keepEndedToday = true))
        assertFalse(upcoming(morning, 12, keepEndedToday = false))
        assertTrue(upcoming(timed(14, 15), 12, keepEndedToday = false))
    }
}
