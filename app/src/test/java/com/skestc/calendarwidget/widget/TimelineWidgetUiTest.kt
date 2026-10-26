package com.skestc.calendarwidget.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import com.skestc.calendarwidget.data.CalendarEvent
import com.skestc.calendarwidget.data.HolidayKind
import com.skestc.calendarwidget.data.TimelineBuilder
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
class TimelineWidgetUiTest {

    @Before
    fun setUp() = WidgetFixtures.useTaiwanDefaults()

    private fun state(now: Long, vararg events: CalendarEvent) =
        TimelineState.Loaded(today, "九月十七", TimelineBuilder(WidgetFixtures.zone).build(events.toList(), today, now))

    @Test
    fun showsNowMarkerAndGap() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(250.dp, 300.dp))
        provideComposable {
            WidgetFrame {
                TimelineContent(state(at(12), timed(1, "晨會", at(9), at(10)), timed(2, "專案討論", at(15), at(16))))
            }
        }
        onNode(hasText("晨會")).assertExists()
        onNode(hasText("專案討論")).assertExists()
        onNode(hasText("現在")).assertExists()
        onNode(hasText("空檔 3 小時")).assertExists()
        onNode(hasText("今天 ·", substring = true)).assertExists()
        onNode(hasText("九月十七")).assertExists()
    }

    @Test
    fun currentEventShowsInProgress() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(250.dp, 300.dp))
        provideComposable {
            WidgetFrame { TimelineContent(state(at(9, 30), timed(1, "晨會", at(9), at(10), location = "線上"))) }
        }
        onNode(hasText("進行中 · 到", substring = true)).assertExists()
        // 有進行中的行程時不顯示「現在」。
        onNode(hasText("現在")).assertDoesNotExist()
    }

    @Test
    fun allDayStripListsObservancesButNotDaysOff() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(250.dp, 300.dp))
        provideComposable {
            WidgetFrame {
                TimelineContent(
                    state(
                        at(8),
                        allDay(1, "生日"),
                        allDay(2, "某紀念日", holiday = HolidayKind.OBSERVANCE),
                        allDay(3, "某放假日", holiday = HolidayKind.DAY_OFF),
                    ),
                )
            }
        }
        onNode(hasText("全天：", substring = true)).assertExists()
        onNode(hasText("生日", substring = true)).assertExists()
        onNode(hasText("某紀念日", substring = true)).assertExists()
        onNode(hasText("某放假日", substring = true)).assertDoesNotExist()
        onNode(hasText("今天沒有其他排定時間的行程")).assertExists()
    }

    @Test
    fun emptyDay() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(250.dp, 300.dp))
        provideComposable { WidgetFrame { TimelineContent(state(at(8))) } }
        onNode(hasText("今天沒有行程", substring = true)).assertExists()
    }
}
