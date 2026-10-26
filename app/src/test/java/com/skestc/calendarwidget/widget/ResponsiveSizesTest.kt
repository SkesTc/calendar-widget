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
 * 月曆與一週改用 Exact（依實際大小畫），不受這個限制。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ResponsiveSizesTest {

    private fun sizes(widget: GlanceAppWidget) = (widget.sizeMode as SizeMode.Responsive).sizes

    /** 月曆版面最大，用 Exact 依實際大小畫，避免一次送出多份版面超過 Binder 上限。 */
    @Test
    fun monthUsesExactSizing() {
        assertTrue(MonthWidget().sizeMode is SizeMode.Exact)
    }

    /** 一週依實際大小決定欄位或每天一列，以及每天顯示幾筆（WeekLayout），也用 Exact。 */
    @Test
    fun weekUsesExactSizing() {
        assertTrue(WeekWidget().sizeMode is SizeMode.Exact)
    }

    /** 行程清單涵蓋一個月以上，清單很長，也用 Exact。 */
    @Test
    fun agendaUsesExactSizing() {
        assertTrue(AgendaWidget().sizeMode is SizeMode.Exact)
    }

    @Test
    fun nextEventDeclaresItsBreakpoints() {
        assertTrue("下一個行程拉高", sizes(NextEventWidget()).any { it.height >= 100.dp })
        assertTrue("下一個行程 1 格高", sizes(NextEventWidget()).any { it.height < 100.dp })
    }
}
