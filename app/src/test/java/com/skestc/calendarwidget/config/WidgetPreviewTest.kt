package com.skestc.calendarwidget.config

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.skestc.calendarwidget.settings.AgendaLayout
import com.skestc.calendarwidget.settings.Appearance
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 設定頁的預覽要跟著選的版面變。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rTW")
class WidgetPreviewTest {

    @get:Rule
    val rule = createComposeRule()

    private fun show(layout: AgendaLayout) {
        rule.setContent {
            WidgetPreview(Appearance(), WidgetKind.AGENDA, darkTheme = false, agendaLayout = layout)
        }
    }

    @Test
    fun listLayoutHasNoNowMarker() {
        show(AgendaLayout.LIST)
        rule.onNodeWithText("今天").assertExists()
        rule.onNodeWithText("晨會").assertExists()
        rule.onNodeWithText("現在").assertDoesNotExist()
    }

    @Test
    fun timelineLayoutShowsNowMarker() {
        show(AgendaLayout.TIMELINE)
        rule.onNodeWithText("現在").assertExists()
    }

    @Test
    fun daysLayoutRendersEvents() {
        show(AgendaLayout.DAYS)
        // 每天一列：沒有「今天」標題，行程寫成「時間  標題」。
        rule.onNodeWithText("今天").assertDoesNotExist()
        rule.onNodeWithText("現在").assertDoesNotExist()
        assertTrue(rule.onAllNodesWithText("晨會", substring = true).fetchSemanticsNodes().isNotEmpty())
    }
}
