package com.skestc.calendarwidget.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class SchoolWeeksTest {
    // 2026/8/31 是週一；開學日 9/1（週二）那一週算第 1 週。
    private val start = LocalDate.of(2026, 9, 1)

    @Test
    fun weekOneStartsOnTheMondayOfTheFirstDay() {
        assertEquals(1, SchoolWeeks.weekOf(LocalDate.of(2026, 8, 31), start))
        assertEquals(1, SchoolWeeks.weekOf(LocalDate.of(2026, 9, 6), start))
        assertEquals(2, SchoolWeeks.weekOf(LocalDate.of(2026, 9, 7), start))
        assertEquals(9, SchoolWeeks.weekOf(LocalDate.of(2026, 10, 28), start))
    }

    @Test
    fun beforeTheTermOrLongAfterShowsNothing() {
        assertNull(SchoolWeeks.weekOf(LocalDate.of(2026, 8, 30), start))
        assertNull(SchoolWeeks.weekOf(LocalDate.of(2027, 6, 1), start))
        assertNull(SchoolWeeks.weekOf(LocalDate.of(2026, 10, 28), null))
    }
}
