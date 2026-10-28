package com.skestc.calendarwidget.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.skestc.calendarwidget.ui.theme.CalendarWidgetTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rTW")
class OnboardingScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private var grants = 0
    private var finished = false

    private fun show(granted: Boolean, hasHoliday: Boolean = false) {
        rule.setContent {
            CalendarWidgetTheme {
                OnboardingScreen(
                    modifier = Modifier,
                    granted = granted,
                    hasHolidayCalendar = hasHoliday,
                    onGrant = { grants++ },
                    onFinish = { finished = true },
                    widgetGallery = { Text("小工具清單") },
                )
            }
        }
    }

    @Test
    fun firstStepAsksForPermission() {
        show(granted = false)
        rule.onNodeWithText("第 1 步，共 3 步").assertIsDisplayed()
        rule.onNodeWithText("歡迎使用行事曆小工具").assertIsDisplayed()
        rule.onNodeWithText("授權讀取日曆").performClick()
        assertEquals(1, grants)
    }

    @Test
    fun alreadyGrantedWalksThroughWidgetsAndHolidays() {
        show(granted = true)
        rule.onNodeWithText("第 2 步，共 3 步").assertIsDisplayed()
        rule.onNodeWithText("小工具清單").assertIsDisplayed()

        rule.onNodeWithText("下一步").performClick()
        rule.onNodeWithText("第 3 步，共 3 步").assertIsDisplayed()
        rule.onNodeWithText("顯示國定假日與補班日").assertIsDisplayed()

        // 上一步回到加小工具。
        rule.onNodeWithText("上一步").performClick()
        rule.onNodeWithText("第 2 步，共 3 步").assertIsDisplayed()

        rule.onNodeWithText("下一步").performClick()
        rule.onNodeWithText("開始使用").performClick()
        assertTrue(finished)
    }

    @Test
    fun skipFinishesRightAway() {
        show(granted = true)
        rule.onNodeWithText("略過").performClick()
        assertTrue(finished)
    }

    @Test
    fun grantingPermissionAdvancesAutomatically() {
        val granted = mutableStateOf(false)
        rule.setContent {
            CalendarWidgetTheme {
                OnboardingScreen(
                    modifier = Modifier,
                    granted = granted.value,
                    hasHolidayCalendar = true,
                    onGrant = { granted.value = true },
                    onFinish = {},
                    widgetGallery = {},
                )
            }
        }
        rule.onNodeWithText("授權讀取日曆").performClick()
        rule.onNodeWithText("第 2 步，共 3 步").assertIsDisplayed()

        rule.onNodeWithText("下一步").performClick()
        rule.onNodeWithText("已經訂閱節慶假日日曆，放假日與補班日會標在小工具上。").assertIsDisplayed()
    }
}
