package com.skestc.calendarwidget.data

import java.util.Locale

/** 可以選的天氣地區：台灣各縣市（座標取縣市政府所在地）。 */
data class WeatherPlace(val id: String, val zh: String, val en: String, val latitude: Double, val longitude: Double) {
    fun name(locale: Locale = Locale.getDefault()): String = if (locale.language == "zh") zh else en

    /** 氣象署預報用的縣市名稱（不含括號裡的說明）。 */
    val cwaName: String get() = zh.substringBefore("（")
}

object WeatherPlaces {
    val all = listOf(
        WeatherPlace("keelung", "基隆市", "Keelung", 25.1283, 121.7419),
        WeatherPlace("taipei", "臺北市", "Taipei", 25.0375, 121.5637),
        WeatherPlace("new_taipei", "新北市", "New Taipei", 25.0120, 121.4657),
        WeatherPlace("taoyuan", "桃園市", "Taoyuan", 24.9937, 121.3010),
        WeatherPlace("hsinchu_city", "新竹市", "Hsinchu City", 24.8039, 120.9647),
        WeatherPlace("hsinchu_county", "新竹縣", "Hsinchu County", 24.8387, 121.0177),
        WeatherPlace("miaoli", "苗栗縣", "Miaoli", 24.5602, 120.8214),
        WeatherPlace("taichung", "臺中市", "Taichung", 24.1477, 120.6736),
        WeatherPlace("changhua", "彰化縣", "Changhua", 24.0518, 120.5161),
        WeatherPlace("nantou", "南投縣", "Nantou", 23.9096, 120.6837),
        WeatherPlace("yunlin", "雲林縣", "Yunlin", 23.7092, 120.4313),
        WeatherPlace("chiayi_city", "嘉義市", "Chiayi City", 23.4801, 120.4491),
        WeatherPlace("chiayi_county", "嘉義縣", "Chiayi County", 23.4590, 120.3329),
        WeatherPlace("tainan", "臺南市", "Tainan", 22.9997, 120.2270),
        WeatherPlace("kaohsiung", "高雄市", "Kaohsiung", 22.6273, 120.3014),
        WeatherPlace("pingtung", "屏東縣", "Pingtung", 22.6727, 120.4884),
        WeatherPlace("yilan", "宜蘭縣", "Yilan", 24.7570, 121.7533),
        WeatherPlace("hualien", "花蓮縣", "Hualien", 23.9872, 121.6015),
        WeatherPlace("taitung", "臺東縣", "Taitung", 22.7583, 121.1444),
        WeatherPlace("penghu", "澎湖縣", "Penghu", 23.5655, 119.5793),
        WeatherPlace("kinmen", "金門縣", "Kinmen", 24.4321, 118.3171),
        WeatherPlace("lienchiang", "連江縣（馬祖）", "Lienchiang (Matsu)", 26.1602, 119.9517),
    )

    fun byId(id: String?): WeatherPlace? = all.firstOrNull { it.id == id }

    /** 離這個座標最近的縣市（GPS 模式顯示「目前位置 · 臺中市」用）。 */
    fun nearest(latitude: Double, longitude: Double): WeatherPlace =
        all.minBy { Geo.distanceKm(latitude, longitude, it.latitude, it.longitude) }
}

object Geo {
    /** 兩點的大圓距離（公里）。 */
    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2).let { it * it } +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2).let { it * it }
        return 6371.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }
}
