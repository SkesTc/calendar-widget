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

    /** 月曆標題列：要顯示哪些按鈕、標題要不要寫年份。 */
    data class HeaderFit(val showYear: Boolean, val separateButtons: Boolean)

    // 標題列的固定寬度（dp）：左右箭頭、「＋」、搜尋、設定、「⋯」與間距，加上外框與標題列的內距。
    private const val NAV = 30f
    private const val ADD = 34f
    private const val ACTION = 30f
    private const val GAP = 4f
    private const val PADDING = 24f
    /**
     * 桌面回報的 widget 大小不一定準（各家桌面、橫直向不同），只用 88% 來算，
     * 寧可少放一顆按鈕，也不要讓按鈕被擠出畫面。
     */
    private const val SAFETY = 0.88f

    /**
     * 依寬度決定標題列：放得下「搜尋」「設定」兩顆就分開放（優先保留），
     * 否則合併成一顆「⋯」選單；年份放得下才寫。[yearTitleDp]、[monthTitleDp] 是兩種標題的估計寬度。
     */
    fun headerFit(widthDp: Float, yearTitleDp: Float, monthTitleDp: Float): HeaderFit {
        val available = widthDp * SAFETY - PADDING
        val base = NAV * 2 + ADD + GAP * 3
        val separate = base + (ACTION + GAP) * 2
        return when {
            available >= separate + yearTitleDp -> HeaderFit(showYear = true, separateButtons = true)
            available >= separate + monthTitleDp -> HeaderFit(showYear = false, separateButtons = true)
            available >= base + ACTION + GAP + yearTitleDp -> HeaderFit(showYear = true, separateButtons = false)
            else -> HeaderFit(showYear = false, separateButtons = false)
        }
    }

    /** 粗估文字寬度（dp）：中日韓文字約一個字高，英數約 0.6，空白 0.3。[sizeSp] 已含文字縮放。 */
    fun textWidthDp(text: String, sizeSp: Float): Float = text.sumOf { c ->
        when {
            c.isWhitespace() -> 0.3
            c.code >= 0x2E80 -> 1.0
            else -> 0.6
        }
    }.toFloat() * sizeSp
}
