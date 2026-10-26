package com.skestc.calendarwidget.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import androidx.glance.testing.unit.hasTextEqualTo
import com.skestc.calendarwidget.data.AgendaFormatter
import com.skestc.calendarwidget.data.HolidayKind
import com.skestc.calendarwidget.widget.WidgetFixtures.allDay
import com.skestc.calendarwidget.widget.WidgetFixtures.at
import com.skestc.calendarwidget.widget.WidgetFixtures.context
import com.skestc.calendarwidget.widget.WidgetFixtures.labeler
import com.skestc.calendarwidget.widget.WidgetFixtures.timed
import com.skestc.calendarwidget.widget.WidgetFixtures.today
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rTW")
class AgendaWidgetUiTest {

    @Before
    fun setUp() = WidgetFixtures.useTaiwanDefaults()

    /** 固定「現在」是早上 8 點，結果不受 CI 執行時間影響。 */
    private fun loaded(vararg events: com.skestc.calendarwidget.data.CalendarEvent, now: Long = at(8)): AgendaState {
        val rows = AgendaFormatter(context, WidgetFixtures.zone, Locale.TAIWAN).rows(events.toList(), today, labeler, now)
        return AgendaState.Loaded(today, days = 7, rows = rows, lunarToday = "九月十七")
    }

    @Test
    fun showsEventsGroupedUnderDateHeaders() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(250.dp, 250.dp))
        provideComposable {
            WidgetFrame {
                AgendaContent(
                    loaded(
                        timed(1, "晨會", at(9, 30), at(10), location = "會議室 A"),
                        timed(2, "牙醫", at(15, date = today.plusDays(1)), at(16, date = today.plusDays(1))),
                    ),
                )
            }
        }
        onNode(hasTextEqualTo("晨會")).assertExists()
        onNode(hasText("會議室 A")).assertExists()
        onNode(hasTextEqualTo("牙醫")).assertExists()
        onNode(hasText("今天 ·")).assertExists()
        onNode(hasText("明天 ·")).assertExists()
        // 大字日期與標題下方的農曆。
        onNode(hasTextEqualTo("26")).assertExists()
        // 頂端日期下方一次，加上「今天」「明天」兩個日期標題各一次。
        onAllNodes(hasText("九月十七")).assertCountEquals(3)
    }

    @Test
    fun holidayGoesIntoHeaderInsteadOfAnEventCard() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(250.dp, 250.dp))
        provideComposable {
            WidgetFrame {
                AgendaContent(loaded(allDay(1, "光復節", today.plusDays(2), HolidayKind.DAY_OFF)))
            }
        }
        onNode(hasText("光復節")).assertExists()
        onNode(hasTextEqualTo("光復節")).assertDoesNotExist()
    }

    @Test
    fun eventsThatEndedTodayStayVisible() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(250.dp, 250.dp))
        provideComposable {
            WidgetFrame {
                AgendaContent(loaded(timed(1, "晨會", at(9), at(10)), timed(2, "午餐", at(12), at(13)), now = at(15)))
            }
        }
        // 已結束的行程用淡色顯示，但不隱藏。
        onNode(hasTextEqualTo("晨會")).assertExists()
        onNode(hasTextEqualTo("午餐")).assertExists()
    }

    @Test
    fun smallSizeSkipsEventsThatAlreadyEnded() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(110.dp, 110.dp))
        provideComposable {
            WidgetFrame {
                AgendaContent(loaded(timed(1, "晨會", at(9), at(10)), timed(2, "午餐", at(12), at(13)), now = at(11)))
            }
        }
        onNode(hasTextEqualTo("午餐")).assertExists()
        onNode(hasTextEqualTo("晨會")).assertDoesNotExist()
    }

    @Test
    fun smallSizeShowsOnlyTheNextEvent() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(110.dp, 110.dp))
        provideComposable {
            WidgetFrame {
                AgendaContent(loaded(timed(1, "晨會", at(9), at(10)), timed(2, "午餐", at(12), at(13))))
            }
        }
        onNode(hasTextEqualTo("晨會")).assertExists()
        onNode(hasTextEqualTo("午餐")).assertDoesNotExist()
    }

    @Test
    fun emptyStateMentionsTheRange() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(250.dp, 250.dp))
        provideComposable {
            WidgetFrame { AgendaContent(AgendaState.Loaded(today, days = 14, rows = emptyList(), lunarToday = null)) }
        }
        onNode(hasText("接下來 14 天沒有行程")).assertExists()
    }

    @Test
    fun askForPermissionWhenNotGranted() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(250.dp, 250.dp))
        provideComposable { WidgetFrame { AgendaContent(AgendaState.NoPermission) } }
        onNode(hasTextEqualTo("點此授權讀取日曆")).assertExists()
    }
}
