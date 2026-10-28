package com.skestc.calendarwidget.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.skestc.calendarwidget.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * 透過 Android Calendar Provider 讀取行事曆。
 *
 * 手機登入的 Google 帳號會由系統自動同步到這裡，所以不需要 OAuth 或 Google Calendar API。
 */
class CalendarRepository(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun calendars(): List<CalendarInfo> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.VISIBLE,
            CalendarContract.Calendars.OWNER_ACCOUNT,
            CalendarContract.Calendars.NAME,
        )
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            null,
            null,
            "${CalendarContract.Calendars.ACCOUNT_NAME} ASC",
        )?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        CalendarInfo(
                            id = c.getLong(0),
                            displayName = c.getString(1).orEmpty(),
                            accountName = c.getString(2).orEmpty(),
                            color = c.getInt(3),
                            visible = c.getInt(4) == 1,
                            isHoliday = Holidays.isHolidayCalendar(c.getString(5), c.getString(6)),
                        )
                    )
                }
            }
        }.orEmpty()
    }

    /**
     * 取得從現在起 [days] 天內、尚未結束的事件，依開始時間排序。
     * [keepEndedToday] 為 true 時，今天已經結束的行程也保留（畫面上用淡色顯示）。
     */
    suspend fun upcomingEvents(
        days: Int = 7,
        limit: Int = 30,
        hiddenCalendarIds: Set<Long> = emptySet(),
        now: Long = System.currentTimeMillis(),
        keepEndedToday: Boolean = false,
    ): List<CalendarEvent> {
        val zone = java.time.ZoneId.systemDefault()
        // 往前多抓一天，才能拿到「今天」的全天事件（以 UTC 午夜為起點）。
        val rangeStart = now - TimeUnit.DAYS.toMillis(1)
        val rangeEnd = now + TimeUnit.DAYS.toMillis(days.toLong())
        return instances(rangeStart, rangeEnd, hiddenCalendarIds)
            .asSequence()
            .filter { isUpcoming(it, now, zone, keepEndedToday) }
            .take(limit)
            .toList()
    }

    companion object {
        /**
         * 還要列出來的事件。全天事件以 UTC 午夜為界（例如昨天的全天行程在台灣到今天早上 8 點才「結束」），
         * 所以要用它的日期判斷，而不是結束的時間點，否則昨天的全天行程會跑到今天。
         * [keepEndedToday]：今天已經結束的行程也留著（畫面上淡化顯示）。
         */
        fun isUpcoming(event: CalendarEvent, now: Long, zone: java.time.ZoneId, keepEndedToday: Boolean): Boolean {
            val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            if (event.allDay) return MonthBuilder(zone).dateSpan(event).second >= today
            val cutoff = if (keepEndedToday) today.atStartOfDay(zone).toInstant().toEpochMilli() else now
            return event.end > cutoff
        }
    }

    /**
     * 查詢 [start, end) 之間的事件實例。查的是 Instances 表，重複事件已由系統展開。
     * [includeHiddenEvents] 為 true 時連使用者隱藏的行程也回傳（給「長期行程」管理畫面用）。
     */
    suspend fun instances(
        start: Long,
        end: Long,
        hiddenCalendarIds: Set<Long> = emptySet(),
        includeHiddenEvents: Boolean = false,
    ): List<CalendarEvent> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()

        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, start)
            ContentUris.appendId(it, end)
        }.build()
        val hiddenEventIds = if (includeHiddenEvents) emptySet() else HiddenEvents(context).ids()

        var selection = "${CalendarContract.Instances.VISIBLE} = 1"
        if (hiddenCalendarIds.isNotEmpty()) {
            // ID 都是 Long，直接組進 SQL 不會有注入問題。
            selection += " AND ${CalendarContract.Instances.CALENDAR_ID} NOT IN " +
                hiddenCalendarIds.joinToString(",", "(", ")")
        }
        // 使用者在「長期行程」隱藏的事件，所有 widget 都不顯示。
        query(uri, selection).filter { it.eventId !in hiddenEventIds }
    }

    /**
     * 用關鍵字搜尋行程（標題、地點、說明），範圍是 [start, end)。
     * 用系統的 Instances 搜尋網址，重複行程已展開；搜尋不受各 widget 的隱藏設定影響。
     */
    suspend fun search(keyword: String, start: Long, end: Long): List<CalendarEvent> = withContext(Dispatchers.IO) {
        if (!hasPermission() || keyword.isBlank()) return@withContext emptyList()
        val uri = CalendarContract.Instances.CONTENT_SEARCH_URI.buildUpon().also {
            ContentUris.appendId(it, start)
            ContentUris.appendId(it, end)
            it.appendPath(keyword.trim())
        }.build()
        query(uri, "${CalendarContract.Instances.VISIBLE} = 1")
    }

    private fun query(uri: android.net.Uri, selection: String): List<CalendarEvent> {
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.DESCRIPTION,
        )
        val holidayCalendarIds = holidayCalendarIds()

        return context.contentResolver.query(
            uri,
            projection,
            selection,
            null,
            "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val title = c.getString(1)?.takeIf { it.isNotBlank() } ?: context.getString(R.string.untitled)
                    val calendarId = c.getLong(8)
                    add(
                        CalendarEvent(
                            eventId = c.getLong(0),
                            title = title,
                            begin = c.getLong(2),
                            end = c.getLong(3),
                            allDay = c.getInt(4) == 1,
                            color = c.getInt(5),
                            location = c.getString(6)?.takeIf { it.isNotBlank() },
                            calendarName = c.getString(7).orEmpty(),
                            calendarId = calendarId,
                            holiday = if (calendarId in holidayCalendarIds) {
                                Holidays.classify(title, c.getString(9))
                            } else {
                                null
                            },
                        )
                    )
                }
            }
        }.orEmpty()
    }

    /** 已訂閱的 Google 節慶假日日曆。 */
    private fun holidayCalendarIds(): Set<Long> {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.OWNER_ACCOUNT,
            CalendarContract.Calendars.NAME,
        )
        return context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            null,
            null,
            null,
        )?.use { c ->
            buildSet {
                while (c.moveToNext()) {
                    if (Holidays.isHolidayCalendar(c.getString(1), c.getString(2))) add(c.getLong(0))
                }
            }
        }.orEmpty()
    }
}
