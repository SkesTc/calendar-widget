package com.skestc.calendarwidget.lunar

import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/** 記住算過的農曆日期；月曆每次切換都要算 40 幾天，前後月份又會重複算到。 */
class CachingLunarSource(private val source: LunarSource) : LunarSource {
    private val cache = ConcurrentHashMap<LocalDate, LunarDate>()

    override fun lunarOf(date: LocalDate): LunarDate = cache.getOrPut(date) { source.lunarOf(date) }
}
