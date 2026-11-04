package com.skestc.calendarwidget.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback

/**
 * 「重新整理」按鈕：清掉月曆的快取，重新讀取日曆並更新所有 widget。
 * 給自動更新被手機省電功能擋住、或剛在別的裝置改了行程還沒同步過來時用。
 */
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        refreshEverything(context)
    }

    companion object {
        suspend fun refreshEverything(context: Context) {
            MonthCache.clear()
            WidgetUpdater.refreshAll(context)
        }
    }
}
