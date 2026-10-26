package com.skestc.calendarwidget.widget

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.skestc.calendarwidget.R
import com.skestc.calendarwidget.settings.WidgetSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/** 中英文字串資源與依語系的預設值。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StringsTest {

    private fun contextFor(locale: Locale): Context {
        val base: Context = ApplicationProvider.getApplicationContext()
        val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(config)
    }

    @Test
    fun durationsInChinese() {
        val zh = contextFor(Locale.TAIWAN)
        assertEquals("45 分", formatDuration(zh, 45))
        assertEquals("2 小時", formatDuration(zh, 120))
        assertEquals("2 小時 30 分", formatDuration(zh, 150))
    }

    @Test
    fun durationsInEnglish() {
        val en = contextFor(Locale.US)
        assertEquals("45 min", formatDuration(en, 45))
        assertEquals("2 h 30 min", formatDuration(en, 150))
    }

    @Test
    fun englishIsTheDefaultAndChineseIsTranslated() {
        assertEquals("Calendar Widgets", contextFor(Locale.US).getString(R.string.app_name))
        assertEquals("Calendar Widgets", contextFor(Locale.JAPAN).getString(R.string.app_name))
        assertEquals("行事曆小工具", contextFor(Locale.TAIWAN).getString(R.string.app_name))
        assertEquals("背景不透明度 60%", contextFor(Locale.TAIWAN).getString(R.string.section_opacity, 60))
    }

    @Test
    fun lunarIsOnByDefaultOnlyForChinese() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.TAIWAN)
            assertTrue(WidgetSettings().showLunar)
            Locale.setDefault(Locale.US)
            assertFalse(WidgetSettings().showLunar)
        } finally {
            Locale.setDefault(original)
        }
    }
}
