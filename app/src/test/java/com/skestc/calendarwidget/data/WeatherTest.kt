package com.skestc.calendarwidget.data

import com.skestc.calendarwidget.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** org.json 在純 JVM 上只有空殼，用 Robolectric 跑。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WeatherTest {

    private val hourTimes = (0 until 24).joinToString(",") { "\"2026-11-03T%02d:00\"".format(it) }
    private val hourTemps = (0 until 24).joinToString(",") { (18 + it % 8).toString() }
    private val hourCodes = (0 until 24).joinToString(",") { if (it < 12) "2" else "61" }
    private val hourRain = (0 until 24).joinToString(",") { (it * 4).toString() }
    private val hourDay = (0 until 24).joinToString(",") { if (it in 6..17) "1" else "0" }

    private val forecast = """
        {"latitude":24.15,"longitude":120.67,
         "current":{"time":"2026-11-03T08:00","interval":900,"temperature_2m":23.6,"relative_humidity_2m":71,
                    "apparent_temperature":25.2,"weather_code":2,"wind_speed_10m":11.4,"is_day":1},
         "hourly":{"time":[$hourTimes],"temperature_2m":[$hourTemps],"weather_code":[$hourCodes],
                   "precipitation_probability":[$hourRain],"is_day":[$hourDay]},
         "daily":{"time":["2026-11-03","2026-11-04","2026-11-05","2026-11-06"],
                  "weather_code":[61,3,0,95],
                  "temperature_2m_max":[27.4,26.0,28.2,25.1],
                  "temperature_2m_min":[18.5,19.0,20.3,18.9],
                  "precipitation_probability_max":[40,10,null,80],
                  "sunrise":["2026-11-03T06:05","2026-11-04T06:06","2026-11-05T06:06","2026-11-06T06:07"],
                  "sunset":["2026-11-03T17:15","2026-11-04T17:14","2026-11-05T17:14","2026-11-06T17:13"],
                  "uv_index_max":[6.35,5.1,7.0,3.2]}}
    """.trimIndent()

    private val openMeteoAir = """{"current":{"time":"2026-11-03T08:00","us_aqi":57.0,"pm2_5":14.2,"pm10":30.1,"ozone":61.0}}"""

    /** 環境部的回應：數值都是字串，有的測站沒有 AQI（維修中）。 */
    private fun moenvRecords(upperCase: Boolean = false): String {
        fun r(site: String, county: String, aqi: String, lat: String, lon: String) =
            if (upperCase) {
                """{"SiteName":"$site","County":"$county","AQI":"$aqi","Pollutant":"細懸浮微粒","PM2.5":"21","PM10":"40","O3":"35.2","Latitude":"$lat","Longitude":"$lon","PublishTime":"2026/11/03 08:00:00"}"""
            } else {
                """{"sitename":"$site","county":"$county","aqi":"$aqi","pollutant":"細懸浮微粒","pm2.5":"21","pm10":"40","o3":"35.2","latitude":"$lat","longitude":"$lon","publishtime":"2026/11/03 08:00:00"}"""
            }
        return listOf(
            r("士林", "臺北市", "45", "25.105417", "121.515000"),
            r("西屯", "臺中市", "", "24.162197", "120.616917"),
            r("忠明", "臺中市", "88", "24.151958", "120.641092"),
            r("前金", "高雄市", "120", "22.632567", "120.288086"),
        ).joinToString(",")
    }

    @Test
    fun parsesTodayFromForecast() {
        val weather = WeatherParser.parse(forecast, AirParsers.openMeteo(openMeteoAir), fetchedAt = 123)
        assertEquals(LocalDate.of(2026, 11, 3), weather.date)
        assertEquals(24, weather.temperature)
        assertEquals(2, weather.code)
        assertTrue(weather.isDay)
        assertEquals(27, weather.high)
        assertEquals(19, weather.low)
        assertEquals(40, weather.rainChance)
        assertEquals(57, weather.aqi)
        assertEquals(123, weather.fetchedAt)
    }

    @Test
    fun missingAirQualityStillShowsWeather() {
        assertNull(WeatherParser.parse(forecast, null, 0).aqi)
        assertNull(AirParsers.openMeteo("""{"current":{"us_aqi":null}}""")?.aqi)
        assertNull(AirParsers.openMeteo("not json"))
    }

    @Test
    fun openMeteoAirKeepsPollutants() {
        val air = AirParsers.openMeteo(openMeteoAir)!!
        assertEquals(AirSource.OPEN_METEO, air.source)
        assertEquals(14.2, air.pm25!!, 0.01)
        assertEquals(30.1, air.pm10!!, 0.01)
        assertEquals(61.0, air.ozone!!, 0.01)
    }

    @Test
    fun moenvPicksNearestStationWithAqi() {
        // 臺中市政府附近：西屯沒有 AQI，跳過，用忠明。
        val air = AirParsers.moenv("[${moenvRecords()}]", 24.1621, 120.6169)!!
        assertEquals(AirSource.MOENV, air.source)
        assertEquals("忠明", air.station)
        assertEquals(88, air.aqi)
        assertEquals("細懸浮微粒", air.pollutant)
        assertEquals(21.0, air.pm25!!, 0.01)
        assertEquals("2026/11/03 08:00:00", air.time)

        assertEquals("前金", AirParsers.moenv("[${moenvRecords()}]", 22.6273, 120.3014)?.station)
    }

    @Test
    fun moenvAcceptsRecordsObjectAndAnyCase() {
        val wrapped = """{"total":"4","records":[${moenvRecords(upperCase = true)}]}"""
        val air = AirParsers.moenv(wrapped, 25.0375, 121.5637)!!
        assertEquals("士林", air.station)
        assertEquals(45, air.aqi)
    }

    @Test
    fun moenvErrorsGiveNull() {
        assertNull(AirParsers.moenv("""{"error":"invalid api key"}""", 25.0, 121.5))
        assertNull(AirParsers.moenv("<html>", 25.0, 121.5))
        assertNull(AirParsers.moenv("[]", 25.0, 121.5))
    }

    @Test
    fun airQualityRoundTripsThroughCache() {
        val air = AirParsers.moenv("[${moenvRecords()}]", 24.15, 120.64)!!
        assertEquals(air, AirQuality.fromJson(air.toJson()))
        val estimate = AirParsers.openMeteo(openMeteoAir)!!
        assertEquals(estimate, AirQuality.fromJson(estimate.toJson()))
        assertNull(AirQuality.fromJson(null))
    }

    @Test
    fun detailHasNextTwelveHoursAndComingDays() {
        val detail = WeatherDetailParser.parse(forecast, null, 0, now = LocalDateTime.of(2026, 11, 3, 8, 40))
        assertEquals(25, detail.feelsLike)
        assertEquals(71, detail.humidity)
        assertEquals(11, detail.windKmh)
        assertEquals(6.35, detail.uvIndex!!, 0.001)
        assertEquals(LocalTime.of(6, 5), detail.sunrise)
        assertEquals(LocalTime.of(17, 15), detail.sunset)

        assertEquals(12, detail.hours.size)
        assertEquals(8, detail.hours.first().time.hour)
        assertEquals(19, detail.hours.last().time.hour)
        assertEquals(32, detail.hours.first().rainChance)
        assertFalse(detail.hours.last().isDay)

        assertEquals(listOf(4, 5, 6), detail.days.map { it.date.dayOfMonth })
        assertNull(detail.days[1].rainChance)
        assertEquals(28, detail.days[1].high)
    }

    @Test
    fun aqiLevelsFollowTaiwanBreakpoints() {
        assertEquals(AqiLevel.GOOD, AqiLevel.of(0))
        assertEquals(AqiLevel.GOOD, AqiLevel.of(50))
        assertEquals(AqiLevel.MODERATE, AqiLevel.of(51))
        assertEquals(AqiLevel.MODERATE, AqiLevel.of(100))
        assertEquals(AqiLevel.SENSITIVE, AqiLevel.of(101))
        assertEquals(AqiLevel.UNHEALTHY, AqiLevel.of(200))
        assertEquals(AqiLevel.VERY_UNHEALTHY, AqiLevel.of(201))
        assertEquals(AqiLevel.HAZARDOUS, AqiLevel.of(450))
        assertTrue(AqiLevel.GOOD.darkText)
        assertFalse(AqiLevel.UNHEALTHY.darkText)
    }

    @Test
    fun weatherCodesMapToIconAndText() {
        assertEquals("☀️", WeatherCodes.emoji(0, isDay = true))
        assertEquals("🌙", WeatherCodes.emoji(0, isDay = false))
        assertEquals("⛈️", WeatherCodes.emoji(95, isDay = true))
        assertEquals(R.string.weather_showers, WeatherCodes.description(81))
        assertEquals(R.string.weather_unknown, WeatherCodes.description(1234))
    }

    @Test
    fun placesAndNearestCounty() {
        assertEquals(22, WeatherPlaces.all.size)
        assertEquals(WeatherPlaces.all.size, WeatherPlaces.all.map { it.id }.toSet().size)
        for (place in WeatherPlaces.all) {
            assertTrue(place.zh, place.latitude in 21.8..26.5 && place.longitude in 118.0..122.1)
        }
        // 台中火車站 → 臺中市；台北 101 → 臺北市。
        assertEquals("taichung", WeatherPlaces.nearest(24.1369, 120.6866).id)
        assertEquals("taipei", WeatherPlaces.nearest(25.0340, 121.5645).id)
        assertEquals(2.0, Geo.distanceKm(25.0, 121.5, 25.018, 121.5), 0.1)
    }

    @Test
    fun urlsAskForTheFieldsTheParserNeeds() {
        val target = WeatherTarget(25.0375, 121.5637, "臺北市")
        val url = WeatherStore.forecastUrl(target)
        for (field in listOf(
            "temperature_2m", "apparent_temperature", "relative_humidity_2m", "wind_speed_10m", "is_day",
            "precipitation_probability", "temperature_2m_max", "precipitation_probability_max", "sunrise", "sunset", "uv_index_max",
        )) {
            assertTrue(field, field in url)
        }
        assertTrue("forecast_days=4" in url)
        for (field in listOf("us_aqi", "pm2_5", "pm10", "ozone")) assertTrue(field, field in WeatherStore.airUrl(target))
        assertTrue("aqx_p_432" in WeatherStore.moenvUrl("ab c"))
        assertTrue("api_key=ab+c" in WeatherStore.moenvUrl("ab c"))
    }
}
