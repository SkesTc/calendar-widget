package com.skestc.calendarwidget.settings

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.skestc.calendarwidget.theme.WidgetThemeId
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetSettingsTest {

    @Test
    fun emptyStateUsesDefaults() {
        val settings = WidgetSettings.from(emptyPreferences())
        assertEquals(WidgetSettings(), settings)
        assertEquals(WidgetThemeId.DYNAMIC, settings.appearance.theme)
        assertEquals(100, settings.appearance.backgroundOpacity)
    }

    @Test
    fun readsAllStoredValues() {
        val prefs = mutablePreferencesOf(
            WidgetSettings.KEY_HIDDEN_CALENDARS to setOf("3", "7", "oops"),
            WidgetSettings.KEY_AGENDA_DAYS to 61,
            WidgetSettings.KEY_MONTH_OFFSET to -2,
            WidgetSettings.KEY_THEME to "JOURNAL",
            WidgetSettings.KEY_BG_OPACITY to 60,
            WidgetSettings.KEY_CORNER to 12,
            WidgetSettings.KEY_TEXT_SCALE to 115,
            WidgetSettings.KEY_FONT to "SERIF",
        )
        val settings = WidgetSettings.from(prefs)
        assertEquals(setOf(3L, 7L), settings.hiddenCalendarIds)
        assertEquals(61, settings.agendaDays)
        assertEquals(-2, settings.monthOffset)
        assertEquals(
            Appearance(
                theme = WidgetThemeId.JOURNAL,
                backgroundOpacity = 60,
                cornerDp = 12,
                textScalePercent = 115,
                font = FontChoice.SERIF,
            ),
            settings.appearance,
        )
    }

    @Test
    fun agendaLayoutDefaultsToListAndReadsTimeline() {
        assertEquals(AgendaLayout.LIST, WidgetSettings.from(mutablePreferencesOf()).agendaLayout)
        val prefs = mutablePreferencesOf(WidgetSettings.KEY_AGENDA_LAYOUT to "TIMELINE")
        assertEquals(AgendaLayout.TIMELINE, WidgetSettings.from(prefs).agendaLayout)
    }

    @Test
    fun agendaShowsAtLeastOneMonth() {
        assertEquals(31, WidgetSettings.from(mutablePreferencesOf()).agendaDays)
        // 舊版存的 7 天升到一個月。
        assertEquals(31, WidgetSettings.from(mutablePreferencesOf(WidgetSettings.KEY_AGENDA_DAYS to 7)).agendaDays)
    }

    @Test
    fun customColorsSurviveTheIntRoundTrip() {
        // Preferences 存 Int：大於 0x7FFFFFFF 的 ARGB 會變成負數，讀回時要還原。
        val prefs = mutablePreferencesOf(
            WidgetSettings.KEY_THEME to "CUSTOM",
            WidgetSettings.KEY_CUSTOM_PRIMARY to 0xFFE67C73.toInt(),
            WidgetSettings.KEY_CUSTOM_BACKGROUND to 0xFF1B2A41.toInt(),
        )
        val appearance = WidgetSettings.from(prefs).appearance
        assertEquals(WidgetThemeId.CUSTOM, appearance.theme)
        assertEquals(0xFFE67C73, appearance.customPrimary)
        assertEquals(0xFF1B2A41, appearance.customBackground)
    }

    @Test
    fun customColorsDefaultToGoogleBlueOnWhite() {
        val appearance = WidgetSettings.from(emptyPreferences()).appearance
        assertEquals(0xFF1A73E8, appearance.customPrimary)
        assertEquals(0xFFFFFFFF, appearance.customBackground)
    }

    @Test
    fun invalidValuesFallBackSafely() {
        val prefs = mutablePreferencesOf(
            WidgetSettings.KEY_THEME to "REMOVED_THEME",
            WidgetSettings.KEY_BG_OPACITY to 250,
            WidgetSettings.KEY_FONT to "COMIC",
        )
        val appearance = WidgetSettings.from(prefs).appearance
        assertEquals(WidgetThemeId.DYNAMIC, appearance.theme)
        assertEquals(100, appearance.backgroundOpacity)
        assertEquals(FontChoice.THEME, appearance.font)
    }
}
