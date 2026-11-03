package com.skestc.calendarwidget.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.skestc.calendarwidget.data.AirQuality
import com.skestc.calendarwidget.data.AirSource
import com.skestc.calendarwidget.data.DayForecast
import com.skestc.calendarwidget.data.HourForecast
import com.skestc.calendarwidget.data.WeatherDetail
import com.skestc.calendarwidget.data.WeatherToday
import com.skestc.calendarwidget.ui.theme.CalendarWidgetTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime

/** 點天氣列打開的詳細內容。 */
@RunWith(RobolectricTestRunner::class)
// 畫面拉長，LazyColumn 的每一區都會畫出來。
@Config(sdk = [35], qualifiers = "zh-rTW-w411dp-h3000dp")
class WeatherDetailContentTest {

    @get:Rule
    val rule = createComposeRule()

    private val date = LocalDate.of(2026, 11, 3)
    private val detail = WeatherDetail(
        today = WeatherToday(date, 24, 2, true, 27, 19, 40, 88, 0),
        feelsLike = 25, humidity = 71, windKmh = 11, uvIndex = 6.4,
        sunrise = LocalTime.of(6, 5), sunset = LocalTime.of(17, 15),
        hours = (8..19).map { HourForecast(date.atTime(it, 0), 20 + it % 5, 2, it < 18, it * 2) },
        days = listOf(DayForecast(date.plusDays(1), 3, 26, 19, 10)),
    )

    private fun show(air: AirQuality?) {
        rule.setContent { CalendarWidgetTheme { WeatherDetailContent(detail, air, fetchedAt = 0) } }
    }

    @Test
    fun showsNowHoursAndDays() {
        show(null)
        rule.onNodeWithText("多雲").assertExists()
        rule.onNodeWithText("體感 25° · 濕度 71% · 風速 11 km/h · 降雨 40%").assertExists()
        rule.onNodeWithText("接下來 12 小時").assertExists()
        rule.onNodeWithText("現在").assertExists()
        rule.onNodeWithText("日出").assertExists()
        rule.onNodeWithText("06:05").assertExists()
        rule.onNodeWithText("高量級").assertExists()
        rule.onNodeWithText("未來幾天").assertExists()
        rule.onNodeWithText("陰").assertExists()
        // 沒有空氣品質時不顯示那一區。
        rule.onNodeWithText("空氣品質").assertDoesNotExist()
    }

    @Test
    fun moenvAirShowsStationLevelAndAdvice() {
        show(
            AirQuality(
                aqi = 88, source = AirSource.MOENV, station = "忠明", pm25 = 21.0, pm10 = 40.0, ozone = 35.2,
                pollutant = "細懸浮微粒", time = "2026/11/03 08:00:00",
            ),
        )
        rule.onNodeWithText("AQI · 普通").assertExists()
        rule.onNodeWithText("環境部 忠明測站 · 2026/11/03 08:00:00").assertExists()
        rule.onNodeWithText("極特殊敏感族群請注意可能出現咳嗽或呼吸急促；其他人可正常戶外活動。").assertExists()
        rule.onNodeWithText("ppb").assertExists()
        rule.onNodeWithText("主要污染物：細懸浮微粒").assertExists()
    }

    @Test
    fun openMeteoAirIsMarkedAsEstimate() {
        show(AirQuality(aqi = 42, source = AirSource.OPEN_METEO, pm25 = 9.5))
        rule.onNodeWithText("AQI · 良好").assertExists()
        rule.onNodeWithText("Open-Meteo 模型推估").assertExists()
    }
}
