package com.skestc.calendarwidget.settings

import android.content.Context

/**
 * 在 widget 上點選時要開什麼（所有 widget 共用）：
 * - 點日期：預設開 App 內的行程檢視；
 * - 點行程：預設用「＋」設定裡選的日曆 App 開啟編輯，方便直接修改。
 * 兩者都可以改成另一種。
 */
object DayOpen {
    private const val PREFS = "day_open"
    private const val KEY_IN_APP = "in_app"
    private const val KEY_EVENTS_IN_APP = "events_in_app"

    /** 點日期時開 App 內的行程檢視（false：開日曆 App）。 */
    fun inApp(context: Context): Boolean = prefs(context).getBoolean(KEY_IN_APP, true)

    fun setInApp(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_IN_APP, value).apply()
    }

    /** 點行程時開 App 內的行程檢視（false：用選定的日曆 App 開啟編輯）。 */
    fun eventsInApp(context: Context): Boolean = prefs(context).getBoolean(KEY_EVENTS_IN_APP, false)

    fun setEventsInApp(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_EVENTS_IN_APP, value).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
