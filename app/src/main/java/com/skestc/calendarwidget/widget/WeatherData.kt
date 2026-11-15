package com.skestc.calendarwidget.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.datastore.preferences.core.Preferences
import androidx.glance.currentState
import com.skestc.calendarwidget.data.WeatherStore
import com.skestc.calendarwidget.data.WeatherToday
import com.skestc.calendarwidget.settings.WidgetSettings
import java.time.LocalDate

/**
 * 天氣列的資料，和行程分開讀：天氣更新（版本號加一）或換日時才重讀快取，
 * 不必重新查日曆、也不會讓月曆的資料快取失效。快取太舊時排一次背景更新（15 分鐘內不重複）。
 */
@Composable
internal fun rememberWeather(context: Context): WeatherToday? {
    val version = currentState<Preferences>()[WidgetSettings.KEY_WEATHER_VERSION] ?: 0L
    val today = LocalDate.now()
    return remember(version, today) {
        val store = WeatherStore(context)
        val weather = store.cached()
        if (store.claimRefresh(current = weather)) WeatherWorker.fetchNow(context)
        weather
    }
}
