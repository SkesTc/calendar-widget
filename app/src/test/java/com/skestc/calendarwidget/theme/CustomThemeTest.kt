package com.skestc.calendarwidget.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomThemeTest {

    private fun assertReadable(primary: Long, background: Long) {
        val spec = CustomTheme.derive(primary, background)
        fun check(label: String, fg: Long, bg: Long, min: Double) {
            val ratio = Contrast.ratio(fg, bg)
            assertTrue(
                "primary=%08X bg=%08X %s %.2f < %.1f".format(primary, background, label, ratio, min),
                ratio >= min,
            )
        }
        val bg = spec.background.day
        check("onSurface", spec.onSurface.day, bg, 4.5)
        check("onSurfaceVariant", spec.onSurfaceVariant.day, bg, 4.5)
        check("onCard", spec.onCard.day, spec.card.day, 4.5)
        check("primary", spec.primary.day, bg, 3.0)
        check("onPrimary", spec.onPrimary.day, spec.primary.day, 4.5)
        check("sunday", spec.sunday.day, bg, 3.0)
        check("saturday", spec.saturday.day, bg, 3.0)
    }

    /** 每個色版 6 階（0、51…255）：6³ 種主色 × 6³ 種背景 = 46,656 種組合全部要看得清楚。 */
    @Test
    fun everyCombinationOnTheGridIsReadable() {
        val steps = listOf(0L, 51L, 102L, 153L, 204L, 255L)
        val colors = steps.flatMap { r -> steps.flatMap { g -> steps.map { b -> 0xFF000000 or (r shl 16) or (g shl 8) or b } } }
        for (bg in colors) for (primary in colors) assertReadable(primary, bg)
    }

    @Test
    fun accentEqualToBackgroundIsPushedAway() {
        val spec = CustomTheme.derive(0xFFFFFFFF, 0xFFFFFFFF)
        assertTrue(Contrast.ratio(spec.primary.day, spec.background.day) >= 3.0)
    }

    @Test
    fun goodAccentIsKeptAsIs() {
        // Google 藍在白底上已經超過 3:1，不需要調整。
        assertEquals(0xFF1A73E8, CustomTheme.derive(0xFF1A73E8, 0xFFFFFFFF).primary.day)
    }

    @Test
    fun alphaInInputsIsIgnored() {
        assertEquals(CustomTheme.derive(0xFF336699, 0xFFFFFFFF), CustomTheme.derive(0x80336699, 0x00FFFFFF))
    }

    @Test
    fun textIsDarkOnLightAndLightOnDark() {
        assertTrue(Contrast.luminance(CustomTheme.readableOn(0xFFFFFFFF)) < 0.1)
        assertTrue(Contrast.luminance(CustomTheme.readableOn(0xFF000000)) > 0.8)
    }
}
