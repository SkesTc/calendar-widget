package com.skestc.calendarwidget.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.glance.GlanceTheme
import androidx.test.core.app.ApplicationProvider
import com.skestc.calendarwidget.data.CalendarEvent
import com.skestc.calendarwidget.data.HolidayKind
import com.skestc.calendarwidget.lunar.DayLabeler
import com.skestc.calendarwidget.lunar.LunarDate
import com.skestc.calendarwidget.settings.Appearance
import com.skestc.calendarwidget.theme.ProvideWidgetStyle
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import java.util.TimeZone

/** widget UI 測試共用：固定時區、語系與日期，和建立假事件。 */
object WidgetFixtures {
    val zone: ZoneId = ZoneId.of("Asia/Taipei")
    val today: LocalDate = LocalDate.of(2026, 10, 26)

    /** 測試前呼叫：widget 內用的是系統預設時區與語系。 */
    fun useTaiwanDefaults() {
        TimeZone.setDefault(TimeZone.getTimeZone(zone))
        Locale.setDefault(Locale.TAIWAN)
    }

    val context: Context get() = ApplicationProvider.getApplicationContext()

    /** 假的農曆：每天都是九月十七，方便斷言。 */
    val labeler = DayLabeler { LunarDate(9, 17, false) }

    fun at(h: Int, m: Int = 0, date: LocalDate = today): Long =
        LocalDateTime.of(date, LocalTime.of(h, m)).atZone(zone).toInstant().toEpochMilli()

    fun timed(
        id: Long,
        title: String,
        from: Long,
        to: Long,
        location: String? = null,
    ) = CalendarEvent(id, title, from, to, false, 0xFF4285F4.toInt(), location, "工作")

    fun allDay(
        id: Long,
        title: String,
        date: LocalDate = today,
        holiday: HolidayKind? = null,
    ) = CalendarEvent(
        id, title,
        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        true, 0xFF0B8043.toInt(), null, "工作", holiday = holiday,
    )
}

/** 和正式 widget 一樣包上 GlanceTheme 與外觀設定。 */
@Composable
fun WidgetFrame(appearance: Appearance = Appearance(), content: @Composable () -> Unit) {
    GlanceTheme {
        ProvideWidgetStyle(appearance, content)
    }
}
