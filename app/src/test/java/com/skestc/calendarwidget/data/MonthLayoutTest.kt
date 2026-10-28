package com.skestc.calendarwidget.data

import com.skestc.calendarwidget.settings.HeaderButton
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
    private val all = HeaderButton.entries
    private val search = HeaderButton.SEARCH

    @Test
    fun wideHeaderShowsYearAndEveryButton() {
        val fit = MonthLayout.headerFit(400f, year, month, all)
        assertTrue(fit.showYear)
        assertEquals(MonthLayout.HeaderPlan(all, menu = false), fit.plan)
    }

    @Test
    fun mediumHeaderKeepsSearchAndFoldsTheRestIntoMenu() {
        val fit = MonthLayout.headerFit(300f, year, month, all)
        assertTrue(!fit.showYear)
        assertEquals(MonthLayout.HeaderPlan(listOf(search), menu = true), fit.plan)
    }

    @Test
    fun narrowHeaderUsesOnlyTheMenu() {
        assertEquals(MonthLayout.HeaderPlan(emptyList(), menu = true), MonthLayout.headerFit(220f, year, month, all).plan)
    }

    @Test
    fun fewerChosenButtonsLeaveRoomForTheYear() {
        val fit = MonthLayout.headerFit(300f, year, month, listOf(search))
        assertTrue(fit.showYear)
        assertEquals(MonthLayout.HeaderPlan(listOf(search), menu = false), fit.plan)
        // 全部取消勾選：只剩「＋」，沒有「⋯」。
        assertEquals(MonthLayout.HeaderPlan(emptyList(), menu = false), MonthLayout.headerFit(220f, year, month, emptyList()).plan)
    }

    @Test
    fun planKeepsOrderAndReservesASlotForTheMenu() {
        assertEquals(MonthLayout.HeaderPlan(all, false), MonthLayout.planButtons(all, 102f))
        assertEquals(MonthLayout.HeaderPlan(listOf(HeaderButton.SEARCH), true), MonthLayout.planButtons(all, 101f))
        assertEquals(MonthLayout.HeaderPlan(emptyList(), true), MonthLayout.planButtons(all, 40f))
        assertEquals(MonthLayout.HeaderPlan(listOf(HeaderButton.REFRESH, HeaderButton.SETTINGS), false),
            MonthLayout.planButtons(listOf(HeaderButton.REFRESH, HeaderButton.SETTINGS), 68f))
    }

    @Test
    fun textWidthCountsCjkWiderThanDigits() {
        assertEquals(16f, MonthLayout.textWidthDp("月", 16f), 0.01f)
        assertEquals(9.6f, MonthLayout.textWidthDp("1", 16f), 0.01f)
    }
}
