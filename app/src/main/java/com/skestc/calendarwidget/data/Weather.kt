package com.skestc.calendarwidget.data

import com.skestc.calendarwidget.R
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

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
    /** 空氣品質指標（環境部 AQI，或 Open-Meteo 依美國 EPA 算法推估，分級相同），沒有資料時為 null。 */
    val aqi: Int?,
    val fetchedAt: Long,
    /** 來源給的天氣描述（氣象署，例如「多雲時晴」）；null 時用 [code] 對應的文字。 */
    val description: String? = null,
    val source: WeatherSource = WeatherSource.OPEN_METEO,
)

enum class AirSource { OPEN_METEO, MOENV }

/** 空氣品質：兩種來源整理成同一個格式，存進快取。 */
data class AirQuality(
    val aqi: Int?,
    val source: AirSource,
    /** 環境部的測站名稱（例如「西屯」）。 */
    val station: String? = null,
    val county: String? = null,
    val pm25: Double? = null,
    val pm10: Double? = null,
    /** 臭氧：環境部是 ppb，Open-Meteo 是 μg/m³。 */
    val ozone: Double? = null,
    /** 主要污染物（環境部）。 */
    val pollutant: String? = null,
    /** 資料時間（來源給的字串，例如「2026/11/03 08:00:00」）。 */
    val time: String? = null,
) {
    fun toJson(): String = JSONObject().apply {
        aqi?.let { put("aqi", it) }
        put("source", source.name)
        station?.let { put("station", it) }
        county?.let { put("county", it) }
        pm25?.let { put("pm25", it) }
        pm10?.let { put("pm10", it) }
        ozone?.let { put("ozone", it) }
        pollutant?.let { put("pollutant", it) }
        time?.let { put("time", it) }
    }.toString()

    companion object {
        fun fromJson(json: String?): AirQuality? = json?.let {
            runCatching {
                val o = JSONObject(it)
                AirQuality(
                    aqi = o.numberOrNull("aqi")?.toInt(),
                    source = AirSource.valueOf(o.getString("source")),
                    station = o.textOrNull("station"),
                    county = o.textOrNull("county"),
                    pm25 = o.numberOrNull("pm25"),
                    pm10 = o.numberOrNull("pm10"),
                    ozone = o.numberOrNull("ozone"),
                    pollutant = o.textOrNull("pollutant"),
                    time = o.textOrNull("time"),
                )
            }.getOrNull()
        }
    }
}

object AirParsers {
    /** Open-Meteo 空氣品質 API 的 current 區塊。 */
    fun openMeteo(json: String): AirQuality? = runCatching {
        val current = JSONObject(json).getJSONObject("current")
        AirQuality(
            aqi = current.numberOrNull("us_aqi")?.let { Math.round(it).toInt() },
            source = AirSource.OPEN_METEO,
            pm25 = current.numberOrNull("pm2_5"),
            pm10 = current.numberOrNull("pm10"),
            ozone = current.numberOrNull("ozone"),
            time = current.textOrNull("time")?.replace('T', ' '),
        )
    }.getOrNull()

    /**
     * 環境部「空氣品質指標(AQI)」（aqx_p_432）：取離 [latitude]、[longitude] 最近、而且有 AQI 數值的測站。
     * 外層可能是陣列或 { "records": [...] }，欄位名稱不分大小寫，數值是字串。
     */
    fun moenv(json: String, latitude: Double, longitude: Double): AirQuality? = runCatching {
        val trimmed = json.trimStart()
        val records = if (trimmed.startsWith("[")) {
            JSONArray(trimmed)
        } else {
            JSONObject(trimmed).let { it.optJSONArray("records") ?: it.optJSONArray("Records") }
        } ?: return null
        val stations = (0 until records.length()).mapNotNull { i ->
            val r = records.optJSONObject(i) ?: return@mapNotNull null
            val aqi = r.field("aqi")?.toDoubleOrNull() ?: return@mapNotNull null
            val lat = r.field("latitude")?.toDoubleOrNull() ?: return@mapNotNull null
            val lon = r.field("longitude")?.toDoubleOrNull() ?: return@mapNotNull null
            Triple(r, aqi, Geo.distanceKm(latitude, longitude, lat, lon))
        }
        val (r, aqi, _) = stations.minByOrNull { it.third } ?: return null
        AirQuality(
            aqi = Math.round(aqi).toInt(),
            source = AirSource.MOENV,
            station = r.field("sitename"),
            county = r.field("county"),
            pm25 = r.field("pm2.5")?.toDoubleOrNull(),
            pm10 = r.field("pm10")?.toDoubleOrNull(),
            ozone = r.field("o3")?.toDoubleOrNull(),
            pollutant = r.field("pollutant"),
            time = r.field("publishtime"),
        )
    }.getOrNull()

    /** 欄位名稱不分大小寫；空字串、「-」視為沒有資料。 */
    private fun JSONObject.field(name: String): String? {
        val key = keys().asSequence().firstOrNull { it.equals(name, ignoreCase = true) } ?: return null
        return optString(key).trim().takeUnless { it.isEmpty() || it == "-" || it == "null" }
    }
}

object WeatherParser {
    /** 解析 Open-Meteo 的天氣預報；空氣品質另外抓，抓不到時 [air] 為 null，天氣照樣顯示。 */
    fun parse(forecast: String, air: AirQuality?, fetchedAt: Long): WeatherToday {
        val root = JSONObject(forecast)
        val current = root.getJSONObject("current")
        val daily = root.getJSONObject("daily")
        return WeatherToday(
            date = LocalDate.parse(daily.getJSONArray("time").getString(0)),
            temperature = Math.round(current.getDouble("temperature_2m")).toInt(),
            code = current.getInt("weather_code"),
            isDay = current.optInt("is_day", 1) == 1,
            high = Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0)).toInt(),
            low = Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0)).toInt(),
            rainChance = daily.optJSONArray("precipitation_probability_max").intOrNull(0),
            aqi = air?.aqi,
            fetchedAt = fetchedAt,
        )
    }
}

data class HourForecast(
    val time: LocalDateTime,
    val temperature: Int,
    val code: Int,
    val isDay: Boolean,
    val rainChance: Int?,
)

data class DayForecast(
    val date: LocalDate,
    val code: Int,
    val high: Int,
    val low: Int,
    val rainChance: Int?,
)

/** 點天氣列開啟的詳細內容。 */
data class WeatherDetail(
    val today: WeatherToday,
    val feelsLike: Int?,
    val humidity: Int?,
    val windKmh: Int?,
    val uvIndex: Double?,
    val sunrise: LocalTime?,
    val sunset: LocalTime?,
    /** 從現在這個小時起的 12 小時。 */
    val hours: List<HourForecast>,
    /** 明天起的幾天。 */
    val days: List<DayForecast>,
    /** 天氣來源選氣象署時：觀測站與 36 小時預報。 */
    val cwa: CwaWeather? = null,
)

object WeatherDetailParser {
    const val HOURS = 12

    fun parse(forecast: String, air: AirQuality?, fetchedAt: Long, now: LocalDateTime): WeatherDetail {
        val today = WeatherParser.parse(forecast, air, fetchedAt)
        val root = JSONObject(forecast)
        val current = root.getJSONObject("current")
        val daily = root.getJSONObject("daily")
        val hours = root.optJSONObject("hourly")?.let { hourly ->
            val times = hourly.getJSONArray("time")
            val temps = hourly.getJSONArray("temperature_2m")
            val codes = hourly.getJSONArray("weather_code")
            val rain = hourly.optJSONArray("precipitation_probability")
            val isDay = hourly.optJSONArray("is_day")
            val from = now.withMinute(0).withSecond(0).withNano(0)
            (0 until times.length())
                .map { i -> i to LocalDateTime.parse(times.getString(i)) }
                .filter { (_, time) -> !time.isBefore(from) }
                .take(HOURS)
                .map { (i, time) ->
                    HourForecast(
                        time = time,
                        temperature = Math.round(temps.getDouble(i)).toInt(),
                        code = codes.getInt(i),
                        isDay = isDay?.intOrNull(i)?.let { it == 1 } ?: true,
                        rainChance = rain.intOrNull(i),
                    )
                }
        }.orEmpty()
        val dates = daily.getJSONArray("time")
        val days = (1 until dates.length()).map { i ->
            DayForecast(
                date = LocalDate.parse(dates.getString(i)),
                code = daily.getJSONArray("weather_code").getInt(i),
                high = Math.round(daily.getJSONArray("temperature_2m_max").getDouble(i)).toInt(),
                low = Math.round(daily.getJSONArray("temperature_2m_min").getDouble(i)).toInt(),
                rainChance = daily.optJSONArray("precipitation_probability_max").intOrNull(i),
            )
        }
        return WeatherDetail(
            today = today,
            feelsLike = current.numberOrNull("apparent_temperature")?.let { Math.round(it).toInt() },
            humidity = current.numberOrNull("relative_humidity_2m")?.let { Math.round(it).toInt() },
            windKmh = current.numberOrNull("wind_speed_10m")?.let { Math.round(it).toInt() },
            uvIndex = daily.optJSONArray("uv_index_max")?.takeUnless { it.isNull(0) }?.optDouble(0),
            sunrise = daily.optJSONArray("sunrise").timeOrNull(0),
            sunset = daily.optJSONArray("sunset").timeOrNull(0),
            hours = hours,
            days = days,
        )
    }

    private fun JSONArray?.timeOrNull(i: Int): LocalTime? =
        this?.takeUnless { it.isNull(i) }?.let { runCatching { LocalDateTime.parse(it.getString(i)).toLocalTime() }.getOrNull() }
}

private fun JSONObject.numberOrNull(name: String): Double? =
    if (!has(name) || isNull(name)) null else optDouble(name).takeUnless { it.isNaN() }

private fun JSONObject.textOrNull(name: String): String? =
    if (!has(name) || isNull(name)) null else optString(name).ifBlank { null }

private fun JSONArray?.intOrNull(i: Int): Int? =
    this?.takeUnless { i >= it.length() || it.isNull(i) }?.let { Math.round(it.getDouble(i)).toInt() }

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

/** 空氣品質等級，顏色與活動建議依環境部 AQI 標準。 */
enum class AqiLevel(val max: Int, val color: Long, val darkText: Boolean, val label: Int, val advice: Int) {
    GOOD(50, 0xFF00E400, true, R.string.aqi_good, R.string.aqi_advice_good),
    MODERATE(100, 0xFFFFFF00, true, R.string.aqi_moderate, R.string.aqi_advice_moderate),
    SENSITIVE(150, 0xFFFF7E00, true, R.string.aqi_sensitive, R.string.aqi_advice_sensitive),
    UNHEALTHY(200, 0xFFFF0000, false, R.string.aqi_unhealthy, R.string.aqi_advice_unhealthy),
    VERY_UNHEALTHY(300, 0xFF8F3F97, false, R.string.aqi_very_unhealthy, R.string.aqi_advice_very_unhealthy),
    HAZARDOUS(Int.MAX_VALUE, 0xFF7E0023, false, R.string.aqi_hazardous, R.string.aqi_advice_hazardous),
    ;

    companion object {
        fun of(aqi: Int): AqiLevel = entries.first { aqi <= it.max }
    }
}
