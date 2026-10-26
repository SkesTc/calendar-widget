package com.skestc.calendarwidget.lunar

import java.time.LocalDate

/** 農曆日期。[month] 為 1～12，[leap] 表示閏月。 */
data class LunarDate(val month: Int, val day: Int, val leap: Boolean)

/** 國曆 → 農曆的轉換來源；正式版用 Android 內建的 ICU，測試可以換成假資料。 */
fun interface LunarSource {
    fun lunarOf(date: LocalDate): LunarDate
}

object LunarFormatter {
    private val MONTHS = listOf("正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "冬", "臘")
    private val DIGITS = listOf("一", "二", "三", "四", "五", "六", "七", "八", "九", "十")

    /** 「正月」「閏六月」「臘月」。 */
    fun monthName(month: Int, leap: Boolean): String =
        (if (leap) "閏" else "") + MONTHS[month - 1] + "月"

    /** 「初一」「十五」「廿三」「三十」。 */
    fun dayName(day: Int): String = when {
        day <= 10 -> "初" + DIGITS[day - 1]
        day < 20 -> "十" + DIGITS[day - 11]
        day == 20 -> "二十"
        day < 30 -> "廿" + DIGITS[day - 21]
        else -> "三十"
    }

    /** 「九月十六」。 */
    fun fullName(date: LunarDate): String = monthName(date.month, date.leap) + dayName(date.day)
}

/** 傳統節日（依農曆，不含閏月）。 */
object ChineseFestivals {
    private val FIXED = mapOf(
        (1 to 1) to "春節",
        (1 to 15) to "元宵節",
        (5 to 5) to "端午節",
        (7 to 7) to "七夕",
        (7 to 15) to "中元節",
        (8 to 15) to "中秋節",
        (9 to 9) to "重陽節",
        (12 to 8) to "臘八",
    )

    /** [tomorrow] 用來判斷除夕（臘月最後一天，可能是廿九或三十）。 */
    fun festivalOf(today: LunarDate, tomorrow: LunarDate): String? {
        if (!today.leap) FIXED[today.month to today.day]?.let { return it }
        if (tomorrow.month == 1 && tomorrow.day == 1 && !tomorrow.leap) return "除夕"
        return null
    }
}

/** 一天在月曆上要顯示的小字。 */
data class DayLabel(
    val lunar: LunarDate,
    val festival: String?,
    val solarTerm: String?,
) {
    /** 月曆格子裡的小字：節日 > 節氣 > 農曆（初一顯示月份）。 */
    val short: String
        get() = festival ?: solarTerm ?: if (lunar.day == 1) {
            LunarFormatter.monthName(lunar.month, lunar.leap)
        } else {
            LunarFormatter.dayName(lunar.day)
        }

    /** 清單標題用的完整說明：「九月十六 · 霜降」。 */
    val long: String
        get() = listOfNotNull(LunarFormatter.fullName(lunar), festival, solarTerm).joinToString(" · ")
}

class DayLabeler(private val source: LunarSource) {
    fun labelOf(date: LocalDate): DayLabel {
        val lunar = source.lunarOf(date)
        return DayLabel(
            lunar = lunar,
            festival = ChineseFestivals.festivalOf(lunar, source.lunarOf(date.plusDays(1))),
            solarTerm = SolarTerms.termOn(date),
        )
    }
}
