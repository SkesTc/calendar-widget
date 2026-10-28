package com.skestc.calendarwidget.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.LocalDate

class AgendaNowMarkerTest {

    private val today = LocalDate.of(2026, 10, 26)
    private fun header(date: LocalDate) = AgendaRow.Header(date, date.toString())
    private fun timed(id: Long, begin: Long, end: Long = begin + 10) =
        AgendaRow.Event(CalendarEvent(id, "e$id", begin, end, false, 1, null, "cal"), "t")
    private fun allDay(id: Long) = AgendaRow.Event(CalendarEvent(id, "a$id", 0, 1, true, 1, null, "cal"), "t")

    private fun List<AgendaRow>.shape() = map {
        when (it) {
            is AgendaRow.Header -> "H"
            is AgendaRow.Event -> it.event.title
            AgendaRow.NowMarker -> "NOW"
        }
    }

    @Test
    fun markerGoesBeforeTheFirstEventNotStartedYet() {
        val rows = listOf(header(today), allDay(1), timed(2, 100), timed(3, 300), header(today.plusDays(1)), timed(4, 900))
        assertEquals(
            listOf("H", "a1", "e2", "NOW", "e3", "H", "e4"),
            AgendaRow.withNowMarker(rows, today, now = 200).shape(),
        )
    }

    @Test
    fun markerGoesAfterTodayWhenEverythingHasStarted() {
        val rows = listOf(header(today), timed(1, 100), header(today.plusDays(1)), timed(2, 900))
        assertEquals(
            listOf("H", "e1", "NOW", "H", "e2"),
            AgendaRow.withNowMarker(rows, today, now = 500).shape(),
        )
    }

    @Test
    fun noMarkerWithoutTimedEventsToday() {
        val rows = listOf(header(today), allDay(1), header(today.plusDays(1)), timed(2, 900))
        assertSame(rows, AgendaRow.withNowMarker(rows, today, now = 500))
        val future = listOf(header(today.plusDays(1)), timed(2, 900))
        assertSame(future, AgendaRow.withNowMarker(future, today, now = 500))
    }
}
