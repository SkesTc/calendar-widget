package com.skestc.calendarwidget.settings

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EventEditorTest {

    private val target = EventEditor.insertIntent()

    private val zone = java.time.ZoneId.of("Asia/Taipei")

    private fun millis(date: java.time.LocalDate, hour: Int) = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun insertOnAnotherDayStartsAtNine() {
        val now = java.time.LocalDateTime.of(2026, 11, 4, 14, 20)
        val day = java.time.LocalDate.of(2026, 11, 20)
        val intent = EventEditor.insertIntent(day, now, zone)
        assertEquals(millis(day, 9), intent.getLongExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, 0))
        assertEquals(millis(day, 10), intent.getLongExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, 0))
    }

    @Test
    fun insertTodayStartsAtTheNextHour() {
        val now = java.time.LocalDateTime.of(2026, 11, 4, 14, 20)
        val intent = EventEditor.insertIntent(now.toLocalDate(), now, zone)
        assertEquals(millis(now.toLocalDate(), 15), intent.getLongExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, 0))
    }

    @Test
    fun fallbackIntentsKeepTheChosenDay() {
        val dated = EventEditor.insertIntent(java.time.LocalDate.of(2026, 11, 20), java.time.LocalDateTime.of(2026, 11, 4, 8, 0), zone)
        val intents = EventEditor.launchIntents(null, dated, "t")
        // 「單筆事件」類型的備援也要帶著開始時間。
        assertEquals(
            millis(java.time.LocalDate.of(2026, 11, 20), 9),
            intents[1].getLongExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, 0),
        )
    }

    @Test
    fun defaultPrefersGoogleCalendarThenTheSystem() {
        val intents = EventEditor.launchIntents(null, target, "t")
        assertEquals(EventEditor.GOOGLE_CALENDAR, intents[0].`package`)
        assertEquals(Intent.ACTION_INSERT, intents[0].action)
        assertEquals(EventEditor.GOOGLE_CALENDAR, intents[1].`package`)
        assertEquals("vnd.android.cursor.item/event", intents[1].type)
        assertNull(intents.last().`package`)
    }

    @Test
    fun askShowsTheChooserThenFallsBack() {
        val intents = EventEditor.launchIntents(EventEditor.ASK, target, "t")
        assertEquals(Intent.ACTION_CHOOSER, intents[0].action)
        assertNull(intents[1].`package`)
    }

    @Test
    fun chosenAppComesFirstWithSystemDefaultAsFallback() {
        val intents = EventEditor.launchIntents("com.example.cal", target, "t")
        assertEquals("com.example.cal", intents[0].`package`)
        assertEquals(Intent.ACTION_INSERT, intents[0].action)
        assertEquals("com.example.cal", intents[1].`package`)
        assertNull(intents.last().`package`)
    }

    @Test
    fun choiceIsStoredAndCleared() {
        val context = RuntimeEnvironment.getApplication()
        EventEditor.setChoice(context, "com.example.cal")
        assertEquals("com.example.cal", EventEditor.choice(context))
        EventEditor.setChoice(context, null)
        assertNull(EventEditor.choice(context))
    }
}
