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

/** org.json 在純 JVM 上只有空殼，用 Robolectric 跑。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WeatherTest {

    private val forecast = """
        {"latitude":24.15,"longitude":120.67,
         "current":{"time":"2026-11-03T08:00","interval":900,"temperature_2m":23.6,"weather_code":2,"is_day":1},
         "daily":{"time":["2026-11-03"],"weather_code":[61],"temperature_2m_max":[27.4],
                  "temperature_2m_min":[18.5],"precipitation_probability_max":[40]}}
    """.trimIndent()

    private val air = """{"current":{"time":"2026-11-03T08:00","us_aqi":57.0}}"""

    @Test
    fun parsesForecastAndAirQuality() {
        val weather = WeatherParser.parse(forecast, air, fetchedAt = 123)
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
        assertNull(WeatherParser.parse(forecast, """{"current":{"us_aqi":null}}""", 0).aqi)
        assertNull(WeatherParser.parse(forecast, "not json", 0).aqi)
    }

    @Test
    fun missingRainChanceIsNull() {
        val noRain = forecast.replace("[40]", "[null]")
        assertNull(WeatherParser.parse(noRain, air, 0).rainChance)
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
    fun placesHaveUniqueIdsInsideTaiwan() {
        assertEquals(22, WeatherPlaces.all.size)
        assertEquals(WeatherPlaces.all.size, WeatherPlaces.all.map { it.id }.toSet().size)
        for (place in WeatherPlaces.all) {
            assertTrue(place.zh, place.latitude in 21.8..26.5 && place.longitude in 118.0..122.1)
        }
        assertEquals("臺中市", WeatherPlaces.byId("taichung")?.zh)
        assertNull(WeatherPlaces.byId(null))
    }

    @Test
    fun urlsAskForTheFieldsTheParserNeeds() {
        val place = WeatherPlaces.byId("taipei")!!
        val url = WeatherStore.forecastUrl(place)
        for (field in listOf("temperature_2m", "weather_code", "is_day", "temperature_2m_max", "precipitation_probability_max")) {
            assertTrue(field, field in url)
        }
        assertTrue("us_aqi" in WeatherStore.airUrl(place))
    }
}
