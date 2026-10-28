package com.skestc.calendarwidget.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import androidx.glance.testing.unit.hasText
import androidx.glance.testing.unit.hasTextEqualTo
import com.skestc.calendarwidget.data.AgendaFormatter
import com.skestc.calendarwidget.settings.AgendaLayout
import com.skestc.calendarwidget.data.HolidayKind
import com.skestc.calendarwidget.widget.WidgetFixtures.allDay
import com.skestc.calendarwidget.widget.WidgetFixtures.at
import com.skestc.calendarwidget.widget.WidgetFixtures.context
import com.skestc.calendarwidget.widget.WidgetFixtures.labeler
import com.skestc.calendarwidget.widget.WidgetFixtures.timed
import com.skestc.calendarwidget.widget.WidgetFixtures.today
import org.junit.Assert.assertEquals
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
    fun daysLayoutListsEachDayWithLunarAndEvents() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(300.dp, 400.dp))
        val events = listOf(
            timed(1, "晨會", at(9), at(10)),
            timed(2, "牙醫", at(15, date = today.plusDays(2)), at(16, date = today.plusDays(2))),
            timed(3, "遠足", at(8, date = today.plusDays(20)), at(12, date = today.plusDays(20))),
            timed(4, "115學年度校慶運動會籌備會議暨各處室工作分配說明", at(13), at(14)),
        )
        val state = AgendaState.Loaded(
            today, days = 31, rows = emptyList(), lunarToday = null,
            layout = AgendaLayout.DAYS, now = at(8),
            dayRows = dayRowsOf(events, today, 31, labeler),
        )
        provideComposable { WidgetFrame { AgendaContent(state) } }
        onNode(hasText("晨會")).assertExists()
        onNode(hasText("牙醫")).assertExists()
        onNode(hasText("遠足")).assertExists()
        // 前 7 天沒有行程的日子也列出來，顯示農曆。
        onNode(hasNumber("27")).assertExists()
        // 長標題完整顯示（換行），不截掉年度前綴。
        onNode(hasText("115學年度校慶運動會籌備會議暨各處室工作分配說明")).assertExists()
    }

    @Test
    fun daysLayoutSkipsEmptyDaysAfterTheFirstWeek() {
        val rows = dayRowsOf(listOf(timed(3, "遠足", at(8, date = today.plusDays(20)), at(9, date = today.plusDays(20)))), today, 31, null)
        assertEquals(8, rows.size)
        assertEquals(today.plusDays(20), rows.last().date)
    }

    @Test
    fun timelineLayoutShowsNowLineAndAllDayColumn() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(250.dp, 300.dp))
        val now = at(10)
        val state = (
            loaded(
                timed(1, "晨會", at(9), at(9, 30)),
                timed(2, "午餐", at(12), at(13)),
                allDay(3, "旅行", today.plusDays(1)),
                now = now,
            ) as AgendaState.Loaded
            ).copy(layout = AgendaLayout.TIMELINE, now = now)
        provideComposable { WidgetFrame { AgendaContent(state) } }
        onNode(hasTextEqualTo("晨會")).assertExists()
        onNode(hasTextEqualTo("午餐")).assertExists()
        onNode(hasTextEqualTo("旅行")).assertExists()
        onNode(hasTextEqualTo("現在")).assertExists()
        // 全天事件在時間欄顯示「全天」。
        onNode(hasTextEqualTo("全天")).assertExists()
        onNode(hasText("今天 ·")).assertExists()
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
        onNode(hasNumber("26")).assertExists()
        // 250dp 寬放不下搜尋與設定兩顆，合併成「⋯」。
        onNode(hasContentDescriptionEqualTo("更多")).assertExists()
        onNode(hasContentDescriptionEqualTo("新增行程")).assertExists()
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
