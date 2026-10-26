package com.skestc.calendarwidget.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import androidx.glance.testing.unit.hasTextEqualTo
import com.skestc.calendarwidget.data.NextEvent
import com.skestc.calendarwidget.widget.WidgetFixtures.allDay
import com.skestc.calendarwidget.widget.WidgetFixtures.at
import com.skestc.calendarwidget.widget.WidgetFixtures.context
import com.skestc.calendarwidget.widget.WidgetFixtures.timed
import com.skestc.calendarwidget.widget.WidgetFixtures.today
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NextEventWidgetUiTest {

    @Before
    fun setUp() = WidgetFixtures.useTaiwanDefaults()

    @Test
    fun inProgressEvent() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(180.dp, 40.dp))
        provideComposable {
            WidgetFrame {
                NextEventContent(NextEventState.Loaded(today, NextEvent(timed(1, "晨會", at(9), at(10), location = "會議室 A"), true)))
            }
        }
        onNode(hasTextEqualTo("晨會")).assertExists()
        onNode(hasText("進行中 · 到")).assertExists()
        // 1 格高時不顯示地點。
        onNode(hasTextEqualTo("會議室 A")).assertDoesNotExist()
    }

    @Test
    fun tallWidgetShowsLocation() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(180.dp, 110.dp))
        provideComposable {
            WidgetFrame {
                NextEventContent(NextEventState.Loaded(today, NextEvent(timed(1, "晨會", at(9), at(10), location = "會議室 A"), false)))
            }
        }
        onNode(hasTextEqualTo("會議室 A")).assertExists()
    }

    @Test
    fun tomorrowAndAllDayLabels() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(180.dp, 40.dp))
        provideComposable {
            WidgetFrame {
                NextEventContent(NextEventState.Loaded(today, NextEvent(allDay(1, "旅行", today.plusDays(1)), false)))
            }
        }
        onNode(hasTextEqualTo("明天 · 全天")).assertExists()
    }

    @Test
    fun nothingUpcoming() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(180.dp, 40.dp))
        provideComposable { WidgetFrame { NextEventContent(NextEventState.Loaded(today, null)) } }
        onNode(hasTextEqualTo("接下來 7 天沒有行程")).assertExists()
    }
}
