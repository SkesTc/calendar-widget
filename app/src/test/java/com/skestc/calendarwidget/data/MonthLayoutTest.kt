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
    private val all = MonthLayout.HeaderButtons.ALL
    private val searchAndMenu = MonthLayout.HeaderButtons.SEARCH_AND_MENU
    private val menu = MonthLayout.HeaderButtons.MENU

    @Test
    fun wideHeaderShowsYearAndEveryButton() {
        assertEquals(MonthLayout.HeaderFit(showYear = true, buttons = all), MonthLayout.headerFit(400f, year, month))
    }

    @Test
    fun mediumHeaderKeepsSearchAndFoldsTheRestIntoMenu() {
        assertEquals(MonthLayout.HeaderFit(showYear = false, buttons = searchAndMenu), MonthLayout.headerFit(300f, year, month))
    }

    @Test
    fun narrowHeaderUsesOnlyTheMenu() {
        assertEquals(MonthLayout.HeaderFit(showYear = false, buttons = menu), MonthLayout.headerFit(220f, year, month))
    }

    @Test
    fun buttonsShrinkInOrderAsSpaceRunsOut() {
        assertEquals(all, MonthLayout.buttonsFor(102f))
        assertEquals(searchAndMenu, MonthLayout.buttonsFor(101f))
        assertEquals(searchAndMenu, MonthLayout.buttonsFor(68f))
        assertEquals(menu, MonthLayout.buttonsFor(67f))
    }

    @Test
    fun largerTextNeedsMoreRoom() {
        val bigYear = MonthLayout.textWidthDp("2026年10月", 16f * 1.5f)
        val bigMonth = MonthLayout.textWidthDp("10月", 16f * 1.5f)
        val big = MonthLayout.headerFit(400f, bigYear, bigMonth)
        assertTrue(big.buttons.ordinal >= MonthLayout.headerFit(400f, year, month).buttons.ordinal)
    }

    @Test
    fun textWidthCountsCjkWiderThanDigits() {
        assertEquals(16f, MonthLayout.textWidthDp("月", 16f), 0.01f)
        assertEquals(9.6f, MonthLayout.textWidthDp("1", 16f), 0.01f)
    }
}
