package com.skestc.calendarwidget.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
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
                        )
                    )
                }
            }
        }.orEmpty()
    }

    /**
     * 取得從現在起 [days] 天內、尚未結束的事件，依開始時間排序。
     * 查詢的是 Instances 表，重複事件已由系統展開。
     */
    suspend fun upcomingEvents(
        days: Int = 7,
        limit: Int = 30,
        now: Long = System.currentTimeMillis(),
    ): List<CalendarEvent> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()

        // 往前多抓一天，才能拿到「今天」的全天事件（以 UTC 午夜為起點）。
        val rangeStart = now - TimeUnit.DAYS.toMillis(1)
        val rangeEnd = now + TimeUnit.DAYS.toMillis(days.toLong())
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, rangeStart)
            ContentUris.appendId(it, rangeEnd)
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
        )

        context.contentResolver.query(
            uri,
            projection,
            "${CalendarContract.Instances.VISIBLE} = 1",
            null,
            "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { c ->
            buildList {
                while (c.moveToNext() && size < limit) {
                    val end = c.getLong(3)
                    if (end <= now) continue
                    add(
                        CalendarEvent(
                            eventId = c.getLong(0),
                            title = c.getString(1)?.takeIf { it.isNotBlank() } ?: "(無標題)",
                            begin = c.getLong(2),
                            end = end,
                            allDay = c.getInt(4) == 1,
                            color = c.getInt(5),
                            location = c.getString(6)?.takeIf { it.isNotBlank() },
                            calendarName = c.getString(7).orEmpty(),
                        )
                    )
                }
            }
        }.orEmpty()
    }
}
