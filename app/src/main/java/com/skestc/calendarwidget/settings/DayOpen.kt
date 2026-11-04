package com.skestc.calendarwidget.settings

import android.content.Context

/** 在 widget 上點日期或行程時：開 App 內的行程檢視（預設），或照舊開日曆 App。 */
object DayOpen {
    private const val PREFS = "day_open"
    private const val KEY_IN_APP = "in_app"

    fun inApp(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_IN_APP, true)

    fun setInApp(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_IN_APP, value).apply()
    }
}
