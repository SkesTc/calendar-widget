package com.skestc.calendarwidget.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HiddenEventsTest {

    private val store = HiddenEvents(ApplicationProvider.getApplicationContext())

    @Test
    fun hideAndUnhideKeepsTitles() {
        store.setHidden(42, "115學年度第一學期", hidden = true)
        store.setHidden(7, "寒假", hidden = true)
        assertEquals(mapOf(42L to "115學年度第一學期", 7L to "寒假"), store.all())
        assertEquals(setOf(42L, 7L), store.ids())

        store.setHidden(42, "115學年度第一學期", hidden = false)
        assertEquals(mapOf(7L to "寒假"), store.all())
    }

    @Test
    fun startsEmpty() {
        assertEquals(emptySet<Long>(), store.ids())
    }
}
