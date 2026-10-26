package com.skestc.calendarwidget.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

class MonthBuilderTest {

    private val taipei = ZoneId.of("Asia/Taipei")
    private val october = YearMonth.of(2026, 10)
    private val today = LocalDate.of(2026, 10, 26)

    private fun builder(locale: Locale = Locale.TAIWAN) = MonthBuilder(taipei, locale)

    private fun allDay(first: LocalDate, lastInclusive: LocalDate, color: Int = 1) = CalendarEvent(
        eventId = 1,
        title = "all day",
        begin = first.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        end = lastInclusive.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        allDay = true,
        color = color,
        location = null,
        calendarName = "test",
    )

    private fun timed(start: LocalDateTime, end: LocalDateTime, color: Int = 1) = CalendarEvent(
        eventId = 2,
        title = "timed",
        begin = start.atZone(taipei).toInstant().toEpochMilli(),
        end = end.atZone(taipei).toInstant().toEpochMilli(),
        allDay = false,
        color = color,
        location = null,
        calendarName = "test",
    )

    private fun MonthData.cell(date: LocalDate) = weeks.flatten().single { it.date == date }

    @Test
    fun gridStartsOnSundayInTaiwan() {
        val b = builder()
        assertEquals(DayOfWeek.SUNDAY, b.firstDayOfWeek)
        // 2026/10/1 是週四，所以格子從 9/27（週日）開始，到 10/31（週六）結束，共 5 週。
        val range = b.gridRange(october)
        assertEquals(LocalDate.of(2026, 9, 27), range.start)
        assertEquals(LocalDate.of(2026, 10, 31), range.endInclusive)
        assertEquals(5, b.build(october, today, emptyList()).weeks.size)
    }

    @Test
    fun gridStartsOnMondayInGermany() {
        val range = builder(Locale.GERMANY).gridRange(october)
        assertEquals(LocalDate.of(2026, 9, 28), range.start)
        assertEquals(LocalDate.of(2026, 11, 1), range.endInclusive)
    }

    @Test
    fun everyWeekHasSevenDaysAndOutOfMonthDaysAreFlagged() {
        val data = builder().build(october, today, emptyList())
        assertTrue(data.weeks.all { it.size == 7 })
        assertFalse(data.cell(LocalDate.of(2026, 9, 30)).inMonth)
        assertTrue(data.cell(LocalDate.of(2026, 10, 1)).inMonth)
    }

    @Test
    fun singleAllDayEventMarksOnlyItsDay() {
        val day = LocalDate.of(2026, 10, 10)
        val data = builder().build(october, today, listOf(allDay(day, day, color = 7)))
        assertEquals(listOf(7), data.cell(day).eventColors)
        assertEquals(emptyList<Int>(), data.cell(day.minusDays(1)).eventColors)
        assertEquals(emptyList<Int>(), data.cell(day.plusDays(1)).eventColors)
    }

    @Test
    fun multiDayAllDayEventMarksEveryDay() {
        val event = allDay(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 12))
        val data = builder().build(october, today, listOf(event))
        for (d in 10..12) assertEquals(listOf(1), data.cell(LocalDate.of(2026, 10, d)).eventColors)
        assertEquals(emptyList<Int>(), data.cell(LocalDate.of(2026, 10, 13)).eventColors)
    }

    @Test
    fun timedEventEndingAtMidnightDoesNotSpillIntoNextDay() {
        val event = timed(
            LocalDateTime.of(2026, 10, 20, 22, 0),
            LocalDateTime.of(2026, 10, 21, 0, 0),
        )
        val data = builder().build(october, today, listOf(event))
        assertEquals(listOf(1), data.cell(LocalDate.of(2026, 10, 20)).eventColors)
        assertEquals(emptyList<Int>(), data.cell(LocalDate.of(2026, 10, 21)).eventColors)
    }

    @Test
    fun overnightTimedEventMarksBothDays() {
        val event = timed(
            LocalDateTime.of(2026, 10, 20, 22, 0),
            LocalDateTime.of(2026, 10, 21, 2, 0),
        )
        val data = builder().build(october, today, listOf(event))
        assertEquals(listOf(1), data.cell(LocalDate.of(2026, 10, 20)).eventColors)
        assertEquals(listOf(1), data.cell(LocalDate.of(2026, 10, 21)).eventColors)
    }

    @Test
    fun dotsAreDistinctAndCappedAtThree() {
        val day = LocalDate.of(2026, 10, 15)
        val events = listOf(1, 1, 2, 3, 4).map { allDay(day, day, color = it) }
        val data = builder().build(october, today, events)
        assertEquals(listOf(1, 2, 3), data.cell(day).eventColors)
    }

    @Test
    fun eventsOutsideTheGridAreIgnored() {
        val event = allDay(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2))
        val data = builder().build(october, today, listOf(event))
        assertTrue(data.weeks.flatten().all { it.eventColors.isEmpty() })
    }

    @Test
    fun holidayEventsMarkTheDayInsteadOfAddingDots() {
        val day = LocalDate.of(2026, 10, 10)
        val events = listOf(
            allDay(day, day, color = 9).copy(title = "國慶日", holiday = HolidayKind.DAY_OFF),
            allDay(day, day, color = 7),
        )
        val cell = builder().build(october, today, events).cell(day)
        assertEquals(listOf(7), cell.eventColors)
        assertEquals(HolidayKind.DAY_OFF, cell.holiday)
        assertEquals("國慶日", cell.label)
    }

    @Test
    fun dayOffWinsOverObservanceOnTheSameDay() {
        val day = LocalDate.of(2026, 10, 25)
        val events = listOf(
            allDay(day, day).copy(title = "某紀念日", holiday = HolidayKind.OBSERVANCE),
            allDay(day, day).copy(title = "光復節", holiday = HolidayKind.DAY_OFF),
        )
        val cell = builder().build(october, today, events).cell(day)
        assertEquals(HolidayKind.DAY_OFF, cell.holiday)
        assertEquals("光復節", cell.label)
    }

    @Test
    fun lunarLabelsAppearOnlyWhenLabelerGiven() {
        val labeler = com.skestc.calendarwidget.lunar.DayLabeler { com.skestc.calendarwidget.lunar.LunarDate(9, 17, false) }
        val day = LocalDate.of(2026, 10, 26)
        assertEquals("十七", builder().build(october, today, emptyList(), labeler).cell(day).label)
        assertEquals(null, builder().build(october, today, emptyList()).cell(day).label)
    }
}
