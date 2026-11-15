package com.skestc.calendarwidget.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class MixArgbTest {
    @Test
    fun mixesEachChannel() {
        assertEquals(0xFFFFFFFFL, mixArgb(0xFFFFFFFFL, 0xFF000000L, 0f))
        assertEquals(0xFF000000L, mixArgb(0xFFFFFFFFL, 0xFF000000L, 1f))
        // 白色混入 50% 純紅。
        assertEquals(0xFFFF7F7FL, mixArgb(0xFFFFFFFFL, 0xFFFF0000L, 0.5f))
    }
}
