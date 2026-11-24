package com.skestc.calendarwidget.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekLayoutTest {

    @Test
    fun tallWidgetsUseRows() {
        // 4×5 直長（截圖的情況）→ 每天一列。
        assertTrue(WeekLayout.useRows(widthDp = 320f, heightDp = 420f))
        assertTrue(WeekLayout.useRows(widthDp = 320f, heightDp = 300f))
        // 4×2、4×3 扁長 → 7 欄。
        assertFalse(WeekLayout.useRows(widthDp = 320f, heightDp = 120f))
        assertFalse(WeekLayout.useRows(widthDp = 320f, heightDp = 200f))
        // 平板上拉得很寬也維持欄位式。
        assertFalse(WeekLayout.useRows(widthDp = 600f, heightDp = 300f))
    }

    @Test
    fun columnChipsGrowWithHeight() {
        assertEquals(2, WeekLayout.chipsPerColumn(110f, showLabel = false))
        assertEquals(3, WeekLayout.chipsPerColumn(140f, showLabel = true))
        // 200 − 16 − 14 − 24 − 12（小字）− 12（+N）= 122dp，可放 7 個 17dp 色塊。
        assertEquals(7, WeekLayout.chipsPerColumn(200f, showLabel = true))
        assertEquals(1, WeekLayout.chipsPerColumn(60f, showLabel = false))
        assertEquals(8, WeekLayout.chipsPerColumn(1000f, showLabel = true))
    }

    @Test
    fun rowLinesGrowWithHeight() {
        assertEquals(1, WeekLayout.linesPerRow(280f))
        assertEquals(3, WeekLayout.linesPerRow(420f))
        assertEquals(4, WeekLayout.linesPerRow(520f))
    }
}
