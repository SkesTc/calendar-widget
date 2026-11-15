package com.skestc.calendarwidget.data

/**
 * 在很窄的 widget 欄位裡縮短行程標題。
 *
 * 學校、機關的行事曆常把年度放在最前面（「115學年度第一學期期中考」「115年度校慶」），
 * 欄位只放得下 3～4 個字時每一格都變成「115年…」，看不出差別。這裡拿掉開頭的年度／學期前綴，
 * 拿掉後沒剩東西時保留原標題。
 */
object TitleShortener {

    private val PREFIX = Regex(
        "^\\s*" +
            // 115、2026、一一五 這類年份
            "(?:\\d{2,4}|[一二三四五六七八九十〇零]{2,4})\\s*" +
            "(?:學年度|學年|年度|年)" +
            // 選擇性的學期：第一學期、上學期、第2學期
            "(?:\\s*(?:第\\s*)?[一二12上下]\\s*學期)?" +
            // 前綴後面常見的分隔符號
            "[\\s\\-_－—–:：、．.]*",
    )

    fun compact(title: String): String {
        val stripped = PREFIX.replaceFirst(title, "")
        return stripped.ifBlank { title }.trim()
    }
}
