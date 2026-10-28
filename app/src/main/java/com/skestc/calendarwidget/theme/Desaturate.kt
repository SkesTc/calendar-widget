package com.skestc.calendarwidget.theme

/** 已結束的行程用：把顏色往同亮度的灰色拉，保留一點原色讓人還認得出是哪個日曆。 */
object Desaturate {
    /** [amount] 0 = 原色、1 = 完全灰階；結果不透明。 */
    fun color(argb: Int, amount: Float = 0.75f): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        val gray = 0.299f * r + 0.587f * g + 0.114f * b
        fun ch(c: Int) = Math.round(c + (gray - c) * amount.coerceIn(0f, 1f)).coerceIn(0, 255)
        return (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }
}
