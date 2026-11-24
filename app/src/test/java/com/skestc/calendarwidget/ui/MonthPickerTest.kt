package com.skestc.calendarwidget.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.skestc.calendarwidget.ui.theme.CalendarWidgetTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rTW")
class MonthPickerTest {

    @get:Rule
    val rule = createComposeRule()

    private var picked: YearMonth? = null

    private fun show(shown: YearMonth) {
        rule.setContent {
            CalendarWidgetTheme {
                MonthPickerDialog(shown, thisMonth = YearMonth.of(2026, 11), onPick = { picked = it }, onDismiss = {})
            }
        }
    }

    @Test
    fun pickAMonthInAnotherYear() {
        show(YearMonth.of(2026, 11))
        rule.onNodeWithText("2026 年").assertExists()
        rule.onNodeWithContentDescription("下一年").performClick()
        rule.onNodeWithText("2027 年").assertExists()
        rule.onNodeWithText("3月").performClick()
        assertEquals(YearMonth.of(2027, 3), picked)
    }

    @Test
    fun backToThisMonth() {
        show(YearMonth.of(2027, 5))
        rule.onNodeWithText("回到本月").performClick()
        assertEquals(YearMonth.of(2026, 11), picked)
    }
}
