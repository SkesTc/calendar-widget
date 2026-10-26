package com.skestc.calendarwidget.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class LongEventsTest {
    private val zone = ZoneId.of("Asia/Taipei")
    private val finder = LongEvents(zone)

    private fun allDay(id: Long, title: String, first: LocalDate, last: LocalDate, holiday: HolidayKind? = null) =
        CalendarEvent(
            id, title,
            first.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            last.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            true, 1, null, "cal", holiday = holiday,
        )

    private fun timed(id: Long, title: String, from: LocalDateTime, to: LocalDateTime) =
        CalendarEvent(id, title, from.atZone(zone).toInstant().toEpochMilli(), to.atZone(zone).toInstant().toEpochMilli(), false, 1, null, "cal")

    @Test
    fun findsEventsSpanningAtLeastThreeDays() {
        val d = LocalDate.of(2026, 10, 1)
        val events = listOf(
            allDay(1, "115學年度第一學期", d, d.plusDays(120)),
            allDay(2, "兩天的研習", d, d.plusDays(1)),
            allDay(3, "三天的露營", d.plusDays(5), d.plusDays(7)),
            timed(4, "跨夜的活動", LocalDateTime.of(2026, 10, 3, 20, 0), LocalDateTime.of(2026, 10, 6, 9, 0)),
            timed(5, "一般會議", LocalDateTime.of(2026, 10, 3, 9, 0), LocalDateTime.of(2026, 10, 3, 10, 0)),
        )
        val found = finder.find(events)
        assertEquals(listOf("115學年度第一學期", "跨夜的活動", "三天的露營"), found.map { it.title })
        assertEquals(121, found.first().days)
        assertEquals(4, found[1].days)
        assertEquals(3, found[2].days)
    }

    @Test
    fun recurringInstancesCollapseToTheEarliest() {
        val d = LocalDate.of(2026, 10, 5)
        val events = listOf(
            allDay(9, "每週考試週", d.plusDays(7), d.plusDays(10)),
            allDay(9, "每週考試週", d, d.plusDays(3)),
        )
        val found = finder.find(events)
        assertEquals(1, found.size)
        assertEquals(d, found.single().first)
    }

    @Test
    fun holidaysAreIgnored() {
        val d = LocalDate.of(2026, 10, 1)
        assertEquals(emptyList<LongEvent>(), finder.find(listOf(allDay(1, "國慶連假", d, d.plusDays(3), HolidayKind.DAY_OFF))))
    }
}
