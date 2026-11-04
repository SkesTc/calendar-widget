package com.skestc.calendarwidget.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** 天氣（現在溫度、今天的天氣與高低溫）的資料來源。 */
enum class WeatherSource { OPEN_METEO, CWA }

/** 中央氣象署 36 小時預報的一個時段（12 小時，第一段可能較短）。 */
data class CwaPeriod(
    val start: LocalDateTime,
    val end: LocalDateTime,
    /** 天氣描述，例如「多雲時晴」。 */
    val text: String,
    /** 天氣現象代碼（1～42）。 */
    val code: Int?,
    val rainChance: Int?,
    val low: Int?,
    val high: Int?,
)

/** 中央氣象署：最近氣象站的觀測，加上縣市的 36 小時預報；整理後存進快取。 */
data class CwaWeather(
    val county: String,
    val station: String? = null,
    /** 觀測時間（例如「2026-11-04 14:00」）。 */
    val observedAt: String? = null,
    val temperature: Double? = null,
    val humidity: Int? = null,
    /** 風速（公尺／秒）。 */
    val windMs: Double? = null,
    val periods: List<CwaPeriod> = emptyList(),
) {
    /** 現在所在的時段（找不到時用第一段）。 */
    fun periodAt(now: LocalDateTime): CwaPeriod? =
        periods.firstOrNull { !now.isBefore(it.start) && now.isBefore(it.end) } ?: periods.firstOrNull { it.end.isAfter(now) }

    fun toJson(): String = JSONObject().apply {
        put("county", county)
        station?.let { put("station", it) }
        observedAt?.let { put("observedAt", it) }
        temperature?.let { put("temperature", it) }
        humidity?.let { put("humidity", it) }
        windMs?.let { put("windMs", it) }
        put(
            "periods",
            JSONArray().apply {
                for (p in periods) {
                    put(
                        JSONObject().apply {
                            put("start", p.start.toString())
                            put("end", p.end.toString())
                            put("text", p.text)
                            p.code?.let { put("code", it) }
                            p.rainChance?.let { put("rain", it) }
                            p.low?.let { put("low", it) }
                            p.high?.let { put("high", it) }
                        },
                    )
                }
            },
        )
    }.toString()

    companion object {
        fun fromJson(json: String?): CwaWeather? = json?.let {
            runCatching {
                val o = JSONObject(it)
                val periods = o.optJSONArray("periods")
                CwaWeather(
                    county = o.getString("county"),
                    station = o.optString("station").ifBlank { null },
                    observedAt = o.optString("observedAt").ifBlank { null },
                    temperature = o.optNumber("temperature"),
                    humidity = o.optNumber("humidity")?.toInt(),
                    windMs = o.optNumber("windMs"),
                    periods = (0 until (periods?.length() ?: 0)).map { i ->
                        val p = periods!!.getJSONObject(i)
                        CwaPeriod(
                            start = LocalDateTime.parse(p.getString("start")),
                            end = LocalDateTime.parse(p.getString("end")),
                            text = p.getString("text"),
                            code = p.optNumber("code")?.toInt(),
                            rainChance = p.optNumber("rain")?.toInt(),
                            low = p.optNumber("low")?.toInt(),
                            high = p.optNumber("high")?.toInt(),
                        )
                    },
                )
            }.getOrNull()
        }

        private fun JSONObject.optNumber(name: String): Double? =
            if (!has(name) || isNull(name)) null else optDouble(name).takeUnless { it.isNaN() }
    }
}

/**
 * 解析中央氣象署開放資料的回應。欄位名稱不分大小寫（新舊版的大小寫不同），
 * 觀測值 -99、-999 等表示沒有資料。
 */
object CwaParsers {
    /** 局屬氣象站觀測（O-A0003-001）：取離座標最近、在 [MAX_STATION_KM] 公里內、有氣溫的測站。 */
    fun observation(json: String, latitude: Double, longitude: Double): CwaWeather? = runCatching {
        val stations = JSONObject(json).obj("records")?.arr("Station") ?: return null
        val candidates = (0 until stations.length()).mapNotNull { i ->
            val s = stations.optJSONObject(i) ?: return@mapNotNull null
            val element = s.obj("WeatherElement") ?: return@mapNotNull null
            val temperature = element.num("AirTemperature")?.takeIf { it > -50 } ?: return@mapNotNull null
            val (lat, lon) = coordinates(s) ?: return@mapNotNull null
            Triple(s, temperature, Geo.distanceKm(latitude, longitude, lat, lon))
        }
        val (station, temperature, distance) = candidates.minByOrNull { it.third } ?: return null
        if (distance > MAX_STATION_KM) return null
        val element = station.obj("WeatherElement")!!
        CwaWeather(
            county = station.obj("GeoInfo")?.str("CountyName").orEmpty(),
            station = station.str("StationName"),
            observedAt = station.obj("ObsTime")?.str("DateTime")?.let(::shortTime),
            temperature = temperature,
            humidity = element.num("RelativeHumidity")?.takeIf { it in 0.0..100.0 }?.let { Math.round(it).toInt() },
            windMs = element.num("WindSpeed")?.takeIf { it >= 0 },
        )
    }.getOrNull()

    /** 縣市 36 小時天氣預報（F-C0032-001）：三個時段的天氣、降雨機率、最低與最高溫。 */
    fun forecast36h(json: String): List<CwaPeriod> = runCatching {
        val location = JSONObject(json).obj("records")?.arr("location")?.optJSONObject(0) ?: return emptyList()
        val elements = location.arr("weatherElement") ?: return emptyList()
        fun element(name: String): JSONArray? = (0 until elements.length())
            .mapNotNull { elements.optJSONObject(it) }
            .firstOrNull { it.str("elementName").equals(name, ignoreCase = true) }
            ?.arr("time")
        val wx = element("Wx") ?: return emptyList()
        val pop = element("PoP")
        val minT = element("MinT")
        val maxT = element("MaxT")
        fun value(times: JSONArray?, i: Int): String? = times?.optJSONObject(i)?.obj("parameter")?.str("parameterName")
        (0 until wx.length()).mapNotNull { i ->
            val time = wx.optJSONObject(i) ?: return@mapNotNull null
            val parameter = time.obj("parameter") ?: return@mapNotNull null
            CwaPeriod(
                start = parseTime(time.str("startTime") ?: return@mapNotNull null) ?: return@mapNotNull null,
                end = parseTime(time.str("endTime") ?: return@mapNotNull null) ?: return@mapNotNull null,
                text = parameter.str("parameterName") ?: return@mapNotNull null,
                code = parameter.str("parameterValue")?.toIntOrNull(),
                rainChance = value(pop, i)?.toIntOrNull(),
                low = value(minT, i)?.toIntOrNull(),
                high = value(maxT, i)?.toIntOrNull(),
            )
        }
    }.getOrDefault(emptyList())

    private const val MAX_STATION_KM = 40.0

    /** 優先用 WGS84 座標（另一組是 TWD67）。 */
    private fun coordinates(station: JSONObject): Pair<Double, Double>? {
        val list = station.obj("GeoInfo")?.arr("Coordinates") ?: return null
        val all = (0 until list.length()).mapNotNull { list.optJSONObject(it) }
        val chosen = all.firstOrNull { it.str("CoordinateName").equals("WGS84", ignoreCase = true) } ?: all.firstOrNull()
            ?: return null
        val lat = chosen.num("StationLatitude") ?: return null
        val lon = chosen.num("StationLongitude") ?: return null
        return lat to lon
    }

    /** 「2026-11-04 12:00:00」或「2026-11-04T12:00:00+08:00」。 */
    internal fun parseTime(text: String): LocalDateTime? = runCatching {
        LocalDateTime.parse(text.replace(' ', 'T').take(19))
    }.getOrNull()

    private fun shortTime(text: String): String =
        parseTime(text)?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) ?: text

    private fun JSONObject.key(name: String): String? = keys().asSequence().firstOrNull { it.equals(name, ignoreCase = true) }
    private fun JSONObject.obj(name: String): JSONObject? = key(name)?.let { optJSONObject(it) }
    private fun JSONObject.arr(name: String): JSONArray? = key(name)?.let { optJSONArray(it) }
    private fun JSONObject.str(name: String): String? =
        key(name)?.let { if (isNull(it)) null else optString(it).trim().ifEmpty { null } }
    private fun JSONObject.num(name: String): Double? =
        str(name)?.toDoubleOrNull()?.takeUnless { it <= -98.0 }
}

/** 氣象署天氣現象代碼 → 最接近的 WMO 代碼（沿用同一套圖示）。 */
object CwaCodes {
    fun toWmo(code: Int): Int = when (code) {
        1 -> 0
        2, 3 -> 1
        4, 5 -> 2
        6, 7 -> 3
        8, 9, 10, 19, 20, 29, 30 -> 80
        11, 12, 13, 14, 31, 32, 38, 39 -> 61
        15, 16, 17, 18, 21, 22, 33, 34, 35, 36, 41 -> 95
        23, 37, 42 -> 71
        24, 25, 26, 27, 28 -> 45
        else -> 2
    }
}

/** 把氣象署的資料蓋在 Open-Meteo 上：現在溫度、天氣描述、高低溫、降雨機率用氣象署的。 */
object WeatherMerge {
    fun today(openMeteo: WeatherToday?, cwa: CwaWeather?, air: AirQuality?, now: LocalDateTime, fetchedAt: Long): WeatherToday? {
        if (cwa == null) return openMeteo
        val date = now.toLocalDate()
        val period = cwa.periodAt(now)
        // 今天剩下的時段：現在這段，加上今天開始的時段。
        val todayPeriods = cwa.periods.filter { it.end.isAfter(now) && (it == period || it.start.toLocalDate() == date) }
        val temperature = cwa.temperature?.let { Math.round(it).toInt() } ?: openMeteo?.temperature
            ?: period?.let { p -> listOfNotNull(p.low, p.high).takeIf { it.isNotEmpty() }?.average()?.let { Math.round(it).toInt() } }
            ?: return openMeteo
        val high = listOfNotNull(todayPeriods.mapNotNull { it.high }.maxOrNull(), cwa.temperature?.let { Math.round(it).toInt() })
            .maxOrNull() ?: openMeteo?.high ?: temperature
        val low = listOfNotNull(todayPeriods.mapNotNull { it.low }.minOrNull(), cwa.temperature?.let { Math.round(it).toInt() })
            .minOrNull() ?: openMeteo?.low ?: temperature
        return WeatherToday(
            date = date,
            temperature = temperature,
            code = period?.code?.let(CwaCodes::toWmo) ?: openMeteo?.code ?: 2,
            isDay = openMeteo?.isDay ?: (now.hour in 6..17),
            high = high,
            low = low,
            rainChance = todayPeriods.mapNotNull { it.rainChance }.maxOrNull() ?: openMeteo?.rainChance,
            aqi = air?.aqi,
            aqiEstimated = air?.source == AirSource.OPEN_METEO,
            fetchedAt = fetchedAt,
            description = period?.text,
            source = WeatherSource.CWA,
        )
    }
}
