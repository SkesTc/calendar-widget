package com.skestc.calendarwidget.data

import android.content.Context

/** 最近搜尋過的關鍵字（最新的在前，最多 [MAX] 個），只存在這支手機上。 */
class RecentSearches(context: Context) {
    private val prefs = context.getSharedPreferences("recent_searches", Context.MODE_PRIVATE)

    fun list(): List<String> = prefs.getString(KEY, null)?.split(SEPARATOR)?.filter { it.isNotBlank() }.orEmpty()

    fun add(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return
        save(Companion.add(list(), trimmed))
    }

    fun clear() = prefs.edit().remove(KEY).apply()

    private fun save(items: List<String>) = prefs.edit().putString(KEY, items.joinToString(SEPARATOR)).apply()

    companion object {
        const val MAX = 8
        private const val KEY = "items"
        // 關鍵字裡不會出現的分隔字元。
        private const val SEPARATOR = "\u001F"

        /** 放到最前面；重複的（不分大小寫）移掉舊的，超過 [MAX] 個丟掉最舊的。 */
        fun add(items: List<String>, keyword: String): List<String> =
            (listOf(keyword) + items.filterNot { it.equals(keyword, ignoreCase = true) }).take(MAX)
    }
}
