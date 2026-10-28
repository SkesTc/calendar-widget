package com.skestc.calendarwidget.widget

import com.skestc.calendarwidget.lunar.CachingLunarSource
import com.skestc.calendarwidget.lunar.DayLabeler
import com.skestc.calendarwidget.lunar.IcuLunarSource
import com.skestc.calendarwidget.settings.WidgetSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 月曆資料的記憶體快取，讓「上個月／下個月」幾乎立即切換：
 * 載入一個月之後，順便在背景先載好前後各一個月。
 *
 * Glance 的 widget 工作階段閒置幾秒就會結束，下一次點擊要重新走 provideGlance()；
 * 只要 App 的程序還在，快取就能省掉重新查詢日曆與計算農曆的時間。
 * 設定（含 refresh 計數器）或日期一變，key 就不同，自然不會用到過期的資料。
 */
internal object MonthCache {
    private const val MAX_ENTRIES = 8

    private data class Key(val settings: WidgetSettings, val today: LocalDate)

    private val entries = object : LinkedHashMap<Key, MonthState.Loaded>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, MonthState.Loaded>?) = size > MAX_ENTRIES
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 農曆、節氣算一次就記住（ICU 的 ChineseCalendar 換算不便宜）。 */
    val labeler: DayLabeler by lazy { DayLabeler(CachingLunarSource(IcuLunarSource())) }

    suspend fun get(settings: WidgetSettings, load: suspend (WidgetSettings) -> MonthState): MonthState {
        val today = LocalDate.now()
        val state = cached(Key(settings, today)) ?: load(settings).also { put(Key(settings, today), it) }
        if (state is MonthState.Loaded) prefetchNeighbours(settings, today, load)
        return state
    }

    fun clear() = synchronized(entries) { entries.clear() }

    private fun cached(key: Key): MonthState.Loaded? = synchronized(entries) { entries[key] }

    private fun put(key: Key, state: MonthState) {
        // 沒有權限等狀態不快取，授權後馬上就能看到月曆。
        if (state is MonthState.Loaded) synchronized(entries) { entries[key] = state }
    }

    private fun prefetchNeighbours(settings: WidgetSettings, today: LocalDate, load: suspend (WidgetSettings) -> MonthState) {
        scope.launch {
            for (delta in listOf(1, -1)) {
                val neighbour = settings.copy(monthOffset = settings.monthOffset + delta)
                val key = Key(neighbour, today)
                if (cached(key) == null) runCatching { load(neighbour) }.getOrNull()?.let { put(key, it) }
            }
        }
    }
}
