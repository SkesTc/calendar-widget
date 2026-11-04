package com.skestc.calendarwidget.lunar

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** 用 Robolectric 內的 android.icu 驗證農曆換算（對照已知日期）。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IcuLunarSourceTest {
    private val icu = IcuLunarSource()
    private val labeler = DayLabeler(icu)

    @Test
    fun knownDates() {
        assertEquals(LunarDate(1, 1, false), icu.lunarOf(LocalDate.of(2026, 2, 17)))
        assertEquals(LunarDate(1, 1, false), icu.lunarOf(LocalDate.of(2025, 1, 29)))
        assertEquals(LunarDate(8, 15, false), icu.lunarOf(LocalDate.of(2026, 9, 25)))
        assertEquals(LunarDate(5, 5, false), icu.lunarOf(LocalDate.of(2026, 6, 19)))
        // 2025 年閏六月。
        assertEquals(LunarDate(6, 1, true), icu.lunarOf(LocalDate.of(2025, 7, 25)))
    }

    @Test
    fun festivalsFromRealCalendar() {
        assertEquals("除夕", labeler.labelOf(LocalDate.of(2026, 2, 16)).festival)
        assertEquals("春節", labeler.labelOf(LocalDate.of(2026, 2, 17)).festival)
        assertEquals("二月十八 · 清明", labeler.labelOf(LocalDate.of(2026, 4, 5)).long)
    }
}
