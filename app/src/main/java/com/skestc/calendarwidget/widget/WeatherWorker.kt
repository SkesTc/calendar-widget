package com.skestc.calendarwidget.widget

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
import com.skestc.calendarwidget.data.WeatherStore
import java.util.concurrent.TimeUnit

/** 有網路時每小時抓一次天氣與空氣品質，抓到後更新 widget。 */
class WeatherWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val store = WeatherStore(applicationContext)
        if (store.place == null) {
            // 天氣關閉了：更新一次讓 widget 拿掉天氣列。
            WidgetUpdater.refreshAll(applicationContext)
            return Result.success()
        }
        if (!store.fetch()) return Result.retry()
        WidgetUpdater.refreshAll(applicationContext)
        return Result.success()
    }

    companion object {
        private const val PERIODIC = "weather-periodic"
        private const val NOW = "weather-now"

        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /** 依目前的設定開始或停止每小時更新。 */
        fun sync(context: Context) {
            if (WeatherStore(context).place == null) {
                cancel(context)
                return
            }
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<WeatherWorker>(1, TimeUnit.HOURS).setConstraints(constraints).build(),
            )
        }

        /** 不連網，只讓 widget 重新讀快取（例如關閉天氣時把那一列拿掉）。 */
        fun refreshWidgets(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                NOW,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<WeatherWorker>().build(),
            )
        }

        fun cancel(context: Context) {
            val work = WorkManager.getInstance(context)
            work.cancelUniqueWork(PERIODIC)
            work.cancelUniqueWork(NOW)
        }

        /** 馬上抓一次（換地區、或 widget 發現快取太舊時）；已經在排隊就不重複。 */
        fun fetchNow(context: Context, replace: Boolean = false) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                NOW,
                if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<WeatherWorker>().setConstraints(constraints).build(),
            )
        }
    }
}
