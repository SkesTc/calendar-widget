package com.skestc.calendarwidget.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class DesaturateTest {

    private fun channels(c: Int) = listOf((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF)

    @Test
    fun fullDesaturationGivesGray() {
        val (r, g, b) = channels(Desaturate.color(0xFF4285F4.toInt(), amount = 1f))
        assertEquals(r, g)
        assertEquals(g, b)
    }

    @Test
    fun partialDesaturationReducesTheSpreadBetweenChannels() {
        val original = 0xFFE67C73.toInt()
        fun spread(c: Int) = channels(c).let { it.max() - it.min() }
        val muted = Desaturate.color(original)
        assertTrue(spread(muted) < spread(original) / 2)
        // 亮度大致不變。
        assertTrue(abs(Contrast.luminance(muted.toLong() and 0xFFFFFFFFL) - Contrast.luminance(original.toLong() and 0xFFFFFFFFL)) < 0.08)
    }

    @Test
    fun zeroKeepsTheColorAndResultIsOpaque() {
        assertEquals(0xFF336699.toInt(), Desaturate.color(0xFF336699.toInt(), amount = 0f))
        assertEquals(0xFF, (Desaturate.color(0x40336699) ushr 24) and 0xFF)
    }
}
