package com.skestc.calendarwidget.tasks

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.skestc.calendarwidget.widget.WidgetUpdater
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 從 Google Tasks 下載未完成的待辦存進快取，再更新 widget。
 * 有網路時每 30 分鐘一次；連結、勾選完成後也會立刻跑一次。
 */
class TasksSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = TasksRepository(applicationContext)
        if (!repository.isConnected) return Result.success()

        val token = GoogleTasksAuth.silentToken(applicationContext)
        if (token == null) {
            // 使用者撤銷了權限，或 Cloud Console 設定有問題：等使用者回 App 重新連結。
            repository.saveError("authorization")
            WidgetUpdater.refreshAll(applicationContext)
            return Result.success()
        }
        return try {
            repository.saveSynced(TasksApi(token).openTasks())
            WidgetUpdater.refreshAll(applicationContext)
            Result.success()
        } catch (e: IOException) {
            repository.saveError(e.message ?: "network")
            Result.retry()
        }
    }

    companion object {
        private const val PERIODIC = "tasks-sync-periodic"
        private const val NOW = "tasks-sync-now"

        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<TasksSyncWorker>(30, TimeUnit.MINUTES)
                .setConstraints(network)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun syncNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<TasksSyncWorker>().setConstraints(network).build()
            WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).apply {
                cancelUniqueWork(PERIODIC)
                cancelUniqueWork(NOW)
            }
        }
    }
}
