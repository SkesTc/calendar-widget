package com.skestc.calendarwidget.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.compose
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 點按之後立刻重畫。
 *
 * Glance 的 update() 要靠 WorkManager 排一個背景工作來跑 widget 的工作階段，
 * 而這種工作不是「加急」的，系統可以延後執行（省電、待機分組），
 * 所以月曆按切換月份或點日期時，有時要等好幾秒、甚至像沒反應。
 * 這裡直接在點按的處理裡合成一次畫面交給桌面；接著仍呼叫 update()，
 * 讓正在執行（或之後啟動）的工作階段也拿到新狀態，不會蓋回舊畫面。
 *
 * 只用在沒有清單（LazyColumn）的 widget：Android 12 以前清單需要工作階段提供內容。
 */
internal object InstantRender {
    // 同一個 widget 同時只能有一個 compose()，連點時依序處理。
    private val mutex = Mutex()

    @OptIn(ExperimentalGlanceApi::class)
    suspend fun render(context: Context, widget: GlanceAppWidget, glanceId: GlanceId) {
        runCatching {
            val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
            val views = mutex.withLock { widget.compose(context, glanceId) }
            AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views)
        }
        widget.update(context, glanceId)
    }
}
