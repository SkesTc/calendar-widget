package com.skestc.calendarwidget.data

import android.content.ContentUris
import android.content.Intent
import android.provider.CalendarContract
import com.skestc.calendarwidget.widget.WidgetFixtures
import com.skestc.calendarwidget.widget.WidgetFixtures.allDay
import com.skestc.calendarwidget.widget.WidgetFixtures.at
import com.skestc.calendarwidget.widget.WidgetFixtures.timed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

/** App 內行程檢視：範圍、切換、每天的行程，以及從 widget 點進來的日期。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EventViewTest {

    @Before
    fun setUp() = WidgetFixtures.useTaiwanDefaults()

    private val day = LocalDate.of(2026, 11, 18) // 星期三
    private val builder = MonthBuilder(WidgetFixtures.zone, Locale.TAIWAN)

    @Test
    fun rangesForEachMode() {
        assertEquals(day..day, EventView.range(ViewMode.DAY, day, DayOfWeek.SUNDAY))
        assertEquals(LocalDate.of(2026, 11, 15)..LocalDate.of(2026, 11, 21), EventView.range(ViewMode.WEEK, day, DayOfWeek.SUNDAY))
        assertEquals(LocalDate.of(2026, 11, 16)..LocalDate.of(2026, 11, 22), EventView.range(ViewMode.WEEK, day, DayOfWeek.MONDAY))
        assertEquals(LocalDate.of(2026, 11, 1)..LocalDate.of(2026, 11, 30), EventView.range(ViewMode.MONTH, day, DayOfWeek.SUNDAY))
        assertEquals(day..LocalDate.of(2026, 12, 15), EventView.range(ViewMode.FOUR_WEEKS, day, DayOfWeek.SUNDAY))
    }

    @Test
    fun shiftMovesByTheModeUnit() {
        assertEquals(day.plusDays(1), EventView.shift(ViewMode.DAY, day, 1))
        assertEquals(day.minusWeeks(1), EventView.shift(ViewMode.WEEK, day, -1))
        assertEquals(LocalDate.of(2026, 12, 18), EventView.shift(ViewMode.MONTH, day, 1))
        assertEquals(day.plusWeeks(4), EventView.shift(ViewMode.FOUR_WEEKS, day, 1))
    }

    @Test
    fun weekListsEveryDayMonthOnlyBusyDays() {
        val events = listOf(
            timed(1, "晨會", at(9, date = day), at(10, date = day)),
            allDay(2, "國慶日", LocalDate.of(2026, 11, 20), HolidayKind.DAY_OFF),
            allDay(3, "運動會", LocalDate.of(2026, 11, 28)),
        )
        val week = EventView.days(events, EventView.range(ViewMode.WEEK, day, DayOfWeek.SUNDAY), ViewMode.WEEK, builder)
        assertEquals(7, week.size)
        assertEquals(listOf("晨會"), week.first { it.date == day }.events.map { it.title })
        // 假日不算一般行程，放在日期旁。
        val holiday = week.first { it.date == LocalDate.of(2026, 11, 20) }
        assertEquals(listOf("國慶日"), holiday.holidays.map { it.title })
        assertEquals(emptyList<CalendarEvent>(), holiday.events)

        val month = EventView.days(events, EventView.range(ViewMode.MONTH, day, DayOfWeek.SUNDAY), ViewMode.MONTH, builder)
        assertEquals(listOf(18, 20, 28), month.map { it.date.dayOfMonth })
    }

    @Test
    fun dayTargetFromWidgetIntent() {
        val millis = day.atTime(0, 0).atZone(WidgetFixtures.zone).toInstant().toEpochMilli()
        val intent = Intent(
            Intent.ACTION_VIEW,
            CalendarContract.CONTENT_URI.buildUpon().appendPath("time").appendPath(millis.toString()).build(),
        )
        assertEquals(ViewTarget(day, null), EventView.targetOf(intent, WidgetFixtures.zone))
    }

    @Test
    fun eventTargetKeepsTheEventId() {
        val intent = Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, 42))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, at(15, date = day))
        assertEquals(ViewTarget(day, 42), EventView.targetOf(intent, WidgetFixtures.zone))
    }

    @Test
    fun insertIntentIsNotAViewTarget() {
        assertNull(EventView.targetOf(Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI), WidgetFixtures.zone))
    }
}
