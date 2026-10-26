package com.skestc.calendarwidget.tasks

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Google Tasks REST API（v1）的最小用戶端。 */
class TasksApi(private val accessToken: String) {

    class HttpException(val code: Int, message: String) : IOException("HTTP $code: $message")

    /** 所有清單裡未完成的待辦。 */
    suspend fun openTasks(): List<TaskItem> = withContext(Dispatchers.IO) {
        val lists = TasksJson.parseLists(get("users/@me/lists?maxResults=100"))
        lists.flatMap { list ->
            buildList {
                var pageToken: String? = null
                do {
                    val query = "showCompleted=false&showHidden=false&maxResults=100" +
                        (pageToken?.let { "&pageToken=" + encode(it) } ?: "")
                    val json = get("lists/${encode(list.id)}/tasks?$query")
                    addAll(TasksJson.parseTasks(json, list.id))
                    pageToken = TasksJson.nextPageToken(json)
                } while (pageToken != null)
            }
        }
    }

    /** 把待辦標成已完成。HttpURLConnection 不支援 PATCH，用 Google API 支援的 method override。 */
    suspend fun complete(task: TaskItem) = withContext(Dispatchers.IO) {
        request(
            path = "lists/${encode(task.listId)}/tasks/${encode(task.id)}",
            method = "POST",
            body = """{"status":"completed"}""",
            methodOverride = "PATCH",
        )
    }

    private fun get(path: String): String = request(path, "GET", null, null)

    private fun request(path: String, method: String, body: String?, methodOverride: String?): String {
        val connection = URL(BASE + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Accept", "application/json")
            if (methodOverride != null) connection.setRequestProperty("X-HTTP-Method-Override", methodOverride)
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw HttpException(code, text.take(200))
            return text
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    companion object {
        private const val BASE = "https://tasks.googleapis.com/tasks/v1/"
    }
}
