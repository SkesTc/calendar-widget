package com.skestc.calendarwidget.widget

import com.skestc.calendarwidget.data.CalendarEvent
import com.skestc.calendarwidget.data.MonthBuilder
import com.skestc.calendarwidget.settings.WidgetSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class MonthSelectionTest {

    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 10, 26)
    private fun at(date: LocalDate, h: Int) = date.atTime(h, 0).atZone(zone).toInstant().toEpochMilli()

    private val events = listOf(
        CalendarEvent(1, "牙醫", at(today.plusDays(2), 15), at(today.plusDays(2), 16), false, 1, null, "cal"),
        CalendarEvent(2, "晨會", at(today, 9), at(today, 10), false, 1, null, "cal"),
    )

    private fun loaded(): MonthState.Loaded {
        val builder = MonthBuilder(zone)
        val data = builder.build(YearMonth.of(2026, 10), today, events)
        return MonthState.Loaded(data, 0, selected = today, selectedEvents = builder.eventsOn(events, today), events = events)
    }

    @Test
    fun pickingADayUsesTheLoadedEventsWithoutReloading() {
        val picked = loaded().withSelection(today.plusDays(2))
        assertEquals(today.plusDays(2), picked.selected)
        assertEquals(listOf("牙醫"), picked.selectedEvents.map { it.title })
    }

    @Test
    fun noPickOrOutsideTheGridKeepsToday() {
        val state = loaded()
        assertSame(state, state.withSelection(null))
        assertSame(state, state.withSelection(LocalDate.of(2027, 3, 1)))
    }

    @Test
    fun selectionDoesNotChangeTheLoadKey() {
        val a = WidgetSettings(monthOffset = 1, selectedEpochDay = 100)
        val b = WidgetSettings(monthOffset = 1, selectedEpochDay = 200)
        assertEquals(a.monthKey(), b.monthKey())
    }
}
