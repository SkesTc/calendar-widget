package com.skestc.calendarwidget.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Process
import android.os.SystemClock
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
 * 這裡直接在點按的處理裡合成畫面交給桌面，每次點按只送一次畫面。
 *
 * - 一定要把 widget 的 options（實際尺寸）傳進 compose()：沒傳時 Glance 會用宣告的「最小尺寸」來畫。
 * - 畫出的格式要和 Glance 工作階段一樣（直向、橫向各一份）：只送一個方向時，
 *   和工作階段送的格式不同，桌面得整個重建畫面，看起來會閃一下、整個消失再出現。
 * - 不再接著呼叫 update()：那會另外排一個背景工作，幾秒後再畫一次、再送一次，
 *   跟下一次點按搶資源也可能造成閃爍。之後真的需要更新時（日曆變動、換日），
 *   工作階段會從 widget 狀態讀到最新的月份與選取，不會蓋回舊畫面。
 *
 * 只用在沒有清單（LazyColumn）的 widget：Android 12 以前清單需要工作階段提供內容。
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
        recordTiming: Boolean = true,
    ) {
        runCatching {
            val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
            val manager = AppWidgetManager.getInstance(context)
            val options = manager.getAppWidgetOptions(appWidgetId)
            val composeStart = SystemClock.elapsedRealtime()
            val views = mutex.withLock { widget.compose(context, glanceId, options = options) }
            manager.updateAppWidget(appWidgetId, views)
            val end = SystemClock.elapsedRealtime()
            if (recordTiming) {
                TapTiming.record(
                    context,
                    totalMs = end - startedAt,
                    composeMs = end - composeStart,
                    // App 的程序才剛被喚醒（被系統清掉後重新啟動）：第一次點會比較慢。
                    coldStart = startedAt - Process.getStartElapsedRealtime() < COLD_START_MS,
                )
            }
        }.onFailure {
            // 立即重畫失敗時（例如 widget 剛被移除）才交給工作階段。
            widget.update(context, glanceId)
        }
    }

    private const val COLD_START_MS = 3_000L
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
