package com.skestc.calendarwidget.lunar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class LunarTest {

    @Test
    fun dayNames() {
        assertEquals("初一", LunarFormatter.dayName(1))
        assertEquals("初十", LunarFormatter.dayName(10))
        assertEquals("十一", LunarFormatter.dayName(11))
        assertEquals("十五", LunarFormatter.dayName(15))
        assertEquals("十九", LunarFormatter.dayName(19))
        assertEquals("二十", LunarFormatter.dayName(20))
        assertEquals("廿一", LunarFormatter.dayName(21))
        assertEquals("廿九", LunarFormatter.dayName(29))
        assertEquals("三十", LunarFormatter.dayName(30))
    }

    @Test
    fun monthNames() {
        assertEquals("正月", LunarFormatter.monthName(1, leap = false))
        assertEquals("閏六月", LunarFormatter.monthName(6, leap = true))
        assertEquals("冬月", LunarFormatter.monthName(11, leap = false))
        assertEquals("臘月", LunarFormatter.monthName(12, leap = false))
    }

    @Test
    fun festivals() {
        val next = LunarDate(1, 2, false)
        assertEquals("春節", ChineseFestivals.festivalOf(LunarDate(1, 1, false), next))
        assertEquals("中秋節", ChineseFestivals.festivalOf(LunarDate(8, 15, false), LunarDate(8, 16, false)))
        // 閏月不過節。
        assertNull(ChineseFestivals.festivalOf(LunarDate(5, 5, true), LunarDate(5, 6, true)))
        // 臘月只有廿九天時，廿九就是除夕。
        assertEquals("除夕", ChineseFestivals.festivalOf(LunarDate(12, 29, false), LunarDate(1, 1, false)))
        assertNull(ChineseFestivals.festivalOf(LunarDate(12, 28, false), LunarDate(12, 29, false)))
    }

    @Test
    fun shortLabelPriority() {
        val lunar = LunarDate(3, 1, false)
        assertEquals("清明", DayLabel(lunar, festival = null, solarTerm = "清明").short)
        assertEquals("端午節", DayLabel(LunarDate(5, 5, false), "端午節", "芒種").short)
        assertEquals("三月", DayLabel(lunar, null, null).short)
        assertEquals("初二", DayLabel(LunarDate(3, 2, false), null, null).short)
        assertEquals("八月十五 · 中秋節", DayLabel(LunarDate(8, 15, false), "中秋節", null).long)
    }

    @Test
    fun labelerCombinesSourceFestivalAndSolarTerm() {
        // 假資料：2026/2/16 是臘月廿九（除夕），2/17 正月初一。
        val source = LunarSource { date ->
            when (date) {
                LocalDate.of(2026, 2, 16) -> LunarDate(12, 29, false)
                LocalDate.of(2026, 2, 17) -> LunarDate(1, 1, false)
                else -> LunarDate(1, 2, false)
            }
        }
        val labeler = DayLabeler(source)
        assertEquals("除夕", labeler.labelOf(LocalDate.of(2026, 2, 16)).festival)
        assertEquals("春節", labeler.labelOf(LocalDate.of(2026, 2, 17)).festival)
        assertEquals("立春", labeler.labelOf(LocalDate.of(2026, 2, 4)).solarTerm)
    }
}
