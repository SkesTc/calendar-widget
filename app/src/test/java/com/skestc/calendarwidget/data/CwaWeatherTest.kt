package com.skestc.calendarwidget.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime

/** 中央氣象署：觀測站、36 小時預報，以及蓋在 Open-Meteo 上的結果。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CwaWeatherTest {

    private fun station(name: String, lat: Double, lon: Double, temperature: Double, humidity: Int = 70) = """
        {"StationName":"$name","StationId":"46","ObsTime":{"DateTime":"2026-11-04T14:00:00+08:00"},
         "GeoInfo":{"Coordinates":[{"CoordinateName":"TWD67","StationLatitude":${lat - 0.002},"StationLongitude":${lon - 0.008}},
                                   {"CoordinateName":"WGS84","StationLatitude":$lat,"StationLongitude":$lon}],
                    "CountyName":"臺中市"},
         "WeatherElement":{"Weather":"多雲","AirTemperature":$temperature,"RelativeHumidity":$humidity,"WindSpeed":2.5}}
    """.trimIndent()

    private val observation = """
        {"success":"true","records":{"Station":[
          ${station("臺北", 25.0377, 121.5149, 22.1)},
          ${station("臺中", 24.1457, 120.6840, 26.4, humidity = 65)},
          ${station("梧棲", 24.2560, 120.5240, -99.0)}
        ]}}
    """.trimIndent()

    private fun time(start: String, end: String, name: String, value: String? = null) =
        """{"startTime":"$start","endTime":"$end","parameter":{"parameterName":"$name"${value?.let { ",\"parameterValue\":\"$it\"" } ?: ""}}}"""

    private val forecast = """
        {"success":"true","records":{"datasetDescription":"三十六小時天氣預報","location":[{"locationName":"臺中市","weatherElement":[
          {"elementName":"Wx","time":[
            ${time("2026-11-04 12:00:00", "2026-11-04 18:00:00", "多雲時晴", "3")},
            ${time("2026-11-04 18:00:00", "2026-11-05 06:00:00", "多雲短暫雨", "8")},
            ${time("2026-11-05 06:00:00", "2026-11-05 18:00:00", "陰有雨", "14")}]},
          {"elementName":"PoP","time":[
            ${time("2026-11-04 12:00:00", "2026-11-04 18:00:00", "10")},
            ${time("2026-11-04 18:00:00", "2026-11-05 06:00:00", "30")},
            ${time("2026-11-05 06:00:00", "2026-11-05 18:00:00", "70")}]},
          {"elementName":"MinT","time":[
            ${time("2026-11-04 12:00:00", "2026-11-04 18:00:00", "24")},
            ${time("2026-11-04 18:00:00", "2026-11-05 06:00:00", "20")},
            ${time("2026-11-05 06:00:00", "2026-11-05 18:00:00", "19")}]},
          {"elementName":"MaxT","time":[
            ${time("2026-11-04 12:00:00", "2026-11-04 18:00:00", "28")},
            ${time("2026-11-04 18:00:00", "2026-11-05 06:00:00", "24")},
            ${time("2026-11-05 06:00:00", "2026-11-05 18:00:00", "23")}]}
        ]}]}}
    """.trimIndent()

    private val now = LocalDateTime.of(2026, 11, 4, 14, 20)

    @Test
    fun observationUsesNearestStationWithValidTemperature() {
        // 梧棲最近但氣溫是 -99（沒有資料），用臺中。
        val cwa = CwaParsers.observation(observation, 24.2500, 120.5300)!!
        assertEquals("臺中", cwa.station)
        assertEquals(26.4, cwa.temperature!!, 0.01)
        assertEquals(65, cwa.humidity)
        assertEquals(2.5, cwa.windMs!!, 0.01)
        assertEquals("2026-11-04 14:00", cwa.observedAt)
        assertEquals("臺中市", cwa.county)
    }

    @Test
    fun observationTooFarAwayIsIgnored() {
        // 金門離本島測站都超過 40 公里。
        assertNull(CwaParsers.observation(observation, 24.4321, 118.3171))
        assertNull(CwaParsers.observation("""{"success":"false","message":"Unauthorized"}""", 24.1, 120.6))
    }

    @Test
    fun forecastHasThreePeriods() {
        val periods = CwaParsers.forecast36h(forecast)
        assertEquals(3, periods.size)
        assertEquals(LocalDateTime.of(2026, 11, 4, 12, 0), periods[0].start)
        assertEquals("多雲時晴", periods[0].text)
        assertEquals(3, periods[0].code)
        assertEquals(10, periods[0].rainChance)
        assertEquals(24, periods[0].low)
        assertEquals(28, periods[0].high)
        assertEquals("陰有雨", periods[2].text)
    }

    @Test
    fun forecastAcceptsPascalCaseKeys() {
        val pascal = forecast
            .replace("\"records\"", "\"Records\"").replace("\"location\"", "\"Location\"")
            .replace("\"weatherElement\"", "\"WeatherElement\"").replace("\"elementName\"", "\"ElementName\"")
            .replace("\"time\"", "\"Time\"").replace("\"startTime\"", "\"StartTime\"").replace("\"endTime\"", "\"EndTime\"")
            .replace("\"parameter\"", "\"Parameter\"").replace("\"parameterName\"", "\"ParameterName\"")
            .replace("\"parameterValue\"", "\"ParameterValue\"")
        assertEquals(3, CwaParsers.forecast36h(pascal).size)
        assertTrue(CwaParsers.forecast36h("""{"records":{}}""").isEmpty())
        assertTrue(CwaParsers.forecast36h("not json").isEmpty())
    }

    @Test
    fun cwaOverridesOpenMeteoForToday() {
        val cwa = CwaParsers.observation(observation, 24.1477, 120.6736)!!.copy(periods = CwaParsers.forecast36h(forecast))
        val openMeteo = WeatherToday(LocalDate.of(2026, 11, 4), 23, 2, true, 25, 18, 5, 60, 0)
        val merged = WeatherMerge.today(openMeteo, cwa, aqi = 60, now = now, fetchedAt = 9)!!
        assertEquals(WeatherSource.CWA, merged.source)
        assertEquals(26, merged.temperature)
        assertEquals("多雲時晴", merged.description)
        assertEquals(CwaCodes.toWmo(3), merged.code)
        // 今天的時段：12–18 時（28/24）與 18 時起（24/20）。
        assertEquals(28, merged.high)
        assertEquals(20, merged.low)
        assertEquals(30, merged.rainChance)
        assertEquals(60, merged.aqi)
        assertTrue(merged.isDay)
    }

    @Test
    fun cwaWorksWithoutOpenMeteo() {
        val cwa = CwaWeather(county = "臺中市", periods = CwaParsers.forecast36h(forecast))
        val merged = WeatherMerge.today(null, cwa, aqi = null, now = now, fetchedAt = 0)!!
        // 沒有觀測：溫度用這個時段的高低溫平均。
        assertEquals(26, merged.temperature)
        assertEquals(LocalDate.of(2026, 11, 4), merged.date)
        assertNull(WeatherMerge.today(null, null, null, now, 0))
    }

    @Test
    fun cacheRoundTrip() {
        val cwa = CwaParsers.observation(observation, 24.1477, 120.6736)!!.copy(periods = CwaParsers.forecast36h(forecast))
        assertEquals(cwa, CwaWeather.fromJson(cwa.toJson()))
        assertEquals("多雲短暫雨", cwa.periodAt(LocalDateTime.of(2026, 11, 4, 22, 0))?.text)
    }

    @Test
    fun cwaCodesMapToIcons() {
        assertEquals(0, CwaCodes.toWmo(1))
        assertEquals(61, CwaCodes.toWmo(14))
        assertEquals(95, CwaCodes.toWmo(22))
        assertEquals(45, CwaCodes.toWmo(24))
        assertEquals(71, CwaCodes.toWmo(42))
    }
}
