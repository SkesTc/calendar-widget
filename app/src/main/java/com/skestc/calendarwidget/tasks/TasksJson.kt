package com.skestc.calendarwidget.tasks

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Google Tasks API 的 JSON 解析，以及本機快取的序列化。 */
object TasksJson {

    data class TaskList(val id: String, val title: String)

    /** GET users/@me/lists 的回應。 */
    fun parseLists(json: String): List<TaskList> {
        val items = JSONObject(json).optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).map { i ->
            val o = items.getJSONObject(i)
            TaskList(o.getString("id"), o.optString("title"))
        }
    }

    /** GET lists/{id}/tasks 的回應；略過已完成、已刪除、隱藏與沒有標題的項目。 */
    fun parseTasks(json: String, listId: String): List<TaskItem> {
        val items = JSONObject(json).optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).mapNotNull { i ->
            val o = items.getJSONObject(i)
            val title = o.optString("title").trim()
            when {
                o.optString("status") == "completed" -> null
                o.optBoolean("deleted") || o.optBoolean("hidden") -> null
                title.isEmpty() -> null
                else -> TaskItem(
                    id = o.getString("id"),
                    listId = listId,
                    title = title,
                    // RFC 3339，例如 "2026-10-26T00:00:00.000Z"；API 只保留日期部分。
                    due = o.optString("due").takeIf { it.length >= 10 }?.let { LocalDate.parse(it.substring(0, 10)) },
                )
            }
        }
    }

    /** nextPageToken（沒有下一頁時為 null）。 */
    fun nextPageToken(json: String): String? =
        JSONObject(json).optString("nextPageToken").takeIf { it.isNotEmpty() }

    fun toCache(tasks: List<TaskItem>): String = JSONArray().apply {
        for (t in tasks) {
            put(
                JSONObject()
                    .put("id", t.id)
                    .put("listId", t.listId)
                    .put("title", t.title)
                    .put("due", t.due?.toString() ?: JSONObject.NULL),
            )
        }
    }.toString()

    fun fromCache(json: String?): List<TaskItem> {
        if (json.isNullOrEmpty()) return emptyList()
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            TaskItem(
                id = o.getString("id"),
                listId = o.getString("listId"),
                title = o.getString("title"),
                due = if (o.isNull("due")) null else LocalDate.parse(o.getString("due")),
            )
        }
    }
}
