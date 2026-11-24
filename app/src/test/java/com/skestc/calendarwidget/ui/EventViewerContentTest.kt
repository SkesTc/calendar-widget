package com.skestc.calendarwidget.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.skestc.calendarwidget.data.HolidayKind
import com.skestc.calendarwidget.data.ViewDay
import com.skestc.calendarwidget.ui.theme.CalendarWidgetTheme
import com.skestc.calendarwidget.widget.WidgetFixtures
import com.skestc.calendarwidget.widget.WidgetFixtures.allDay
import com.skestc.calendarwidget.widget.WidgetFixtures.at
import com.skestc.calendarwidget.widget.WidgetFixtures.timed
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rTW-w411dp-h2000dp")
class EventViewerContentTest {

    @get:Rule
    val rule = createComposeRule()

    @Before
    fun setUp() = WidgetFixtures.useTaiwanDefaults()

    private val day = LocalDate.of(2030, 11, 18)

    @Test
    fun showsDaysHolidaysAndEvents() {
        val days = listOf(
            ViewDay(day, emptyList(), listOf(timed(1, "晨會", at(9, date = day), at(10, date = day), location = "會議室"))),
            ViewDay(day.plusDays(1), emptyList(), emptyList()),
            ViewDay(
                day.plusDays(2),
                listOf(allDay(2, "國慶日", day.plusDays(2), HolidayKind.DAY_OFF)),
                listOf(allDay(3, "運動會", day.plusDays(2))),
            ),
        )
        rule.setContent { CalendarWidgetTheme { EventViewerContent(days, highlight = null) } }
        rule.onNodeWithText("11月18日 星期一").assertExists()
        rule.onNodeWithText("晨會").assertExists()
        rule.onNodeWithText("沒有行程").assertExists()
        rule.onNodeWithText("國慶日").assertExists()
        rule.onNodeWithText("運動會").assertExists()
    }

    @Test
    fun emptyPeriodSaysSo() {
        rule.setContent { CalendarWidgetTheme { EventViewerContent(emptyList(), highlight = null) } }
        rule.onNodeWithText("這段期間沒有行程").assertExists()
    }
}
