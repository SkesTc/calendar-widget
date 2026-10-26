package com.skestc.calendarwidget.tasks

import android.content.Context
import androidx.core.content.edit

/**
 * Google Tasks 的本機快取與連結狀態。
 *
 * widget 只讀快取（快、離線也能顯示），網路同步交給 [TasksSyncWorker]。
 */
class TasksRepository(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val isConnected: Boolean get() = prefs.getBoolean(KEY_CONNECTED, false)

    /** 上次成功同步的時間（epoch 毫秒），從未同步時為 0。 */
    val lastSync: Long get() = prefs.getLong(KEY_LAST_SYNC, 0L)

    /** 上次同步失敗的原因，成功後清除。 */
    val lastError: String? get() = prefs.getString(KEY_LAST_ERROR, null)

    /** 快取的待辦；沒連結時一律回傳空清單。 */
    fun cachedTasks(): List<TaskItem> =
        if (isConnected) runCatching { TasksJson.fromCache(prefs.getString(KEY_CACHE, null)) }.getOrDefault(emptyList()) else emptyList()

    fun setConnected(connected: Boolean) = prefs.edit {
        putBoolean(KEY_CONNECTED, connected)
        if (!connected) {
            remove(KEY_CACHE)
            remove(KEY_LAST_SYNC)
            remove(KEY_LAST_ERROR)
        }
    }

    fun saveSynced(tasks: List<TaskItem>, now: Long = System.currentTimeMillis()) = prefs.edit {
        putString(KEY_CACHE, TasksJson.toCache(tasks))
        putLong(KEY_LAST_SYNC, now)
        remove(KEY_LAST_ERROR)
    }

    fun saveError(message: String) = prefs.edit { putString(KEY_LAST_ERROR, message) }

    /** 勾選完成時先從快取拿掉，widget 立刻更新，不必等網路。 */
    fun removeFromCache(taskId: String) {
        val remaining = cachedTasks().filterNot { it.id == taskId }
        prefs.edit { putString(KEY_CACHE, TasksJson.toCache(remaining)) }
    }

    fun findCached(taskId: String): TaskItem? = cachedTasks().firstOrNull { it.id == taskId }

    companion object {
        private const val PREFS = "google_tasks"
        private const val KEY_CONNECTED = "connected"
        private const val KEY_CACHE = "cache"
        private const val KEY_LAST_SYNC = "last_sync"
        private const val KEY_LAST_ERROR = "last_error"
    }
}
