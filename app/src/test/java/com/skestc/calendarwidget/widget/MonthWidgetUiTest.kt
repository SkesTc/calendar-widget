package com.skestc.calendarwidget.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import androidx.glance.testing.unit.hasText
import androidx.glance.testing.unit.hasTextEqualTo
import com.skestc.calendarwidget.data.HolidayKind
import com.skestc.calendarwidget.data.MonthBuilder
import com.skestc.calendarwidget.settings.Appearance
import com.skestc.calendarwidget.theme.WidgetThemeId
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
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rTW")
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
        onNode(hasText("10月")).assertExists()
        onNode(hasContentDescriptionEqualTo("上個月")).assertExists()
        onNode(hasContentDescriptionEqualTo("下個月")).assertExists()
        // 300dp 寬：標題列放得下搜尋按鈕。
        onNode(hasContentDescriptionEqualTo("搜尋行程")).assertExists()
        // 300dp：標題只寫月份，把空間讓給按鈕。
        onNode(hasTextEqualTo("10月")).assertExists()
        // 300dp：只留搜尋，重新整理與設定收進「⋯」。
        onNode(hasContentDescriptionEqualTo("更多")).assertExists()
        onNode(hasTextEqualTo("國慶日")).assertExists()
        onNode(hasTextEqualTo("補班")).assertExists()
        // 節氣是真的天文計算：2026/10/8 寒露、10/23 霜降，會取代農曆小字。
        onNode(hasTextEqualTo("寒露")).assertExists()
        onNode(hasTextEqualTo("霜降")).assertExists()
        // 假農曆每天都是十七；10 月格子共 35 天，扣掉 2 個假日與 2 個節氣。
        onAllNodes(hasTextEqualTo("十七")).assertCountEquals(31)
    }

    @Test
    fun compactMonthHidesLabels() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(180.dp, 180.dp))
        provideComposable { WidgetFrame { MonthContent(october()) } }
        onNode(hasTextEqualTo("國慶日")).assertDoesNotExist()
        onNode(hasTextEqualTo("十七")).assertDoesNotExist()
        onNode(hasTextEqualTo("31")).assertExists()
    }

    @Test
    fun tallMonthShowsSelectedDayEvents() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(300.dp, 420.dp))
        val selected = LocalDate.of(2026, 10, 28)
        val state = (october() as MonthState.Loaded).copy(
            selected = selected,
            selectedEvents = listOf(
                timed(1, "牙醫", at(15, date = selected), at(16, date = selected)),
                allDay(2, "旅行", selected),
            ),
        )
        provideComposable { WidgetFrame { MonthContent(state) } }
        onNode(hasText("牙醫")).assertExists()
        onNode(hasText("旅行")).assertExists()
        onNode(hasText("10月28日")).assertExists()
        onNode(hasTextEqualTo("開啟 ›")).assertExists()
    }

    @Test
    fun busyDayGrowsThePanelBelow() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(300.dp, 420.dp))
        val selected = LocalDate.of(2026, 10, 28)
        val state = (october() as MonthState.Loaded).copy(
            selected = selected,
            selectedEvents = (1..10).map { timed(it.toLong(), "行程$it", at(8, date = selected), at(9, date = selected)) },
        )
        provideComposable { WidgetFrame { MonthContent(state) } }
        // 下方區塊最多占約一半高度：420dp 時放 6 筆，再加「+4」。
        onNode(hasText("行程6")).assertExists()
        onNode(hasText("行程7")).assertDoesNotExist()
        onNode(hasTextEqualTo("+4")).assertExists()
    }

    @Test
    fun selectedDayWithoutEventsSaysSo() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(300.dp, 420.dp))
        provideComposable { WidgetFrame { MonthContent(october()) } }
        onNode(hasTextEqualTo("沒有行程")).assertExists()
    }

    @Test
    fun shorterMonthHasNoDayPanel() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(300.dp, 320.dp))
        provideComposable { WidgetFrame { MonthContent(october()) } }
        onNode(hasTextEqualTo("開啟 ›")).assertDoesNotExist()
    }

    @Test
    fun wideMonthShowsTheDayPanelBesideTheGrid() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(520.dp, 300.dp))
        val selected = LocalDate.of(2026, 10, 28)
        val state = (october() as MonthState.Loaded).copy(
            selected = selected,
            selectedEvents = (1..15).map { timed(it.toLong(), "行程$it", at(8, date = selected), at(9, date = selected)) },
        )
        provideComposable { WidgetFrame { MonthContent(state) } }
        onNode(hasTextEqualTo("開啟 ›")).assertExists()
        // 300dp 高的右側區塊：放得下 11 筆，再加一行「+4」。
        onNode(hasText("行程11")).assertExists()
        onNode(hasText("行程12")).assertDoesNotExist()
        onNode(hasTextEqualTo("+4")).assertExists()
        onNode(hasTextEqualTo("國慶日")).assertExists()
        // 夠寬：搜尋、重新整理、設定各一顆。
        onNode(hasContentDescriptionEqualTo("重新整理")).assertExists()
        onNode(hasContentDescriptionEqualTo("小工具設定")).assertExists()
    }

    @Test
    fun customThemeRenders() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(300.dp, 320.dp))
        val appearance = Appearance(theme = WidgetThemeId.CUSTOM, customPrimary = 0xFFE67C73, customBackground = 0xFF1B2A41)
        provideComposable { WidgetFrame(appearance) { MonthContent(october()) } }
        onNode(hasTextEqualTo("國慶日")).assertExists()
        onNode(hasTextEqualTo("31")).assertExists()
    }

    @Test
    fun narrowerMonthStillFitsSearch() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(260.dp, 320.dp))
        provideComposable { WidgetFrame { MonthContent(october()) } }
        // 放不下搜尋與設定兩顆：合併成「⋯」，其他按鈕不會被擠出畫面。
        onNode(hasContentDescriptionEqualTo("更多")).assertExists()
        onNode(hasContentDescriptionEqualTo("搜尋行程")).assertDoesNotExist()
        onNode(hasContentDescriptionEqualTo("下個月")).assertExists()
        onNode(hasContentDescriptionEqualTo("新增行程")).assertExists()
    }

    @Test
    fun schoolWeekNumbersAppearBesideEachWeek() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(300.dp, 320.dp))
        val state = (october() as MonthState.Loaded).copy(schoolStart = LocalDate.of(2026, 9, 1))
        provideComposable { WidgetFrame { MonthContent(state) } }
        // 10/26 那一週是第 9 週：「9」出現兩次（10/9 的日期與週次）。
        onAllNodes(hasTextEqualTo("9")).assertCountEquals(2)
    }

    @Test
    fun otherMonthShowsBackToTodayButton() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(300.dp, 320.dp))
        provideComposable { WidgetFrame { MonthContent(october(monthOffset = 1)) } }
        // 不在本月時沒有額外的「今」按鈕，點標題就回到本月。
        onNode(hasTextEqualTo("今")).assertDoesNotExist()
        onNode(hasTextEqualTo("10月")).assertExists()
    }
}
