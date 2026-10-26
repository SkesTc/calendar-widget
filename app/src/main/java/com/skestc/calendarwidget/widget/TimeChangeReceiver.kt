package com.skestc.calendarwidget.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 時區、系統時間或語言改變時重新排版；開機或 App 更新後重新排好午夜鬧鐘（鬧鐘在重開機後會消失）。
 */
class TimeChangeReceiver : BroadcastReceiver() {
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
