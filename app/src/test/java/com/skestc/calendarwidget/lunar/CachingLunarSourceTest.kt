package com.skestc.calendarwidget.lunar

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CachingLunarSourceTest {

    @Test
    fun eachDateIsComputedOnce() {
        var calls = 0
        val source = CachingLunarSource { calls++; LunarDate(it.monthValue, it.dayOfMonth, false) }
        val date = LocalDate.of(2026, 10, 26)
        repeat(3) { assertEquals(LunarDate(10, 26, false), source.lunarOf(date)) }
        source.lunarOf(date.plusDays(1))
        assertEquals(2, calls)
    }
}
