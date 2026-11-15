package com.skestc.calendarwidget.widget

import com.skestc.calendarwidget.data.AgendaRow
import com.skestc.calendarwidget.data.CalendarEvent
import com.skestc.calendarwidget.theme.Segment
import com.skestc.calendarwidget.theme.Segment.BOTTOM
import com.skestc.calendarwidget.theme.Segment.MIDDLE
import com.skestc.calendarwidget.theme.Segment.SINGLE
import com.skestc.calendarwidget.theme.Segment.TOP
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SegmentsTest {

    private val day = LocalDate.of(2026, 10, 26)
    private fun header(offset: Long) = AgendaRow.Header(day.plusDays(offset), "d$offset")
    private fun event(id: Long, past: Boolean = false) =
        AgendaRow.Event(CalendarEvent(id, "e$id", id, id + 1, false, 1, null, "cal"), "t", past)

    @Test
    fun ofCoversAllNeighbourCombinations() {
        assertEquals(SINGLE, Segment.of(hasPrevious = false, hasNext = false))
        assertEquals(TOP, Segment.of(hasPrevious = false, hasNext = true))
        assertEquals(MIDDLE, Segment.of(hasPrevious = true, hasNext = true))
        assertEquals(BOTTOM, Segment.of(hasPrevious = true, hasNext = false))
    }

    @Test
    fun eventsGroupPerDayAndHeadersSplitGroups() {
        val rows = listOf(header(0), event(1), event(2), event(3), header(1), event(4))
        assertEquals(
            listOf(SINGLE, TOP, MIDDLE, BOTTOM, SINGLE, SINGLE),
            segmentsOf(rows),
        )
    }

    @Test
    fun pastEventsHaveNoCardAndSplitTheGroup() {
        val rows = listOf(header(0), event(1, past = true), event(2), event(3))
        assertEquals(
            listOf(SINGLE, SINGLE, TOP, BOTTOM),
            segmentsOf(rows),
        )
    }
}
