package com.skestc.calendarwidget.widget

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.glance.LocalContext
import com.skestc.calendarwidget.R

/**
 * 在 widget 的 composition 裡依 [key] 載入資料。
 *
 * provideGlance() 先用 [initialKey] 載好 [initial]，所以第一次畫面不會空白；
 * 之後 key（設定、月份偏移、refresh 計數器）一改變就重新載入。
 */
@Composable
fun <K, T> rememberWidgetData(
    key: K,
    initialKey: K,
    initial: T,
    load: suspend (K) -> T,
): T {
    var value by remember { mutableStateOf(initial) }
    var loadedKey by remember { mutableStateOf(initialKey) }
    LaunchedEffect(key) {
        if (key != loadedKey) {
            value = load(key)
            loadedKey = key
        }
    }
    return value
}

/** Glance 沒有 stringResource()，用 LocalContext 讀字串資源。 */
@Composable
fun glanceString(@StringRes id: Int, vararg args: Any): String =
    LocalContext.current.getString(id, *args)

/** 「2 小時 30 分」／「2 h 30 min」。 */
fun formatDuration(context: Context, minutes: Long): String {
    val h = (minutes / 60).toInt()
    val m = (minutes % 60).toInt()
    return when {
        h == 0 -> context.getString(R.string.duration_minutes, m)
        m == 0 -> context.getString(R.string.duration_hours, h)
        else -> context.getString(R.string.duration_hours_minutes, h, m)
    }
}
