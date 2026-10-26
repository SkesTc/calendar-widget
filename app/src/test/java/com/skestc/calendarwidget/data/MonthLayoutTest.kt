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
