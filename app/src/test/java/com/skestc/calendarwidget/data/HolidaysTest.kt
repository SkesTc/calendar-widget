package com.skestc.calendarwidget.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HolidaysTest {

    @Test
    fun detectsGoogleHolidayCalendars() {
        assertTrue(Holidays.isHolidayCalendar("zh-tw.taiwan#holiday@group.v.calendar.google.com", null))
        assertTrue(Holidays.isHolidayCalendar(null, "en.taiwan#holiday@group.v.calendar.google.com"))
        assertFalse(Holidays.isHolidayCalendar("someone@gmail.com", "someone@gmail.com"))
        assertFalse(Holidays.isHolidayCalendar(null, null))
    }

    @Test
    fun classifiesHolidayEvents() {
        assertEquals(HolidayKind.DAY_OFF, Holidays.classify("中秋節", "國定假日"))
        assertEquals(HolidayKind.DAY_OFF, Holidays.classify("國慶日", null))
        assertEquals(HolidayKind.MAKEUP_WORKDAY, Holidays.classify("補行上班", "國定假日"))
        assertEquals(HolidayKind.OBSERVANCE, Holidays.classify("母親節", "紀念日\n如要隱藏紀念日，請前往 Google 日曆設定"))
        assertEquals(HolidayKind.OBSERVANCE, Holidays.classify("Mother's Day", "Observance\nTo hide observances…"))
    }
}
