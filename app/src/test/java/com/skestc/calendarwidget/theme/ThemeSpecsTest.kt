package com.skestc.calendarwidget.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 確保每個主題的文字都看得清楚（WCAG）：內文 4.5:1、次要文字與大字 3:1。
 * 半透明的毛玻璃以「疊在白色／黑色桌布上」估算。
 */
class ThemeSpecsTest {

    private val themes = mapOf(
        "BASELINE" to ThemeSpecs.BASELINE,
        "AMOLED" to ThemeSpecs.AMOLED,
        "MORANDI" to ThemeSpecs.MORANDI,
        "JOURNAL" to ThemeSpecs.JOURNAL,
        "GLASS" to ThemeSpecs.GLASS,
    )

    private fun check(name: String, spec: ThemeSpec, night: Boolean) {
        fun DayNight.pick() = if (night) this.night else day
        val wallpaper = if (night) 0xFF000000 else 0xFFFFFFFF
        val bg = Contrast.composite(spec.background.pick(), wallpaper)
        val card = Contrast.composite(spec.card.pick(), bg)
        val mode = if (night) "night" else "day"

        fun assertContrast(label: String, fg: Long, back: Long, min: Double) {
            val ratio = Contrast.ratio(Contrast.composite(fg, back), back)
            assertTrue("$name/$mode $label contrast %.2f < $min".format(ratio), ratio >= min)
        }

        assertContrast("onSurface/background", spec.onSurface.pick(), bg, 4.5)
        assertContrast("onSurfaceVariant/background", spec.onSurfaceVariant.pick(), bg, 4.5)
        assertContrast("onCard/card", spec.onCard.pick(), card, 4.5)
        assertContrast("primary/background", spec.primary.pick(), bg, 3.0)
        assertContrast("onPrimary/primary", spec.onPrimary.pick(), spec.primary.pick(), 4.5)
        assertContrast("sunday/background", spec.sunday.pick(), bg, 3.0)
        assertContrast("saturday/background", spec.saturday.pick(), bg, 3.0)
    }

    @Test
    fun allThemesAreReadableInLightAndDark() {
        for ((name, spec) in themes) {
            check(name, spec, night = false)
            check(name, spec, night = true)
        }
    }

    @Test
    fun contrastMathMatchesKnownValues() {
        assertEquals(21.0, Contrast.ratio(0xFF000000, 0xFFFFFFFF), 0.01)
        assertEquals(1.0, Contrast.ratio(0xFF777777, 0xFF777777), 0.01)
        assertEquals(0xFF808080, Contrast.composite(0x80FFFFFF, 0xFF000000))
    }

    @Test
    fun unknownThemeKeyFallsBackToDynamic() {
        assertEquals(WidgetThemeId.DYNAMIC, WidgetThemeId.fromKey(null))
        assertEquals(WidgetThemeId.DYNAMIC, WidgetThemeId.fromKey("NOPE"))
        assertEquals(WidgetThemeId.JOURNAL, WidgetThemeId.fromKey("JOURNAL"))
    }
}
