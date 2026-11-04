package com.skestc.calendarwidget.data

import kotlin.math.floor

/**
 * 一週 widget 依實際大小（dp）決定版面：
 * - 直長（約 4×4 以上）：每天一列，行程用整行寬度顯示完整標題。
 * - 扁長（4×2、4×3）：7 欄並排，每欄能放幾個行程色塊依高度計算。
 */
object WeekLayout {
    private const val PADDING = 16f
    private const val WEEKDAY = 14f
    private const val DATE = 24f
    private const val LABEL = 12f
    private const val CHIP = 17f
    private const val MORE = 12f
    private const val LINE = 17f

    /** 夠高、而且比較接近直的時候，改成每天一列。 */
    fun useRows(widthDp: Float, heightDp: Float): Boolean = heightDp >= 280f && heightDp >= widthDp * 0.75f

    /** 欄位式：每欄放得下幾個色塊（保留「+N」那一行的空間），至少 1 個。 */
    fun chipsPerColumn(heightDp: Float, showLabel: Boolean): Int {
        val available = heightDp - PADDING - WEEKDAY - DATE - (if (showLabel) LABEL else 0f) - MORE
        return floor(available / CHIP).toInt().coerceIn(1, 8)
    }

    /** 每天一列：每列放得下幾行行程（保留「+N」的空間時另外扣），至少 1 行。 */
    fun linesPerRow(heightDp: Float): Int {
        val rowHeight = (heightDp - PADDING) / 7f
        return floor((rowHeight - 4f) / LINE).toInt().coerceIn(1, 6)
    }
}
