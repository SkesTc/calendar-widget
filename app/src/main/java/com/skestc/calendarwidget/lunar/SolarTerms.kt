package com.skestc.calendarwidget.lunar

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.floor
import kotlin.math.sin

/**
 * 二十四節氣：用太陽視黃經計算（Meeus《天文算法》第 25 章低精度公式，誤差約 0.01°，相當於 15 分鐘內）。
 * 節氣日期依慣例以東八區（UTC+8）判定。
 */
object SolarTerms {

    /** 依黃經 0°、15°、30°…排列，0° 是春分。 */
    val NAMES = listOf(
        "春分", "清明", "穀雨", "立夏", "小滿", "芒種",
        "夏至", "小暑", "大暑", "立秋", "處暑", "白露",
        "秋分", "寒露", "霜降", "立冬", "小雪", "大雪",
        "冬至", "小寒", "大寒", "立春", "雨水", "驚蟄",
    )

    private val CHINA = ZoneOffset.ofHours(8)

    /** ΔT（地球時 − 世界時），2020 年代約 69 秒。 */
    private const val DELTA_T_SECONDS = 69.0

    private val cache = mutableMapOf<Int, Map<LocalDate, String>>()

    /** [date] 當天（東八區）若是節氣就回傳名稱。 */
    fun termOn(date: LocalDate): String? = termsOf(date.year)[date]

    /** 一整年的節氣：日期 → 名稱，共 24 個。 */
    @Synchronized
    fun termsOf(year: Int): Map<LocalDate, String> = cache.getOrPut(year) {
        val result = linkedMapOf<LocalDate, String>()
        var day = LocalDate.of(year, 1, 1)
        var startSlot = slot(day)
        while (day.year == year) {
            val next = day.plusDays(1)
            val endSlot = slot(next)
            if (endSlot != startSlot) result[day] = NAMES[endSlot]
            day = next
            startSlot = endSlot
        }
        result
    }

    /** 當天 00:00（東八區）太陽黃經落在第幾個 15° 區間（0～23）。 */
    private fun slot(date: LocalDate): Int {
        val millis = date.atStartOfDay(CHINA).toInstant().toEpochMilli()
        return (floor(apparentLongitude(millis) / 15.0).toInt()) % 24
    }

    /** 太陽視黃經（度，0～360）。 */
    fun apparentLongitude(epochMillis: Long): Double {
        val jd = epochMillis / 86_400_000.0 + 2_440_587.5 + DELTA_T_SECONDS / 86_400.0
        val t = (jd - 2_451_545.0) / 36_525.0
        val l0 = 280.46646 + 36_000.76983 * t + 0.0003032 * t * t
        val m = Math.toRadians(357.52911 + 35_999.05029 * t - 0.0001537 * t * t)
        val c = (1.914602 - 0.004817 * t - 0.000014 * t * t) * sin(m) +
            (0.019993 - 0.000101 * t) * sin(2 * m) +
            0.000289 * sin(3 * m)
        val omega = Math.toRadians(125.04 - 1_934.136 * t)
        val apparent = l0 + c - 0.00569 - 0.00478 * sin(omega)
        return ((apparent % 360.0) + 360.0) % 360.0
    }

    /** 方便測試：某個時刻在東八區是哪一天。 */
    internal fun chinaDate(instant: Instant): LocalDate = instant.atOffset(CHINA).toLocalDate()
}
