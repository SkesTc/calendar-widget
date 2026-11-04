package com.skestc.calendarwidget.widget

import android.content.Context
import android.provider.CalendarContract
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Duration

/**
 * 日曆資料（例如 Google 同步進來的新事件）一有變動就更新 widget。
 * Content URI 觸發器只會觸發一次，所以每次執行完都要重新排一次。
 */
class CalendarChangeWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        WidgetUpdater.refreshAll(applicationContext)
        enqueue(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "calendar-change-observer"

        fun schedule(context: Context) = enqueue(context, ExistingWorkPolicy.KEEP)

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        private fun enqueue(context: Context, policy: ExistingWorkPolicy) {
            val constraints = Constraints.Builder()
                .addContentUriTrigger(CalendarContract.CONTENT_URI, true)
                // 盡快反應（日曆 App 一次存檔可能連續寫好幾筆，1 秒內的變動合併成一次）。
                .setTriggerContentUpdateDelay(Duration.ofSeconds(1))
                .setTriggerContentMaxDelay(Duration.ofSeconds(10))
                .build()
            val request = OneTimeWorkRequestBuilder<CalendarChangeWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request)
        }
    }
}
