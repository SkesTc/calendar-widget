package com.skestc.calendarwidget.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * 每天午夜換日，以及時間軸 widget 的行程開始／結束時更新 widget。
 *
 * 用非精確、非喚醒的鬧鐘：不需要 SCHEDULE_EXACT_ALARM 權限，也不會為了更新把手機叫醒；
 * 螢幕關著時會延到下次亮屏才觸發，這時使用者剛好會看到 widget。
 */
object DayChangeScheduler {
    private const val REQUEST_MIDNIGHT = 0
    private const val REQUEST_BOUNDARY = 1
    private const val PREFS = "day_change"
    private const val KEY_BOUNDARY = "next_boundary"

    fun schedule(context: Context) {
        val nextMidnight = LocalDate.now()
            .plusDays(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        context.getSystemService(AlarmManager::class.java)
            .setAndAllowWhileIdle(AlarmManager.RTC, nextMidnight + 1_000, pendingIntent(context, REQUEST_MIDNIGHT))
    }

    /**
     * 在 [at] 時更新（時間軸 widget 的下一個行程開始或結束）。
     * 多個時間軸 widget 各自篩選日曆，所以只在比已排定的更早時才改排，確保最早的那個不會被蓋掉。
     */
    fun scheduleBoundary(context: Context, at: Long) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val scheduled = prefs.getLong(KEY_BOUNDARY, 0L)
        if (scheduled in (now + 1)..at) return
        prefs.edit().putLong(KEY_BOUNDARY, at).apply()
        context.getSystemService(AlarmManager::class.java)
            .setAndAllowWhileIdle(AlarmManager.RTC, at + 5_000, pendingIntent(context, REQUEST_BOUNDARY))
    }

    fun cancel(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        alarms.cancel(pendingIntent(context, REQUEST_MIDNIGHT))
        alarms.cancel(pendingIntent(context, REQUEST_BOUNDARY))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_BOUNDARY).apply()
    }

    private fun pendingIntent(context: Context, requestCode: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        requestCode,
        Intent(context, DayChangeReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

class DayChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                WidgetUpdater.refreshAll(context)
                WidgetUpdater.syncSchedules(context)
            } finally {
                pending.finish()
            }
        }
    }
}
