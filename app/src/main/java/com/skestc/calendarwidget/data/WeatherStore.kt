package com.skestc.calendarwidget.data

import android.content.Context
import com.skestc.calendarwidget.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime

/** 天氣的地點：關閉、自選縣市，或跟隨手機位置。 */
enum class WeatherLocationMode { OFF, PLACE, GPS }

/** 這次要查詢的座標與顯示名稱。 */
data class WeatherTarget(val latitude: Double, val longitude: Double, val name: String)

/** 抓天氣的結果，設定頁顯示用。 */
sealed interface FetchResult {
    data object Ok : FetchResult
    data object NoLocation : FetchResult
    data object WeatherFailed : FetchResult
    /** 天氣有抓到，但環境部的資料抓不到，改用 Open-Meteo 的空氣品質。 */
    data class AirFallback(val reason: String) : FetchResult
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
        WeatherLocationMode.PLACE -> place?.let { WeatherTarget(it.latitude, it.longitude, it.name()) }
        WeatherLocationMode.GPS -> lastGps()?.let { (lat, lon) ->
            WeatherTarget(lat, lon, app.getString(R.string.weather_here, WeatherPlaces.nearest(lat, lon).name()))
        }
    }

    /** 快取裡今天的天氣；關閉、沒抓過或已經不是今天時回傳 null。 */
    fun cached(today: LocalDate = LocalDate.now()): WeatherToday? {
        if (!enabled) return null
        val forecast = prefs.getString(KEY_FORECAST, null) ?: return null
        return runCatching { WeatherParser.parse(forecast, cachedAir(), fetchedAt()) }.getOrNull()
            ?.takeIf { it.date == today }
    }

    fun cachedDetail(now: LocalDateTime = LocalDateTime.now()): WeatherDetail? {
        if (!enabled) return null
        val forecast = prefs.getString(KEY_FORECAST, null) ?: return null
        return runCatching { WeatherDetailParser.parse(forecast, cachedAir(), fetchedAt(), now) }.getOrNull()
            ?.takeIf { it.today.date == now.toLocalDate() }
    }

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
        val forecast = runCatching { get(forecastUrl(target)) }.getOrNull()
            ?.takeIf { runCatching { WeatherParser.parse(it, null, 0) }.isSuccess }
            ?: return@withContext FetchResult.WeatherFailed

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
            .putString(KEY_FORECAST, forecast)
            .putString(KEY_AIR, air?.toJson())
            .putLong(KEY_FETCHED_AT, System.currentTimeMillis())
            .putString(KEY_FETCHED_NAME, target.name)
            .putString(KEY_FETCHED_LAT, target.latitude.toString())
            .putString(KEY_FETCHED_LON, target.longitude.toString())
            .putString(KEY_AIR_ERROR, airError)
            .apply()
        airError?.let { FetchResult.AirFallback(it) } ?: FetchResult.Ok
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

        internal fun moenvUrl(key: String) =
            "https://data.moenv.gov.tw/api/v2/aqx_p_432?api_key=${URLEncoder.encode(key, "UTF-8")}&limit=1000&format=JSON"
    }
}
