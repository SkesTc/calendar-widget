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
import com.skestc.calendarwidget.data.FetchResult
import com.skestc.calendarwidget.data.LocationReader
import com.skestc.calendarwidget.data.WeatherLocationMode
import com.skestc.calendarwidget.data.WeatherStore
import java.util.concurrent.TimeUnit

/** 有網路時每小時抓一次天氣與空氣品質，抓到後更新 widget。 */
class WeatherWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val store = WeatherStore(applicationContext)
        if (!store.enabled) {
            // 天氣關閉了：更新一次讓 widget 拿掉天氣列。
            WidgetUpdater.refreshAll(applicationContext)
            return Result.success()
        }
        // 背景拿不到新的位置，用系統記得的最後位置（有的話）。
        if (store.mode == WeatherLocationMode.GPS) {
            LocationReader.lastKnown(applicationContext)?.let { store.saveGps(it.latitude, it.longitude) }
        }
        return when (store.fetch()) {
            FetchResult.WeatherFailed -> Result.retry()
            FetchResult.NoLocation -> Result.success()
            else -> {
                WidgetUpdater.refreshAll(applicationContext)
                Result.success()
            }
        }
    }

    companion object {
        private const val PERIODIC = "weather-periodic"
        private const val NOW = "weather-now"

        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /** 依目前的設定開始或停止每小時更新。 */
        fun sync(context: Context) {
            if (!WeatherStore(context).enabled) {
                cancel(context)
                return
            }
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<WeatherWorker>(1, TimeUnit.HOURS).setConstraints(constraints).build(),
            )
        }

        /** 馬上抓一次（換設定、或 widget 發現快取太舊時）；已經在排隊就不重複。 */
        fun fetchNow(context: Context, replace: Boolean = false) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                NOW,
                if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<WeatherWorker>().setConstraints(constraints).build(),
            )
        }

        fun cancel(context: Context) {
            val work = WorkManager.getInstance(context)
            work.cancelUniqueWork(PERIODIC)
            work.cancelUniqueWork(NOW)
        }
    }
}
