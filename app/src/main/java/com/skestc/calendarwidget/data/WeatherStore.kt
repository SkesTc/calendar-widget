package com.skestc.calendarwidget.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/**
 * 天氣與空氣品質：選了地區才會連網，所有 widget 共用一份快取。
 * 資料來自 Open-Meteo（免費、不需要帳號或金鑰），只送出所選縣市的座標。
 */
class WeatherStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 選的地區；null 表示關閉天氣。 */
    var place: WeatherPlace?
        get() = WeatherPlaces.byId(prefs.getString(KEY_PLACE, null))
        set(value) {
            prefs.edit().apply {
                if (value == null) remove(KEY_PLACE) else putString(KEY_PLACE, value.id)
                // 換地區後舊的快取不能用。
                remove(KEY_FORECAST)
                remove(KEY_AIR)
                remove(KEY_FETCHED_AT)
                remove(KEY_FETCHED_PLACE)
            }.apply()
        }

    /** 快取裡今天的天氣；關閉、沒抓過、地區不符或已經不是今天時回傳 null。 */
    fun cached(today: LocalDate = LocalDate.now()): WeatherToday? {
        val place = place ?: return null
        if (prefs.getString(KEY_FETCHED_PLACE, null) != place.id) return null
        val forecast = prefs.getString(KEY_FORECAST, null) ?: return null
        val weather = runCatching {
            WeatherParser.parse(forecast, prefs.getString(KEY_AIR, null), prefs.getLong(KEY_FETCHED_AT, 0))
        }.getOrNull() ?: return null
        return weather.takeIf { it.date == today }
    }

    /** 快取超過 [MAX_AGE_MS] 或不存在時需要重新抓。 */
    fun isStale(now: Long = System.currentTimeMillis()): Boolean =
        place != null && (cached() == null || now - prefs.getLong(KEY_FETCHED_AT, 0) > MAX_AGE_MS)

    /** 連網抓一次並存進快取；失敗回傳 false（保留舊的快取）。 */
    suspend fun fetch(): Boolean = withContext(Dispatchers.IO) {
        val place = place ?: return@withContext false
        val forecast = runCatching { get(forecastUrl(place)) }.getOrNull() ?: return@withContext false
        // 空氣品質抓不到時天氣照樣顯示。
        val air = runCatching { get(airUrl(place)) }.getOrNull()
        // 確認格式正確才存。
        if (runCatching { WeatherParser.parse(forecast, air, 0) }.isFailure) return@withContext false
        prefs.edit()
            .putString(KEY_FORECAST, forecast)
            .putString(KEY_AIR, air)
            .putLong(KEY_FETCHED_AT, System.currentTimeMillis())
            .putString(KEY_FETCHED_PLACE, place.id)
            .apply()
        true
    }

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        try {
            check(connection.responseCode == HttpURLConnection.HTTP_OK) { "HTTP ${connection.responseCode}" }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val PREFS = "weather"
        private const val KEY_PLACE = "place"
        private const val KEY_FORECAST = "forecast"
        private const val KEY_AIR = "air"
        private const val KEY_FETCHED_AT = "fetched_at"
        private const val KEY_FETCHED_PLACE = "fetched_place"
        private const val TIMEOUT_MS = 15_000
        /** 背景每小時更新；超過兩小時沒更新就在 widget 載入時補抓。 */
        const val MAX_AGE_MS = 2 * 60 * 60 * 1000L

        internal fun forecastUrl(place: WeatherPlace) =
            "https://api.open-meteo.com/v1/forecast?latitude=${place.latitude}&longitude=${place.longitude}" +
                "&current=temperature_2m,weather_code,is_day" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
                "&timezone=auto&forecast_days=1"

        internal fun airUrl(place: WeatherPlace) =
            "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=${place.latitude}&longitude=${place.longitude}" +
                "&current=us_aqi&timezone=auto"
    }
}
