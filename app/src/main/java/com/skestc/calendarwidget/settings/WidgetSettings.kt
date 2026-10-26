package com.skestc.calendarwidget.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey

/**
 * 每個 widget 各自的設定，存在 Glance 的 per-widget Preferences 狀態裡。
 *
 * [refresh] 是一個遞增計數器：日曆資料或日期改變時加一，讓正在執行中的 widget session 重新讀取資料。
 */
data class WidgetSettings(
    /** 要隱藏的日曆；用「排除」而不是「選取」，新加入的日曆預設就會顯示。 */
    val hiddenCalendarIds: Set<Long> = emptySet(),
    val agendaDays: Int = DEFAULT_AGENDA_DAYS,
    val monthOffset: Int = 0,
    val refresh: Long = 0,
) {
    companion object {
        const val DEFAULT_AGENDA_DAYS = 7
        val AGENDA_DAY_OPTIONS = listOf(3, 7, 14, 30)

        val KEY_HIDDEN_CALENDARS = stringSetPreferencesKey("hidden_calendars")
        val KEY_AGENDA_DAYS = intPreferencesKey("agenda_days")
        val KEY_MONTH_OFFSET = intPreferencesKey("month_offset")
        val KEY_REFRESH = longPreferencesKey("refresh")

        fun from(prefs: Preferences) = WidgetSettings(
            hiddenCalendarIds = prefs[KEY_HIDDEN_CALENDARS].orEmpty()
                .mapNotNull { it.toLongOrNull() }
                .toSet(),
            agendaDays = prefs[KEY_AGENDA_DAYS] ?: DEFAULT_AGENDA_DAYS,
            monthOffset = prefs[KEY_MONTH_OFFSET] ?: 0,
            refresh = prefs[KEY_REFRESH] ?: 0,
        )
    }
}
