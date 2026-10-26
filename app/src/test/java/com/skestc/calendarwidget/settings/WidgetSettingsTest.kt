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
            WidgetSettings.KEY_AGENDA_DAYS to 14,
            WidgetSettings.KEY_MONTH_OFFSET to -2,
            WidgetSettings.KEY_THEME to "JOURNAL",
            WidgetSettings.KEY_BG_OPACITY to 60,
            WidgetSettings.KEY_CORNER to 12,
            WidgetSettings.KEY_TEXT_SCALE to 115,
            WidgetSettings.KEY_FONT to "SERIF",
        )
        val settings = WidgetSettings.from(prefs)
        assertEquals(setOf(3L, 7L), settings.hiddenCalendarIds)
        assertEquals(14, settings.agendaDays)
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
