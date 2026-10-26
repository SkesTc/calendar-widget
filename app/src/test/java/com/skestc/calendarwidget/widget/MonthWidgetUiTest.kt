package com.skestc.calendarwidget.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import com.skestc.calendarwidget.data.HolidayKind
import com.skestc.calendarwidget.data.MonthBuilder
import com.skestc.calendarwidget.widget.WidgetFixtures.allDay
import com.skestc.calendarwidget.widget.WidgetFixtures.context
import com.skestc.calendarwidget.widget.WidgetFixtures.labeler
import com.skestc.calendarwidget.widget.WidgetFixtures.today
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MonthWidgetUiTest {

    @Before
    fun setUp() = WidgetFixtures.useTaiwanDefaults()

    private fun october(monthOffset: Int = 0): MonthState {
        val events = listOf(
            allDay(1, "國慶日", LocalDate.of(2026, 10, 10), HolidayKind.DAY_OFF),
            allDay(2, "補行上班", LocalDate.of(2026, 10, 17), HolidayKind.MAKEUP_WORKDAY),
        )
        val data = MonthBuilder(WidgetFixtures.zone, Locale.TAIWAN).build(YearMonth.of(2026, 10), today, events, labeler)
        return MonthState.Loaded(data, monthOffset)
    }

    @Test
    fun largeMonthShowsTitleNavigationAndLabels() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(300.dp, 320.dp))
        provideComposable { WidgetFrame { MonthContent(october()) } }
        onNode(hasText("10月", substring = true)).assertExists()
        onNode(hasText("‹")).assertExists()
        onNode(hasText("›")).assertExists()
        // 在本月時不顯示「今」。
        onNode(hasText("今")).assertDoesNotExist()
        onNode(hasText("國慶日")).assertExists()
        onNode(hasText("補班")).assertExists()
        // 假農曆每天都是十七；10 月格子共 35 天，其中 2 天顯示假日名稱。
        onAllNodes(hasText("十七")).assertCountEquals(33)
    }

    @Test
    fun compactMonthHidesLabels() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(180.dp, 180.dp))
        provideComposable { WidgetFrame { MonthContent(october()) } }
        onNode(hasText("國慶日")).assertDoesNotExist()
        onNode(hasText("十七")).assertDoesNotExist()
        onNode(hasText("31")).assertExists()
    }

    @Test
    fun otherMonthShowsBackToTodayButton() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(300.dp, 320.dp))
        provideComposable { WidgetFrame { MonthContent(october(monthOffset = 1)) } }
        onNode(hasText("今")).assertExists()
    }
}
