package com.skestc.calendarwidget.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasTextEqualTo
import com.skestc.calendarwidget.data.HolidayKind
import com.skestc.calendarwidget.data.WeekBuilder
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rTW")
class WeekWidgetUiTest {

    @Before
    fun setUp() = WidgetFixtures.useTaiwanDefaults()

    private fun week() = WeekState.Loaded(
        today,
        WeekBuilder(WidgetFixtures.zone).build(
            listOf(
                timed(1, "早會", at(8), at(9)),
                timed(2, "午餐", at(12), at(13)),
                timed(3, "健身", at(18), at(19)),
                timed(4, "讀書", at(21), at(22)),
                allDay(5, "某假日", today.plusDays(3), HolidayKind.DAY_OFF),
            ),
            today,
            maxPerDay = 4,
            labeler = labeler,
        ),
    )

    @Test
    fun shortWidgetShowsTwoPerDayAndOverflow() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(300.dp, 120.dp))
        provideComposable { WidgetFrame { WeekContent(week()) } }
        onNode(hasTextEqualTo("早會")).assertExists()
        onNode(hasTextEqualTo("午餐")).assertExists()
        onNode(hasTextEqualTo("健身")).assertDoesNotExist()
        onNode(hasTextEqualTo("+2")).assertExists()
        // 太矮時不顯示農曆／假日小字。
        onNode(hasTextEqualTo("某假日")).assertDoesNotExist()
        // 7 天：26 日到 11/1。
        onNode(hasTextEqualTo("26")).assertExists()
        onNode(hasTextEqualTo("1")).assertExists()
    }

    @Test
    fun tallWidgetShowsMoreAndLabels() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(300.dp, 220.dp))
        provideComposable { WidgetFrame { WeekContent(week()) } }
        onNode(hasTextEqualTo("讀書")).assertExists()
        onNode(hasTextEqualTo("+2")).assertDoesNotExist()
        onNode(hasTextEqualTo("某假日")).assertExists()
        onAllNodes(hasTextEqualTo("十七")).assertCountEquals(6)
    }
}
