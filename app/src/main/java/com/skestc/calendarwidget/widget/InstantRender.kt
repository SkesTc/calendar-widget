package com.skestc.calendarwidget.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.util.SizeF
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
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
 *
 * 一定要把 widget 的實際尺寸傳進 compose()：沒傳時 Glance 會用宣告的「最小尺寸」來畫，
 * 月曆會先閃出縮小版。只畫目前方向（直向或橫向）的那一份，速度比兩個方向都畫快一倍；
 * 另一個方向由隨後的 update() 補上。
 */
internal object InstantRender {
    // 同一個 widget 同時只能有一個 compose()，連點時依序處理。
    private val mutex = Mutex()

    /** [startedAt]：點按處理開始的時間（SystemClock.elapsedRealtime），用來記錄反應時間。 */
    @OptIn(ExperimentalGlanceApi::class)
    suspend fun render(
        context: Context,
        widget: GlanceAppWidget,
        glanceId: GlanceId,
        startedAt: Long = SystemClock.elapsedRealtime(),
        /** 先畫一個暫時的畫面時設成 false：之後還會再畫完整的那一次，由那次負責通知工作階段。 */
        updateAfter: Boolean = true,
        recordTiming: Boolean = true,
    ) {
        runCatching {
            val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
            val manager = AppWidgetManager.getInstance(context)
            val options = manager.getAppWidgetOptions(appWidgetId)
            val size = currentSize(context, options)
            val composeStart = SystemClock.elapsedRealtime()
            val views = mutex.withLock { widget.compose(context, glanceId, options = options, size = size) }
            manager.updateAppWidget(appWidgetId, views)
            val end = SystemClock.elapsedRealtime()
            if (recordTiming) TapTiming.record(
                context,
                totalMs = end - startedAt,
                composeMs = end - composeStart,
                // App 的程序才剛被喚醒（被系統清掉後重新啟動）：第一次點會比較慢。
                coldStart = startedAt - Process.getStartElapsedRealtime() < COLD_START_MS,
            )
        }
        if (updateAfter) widget.update(context, glanceId)
    }

    private const val COLD_START_MS = 3_000L

    /** 目前方向的 widget 大小（直向：最小寬 × 最大高；橫向：最大寬 × 最小高），拿不到時回傳 null。 */
    @Suppress("DEPRECATION")
    internal fun currentSize(context: Context, options: Bundle): DpSize? {
        val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val sizes = options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
            val pick = if (landscape) sizes?.maxByOrNull { it.width } else sizes?.maxByOrNull { it.height }
            if (pick != null) return DpSize(pick.width.dp, pick.height.dp)
        }
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        if (minWidth == 0 || maxWidth == 0 || minHeight == 0 || maxHeight == 0) return null
        return if (landscape) DpSize(maxWidth.dp, minHeight.dp) else DpSize(minWidth.dp, maxHeight.dp)
    }
}

/** 記下最近一次月曆點按的反應時間，首頁顯示，方便判斷卡頓的原因。 */
object TapTiming {
    private const val PREFS = "tap_timing"

    data class Record(val totalMs: Long, val composeMs: Long, val coldStart: Boolean, val at: Long)

    fun record(context: Context, totalMs: Long, composeMs: Long, coldStart: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("total", totalMs)
            .putLong("compose", composeMs)
            .putBoolean("cold", coldStart)
            .putLong("at", System.currentTimeMillis())
            .apply()
    }

    fun last(context: Context): Record? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains("total")) return null
        return Record(prefs.getLong("total", 0), prefs.getLong("compose", 0), prefs.getBoolean("cold", false), prefs.getLong("at", 0))
    }
}
