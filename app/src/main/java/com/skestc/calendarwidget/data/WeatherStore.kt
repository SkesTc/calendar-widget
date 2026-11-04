package com.skestc.calendarwidget.data

import android.content.Context
import com.skestc.calendarwidget.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDateTime

/** 天氣的地點：關閉、自選縣市，或跟隨手機位置。 */
enum class WeatherLocationMode { OFF, PLACE, GPS }

/** 這次要查詢的座標、顯示名稱，與氣象署預報用的縣市。 */
data class WeatherTarget(val latitude: Double, val longitude: Double, val name: String, val county: String)

/** 抓天氣的結果，設定頁顯示用。 */
sealed interface FetchResult {
    data object Ok : FetchResult
    data object NoLocation : FetchResult
    data object WeatherFailed : FetchResult
    /** 有抓到資料，但氣象署或環境部抓不到，該部分暫用 Open-Meteo（原因見設定頁）。 */
    data object Partial : FetchResult
}

/**
 * 天氣與空氣品質的設定與快取，所有 widget 共用。開啟後才會連網。
 * 天氣來自 Open-Meteo；空氣品質可選 Open-Meteo（免金鑰，模型推估）或環境部測站（需要 API 金鑰）。
 */
class WeatherStore(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var mode: WeatherLocationMode
        get() = prefs.getString(KEY_MODE, null)?.let { runCatching { WeatherLocationMode.valueOf(it) }.getOrNull() }
            // 舊版只有縣市：有選縣市就是 PLACE。
            ?: if (prefs.contains(KEY_PLACE)) WeatherLocationMode.PLACE else WeatherLocationMode.OFF
        set(value) {
            prefs.edit().putString(KEY_MODE, value.name).apply()
            clearCache()
        }

    /** 自選的縣市（PLACE 模式用；GPS 模式時保留，切回來不用重選）。 */
    var place: WeatherPlace?
        get() = WeatherPlaces.byId(prefs.getString(KEY_PLACE, null))
        set(value) {
            prefs.edit().apply { if (value == null) remove(KEY_PLACE) else putString(KEY_PLACE, value.id) }.apply()
            clearCache()
        }

    var airSource: AirSource
        get() = prefs.getString(KEY_AIR_SOURCE, null)?.let { runCatching { AirSource.valueOf(it) }.getOrNull() }
            ?: AirSource.OPEN_METEO
        set(value) {
            prefs.edit().putString(KEY_AIR_SOURCE, value.name).apply()
            markStale()
        }

    var weatherSource: WeatherSource
        get() = prefs.getString(KEY_WEATHER_SOURCE, null)?.let { runCatching { WeatherSource.valueOf(it) }.getOrNull() }
            ?: WeatherSource.OPEN_METEO
        set(value) {
            prefs.edit().putString(KEY_WEATHER_SOURCE, value.name).apply()
            markStale()
        }

    /** 中央氣象署氣象資料開放平臺的授權碼。 */
    var cwaKey: String
        get() = prefs.getString(KEY_CWA_KEY, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_CWA_KEY, value.trim()).apply()
            markStale()
        }

    /** 環境部資料開放平臺的 API 金鑰。 */
    var moenvKey: String
        get() = prefs.getString(KEY_MOENV_KEY, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_MOENV_KEY, value.trim()).apply()
            markStale()
        }

    val enabled: Boolean
        get() = when (mode) {
            WeatherLocationMode.OFF -> false
            WeatherLocationMode.PLACE -> place != null
            WeatherLocationMode.GPS -> true
        }

    /** 上次記下的手機位置（GPS 模式）。 */
    fun lastGps(): Pair<Double, Double>? {
        val lat = prefs.getString(KEY_GPS_LAT, null)?.toDoubleOrNull() ?: return null
        val lon = prefs.getString(KEY_GPS_LON, null)?.toDoubleOrNull() ?: return null
        return lat to lon
    }

    /** 記下手機位置；離上次抓天氣的地點超過 [MOVE_KM] 公里就讓快取過期。 */
    fun saveGps(latitude: Double, longitude: Double) {
        prefs.edit()
            .putString(KEY_GPS_LAT, latitude.toString())
            .putString(KEY_GPS_LON, longitude.toString())
            .apply()
    }

    fun target(): WeatherTarget? = when (mode) {
        WeatherLocationMode.OFF -> null
        WeatherLocationMode.PLACE -> place?.let { WeatherTarget(it.latitude, it.longitude, it.name(), it.cwaName) }
        WeatherLocationMode.GPS -> lastGps()?.let { (lat, lon) ->
            val county = WeatherPlaces.nearest(lat, lon)
            WeatherTarget(lat, lon, app.getString(R.string.weather_here, county.name()), county.cwaName)
        }
    }

    /** 快取裡今天的天氣；關閉、沒抓過或已經不是今天時回傳 null。 */
    fun cached(now: LocalDateTime = LocalDateTime.now()): WeatherToday? {
        if (!enabled) return null
        val air = cachedAir()
        val openMeteo = prefs.getString(KEY_FORECAST, null)
            ?.let { runCatching { WeatherParser.parse(it, air, fetchedAt()) }.getOrNull() }
            ?.takeIf { it.date == now.toLocalDate() }
        return WeatherMerge.today(openMeteo, cachedCwa(now), air?.aqi, now, fetchedAt())
            ?.takeIf { it.date == now.toLocalDate() }
    }

    fun cachedDetail(now: LocalDateTime = LocalDateTime.now()): WeatherDetail? {
        val today = cached(now) ?: return null
        val cwa = cachedCwa(now)
        val base = prefs.getString(KEY_FORECAST, null)
            ?.let { runCatching { WeatherDetailParser.parse(it, cachedAir(), fetchedAt(), now) }.getOrNull() }
            ?.takeIf { it.today.date == now.toLocalDate() }
            ?: WeatherDetail(today, null, null, null, null, null, null, emptyList(), emptyList())
        if (cwa == null) return base
        return base.copy(
            today = today,
            humidity = cwa.humidity ?: base.humidity,
            windKmh = cwa.windMs?.let { Math.round(it * 3.6).toInt() } ?: base.windKmh,
            cwa = cwa,
        )
    }

    /** 選了氣象署、而且有抓到今天還用得到的資料時才回傳。 */
    fun cachedCwa(now: LocalDateTime = LocalDateTime.now()): CwaWeather? {
        if (weatherSource != WeatherSource.CWA) return null
        val cwa = CwaWeather.fromJson(prefs.getString(KEY_CWA, null)) ?: return null
        return cwa.takeIf { it.temperature != null || it.periodAt(now) != null }
    }

    /** 上次抓氣象署資料失敗的原因（設定頁顯示）；成功時為 null。 */
    fun weatherError(): String? = prefs.getString(KEY_WEATHER_ERROR, null)

    fun cachedAir(): AirQuality? = AirQuality.fromJson(prefs.getString(KEY_AIR, null))

    /** 上次抓資料時的地點名稱。 */
    fun cachedName(): String? = prefs.getString(KEY_FETCHED_NAME, null)

    fun fetchedAt(): Long = prefs.getLong(KEY_FETCHED_AT, 0)

    /** 上次抓環境部資料失敗的原因（設定頁顯示）；成功時為 null。 */
    fun airError(): String? = prefs.getString(KEY_AIR_ERROR, null)

    /** 快取太舊、不存在，或 GPS 位置移動太多時需要重新抓。 */
    fun isStale(now: Long = System.currentTimeMillis()): Boolean {
        if (!enabled) return false
        if (cached() == null || now - fetchedAt() > MAX_AGE_MS) return true
        if (mode == WeatherLocationMode.GPS) {
            val (lat, lon) = lastGps() ?: return false
            val fetchedLat = prefs.getString(KEY_FETCHED_LAT, null)?.toDoubleOrNull() ?: return true
            val fetchedLon = prefs.getString(KEY_FETCHED_LON, null)?.toDoubleOrNull() ?: return true
            if (Geo.distanceKm(lat, lon, fetchedLat, fetchedLon) > MOVE_KM) return true
        }
        return false
    }

    /** 連網抓一次並存進快取；天氣失敗時保留舊的快取。 */
    suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        val target = target() ?: return@withContext FetchResult.NoLocation
        // Open-Meteo 一定抓：逐時、未來幾天、紫外線與日出日落都來自它。
        val forecast = runCatching { get(forecastUrl(target)) }.getOrNull()
            ?.takeIf { runCatching { WeatherParser.parse(it, null, 0) }.isSuccess }

        var weatherError: String? = null
        val cwa = if (weatherSource == WeatherSource.CWA) {
            if (cwaKey.isBlank()) {
                weatherError = app.getString(R.string.cwa_error_no_key)
                null
            } else {
                val observation = runCatching { get(cwaObservationUrl(cwaKey)) }
                    .onFailure { weatherError = it.message ?: it.javaClass.simpleName }
                    .getOrNull()
                    ?.let { CwaParsers.observation(it, target.latitude, target.longitude) }
                val periods = runCatching { get(cwaForecastUrl(cwaKey, target.county)) }
                    .onFailure { weatherError = it.message ?: it.javaClass.simpleName }
                    .getOrNull()
                    ?.let { CwaParsers.forecast36h(it) }
                    .orEmpty()
                if (observation == null && periods.isEmpty()) {
                    if (weatherError == null) weatherError = app.getString(R.string.moenv_error_format)
                    null
                } else {
                    (observation ?: CwaWeather(county = target.county)).copy(county = target.county, periods = periods)
                }
            }
        } else {
            null
        }
        if (forecast == null && cwa == null) return@withContext FetchResult.WeatherFailed

        var airError: String? = null
        val moenv = if (airSource == AirSource.MOENV) {
            if (moenvKey.isBlank()) {
                airError = app.getString(R.string.moenv_error_no_key)
                null
            } else {
                runCatching { get(moenvUrl(moenvKey)) }
                    .onFailure { airError = it.message ?: it.javaClass.simpleName }
                    .getOrNull()
                    ?.let { body ->
                        AirParsers.moenv(body, target.latitude, target.longitude)
                            ?: run {
                                airError = app.getString(R.string.moenv_error_format)
                                null
                            }
                    }
            }
        } else {
            null
        }
        // 環境部抓不到時（或選的就是 Open-Meteo）用 Open-Meteo 的空氣品質。
        val air = moenv ?: runCatching { get(airUrl(target)) }.getOrNull()?.let { AirParsers.openMeteo(it) }

        prefs.edit()
            .apply { if (forecast != null) putString(KEY_FORECAST, forecast) }
            .putString(KEY_CWA, cwa?.toJson())
            .putString(KEY_WEATHER_ERROR, weatherError)
            .putString(KEY_AIR, air?.toJson())
            .putLong(KEY_FETCHED_AT, System.currentTimeMillis())
            .putString(KEY_FETCHED_NAME, target.name)
            .putString(KEY_FETCHED_LAT, target.latitude.toString())
            .putString(KEY_FETCHED_LON, target.longitude.toString())
            .putString(KEY_AIR_ERROR, airError)
            .apply()
        if (airError != null || weatherError != null) FetchResult.Partial else FetchResult.Ok
    }

    private fun clearCache() {
        prefs.edit()
            .remove(KEY_FORECAST)
            .remove(KEY_AIR)
            .remove(KEY_FETCHED_AT)
            .remove(KEY_FETCHED_NAME)
            .remove(KEY_FETCHED_LAT)
            .remove(KEY_FETCHED_LON)
            .remove(KEY_AIR_ERROR)
            .remove(KEY_CWA)
            .remove(KEY_WEATHER_ERROR)
            .apply()
    }

    /** 換空氣品質來源時，天氣保留、但下次載入時重新抓。 */
    private fun markStale() {
        prefs.edit().putLong(KEY_FETCHED_AT, 0).apply()
    }

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        try {
            val code = connection.responseCode
            check(code == HttpURLConnection.HTTP_OK) { "HTTP $code" }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val PREFS = "weather"
        private const val KEY_MODE = "mode"
        private const val KEY_PLACE = "place"
        private const val KEY_AIR_SOURCE = "air_source"
        private const val KEY_MOENV_KEY = "moenv_key"
        private const val KEY_GPS_LAT = "gps_lat"
        private const val KEY_GPS_LON = "gps_lon"
        private const val KEY_FORECAST = "forecast"
        private const val KEY_AIR = "air_quality"
        private const val KEY_FETCHED_AT = "fetched_at"
        private const val KEY_FETCHED_NAME = "fetched_name"
        private const val KEY_FETCHED_LAT = "fetched_lat"
        private const val KEY_FETCHED_LON = "fetched_lon"
        private const val KEY_AIR_ERROR = "air_error"
        private const val KEY_WEATHER_SOURCE = "weather_source"
        private const val KEY_CWA_KEY = "cwa_key"
        private const val KEY_CWA = "cwa"
        private const val KEY_WEATHER_ERROR = "weather_error"
        private const val TIMEOUT_MS = 15_000
        /** 背景每小時更新；超過兩小時沒更新就在 widget 載入時補抓。 */
        const val MAX_AGE_MS = 2 * 60 * 60 * 1000L
        /** GPS 模式下移動超過這個距離就重新抓。 */
        const val MOVE_KM = 5.0

        internal fun forecastUrl(target: WeatherTarget) =
            "https://api.open-meteo.com/v1/forecast?latitude=${target.latitude}&longitude=${target.longitude}" +
                "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m,is_day" +
                "&hourly=temperature_2m,weather_code,precipitation_probability,is_day" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset,uv_index_max" +
                "&timezone=auto&forecast_days=4"

        internal fun airUrl(target: WeatherTarget) =
            "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=${target.latitude}&longitude=${target.longitude}" +
                "&current=us_aqi,pm2_5,pm10,ozone&timezone=auto"

        private const val CWA_API = "https://opendata.cwa.gov.tw/api/v1/rest/datastore"

        /** 局屬氣象站的現在天氣觀測。 */
        internal fun cwaObservationUrl(key: String) =
            "$CWA_API/O-A0003-001?Authorization=${URLEncoder.encode(key, "UTF-8")}&format=JSON"

        /** 縣市的 36 小時天氣預報。 */
        internal fun cwaForecastUrl(key: String, county: String) =
            "$CWA_API/F-C0032-001?Authorization=${URLEncoder.encode(key, "UTF-8")}&format=JSON" +
                "&locationName=${URLEncoder.encode(county, "UTF-8")}"

        internal fun moenvUrl(key: String) =
            "https://data.moenv.gov.tw/api/v2/aqx_p_432?api_key=${URLEncoder.encode(key, "UTF-8")}&limit=1000&format=JSON"
    }
}
