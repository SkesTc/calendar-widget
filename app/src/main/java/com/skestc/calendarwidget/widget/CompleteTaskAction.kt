package com.skestc.calendarwidget.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.skestc.calendarwidget.tasks.GoogleTasksAuth
import com.skestc.calendarwidget.tasks.TasksApi
import com.skestc.calendarwidget.tasks.TasksRepository
import com.skestc.calendarwidget.tasks.TasksSyncWorker
import java.io.IOException

/** 在 widget 上勾選待辦：先從快取拿掉讓畫面立刻更新，再通知 Google Tasks。 */
class CompleteTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val taskId = parameters[TaskIdKey] ?: return
        val repository = TasksRepository(context)
        val task = repository.findCached(taskId) ?: return

        repository.removeFromCache(taskId)
        WidgetUpdater.refreshAll(context)

        val token = GoogleTasksAuth.silentToken(context)
        try {
            if (token == null) throw IOException("authorization")
            TasksApi(token).complete(task)
        } catch (e: IOException) {
            // 沒成功的話，下次同步會把這筆待辦放回來，不會默默遺失。
            repository.saveError(e.message ?: "network")
            TasksSyncWorker.syncNow(context)
        }
    }

    companion object {
        val TaskIdKey = ActionParameters.Key<String>("taskId")
    }
}
