package com.skestc.calendarwidget.data

import kotlin.math.floor

/**
 * 月曆下方「當天行程」區塊的高度：依當天行程數量決定，最多占 widget 高度的 [MAX_SHARE]，
 * 剩下的給月曆格子。行程少時不浪費空間，行程多時盡量多列幾筆。
 */
object MonthLayout {
    /** 上下內距 16 + 標題列 20 + 與月曆的間距 6。 */
    private const val CHROME = 42f
    private const val LINE = 20f
    private const val MORE = 16f
    const val MAX_SHARE = 0.45f

    private fun line(textScale: Float) = LINE * textScale.coerceAtLeast(0.8f)

    /** 下方區塊的高度（dp）：剛好放得下全部行程，但不超過上限；至少放得下一行。 */
    fun panelHeight(widgetHeightDp: Float, eventCount: Int, textScale: Float = 1f): Float {
        val line = line(textScale)
        val needed = CHROME + maxOf(1, eventCount) * line
        val min = CHROME + line
        return needed.coerceAtMost(widgetHeightDp * MAX_SHARE).coerceAtLeast(min)
    }

    /** [panelHeightDp] 高的區塊能列出幾筆行程；放不下全部時保留「+N」那一行，至少 1 筆。 */
    fun eventsThatFit(panelHeightDp: Float, eventCount: Int, textScale: Float = 1f): Int {
        val line = line(textScale)
        val all = floor((panelHeightDp - CHROME) / line).toInt()
        if (eventCount <= all) return eventCount
        return floor((panelHeightDp - CHROME - MORE) / line).toInt().coerceAtLeast(1)
    }
}
