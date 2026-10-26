package com.skestc.calendarwidget.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.skestc.calendarwidget.theme.CustomTheme
import com.skestc.calendarwidget.theme.WidgetThemeId
import java.util.Locale

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
    /** 月曆上點選的日期（epoch day），null 表示沒點過。 */
    val selectedEpochDay: Long? = null,
    val refresh: Long = 0,
    val appearance: Appearance = Appearance(),
    /** 顯示農曆、節氣與傳統節日；預設只在中文介面開啟。 */
    val showLunar: Boolean = defaultShowLunar(),
    /** 行程清單的版面：一般清單，或像「今日時間軸」那樣有時間欄與「現在」橫線。 */
    val agendaLayout: AgendaLayout = AgendaLayout.LIST,
) {
    companion object {
        /** 行程清單至少顯示一個月（31 天）；選項是 1、2、3 個月。 */
        const val DEFAULT_AGENDA_DAYS = 31
        val AGENDA_DAY_OPTIONS = listOf(31, 61, 92)

        val KEY_HIDDEN_CALENDARS = stringSetPreferencesKey("hidden_calendars")
        val KEY_AGENDA_DAYS = intPreferencesKey("agenda_days")
        val KEY_MONTH_OFFSET = intPreferencesKey("month_offset")
        val KEY_SELECTED_DAY = longPreferencesKey("selected_day")
        val KEY_REFRESH = longPreferencesKey("refresh")
        val KEY_THEME = stringPreferencesKey("theme")
        val KEY_BG_OPACITY = intPreferencesKey("bg_opacity")
        val KEY_CORNER = intPreferencesKey("corner_dp")
        val KEY_TEXT_SCALE = intPreferencesKey("text_scale_percent")
        val KEY_FONT = stringPreferencesKey("font")
        val KEY_CUSTOM_PRIMARY = intPreferencesKey("custom_primary")
        val KEY_CUSTOM_BACKGROUND = intPreferencesKey("custom_background")
        val KEY_SHOW_LUNAR = booleanPreferencesKey("show_lunar")
        val KEY_AGENDA_LAYOUT = stringPreferencesKey("agenda_layout")

        fun defaultShowLunar(): Boolean = Locale.getDefault().language == "zh"

        fun from(prefs: Preferences) = WidgetSettings(
            hiddenCalendarIds = prefs[KEY_HIDDEN_CALENDARS].orEmpty()
                .mapNotNull { it.toLongOrNull() }
                .toSet(),
            // 舊版的 3／7／14 天設定一律升到一個月。
            agendaDays = (prefs[KEY_AGENDA_DAYS] ?: DEFAULT_AGENDA_DAYS).coerceAtLeast(DEFAULT_AGENDA_DAYS),
            monthOffset = prefs[KEY_MONTH_OFFSET] ?: 0,
            selectedEpochDay = prefs[KEY_SELECTED_DAY],
            refresh = prefs[KEY_REFRESH] ?: 0,
            appearance = Appearance(
                theme = WidgetThemeId.fromKey(prefs[KEY_THEME]),
                backgroundOpacity = (prefs[KEY_BG_OPACITY] ?: 100).coerceIn(0, 100),
                cornerDp = prefs[KEY_CORNER] ?: Appearance.DEFAULT_CORNER,
                textScalePercent = prefs[KEY_TEXT_SCALE] ?: 100,
                font = FontChoice.fromKey(prefs[KEY_FONT]),
                customPrimary = prefs[KEY_CUSTOM_PRIMARY]?.toArgbLong() ?: CustomTheme.DEFAULT_PRIMARY,
                customBackground = prefs[KEY_CUSTOM_BACKGROUND]?.toArgbLong() ?: CustomTheme.DEFAULT_BACKGROUND,
            ),
            showLunar = prefs[KEY_SHOW_LUNAR] ?: defaultShowLunar(),
            agendaLayout = AgendaLayout.fromKey(prefs[KEY_AGENDA_LAYOUT]),
        )
    }
}

/** 行程清單的版面。 */
enum class AgendaLayout {
    LIST,
    TIMELINE;

    companion object {
        fun fromKey(key: String?): AgendaLayout = entries.firstOrNull { it.name == key } ?: LIST
    }
}

/** 顯示名稱在 strings.xml（font_*）。 */
enum class FontChoice {
    THEME,
    SANS,
    SERIF,
    MONO;

    companion object {
        fun fromKey(key: String?): FontChoice = entries.firstOrNull { it.name == key } ?: THEME
    }
}

/** 外觀設定：主題、背景透明度、圓角、文字大小、字型。 */
data class Appearance(
    val theme: WidgetThemeId = WidgetThemeId.DYNAMIC,
    /** 0～100；毛玻璃主題不套用。 */
    val backgroundOpacity: Int = 100,
    /** [SYSTEM_CORNER] 表示跟隨系統（Android 12+ 的 system_app_widget_background_radius）。 */
    val cornerDp: Int = DEFAULT_CORNER,
    val textScalePercent: Int = 100,
    val font: FontChoice = FontChoice.THEME,
    /** 自訂主題的主色與背景色（ARGB）；其他主題不會用到。 */
    val customPrimary: Long = CustomTheme.DEFAULT_PRIMARY,
    val customBackground: Long = CustomTheme.DEFAULT_BACKGROUND,
) {
    companion object {
        const val SYSTEM_CORNER = -1
        const val DEFAULT_CORNER = SYSTEM_CORNER
        val CORNER_OPTIONS = listOf(SYSTEM_CORNER, 0, 12, 24, 32)
        /** 小／中／大／特大。 */
        val TEXT_SCALE_OPTIONS = listOf(90, 100, 115, 130)
    }
}

/** Preferences 只能存 Int；轉回不帶正負號的 ARGB Long。 */
private fun Int.toArgbLong(): Long = toLong() and 0xFFFFFFFFL
