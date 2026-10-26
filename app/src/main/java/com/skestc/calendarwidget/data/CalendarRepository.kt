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
        val cutoff = if (keepEndedToday) {
            java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        } else {
            now
        }
        // 往前多抓一天，才能拿到「今天」的全天事件（以 UTC 午夜為起點）。
        val rangeStart = now - TimeUnit.DAYS.toMillis(1)
        val rangeEnd = now + TimeUnit.DAYS.toMillis(days.toLong())
        return instances(rangeStart, rangeEnd, hiddenCalendarIds)
            .asSequence()
            .filter { it.end > cutoff }
            .take(limit)
            .toList()
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
        val hiddenEventIds = if (includeHiddenEvents) emptySet() else HiddenEvents(context).ids()

        var selection = "${CalendarContract.Instances.VISIBLE} = 1"
        if (hiddenCalendarIds.isNotEmpty()) {
            // ID 都是 Long，直接組進 SQL 不會有注入問題。
            selection += " AND ${CalendarContract.Instances.CALENDAR_ID} NOT IN " +
                hiddenCalendarIds.joinToString(",", "(", ")")
        }

        context.contentResolver.query(
            uri,
            projection,
            selection,
            null,
            "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    // 使用者在「長期行程」隱藏的事件，所有 widget 都不顯示。
                    if (c.getLong(0) in hiddenEventIds) continue
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
