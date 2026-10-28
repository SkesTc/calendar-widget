package com.skestc.calendarwidget.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONObject

/**
 * 使用者個別隱藏的行程（依事件 ID；重複事件的所有實例一起隱藏），所有 widget 共用。
 * 同時記下標題，行程超出查詢範圍後仍能在清單裡看到並取消隱藏。
 */
class HiddenEvents(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 事件 ID → 隱藏時的標題。 */
    fun all(): Map<Long, String> {
        val json = prefs.getString(KEY, null) ?: return emptyMap()
        return runCatching {
            val o = JSONObject(json)
            o.keys().asSequence().associate { it.toLong() to o.getString(it) }
        }.getOrDefault(emptyMap())
    }

    fun ids(): Set<Long> = all().keys

    fun setHidden(eventId: Long, title: String, hidden: Boolean) {
        val updated = all().toMutableMap()
        if (hidden) updated[eventId] = title else updated.remove(eventId)
        prefs.edit {
            putString(KEY, JSONObject().apply { updated.forEach { (id, t) -> put(id.toString(), t) } }.toString())
        }
    }

    companion object {
        private const val PREFS = "hidden_events"
        private const val KEY = "events"
    }
}
