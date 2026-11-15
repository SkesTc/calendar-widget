package com.skestc.calendarwidget.ui

import android.appwidget.AppWidgetManager
import android.os.Bundle
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.skestc.calendarwidget.ui.theme.CalendarWidgetTheme
import com.skestc.calendarwidget.widget.InstantRender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun instantRenderUsesThePortraitSizeFromOptions() {
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 300)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 420)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 280)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 400)
        }
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertEquals(DpSize(300.dp, 400.dp), InstantRender.currentSize(context, options))
        assertNull(InstantRender.currentSize(context, Bundle()))
    }
}
