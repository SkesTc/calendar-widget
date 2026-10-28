package com.skestc.calendarwidget.data

import android.content.Context
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * 學期週次：設定開學日後，小工具上顯示「第 8 週」（學校行事曆多半用週次溝通）。
 * 開學日所在的那一週（週一起算）是第 1 週；所有小工具共用同一個開學日。
 */
object SchoolWeeks {
    private const val PREFS = "school_weeks"
    private const val KEY_START = "start_epoch_day"

    /** 超過這個週數（一學期約 20 週，含寒暑假前後）就不顯示，免得忘了更新開學日還一直往上數。 */
    const val MAX_WEEK = 26

    fun start(context: Context): LocalDate? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .takeIf { it.contains(KEY_START) }
            ?.getLong(KEY_START, 0)
            ?.let(LocalDate::ofEpochDay)

    fun setStart(context: Context, start: LocalDate?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (start == null) remove(KEY_START) else putLong(KEY_START, start.toEpochDay())
        }.apply()
    }

    /** [date] 是第幾週；開學前或超過 [MAX_WEEK] 週回傳 null。 */
    fun weekOf(date: LocalDate, start: LocalDate?): Int? {
        start ?: return null
        val firstMonday = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        if (date < firstMonday) return null
        val week = (ChronoUnit.DAYS.between(firstMonday, date) / 7 + 1).toInt()
        return week.takeIf { it <= MAX_WEEK }
    }
}
