package com.skestc.calendarwidget.lunar

import android.icu.util.ChineseCalendar
import android.icu.util.TimeZone
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 用 Android 內建的 ICU ChineseCalendar 換算農曆（API 24+）。
 * 把日期設成該日 UTC 正午、時區設成 UTC，國曆日期就不會因為時區跑掉；
 * ICU 內部本來就固定用東八區判定朔日。
 */
class IcuLunarSource : LunarSource {
    private val calendar = ChineseCalendar(TimeZone.getTimeZone("UTC"))

    @Synchronized
    override fun lunarOf(date: LocalDate): LunarDate {
        calendar.timeInMillis = date.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        return LunarDate(
            month = calendar.get(ChineseCalendar.MONTH) + 1,
            day = calendar.get(ChineseCalendar.DAY_OF_MONTH),
            leap = calendar.get(ChineseCalendar.IS_LEAP_MONTH) == 1,
        )
    }
}
