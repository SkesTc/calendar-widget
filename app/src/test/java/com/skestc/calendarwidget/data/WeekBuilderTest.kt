package com.skestc.calendarwidget.data

import com.skestc.calendarwidget.lunar.DayLabeler
import com.skestc.calendarwidget.lunar.LunarDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

class WeekBuilderTest {

    private val zone = ZoneId.of("Asia/Taipei")
    private val today = LocalDate.of(2026, 10, 26)

    private fun at(date: LocalDate, h: Int) =
        LocalDateTime.of(date, LocalTime.of(h, 0)).atZone(zone).toInstant().toEpochMilli()

    private fun timed(id: Long, date: LocalDate, from: Int, to: Int) =
        CalendarEvent(id, "e$id", at(date, from), at(date, to), false, 1, null, "cal")

    private fun allDay(id: Long, first: LocalDate, last: LocalDate = first, holiday: HolidayKind? = null) =
        CalendarEvent(
            id, "a$id",
            first.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            last.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            true, 1, null, "cal", holiday = holiday,
        )

    @Test
    fun sevenDaysStartingFromStart() {
        val week = WeekBuilder(zone).build(emptyList(), today)
        assertEquals(7, week.size)
        assertEquals(today, week.first().date)
        assertEquals(today.plusDays(6), week.last().date)
    }

    @Test
    fun eventsSortedAllDayFirstAndOverflowCounted() {
        val events = listOf(
            timed(1, today, 15, 16),
            timed(2, today, 9, 10),
            allDay(3, today),
            timed(4, today, 18, 19),
        )
        val day = WeekBuilder(zone).build(events, today, maxPerDay = 3).first()
        assertEquals(listOf("a3", "e2", "e1"), day.events.map { it.title })
        assertEquals(1, day.more)
    }

    @Test
    fun multiDayEventsAppearOnEachDay() {
        val week = WeekBuilder(zone).build(listOf(allDay(1, today.plusDays(1), today.plusDays(3))), today)
        assertEquals(listOf(0, 1, 1, 1, 0, 0, 0), week.map { it.events.size })
    }

    @Test
    fun holidaysBecomeLabelsNotEvents() {
        val events = listOf(
            allDay(1, today.plusDays(2), holiday = HolidayKind.DAY_OFF).copy(title = "某假日"),
            timed(2, today.plusDays(2), 9, 10),
        )
        val labeler = DayLabeler { LunarDate(9, 20, false) }
        val week = WeekBuilder(zone).build(events, today, labeler = labeler)
        assertEquals("某假日", week[2].label)
        assertEquals(HolidayKind.DAY_OFF, week[2].holiday)
        assertEquals(listOf("e2"), week[2].events.map { it.title })
        assertEquals("二十", week[0].label)
    }

    @Test
    fun nextEventPrefersTimedAndDetectsInProgress() {
        val now = at(today, 10)
        val events = listOf(
            allDay(1, today),
            timed(2, today, 9, 11),
            timed(3, today, 13, 14),
            allDay(4, today, holiday = HolidayKind.DAY_OFF),
        )
        val next = NextEventPicker.pick(events, now)!!
        assertEquals("e2", next.event.title)
        assertEquals(true, next.inProgress)
        assertEquals(at(today, 11), NextEventPicker.nextBoundary(next, now))

        val later = NextEventPicker.pick(events, at(today, 12))!!
        assertEquals("e3", later.event.title)
        assertEquals(false, later.inProgress)
        assertEquals(at(today, 13), NextEventPicker.nextBoundary(later, at(today, 12)))
    }

    /** 昨天的全天行程在台灣到今天 08:00（UTC 午夜）才結束，但不能算今天的「下一個行程」。 */
    @Test
    fun yesterdaysAllDayEventIsNotPickedBeforeEightAm() {
        val now = at(today, 7)
        assertNull(NextEventPicker.pick(listOf(allDay(1, today.minusDays(1))), now, zone))
        val todays = NextEventPicker.pick(listOf(allDay(1, today.minusDays(1)), allDay(2, today)), now, zone)!!
        assertEquals("a2", todays.event.title)
        assertEquals(true, todays.inProgress)
        val tomorrow = NextEventPicker.pick(listOf(allDay(3, today.plusDays(1))), now, zone)!!
        assertEquals(false, tomorrow.inProgress)
    }

    @Test
    fun nextEventFallsBackToAllDayThenNothing() {
        val now = at(today, 20)
        val events = listOf(allDay(1, today), timed(2, today, 9, 10))
        assertEquals("a1", NextEventPicker.pick(events, now)!!.event.title)
        assertNull(NextEventPicker.pick(listOf(timed(2, today, 9, 10)), now))
        assertNull(NextEventPicker.nextBoundary(null, now))
    }
}
