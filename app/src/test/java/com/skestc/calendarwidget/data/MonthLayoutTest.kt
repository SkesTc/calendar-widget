package com.skestc.calendarwidget.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthLayoutTest {

    @Test
    fun panelGrowsWithEventsUpToTheCap() {
        val one = MonthLayout.panelHeight(480f, 1)
        val three = MonthLayout.panelHeight(480f, 3)
        val many = MonthLayout.panelHeight(480f, 30)
        assertTrue(three > one)
        assertEquals(480f * MonthLayout.MAX_SHARE, many, 0.01f)
    }

    @Test
    fun emptyDayStillGetsOneLine() {
        assertEquals(MonthLayout.panelHeight(480f, 1), MonthLayout.panelHeight(480f, 0), 0.01f)
    }

    @Test
    fun allEventsFitWhenThePanelWasSizedForThem() {
        val h = MonthLayout.panelHeight(600f, 5)
        assertEquals(5, MonthLayout.eventsThatFit(h, 5))
    }

    @Test
    fun overflowKeepsRoomForMoreLine() {
        val h = MonthLayout.panelHeight(480f, 30)
        val shown = MonthLayout.eventsThatFit(h, 30)
        assertTrue(shown in 1 until 30)
        // 多留一行給「+N」：比完全不保留時少一筆以上。
        assertTrue(shown < ((h - 42f) / 20f).toInt() + 1)
    }

    @Test
    fun largerTextFitsFewerEvents() {
        assertTrue(MonthLayout.eventsThatFit(200f, 30, 1.3f) < MonthLayout.eventsThatFit(200f, 30, 1f))
    }
}

class MonthHeaderFitTest {
    private val year = MonthLayout.textWidthDp("2026年10月", 16f)
    private val month = MonthLayout.textWidthDp("10月", 16f)

    @Test
    fun wideHeaderShowsYearAndSeparateButtons() {
        assertEquals(MonthLayout.HeaderFit(showYear = true, separateButtons = true), MonthLayout.headerFit(400f, year, month))
    }

    @Test
    fun mediumHeaderDropsTheYearBeforeAnyButton() {
        assertEquals(MonthLayout.HeaderFit(showYear = false, separateButtons = true), MonthLayout.headerFit(300f, year, month))
    }

    @Test
    fun narrowHeaderMergesSearchAndSettingsIntoMenu() {
        assertEquals(MonthLayout.HeaderFit(showYear = false, separateButtons = false), MonthLayout.headerFit(220f, year, month))
    }

    @Test
    fun largerTextNeedsMoreRoom() {
        val bigYear = MonthLayout.textWidthDp("2026年10月", 16f * 1.5f)
        val bigMonth = MonthLayout.textWidthDp("10月", 16f * 1.5f)
        val normal = MonthLayout.headerFit(300f, year, month)
        val big = MonthLayout.headerFit(300f, bigYear, bigMonth)
        assertTrue(big.separateButtons <= normal.separateButtons)
        assertTrue(!big.showYear)
    }

    @Test
    fun textWidthCountsCjkWiderThanDigits() {
        assertEquals(16f, MonthLayout.textWidthDp("月", 16f), 0.01f)
        assertEquals(9.6f, MonthLayout.textWidthDp("1", 16f), 0.01f)
    }
}
