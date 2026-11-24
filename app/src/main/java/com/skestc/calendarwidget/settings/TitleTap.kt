package com.skestc.calendarwidget.settings

/** 點行程清單左上角的日期、或月曆的月份標題時要做什麼（每個 widget 各自設定）。 */
sealed interface TitleTap {
    /** 跳出選單，每次再選。 */
    data object Menu : TitleTap

    /** App 內的行程檢視（行程清單：今天；月曆：那個月）。 */
    data object Viewer : TitleTap

    /** 選擇月份的對話框（月曆用；行程清單上視同行程檢視）。 */
    data object MonthPicker : TitleTap

    /** 用指定的日曆 App 開到那一天（手機內建的、Google 日曆或其他）。 */
    data class App(val packageName: String) : TitleTap

    fun key(): String = when (this) {
        Menu -> "menu"
        Viewer -> "viewer"
        MonthPicker -> "picker"
        is App -> "app:$packageName"
    }

    companion object {
        fun fromKey(key: String?): TitleTap = when {
            key == null -> Menu
            key == "viewer" -> Viewer
            key == "picker" -> MonthPicker
            key.startsWith("app:") && key.length > 4 -> App(key.removePrefix("app:"))
            else -> Menu
        }
    }
}
