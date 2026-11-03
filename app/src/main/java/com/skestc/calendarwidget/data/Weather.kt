package com.skestc.calendarwidget.data

import com.skestc.calendarwidget.R
import org.json.JSONObject
import java.time.LocalDate

/** 行程清單最上方那一列：現在的天氣、今天的高低溫與降雨機率、空氣品質。 */
data class WeatherToday(
    /** 抓資料的那一天；過了午夜就不再顯示昨天的預報。 */
    val date: LocalDate,
    val temperature: Int,
    /** WMO 天氣代碼（Open-Meteo 的 weather_code）。 */
    val code: Int,
    val isDay: Boolean,
    val high: Int,
    val low: Int,
    /** 今天最高的降雨機率（%），沒有資料時為 null。 */
    val rainChance: Int?,
    /** 空氣品質指標（美國 EPA 算法，和環境部 AQI 的分級相同），沒有資料時為 null。 */
    val aqi: Int?,
    val fetchedAt: Long,
)

object WeatherParser {
    /**
     * 解析 Open-Meteo 的天氣預報與空氣品質回應。
     * 空氣品質是另一個 API，抓不到時 [air] 為 null，天氣照樣顯示。
     */
    fun parse(forecast: String, air: String?, fetchedAt: Long): WeatherToday {
        val root = JSONObject(forecast)
        val current = root.getJSONObject("current")
        val daily = root.getJSONObject("daily")
        val rain = daily.optJSONArray("precipitation_probability_max")
        val aqi = air?.let { runCatching { JSONObject(it).getJSONObject("current") }.getOrNull() }
        return WeatherToday(
            date = LocalDate.parse(daily.getJSONArray("time").getString(0)),
            temperature = Math.round(current.getDouble("temperature_2m")).toInt(),
            code = current.getInt("weather_code"),
            isDay = current.optInt("is_day", 1) == 1,
            high = Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0)).toInt(),
            low = Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0)).toInt(),
            rainChance = rain?.takeUnless { it.isNull(0) }?.getInt(0),
            aqi = aqi?.takeUnless { it.isNull("us_aqi") }?.let { Math.round(it.getDouble("us_aqi")).toInt() },
            fetchedAt = fetchedAt,
        )
    }
}

/** WMO 天氣代碼 → 圖示與文字。 */
object WeatherCodes {
    fun emoji(code: Int, isDay: Boolean): String = when (code) {
        0 -> if (isDay) "☀️" else "🌙"
        1 -> if (isDay) "🌤️" else "🌙"
        2 -> "⛅"
        3 -> "☁️"
        45, 48 -> "🌫️"
        51, 53, 55, 56, 57, 80, 81, 82 -> "🌦️"
        61, 63, 65, 66, 67 -> "🌧️"
        71, 73, 75, 77, 85, 86 -> "🌨️"
        95, 96, 99 -> "⛈️"
        else -> "🌡️"
    }

    fun description(code: Int): Int = when (code) {
        0 -> R.string.weather_clear
        1 -> R.string.weather_mostly_clear
        2 -> R.string.weather_partly_cloudy
        3 -> R.string.weather_overcast
        45, 48 -> R.string.weather_fog
        51, 53, 55, 56, 57 -> R.string.weather_drizzle
        61, 63, 66 -> R.string.weather_rain
        65, 67 -> R.string.weather_heavy_rain
        71, 73, 75, 77, 85, 86 -> R.string.weather_snow
        80, 81, 82 -> R.string.weather_showers
        95, 96, 99 -> R.string.weather_thunder
        else -> R.string.weather_unknown
    }
}

/** 空氣品質等級，顏色與環境部 AQI 的標準色相同。 */
enum class AqiLevel(val max: Int, val color: Long, val darkText: Boolean, val label: Int) {
    GOOD(50, 0xFF00E400, true, R.string.aqi_good),
    MODERATE(100, 0xFFFFFF00, true, R.string.aqi_moderate),
    SENSITIVE(150, 0xFFFF7E00, true, R.string.aqi_sensitive),
    UNHEALTHY(200, 0xFFFF0000, false, R.string.aqi_unhealthy),
    VERY_UNHEALTHY(300, 0xFF8F3F97, false, R.string.aqi_very_unhealthy),
    HAZARDOUS(Int.MAX_VALUE, 0xFF7E0023, false, R.string.aqi_hazardous),
    ;

    companion object {
        fun of(aqi: Int): AqiLevel = entries.first { aqi <= it.max }
    }
}
