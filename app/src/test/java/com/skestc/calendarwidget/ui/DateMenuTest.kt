package com.skestc.calendarwidget.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.skestc.calendarwidget.data.ViewMode
import com.skestc.calendarwidget.ui.theme.CalendarWidgetTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 點行程清單的日期、月曆的月份時跳出的選單。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rTW")
class DateMenuTest {

    @get:Rule
    val rule = createComposeRule()

    private var viewed: ViewMode? = null
    private var calendarApp = false
    private var picked = false
    private var thisMonth = false

    private fun show(isMonth: Boolean, monthOffset: Int = 0) {
        rule.setContent {
            CalendarWidgetTheme {
                DateMenu(
                    isMonth = isMonth,
                    monthOffset = monthOffset,
                    canChangeMonth = true,
                    onViewer = { viewed = it },
                    onCalendarApp = { calendarApp = true },
                    onPickMonth = { picked = true },
                    onThisMonth = { thisMonth = true },
                    onAdd = {},
                    onSearch = {},
                    onDismiss = {},
                )
            }
        }
    }

    @Test
    fun agendaMenuOffersTodayWeekAndTheCalendarApp() {
        show(isMonth = false)
        rule.onNodeWithText("今天的行程").performClick()
        assertEquals(ViewMode.DAY, viewed)
        rule.onNodeWithText("本週的行程").performClick()
        assertEquals(ViewMode.WEEK, viewed)
        // 沒選過時預設 Google 日曆。
        rule.onNodeWithText("開啟 Google 日曆").performClick()
        assertTrue(calendarApp)
        rule.onNodeWithText("選擇月份…").assertDoesNotExist()
    }

    @Test
    fun monthMenuPicksMonthsAndShowsThisMonthWhenAway() {
        show(isMonth = true, monthOffset = 2)
        rule.onNodeWithText("選擇月份…").performClick()
        assertTrue(picked)
        rule.onNodeWithText("回到本月").performClick()
        assertTrue(thisMonth)
        rule.onNodeWithText("這個月的行程").performClick()
        assertEquals(ViewMode.MONTH, viewed)
    }

    @Test
    fun monthMenuHidesThisMonthWhenAlreadyThere() {
        show(isMonth = true)
        rule.onNodeWithText("回到本月").assertDoesNotExist()
        rule.onNodeWithText("新增行程").assertExists()
        rule.onNodeWithText("搜尋行程").assertExists()
    }
}
