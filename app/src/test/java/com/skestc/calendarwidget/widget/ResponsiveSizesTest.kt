package com.skestc.calendarwidget.widget

import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * SizeMode.Responsive 只會用宣告過的尺寸畫版面（LocalSize 不是 widget 的實際大小），
 * 所以版面裡每個依大小切換的門檻，都必須有一個剛好達到門檻的尺寸，否則實機上永遠看不到。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ResponsiveSizesTest {

    private fun sizes(widget: GlanceAppWidget) = (widget.sizeMode as SizeMode.Responsive).sizes

    @Test
    fun monthDeclaresEveryLayoutBreakpoint() {
        val sizes = sizes(MonthWidget())
        assertTrue("農曆小字", sizes.any { it.width in 250.dp..<MonthWidget.WIDE_MIN_WIDTH && it.height >= MonthWidget.LABELS_MIN_HEIGHT && it.height < MonthWidget.PANEL_MIN_HEIGHT })
        assertTrue("下方行程", sizes.any { it.width in 250.dp..<MonthWidget.WIDE_MIN_WIDTH && it.height >= MonthWidget.PANEL_MIN_HEIGHT })
        assertTrue("寬版", sizes.any { it.width >= MonthWidget.WIDE_MIN_WIDTH })
        assertTrue("精簡", sizes.any { it.width < 250.dp })
        assertTrue("沒有小字的一般大小", sizes.any { it.width >= 250.dp && it.height < MonthWidget.LABELS_MIN_HEIGHT })
    }

    @Test
    fun weekDeclaresTwoThreeAndFourEventsPerDay() {
        val heights = sizes(WeekWidget()).map { it.height }
        assertTrue("每天 2 筆", heights.any { it < 140.dp })
        assertTrue("每天 3 筆", heights.any { it >= 140.dp && it < 200.dp })
        assertTrue("每天 4 筆", heights.any { it >= 200.dp })
    }

    @Test
    fun agendaAndNextEventDeclareTheirBreakpoints() {
        assertTrue("行程清單 2×2", sizes(AgendaWidget()).any { it.width < 180.dp })
        assertTrue("行程清單完整清單", sizes(AgendaWidget()).any { it.width >= 180.dp })
        assertTrue("下一個行程拉高", sizes(NextEventWidget()).any { it.height >= 100.dp })
        assertTrue("下一個行程 1 格高", sizes(NextEventWidget()).any { it.height < 100.dp })
    }
}
