package com.skestc.calendarwidget.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

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
